package com.vyibc.autocrplugin.service.impl

import com.vyibc.autocrplugin.service.TranslationResult
import com.vyibc.autocrplugin.service.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser

/**
 * Google翻译服务实现
 * 使用Google Translate的免费API
 */
class GoogleTranslationService : TranslationService {

    private val gson = Gson()

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String?
    ): TranslationResult = withContext(Dispatchers.IO) {
        try {
            val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
            val sourceLang = sourceLanguage ?: "auto"
            
            val urlString = "https://translate.googleapis.com/translate_a/single?" +
                    "client=gtx&sl=$sourceLang&tl=$targetLanguage&dt=t&q=$encodedText"
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                
                parseGoogleTranslateResponse(response, text, sourceLang, targetLanguage)
            } else {
                throw Exception("翻译请求失败，响应码: $responseCode")
            }
        } catch (e: Exception) {
            throw Exception("翻译失败: ${e.message}")
        }
    }

    private fun parseGoogleTranslateResponse(
        response: String,
        originalText: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult {
        try {
            val jsonArray = JsonParser.parseString(response).asJsonArray
            val translationsArray = jsonArray[0].asJsonArray
            
            val translatedText = StringBuilder()
            for (i in 0 until translationsArray.size()) {
                val translation = translationsArray[i].asJsonArray
                translatedText.append(translation[0].asString)
            }
            
            // 尝试获取检测到的源语言
            val detectedSourceLang = try {
                jsonArray[2].asString
            } catch (e: Exception) {
                sourceLanguage
            }
            
            return TranslationResult(
                originalText = originalText,
                translatedText = translatedText.toString(),
                sourceLanguage = detectedSourceLang,
                targetLanguage = targetLanguage,
                service = getServiceName()
            )
        } catch (e: Exception) {
            throw Exception("解析翻译结果失败: ${e.message}")
        }
    }

    override fun getSupportedLanguages(): Map<String, String> {
        return mapOf(
            "auto" to "自动检测",
            "zh" to "中文",
            "en" to "英语",
            "ja" to "日语",
            "ko" to "韩语",
            "fr" to "法语",
            "de" to "德语",
            "es" to "西班牙语",
            "ru" to "俄语",
            "it" to "意大利语",
            "pt" to "葡萄牙语",
            "ar" to "阿拉伯语",
            "hi" to "印地语",
            "th" to "泰语",
            "vi" to "越南语"
        )
    }

    override fun getServiceName(): String = "Google Translate"
}
