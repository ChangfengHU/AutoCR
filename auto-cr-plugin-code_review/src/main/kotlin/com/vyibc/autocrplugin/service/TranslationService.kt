package com.vyibc.autocrplugin.service

/**
 * 翻译结果数据类
 */
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val service: String
)

/**
 * 翻译服务接口
 */
interface TranslationService {
    /**
     * 翻译文本
     * @param text 要翻译的文本
     * @param targetLanguage 目标语言代码 (如: "zh", "en", "ja")
     * @param sourceLanguage 源语言代码，null表示自动检测
     * @return 翻译结果
     */
    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String? = null
    ): TranslationResult

    /**
     * 获取支持的语言列表
     * @return 语言代码到语言名称的映射
     */
    fun getSupportedLanguages(): Map<String, String>

    /**
     * 获取服务名称
     */
    fun getServiceName(): String
}
