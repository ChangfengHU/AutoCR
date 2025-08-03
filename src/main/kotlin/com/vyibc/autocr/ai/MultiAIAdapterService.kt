package com.vyibc.autocr.ai

import com.vyibc.autocr.model.*
import com.vyibc.autocr.preprocessor.DualFlowAnalysisReport
import com.vyibc.autocr.cache.CacheService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.slf4j.LoggerFactory

/**
 * AI供应商类型
 */
enum class AIProvider {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    OLLAMA,
    AZURE_OPENAI,
    ALIBABA_TONGYI,  // 阿里通义千问
    DEEPSEEK         // DeepSeek
}

/**
 * AI模型配置
 */
data class AIModelConfig(
    val provider: AIProvider,
    val modelName: String,
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val maxTokens: Int = 4000,
    val temperature: Double = 0.1,
    val timeout: Long = 30000L, // 30秒超时
    val enabled: Boolean = true
)

/**
 * AI请求上下文
 */
data class AIRequestContext(
    val requestId: String,
    val requestType: AIRequestType,
    val priority: Int = 0, // 0-10, 10为最高优先级
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val cacheEnabled: Boolean = true
)

/**
 * AI请求类型
 */
enum class AIRequestType {
    CODE_REVIEW,           // 代码评审
    SECURITY_ANALYSIS,     // 安全分析
    PERFORMANCE_ANALYSIS,  // 性能分析
    ARCHITECTURE_REVIEW,   // 架构评审
    BEST_PRACTICES,       // 最佳实践建议
    BUG_DETECTION,        // 缺陷检测
    CODE_QUALITY          // 代码质量评估
}

/**
 * AI响应
 */
