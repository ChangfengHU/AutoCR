package com.vyibc.autocr.ai

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * AI负载均衡器
 * 管理多个AI供应商的负载均衡和故障转移
 */
class AILoadBalancer {
    private val logger = LoggerFactory.getLogger(AILoadBalancer::class.java)
    
    // 供应商权重配置
    private val providerWeights = ConcurrentHashMap<AIProvider, Int>()
    
    // 供应商健康状态
    private val providerHealth = ConcurrentHashMap<AIProvider, Boolean>()
    
    // 供应商请求计数（用于轮询）
    private val requestCounters = ConcurrentHashMap<AIProvider, AtomicInteger>()
    
    // 供应商偏好配置（基于请求类型）
    private val requestTypePreferences = mapOf(
        AIRequestType.CODE_REVIEW to listOf(AIProvider.ANTHROPIC, AIProvider.OPENAI, AIProvider.GOOGLE),
        AIRequestType.SECURITY_ANALYSIS to listOf(AIProvider.OPENAI, AIProvider.ANTHROPIC, AIProvider.GOOGLE),
        AIRequestType.PERFORMANCE_ANALYSIS to listOf(AIProvider.OPENAI, AIProvider.GOOGLE, AIProvider.ANTHROPIC),
        AIRequestType.ARCHITECTURE_REVIEW to listOf(AIProvider.ANTHROPIC, AIProvider.OPENAI, AIProvider.GOOGLE),
        AIRequestType.BEST_PRACTICES to listOf(AIProvider.OPENAI, AIProvider.ANTHROPIC, AIProvider.GOOGLE),
        AIRequestType.BUG_DETECTION to listOf(AIProvider.OPENAI, AIProvider.GOOGLE, AIProvider.ANTHROPIC),
        AIRequestType.CODE_QUALITY to listOf(AIProvider.ANTHROPIC, AIProvider.OPENAI, AIProvider.GOOGLE)
    )
    
    init {
        initializeDefaults()
    }
    
    /**
     * 选择最佳AI供应商
     */
    fun selectProvider(
        requestType: AIRequestType,
        priority: Int,
        availableProviders: List<AIProvider>
    ): AIProvider {
        if (availableProviders.isEmpty()) {
            throw IllegalStateException("No available AI providers")
        }
        
        // 根据策略选择供应商
        val strategy = determineSelectionStrategy(priority, availableProviders.size)
        
        return when (strategy) {
            SelectionStrategy.PREFERENCE_BASED -> selectByPreference(requestType, availableProviders)
            SelectionStrategy.WEIGHTED_ROUND_ROBIN -> selectByWeightedRoundRobin(availableProviders)
            SelectionStrategy.HEALTH_AWARE -> selectByHealth(availableProviders)
            SelectionStrategy.LOAD_BALANCED -> selectByLoad(availableProviders)
        }
    }
    
    /**
     * 选择故障转移供应商
     */
    fun selectFallbackProvider(
        failedProvider: AIProvider,
        availableProviders: List<AIProvider>
    ): AIProvider? {
        // 标记失败的供应商为不健康
        providerHealth[failedProvider] = false
        
        // 从可用供应商中排除失败的供应商
        val fallbackCandidates = availableProviders.filter { 
            it != failedProvider && isProviderHealthy(it) 
        }
        
        if (fallbackCandidates.isEmpty()) {
            logger.warn("No healthy fallback providers available")
            return null
        }
        
        // 选择负载最低的健康供应商
        return selectByLoad(fallbackCandidates)
    }
    
    /**
     * 更新供应商健康状态
     */
    fun updateProviderHealth(provider: AIProvider, isHealthy: Boolean) {
        val previousHealth = providerHealth[provider]
        providerHealth[provider] = isHealthy
        
        if (previousHealth != isHealthy) {
            logger.info("Provider {} health changed: {} -> {}", 
                provider, previousHealth, isHealthy)
        }
    }
    
    /**
     * 更新供应商权重
     */
    fun updateProviderWeight(provider: AIProvider, weight: Int) {
        val previousWeight = providerWeights[provider]
        providerWeights[provider] = weight
        
        logger.info("Provider {} weight changed: {} -> {}", 
            provider, previousWeight, weight)
    }
    
    /**
     * 获取负载均衡统计
     */
    fun getLoadBalancingStats(): LoadBalancingStats {
        val providerStats = AIProvider.values().map { provider ->
            ProviderLoadStats(
                provider = provider,
                requestCount = requestCounters[provider]?.get() ?: 0,
                isHealthy = isProviderHealthy(provider),
                weight = providerWeights[provider] ?: 1
            )
        }
        
        return LoadBalancingStats(
            totalRequests = requestCounters.values.sumOf { it.get() },
            healthyProviders = providerStats.count { it.isHealthy },
            totalProviders = providerStats.size,
            providerStats = providerStats
        )
    }
    
    /**
     * 重置统计信息
     */
    fun resetStats() {
        requestCounters.values.forEach { it.set(0) }
        logger.info("Load balancing stats reset")
    }
    
    /**
     * 确定选择策略
     */
    private fun determineSelectionStrategy(priority: Int, providerCount: Int): SelectionStrategy {
        return when {
            priority >= 8 -> SelectionStrategy.PREFERENCE_BASED    // 高优先级使用偏好策略
            priority >= 5 -> SelectionStrategy.HEALTH_AWARE       // 中优先级考虑健康状态
            providerCount > 2 -> SelectionStrategy.LOAD_BALANCED  // 多供应商时负载均衡
            else -> SelectionStrategy.WEIGHTED_ROUND_ROBIN        // 默认加权轮询
        }
    }
    
