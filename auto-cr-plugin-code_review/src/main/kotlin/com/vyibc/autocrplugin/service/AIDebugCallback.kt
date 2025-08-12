package com.vyibc.autocrplugin.service

/**
 * AI调试回调接口
 * 用于在AI分析过程中传递详细的调试信息到UI
 */
interface AIDebugCallback {
    
    /**
     * 记录AI请求信息
     */
    fun onAIRequest(serviceName: String, prompt: String, requestTime: String)
    
    /**
     * 记录AI原始响应
     */
    fun onAIResponse(response: String, responseTime: String)
    
    /**
     * 记录解析过程
     */
    fun onParsingStep(step: String, details: String)
    
    /**
     * 记录解析结果
     */
    fun onParsingResult(success: Boolean, result: CodeReviewResult?, error: String?)
}

/**
 * AI调试信息数据类
 */
data class AIDebugInfo(
    val serviceName: String,
    val requestTime: String,
    val responseTime: String,
    val promptLength: Int,
    val responseLength: Int,
    val prompt: String,
    val response: String,
    val parseSuccess: Boolean,
    val parseError: String?,
    val result: CodeReviewResult?
)
