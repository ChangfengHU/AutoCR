package com.vyibc.autocr.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * AutoCR设置配置页面 - 简化版，专注于Neo4j配置
 */
class AutoCRSettingsConfigurable(private val project: Project) : Configurable {

    private val logger = LoggerFactory.getLogger(AutoCRSettingsConfigurable::class.java)
    private val settings = AutoCRSettingsState.getInstance(project)

    // Neo4j配置组件
    private lateinit var neo4jPanel: Neo4jPanel
    
    // AI配置组件
    private lateinit var aiConfigPanel: AIConfigPanel

    private lateinit var mainPanel: JPanel

    override fun getDisplayName(): String = "AutoCR"

    override fun createComponent(): JComponent? {
        mainPanel = JPanel(BorderLayout())

        val tabbedPane = JBTabbedPane()

        // Neo4j配置标签页
        neo4jPanel = Neo4jPanel()
        tabbedPane.addTab("Neo4j 数据库", neo4jPanel.createPanel())
        
        // AI配置标签页
        aiConfigPanel = AIConfigPanel()
        tabbedPane.addTab("AI 模型配置", aiConfigPanel.createPanel())

        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        // 加载当前设置
        reset()

        return mainPanel
    }

    override fun isModified(): Boolean {
        return neo4jPanel.isModified(settings.neo4jConfig) ||
               aiConfigPanel.isModified(settings.aiConfigs)
    }

    override fun apply() {
        // 验证设置
        val validationErrors = validate()
        if (validationErrors.isNotEmpty()) {
            val errorMessage = validationErrors.joinToString("\n")
            JOptionPane.showMessageDialog(
                mainPanel,
                errorMessage,
                "配置验证失败",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        // 应用设置
        neo4jPanel.applyTo(settings.neo4jConfig)
        
        // 应用AI配置
        settings.aiConfigs.clear()
        settings.aiConfigs.addAll(aiConfigPanel.getConfigs())

        logger.info("AutoCR settings (Neo4j + AI) applied successfully")

        // 通知用户设置已保存
        JOptionPane.showMessageDialog(
            mainPanel,
            "设置已保存成功！\n某些更改可能需要重启IDE才能生效。",
            "设置保存",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    override fun reset() {
        neo4jPanel.loadFrom(settings.neo4jConfig)
        aiConfigPanel.loadFrom(settings.aiConfigs)
    }

    /**
     * 验证所有设置
     */
    private fun validate(): List<String> {
        val errors = mutableListOf<String>()

        // 验证Neo4j设置
        errors.addAll(SettingsValidator.validateNeo4jSettings(neo4jPanel.getCurrentSettings()))

        // 验证AI配置（包含API Key有效性在线校验）
        errors.addAll(aiConfigPanel.validateBeforeApply())

        return errors
    }

    override fun disposeUIResources() {
        // 清理资源
    }
}