package com.mowazze3.data.proxy

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MowazzeProxy"
private const val BUFFER_SIZE = 16 * 1024
private const val DEFAULT_PORT = 8245
private const val SOCKET_TIMEOUT_MILLIS = 30_000

/**
 * Lightweight embedded HTTP/HTTPS proxy server that routes traffic from connected Wi-Fi Direct clients
 * to the phone's active upstream connection (e.g. Wi-Fi).
 */
class MowazzeProxyServer(
    val port: Int = DEFAULT_PORT,
    private val bindAddress: InetAddress? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    private val _totalBytes = AtomicLong(0L)
    val totalBytes: Long get() = _totalBytes.get()

    fun start(): Int {
        if (running.getAndSet(true)) return serverSocket?.localPort ?: port
        try {
            val server = ServerSocket()
            server.reuseAddress = true
            val address = if (bindAddress != null) InetSocketAddress(bindAddress, port) else InetSocketAddress(port)
            server.bind(address)
            serverSocket = server
            val actualPort = server.localPort

            scope.launch {
                while (isActive && running.get()) {
                    try {
                        val clientSocket = server.accept()
                        clientSocket.soTimeout = SOCKET_TIMEOUT_MILLIS
                        activeSockets.add(clientSocket)
                        launch {
                            try {
                                handleClient(clientSocket)
                            } catch (e: Exception) {
                                if (running.get() && e !is SocketTimeoutException) {
                                    Log.d(TAG, "Connection error: ${e.message}")
                                }
                            } finally {
                                closeQuietly(clientSocket)
                                activeSockets.remove(clientSocket)
                            }
                        }
                    } catch (e: Exception) {
                        if (!running.get()) break
                        Log.w(TAG, "Server accept error", e)
                    }
                }
            }
            Log.i(TAG, "Proxy server listening on port $actualPort")
            return actualPort
        } catch (e: Exception) {
            running.set(false)
            Log.e(TAG, "Failed to start proxy server on port $port", e)
            throw e
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        activeSockets.forEach { closeQuietly(it) }
        activeSockets.clear()
        scope.cancel()
        Log.i(TAG, "Proxy server stopped")
    }

    private fun handleClient(client: Socket) {
        val clientIn = client.getInputStream()
        val clientOut = client.getOutputStream()

        val initialLine = readLine(clientIn) ?: return
        val parts = initialLine.trim().split(" ")
        if (parts.size < 2) return

        val method = parts[0].uppercase()
        val targetUri = parts[1]

        // Serve PAC auto-config file if requested
        if (method == "GET" && (targetUri.endsWith("/proxy.pac") || targetUri.endsWith("/pac"))) {
            servePacFile(clientOut)
            return
        }

        if (method == "CONNECT") {
            handleConnect(client, clientIn, clientOut, targetUri)
        } else {
            handlePlainHttp(client, clientIn, clientOut, initialLine, targetUri)
        }
    }

    /** HTTPS tunneling through HTTP CONNECT */
    private fun handleConnect(client: Socket, clientIn: InputStream, clientOut: OutputStream, targetUri: String) {
        val hostPort = targetUri.split(":")
        val host = hostPort[0]
        val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

        // Consume all remaining client headers until the blank line
        while (true) {
            val line = readLine(clientIn) ?: break
            if (line.isEmpty() || line == "\r") break
        }

        val targetSocket = try {
            Socket().apply {
                soTimeout = SOCKET_TIMEOUT_MILLIS
                connect(InetSocketAddress(host, port), 10_000)
            }
        } catch (e: Exception) {
            clientOut.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n".toByteArray())
            clientOut.flush()
            return
        }

        activeSockets.add(targetSocket)
        try {
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            clientOut.flush()

            // Bi-directional relay between client and target
            relayBidirectional(client, targetSocket)
        } finally {
            closeQuietly(targetSocket)
            activeSockets.remove(targetSocket)
        }
    }

    /** Plain HTTP proxy forwarding */
    private fun handlePlainHttp(
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        initialLine: String,
        targetUri: String,
    ) {
        var host = ""
        var port = 80
        val headers = mutableListOf<String>()

        if (targetUri.startsWith("http://", ignoreCase = true)) {
            val noScheme = targetUri.substring(7)
            val hostPart = noScheme.substringBefore("/")
            if (":" in hostPart) {
                host = hostPart.substringBefore(":")
                port = hostPart.substringAfter(":").toIntOrNull() ?: 80
            } else {
                host = hostPart
            }
        }

        while (true) {
            val line = readLine(clientIn) ?: break
            if (line.isEmpty() || line == "\r") break
            if (host.isEmpty() && line.startsWith("Host:", ignoreCase = true)) {
                val hostHeader = line.substringAfter(":").trim()
                if (":" in hostHeader) {
                    host = hostHeader.substringBefore(":")
                    port = hostHeader.substringAfter(":").toIntOrNull() ?: 80
                } else {
                    host = hostHeader
                }
            }
            // Strip proxy-connection header
            if (!line.startsWith("Proxy-Connection:", ignoreCase = true)) {
                headers.add(line)
            }
        }

        if (host.isEmpty()) return

        val targetSocket = try {
            Socket().apply {
                soTimeout = SOCKET_TIMEOUT_MILLIS
                connect(InetSocketAddress(host, port), 10_000)
            }
        } catch (_: Exception) {
            clientOut.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n".toByteArray())
            clientOut.flush()
            return
        }

        activeSockets.add(targetSocket)
        try {
            val targetOut = targetSocket.getOutputStream()
            val rewrittenPath = if (targetUri.startsWith("http://", ignoreCase = true)) {
                val path = targetUri.substring(7).substringAfter("/", "/")
                if (path.startsWith("/")) path else "/$path"
            } else {
                targetUri
            }

            val requestBuilder = StringBuilder()
            requestBuilder.append(initialLine.substringBefore(" ")).append(" ").append(rewrittenPath).append(" HTTP/1.1\r\n")
            for (header in headers) {
                requestBuilder.append(header).append("\r\n")
            }
            requestBuilder.append("Connection: close\r\n\r\n")

            val headerBytes = requestBuilder.toString().toByteArray()
            targetOut.write(headerBytes)
            targetOut.flush()
            _totalBytes.addAndGet(headerBytes.size.toLong())

            relayBidirectional(client, targetSocket)
        } finally {
            closeQuietly(targetSocket)
            activeSockets.remove(targetSocket)
        }
    }

    private fun relayBidirectional(s1: Socket, s2: Socket) {
        val job1 = scope.launch { pipe(s1.getInputStream(), s2.getOutputStream()) }
        val job2 = scope.launch { pipe(s2.getInputStream(), s1.getOutputStream()) }
        // Wait until one stream ends, then close sockets
        while (running.get() && job1.isActive && job2.isActive) {
            Thread.sleep(100)
        }
        job1.cancel()
        job2.cancel()
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            while (running.get()) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                output.flush()
                _totalBytes.addAndGet(read.toLong())
            }
        } catch (_: Exception) {}
    }

    private fun servePacFile(clientOut: OutputStream) {
        val actualPort = serverSocket?.localPort ?: port
        val pacContent = """
            function FindProxyForURL(url, host) {
                return "PROXY 192.168.49.1:$actualPort; DIRECT";
            }
        """.trimIndent()

        val response = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/x-ns-proxy-autoconfig\r\n" +
            "Content-Length: ${pacContent.toByteArray().size}\r\n" +
            "Connection: close\r\n\r\n" +
            pacContent

        clientOut.write(response.toByteArray())
        clientOut.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sb.isNotEmpty()) sb.toString() else null
            }
            val c = b.toChar()
            if (c == '\n') break
            if (c != '\r') sb.append(c)
        }
        return sb.toString()
    }

    private fun closeQuietly(socket: Socket?) {
        try {
            socket?.close()
        } catch (_: Exception) {}
    }
}
