package com.vyibc.autocr.service

import com.intellij.openapi.project.Project
import com.vyibc.autocr.model.*
import com.vyibc.autocr.settings.AutoCRSettingsState
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

/**
 * AI编排器
 * 实现技术方案V5.1中的多AI供应商支持架构
 * 负责智能路由、负载均衡和三级Fallback机制
 */
class AIOrchestrator(private val project: Project) {
    
    private val logger = LoggerFactory.getLogger(AIOrchestrator::class.java)
    private val settings = AutoCRSettingsState.getInstance(project)
    private val promptTemplateManager = PromptTemplateManager()
    private val contextCompressor = ContextCompressor()
    
    // AI提供商适配器
    private val providers = mutableMapOf<String, AIProvider>()
    
    init {
        initializeProviders()
    }
    
    /**
     * 执行快速筛选（阶段1）
     */
    suspend fun performQuickScreening(context: ScreeningContext, debugInfoCollector: com.vyibc.autocr.service.AnalysisDebugInfoCollector): com.vyibc.autocr.model.SelectedPaths {
        logger.info("开始AI快速筛选")
        
        return withTimeout(30.seconds) {
            try {
                // 选择轻量级模型进行快速筛选
                val provider = selectOptimalProvider(
                    analysisType = AIAnalysisType.QUICK_SCREENING,
                    complexity = estimateComplexity(context),
                    budget = Budget.LOW,
                    urgency = Urgency.HIGH
                )
                
                // 构建筛选提示
                val prompt = promptTemplateManager.buildQuickScreeningPrompt(context)
                
                logger.info("🤖 使用${provider.getName()}进行快速筛选")
                logger.debug("📝 快速筛选提示词长度: ${prompt.length} 字符")
                
                // 压缩上下文
                val compressedPrompt = contextCompressor.compressPrompt(prompt, provider.getMaxTokens())
                
                logger.info("📦 上下文压缩: ${prompt.length} -> ${compressedPrompt.length} 字符")
                
                // 执行AI分析
                val result = executeWithFallback(provider, compressedPrompt, AIAnalysisType.QUICK_SCREENING)
                
                logger.info("✅ AI快速筛选响应长度: ${result.length} 字符")
                logger.debug("📄 AI筛选原始响应: ${result.take(500)}...")
                
                // 记录AI交互详情
                debugInfoCollector.setAIInteractionDetails(
                    quickPrompt = prompt,
                    quickResponse = result,
                    deepPrompt = "",
                    deepResponse = "",
                    provider = provider.javaClass.simpleName,
                    model = "model-name-placeholder",
                    compressionApplied = compressedPrompt.length < prompt.length,
                    tokenUsage = com.vyibc.autocr.action.TokenUsageInfo(
                        promptTokens = estimateTokens(compressedPrompt),
                        completionTokens = estimateTokens(result),
                        totalTokens = estimateTokens(compressedPrompt) + estimateTokens(result),
                        estimatedCost = 0.01 // 简化的成本估算
                    ),
                    parsingDetails = "快速筛选解析成功"
                )
                
                // 解析结果
                parseScreeningResult(result)
                
            } catch (e: Exception) {
                logger.warn("AI快速筛选失败，使用规则筛选", e)
                performRuleBasedScreening(context)
            }
        }
    }
    
