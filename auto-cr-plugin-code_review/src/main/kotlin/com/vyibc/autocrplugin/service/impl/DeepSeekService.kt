package com.vyibc.autocrplugin.service.impl

import com.vyibc.autocrplugin.service.AIService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * DeepSeek AI服务实现
 */
class DeepSeekService(
    private val apiKey: String = "",
    private val apiEndpoint: String = "https://api.deepseek.com/v1/chat/completions"
) : AIService {

    private val gson = Gson()

    override suspend fun callAI(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL(apiEndpoint)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            
            val requestBody = createRequestBody(prompt)
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = reader.readText()
                    parseResponse(response)
                }
            } else {
                val errorStream = connection.errorStream
                val errorMessage = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else {
                    "HTTP Error: $responseCode"
                }
                throw Exception("DeepSeek API调用失败: $errorMessage")
            }
        } catch (e: Exception) {
            throw Exception("DeepSeek服务调用失败: ${e.message}")
        }
    }

    private fun createRequestBody(prompt: String): String {
        val requestJson = JsonObject().apply {
            addProperty("model", "deepseek-coder")
            add("messages", gson.toJsonTree(listOf(
                mapOf(
                    "role" to "system",
                    "content" to "你是一个专业的代码评估专家，请对提供的代码进行详细的质量评估。"
                ),
                mapOf(
                    "role" to "user", 
                    "content" to prompt
                )
            )))
            addProperty("max_tokens", 2000)
            addProperty("temperature", 0.1)
            addProperty("stream", false)
        }
        return gson.toJson(requestJson)
    }

    private fun parseResponse(response: String): String {
        try {
            val jsonResponse = gson.fromJson(response, JsonObject::class.java)
            val choices = jsonResponse.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val firstChoice = choices[0].asJsonObject
                val message = firstChoice.getAsJsonObject("message")
                return message.get("content").asString
            }
            throw Exception("无效的响应格式")
        } catch (e: Exception) {
            throw Exception("解析DeepSeek响应失败: ${e.message}")
        }
    }

    override fun getServiceName(): String = "DeepSeek"

    override suspend fun isAvailable(): Boolean {
        return try {
            if (apiKey.isBlank()) {
                false
            } else {
                // 发送一个简单的测试请求
                callAI("测试连接")
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
