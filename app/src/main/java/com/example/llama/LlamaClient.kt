package com.example.llama

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class LlamaClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun sendVisionRequest(userMessage: String, imagePath: String?, systemPrompt: String? = null): String {
        val messages = JSONArray()

        systemPrompt?.let {
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", it)
            })
        }

        val userContent = if (imagePath != null) {
            val imageFile = File(imagePath)
            val base64 = android.util.Base64.encodeToString(imageFile.readBytes(), android.util.Base64.NO_WRAP)
            val imageUrl = "data:image/jpeg;base64,$base64"
            JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", userMessage)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", imageUrl)
                    })
                })
            }
        } else {
            userMessage
        }

        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userContent)
        })

        val requestBody = JSONObject().apply {
            put("messages", messages)
            put("stream", false)
            put("temperature", 0.7)
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return "Error: empty response"
            return try {
                JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } catch (e: Exception) {
                "Error parsing response: $body"
            }
        }
    }
}