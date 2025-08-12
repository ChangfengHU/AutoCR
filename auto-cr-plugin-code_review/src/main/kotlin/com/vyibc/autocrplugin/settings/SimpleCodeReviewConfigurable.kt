package com.vyibc.autocrplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.*

/**
 * 简化的代码评估设置配置页面
 */
class SimpleCodeReviewConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var minimumScoreField: JBTextField
    private lateinit var cascadeDepthField: JBTextField
    private lateinit var promptTextArea: JBTextArea

    // AI服务配置
    private lateinit var deepSeekEnabledCheckBox: JBCheckBox
    private lateinit var deepSeekApiKeyField: JBPasswordField
    private lateinit var tongyiEnabledCheckBox: JBCheckBox
    private lateinit var tongyiApiKeyField: JBPasswordField
    private lateinit var geminiEnabledCheckBox: JBCheckBox
    private lateinit var geminiApiKeyField: JBPasswordField

    override fun getDisplayName(): String = "Auto Code Review"

    override fun createComponent(): JComponent? {
        if (settingsPanel == null) {
            settingsPanel = createSettingsPanel()
        }
        return settingsPanel
    }

    private fun createSettingsPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // 标题
        val titleLabel = JLabel("自动代码评估设置")
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.style or java.awt.Font.BOLD, 16f)
        panel.add(titleLabel, BorderLayout.NORTH)

        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)

        // 启用功能
        enabledCheckBox = JBCheckBox("启用自动代码评估")
        enabledCheckBox.isSelected = true
        mainPanel.add(enabledCheckBox)

        mainPanel.add(Box.createVerticalStrut(20))

        // AI服务配置
        val aiPanel = JPanel()
        aiPanel.layout = BoxLayout(aiPanel, BoxLayout.Y_AXIS)
        aiPanel.border = BorderFactory.createTitledBorder("AI服务配置 (选择一个)")

        // DeepSeek
        deepSeekEnabledCheckBox = JBCheckBox("DeepSeek (推荐)")
        deepSeekEnabledCheckBox.isSelected = true
        aiPanel.add(deepSeekEnabledCheckBox)

        val deepSeekPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        deepSeekPanel.add(JLabel("API Key:"))
        deepSeekApiKeyField = JBPasswordField()
        deepSeekApiKeyField.preferredSize = Dimension(300, 25)
        deepSeekPanel.add(deepSeekApiKeyField)
        deepSeekPanel.add(JLabel("<html><a href='https://platform.deepseek.com/api_keys'>获取</a></html>"))
        aiPanel.add(deepSeekPanel)

        aiPanel.add(Box.createVerticalStrut(10))

        // 通义千问
        tongyiEnabledCheckBox = JBCheckBox("阿里通义千问")
        aiPanel.add(tongyiEnabledCheckBox)

        val tongyiPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        tongyiPanel.add(JLabel("API Key:"))
        tongyiApiKeyField = JBPasswordField()
        tongyiApiKeyField.preferredSize = Dimension(300, 25)
        tongyiPanel.add(tongyiApiKeyField)
        tongyiPanel.add(JLabel("<html><a href='https://dashscope.aliyun.com/'>获取</a></html>"))
        aiPanel.add(tongyiPanel)

        aiPanel.add(Box.createVerticalStrut(10))

        // Gemini
        geminiEnabledCheckBox = JBCheckBox("Google Gemini")
        aiPanel.add(geminiEnabledCheckBox)

        val geminiPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        geminiPanel.add(JLabel("API Key:"))
        geminiApiKeyField = JBPasswordField()
        geminiApiKeyField.preferredSize = Dimension(300, 25)
        geminiPanel.add(geminiApiKeyField)
        geminiPanel.add(JLabel("<html><a href='https://makersuite.google.com/app/apikey'>获取</a></html>"))
        aiPanel.add(geminiPanel)

        mainPanel.add(aiPanel)

        mainPanel.add(Box.createVerticalStrut(20))

        // 评估设置
        val settingsPanel = JPanel()
        settingsPanel.layout = BoxLayout(settingsPanel, BoxLayout.Y_AXIS)
        settingsPanel.border = BorderFactory.createTitledBorder("评估设置")

        val scorePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        scorePanel.add(JLabel("最低评分要求 (0-100):"))
        minimumScoreField = JBTextField("60")
        minimumScoreField.preferredSize = Dimension(100, 25)
        minimumScoreField.toolTipText = "低于此评分的代码将被阻止提交"
        scorePanel.add(minimumScoreField)
        settingsPanel.add(scorePanel)

        val cascadePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        cascadePanel.add(JLabel("方法级联分析深度 (0-3):"))
        cascadeDepthField = JBTextField("1")
        cascadeDepthField.preferredSize = Dimension(100, 25)
        cascadeDepthField.toolTipText = "分析方法调用的级联深度，数值越大分析越深入但耗时越长"
        cascadePanel.add(cascadeDepthField)
        settingsPanel.add(cascadePanel)

        mainPanel.add(settingsPanel)

        mainPanel.add(Box.createVerticalStrut(20))

        // AI提示词配置
        val promptPanel = JPanel()
        promptPanel.layout = BoxLayout(promptPanel, BoxLayout.Y_AXIS)
        promptPanel.border = BorderFactory.createTitledBorder("AI分析提示词配置")

        val promptLabel = JLabel("自定义AI分析提示词 (留空使用默认提示词):")
        promptPanel.add(promptLabel)

        promptTextArea = JBTextArea()
        promptTextArea.rows = 8
        promptTextArea.lineWrap = true
        promptTextArea.wrapStyleWord = true
        promptTextArea.toolTipText = "自定义AI分析的提示词，可以针对特定的代码规范和安全要求进行调整"

        val scrollPane = JBScrollPane(promptTextArea)
        scrollPane.preferredSize = Dimension(600, 200)
        promptPanel.add(scrollPane)

        // 重置为默认提示词按钮
        val resetPromptPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val resetPromptButton = JButton("重置为默认提示词")
        resetPromptButton.addActionListener {
            promptTextArea.text = getDefaultPrompt()
        }
        resetPromptPanel.add(resetPromptButton)
        promptPanel.add(resetPromptPanel)

        mainPanel.add(promptPanel)

        mainPanel.add(Box.createVerticalStrut(20))

        // 使用说明
        val infoPanel = JPanel(BorderLayout())
        val infoText = """
            <html>
            <h3>使用说明:</h3>
            <ul>
            <li>1. 启用自动代码评估功能</li>
            <li>2. 选择并配置一个AI服务的API Key</li>
            <li>3. 设置最低评分要求</li>
            <li>4. 可选：自定义AI分析提示词</li>
            <li>5. 右键点击选择 'Code Review' 进行评估</li>
            <li>6. 或使用快捷键: Ctrl+Alt+C</li>
            </ul>
            <p><b>注意:</b> 代码评估需要将代码发送给AI服务进行分析</p>
            </html>
        """.trimIndent()

        val infoLabel = JLabel(infoText)
        infoPanel.add(infoLabel, BorderLayout.CENTER)
        mainPanel.add(infoPanel)

        panel.add(mainPanel, BorderLayout.CENTER)

        return panel
    }

    /**
     * 获取默认的AI分析提示词
     */
    private fun getDefaultPrompt(): String {
        return """
🚨 **生产环境安全代码审查专家 - 严格风险评估标准**

**核心任务：基于方法实现中检测到的危险操作进行精确风险评估**

## 🔍 **风险评估方法论：**

### 第一步：危险操作检测分析
**重点关注系统预检测标记的"已检测到潜在危险操作"，这些是自动化工具识别的高风险模式：**
- 🚨 如标记为"Redis危险操作" → 分析具体影响和阻塞风险
- 🚨 如标记为"SQL危险操作" → 分析查询性能和注入风险  
- 🚨 如标记为"资源泄漏风险" → 分析内存和连接泄漏影响
- 🚨 如标记为"阻塞操作" → 分析并发性能和响应时间影响

### 第二步：生产环境影响评估
**针对检测到的每种危险操作，评估其在高并发生产环境下的影响：**
- **服务可用性影响** - 是否会导致服务不可用？
- **性能影响程度** - 对系统整体性能的影响范围？
- **故障传播风险** - 是否会引发连锁故障？
- **恢复难度评估** - 故障后恢复的复杂度？

### 第三步：风险等级判定标准
**基于影响程度确定风险等级：**

#### 🚨 CRITICAL (0-30分)：
- 会导致服务完全不可用的操作
- 可能引发系统宕机的风险
- 影响所有用户的致命问题
- 数据安全威胁

#### ⚠️ HIGH (31-60分)：
- 严重影响性能但不至于宕机
- 安全漏洞但影响范围有限
- 需要紧急修复的问题

#### 📊 MEDIUM (61-80分)：
- 一般性能问题
- 代码质量问题
- 建议优化的改进点

#### 💡 LOW (81-100分)：
- 轻微改进建议
- 最佳实践推荐
- 代码规范问题

## 🎯 **分析执行原则：**

1. **基于事实评估** - 严格基于方法实现代码和检测到的危险操作进行评估
2. **影响导向评估** - 重点关注对生产环境的实际影响程度
3. **具体化建议** - 提供针对性的技术解决方案
4. **严格等级标准** - 严格按照风险等级对应的评分范围给分
        """.trimIndent()
    }

    override fun isModified(): Boolean {
        val settings = CodeReviewSettings.getInstance()
        return enabledCheckBox.isSelected != settings.deepSeekEnabled ||
                deepSeekEnabledCheckBox.isSelected != settings.deepSeekEnabled ||
                String(deepSeekApiKeyField.password) != settings.deepSeekApiKey ||
                tongyiEnabledCheckBox.isSelected != settings.tongyiEnabled ||
                String(tongyiApiKeyField.password) != settings.tongyiApiKey ||
                geminiEnabledCheckBox.isSelected != settings.geminiEnabled ||
                String(geminiApiKeyField.password) != settings.geminiApiKey ||
                minimumScoreField.text != settings.minimumScore.toString() ||
                cascadeDepthField.text != settings.maxCascadeDepth.toString() ||
                promptTextArea.text != settings.customPrompt
    }

    override fun apply() {
        val settings = CodeReviewSettings.getInstance()
        settings.deepSeekEnabled = enabledCheckBox.isSelected && deepSeekEnabledCheckBox.isSelected
        settings.deepSeekApiKey = String(deepSeekApiKeyField.password)
        settings.tongyiEnabled = enabledCheckBox.isSelected && tongyiEnabledCheckBox.isSelected
        settings.tongyiApiKey = String(tongyiApiKeyField.password)
        settings.geminiEnabled = enabledCheckBox.isSelected && geminiEnabledCheckBox.isSelected
        settings.geminiApiKey = String(geminiApiKeyField.password)
        settings.customPrompt = promptTextArea.text

        try {
            settings.minimumScore = minimumScoreField.text.toInt().coerceIn(0, 100)
        } catch (e: NumberFormatException) {
            settings.minimumScore = 60
        }

        try {
            settings.maxCascadeDepth = cascadeDepthField.text.toInt().coerceIn(0, 3)
        } catch (e: NumberFormatException) {
            settings.maxCascadeDepth = 1
        }
    }

    override fun reset() {
        val settings = CodeReviewSettings.getInstance()
        enabledCheckBox.isSelected = settings.deepSeekEnabled || settings.tongyiEnabled || settings.geminiEnabled
        deepSeekEnabledCheckBox.isSelected = settings.deepSeekEnabled
        deepSeekApiKeyField.text = settings.deepSeekApiKey
        tongyiEnabledCheckBox.isSelected = settings.tongyiEnabled
        tongyiApiKeyField.text = settings.tongyiApiKey
        geminiEnabledCheckBox.isSelected = settings.geminiEnabled
        geminiApiKeyField.text = settings.geminiApiKey
        minimumScoreField.text = settings.minimumScore.toString()
        cascadeDepthField.text = settings.maxCascadeDepth.toString()

        // 如果没有自定义提示词，显示默认提示词
        promptTextArea.text = if (settings.customPrompt.isNotEmpty()) {
            settings.customPrompt
        } else {
            getDefaultPrompt()
        }
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
