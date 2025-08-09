package com.vyibc.autocr.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * AutoCR插件配置状态
 * 使用IntelliJ平台的状态持久化机制
 */
@State(
    name = "AutoCRSettings",
    storages = [Storage("autoCRSettings.xml")]
)
@Service(Service.Level.PROJECT)
class AutoCRSettingsState : PersistentStateComponent<AutoCRSettingsState> {

    // Neo4j配置
    var neo4jConfig = Neo4jSettings()

    // AI供应商配置
    var aiConfigs = mutableListOf<AIModelConfig>()
    
    // 通用设置
    var generalSettings = GeneralSettings()

    override fun getState(): AutoCRSettingsState = this

    override fun loadState(state: AutoCRSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): AutoCRSettingsState {
            return project.service()
        }
    }
}

/**
 * AI供应商类型
 */
enum class AIProvider(val displayName: String, val emoji: String) {
    OPENAI("OpenAI", "🤖"),
    ANTHROPIC("Anthropic", "🧠"),
    GOOGLE("Google Gemini", "🌟"),
    ALIBABA_TONGYI("阿里通义千问", "☁️"),
    DEEPSEEK("DeepSeek", "🔍"),
    OPENROUTER("OpenRouter", "🚀")
}

/**
 * AI模型配置
 */
data class AIModelConfig(
    var id: String = "",
    var name: String = "",
    var provider: AIProvider = AIProvider.OPENAI,
    var modelName: String = "",
    var apiKey: String = "",
    var baseUrl: String = "",
    var maxTokens: Int = 4000,
    var temperature: Double = 0.1,
    var timeout: Long = 30000L,
    var enabled: Boolean = true
) {
    constructor() : this("", "", AIProvider.OPENAI, "", "", "", 4000, 0.1, 30000L, true)
}

/**
 * Neo4j数据库配置
 */
data class Neo4jSettings(
    var enabled: Boolean = false,
    var uri: String = "bolt://localhost:7687",
    var username: String = "neo4j",
    var password: String = "",
    var database: String = "autocr",
    var maxConnectionPoolSize: Int = 10,
    var connectionTimeout: Long = 5000L,
    var maxTransactionRetryTime: Long = 30000L
) {
    constructor() : this(false, "bolt://localhost:7687", "neo4j", "", "autocr", 10, 5000L, 30000L)
}

/**
 * 通用设置
 */
data class GeneralSettings(
    var enableCache: Boolean = true,
    var cacheExpireTime: Long = 3600000L, // 1小时
    var maxCacheSize: Long = 1000L,
    var enableNotifications: Boolean = true,
    var logLevel: String = "INFO",
    var excludeFilePatterns: MutableList<String> = mutableListOf("*.test.java", "*.spec.js", "target/**", "build/**")
) {
    constructor() : this(true, 3600000L, 1000L, true, "INFO", mutableListOf())
}

/**
 * 设置验证器
 */
object SettingsValidator {

    fun validateNeo4jSettings(settings: Neo4jSettings): List<String> {
        val errors = mutableListOf<String>()

        if (settings.enabled) {
            if (settings.uri.isBlank()) {
                errors.add("Neo4j URI不能为空")
            } else if (!settings.uri.startsWith("bolt://") && !settings.uri.startsWith("neo4j://")) {
                errors.add("Neo4j URI必须以bolt://或neo4j://开头")
            }

            if (settings.username.isBlank()) {
                errors.add("Neo4j 用户名不能为空")
            }

            if (settings.password.isBlank()) {
                errors.add("Neo4j 密码不能为空")
            }

            if (settings.database.isBlank()) {
                errors.add("Neo4j 数据库名不能为空")
            }

            if (settings.maxConnectionPoolSize <= 0) {
                errors.add("最大连接池大小必须大于0")
            }

            if (settings.connectionTimeout <= 0) {
                errors.add("连接超时时间必须大于0")
            }
        }

        return errors
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            java.net.URL(url)
            true
        } catch (e: Exception) {
            false
        }
    }
}