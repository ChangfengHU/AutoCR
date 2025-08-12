package com.vyibc.autocrplugin.service

/**
 * AI服务接口
 */
interface AIService {
    /**
     * 调用AI服务进行代码评估
     * @param prompt 评估提示词
     * @return AI响应结果
     */
    suspend fun callAI(prompt: String): String
    
    /**
     * 获取服务名称
     */
    fun getServiceName(): String
    
    /**
     * 检查服务是否可用
     */
    suspend fun isAvailable(): Boolean
}

/**
 * AI服务配置
 */
data class AIServiceConfig(
    val serviceName: String,
    val apiKey: String,
    val apiEndpoint: String,
    val model: String? = null,
    val enabled: Boolean = true
)

/**
 * AI服务类型枚举
 */
enum class AIServiceType(val displayName: String) {
    DEEPSEEK("DeepSeek"),
    TONGYI("阿里通义千问"),
    GEMINI("Google Gemini"),
    OPENAI("OpenAI GPT")
}
