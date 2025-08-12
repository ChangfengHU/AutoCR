package com.vyibc.autocrplugin.service.impl

import com.vyibc.autocrplugin.service.AIService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Google Gemini AI服务实现
 */
class GeminiService(
    private val apiKey: String = "",
    private val model: String = "gemini-pro"
) : AIService {

    private val gson = Gson()
    private val apiEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    override suspend fun callAI(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("$apiEndpoint?key=${URLEncoder.encode(apiKey, "UTF-8")}")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
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
                throw Exception("Gemini API调用失败: $errorMessage")
            }
        } catch (e: Exception) {
            throw Exception("Gemini服务调用失败: ${e.message}")
        }
    }

    private fun createRequestBody(prompt: String): String {
        val systemPrompt = """
            你是一个专业的代码评估专家，请对提供的代码进行详细的质量评估。
            请从以下维度进行分析：
            1. 代码风格和规范
            2. 性能问题
            3. 安全风险
            4. 潜在Bug
            5. 可维护性
            6. 最佳实践
            
            请以JSON格式返回评估结果。
        """.trimIndent()
        
        val fullPrompt = "$systemPrompt\n\n$prompt"
        
        val requestJson = JsonObject().apply {
            add("contents", gson.toJsonTree(listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to fullPrompt)
                    )
                )
            )))
            
            val generationConfig = JsonObject().apply {
                addProperty("temperature", 0.1)
                addProperty("topK", 32)
                addProperty("topP", 1.0)
                addProperty("maxOutputTokens", 2048)
            }
            add("generationConfig", generationConfig)
            
            add("safetySettings", gson.toJsonTree(listOf(
                mapOf(
                    "category" to "HARM_CATEGORY_HARASSMENT",
                    "threshold" to "BLOCK_MEDIUM_AND_ABOVE"
                ),
                mapOf(
                    "category" to "HARM_CATEGORY_HATE_SPEECH",
                    "threshold" to "BLOCK_MEDIUM_AND_ABOVE"
                ),
                mapOf(
                    "category" to "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "threshold" to "BLOCK_MEDIUM_AND_ABOVE"
                ),
                mapOf(
                    "category" to "HARM_CATEGORY_DANGEROUS_CONTENT",
                    "threshold" to "BLOCK_MEDIUM_AND_ABOVE"
                )
            )))
        }
        return gson.toJson(requestJson)
    }

    private fun parseResponse(response: String): String {
        try {
            val jsonResponse = gson.fromJson(response, JsonObject::class.java)
            val candidates = jsonResponse.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val firstCandidate = candidates[0].asJsonObject
                val content = firstCandidate.getAsJsonObject("content")
                val parts = content.getAsJsonArray("parts")
                if (parts != null && parts.size() > 0) {
                    val firstPart = parts[0].asJsonObject
                    return firstPart.get("text").asString
                }
            }
            throw Exception("无效的响应格式")
        } catch (e: Exception) {
            throw Exception("解析Gemini响应失败: ${e.message}")
        }
    }

    override fun getServiceName(): String = "Google Gemini"

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
