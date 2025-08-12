package com.vyibc.autocrplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import com.vyibc.autocrplugin.service.AIServiceType

/**
 * 代码评估设置状态类
 */
@State(
    name = "CodeReviewSettings",
    storages = [Storage("CodeReviewSettings.xml")]
)
class CodeReviewSettings : PersistentStateComponent<CodeReviewSettings> {

    // AI服务配置
    var primaryAIService: AIServiceType = AIServiceType.DEEPSEEK
    
    // DeepSeek配置
    var deepSeekEnabled: Boolean = true
    var deepSeekApiKey: String = ""
    
    // 通义千问配置
    var tongyiEnabled: Boolean = true
    var tongyiApiKey: String = ""
    
    // Gemini配置
    var geminiEnabled: Boolean = true
    var geminiApiKey: String = ""
    
    // 评估配置
    var autoReviewOnCommit: Boolean = false
    var showDetailedResults: Boolean = true
    var blockHighRiskCommits: Boolean = false
    var minimumScore: Int = 60
    
    // 评估规则配置
    var checkCodeStyle: Boolean = true
    var checkPerformance: Boolean = true
    var checkSecurity: Boolean = true
    var checkBugRisk: Boolean = true
    var checkMaintainability: Boolean = true
    var checkDocumentation: Boolean = false
    var checkBestPractices: Boolean = true
    
    // 高级配置
    var maxTokens: Int = 2000
    var temperature: Double = 0.1
    var enableFallback: Boolean = true

    // AI提示词配置
    var customPrompt: String = ""
    var requestTimeout: Int = 30 // 秒
    
    // 方法调用分析配置
    var maxCascadeDepth: Int = 1 // 级联分析深度，默认2层

    companion object {
        fun getInstance(): CodeReviewSettings {
            return ApplicationManager.getApplication().getService(CodeReviewSettings::class.java)
        }
    }

    override fun getState(): CodeReviewSettings = this

    override fun loadState(state: CodeReviewSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
