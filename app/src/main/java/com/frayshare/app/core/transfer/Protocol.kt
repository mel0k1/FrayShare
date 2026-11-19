package com.frayshare.app.core.transfer

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

object Protocol {
    const val PORT = 47470
    const val DISCOVERY_PORT = 47471
    const val CHUNK = 64 * 1024

    fun input(socket: Socket): DataInputStream =
        DataInputStream(BufferedInputStream(socket.getInputStream(), CHUNK))

    fun output(socket: Socket): DataOutputStream =
        DataOutputStream(BufferedOutputStream(socket.getOutputStream(), CHUNK))

    fun readMessage(input: DataInputStream): JSONObject {
        val length = input.readInt()
        require(length in 1..(1 shl 24)) { "bad message length $length" }
        val buffer = ByteArray(length)
        input.readFully(buffer)
        return JSONObject(String(buffer, Charsets.UTF_8))
    }

    fun writeMessage(output: DataOutputStream, message: JSONObject) {
        val bytes = message.toString().toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }
}
