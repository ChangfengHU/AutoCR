package com.vyibc.autocrplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.vyibc.autocrplugin.service.AIServiceType
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * 代码评估设置配置页面
 */
class CodeReviewConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    
    // AI服务配置
    private lateinit var primaryServiceCombo: ComboBox<AIServiceType>
    private lateinit var deepSeekEnabledCheckBox: JBCheckBox
    private lateinit var deepSeekApiKeyField: JBPasswordField
    private lateinit var tongyiEnabledCheckBox: JBCheckBox
    private lateinit var tongyiApiKeyField: JBPasswordField
    private lateinit var geminiEnabledCheckBox: JBCheckBox
    private lateinit var geminiApiKeyField: JBPasswordField
    
    // 评估配置
    private lateinit var autoReviewCheckBox: JBCheckBox
    private lateinit var showDetailedResultsCheckBox: JBCheckBox
    private lateinit var blockHighRiskCheckBox: JBCheckBox
    private lateinit var minimumScoreSpinner: JSpinner
    
    // 评估规则配置
    private lateinit var checkCodeStyleCheckBox: JBCheckBox
    private lateinit var checkPerformanceCheckBox: JBCheckBox
    private lateinit var checkSecurityCheckBox: JBCheckBox
    private lateinit var checkBugRiskCheckBox: JBCheckBox
    private lateinit var checkMaintainabilityCheckBox: JBCheckBox
    private lateinit var checkDocumentationCheckBox: JBCheckBox
    private lateinit var checkBestPracticesCheckBox: JBCheckBox

    override fun getDisplayName(): String = "代码评估设置"

    override fun createComponent(): JComponent? {
        if (settingsPanel == null) {
            settingsPanel = createSettingsPanel()
        }
        return settingsPanel
    }

    private fun createSettingsPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST

        var row = 0

        // AI服务配置部分
        addSectionTitle(panel, gbc, row++, "AI服务配置")
        
        // 主要AI服务选择
        gbc.gridx = 0
        gbc.gridy = row
        panel.add(JLabel("主要AI服务:"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        primaryServiceCombo = ComboBox(AIServiceType.values())
        panel.add(primaryServiceCombo, gbc)
        row++

        // DeepSeek配置
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        deepSeekEnabledCheckBox = JBCheckBox("启用 DeepSeek")
        panel.add(deepSeekEnabledCheckBox, gbc)

        gbc.gridx = 0
        gbc.gridy = row + 1
        panel.add(JLabel("DeepSeek API Key:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        deepSeekApiKeyField = JBPasswordField()
        panel.add(deepSeekApiKeyField, gbc)
        row += 2

        // 通义千问配置
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        tongyiEnabledCheckBox = JBCheckBox("启用 阿里通义千问")
        panel.add(tongyiEnabledCheckBox, gbc)

        gbc.gridx = 0
        gbc.gridy = row + 1
        panel.add(JLabel("通义千问 API Key:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        tongyiApiKeyField = JBPasswordField()
        panel.add(tongyiApiKeyField, gbc)
        row += 2

        // Gemini配置
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        geminiEnabledCheckBox = JBCheckBox("启用 Google Gemini")
        panel.add(geminiEnabledCheckBox, gbc)

        gbc.gridx = 0
        gbc.gridy = row + 1
        panel.add(JLabel("Gemini API Key:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        geminiApiKeyField = JBPasswordField()
        panel.add(geminiApiKeyField, gbc)
        row += 2

        // 评估配置部分
        addSectionTitle(panel, gbc, row++, "评估配置")
        
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        autoReviewCheckBox = JBCheckBox("提交时自动进行代码评估")
        panel.add(autoReviewCheckBox, gbc)
        row++

        gbc.gridy = row
        showDetailedResultsCheckBox = JBCheckBox("显示详细评估结果")
        panel.add(showDetailedResultsCheckBox, gbc)
        row++

        gbc.gridy = row
        blockHighRiskCheckBox = JBCheckBox("阻止高风险代码提交")
        panel.add(blockHighRiskCheckBox, gbc)
        row++

        // 最低评分设置
        gbc.gridwidth = 1
        gbc.gridy = row
        gbc.gridx = 0
        panel.add(JLabel("最低评分要求:"), gbc)
        
        gbc.gridx = 1
        minimumScoreSpinner = JSpinner(SpinnerNumberModel(60, 0, 100, 5))
        panel.add(minimumScoreSpinner, gbc)
        row++

        // 评估规则配置部分
        addSectionTitle(panel, gbc, row++, "评估规则")
        
        // 创建各个检查项的复选框
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        checkCodeStyleCheckBox = JBCheckBox("代码风格检查")
        panel.add(checkCodeStyleCheckBox, gbc)
        row++

        gbc.gridy = row
        checkPerformanceCheckBox = JBCheckBox("性能问题检查")
        panel.add(checkPerformanceCheckBox, gbc)
        row++

        gbc.gridy = row
        checkSecurityCheckBox = JBCheckBox("安全风险检查")
        panel.add(checkSecurityCheckBox, gbc)
        row++

        gbc.gridy = row
        checkBugRiskCheckBox = JBCheckBox("Bug风险检查")
        panel.add(checkBugRiskCheckBox, gbc)
        row++

        gbc.gridy = row
        checkMaintainabilityCheckBox = JBCheckBox("可维护性检查")
        panel.add(checkMaintainabilityCheckBox, gbc)
        row++

        gbc.gridy = row
        checkDocumentationCheckBox = JBCheckBox("文档检查")
        panel.add(checkDocumentationCheckBox, gbc)
        row++

        gbc.gridy = row
        checkBestPracticesCheckBox = JBCheckBox("最佳实践检查")
        panel.add(checkBestPracticesCheckBox, gbc)
        row++

        return panel
    }

    private fun addSectionTitle(panel: JPanel, gbc: GridBagConstraints, row: Int, title: String) {
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        val titleLabel = JLabel(title)
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.style or java.awt.Font.BOLD)
        panel.add(titleLabel, gbc)
        
        gbc.gridy = row
        panel.add(JSeparator(), gbc)
    }



    override fun isModified(): Boolean {
        val settings = CodeReviewSettings.getInstance()
        return primaryServiceCombo.selectedItem != settings.primaryAIService ||
                deepSeekEnabledCheckBox.isSelected != settings.deepSeekEnabled ||
                String(deepSeekApiKeyField.password) != settings.deepSeekApiKey ||
                tongyiEnabledCheckBox.isSelected != settings.tongyiEnabled ||
                String(tongyiApiKeyField.password) != settings.tongyiApiKey ||
                geminiEnabledCheckBox.isSelected != settings.geminiEnabled ||
                String(geminiApiKeyField.password) != settings.geminiApiKey ||
                autoReviewCheckBox.isSelected != settings.autoReviewOnCommit ||
                showDetailedResultsCheckBox.isSelected != settings.showDetailedResults ||
                blockHighRiskCheckBox.isSelected != settings.blockHighRiskCommits ||
                (minimumScoreSpinner.value as Int) != settings.minimumScore ||
                checkCodeStyleCheckBox.isSelected != settings.checkCodeStyle ||
                checkPerformanceCheckBox.isSelected != settings.checkPerformance ||
                checkSecurityCheckBox.isSelected != settings.checkSecurity ||
                checkBugRiskCheckBox.isSelected != settings.checkBugRisk ||
                checkMaintainabilityCheckBox.isSelected != settings.checkMaintainability ||
                checkDocumentationCheckBox.isSelected != settings.checkDocumentation ||
                checkBestPracticesCheckBox.isSelected != settings.checkBestPractices
    }

    override fun apply() {
        val settings = CodeReviewSettings.getInstance()
        settings.primaryAIService = primaryServiceCombo.selectedItem as AIServiceType
        settings.deepSeekEnabled = deepSeekEnabledCheckBox.isSelected
        settings.deepSeekApiKey = String(deepSeekApiKeyField.password)
        settings.tongyiEnabled = tongyiEnabledCheckBox.isSelected
        settings.tongyiApiKey = String(tongyiApiKeyField.password)
        settings.geminiEnabled = geminiEnabledCheckBox.isSelected
        settings.geminiApiKey = String(geminiApiKeyField.password)
        settings.autoReviewOnCommit = autoReviewCheckBox.isSelected
        settings.showDetailedResults = showDetailedResultsCheckBox.isSelected
        settings.blockHighRiskCommits = blockHighRiskCheckBox.isSelected
        settings.minimumScore = minimumScoreSpinner.value as Int
        settings.checkCodeStyle = checkCodeStyleCheckBox.isSelected
        settings.checkPerformance = checkPerformanceCheckBox.isSelected
        settings.checkSecurity = checkSecurityCheckBox.isSelected
        settings.checkBugRisk = checkBugRiskCheckBox.isSelected
        settings.checkMaintainability = checkMaintainabilityCheckBox.isSelected
        settings.checkDocumentation = checkDocumentationCheckBox.isSelected
        settings.checkBestPractices = checkBestPracticesCheckBox.isSelected
    }

    override fun reset() {
        val settings = CodeReviewSettings.getInstance()
        primaryServiceCombo.selectedItem = settings.primaryAIService
        deepSeekEnabledCheckBox.isSelected = settings.deepSeekEnabled
        deepSeekApiKeyField.text = settings.deepSeekApiKey
        tongyiEnabledCheckBox.isSelected = settings.tongyiEnabled
        tongyiApiKeyField.text = settings.tongyiApiKey
        geminiEnabledCheckBox.isSelected = settings.geminiEnabled
        geminiApiKeyField.text = settings.geminiApiKey
        autoReviewCheckBox.isSelected = settings.autoReviewOnCommit
        showDetailedResultsCheckBox.isSelected = settings.showDetailedResults
        blockHighRiskCheckBox.isSelected = settings.blockHighRiskCommits
        minimumScoreSpinner.value = settings.minimumScore
        checkCodeStyleCheckBox.isSelected = settings.checkCodeStyle
        checkPerformanceCheckBox.isSelected = settings.checkPerformance
        checkSecurityCheckBox.isSelected = settings.checkSecurity
        checkBugRiskCheckBox.isSelected = settings.checkBugRisk
        checkMaintainabilityCheckBox.isSelected = settings.checkMaintainability
        checkDocumentationCheckBox.isSelected = settings.checkDocumentation
        checkBestPracticesCheckBox.isSelected = settings.checkBestPractices
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