    /**
     * 基于偏好选择供应商
     */
    private fun selectByPreference(
        requestType: AIRequestType,
        availableProviders: List<AIProvider>
    ): AIProvider {
        val preferences = requestTypePreferences[requestType] ?: AIProvider.values().toList()
        
        // 选择第一个可用且健康的偏好供应商
        for (preferredProvider in preferences) {
            if (preferredProvider in availableProviders && isProviderHealthy(preferredProvider)) {
                incrementRequestCount(preferredProvider)
                logger.debug("Selected provider by preference: {} for {}", preferredProvider, requestType)
                return preferredProvider
            }
        }
        
        // 如果没有偏好供应商可用，回退到负载均衡
        return selectByLoad(availableProviders)
    }
    
    /**
     * 基于加权轮询选择供应商
     */
    private fun selectByWeightedRoundRobin(availableProviders: List<AIProvider>): AIProvider {
        val healthyProviders = availableProviders.filter { isProviderHealthy(it) }
        
        if (healthyProviders.isEmpty()) {
            // 如果没有健康的供应商，选择第一个可用的
            return availableProviders.first().also { incrementRequestCount(it) }
        }
        
        // 计算加权总数
        val totalWeight = healthyProviders.sumOf { providerWeights[it] ?: 1 }
        val random = (1..totalWeight).random()
        
        var currentWeight = 0
        for (provider in healthyProviders) {
            currentWeight += providerWeights[provider] ?: 1
            if (random <= currentWeight) {
                incrementRequestCount(provider)
                logger.debug("Selected provider by weighted round robin: {}", provider)
                return provider
            }
        }
        
        // 备用选择
        return healthyProviders.first().also { incrementRequestCount(it) }
    }
    
    /**
     * 基于健康状态选择供应商
     */
    private fun selectByHealth(availableProviders: List<AIProvider>): AIProvider {
        val healthyProviders = availableProviders.filter { isProviderHealthy(it) }
        
        if (healthyProviders.isEmpty()) {
            logger.warn("No healthy providers available, selecting from all available")
            return availableProviders.first().also { incrementRequestCount(it) }
        }
        
        // 从健康的供应商中随机选择
        val selected = healthyProviders.random()
        incrementRequestCount(selected)
        logger.debug("Selected provider by health: {}", selected)
        return selected
    }
    
    /**
     * 基于负载选择供应商
     */
    private fun selectByLoad(availableProviders: List<AIProvider>): AIProvider {
        val healthyProviders = availableProviders.filter { isProviderHealthy(it) }
        
        if (healthyProviders.isEmpty()) {
            return availableProviders.first().also { incrementRequestCount(it) }
        }
        
        // 选择请求计数最少的供应商
        val selected = healthyProviders.minByOrNull { 
            requestCounters[it]?.get() ?: 0 
        } ?: healthyProviders.first()
        
        incrementRequestCount(selected)
        logger.debug("Selected provider by load: {} (count: {})", 
            selected, requestCounters[selected]?.get())
        return selected
    }
    
    /**
     * 检查供应商是否健康
     */
    private fun isProviderHealthy(provider: AIProvider): Boolean {
        return providerHealth[provider] ?: true // 默认认为是健康的
    }
    
    /**
     * 增加请求计数
     */
    private fun incrementRequestCount(provider: AIProvider) {
        requestCounters.computeIfAbsent(provider) { AtomicInteger(0) }.incrementAndGet()
    }
    
    /**
     * 初始化默认值
     */
    private fun initializeDefaults() {
        // 设置默认权重
        AIProvider.values().forEach { provider ->
            providerWeights[provider] = when (provider) {
                AIProvider.OPENAI -> 3      // 高权重
                AIProvider.ANTHROPIC -> 3   // 高权重
                AIProvider.GOOGLE -> 2      // 中权重
                AIProvider.OLLAMA -> 1      // 低权重（本地模型）
                AIProvider.AZURE_OPENAI -> 2 // 中权重
                AIProvider.ALIBABA_TONGYI -> 3 // 高权重（阿里通义）
                AIProvider.DEEPSEEK -> 2    // 中权重（DeepSeek）
            }
            
            // 默认都是健康的
            providerHealth[provider] = true
            
            // 初始化请求计数器
            requestCounters[provider] = AtomicInteger(0)
        }
        
        logger.info("Initialized load balancer with {} providers", AIProvider.values().size)
    }
}

/**
 * 选择策略
 */
enum class SelectionStrategy {
    PREFERENCE_BASED,      // 基于偏好
    WEIGHTED_ROUND_ROBIN,  // 加权轮询
    HEALTH_AWARE,          // 健康感知
    LOAD_BALANCED          // 负载均衡
}

/**
 * 负载均衡统计
 */
data class LoadBalancingStats(
    val totalRequests: Int,
    val healthyProviders: Int,
    val totalProviders: Int,
    val providerStats: List<ProviderLoadStats>
)

/**
 * 供应商负载统计
 */
data class ProviderLoadStats(
    val provider: AIProvider,
    val requestCount: Int,
    val isHealthy: Boolean,
    val weight: Int
)