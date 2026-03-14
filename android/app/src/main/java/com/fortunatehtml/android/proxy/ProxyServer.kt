package com.fortunatehtml.android.proxy

import android.net.VpnService
import com.fortunatehtml.android.data.TrafficRepository
import com.fortunatehtml.android.model.TrafficEntry
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocket

class ProxyServer(
    private val port: Int,
    private val certificateManager: CertificateManager,
    private val trafficRepository: TrafficRepository,
    private val mitmEnabled: Boolean = true,
    private val vpnService: VpnService? = null
) {

    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    @Volatile
    private var running = false

    fun start() {
        running = true
        executor = Executors.newCachedThreadPool()
        serverSocket = ServerSocket(port)

        Thread {
            while (running) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    executor?.submit { handleClient(clientSocket) }
                } catch (_: SocketException) {
                    // Server socket closed
                } catch (e: IOException) {
                    if (running) {
                        e.printStackTrace()
                    }
                }
            }
        }.start()
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        executor?.shutdownNow()
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val requestLine = reader.readLine() ?: return

            val parts = requestLine.split(" ")
            if (parts.size < 3) return

            val method = parts[0]
            val target = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val colonIndex = line!!.indexOf(':')
                if (colonIndex > 0) {
                    val key = line!!.substring(0, colonIndex).trim()
                    val value = line!!.substring(colonIndex + 1).trim()
                    headers[key] = value
                    if (key.equals("Content-Length", ignoreCase = true)) {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            // Read body if present
            var requestBody: String? = null
            if (contentLength > 0) {
                val body = CharArray(contentLength)
                reader.read(body, 0, contentLength)
                requestBody = String(body)
            }

            if (method == "CONNECT") {
                handleConnect(clientSocket, target, headers)
            } else {
                handleHttp(clientSocket, method, target, headers, requestBody)
            }
        } catch (_: Exception) {
        } finally {
            try {
                clientSocket.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun handleConnect(clientSocket: Socket, target: String, headers: Map<String, String>) {
        val hostPort = target.split(":")
        val host = hostPort[0]
        val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 443 else 443

        // Send 200 Connection Established
        val output = clientSocket.getOutputStream()
        output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
        output.flush()

        if (mitmEnabled) {
            handleMitmConnection(clientSocket, host, port)
        } else {
            tunnelConnection(clientSocket, host, port)
        }
    }

    private fun handleMitmConnection(clientSocket: Socket, host: String, remotePort: Int) {
        try {
            val sslContext = certificateManager.getSSLContextForHost(host)

            // Wrap client connection with SSL using our generated certificate
            val sslSocket = sslContext.socketFactory.createSocket(
                clientSocket, host, clientSocket.port, true
            ) as SSLSocket
            sslSocket.useClientMode = false
            sslSocket.startHandshake()

            // Read the actual HTTPS request
            val reader = BufferedReader(InputStreamReader(sslSocket.getInputStream()))
            val requestLine = reader.readLine() ?: return

            val parts = requestLine.split(" ")
            if (parts.size < 3) return

            val method = parts[0]
            val path = parts[1]

            // Read headers
            val requestHeaders = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val colonIndex = line!!.indexOf(':')
                if (colonIndex > 0) {
                    val key = line!!.substring(0, colonIndex).trim()
                    val value = line!!.substring(colonIndex + 1).trim()
                    requestHeaders[key] = value
                    if (key.equals("Content-Length", ignoreCase = true)) {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            var requestBody: String? = null
            if (contentLength > 0) {
                val body = CharArray(contentLength)
                reader.read(body, 0, contentLength)
                requestBody = String(body)
            }

            val url = "https://$host$path"
            val entry = TrafficEntry(
                method = method,
                url = url,
                host = host,
                path = path,
                scheme = "https",
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                isHttps = true
            )
            trafficRepository.addEntry(entry)

            // Forward to actual server
            forwardHttpsRequest(
                sslSocket.getOutputStream(), entry, method, path,
                host, remotePort, requestHeaders, requestBody
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun forwardHttpsRequest(
        clientOutput: OutputStream,
        entry: TrafficEntry,
        method: String,
        path: String,
        host: String,
        port: Int,
        headers: Map<String, String>,
        body: String?
    ) {
        try {
            // Create SSL connection to actual server
            val trustAllContext = SSLContextHelper.createTrustAllSSLContext()
            val socket = Socket()
            vpnService?.protect(socket)
            socket.connect(InetSocketAddress(host, port))
            val sslSocket = trustAllContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
            sslSocket.startHandshake()

            val startTime = System.currentTimeMillis()

            // Send request to server
            val serverOutput = sslSocket.getOutputStream()
            val requestBuilder = StringBuilder()
            requestBuilder.append("$method $path HTTP/1.1\r\n")
            requestBuilder.append("Host: $host\r\n")
            for ((key, value) in headers) {
                if (!key.equals("Host", ignoreCase = true)) {
                    requestBuilder.append("$key: $value\r\n")
                }
            }
            requestBuilder.append("\r\n")
            serverOutput.write(requestBuilder.toString().toByteArray())
            if (body != null) {
                serverOutput.write(body.toByteArray())
            }
            serverOutput.flush()

            // Read response from server
            val serverReader = BufferedReader(InputStreamReader(sslSocket.getInputStream()))
            val statusLine = serverReader.readLine() ?: return

            val statusParts = statusLine.split(" ", limit = 3)
            val statusCode = if (statusParts.size >= 2) statusParts[1].toIntOrNull() ?: 0 else 0

            val responseHeaders = mutableMapOf<String, String>()
            var responseLine: String?
            var responseContentLength = 0
            val responseBuilder = StringBuilder()
            responseBuilder.append("$statusLine\r\n")

            while (serverReader.readLine().also { responseLine = it } != null) {
                if (responseLine.isNullOrEmpty()) {
                    responseBuilder.append("\r\n")
                    break
                }
                responseBuilder.append("$responseLine\r\n")
                val colonIndex = responseLine!!.indexOf(':')
                if (colonIndex > 0) {
                    val key = responseLine!!.substring(0, colonIndex).trim()
                    val value = responseLine!!.substring(colonIndex + 1).trim()
                    responseHeaders[key] = value
                    if (key.equals("Content-Length", ignoreCase = true)) {
                        responseContentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            var responseBody: String? = null
            if (responseContentLength > 0) {
                val bodyChars = CharArray(responseContentLength)
                var read = 0
                while (read < responseContentLength) {
                    val n = serverReader.read(bodyChars, read, responseContentLength - read)
                    if (n == -1) break
                    read += n
                }
                responseBody = String(bodyChars, 0, read)
                responseBuilder.append(responseBody)
            }

            val duration = System.currentTimeMillis() - startTime

            // Update traffic entry with response
            trafficRepository.updateEntry(entry.id) { existing ->
                existing.copy(
                    statusCode = statusCode,
                    responseHeaders = responseHeaders,
                    responseBody = responseBody,
                    duration = duration,
                    responseSize = responseBody?.length?.toLong(),
                    state = TrafficEntry.State.COMPLETE
                )
            }

            // Send response back to client
            clientOutput.write(responseBuilder.toString().toByteArray())
            clientOutput.flush()

            sslSocket.close()
            socket.close()
        } catch (e: Exception) {
            trafficRepository.updateEntry(entry.id) { existing ->
                existing.copy(state = TrafficEntry.State.FAILED)
            }
            e.printStackTrace()
        }
    }

    private fun tunnelConnection(clientSocket: Socket, host: String, port: Int) {
        try {
            val remoteSocket = Socket()
            vpnService?.protect(remoteSocket)
            remoteSocket.connect(InetSocketAddress(host, port))
            val t1 = Thread { relay(clientSocket.getInputStream(), remoteSocket.getOutputStream()) }
            val t2 = Thread { relay(remoteSocket.getInputStream(), clientSocket.getOutputStream()) }
            t1.start()
            t2.start()
            t1.join()
            t2.join()
            remoteSocket.close()
        } catch (_: Exception) {
        }
    }

    private fun relay(input: java.io.InputStream, output: OutputStream) {
        try {
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Exception) {
        }
    }

    private fun handleHttp(
        clientSocket: Socket,
        method: String,
        target: String,
        headers: Map<String, String>,
        requestBody: String?
    ) {
        var entry: TrafficEntry? = null
        try {
            val uri = URI(target)
            val host = uri.host ?: return
            val port = if (uri.port > 0) uri.port else 80
            val path = if (uri.rawQuery != null) "${uri.rawPath}?${uri.rawQuery}" else uri.rawPath ?: "/"

            entry = TrafficEntry(
                method = method,
                url = target,
                host = host,
                path = path,
                scheme = "http",
                requestHeaders = headers,
                requestBody = requestBody,
                isHttps = false
            )
            trafficRepository.addEntry(entry)

            val startTime = System.currentTimeMillis()
            val remoteSocket = Socket()
            vpnService?.protect(remoteSocket)
            remoteSocket.connect(InetSocketAddress(host, port))
            val serverOutput = remoteSocket.getOutputStream()

            // Forward request
            val requestBuilder = StringBuilder()
            requestBuilder.append("$method $path HTTP/1.1\r\n")
            requestBuilder.append("Host: $host\r\n")
            for ((key, value) in headers) {
                if (!key.equals("Host", ignoreCase = true) && !key.equals("Proxy-Connection", ignoreCase = true)) {
                    requestBuilder.append("$key: $value\r\n")
                }
            }
            requestBuilder.append("\r\n")
            serverOutput.write(requestBuilder.toString().toByteArray())
            if (requestBody != null) {
                serverOutput.write(requestBody.toByteArray())
            }
            serverOutput.flush()

            // Read response
            val serverReader = BufferedReader(InputStreamReader(remoteSocket.getInputStream()))
            val statusLine = serverReader.readLine() ?: return

            val statusParts = statusLine.split(" ", limit = 3)
            val statusCode = if (statusParts.size >= 2) statusParts[1].toIntOrNull() ?: 0 else 0

            val responseHeaders = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0
            val responseRaw = StringBuilder()
            responseRaw.append("$statusLine\r\n")

            while (serverReader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) {
                    responseRaw.append("\r\n")
                    break
                }
                responseRaw.append("$line\r\n")
                val colonIndex = line!!.indexOf(':')
                if (colonIndex > 0) {
                    val key = line!!.substring(0, colonIndex).trim()
                    val value = line!!.substring(colonIndex + 1).trim()
                    responseHeaders[key] = value
                    if (key.equals("Content-Length", ignoreCase = true)) {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            var responseBody: String? = null
            if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = serverReader.read(bodyChars, read, contentLength - read)
                    if (n == -1) break
                    read += n
                }
                responseBody = String(bodyChars, 0, read)
                responseRaw.append(responseBody)
            }

            val duration = System.currentTimeMillis() - startTime

            trafficRepository.updateEntry(entry.id) { existing ->
                existing.copy(
                    statusCode = statusCode,
                    responseHeaders = responseHeaders,
                    responseBody = responseBody,
                    duration = duration,
                    responseSize = responseBody?.length?.toLong(),
                    state = TrafficEntry.State.COMPLETE
                )
            }

            // Send response to client
            clientSocket.getOutputStream().write(responseRaw.toString().toByteArray())
            clientSocket.getOutputStream().flush()

            remoteSocket.close()
        } catch (e: Exception) {
            entry?.let { e ->
                trafficRepository.updateEntry(e.id) { existing ->
                    existing.copy(state = TrafficEntry.State.FAILED)
                }
            }
        }
    }
}
