package com.proxyman.android.proxy

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class CertificateManager(private val context: Context) {

    private var caKeyPair: KeyPair? = null
    private var caCertificate: X509Certificate? = null
    private val hostCertCache = ConcurrentHashMap<String, SSLContext>()

    init {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
    }

    fun initialize() {
        val caFile = File(context.filesDir, CA_KEYSTORE_FILE)
        if (caFile.exists()) {
            loadCA(caFile)
        } else {
            generateCA()
            saveCA(caFile)
        }
    }

    fun getCACertificate(): X509Certificate? = caCertificate

    fun getSSLContextForHost(host: String): SSLContext {
        return hostCertCache.getOrPut(host) {
            createSSLContextForHost(host)
        }
    }

    private fun generateCA() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        keyPairGenerator.initialize(2048, SecureRandom())
        caKeyPair = keyPairGenerator.generateKeyPair()

        val now = Date()
        val calendar = Calendar.getInstance()
        calendar.time = now
        calendar.add(Calendar.YEAR, 10)
        val expiry = calendar.time

        val issuer = X500Name("CN=Proxyman CA, O=Proxyman, L=Android")
        val serial = BigInteger.valueOf(SecureRandom().nextLong()).abs()

        val builder = JcaX509v3CertificateBuilder(
            issuer, serial, now, expiry, issuer, caKeyPair!!.public
        )

        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage, true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
        )

        val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(caKeyPair!!.private)

        val holder: X509CertificateHolder = builder.build(signer)
        caCertificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(holder)
    }

    private fun loadCA(file: File) {
        val keyStore = KeyStore.getInstance("BKS", BouncyCastleProvider.PROVIDER_NAME)
        FileInputStream(file).use { fis ->
            keyStore.load(fis, CA_PASSWORD.toCharArray())
        }
        caKeyPair = KeyPair(
            keyStore.getCertificate(CA_ALIAS).publicKey,
            keyStore.getKey(CA_ALIAS, CA_PASSWORD.toCharArray()) as java.security.PrivateKey
        )
        caCertificate = keyStore.getCertificate(CA_ALIAS) as X509Certificate
    }

    private fun saveCA(file: File) {
        val keyStore = KeyStore.getInstance("BKS", BouncyCastleProvider.PROVIDER_NAME)
        keyStore.load(null, CA_PASSWORD.toCharArray())
        keyStore.setKeyEntry(
            CA_ALIAS,
            caKeyPair!!.private,
            CA_PASSWORD.toCharArray(),
            arrayOf(caCertificate)
        )
        FileOutputStream(file).use { fos ->
            keyStore.store(fos, CA_PASSWORD.toCharArray())
        }
    }

    private fun createSSLContextForHost(host: String): SSLContext {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        keyPairGenerator.initialize(2048, SecureRandom())
        val hostKeyPair = keyPairGenerator.generateKeyPair()

        val now = Date()
        val calendar = Calendar.getInstance()
        calendar.time = now
        calendar.add(Calendar.YEAR, 1)
        val expiry = calendar.time

        val subject = X500Name("CN=$host, O=Proxyman")
        val serial = BigInteger.valueOf(SecureRandom().nextLong()).abs()

        val builder = JcaX509v3CertificateBuilder(
            X500Name(caCertificate!!.subjectX500Principal.name),
            serial, now, expiry, subject, hostKeyPair.public
        )

        val san = GeneralNames(GeneralName(GeneralName.dNSName, host))
        builder.addExtension(Extension.subjectAlternativeName, false, san)

        val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(caKeyPair!!.private)

        val holder = builder.build(signer)
        val hostCert = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(holder)

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            "host",
            hostKeyPair.private,
            "password".toCharArray(),
            arrayOf(hostCert, caCertificate)
        )

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, "password".toCharArray())

        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        return sslContext
    }

    fun exportCACertificatePem(): String? {
        val cert = caCertificate ?: return null
        val encoded = android.util.Base64.encodeToString(cert.encoded, android.util.Base64.DEFAULT)
        return "-----BEGIN CERTIFICATE-----\n$encoded-----END CERTIFICATE-----\n"
    }

    companion object {
        private const val CA_KEYSTORE_FILE = "proxyman_ca.bks"
        private const val CA_ALIAS = "proxyman_ca"
        private const val CA_PASSWORD = "proxyman_ca_store"
    }
}
