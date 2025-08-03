package com.vyibc.autocr.ai

import org.slf4j.LoggerFactory

/**
 * AI供应商适配器接口
 */
interface AIProviderAdapter {
    /**
     * 发送请求到AI供应商
     */
    suspend fun sendRequest(prompt: String, context: AIRequestContext): AIResponse
    
    /**
     * 更新配置
     */
    fun updateConfig(config: AIModelConfig)
    
    /**
     * 获取请求计数
     */
    fun getRequestCount(): Long
    
    /**
     * 获取成功率
     */
    fun getSuccessRate(): Double
    
    /**
     * 获取平均响应时间
     */
    fun getAverageResponseTime(): Double
    
    /**
     * 获取总Token使用量
     */
    fun getTotalTokenUsage(): Long
    
    /**
     * 获取错误计数
     */
    fun getErrorCount(): Long
    
    /**
     * 健康检查
     */
    suspend fun healthCheck(): Boolean
}

/**
 * 抽象适配器基类
 */
abstract class BaseAIAdapter : AIProviderAdapter {
    protected val logger = LoggerFactory.getLogger(this::class.java)
    protected var config: AIModelConfig? = null
    
    // 统计信息
    @Volatile
    private var _requestCount: Long = 0
    @Volatile
    private var _successCount: Long = 0
    @Volatile
    private var _errorCount: Long = 0
    @Volatile
    private var _totalResponseTime: Long = 0
    @Volatile
    private var _totalTokenUsage: Long = 0
    
    override fun updateConfig(config: AIModelConfig) {
        this.config = config
        logger.info("Updated config for {}: model={}", config.provider, config.modelName)
    }
    
    override fun getRequestCount(): Long = _requestCount
    
    override fun getSuccessRate(): Double {
        return if (_requestCount > 0) _successCount.toDouble() / _requestCount else 0.0
    }
    
    override fun getAverageResponseTime(): Double {
        return if (_successCount > 0) _totalResponseTime.toDouble() / _successCount else 0.0
    }
    
    override fun getTotalTokenUsage(): Long = _totalTokenUsage
    
    override fun getErrorCount(): Long = _errorCount
    
    /**
     * 记录请求统计
     */
    protected fun recordRequest(success: Boolean, responseTime: Long, tokenUsage: TokenUsage?) {
        _requestCount++
        if (success) {
            _successCount++
            _totalResponseTime += responseTime
            tokenUsage?.let { _totalTokenUsage += it.totalTokens }
        } else {
            _errorCount++
        }
    }
}

/**
 * OpenAI适配器实现
 */
class OpenAIAdapter : BaseAIAdapter() {
    
    override suspend fun sendRequest(prompt: String, context: AIRequestContext): AIResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 模拟OpenAI API调用
            logger.info("Sending request to OpenAI: {}", context.requestId)
            
            // 这里应该实现实际的OpenAI API调用
            // 目前返回模拟响应
            val mockResponse = generateMockResponse(prompt, context, AIProvider.OPENAI)
            val responseTime = System.currentTimeMillis() - startTime
            
            recordRequest(true, responseTime, mockResponse.tokenUsage)
            mockResponse.copy(responseTime = responseTime)
            
        } catch (e: Exception) {
            logger.error("OpenAI request failed: {}", context.requestId, e)
            val responseTime = System.currentTimeMillis() - startTime
            recordRequest(false, responseTime, null)
            
            AIResponse(
                requestId = context.requestId,
                provider = AIProvider.OPENAI,
                model = config?.modelName ?: "gpt-4",
                content = "",
                success = false,
                errorMessage = e.message,
                responseTime = responseTime
            )
        }
    }
    
    override suspend fun healthCheck(): Boolean {
        return try {
            // 实际应该发送一个简单的健康检查请求
            true
        } catch (e: Exception) {
            logger.warn("OpenAI health check failed", e)
            false
        }
    }
}

/**
 * Anthropic适配器实现
 */
class AnthropicAdapter : BaseAIAdapter() {
    
    override suspend fun sendRequest(prompt: String, context: AIRequestContext): AIResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            logger.info("Sending request to Anthropic: {}", context.requestId)
            
            // 模拟Anthropic API调用
            val mockResponse = generateMockResponse(prompt, context, AIProvider.ANTHROPIC)
            val responseTime = System.currentTimeMillis() - startTime
            
            recordRequest(true, responseTime, mockResponse.tokenUsage)
            mockResponse.copy(responseTime = responseTime)
            
        } catch (e: Exception) {
            logger.error("Anthropic request failed: {}", context.requestId, e)
            val responseTime = System.currentTimeMillis() - startTime
            recordRequest(false, responseTime, null)
            
            AIResponse(
                requestId = context.requestId,
                provider = AIProvider.ANTHROPIC,
                model = config?.modelName ?: "claude-3-sonnet",
                content = "",
                success = false,
                errorMessage = e.message,
                responseTime = responseTime
            )
        }
    }
    
    override suspend fun healthCheck(): Boolean {
        return try {
            true
        } catch (e: Exception) {
            logger.warn("Anthropic health check failed", e)
            false
        }
    }
}

/**
 * Google AI适配器实现
 */
class GoogleAIAdapter : BaseAIAdapter() {
    
    override suspend fun sendRequest(prompt: String, context: AIRequestContext): AIResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            logger.info("Sending request to Google AI: {}", context.requestId)
            
            val mockResponse = generateMockResponse(prompt, context, AIProvider.GOOGLE)
            val responseTime = System.currentTimeMillis() - startTime
            
