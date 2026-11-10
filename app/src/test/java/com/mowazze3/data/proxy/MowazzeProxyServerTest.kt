package com.mowazze3.data.proxy

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MowazzeProxyServerTest {

    @Test
    fun `server starts and serves PAC auto config`() {
        val server = MowazzeProxyServer(port = 0) // dynamic port
        val port = server.start()
        assertTrue(port > 0)

        try {
            Socket("127.0.0.1", port).use { socket ->
                val writer = OutputStreamWriter(socket.getOutputStream())
                writer.write("GET /proxy.pac HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                writer.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val statusLine = reader.readLine()
                assertEquals("HTTP/1.1 200 OK", statusLine)

                val response = StringBuilder()
                var line = reader.readLine()
                while (line != null) {
                    response.append(line).append("\n")
                    line = reader.readLine()
                }

                assertTrue(response.contains("FindProxyForURL"))
                assertTrue(response.contains("PROXY 192.168.49.1:$port"))
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `stop terminates the server cleanly`() {
        val server = MowazzeProxyServer(port = 0)
        val port = server.start()
        assertTrue(port > 0)
        server.stop()

        // Starting again after stop should work cleanly
        val newPort = server.start()
        assertTrue(newPort > 0)
        server.stop()
    }
}