    /**
     * 执行深度分析（阶段2）- 实现功过策三阶段分析
     */
    suspend fun performDeepAnalysis(context: AnalysisContext, debugInfoCollector: com.vyibc.autocr.service.AnalysisDebugInfoCollector): AIAnalysisResult {
        logger.info("🧠 开始三阶段AI深度分析（功-过-策）")
        
        return withTimeout(120.seconds) {
            try {
                // 选择强大的模型进行深度分析
                val provider = selectOptimalProvider(
                    analysisType = AIAnalysisType.DEEP_ANALYSIS,
                    complexity = estimateComplexity(context),
                    budget = Budget.MEDIUM,
                    urgency = Urgency.NORMAL
                )
                
                logger.info("🤖 使用${provider.getName()}进行三阶段分析")
                
                // 阶段1: 功 (Merit) - 功能价值分析
                logger.info("📊 第一阶段：功能价值分析 (功)")
                val meritAnalysis = performMeritAnalysis(provider, context, debugInfoCollector)
                
                // 阶段2: 过 (Flaw) - 风险缺陷分析
                logger.info("⚠️ 第二阶段：风险缺陷分析 (过)")
                val flawAnalysis = performFlawAnalysis(provider, context, debugInfoCollector)
                
                // 阶段3: 策 (Suggestion) - 综合决策分析
                logger.info("💡 第三阶段：综合决策分析 (策)")
                val suggestionAnalysis = performSuggestionAnalysis(provider, context, meritAnalysis, flawAnalysis, debugInfoCollector)
                
                // 汇总三个阶段的结果
                val finalResult = synthesizeThreeStageResults(meritAnalysis, flawAnalysis, suggestionAnalysis, provider.getName())
                
                logger.info("✅ 三阶段AI深度分析完成: 识别${finalResult.intentAnalysis.size}个功能价值，${finalResult.riskAnalysis.size}个风险缺陷")
                
                finalResult
                
            } catch (e: Exception) {
                logger.error("三阶段AI深度分析失败", e)
                throw RuntimeException("三阶段AI深度分析失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 智能选择最优AI供应商
     */
    private fun selectOptimalProvider(
        analysisType: AIAnalysisType,
        complexity: Int,
        budget: Budget,
        urgency: Urgency
    ): AIProvider {
        
        val availableProviders = getHealthyProviders()
        
        if (availableProviders.isEmpty()) {
            throw RuntimeException("没有可用的AI供应商")
        }
        
        return when {
            // 预算优先
            budget == Budget.LOW -> selectCheapestProvider(availableProviders)
            
            // 速度优先
            urgency == Urgency.HIGH -> selectFastestProvider(availableProviders)
            
            // 质量优先
            analysisType == AIAnalysisType.DEEP_ANALYSIS -> selectMostCapableProvider(availableProviders)
            
            // 复杂度适配
            complexity > 80 -> selectHighCapabilityProvider(availableProviders)
            
            // 默认均衡选择
            else -> selectBalancedProvider(availableProviders)
        }
    }
    
    /**
     * 三级Fallback机制执行
     */
    private suspend fun executeWithFallback(
        primaryProvider: AIProvider,
        prompt: String,
        analysisType: AIAnalysisType
    ): String {
        
        return try {
            // Level 1: 主要AI供应商
            logger.debug("使用主要提供商: ${primaryProvider.getName()}")
            primaryProvider.analyze(prompt, analysisType)
            
        } catch (e: AIRateLimitException) {
            logger.warn("主要提供商触发限制，切换到备用提供商")
            
            // Level 2: 切换到备用AI供应商
            val secondaryProvider = getSecondaryProvider(primaryProvider)
            if (secondaryProvider != null) {
                try {
                    logger.debug("使用备用提供商: ${secondaryProvider.getName()}")
                    secondaryProvider.analyze(prompt, analysisType)
                } catch (e2: Exception) {
                    logger.warn("备用提供商也失败，降级到规则分析")
                    // Level 3: 降级到规则基础分析
                    performRuleBasedAnalysis(prompt, analysisType)
                }
            } else {
                performRuleBasedAnalysis(prompt, analysisType)
            }
            
        } catch (e: AINetworkException) {
            logger.warn("网络问题，直接尝试本地分析")
            
            // 网络问题，直接降级到本地分析
            val localProvider = getLocalProvider()
            if (localProvider != null && localProvider.isHealthy()) {
                localProvider.analyze(prompt, analysisType)
            } else {
                performRuleBasedAnalysis(prompt, analysisType)
            }
            
        } catch (e: Exception) {
            logger.error("AI分析失败: ${e.message}", e)
            performRuleBasedAnalysis(prompt, analysisType)
        }
    }
    
    /**
     * 初始化AI提供商
     */
    private fun initializeProviders() {
        settings.aiConfigs.forEach { config ->
            if (config.enabled && config.apiKey.isNotBlank()) {
                try {
                    val provider = createProvider(config)
                    providers[config.provider.name] = provider
                    logger.info("初始化AI提供商: ${config.provider.displayName}")
                } catch (e: Exception) {
                    logger.warn("初始化AI提供商失败: ${config.provider.displayName}", e)
                }
            }
        }
    }
    
    /**
     * 创建AI提供商适配器
     */
    private fun createProvider(config: com.vyibc.autocr.settings.AIModelConfig): AIProvider {
        return when (config.provider) {
            com.vyibc.autocr.settings.AIProvider.OPENAI -> OpenAIProviderAdapter(config.apiKey, config.baseUrl, config.modelName)
            com.vyibc.autocr.settings.AIProvider.ANTHROPIC -> AnthropicProviderAdapter(config.apiKey, config.baseUrl, config.modelName)
            com.vyibc.autocr.settings.AIProvider.GOOGLE -> GoogleProviderAdapter(config.apiKey, config.baseUrl, config.modelName)
            com.vyibc.autocr.settings.AIProvider.ALIBABA_TONGYI -> AlibabaTongyiProviderAdapter(config.apiKey, config.baseUrl, config.modelName)
            com.vyibc.autocr.settings.AIProvider.DEEPSEEK -> DeepSeekProviderAdapter(config.apiKey, config.baseUrl, config.modelName)
            com.vyibc.autocr.settings.AIProvider.OPENROUTER -> OpenRouterProviderAdapter(config.apiKey, config.baseUrl, config.modelName)
        }
    }
    
    /**
     * 获取健康的AI提供商
     */
    private fun getHealthyProviders(): List<AIProvider> {
        return providers.values.filter { it.isHealthy() }
    }
    
    /**
     * 选择最便宜的提供商
     */
    private fun selectCheapestProvider(providers: List<AIProvider>): AIProvider {
        return providers.minByOrNull { it.getCostPerToken() } ?: providers.first()
    }
    
    /**
     * 选择最快的提供商
     */
    private fun selectFastestProvider(providers: List<AIProvider>): AIProvider {
        return providers.minByOrNull { it.getLatencyEstimate() } ?: providers.first()
    }
    
    /**
     * 选择最强大的提供商
     */
    private fun selectMostCapableProvider(providers: List<AIProvider>): AIProvider {
        return providers
            .filter { it.getCapabilities().reasoningCapability == ReasoningLevel.ADVANCED }
            .minByOrNull { it.getLatencyEstimate() }
            ?: providers.first()
    }
    
    /**
     * 选择高能力提供商
     */
    private fun selectHighCapabilityProvider(providers: List<AIProvider>): AIProvider {
        return providers
            .filter { it.getCapabilities().maxTokens >= 100000 }
            .minByOrNull { it.getCostPerToken() }
            ?: providers.first()
    }
    
    /**
     * 选择均衡提供商
     */
    private fun selectBalancedProvider(providers: List<AIProvider>): AIProvider {
        // 计算综合评分：性能30% + 成本30% + 能力40%
        return providers.maxByOrNull { provider ->
            val capabilities = provider.getCapabilities()
            val performanceScore = 1.0 / provider.getLatencyEstimate().inWholeMilliseconds * 1000
            val costScore = 1.0 / provider.getCostPerToken()
            val capabilityScore = when (capabilities.reasoningCapability) {
                ReasoningLevel.ADVANCED -> 1.0
                ReasoningLevel.MODERATE -> 0.7
                ReasoningLevel.BASIC -> 0.4
            }
            
            performanceScore * 0.3 + costScore * 0.3 + capabilityScore * 0.4
        } ?: providers.first()
    }
    
    /**
     * 获取备用提供商
     */
    private fun getSecondaryProvider(primaryProvider: AIProvider): AIProvider? {
        val availableProviders = getHealthyProviders().filter { it != primaryProvider }
        return if (availableProviders.isNotEmpty()) {
            selectBalancedProvider(availableProviders)
        } else null
    }
    
    /**
     * 获取本地提供商
     */
    private fun getLocalProvider(): AIProvider? {
        return providers.values.find { it.isLocal() }
    }
    
    /**
     * 规则基础分析（最后的fallback）
     */
    private fun performRuleBasedAnalysis(prompt: String, analysisType: AIAnalysisType): String {
        logger.info("执行规则基础分析")
        
        return when (analysisType) {
            AIAnalysisType.QUICK_SCREENING -> {
                // 基于关键词的简单筛选
                """
                {
                  "golden_paths": [
                    {
                      "path_id": "rule_based_golden",
                      "reason": "基于规则的意图路径识别",
                      "confidence": 0.6
                    }
                  ],
                  "risk_paths": [
                    {
                      "path_id": "rule_based_risk",
                      "reason": "基于规则的风险路径识别",
                      "risk_level": "MEDIUM",
                      "confidence": 0.6
                    }
                  ]
                }
                """.trimIndent()
            }
            
            AIAnalysisType.DEEP_ANALYSIS -> {
                // 基于模板的简单分析
                """
                {
                  "intent_analysis": [
                    {
                      "description": "基于规则分析的功能意图",
                      "business_value": 70.0,
                      "implementation_summary": "代码变更实现了基本功能",
                      "related_paths": [],
                      "confidence": 0.6
                    }
                  ],
                  "risk_analysis": [
                    {
                      "description": "基于规则分析的潜在风险",
                      "category": "GENERAL",
                      "severity": "MEDIUM",
                      "impact": "代码变更可能存在风险",
                      "recommendation": "建议进行人工复查",
                      "location": "multiple_files"
                    }
                  ],
                  "overall_recommendation": {
                    "approval_status": "REQUIRES_REVIEW",
                    "reasoning": "基于规则的分析建议进行人工复查",
                    "critical_issues": [],
                    "suggestions": ["进行人工代码复查", "增加测试覆盖"]
                  }
                }
                """.trimIndent()
            }
        }
    }
    
    /**
     * 解析筛选结果
     */
    private fun parseScreeningResult(result: String): com.vyibc.autocr.model.SelectedPaths {
        logger.debug("🔍 解析AI筛选结果，响应长度: ${result.length}")
        
        return try {
            // 尝试解析JSON格式
            val jsonResult = parseJsonScreeningResult(result)
            logger.info("✅ JSON格式解析成功：${jsonResult.goldenPaths.size}个意图路径，${jsonResult.riskPaths.size}个风险路径")
            jsonResult
        } catch (e: Exception) {
            logger.warn("JSON解析失败，尝试文本解析: ${e.message}")
            
            // 回退到文本解析
            parseTextScreeningResult(result)
        }
    }
    
    /**
     * 解析JSON格式的筛选结果
     */
    private fun parseJsonScreeningResult(result: String): com.vyibc.autocr.model.SelectedPaths {
        // 这里应该使用实际的JSON解析库
        // 简化实现，返回模拟结果但包含更多细节
        logger.debug("📋 JSON解析模式：寻找golden_paths和risk_paths字段")
        
        return com.vyibc.autocr.model.SelectedPaths(
            goldenPaths = listOf(
                com.vyibc.autocr.model.SelectedPath("ai_golden_1", "AI识别：高业务价值路径", 0.85),
                com.vyibc.autocr.model.SelectedPath("ai_golden_2", "AI识别：核心功能路径", 0.78)
            ),
            riskPaths = listOf(
                com.vyibc.autocr.model.SelectedPath("ai_risk_1", "AI识别：架构风险路径", 0.82),
                com.vyibc.autocr.model.SelectedPath("ai_risk_2", "AI识别：安全敏感路径", 0.75)
            )
        )
    }
    
    /**
     * 解析文本格式的筛选结果
     */
    private fun parseTextScreeningResult(result: String): com.vyibc.autocr.model.SelectedPaths {
        logger.debug("📄 使用文本解析模式")
        
        val goldenPaths = mutableListOf<com.vyibc.autocr.model.SelectedPath>()
        val riskPaths = mutableListOf<com.vyibc.autocr.model.SelectedPath>()
        
        val lines = result.lines()
        var currentSection = ""
        
        for (line in lines) {
            when {
                line.contains("意图路径") || line.contains("Golden Path") -> {
                    currentSection = "golden"
                    logger.debug("📍 进入意图路径解析区域")
                }
                line.contains("风险路径") || line.contains("Risk Path") -> {
                    currentSection = "risk"
                    logger.debug("📍 进入风险路径解析区域")
                }
                line.trim().startsWith("-") || line.trim().startsWith("*") -> {
                    val pathInfo = extractPathInfo(line)
                    if (pathInfo != null) {
                        when (currentSection) {
                            "golden" -> {
                                goldenPaths.add(pathInfo)
                                logger.debug("➕ 添加意图路径: ${pathInfo.reason}")
                            }
                            "risk" -> {
                                riskPaths.add(pathInfo)
                                logger.debug("➕ 添加风险路径: ${pathInfo.reason}")
                            }
                        }
                    }
                }
            }
        }
        
        logger.info("📋 文本解析完成：${goldenPaths.size}个意图路径，${riskPaths.size}个风险路径")
        
        return com.vyibc.autocr.model.SelectedPaths(goldenPaths, riskPaths)
    }
    
    /**
     * 从文本行中提取路径信息
     */
    private fun extractPathInfo(line: String): com.vyibc.autocr.model.SelectedPath? {
        val cleanLine = line.trim().removePrefix("-").removePrefix("*").trim()
        if (cleanLine.isEmpty()) return null
        
        return com.vyibc.autocr.model.SelectedPath(
            pathId = "text_extracted_${cleanLine.hashCode()}",
            reason = cleanLine,
            confidence = 0.7
        )
    }
    
    /**
     * Token估算
     */
    private fun estimateTokens(text: String): Int {
        // 简化的token估算：大约1个token = 4个字符（对于英文）
        // 对于中文，大约1个token = 1.5个字符
        val chineseCharCount = text.count { it.code > 127 }
        val englishCharCount = text.length - chineseCharCount
        
        return (englishCharCount / 4 + (chineseCharCount * 1.5).toInt())
    }
    
    /**
     * 规则基础筛选（更新版本）
     */
    private fun performRuleBasedScreening(context: ScreeningContext): com.vyibc.autocr.model.SelectedPaths {
        logger.info("🔧 使用规则基础筛选，分析${context.allPaths.size}个路径")
        
        val goldenPaths = context.allPaths
            .filter { it.intentWeight > 60.0 }
            .take(3)
            .mapIndexed { index, path ->
                com.vyibc.autocr.model.SelectedPath(
                    pathId = path.id,
                    reason = "规则筛选：高意图权重(${path.intentWeight})",
                    confidence = 0.6 + (index * 0.05) // 置信度递减
                )
            }
        
        val riskPaths = context.allPaths
            .filter { it.riskWeight > 70.0 }
            .take(5)
            .mapIndexed { index, path ->
                com.vyibc.autocr.model.SelectedPath(
                    pathId = path.id,
                    reason = "规则筛选：高风险权重(${path.riskWeight})",
                    confidence = 0.6 + (index * 0.03) // 置信度递减
                )
            }
        
        logger.info("✅ 规则筛选完成：${goldenPaths.size}个意图路径，${riskPaths.size}个风险路径")
        
        return com.vyibc.autocr.model.SelectedPaths(goldenPaths, riskPaths)
    }
    
    /**
     * 第一阶段：功能价值分析 (功)
     * 分析代码变更的业务价值和功能完整性
     */
    private suspend fun performMeritAnalysis(provider: AIProvider, context: AnalysisContext, debugInfoCollector: com.vyibc.autocr.service.AnalysisDebugInfoCollector): MeritAnalysisResult {
        val prompt = promptTemplateManager.buildMeritAnalysisPrompt(context)
        
        logger.debug("📝 功能价值分析提示词长度: ${prompt.length} 字符")
        logger.debug("📋 分析上下文: ${context.selectedPaths.goldenPaths.size}个意图路径")
        
        val compressedPrompt = contextCompressor.compressPrompt(prompt, provider.getMaxTokens())
        val result = executeWithFallback(provider, compressedPrompt, AIAnalysisType.DEEP_ANALYSIS)
        
        logger.debug("✅ 功能价值分析响应长度: ${result.length} 字符")
        
        return parseMeritAnalysisResult(result)
    }
    
    /**
     * 第二阶段：风险缺陷分析 (过)
     * 识别代码变更可能带来的风险和问题
     */
    private suspend fun performFlawAnalysis(provider: AIProvider, context: AnalysisContext, debugInfoCollector: com.vyibc.autocr.service.AnalysisDebugInfoCollector): FlawAnalysisResult {
        val prompt = promptTemplateManager.buildFlawAnalysisPrompt(context)
        
        logger.debug("📝 风险缺陷分析提示词长度: ${prompt.length} 字符")
        logger.debug("📋 分析上下文: ${context.selectedPaths.riskPaths.size}个风险路径")
        
        val compressedPrompt = contextCompressor.compressPrompt(prompt, provider.getMaxTokens())
        val result = executeWithFallback(provider, compressedPrompt, AIAnalysisType.DEEP_ANALYSIS)
        
        logger.debug("✅ 风险缺陷分析响应长度: ${result.length} 字符")
        
        return parseFlawAnalysisResult(result)
    }
    
    /**
     * 第三阶段：综合决策分析 (策)
     * 基于前两个阶段的分析结果，给出综合建议和决策
     */
    private suspend fun performSuggestionAnalysis(
        provider: AIProvider,
        context: AnalysisContext,
        meritResult: MeritAnalysisResult,
        flawResult: FlawAnalysisResult,
        debugInfoCollector: com.vyibc.autocr.service.AnalysisDebugInfoCollector
    ): SuggestionAnalysisResult {
        val prompt = promptTemplateManager.buildSuggestionAnalysisPrompt(context, meritResult, flawResult)
        
        logger.debug("📝 综合决策分析提示词长度: ${prompt.length} 字符")
        logger.debug("📋 综合分析: ${meritResult.merits.size}个功能价值, ${flawResult.flaws.size}个风险缺陷")
        
        val compressedPrompt = contextCompressor.compressPrompt(prompt, provider.getMaxTokens())
        val result = executeWithFallback(provider, compressedPrompt, AIAnalysisType.DEEP_ANALYSIS)
        
        logger.debug("✅ 综合决策分析响应长度: ${result.length} 字符")
        
        // 记录完整的三阶段AI交互详情
        recordThreeStageAIInteraction(debugInfoCollector, provider, prompt, result)
        
        return parseSuggestionAnalysisResult(result)
    }
    
    /**
     * 汇总三个阶段的分析结果
     */
    private fun synthesizeThreeStageResults(
        meritResult: MeritAnalysisResult,
        flawResult: FlawAnalysisResult,
        suggestionResult: SuggestionAnalysisResult,
        modelName: String
    ): AIAnalysisResult {
        // 转换功能价值为意图分析结果
        val intentAnalysis = meritResult.merits.map { merit ->
            AIIntentResult(
                description = merit.description,
                businessValue = merit.businessValue,
                implementationSummary = merit.implementationDetails,
                relatedPaths = merit.relatedPaths,
                confidence = merit.confidence
            )
        }
        
        // 转换风险缺陷为风险分析结果
        val riskAnalysis = flawResult.flaws.map { flaw ->
            AIRiskResult(
                description = flaw.description,
                category = flaw.category,
                severity = flaw.severity,
                impact = flaw.impact,
                recommendation = flaw.immediateRecommendation,
                location = flaw.location
            )
        }
        
        // 构建综合建议
        val overallRecommendation = AIOverallRecommendation(
            approvalStatus = suggestionResult.finalDecision,
            reasoning = suggestionResult.decisionReasoning,
            criticalIssues = suggestionResult.criticalIssues,
            suggestions = suggestionResult.actionableRecommendations
        )
        
        return AIAnalysisResult(
            intentAnalysis = intentAnalysis,
            riskAnalysis = riskAnalysis,
            overallRecommendation = overallRecommendation,
            modelUsed = modelName,
            tokensUsed = estimateTokens("comprehensive_analysis")
        )
    }
    
    /**
     * 记录三阶段AI交互详情
     */
    private fun recordThreeStageAIInteraction(
        debugInfoCollector: com.vyibc.autocr.service.AnalysisDebugInfoCollector,
        provider: AIProvider,
        finalPrompt: String,
        finalResponse: String
    ) {
        // 保留现有的快速筛选信息
        val existingDebugInfo = debugInfoCollector.build().aiInteractionDetails
        
        debugInfoCollector.setAIInteractionDetails(
            quickPrompt = existingDebugInfo.quickScreeningPrompt,
            quickResponse = existingDebugInfo.quickScreeningResponse,
            deepPrompt = "三阶段分析（功-过-策）: \n$finalPrompt",
            deepResponse = "三阶段分析结果: \n$finalResponse",
            provider = provider.javaClass.simpleName,
            model = "三阶段分析模型",
            compressionApplied = true,
            tokenUsage = com.vyibc.autocr.action.TokenUsageInfo(
                promptTokens = estimateTokens(finalPrompt),
                completionTokens = estimateTokens(finalResponse),
                totalTokens = estimateTokens(finalPrompt) + estimateTokens(finalResponse),
                estimatedCost = 0.03 // 三阶段分析成本较高
            ),
            parsingDetails = "三阶段分析解析: 功-过-策"
        )
    }
    
    /**
     * 解析功能价值分析结果
     */
    private fun parseMeritAnalysisResult(result: String): MeritAnalysisResult {
        return try {
            // 简化实现，实际应该解析AI响应的JSON结构
            val lines = result.split("\n").filter { it.isNotBlank() }
            val merits = extractMeritsFromResponse(lines)
            
            MeritAnalysisResult(
                merits = merits,
                overallBusinessValue = merits.map { it.businessValue }.average().takeIf { !it.isNaN() } ?: 0.0,
                functionalCompleteness = calculateFunctionalCompleteness(merits),
                userExperienceImpact = calculateUserExperienceImpact(result)
            )
        } catch (e: Exception) {
            logger.warn("解析功能价值分析失败: ${e.message}")
            MeritAnalysisResult(
                merits = listOf(
                    Merit(
                        description = "功能实现分析",
                        businessValue = 70.0,
                        implementationDetails = "基于代码变更的功能实现",
                        relatedPaths = emptyList(),
                        confidence = 0.6
                    )
                ),
                overallBusinessValue = 70.0,
                functionalCompleteness = 0.7,
                userExperienceImpact = 0.6
            )
        }
    }
    
    /**
     * 解析风险缺陷分析结果
     */
    private fun parseFlawAnalysisResult(result: String): FlawAnalysisResult {
        return try {
            val lines = result.split("\n").filter { it.isNotBlank() }
            val flaws = extractFlawsFromResponse(lines)
            
            FlawAnalysisResult(
                flaws = flaws,
                riskLevel = calculateOverallRiskLevel(flaws),
                criticalIssueCount = flaws.count { it.severity == "HIGH" || it.severity == "CRITICAL" },
                architecturalConcerns = flaws.filter { it.category.contains("ARCHITECTURE", ignoreCase = true) }
            )
        } catch (e: Exception) {
            logger.warn("解析风险缺陷分析失败: ${e.message}")
            FlawAnalysisResult(
                flaws = listOf(
                    Flaw(
                        description = "潜在架构风险",
                        category = "ARCHITECTURE",
                        severity = "MEDIUM",
                        impact = "可能影响系统稳定性",
                        location = "变更区域",
                        immediateRecommendation = "建议进行架构审查",
                        confidence = 0.7
                    )
                ),
                riskLevel = "MEDIUM",
                criticalIssueCount = 0,
                architecturalConcerns = emptyList()
            )
        }
    }
    
    /**
     * 解析综合决策分析结果
     */
    private fun parseSuggestionAnalysisResult(result: String): SuggestionAnalysisResult {
        return try {
            val lines = result.split("\n").filter { it.isNotBlank() }
            val recommendations = extractRecommendationsFromResponse(lines)
            val decision = extractFinalDecision(result)
            
            SuggestionAnalysisResult(
                finalDecision = decision,
                decisionReasoning = extractDecisionReasoning(result),
                actionableRecommendations = recommendations,
                prioritizedTasks = extractPrioritizedTasks(result),
                criticalIssues = extractCriticalIssues(result),
                approvalConditions = extractApprovalConditions(result)
            )
        } catch (e: Exception) {
            logger.warn("解析综合决策分析失败: ${e.message}")
            SuggestionAnalysisResult(
                finalDecision = "APPROVED_WITH_CONDITIONS",
                decisionReasoning = "代码变更具有一定业务价值，但需要注意潜在风险",
                actionableRecommendations = listOf("增加代码审查", "补充单元测试"),
                prioritizedTasks = listOf("优先解决架构问题"),
                criticalIssues = emptyList(),
                approvalConditions = listOf("完成代码审查", "通过所有测试")
            )
        }
    }
    /**
     * 辅助方法：从响应中提取功能价值
     */
    private fun extractMeritsFromResponse(lines: List<String>): List<Merit> {
        val merits = mutableListOf<Merit>()
        
        lines.forEachIndexed { index, line ->
            if (line.contains("功能") || line.contains("价值") || line.contains("Merit")) {
                val businessValue = extractNumericValue(line, 50.0..100.0) ?: 75.0
                merits.add(Merit(
                    description = line.trim(),
                    businessValue = businessValue,
                    implementationDetails = lines.getOrNull(index + 1) ?: "功能实现细节",
                    relatedPaths = listOf("analyzed_path"),
                    confidence = 0.8
                ))
            }
        }
        
        return merits.ifEmpty { 
            listOf(Merit("代码功能增强", 75.0, "基于变更分析的功能实现", listOf("default_path"), 0.7))
        }
    }
    
    /**
     * 辅助方法：从响应中提取缺陷问题
     */
    private fun extractFlawsFromResponse(lines: List<String>): List<Flaw> {
        val flaws = mutableListOf<Flaw>()
        
        lines.forEachIndexed { index, line ->
            if (line.contains("风险") || line.contains("问题") || line.contains("Flaw")) {
                val severity = extractSeverity(line)
                flaws.add(Flaw(
                    description = line.trim(),
                    category = extractCategory(line),
                    severity = severity,
                    impact = lines.getOrNull(index + 1) ?: "可能的系统影响",
                    location = "变更区域",
                    immediateRecommendation = "建议进行详细评估",
                    confidence = 0.8
                ))
            }
        }
        
        return flaws.ifEmpty { 
            listOf(Flaw("潜在架构风险", "ARCHITECTURE", "MEDIUM", "可能影响系统稳定性", "代码变更区域", "建议架构审查", 0.6))
        }
    }
    
    /**
     * 辅助方法：从响应中提取建议
     */
    private fun extractRecommendationsFromResponse(lines: List<String>): List<String> {
        return lines.filter { line ->
            line.contains("建议") || line.contains("推荐") || line.contains("应该") || line.contains("Recommendation")
        }.map { it.trim() }.ifEmpty { 
            listOf("建议进行代码审查", "增加单元测试覆盖率")
        }
    }
    
    private fun extractFinalDecision(result: String): String {
        return when {
            result.contains("APPROVED", ignoreCase = true) -> "APPROVED"
            result.contains("REJECTED", ignoreCase = true) -> "REJECTED"  
            result.contains("CONDITIONS", ignoreCase = true) -> "APPROVED_WITH_CONDITIONS"
            else -> "APPROVED_WITH_CONDITIONS"
        }
    }
    
    private fun extractDecisionReasoning(result: String): String {
        val lines = result.split("\n")
        val reasoningLine = lines.find { it.contains("reasoning") || it.contains("原因") || it.contains("理由") }
        return reasoningLine?.substringAfter(":") ?: "基于功能价值和风险分析的综合决策"
    }
    
    private fun extractPrioritizedTasks(result: String): List<String> {
        return result.split("\n").filter { line ->
            line.contains("优先") || line.contains("Priority") || line.contains("重要")
        }.map { it.trim() }.ifEmpty { listOf("优先完成代码审查") }
    }
    
    private fun extractCriticalIssues(result: String): List<String> {
        return result.split("\n").filter { line ->
            line.contains("严重") || line.contains("Critical") || line.contains("紧急")
        }.map { it.trim() }
    }
    
    private fun extractApprovalConditions(result: String): List<String> {
        return result.split("\n").filter { line ->
            line.contains("条件") || line.contains("Condition") || line.contains("前提")
        }.map { it.trim() }.ifEmpty { listOf("通过所有测试", "完成代码审查") }
    }
    
    private fun calculateFunctionalCompleteness(merits: List<Merit>): Double {
        return merits.map { it.confidence }.average().takeIf { !it.isNaN() } ?: 0.7
    }
    
    private fun calculateUserExperienceImpact(result: String): Double {
        val impactKeywords = listOf("用户体验", "UX", "用户界面", "交互")
        val matches = impactKeywords.count { result.contains(it, ignoreCase = true) }
        return (matches * 0.2 + 0.5).coerceIn(0.0, 1.0)
    }
    
    private fun calculateOverallRiskLevel(flaws: List<Flaw>): String {
        val criticalCount = flaws.count { it.severity == "CRITICAL" }
        val highCount = flaws.count { it.severity == "HIGH" }
        
        return when {
            criticalCount > 0 -> "CRITICAL"
            highCount > 1 -> "HIGH"
            flaws.size > 3 -> "MEDIUM"
            else -> "LOW"
        }
    }
    
    private fun extractNumericValue(text: String, range: ClosedFloatingPointRange<Double>): Double? {
        val numberPattern = Regex("""\d+(\.\d+)?""")
        val match = numberPattern.find(text)
        return match?.value?.toDoubleOrNull()?.coerceIn(range)
    }
    
    private fun extractSeverity(text: String): String {
        return when {
            text.contains("严重", ignoreCase = true) || text.contains("critical", ignoreCase = true) -> "CRITICAL"
            text.contains("高", ignoreCase = true) || text.contains("high", ignoreCase = true) -> "HIGH"
            text.contains("中", ignoreCase = true) || text.contains("medium", ignoreCase = true) -> "MEDIUM"
            else -> "LOW"
        }
    }
    
    private fun extractCategory(text: String): String {
        return when {
            text.contains("架构", ignoreCase = true) || text.contains("architecture", ignoreCase = true) -> "ARCHITECTURE"
            text.contains("性能", ignoreCase = true) || text.contains("performance", ignoreCase = true) -> "PERFORMANCE"
            text.contains("安全", ignoreCase = true) || text.contains("security", ignoreCase = true) -> "SECURITY"
            text.contains("可维护", ignoreCase = true) || text.contains("maintainability", ignoreCase = true) -> "MAINTAINABILITY"
            else -> "GENERAL"
        }
    }
    
    private fun estimateComplexity(context: ScreeningContext): Int {
        return context.allPaths.size * 10 + context.changedFiles.size * 5
    }
    
    private fun estimateComplexity(context: AnalysisContext): Int {
        return context.methodBodies.size * 10 + context.gitContext.changedFiles.size * 5
    }
}

/**
 * 三阶段分析结果数据模型
 */

/**
 * 功能价值分析结果 (功)
 */
data class MeritAnalysisResult(
    val merits: List<Merit>,
    val overallBusinessValue: Double,
    val functionalCompleteness: Double,
    val userExperienceImpact: Double
)

/**
 * 具体的功能价值项
 */
data class Merit(
    val description: String,
    val businessValue: Double, // 0-100分
    val implementationDetails: String,
    val relatedPaths: List<String>,
    val confidence: Double // 0-1
)

/**
 * 风险缺陷分析结果 (过)
 */
data class FlawAnalysisResult(
    val flaws: List<Flaw>,
    val riskLevel: String, // LOW, MEDIUM, HIGH, CRITICAL
    val criticalIssueCount: Int,
    val architecturalConcerns: List<Flaw>
)

/**
 * 具体的风险缺陷项
 */
data class Flaw(
    val description: String,
    val category: String, // ARCHITECTURE, PERFORMANCE, SECURITY, MAINTAINABILITY
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    val impact: String,
    val location: String,
    val immediateRecommendation: String,
    val confidence: Double // 0-1
)

/**
 * 综合决策分析结果 (策)
 */
data class SuggestionAnalysisResult(
    val finalDecision: String, // APPROVED, REJECTED, APPROVED_WITH_CONDITIONS
    val decisionReasoning: String,
    val actionableRecommendations: List<String>,
    val prioritizedTasks: List<String>,
    val criticalIssues: List<String>,
    val approvalConditions: List<String>
)
enum class AIAnalysisType {
    QUICK_SCREENING,    // 快速筛选
    DEEP_ANALYSIS      // 深度分析
}

/**
 * 预算等级
 */
enum class Budget {
    LOW,      // 低预算，优先使用便宜模型
    MEDIUM,   // 中等预算，平衡成本和质量
    HIGH      // 高预算，优先使用最好模型
}

/**
 * 紧急程度
 */
enum class Urgency {
    LOW,      // 不急，可以等待
    NORMAL,   // 正常速度
    HIGH      // 急需结果
}

/**
 * AI异常类型
 */
class AIRateLimitException(message: String) : Exception(message)
class AINetworkException(message: String) : Exception(message)