            recordRequest(true, responseTime, mockResponse.tokenUsage)
            mockResponse.copy(responseTime = responseTime)
            
        } catch (e: Exception) {
            logger.error("Google AI request failed: {}", context.requestId, e)
            val responseTime = System.currentTimeMillis() - startTime
            recordRequest(false, responseTime, null)
            
            AIResponse(
                requestId = context.requestId,
                provider = AIProvider.GOOGLE,
                model = config?.modelName ?: "gemini-pro",
                content = "",
                success = false,
                errorMessage = e.message,
                responseTime = responseTime
            )
        }
    }
    
    override suspend fun healthCheck(): Boolean {
        return try {
            true
        } catch (e: Exception) {
            logger.warn("Google AI health check failed", e)
            false
        }
    }
}

/**
 * Ollama适配器实现（本地模型）
 */
class OllamaAdapter : BaseAIAdapter() {
    
    override suspend fun sendRequest(prompt: String, context: AIRequestContext): AIResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            logger.info("Sending request to Ollama: {}", context.requestId)
            
            val mockResponse = generateMockResponse(prompt, context, AIProvider.OLLAMA)
            val responseTime = System.currentTimeMillis() - startTime
            
            recordRequest(true, responseTime, mockResponse.tokenUsage)
            mockResponse.copy(responseTime = responseTime)
            
        } catch (e: Exception) {
            logger.error("Ollama request failed: {}", context.requestId, e)
            val responseTime = System.currentTimeMillis() - startTime
            recordRequest(false, responseTime, null)
            
            AIResponse(
                requestId = context.requestId,
                provider = AIProvider.OLLAMA,
                model = config?.modelName ?: "codellama",
                content = "",
                success = false,
                errorMessage = e.message,
                responseTime = responseTime
            )
        }
    }
    
    override suspend fun healthCheck(): Boolean {
        return try {
            // 检查Ollama服务是否运行
            true
        } catch (e: Exception) {
            logger.warn("Ollama health check failed", e)
            false
        }
    }
}

/**
 * Azure OpenAI适配器实现
 */
class AzureOpenAIAdapter : BaseAIAdapter() {
    
    override suspend fun sendRequest(prompt: String, context: AIRequestContext): AIResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            logger.info("Sending request to Azure OpenAI: {}", context.requestId)
            
            val mockResponse = generateMockResponse(prompt, context, AIProvider.AZURE_OPENAI)
            val responseTime = System.currentTimeMillis() - startTime
            
            recordRequest(true, responseTime, mockResponse.tokenUsage)
            mockResponse.copy(responseTime = responseTime)
            
        } catch (e: Exception) {
            logger.error("Azure OpenAI request failed: {}", context.requestId, e)
            val responseTime = System.currentTimeMillis() - startTime
            recordRequest(false, responseTime, null)
            
            AIResponse(
                requestId = context.requestId,
                provider = AIProvider.AZURE_OPENAI,
                model = config?.modelName ?: "gpt-4",
                content = "",
                success = false,
                errorMessage = e.message,
                responseTime = responseTime
            )
        }
    }
    
    override suspend fun healthCheck(): Boolean {
        return try {
            true
        } catch (e: Exception) {
            logger.warn("Azure OpenAI health check failed", e)
            false
        }
    }
}

/**
 * 生成模拟响应（用于测试和开发）
 */
private fun generateMockResponse(prompt: String, context: AIRequestContext, provider: AIProvider): AIResponse {
    val mockContent = when (context.requestType) {
        AIRequestType.CODE_REVIEW -> """
            ## 代码评审报告
            
            ### 总体评价: 8/10
            
            ### 主要发现:
            1. **代码质量**: 良好，遵循了大部分最佳实践
            2. **安全性**: 需要注意输入验证
            3. **性能**: 整体良好，建议优化数据库查询
            4. **可维护性**: 代码结构清晰，注释充分
            
            ### 具体建议:
            - 添加输入参数验证
            - 优化循环逻辑
            - 考虑使用缓存机制
            - 增加单元测试覆盖率
        """.trimIndent()
        
        AIRequestType.SECURITY_ANALYSIS -> """
            ## 安全分析报告
            
            ### 安全风险评级: 6/10
            
            ### 发现的安全问题:
            1. **SQL注入风险**: 中等 - 建议使用参数化查询
            2. **输入验证**: 缺失 - 需要添加输入清理
            3. **权限控制**: 良好 - 已正确实现
            4. **敏感数据**: 注意 - 避免日志中包含敏感信息
            
            ### 安全建议:
            - 实施输入验证和清理
            - 使用预编译SQL语句
            - 加强错误处理
            - 定期安全审计
        """.trimIndent()
        
        AIRequestType.PERFORMANCE_ANALYSIS -> """
            ## 性能分析报告
            
            ### 性能风险评级: 7/10
            
            ### 性能问题:
            1. **复杂度**: 部分方法复杂度较高
            2. **数据库查询**: 可以优化
            3. **内存使用**: 整体良好
            4. **并发性**: 需要考虑线程安全
            
            ### 优化建议:
            - 简化复杂算法
            - 使用批量操作
            - 实施缓存策略
            - 优化数据库索引
        """.trimIndent()
        
        else -> "AI分析结果: 已完成${context.requestType}分析"
    }
    
    return AIResponse(
        requestId = context.requestId,
        provider = provider,
        model = "mock-model",
        content = mockContent,
        confidence = 0.85,
        tokenUsage = TokenUsage(
            promptTokens = prompt.length / 4, // 粗略估算
            completionTokens = mockContent.length / 4,
            totalTokens = (prompt.length + mockContent.length) / 4
        ),
        success = true,
        responseTime = (100..500).random().toLong() // 模拟响应时间
    )
}