data class AIResponse(
    val requestId: String,
    val provider: AIProvider,
    val model: String,
    val content: String,
    val confidence: Double = 0.0,
    val tokenUsage: TokenUsage? = null,
    val responseTime: Long,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Token使用统计
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * 多AI供应商适配器服务
 * 管理多个AI提供商的统一接口和智能调度
 */
@Service(Service.Level.PROJECT)
class MultiAIAdapterService(private val project: Project) {
    private val logger = LoggerFactory.getLogger(MultiAIAdapterService::class.java)
    private val cacheService = CacheService.getInstance(project)
    
    // AI供应商适配器映射
    private val adapters = mutableMapOf<AIProvider, AIProviderAdapter>()
    
    // AI模型配置
    private val modelConfigs = mutableMapOf<AIProvider, AIModelConfig>()
    
    // 负载均衡和故障转移
    private val loadBalancer = AILoadBalancer()
    
    init {
        initializeAdapters()
        loadConfigurations()
    }
    
    /**
     * 执行代码评审
     */
    suspend fun performCodeReview(
        analysisReport: DualFlowAnalysisReport,
        context: AIRequestContext = AIRequestContext(
            requestId = generateRequestId(),
            requestType = AIRequestType.CODE_REVIEW
        )
    ): AIResponse {
        logger.info("Performing code review with request ID: {}", context.requestId)
        
        // 检查缓存
        if (context.cacheEnabled) {
            val cacheKey = "code_review_${analysisReport.hashCode()}"
            // 简化版本，暂时跳过缓存
        }
        
        // 构建评审提示
        val prompt = buildCodeReviewPrompt(analysisReport)
        
        // 选择最佳AI供应商
        val selectedProvider = loadBalancer.selectProvider(
            context.requestType,
            context.priority,
            getAvailableProviders()
        )
        
        return executeAIRequest(selectedProvider, prompt, context)
    }
    
    /**
     * 执行安全分析
     */
    suspend fun performSecurityAnalysis(
        methods: List<MethodNode>,
        context: AIRequestContext = AIRequestContext(
            requestId = generateRequestId(),
            requestType = AIRequestType.SECURITY_ANALYSIS
        )
    ): AIResponse {
        logger.info("Performing security analysis with request ID: {}", context.requestId)
        
        val prompt = buildSecurityAnalysisPrompt(methods)
        val selectedProvider = loadBalancer.selectProvider(
            context.requestType,
            context.priority,
            getAvailableProviders()
        )
        
        return executeAIRequest(selectedProvider, prompt, context)
    }
    
    /**
     * 执行性能分析
     */
    suspend fun performPerformanceAnalysis(
        methods: List<MethodNode>,
        context: AIRequestContext = AIRequestContext(
            requestId = generateRequestId(),
            requestType = AIRequestType.PERFORMANCE_ANALYSIS
        )
    ): AIResponse {
        logger.info("Performing performance analysis with request ID: {}", context.requestId)
        
        val prompt = buildPerformanceAnalysisPrompt(methods)
        val selectedProvider = loadBalancer.selectProvider(
            context.requestType,
            context.priority,
            getAvailableProviders()
        )
        
        return executeAIRequest(selectedProvider, prompt, context)
    }
    
    /**
     * 批量AI请求处理
     */
    suspend fun batchProcessRequests(
        requests: List<BatchAIRequest>
    ): List<AIResponse> {
        logger.info("Processing {} batch AI requests", requests.size)
        
        return requests.mapIndexed { index, request ->
            try {
                val context = AIRequestContext(
                    requestId = "${generateRequestId()}_batch_$index",
                    requestType = request.requestType,
                    priority = request.priority
                )
                
                when (request.requestType) {
                    AIRequestType.CODE_REVIEW -> {
                        request.analysisReport?.let { performCodeReview(it, context) }
                            ?: createErrorResponse(context.requestId, "Missing analysis report for code review")
                    }
                    AIRequestType.SECURITY_ANALYSIS -> {
                        request.methods?.let { performSecurityAnalysis(it, context) }
                            ?: createErrorResponse(context.requestId, "Missing methods for security analysis")
                    }
                    AIRequestType.PERFORMANCE_ANALYSIS -> {
                        request.methods?.let { performPerformanceAnalysis(it, context) }
                            ?: createErrorResponse(context.requestId, "Missing methods for performance analysis")
                    }
                    else -> createErrorResponse(context.requestId, "Unsupported request type: ${request.requestType}")
                }
            } catch (e: Exception) {
                logger.error("Error processing batch request at index $index", e)
                createErrorResponse("batch_$index", e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * 获取AI使用统计
     */
    fun getUsageStatistics(): AIUsageStatistics {
        val totalRequests = adapters.values.sumOf { it.getRequestCount() }
        val totalTokens = adapters.values.sumOf { it.getTotalTokenUsage() }
        val avgResponseTime = adapters.values.map { it.getAverageResponseTime() }.average()
        
        val providerStats = adapters.map { (provider, adapter) ->
            ProviderStatistics(
                provider = provider,
                requestCount = adapter.getRequestCount(),
                successRate = adapter.getSuccessRate(),
                averageResponseTime = adapter.getAverageResponseTime(),
                totalTokenUsage = adapter.getTotalTokenUsage(),
                errorCount = adapter.getErrorCount()
            )
        }
        
        return AIUsageStatistics(
            totalRequests = totalRequests,
            totalTokenUsage = totalTokens,
            averageResponseTime = avgResponseTime,
            providerStatistics = providerStats,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 更新供应商配置
     */
    fun updateProviderConfig(provider: AIProvider, config: AIModelConfig) {
        modelConfigs[provider] = config
        adapters[provider]?.updateConfig(config)
        logger.info("Updated configuration for provider: {}", provider)
    }
    
    /**
     * 启用/禁用供应商
     */
    fun setProviderEnabled(provider: AIProvider, enabled: Boolean) {
        modelConfigs[provider]?.let { config ->
            modelConfigs[provider] = config.copy(enabled = enabled)
        }
        logger.info("Provider {} {}", provider, if (enabled) "enabled" else "disabled")
    }
    
    /**
     * 获取可用的供应商列表
     */
    private fun getAvailableProviders(): List<AIProvider> {
        return modelConfigs.filter { it.value.enabled }.keys.toList()
    }
    
    /**
     * 执行AI请求
     */
    private suspend fun executeAIRequest(
        provider: AIProvider,
        prompt: String,
        context: AIRequestContext
    ): AIResponse {
        val adapter = adapters[provider]
            ?: return createErrorResponse(context.requestId, "No adapter found for provider: $provider")
        
        val startTime = System.currentTimeMillis()
        
        return try {
            val response = adapter.sendRequest(prompt, context)
            val responseTime = System.currentTimeMillis() - startTime
            
            response.copy(responseTime = responseTime)
        } catch (e: Exception) {
            logger.error("Error executing AI request for provider: {}", provider, e)
            
            // 故障转移
            if (context.retryCount < context.maxRetries) {
                val fallbackProvider = loadBalancer.selectFallbackProvider(provider, getAvailableProviders())
                if (fallbackProvider != null && fallbackProvider != provider) {
                    logger.info("Falling back to provider: {}", fallbackProvider)
                    val retryContext = context.copy(retryCount = context.retryCount + 1)
                    return executeAIRequest(fallbackProvider, prompt, retryContext)
                }
            }
            
            createErrorResponse(context.requestId, e.message ?: "AI request failed")
        }
    }
    
    /**
     * 构建代码评审提示
     */
    private fun buildCodeReviewPrompt(analysisReport: DualFlowAnalysisReport): String {
        return """
            请对以下代码变更进行全面的代码评审：
            
            ## 变更概览
            - 总文件数: ${analysisReport.totalFiles}
            - 高优先级文件: ${analysisReport.highPriorityFiles.size}
            - 中优先级文件: ${analysisReport.mediumPriorityFiles.size}
            - 低优先级文件: ${analysisReport.lowPriorityFiles.size}
            - 预估评审时间: ${analysisReport.estimatedTotalReviewTime} 分钟
            
            ## 主要风险因素
            ${analysisReport.topRiskFactors.take(5).joinToString("\n") { "- ${it.first}: ${it.second}次" }}
            
            ## 建议评审顺序
            ${analysisReport.recommendedReviewOrder.take(10).joinToString("\n") { "- $it" }}
            
            请提供以下方面的评审意见：
            1. 代码质量和可维护性
            2. 安全风险评估
            3. 性能影响分析
            4. 架构设计建议
            5. 最佳实践推荐
            6. 潜在bug和问题
            
            请使用结构化的格式回复，包含评级（1-10分）和具体建议。
        """.trimIndent()
    }
    
    /**
     * 构建安全分析提示
     */
    private fun buildSecurityAnalysisPrompt(methods: List<MethodNode>): String {
        val methodInfos = methods.take(10).map { method ->
            "- ${method.methodName}: ${method.signature} (${method.blockType})"
        }.joinToString("\n")
        
        return """
            请对以下方法进行安全分析：
            
            $methodInfos
            
            请重点关注：
            1. 输入验证和清理
            2. SQL注入风险
            3. XSS攻击风险
            4. 权限控制问题
            5. 敏感数据处理
            6. 加密和解密操作
            7. 会话管理安全
            8. API安全最佳实践
            
            请提供安全风险评级（1-10）和具体的安全建议。
        """.trimIndent()
    }
    
    /**
     * 构建性能分析提示
     */
    private fun buildPerformanceAnalysisPrompt(methods: List<MethodNode>): String {
        val complexMethods = methods.filter { it.cyclomaticComplexity > 5 }.take(5)
        val methodInfos = complexMethods.map { method ->
            "- ${method.methodName}: 复杂度=${method.cyclomaticComplexity}, 行数=${method.linesOfCode}"
        }.joinToString("\n")
        
        return """
            请对以下高复杂度方法进行性能分析：
            
            $methodInfos
            
            请重点评估：
            1. 算法复杂度和效率
            2. 数据库查询优化
            3. 缓存策略建议
            4. 内存使用优化
            5. 并发性能考虑
            6. I/O操作优化
            7. 循环和递归优化
            8. 资源管理最佳实践
            
            请提供性能风险评级（1-10）和具体的优化建议。
        """.trimIndent()
    }
    
    /**
     * 初始化适配器
     */
    private fun initializeAdapters() {
        adapters[AIProvider.OPENAI] = OpenAIAdapter()
        adapters[AIProvider.ANTHROPIC] = AnthropicAdapter()
        adapters[AIProvider.GOOGLE] = GoogleAIAdapter()
        adapters[AIProvider.OLLAMA] = OllamaAdapter()
        adapters[AIProvider.AZURE_OPENAI] = AzureOpenAIAdapter()
        
        logger.info("Initialized {} AI adapters", adapters.size)
    }
    
    /**
     * 加载配置
     */
    private fun loadConfigurations() {
        val settings = com.vyibc.autocr.settings.AutoCRSettingsState.getInstance(project)
        
        // 加载OpenAI配置
        modelConfigs[AIProvider.OPENAI] = AIModelConfig(
            provider = AIProvider.OPENAI,
            modelName = settings.openAIConfig.modelName.ifEmpty { "gpt-4" },
            apiKey = settings.openAIConfig.apiKey,
            baseUrl = settings.openAIConfig.baseUrl,
            maxTokens = settings.openAIConfig.maxTokens,
            temperature = settings.openAIConfig.temperature,
            timeout = settings.openAIConfig.timeout,
            enabled = settings.openAIConfig.enabled
        )
        
        // 加载Anthropic配置
        modelConfigs[AIProvider.ANTHROPIC] = AIModelConfig(
            provider = AIProvider.ANTHROPIC,
            modelName = settings.anthropicConfig.modelName.ifEmpty { "claude-3-sonnet-20240229" },
            apiKey = settings.anthropicConfig.apiKey,
            baseUrl = settings.anthropicConfig.baseUrl,
            maxTokens = settings.anthropicConfig.maxTokens,
            temperature = settings.anthropicConfig.temperature,
            timeout = settings.anthropicConfig.timeout,
            enabled = settings.anthropicConfig.enabled
        )
        
        // 加载Google配置
        modelConfigs[AIProvider.GOOGLE] = AIModelConfig(
            provider = AIProvider.GOOGLE,
            modelName = settings.googleConfig.modelName.ifEmpty { "gemini-pro" },
            apiKey = settings.googleConfig.apiKey,
            baseUrl = settings.googleConfig.baseUrl,
            maxTokens = settings.googleConfig.maxTokens,
            temperature = settings.googleConfig.temperature,
            timeout = settings.googleConfig.timeout,
            enabled = settings.googleConfig.enabled
        )
        
        // 加载Ollama配置
        modelConfigs[AIProvider.OLLAMA] = AIModelConfig(
            provider = AIProvider.OLLAMA,
            modelName = settings.ollamaConfig.modelName.ifEmpty { "codellama" },
            baseUrl = settings.ollamaConfig.baseUrl.ifEmpty { "http://localhost:11434" },
            maxTokens = settings.ollamaConfig.maxTokens,
            temperature = settings.ollamaConfig.temperature,
            timeout = settings.ollamaConfig.timeout,
            enabled = settings.ollamaConfig.enabled
        )
        
        // 加载Azure OpenAI配置
        modelConfigs[AIProvider.AZURE_OPENAI] = AIModelConfig(
            provider = AIProvider.AZURE_OPENAI,
            modelName = settings.azureOpenAIConfig.modelName.ifEmpty { "gpt-4" },
            apiKey = settings.azureOpenAIConfig.apiKey,
            baseUrl = settings.azureOpenAIConfig.baseUrl,
            maxTokens = settings.azureOpenAIConfig.maxTokens,
            temperature = settings.azureOpenAIConfig.temperature,
            timeout = settings.azureOpenAIConfig.timeout,
            enabled = settings.azureOpenAIConfig.enabled
        )
        
        logger.info("Loaded configurations for {} providers", modelConfigs.size)
        
        // 更新适配器配置
        adapters.forEach { (provider, adapter) ->
            modelConfigs[provider]?.let { config ->
                adapter.updateConfig(config)
            }
        }
    }
    
    /**
     * 创建错误响应
     */
    private fun createErrorResponse(requestId: String, errorMessage: String): AIResponse {
        return AIResponse(
            requestId = requestId,
            provider = AIProvider.OPENAI, // 默认值
            model = "unknown",
            content = "",
            success = false,
            errorMessage = errorMessage,
            responseTime = 0L
        )
    }
    
    /**
     * 生成请求ID
     */
    private fun generateRequestId(): String {
        return "ai_req_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    companion object {
        fun getInstance(project: Project): MultiAIAdapterService {
            return project.getService(MultiAIAdapterService::class.java)
        }
    }
}

/**
 * 批量AI请求
 */
data class BatchAIRequest(
    val requestType: AIRequestType,
    val priority: Int = 0,
    val analysisReport: DualFlowAnalysisReport? = null,
    val methods: List<MethodNode>? = null
)

/**
 * AI使用统计
 */
data class AIUsageStatistics(
    val totalRequests: Long,
    val totalTokenUsage: Long,
    val averageResponseTime: Double,
    val providerStatistics: List<ProviderStatistics>,
    val timestamp: Long
)

/**
 * 供应商统计
 */
data class ProviderStatistics(
    val provider: AIProvider,
    val requestCount: Long,
    val successRate: Double,
    val averageResponseTime: Double,
    val totalTokenUsage: Long,
    val errorCount: Long
)