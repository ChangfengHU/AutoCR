package com.vyibc.autocrplugin.service

import com.vyibc.autocrplugin.service.impl.DeepSeekService
import com.vyibc.autocrplugin.service.impl.GeminiService
import com.vyibc.autocrplugin.service.impl.TongyiService
import com.vyibc.autocrplugin.settings.CodeReviewSettings

/**
 * AI服务管理器
 * 负责管理多个AI服务，提供服务选择和故障转移功能
 */
class AIServiceManager {
    
    private val settings = CodeReviewSettings.getInstance()
    
    /**
     * 获取可用的AI服务列表
     */
    fun getAvailableServices(): List<AIService> {
        val services = mutableListOf<AIService>()
        
        // DeepSeek服务
        if (settings.deepSeekEnabled && settings.deepSeekApiKey.isNotBlank()) {
            services.add(DeepSeekService(settings.deepSeekApiKey))
        }
        
        // 通义千问服务
        if (settings.tongyiEnabled && settings.tongyiApiKey.isNotBlank()) {
            services.add(TongyiService(settings.tongyiApiKey))
        }
        
        // Gemini服务
        if (settings.geminiEnabled && settings.geminiApiKey.isNotBlank()) {
            services.add(GeminiService(settings.geminiApiKey))
        }
        
        return services
    }
    
    /**
     * 获取首选的AI服务
     */
    fun getPrimaryService(): AIService? {
        val services = getAvailableServices()
        
        // 根据设置的优先级返回服务
        return when (settings.primaryAIService) {
            AIServiceType.DEEPSEEK -> services.find { it is DeepSeekService }
            AIServiceType.TONGYI -> services.find { it is TongyiService }
            AIServiceType.GEMINI -> services.find { it is GeminiService }
            else -> services.firstOrNull()
        } ?: services.firstOrNull()
    }
    
    /**
     * 调用AI服务，支持故障转移
     */
    suspend fun callAIWithFallback(prompt: String): String {
        val services = getAvailableServices()
        
        if (services.isEmpty()) {
            throw Exception("没有可用的AI服务，请检查配置")
        }
        
        // 首先尝试主要服务
        val primaryService = getPrimaryService()
        if (primaryService != null) {
            try {
                return primaryService.callAI(prompt)
            } catch (e: Exception) {
                println("主要AI服务调用失败: ${e.message}")
            }
        }
        
        // 如果主要服务失败，尝试其他服务
        for (service in services) {
            if (service == primaryService) continue // 跳过已经尝试过的主要服务
            
            try {
                return service.callAI(prompt)
            } catch (e: Exception) {
                println("AI服务 ${service.getServiceName()} 调用失败: ${e.message}")
            }
        }
        
        throw Exception("所有AI服务都不可用")
    }
    
    /**
     * 检查服务可用性
     */
    suspend fun checkServiceAvailability(): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        
        val services = listOf(
            DeepSeekService(settings.deepSeekApiKey),
            TongyiService(settings.tongyiApiKey),
            GeminiService(settings.geminiApiKey)
        )
        
        for (service in services) {
            try {
                result[service.getServiceName()] = service.isAvailable()
            } catch (e: Exception) {
                result[service.getServiceName()] = false
            }
        }
        
        return result
    }
    
    /**
     * 获取服务统计信息
     */
    fun getServiceStats(): Map<String, Any> {
        val services = getAvailableServices()
        return mapOf(
            "totalServices" to services.size,
            "primaryService" to (getPrimaryService()?.getServiceName() ?: "None"),
            "availableServices" to services.map { it.getServiceName() }
        )
    }
}
