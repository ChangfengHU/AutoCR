//package com.vyibc.autocr.settings
//
//import com.intellij.openapi.components.*
//import com.intellij.openapi.project.Project
//import com.intellij.util.xmlb.XmlSerializerUtil
//import com.vyibc.autocr.ai.AIProvider
//
///**
// * AutoCR插件配置状态
// * 使用IntelliJ平台的状态持久化机制
// */
//@State(
//    name = "AutoCRSettings",
//    storages = [Storage("autoCRSettings.xml")]
//)
//@Service(Service.Level.PROJECT)
//class AutoCRSettingsState : PersistentStateComponent<AutoCRSettingsState> {
//
//    // AI供应商配置
//    var openAIConfig = AIProviderSettings()
//    var anthropicConfig = AIProviderSettings()
//    var googleConfig = AIProviderSettings()
//    var ollamaConfig = AIProviderSettings()
//    var azureOpenAIConfig = AIProviderSettings()
//    var alibabaConfig = AIProviderSettings()  // 阿里通义千问
//    var deepseekConfig = AIProviderSettings() // DeepSeek
//
//    // Neo4j配置
//    var neo4jConfig = Neo4jSettings()
//
//    // 提示词模板配置
//    var promptTemplates = PromptTemplateSettings()
//
//    // 通用设置
//    var generalSettings = GeneralSettings()
//
//    override fun getState(): AutoCRSettingsState = this
//
//    override fun loadState(state: AutoCRSettingsState) {
//        XmlSerializerUtil.copyBean(state, this)
//    }
//
//    companion object {
//        fun getInstance(project: Project): AutoCRSettingsState {
//            return project.service()
//        }
//    }
//}
//
///**
// * AI供应商配置
// */
//data class AIProviderSettings(
//    var enabled: Boolean = false,
//    var apiKey: String = "",
//    var baseUrl: String = "",
//    var modelName: String = "",
//    var maxTokens: Int = 4000,
//    var temperature: Double = 0.1,
//    var timeout: Long = 30000L,
//    var requestsPerMinute: Int = 60,
//    var priority: Int = 1 // 1-10, 数字越大优先级越高
//) {
//    // 默认构造函数，用于XML序列化
//    constructor() : this(false, "", "", "", 4000, 0.1, 30000L, 60, 1)
//}
//
///**
// * Neo4j数据库配置
// */
//data class Neo4jSettings(
//    var enabled: Boolean = false,
//    var uri: String = "bolt://localhost:7687",
//    var username: String = "neo4j",
//    var password: String = "",
//    var database: String = "autocr",
//    var maxConnectionPoolSize: Int = 10,
//    var connectionTimeout: Long = 5000L,
//    var maxTransactionRetryTime: Long = 30000L
//) {
//    constructor() : this(false, "bolt://localhost:7687", "neo4j", "", "autocr", 10, 5000L, 30000L)
//}
//
///**
// * 提示词模板配置
// */
//data class PromptTemplateSettings(
//    var codeReviewTemplate: String = DEFAULT_CODE_REVIEW_TEMPLATE,
//    var securityAnalysisTemplate: String = DEFAULT_SECURITY_ANALYSIS_TEMPLATE,
//    var performanceAnalysisTemplate: String = DEFAULT_PERFORMANCE_ANALYSIS_TEMPLATE,
//    var customTemplates: MutableMap<String, String> = mutableMapOf()
//) {
//    constructor() : this(DEFAULT_CODE_REVIEW_TEMPLATE, DEFAULT_SECURITY_ANALYSIS_TEMPLATE, DEFAULT_PERFORMANCE_ANALYSIS_TEMPLATE, mutableMapOf())
//
//    companion object {
//        val DEFAULT_CODE_REVIEW_TEMPLATE = """
//请对以下代码变更进行全面的代码评审：
//
//## 变更概览11
//- 总文件数: {{totalFiles}}
//- 高优先级文件: {{highPriorityFiles}}
//- 中优先级文件: {{mediumPriorityFiles}}
//- 低优先级文件: {{lowPriorityFiles}}
//- 预估评审时间: {{estimatedTime}} 分钟
//
//## 主要风险因素
//{{topRiskFactors}}
//
//## 建议评审顺序
//{{recommendedOrder}}
//
//请提供以下方面的评审意见：
//1. 代码质量和可维护性
//2. 安全风险评估
//3. 性能影响分析
//4. 架构设计建议
//5. 最佳实践推荐
//6. 潜在bug和问题
//
//请使用结构化的格式回复，包含评级（1-10分）和具体建议。
//        """.trimIndent()
//
//        val DEFAULT_SECURITY_ANALYSIS_TEMPLATE = """
//请对以下方法进行安全分析：
//
//{{methodList}}
//
//请重点关注：
//1. 输入验证和清理
//2. SQL注入风险
//3. XSS攻击风险
//4. 权限控制问题
//5. 敏感数据处理
//6. 加密和解密操作
//7. 会话管理安全
//8. API安全最佳实践
//
//请提供安全风险评级（1-10）和具体的安全建议。
//        """.trimIndent()
//
//        val DEFAULT_PERFORMANCE_ANALYSIS_TEMPLATE = """
//请对以下高复杂度方法进行性能分析：
//
//{{methodList}}
//
//请重点评估：
//1. 算法复杂度和效率
//2. 数据库查询优化
//3. 缓存策略建议
//4. 内存使用优化
//5. 并发性能考虑
//6. I/O操作优化
//7. 循环和递归优化
//8. 资源管理最佳实践
//
//请提供性能风险评级（1-10）和具体的优化建议。
//        """.trimIndent()
//    }
//}
//
///**
// * 通用设置
// */
//data class GeneralSettings(
//    var enableCache: Boolean = true,
//    var cacheExpireTime: Long = 3600000L, // 1小时
//    var maxCacheSize: Long = 1000L,
//    var enableTelemetry: Boolean = true,
//    var logLevel: String = "INFO",
//    var enableNotifications: Boolean = true,
//    var autoStartReview: Boolean = false,
//    var defaultSourceBranch: String = "main",
//    var excludeFilePatterns: MutableList<String> = mutableListOf("*.test.java", "*.spec.js", "target/**", "build/**")
//) {
//    constructor() : this(true, 3600000L, 1000L, true, "INFO", true, false, "main", mutableListOf())
//}
//
///**
// * 设置验证器
// */
//object SettingsValidator {
//
//    fun validateAIProviderSettings(settings: AIProviderSettings, provider: AIProvider): List<String> {
//        val errors = mutableListOf<String>()
//
//        if (settings.enabled) {
//            if (settings.apiKey.isBlank() && provider != AIProvider.OLLAMA) {
//                errors.add("${provider.name} API Key不能为空")
//            }
//
//            if (settings.modelName.isBlank()) {
//                errors.add("${provider.name} 模型名称不能为空")
//            }
//
//            if (settings.maxTokens <= 0 || settings.maxTokens > 100000) {
//                errors.add("${provider.name} 最大Token数必须在1-100000之间")
//            }
//
//            if (settings.temperature < 0.0 || settings.temperature > 2.0) {
//                errors.add("${provider.name} 温度参数必须在0.0-2.0之间")
//            }
//
//            if (settings.timeout <= 0) {
//                errors.add("${provider.name} 超时时间必须大于0")
//            }
//
//            if (settings.requestsPerMinute <= 0 || settings.requestsPerMinute > 10000) {
//                errors.add("${provider.name} 每分钟请求数必须在1-10000之间")
//            }
//
//            if (settings.baseUrl.isNotBlank() && !isValidUrl(settings.baseUrl)) {
//                errors.add("${provider.name} 基础URL格式不正确")
//            }
//        }
//
//        return errors
//    }
//
//    fun validateNeo4jSettings(settings: Neo4jSettings): List<String> {
//        val errors = mutableListOf<String>()
//
//        if (settings.enabled) {
//            if (settings.uri.isBlank()) {
//                errors.add("Neo4j URI不能为空")
//            } else if (!settings.uri.startsWith("bolt://") && !settings.uri.startsWith("neo4j://")) {
//                errors.add("Neo4j URI必须以bolt://或neo4j://开头")
//            }
//
//            if (settings.username.isBlank()) {
//                errors.add("Neo4j 用户名不能为空")
//            }
//
//            if (settings.password.isBlank()) {
//                errors.add("Neo4j 密码不能为空")
//            }
//
//            if (settings.database.isBlank()) {
//                errors.add("Neo4j 数据库名不能为空")
//            }
//
//            if (settings.maxConnectionPoolSize <= 0) {
//                errors.add("最大连接池大小必须大于0")
//            }
//
//            if (settings.connectionTimeout <= 0) {
//                errors.add("连接超时时间必须大于0")
//            }
//        }
//
//        return errors
//    }
//
//    fun validatePromptTemplate(template: String, templateName: String): List<String> {
//        val errors = mutableListOf<String>()
//
//        if (template.isBlank()) {
//            errors.add("$templateName 模板不能为空")
//        }
//
//        if (template.length > 50000) {
//            errors.add("$templateName 模板长度不能超过50000字符")
//        }
//
//        // 检查模板变量格式
//        val variablePattern = Regex("\\{\\{\\w+\\}\\}")
//        val variables = variablePattern.findAll(template).map { it.value }.toSet()
//
//        // 这里可以添加必需变量的检查
//        if (templateName == "代码评审模板") {
//            val requiredVars = setOf("{{totalFiles}}", "{{topRiskFactors}}")
//            val missingVars = requiredVars.filter { !variables.contains(it) }
//            if (missingVars.isNotEmpty()) {
//                errors.add("$templateName 缺少必需变量: ${missingVars.joinToString(", ")}")
//            }
//        }
//
//        return errors
//    }
//
//    private fun isValidUrl(url: String): Boolean {
//        return try {
//            java.net.URL(url)
//            true
//        } catch (e: Exception) {
//            false
//        }
//    }
//}