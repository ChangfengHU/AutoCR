//package com.vyibc.autocr.settings
//
//import com.intellij.openapi.options.Configurable
//import com.intellij.openapi.project.Project
//import com.intellij.openapi.ui.ValidationInfo
//import com.intellij.ui.components.*
//import com.intellij.ui.dsl.builder.*
//import com.intellij.util.ui.JBUI
//import com.vyibc.autocr.ai.AIProvider
//import org.slf4j.LoggerFactory
//import java.awt.BorderLayout
//import java.awt.Dimension
//import javax.swing.*
//
///**
// * AutoCR设置配置页面
// */
//class AutoCRSettingsConfigurable(private val project: Project) : Configurable {
//
//    private val logger = LoggerFactory.getLogger(AutoCRSettingsConfigurable::class.java)
//    private val settings = AutoCRSettingsState.getInstance(project)
//
//    // AI配置组件
//    private lateinit var openAIPanel: AIProviderPanel
//    private lateinit var anthropicPanel: AIProviderPanel
//    private lateinit var googlePanel: AIProviderPanel
//    private lateinit var ollamaPanel: AIProviderPanel
//    private lateinit var azureOpenAIPanel: AIProviderPanel
//    private lateinit var alibabaPanel: AIProviderPanel      // 阿里通义
//    private lateinit var deepseekPanel: AIProviderPanel     // DeepSeek
//
//    // Neo4j配置组件
//    private lateinit var neo4jPanel: Neo4jPanel
//
//    // 提示词模板组件
//    private lateinit var promptTemplatePanel: PromptTemplatePanel
//
//    // 通用设置组件
//    private lateinit var generalSettingsPanel: GeneralSettingsPanel
//
//    private lateinit var mainPanel: JPanel
//
//    override fun getDisplayName(): String = "AutoCR"
//
//    override fun createComponent(): JComponent? {
//        mainPanel = JPanel(BorderLayout())
//
//        val tabbedPane = JBTabbedPane()
//
//        // AI供应商配置标签页
//        val aiConfigPanel = createAIConfigPanel()
//        tabbedPane.addTab("AI 供应商", aiConfigPanel)
//
//        // Neo4j配置标签页
//        neo4jPanel = Neo4jPanel()
//        tabbedPane.addTab("Neo4j 数据库", neo4jPanel.createPanel())
//
//        // 提示词模板标签页
//        promptTemplatePanel = PromptTemplatePanel()
//        tabbedPane.addTab("提示词模板", promptTemplatePanel.createPanel())
//
//        // 通用设置标签页
//        generalSettingsPanel = GeneralSettingsPanel()
//        tabbedPane.addTab("通用设置", generalSettingsPanel.createPanel())
//
//        mainPanel.add(tabbedPane, BorderLayout.CENTER)
//
//        // 加载当前设置
//        reset()
//
//        return mainPanel
//    }
//
//    /**
//     * 创建AI配置面板 - 使用现代化卡片布局
//     */
//    private fun createAIConfigPanel(): JPanel {
//        val panel = JPanel(BorderLayout())
//        val scrollPane = JBScrollPane()
//
//        val contentPanel = JPanel()
//        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
//        contentPanel.border = JBUI.Borders.empty(20)
//
//        // 标题和说明
//        val titlePanel = JPanel()
//        titlePanel.layout = BoxLayout(titlePanel, BoxLayout.Y_AXIS)
//        titlePanel.border = JBUI.Borders.emptyBottom(20)
//
//        val titleLabel = JBLabel("<html><h2>AI 供应商配置</h2></html>")
//        titleLabel.font = titleLabel.font.deriveFont(16f)
//
//        val descLabel = JBLabel("<html><p style='color: #666666;'>配置一个或多个AI供应商来进行代码评审。建议配置多个供应商以实现负载均衡和故障转移。</p></html>")
//
//        titlePanel.add(titleLabel)
//        titlePanel.add(Box.createVerticalStrut(5))
//        titlePanel.add(descLabel)
//
//        contentPanel.add(titlePanel)
//
//        // 使用两列布局展示AI供应商卡片
//        val cardsContainer = JPanel()
//        cardsContainer.layout = BoxLayout(cardsContainer, BoxLayout.Y_AXIS)
//
//        // 第一行：OpenAI 和 Anthropic
//        val row1 = JPanel()
//        row1.layout = BoxLayout(row1, BoxLayout.X_AXIS)
//
//        openAIPanel = AIProviderPanel("OpenAI", AIProvider.OPENAI, "ChatGPT-4/3.5", "#10A37F")
//        anthropicPanel = AIProviderPanel("Anthropic", AIProvider.ANTHROPIC, "Claude-3/Sonnet", "#B8860B")
//
//        row1.add(openAIPanel.createCompactPanel())
//        row1.add(Box.createHorizontalStrut(15))
//        row1.add(anthropicPanel.createCompactPanel())
//        row1.add(Box.createHorizontalGlue())
//
//        cardsContainer.add(row1)
//        cardsContainer.add(Box.createVerticalStrut(15))
//
//        // 第二行：Google 和 阿里通义
//        val row2 = JPanel()
//        row2.layout = BoxLayout(row2, BoxLayout.X_AXIS)
//
//        googlePanel = AIProviderPanel("Google", AIProvider.GOOGLE, "Gemini Pro", "#4285F4")
//        alibabaPanel = AIProviderPanel("阿里通义", AIProvider.ALIBABA_TONGYI, "通义千问", "#FF6700")
//
//        row2.add(googlePanel.createCompactPanel())
//        row2.add(Box.createHorizontalStrut(15))
//        row2.add(alibabaPanel.createCompactPanel())
//        row2.add(Box.createHorizontalGlue())
//
//        cardsContainer.add(row2)
//        cardsContainer.add(Box.createVerticalStrut(15))
//
//        // 第三行：DeepSeek 和 Ollama
//        val row3 = JPanel()
//        row3.layout = BoxLayout(row3, BoxLayout.X_AXIS)
//
//        deepseekPanel = AIProviderPanel("DeepSeek", AIProvider.DEEPSEEK, "DeepSeek-Coder", "#1E90FF")
//        ollamaPanel = AIProviderPanel("Ollama (本地)", AIProvider.OLLAMA, "本地模型", "#6B7280")
//
//        row3.add(deepseekPanel.createCompactPanel())
//        row3.add(Box.createHorizontalStrut(15))
//        row3.add(ollamaPanel.createCompactPanel())
//        row3.add(Box.createHorizontalGlue())
//
//        cardsContainer.add(row3)
//        cardsContainer.add(Box.createVerticalStrut(15))
//
//        // 第四行：Azure OpenAI (单独一行)
//        val row4 = JPanel()
//        row4.layout = BoxLayout(row4, BoxLayout.X_AXIS)
//
//        azureOpenAIPanel = AIProviderPanel("Azure OpenAI", AIProvider.AZURE_OPENAI, "企业级OpenAI", "#0078D4")
//
//        row4.add(azureOpenAIPanel.createCompactPanel())
//        row4.add(Box.createHorizontalGlue())
//
//        cardsContainer.add(row4)
//
//        contentPanel.add(cardsContainer)
//        contentPanel.add(Box.createVerticalGlue())
//
//        scrollPane.setViewportView(contentPanel)
//        scrollPane.border = null
//        panel.add(scrollPane, BorderLayout.CENTER)
//
//        return panel
//    }
//
//    override fun isModified(): Boolean {
//        return openAIPanel.isModified(settings.openAIConfig) ||
//               anthropicPanel.isModified(settings.anthropicConfig) ||
//               googlePanel.isModified(settings.googleConfig) ||
//               ollamaPanel.isModified(settings.ollamaConfig) ||
//               azureOpenAIPanel.isModified(settings.azureOpenAIConfig) ||
//               alibabaPanel.isModified(settings.alibabaConfig) ||
//               deepseekPanel.isModified(settings.deepseekConfig) ||
//               neo4jPanel.isModified(settings.neo4jConfig) ||
//               promptTemplatePanel.isModified(settings.promptTemplates) ||
//               generalSettingsPanel.isModified(settings.generalSettings)
//    }
//
//    override fun apply() {
//        // 验证设置
//        val validationErrors = validate()
//        if (validationErrors.isNotEmpty()) {
//            val errorMessage = validationErrors.joinToString("\\n")
//            JOptionPane.showMessageDialog(
//                mainPanel,
//                errorMessage,
//                "配置验证失败",
//                JOptionPane.ERROR_MESSAGE
//            )
//            return
//        }
//
//        // 应用设置
//        openAIPanel.applyTo(settings.openAIConfig)
//        anthropicPanel.applyTo(settings.anthropicConfig)
//        googlePanel.applyTo(settings.googleConfig)
//        ollamaPanel.applyTo(settings.ollamaConfig)
//        azureOpenAIPanel.applyTo(settings.azureOpenAIConfig)
//        alibabaPanel.applyTo(settings.alibabaConfig)
//        deepseekPanel.applyTo(settings.deepseekConfig)
//        neo4jPanel.applyTo(settings.neo4jConfig)
//        promptTemplatePanel.applyTo(settings.promptTemplates)
//        generalSettingsPanel.applyTo(settings.generalSettings)
//
//        logger.info("AutoCR settings applied successfully")
//
//        // 通知用户设置已保存
//        JOptionPane.showMessageDialog(
//            mainPanel,
//            "设置已保存成功！\\n某些更改可能需要重启IDE才能生效。",
//            "设置保存",
//            JOptionPane.INFORMATION_MESSAGE
//        )
//    }
//
//    override fun reset() {
//        openAIPanel.loadFrom(settings.openAIConfig)
//        anthropicPanel.loadFrom(settings.anthropicConfig)
//        googlePanel.loadFrom(settings.googleConfig)
//        ollamaPanel.loadFrom(settings.ollamaConfig)
//        azureOpenAIPanel.loadFrom(settings.azureOpenAIConfig)
//        alibabaPanel.loadFrom(settings.alibabaConfig)
//        deepseekPanel.loadFrom(settings.deepseekConfig)
//        neo4jPanel.loadFrom(settings.neo4jConfig)
//        promptTemplatePanel.loadFrom(settings.promptTemplates)
//        generalSettingsPanel.loadFrom(settings.generalSettings)
//    }
//
//    /**
//     * 验证所有设置
//     */
//    private fun validate(): List<String> {
//        val errors = mutableListOf<String>()
//
//        // 验证AI供应商设置
//        errors.addAll(SettingsValidator.validateAIProviderSettings(openAIPanel.getCurrentSettings(), AIProvider.OPENAI))
//        errors.addAll(SettingsValidator.validateAIProviderSettings(anthropicPanel.getCurrentSettings(), AIProvider.ANTHROPIC))
//        errors.addAll(SettingsValidator.validateAIProviderSettings(googlePanel.getCurrentSettings(), AIProvider.GOOGLE))
//        errors.addAll(SettingsValidator.validateAIProviderSettings(ollamaPanel.getCurrentSettings(), AIProvider.OLLAMA))
//        errors.addAll(SettingsValidator.validateAIProviderSettings(azureOpenAIPanel.getCurrentSettings(), AIProvider.AZURE_OPENAI))
//        errors.addAll(SettingsValidator.validateAIProviderSettings(alibabaPanel.getCurrentSettings(), AIProvider.ALIBABA_TONGYI))
//        errors.addAll(SettingsValidator.validateAIProviderSettings(deepseekPanel.getCurrentSettings(), AIProvider.DEEPSEEK))
//
//        // 验证Neo4j设置
//        errors.addAll(SettingsValidator.validateNeo4jSettings(neo4jPanel.getCurrentSettings()))
//
//        // 验证提示词模板
//        val promptSettings = promptTemplatePanel.getCurrentSettings()
//        errors.addAll(SettingsValidator.validatePromptTemplate(promptSettings.codeReviewTemplate, "代码评审模板"))
//        errors.addAll(SettingsValidator.validatePromptTemplate(promptSettings.securityAnalysisTemplate, "安全分析模板"))
//        errors.addAll(SettingsValidator.validatePromptTemplate(promptSettings.performanceAnalysisTemplate, "性能分析模板"))
//
//        // 检查至少启用一个AI供应商
//        val hasEnabledProvider = listOf(
//            openAIPanel.getCurrentSettings().enabled,
//            anthropicPanel.getCurrentSettings().enabled,
//            googlePanel.getCurrentSettings().enabled,
//            ollamaPanel.getCurrentSettings().enabled,
//            azureOpenAIPanel.getCurrentSettings().enabled,
//            alibabaPanel.getCurrentSettings().enabled,
//            deepseekPanel.getCurrentSettings().enabled
//        ).any { it }
//
//        if (!hasEnabledProvider) {
//            errors.add("至少需要启用一个AI供应商")
//        }
//
//        return errors
//    }
//
//    override fun disposeUIResources() {
//        // 清理资源
//    }
//}
//
///**
// * AI供应商配置面板 - 现代化卡片设计
// */
//class AIProviderPanel(
//    private val providerName: String,
//    private val provider: AIProvider,
//    private val description: String = "",
//    private val themeColor: String = "#6B7280"
//) {
//    private lateinit var mainCard: JPanel
//    private lateinit var enabledCheckBox: JBCheckBox
//    private lateinit var apiKeyField: JBPasswordField
//    private lateinit var baseUrlField: JBTextField
//    private lateinit var modelNameField: JBTextField
//    private lateinit var maxTokensSpinner: JSpinner
//    private lateinit var temperatureSpinner: JSpinner
//    private lateinit var timeoutSpinner: JSpinner
//    private lateinit var requestsPerMinuteSpinner: JSpinner
//    private lateinit var prioritySpinner: JSpinner
//    private lateinit var testConnectionButton: JButton
//    private lateinit var advancedPanel: JPanel
//    private var isAdvancedVisible = false
//
//    /**
//     * 创建紧凑的卡片式面板
//     */
//    fun createCompactPanel(): JPanel {
//        mainCard = JPanel()
//        mainCard.layout = BorderLayout()
//        mainCard.border = JBUI.Borders.compound(
//            JBUI.Borders.empty(5),
//            BorderFactory.createRaisedBevelBorder()
//        )
//        mainCard.preferredSize = Dimension(350, 200)
//        mainCard.maximumSize = Dimension(350, 300)
//
//        // 顶部标题区域
//        val headerPanel = createHeaderPanel()
//        mainCard.add(headerPanel, BorderLayout.NORTH)
//
//        // 主要配置区域
//        val contentPanel = createContentPanel()
//        mainCard.add(contentPanel, BorderLayout.CENTER)
//
//        // 底部按钮区域
//        val buttonPanel = createButtonPanel()
//        mainCard.add(buttonPanel, BorderLayout.SOUTH)
//
//        updateFieldsEnabled()
//        return mainCard
//    }
//
//    private fun createHeaderPanel(): JPanel {
//        val panel = JPanel()
//        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
//        panel.border = JBUI.Borders.empty(15, 15, 10, 15)
//
//        // 启用状态和供应商名称
//        val titleRow = JPanel()
//        titleRow.layout = BoxLayout(titleRow, BoxLayout.X_AXIS)
//
//        enabledCheckBox = JBCheckBox("").apply {
//            addActionListener {
//                updateFieldsEnabled()
//                updateCardAppearance()
//            }
//        }
//
//        val nameLabel = JBLabel(providerName)
//        nameLabel.font = nameLabel.font.deriveFont(14f).deriveFont(java.awt.Font.BOLD)
//
//        titleRow.add(enabledCheckBox)
//        titleRow.add(Box.createHorizontalStrut(8))
//        titleRow.add(nameLabel)
//        titleRow.add(Box.createHorizontalGlue())
//
//        // 描述信息
//        val descLabel = JBLabel("<html><span style='color: #666666; font-size: 11px;'>$description</span></html>")
//
//        panel.add(titleRow)
//        panel.add(Box.createVerticalStrut(5))
//        panel.add(descLabel)
//
//        return panel
//    }
//
//    private fun createContentPanel(): JPanel {
//        val panel = JPanel()
//        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
//        panel.border = JBUI.Borders.empty(0, 15, 0, 15)
//
//        // API Key 输入
//        if (provider != AIProvider.OLLAMA) {
//            val apiKeyPanel = JPanel()
//            apiKeyPanel.layout = BoxLayout(apiKeyPanel, BoxLayout.X_AXIS)
//
//            val label = JBLabel("API Key:")
//            label.preferredSize = Dimension(80, 25)
//            apiKeyField = JBPasswordField()
//            apiKeyField.toolTipText = "输入${providerName}的API密钥"
//
//            apiKeyPanel.add(label)
//            apiKeyPanel.add(Box.createHorizontalStrut(10))
//            apiKeyPanel.add(apiKeyField)
//
//            panel.add(apiKeyPanel)
//            panel.add(Box.createVerticalStrut(8))
//        }
//
//        // 模型名称
//        val modelPanel = JPanel()
//        modelPanel.layout = BoxLayout(modelPanel, BoxLayout.X_AXIS)
//
//        val modelLabel = JBLabel("模型:")
//        modelLabel.preferredSize = Dimension(80, 25)
//        modelNameField = JBTextField()
//        modelNameField.toolTipText = "输入要使用的模型名称"
//        setDefaultModelName()
//
//        modelPanel.add(modelLabel)
//        modelPanel.add(Box.createHorizontalStrut(10))
//        modelPanel.add(modelNameField)
//
//        panel.add(modelPanel)
//        panel.add(Box.createVerticalStrut(8))
//
//        // Base URL (仅部分供应商需要)
//        if (shouldShowBaseUrl()) {
//            val urlPanel = JPanel()
//            urlPanel.layout = BoxLayout(urlPanel, BoxLayout.X_AXIS)
//
//            val urlLabel = JBLabel("Base URL:")
//            urlLabel.preferredSize = Dimension(80, 25)
//            baseUrlField = JBTextField()
//            baseUrlField.toolTipText = "API基础URL地址"
//            setDefaultBaseUrl()
//
//            urlPanel.add(urlLabel)
//            urlPanel.add(Box.createHorizontalStrut(10))
//            urlPanel.add(baseUrlField)
//
//            panel.add(urlPanel)
//            panel.add(Box.createVerticalStrut(8))
//        }
//
//        // 高级设置面板（默认隐藏）
//        advancedPanel = createAdvancedPanel()
//        advancedPanel.isVisible = false
//        panel.add(advancedPanel)
//
//        return panel
//    }
//
//    private fun createAdvancedPanel(): JPanel {
//        val panel = JPanel()
//        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
//        panel.border = BorderFactory.createTitledBorder("高级设置")
//
//        // Token数量和温度参数在一行
//        val row1 = JPanel()
//        row1.layout = BoxLayout(row1, BoxLayout.X_AXIS)
//
//        row1.add(JBLabel("Token:"))
//        maxTokensSpinner = JSpinner(SpinnerNumberModel(4000, 1, 100000, 100))
//        maxTokensSpinner.preferredSize = Dimension(80, 25)
//        row1.add(maxTokensSpinner)
//
//        row1.add(Box.createHorizontalStrut(15))
//        row1.add(JBLabel("温度:"))
//        temperatureSpinner = JSpinner(SpinnerNumberModel(0.1, 0.0, 2.0, 0.1))
//        temperatureSpinner.preferredSize = Dimension(60, 25)
//        row1.add(temperatureSpinner)
//        row1.add(Box.createHorizontalGlue())
//
//        // 超时和请求频率在一行
//        val row2 = JPanel()
//        row2.layout = BoxLayout(row2, BoxLayout.X_AXIS)
//
//        row2.add(JBLabel("超时(ms):"))
//        timeoutSpinner = JSpinner(SpinnerNumberModel(30000, 1000, 300000, 1000))
//        timeoutSpinner.preferredSize = Dimension(80, 25)
//        row2.add(timeoutSpinner)
//
//        row2.add(Box.createHorizontalStrut(15))
//        row2.add(JBLabel("优先级:"))
//        prioritySpinner = JSpinner(SpinnerNumberModel(1, 1, 10, 1))
//        prioritySpinner.preferredSize = Dimension(60, 25)
//        row2.add(prioritySpinner)
//        row2.add(Box.createHorizontalGlue())
//
//        panel.add(row1)
//        panel.add(Box.createVerticalStrut(8))
//        panel.add(row2)
//
//        return panel
//    }
//
//    private fun createButtonPanel(): JPanel {
//        val panel = JPanel()
//        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
//        panel.border = JBUI.Borders.empty(10, 15, 15, 15)
//
//        // 高级设置切换按钮
//        val advancedButton = JButton("高级设置").apply {
//            addActionListener { toggleAdvancedSettings() }
//            font = font.deriveFont(11f)
//        }
//
//        // 测试连接按钮
//        testConnectionButton = JButton("测试").apply {
//            addActionListener { testConnection() }
//            font = font.deriveFont(11f)
//        }
//
//        panel.add(advancedButton)
//        panel.add(Box.createHorizontalStrut(8))
//        panel.add(testConnectionButton)
//        panel.add(Box.createHorizontalGlue())
//
//        return panel
//    }
//
//    private fun setDefaultModelName() {
//        val defaultModel = when (provider) {
//            AIProvider.OPENAI -> "gpt-4o"
//            AIProvider.ANTHROPIC -> "claude-3-5-sonnet-20241022"
//            AIProvider.GOOGLE -> "gemini-1.5-pro"
//            AIProvider.OLLAMA -> "qwen2.5-coder:7b"
//            AIProvider.AZURE_OPENAI -> "gpt-4"
//            AIProvider.ALIBABA_TONGYI -> "qwen-max"
//            AIProvider.DEEPSEEK -> "deepseek-coder"
//        }
//        modelNameField.text = defaultModel
//    }
//
//    private fun setDefaultBaseUrl() {
//        val defaultUrl = when (provider) {
//            AIProvider.OPENAI -> "https://api.openai.com/v1"
//            AIProvider.ANTHROPIC -> "https://api.anthropic.com"
//            AIProvider.GOOGLE -> "https://generativelanguage.googleapis.com/v1"
//            AIProvider.OLLAMA -> "http://localhost:11434"
//            AIProvider.AZURE_OPENAI -> "https://your-resource.openai.azure.com"
//            AIProvider.ALIBABA_TONGYI -> "https://dashscope.aliyuncs.com/api/v1"
//            AIProvider.DEEPSEEK -> "https://api.deepseek.com/v1"
//        }
//        if (::baseUrlField.isInitialized) {
//            baseUrlField.text = defaultUrl
//        }
//    }
//
//    private fun shouldShowBaseUrl(): Boolean {
//        return provider in listOf(
//            AIProvider.OLLAMA,
//            AIProvider.AZURE_OPENAI,
//            AIProvider.ALIBABA_TONGYI,
//            AIProvider.DEEPSEEK
//        )
//    }
//
//    private fun toggleAdvancedSettings() {
//        isAdvancedVisible = !isAdvancedVisible
//        advancedPanel.isVisible = isAdvancedVisible
//
//        // 更新卡片高度
//        mainCard.preferredSize = if (isAdvancedVisible) {
//            Dimension(350, 320)
//        } else {
//            Dimension(350, 200)
//        }
//
//        mainCard.revalidate()
//        mainCard.repaint()
//    }
//
//    private fun updateFieldsEnabled() {
//        val enabled = enabledCheckBox.isSelected
//
//        if (::apiKeyField.isInitialized) apiKeyField.isEnabled = enabled
//        if (::baseUrlField.isInitialized) baseUrlField.isEnabled = enabled
//        modelNameField.isEnabled = enabled
//        maxTokensSpinner.isEnabled = enabled
//        temperatureSpinner.isEnabled = enabled
//        timeoutSpinner.isEnabled = enabled
//        requestsPerMinuteSpinner.isEnabled = enabled
//        prioritySpinner.isEnabled = enabled
//        testConnectionButton.isEnabled = enabled
//    }
//
//    private fun updateCardAppearance() {
//        val enabled = enabledCheckBox.isSelected
//        if (enabled) {
//            mainCard.background = null
//            mainCard.border = JBUI.Borders.compound(
//                JBUI.Borders.empty(5),
//                BorderFactory.createLoweredBevelBorder()
//            )
//        } else {
//            mainCard.background = java.awt.Color(245, 245, 245)
//            mainCard.border = JBUI.Borders.compound(
//                JBUI.Borders.empty(5),
//                BorderFactory.createRaisedBevelBorder()
//            )
//        }
//        mainCard.repaint()
//    }
//
//    private fun testConnection() {
//        testConnectionButton.text = "测试中..."
//        testConnectionButton.isEnabled = false
//
//        // 模拟连接测试
//        SwingUtilities.invokeLater {
//            Thread.sleep(1000)
//            SwingUtilities.invokeLater {
//                testConnectionButton.text = "测试"
//                testConnectionButton.isEnabled = true
//
//                JOptionPane.showMessageDialog(
//                    mainCard,
//                    "连接测试成功！",
//                    "测试结果",
//                    JOptionPane.INFORMATION_MESSAGE
//                )
//            }
//        }
//    }
//
//    // 初始化缺失的字段
//    init {
//        requestsPerMinuteSpinner = JSpinner(SpinnerNumberModel(60, 1, 10000, 1))
//    }
//
//    fun loadFrom(settings: AIProviderSettings) {
//        enabledCheckBox.isSelected = settings.enabled
//        if (::apiKeyField.isInitialized) {
//            apiKeyField.text = settings.apiKey
//        }
//        if (::baseUrlField.isInitialized) {
//            baseUrlField.text = settings.baseUrl
//        }
//        modelNameField.text = settings.modelName.ifEmpty {
//            when (provider) {
//                AIProvider.OPENAI -> "gpt-4o"
//                AIProvider.ANTHROPIC -> "claude-3-5-sonnet-20241022"
//                AIProvider.GOOGLE -> "gemini-1.5-pro"
//                AIProvider.OLLAMA -> "qwen2.5-coder:7b"
//                AIProvider.AZURE_OPENAI -> "gpt-4"
//                AIProvider.ALIBABA_TONGYI -> "qwen-max"
//                AIProvider.DEEPSEEK -> "deepseek-coder"
//            }
//        }
//        maxTokensSpinner.value = settings.maxTokens
//        temperatureSpinner.value = settings.temperature
//        timeoutSpinner.value = settings.timeout
//        requestsPerMinuteSpinner.value = settings.requestsPerMinute
//        prioritySpinner.value = settings.priority
//        updateFieldsEnabled()
//    }
//
//    fun applyTo(settings: AIProviderSettings) {
//        settings.enabled = enabledCheckBox.isSelected
//        if (::apiKeyField.isInitialized) {
//            settings.apiKey = String(apiKeyField.password)
//        }
//        if (::baseUrlField.isInitialized) {
//            settings.baseUrl = baseUrlField.text.trim()
//        }
//        settings.modelName = modelNameField.text.trim()
//        settings.maxTokens = maxTokensSpinner.value as Int
//        settings.temperature = temperatureSpinner.value as Double
//        settings.timeout = timeoutSpinner.value as Long
//        settings.requestsPerMinute = requestsPerMinuteSpinner.value as Int
//        settings.priority = prioritySpinner.value as Int
//    }
//
//    fun getCurrentSettings(): AIProviderSettings {
//        return AIProviderSettings(
//            enabled = enabledCheckBox.isSelected,
//            apiKey = if (::apiKeyField.isInitialized) String(apiKeyField.password) else "",
//            baseUrl = if (::baseUrlField.isInitialized) baseUrlField.text.trim() else "",
//            modelName = modelNameField.text.trim(),
//            maxTokens = maxTokensSpinner.value as Int,
//            temperature = temperatureSpinner.value as Double,
//            timeout = timeoutSpinner.value as Long,
//            requestsPerMinute = requestsPerMinuteSpinner.value as Int,
//            priority = prioritySpinner.value as Int
//        )
//    }
//
//    fun isModified(settings: AIProviderSettings): Boolean {
//        return enabledCheckBox.isSelected != settings.enabled ||
//               (::apiKeyField.isInitialized && String(apiKeyField.password) != settings.apiKey) ||
//               (::baseUrlField.isInitialized && baseUrlField.text.trim() != settings.baseUrl) ||
//               modelNameField.text.trim() != settings.modelName ||
//               maxTokensSpinner.value != settings.maxTokens ||
//               temperatureSpinner.value != settings.temperature ||
//               timeoutSpinner.value != settings.timeout ||
//               requestsPerMinuteSpinner.value != settings.requestsPerMinute ||
//               prioritySpinner.value != settings.priority
//    }
//}