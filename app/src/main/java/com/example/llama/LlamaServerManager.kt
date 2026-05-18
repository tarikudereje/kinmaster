package com.example.llama

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader

class LlamaServerManager(private val context: Context) {

    private var serverProcess: Process? = null
    val port = 8080
    val baseUrl = "http://127.0.0.1:$port/v1"

    suspend fun startServer(modelPath: String, mmprojPath: String): Boolean = withContext(Dispatchers.IO) {
        // Use cache directory (execution is allowed here)
        val cacheDir = context.cacheDir
        val serverFile = File(cacheDir, "llama-server")

        // Copy binary from assets if missing
        if (!serverFile.exists()) {
            try {
                context.assets.open("bin/llama-server").use { input ->
                    FileOutputStream(serverFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("LlamaServer", "Binary copied to ${serverFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("LlamaServer", "Failed to copy binary", e)
                return@withContext false
            }
        }

        // Set execute permission (for all users)
        serverFile.setExecutable(true, false)

        // Also run chmod 755 to ensure execution rights
        try {
            val chmod = Runtime.getRuntime().exec(arrayOf("chmod", "755", serverFile.absolutePath))
            chmod.waitFor()
            Log.d("LlamaServer", "chmod exit code: ${chmod.exitValue()}")
        } catch (e: Exception) {
            Log.e("LlamaServer", "chmod failed", e)
        }

        if (!serverFile.canExecute()) {
            Log.e("LlamaServer", "Binary still not executable")
            return@withContext false
        }

        val cmd = listOf(
            serverFile.absolutePath,
            "-m", modelPath,
            "--mmproj", mmprojPath,
            "--port", port.toString(),
            "-c", "2048",
            "--temp", "0.7"
        )
        Log.d("LlamaServer", "Starting: ${cmd.joinToString(" ")}")

        return@withContext try {
            val process = ProcessBuilder(cmd)
                .directory(cacheDir)
                .redirectErrorStream(true)
                .start()

            // Read output for debugging
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val thread = Thread {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { Log.d("LlamaServer", it) }
                }
            }
            thread.start()

            // Wait 3 seconds for server to start
            Thread.sleep(3000)

            if (process.isAlive) {
                serverProcess = process
                Log.i("LlamaServer", "Server started successfully")
                true
            } else {
                val exitCode = process.exitValue()
                Log.e("LlamaServer", "Process exited with code $exitCode")
                false
            }
        } catch (e: Exception) {
            Log.e("LlamaServer", "Exception", e)
            false
        }
    }

    fun stopServer() {
        serverProcess?.destroy()
        serverProcess = null
    }
}