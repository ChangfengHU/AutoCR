package com.vyibc.autocr.service

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * AI提供商接口
 * 定义所有AI提供商适配器的通用接口
 */
interface AIProvider {
    suspend fun analyze(prompt: String, analysisType: AIAnalysisType): String
    fun getCapabilities(): AICapabilities
    fun getCostPerToken(): Double
    fun getLatencyEstimate(): kotlin.time.Duration
    fun getMaxTokens(): Int
    fun isHealthy(): Boolean
    fun isLocal(): Boolean
    fun getName(): String
}

/**
 * AI能力描述
 */
data class AICapabilities(
    val maxTokens: Int,
    val supportsCodeAnalysis: Boolean,
    val supportsFunctionCalling: Boolean,
    val reasoningCapability: ReasoningLevel,
    val costTier: CostTier
)

/**
 * 推理能力等级
 */
enum class ReasoningLevel {
    BASIC,      // 基础推理能力
    MODERATE,   // 中等推理能力
    ADVANCED    // 高级推理能力
}

/**
 * 成本等级
 */
enum class CostTier {
    FREE,       // 免费
    LOW,        // 低成本
    MEDIUM,     // 中等成本
    HIGH        // 高成本
}

/**
 * OpenAI提供商适配器
 */
class OpenAIProviderAdapter(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : AIProvider {
    
    private val logger = LoggerFactory.getLogger(OpenAIProviderAdapter::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    override suspend fun analyze(prompt: String, analysisType: AIAnalysisType): String {
        logger.debug("使用OpenAI模型分析: $model")
        
        val requestBody = buildOpenAIRequest(prompt, analysisType)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(120))
            .build()
        
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            when (response.statusCode()) {
                200 -> parseOpenAIResponse(response.body())
                429 -> throw AIRateLimitException("OpenAI API rate limit exceeded")
                else -> throw RuntimeException("OpenAI API error: ${response.statusCode()}")
            }
        } catch (e: java.net.ConnectException) {
            throw AINetworkException("Unable to connect to OpenAI API")
        }
    }
    
    override fun getCapabilities(): AICapabilities {
        return AICapabilities(
            maxTokens = when (model) {
                "gpt-4o", "gpt-4o-mini" -> 128000
                "gpt-4-turbo" -> 128000
                "gpt-3.5-turbo" -> 16385
                else -> 4096
            },
            supportsCodeAnalysis = true,
            supportsFunctionCalling = true,
            reasoningCapability = ReasoningLevel.ADVANCED,
            costTier = CostTier.HIGH
        )
    }
    
    override fun getCostPerToken(): Double {
        return when (model) {
            "gpt-4o" -> 0.00005  // $0.05 per 1K tokens
            "gpt-4o-mini" -> 0.000015  // $0.015 per 1K tokens  
            "gpt-4-turbo" -> 0.00003  // $0.03 per 1K tokens
            "gpt-3.5-turbo" -> 0.000002  // $0.002 per 1K tokens
            else -> 0.00001
        }
    }
    
    override fun getLatencyEstimate(): kotlin.time.Duration = 3.seconds
    override fun getMaxTokens(): Int = getCapabilities().maxTokens
    override fun isLocal(): Boolean = false
    override fun getName(): String = "OpenAI-$model"
    
    override fun isHealthy(): Boolean {
        return try {
            val testRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/v1/models"))
                .header("Authorization", "Bearer $apiKey")
                .timeout(Duration.ofSeconds(10))
                .build()
            
            val response = client.send(testRequest, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
    
    private fun buildOpenAIRequest(prompt: String, analysisType: AIAnalysisType): String {
        val temperature = when (analysisType) {
            AIAnalysisType.QUICK_SCREENING -> 0.3  // 更确定性的结果
            AIAnalysisType.DEEP_ANALYSIS -> 0.7   // 更有创造性的分析
        }
        
        return """
        {
            "model": "$model",
            "messages": [
                {
                    "role": "system",
                    "content": "You are a senior software architect performing code review analysis."
                },
                {
                    "role": "user", 
                    "content": "$prompt"
                }
            ],
            "temperature": $temperature,
            "max_tokens": ${getMaxTokens() / 2},
            "top_p": 1,
            "frequency_penalty": 0,
            "presence_penalty": 0
        }
        """.trimIndent()
    }
    
    private fun parseOpenAIResponse(responseBody: String): String {
        // 简化的JSON解析，实际应该使用JSON库
        return try {
            // 提取content字段的内容
            val contentStart = responseBody.indexOf("\"content\":\"") + 11
            val contentEnd = responseBody.indexOf("\"", contentStart)
            
            if (contentStart > 10 && contentEnd > contentStart) {
                responseBody.substring(contentStart, contentEnd)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
            } else {
                responseBody
            }
        } catch (e: Exception) {
            responseBody
        }
    }
}

/**
 * Anthropic提供商适配器
 */
class AnthropicProviderAdapter(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : AIProvider {
    
    private val logger = LoggerFactory.getLogger(AnthropicProviderAdapter::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    override suspend fun analyze(prompt: String, analysisType: AIAnalysisType): String {
        logger.debug("使用Anthropic模型分析: $model")
        
        val requestBody = buildAnthropicRequest(prompt, analysisType)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(120))
            .build()
        
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            when (response.statusCode()) {
                200 -> parseAnthropicResponse(response.body())
                429 -> throw AIRateLimitException("Anthropic API rate limit exceeded")
                else -> throw RuntimeException("Anthropic API error: ${response.statusCode()}")
            }
        } catch (e: java.net.ConnectException) {
            throw AINetworkException("Unable to connect to Anthropic API")
        }
    }
    
    override fun getCapabilities(): AICapabilities {
        return AICapabilities(
            maxTokens = when (model) {
                "claude-3-5-sonnet-20241022" -> 200000
                "claude-3-opus-20240229" -> 200000
                "claude-3-haiku-20240307" -> 200000
                else -> 100000
            },
            supportsCodeAnalysis = true,
            supportsFunctionCalling = false,
            reasoningCapability = ReasoningLevel.ADVANCED,
            costTier = CostTier.HIGH
        )
    }
    
    override fun getCostPerToken(): Double {
        return when (model) {
            "claude-3-opus-20240229" -> 0.000075  // $0.075 per 1K tokens
            "claude-3-5-sonnet-20241022" -> 0.000015  // $0.015 per 1K tokens
            "claude-3-haiku-20240307" -> 0.0000025  // $0.0025 per 1K tokens
            else -> 0.00003
        }
    }
    
    override fun getLatencyEstimate(): kotlin.time.Duration = 4.seconds
    override fun getMaxTokens(): Int = getCapabilities().maxTokens
    override fun isLocal(): Boolean = false
    override fun getName(): String = "Anthropic-$model"
    
    override fun isHealthy(): Boolean {
        return try {
            // Anthropic没有直接的健康检查端点，使用简单请求测试
            val testRequestBody = """
            {
                "model": "$model",
                "max_tokens": 10,
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello"
                    }
                ]
            }
            """.trimIndent()
            
            val testRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(testRequestBody))
                .timeout(Duration.ofSeconds(10))
                .build()
            
            val response = client.send(testRequest, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
    
    private fun buildAnthropicRequest(prompt: String, analysisType: AIAnalysisType): String {
        return """
        {
            "model": "$model",
            "max_tokens": ${getMaxTokens() / 2},
            "messages": [
                {
                    "role": "user",
                    "content": "$prompt"
                }
            ]
        }
        """.trimIndent()
    }
    
    private fun parseAnthropicResponse(responseBody: String): String {
        // 简化的JSON解析
        return try {
            val contentStart = responseBody.indexOf("\"text\":\"") + 8
            val contentEnd = responseBody.indexOf("\"", contentStart)
            
            if (contentStart > 7 && contentEnd > contentStart) {
                responseBody.substring(contentStart, contentEnd)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
            } else {
                responseBody
            }
        } catch (e: Exception) {
            responseBody
        }
    }
}

/**
 * 其他AI提供商适配器的简化实现
 */
class GoogleProviderAdapter(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : AIProvider {
    
    override suspend fun analyze(prompt: String, analysisType: AIAnalysisType): String {
        // 模拟Google Gemini API调用
        delay(2.seconds)
        return "Google AI 分析结果（模拟）: 基于Gemini模型的代码分析"
    }
    
    override fun getCapabilities(): AICapabilities {
        return AICapabilities(
            maxTokens = 32768,
            supportsCodeAnalysis = true,
            supportsFunctionCalling = true,
            reasoningCapability = ReasoningLevel.ADVANCED,
            costTier = CostTier.MEDIUM
        )
    }
    
    override fun getCostPerToken(): Double = 0.00002
    override fun getLatencyEstimate(): kotlin.time.Duration = 2.seconds
    override fun getMaxTokens(): Int = 32768
    override fun isHealthy(): Boolean = true
    override fun isLocal(): Boolean = false
    override fun getName(): String = "Google-$model"
}

class AlibabaTongyiProviderAdapter(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : AIProvider {
    
    override suspend fun analyze(prompt: String, analysisType: AIAnalysisType): String {
        delay(3.seconds)
        return "阿里通义千问分析结果（模拟）: 基于通义千问的代码分析"
    }
    
    override fun getCapabilities(): AICapabilities {
        return AICapabilities(
            maxTokens = 8192,
            supportsCodeAnalysis = true,
            supportsFunctionCalling = false,
            reasoningCapability = ReasoningLevel.MODERATE,
            costTier = CostTier.LOW
        )
    }
    
    override fun getCostPerToken(): Double = 0.000005
    override fun getLatencyEstimate(): kotlin.time.Duration = 3.seconds
    override fun getMaxTokens(): Int = 8192
    override fun isHealthy(): Boolean = true
    override fun isLocal(): Boolean = false
    override fun getName(): String = "Alibaba-$model"
}

class DeepSeekProviderAdapter(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : AIProvider {
    
    override suspend fun analyze(prompt: String, analysisType: AIAnalysisType): String {
        delay(2500.milliseconds)
        return "DeepSeek分析结果（模拟）: 基于DeepSeek模型的代码分析"
    }
    
    override fun getCapabilities(): AICapabilities {
        return AICapabilities(
            maxTokens = 32768,
            supportsCodeAnalysis = true,
            supportsFunctionCalling = true,
            reasoningCapability = ReasoningLevel.ADVANCED,
            costTier = CostTier.LOW
        )
    }
    
    override fun getCostPerToken(): Double = 0.000002
    override fun getLatencyEstimate(): kotlin.time.Duration = 2.5.seconds
    override fun getMaxTokens(): Int = 32768
    override fun isHealthy(): Boolean = true
    override fun isLocal(): Boolean = false
    override fun getName(): String = "DeepSeek-$model"
}

class OpenRouterProviderAdapter(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : AIProvider {
    
    override suspend fun analyze(prompt: String, analysisType: AIAnalysisType): String {
        delay(4.seconds)
        return "OpenRouter分析结果（模拟）: 通过OpenRouter路由的AI分析"
    }
    
    override fun getCapabilities(): AICapabilities {
        return AICapabilities(
            maxTokens = 128000,
            supportsCodeAnalysis = true,
            supportsFunctionCalling = true,
            reasoningCapability = ReasoningLevel.ADVANCED,
            costTier = CostTier.MEDIUM
        )
    }
    
    override fun getCostPerToken(): Double = 0.00003
    override fun getLatencyEstimate(): kotlin.time.Duration = 4.seconds
    override fun getMaxTokens(): Int = 128000
    override fun isHealthy(): Boolean = true
    override fun isLocal(): Boolean = false
    override fun getName(): String = "OpenRouter-$model"
}