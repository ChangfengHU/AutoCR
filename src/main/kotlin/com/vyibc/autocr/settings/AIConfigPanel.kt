package com.vyibc.autocr.settings

import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.border.Border
import kotlin.concurrent.thread

/**
 * AI供应商配置面板 - 现代化卡片设计
 */
class AIConfigPanel {
    
    private lateinit var cardsContainer: JPanel
    private val providerCards = mutableMapOf<AIProvider, ProviderCard>()
    private val aiConfigs = mutableListOf<AIModelConfig>()
    
    companion object {
        // IDEA风格的颜色定义
        private val CARD_BACKGROUND = JBColor.WHITE
        private val PANEL_BACKGROUND = JBColor(Color(0xF7F8FA), Color(0x2B2D30))
        private val BORDER_COLOR = JBColor(Color(0xDFE1E5), Color(0x4C5052))
        private val HOVER_BORDER_COLOR = JBColor(Color(0x0066CC), Color(0x4A9EFF))
        private val HOVER_BACKGROUND = JBColor(Color(0xF0F7FF), Color(0x313438))
        private val TEXT_COLOR = JBColor.BLACK
        private val SECONDARY_TEXT_COLOR = JBColor(Color(0x6C707E), Color(0x9AA7B0))
        private val SUCCESS_COLOR = JBColor(Color(0x0D8043), Color(0x499C54))
        private val WARNING_COLOR = JBColor(Color(0xF57C00), Color(0xFF8F00))
        private val DANGER_COLOR = JBColor(Color(0xD32F2F), Color(0xF44336))
        private val PRIMARY_COLOR = JBColor(Color(0x0066CC), Color(0x4A9EFF))
    }
    
    fun createPanel(): JPanel {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.background = PANEL_BACKGROUND
        mainPanel.border = JBUI.Borders.empty(24)
        
        // 创建标题区域
        val headerPanel = createHeaderPanel()
        mainPanel.add(headerPanel, BorderLayout.NORTH)
        
        // 创建卡片容器
        val scrollPane = createCardsScrollPane()
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        
        return mainPanel
    }
    
    private fun createHeaderPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = PANEL_BACKGROUND
        
        // 标题
        val titleLabel = JBLabel("AI 模型配置")
        titleLabel.font = JBUI.Fonts.label(18f).asBold()
        titleLabel.foreground = TEXT_COLOR
        titleLabel.alignmentX = Component.LEFT_ALIGNMENT
        
        // 描述
        val descLabel = JBLabel("配置多个AI服务提供商，支持智能调度和负载均衡")
        descLabel.font = JBUI.Fonts.label(13f)
        descLabel.foreground = SECONDARY_TEXT_COLOR
        descLabel.alignmentX = Component.LEFT_ALIGNMENT
        
        panel.add(titleLabel)
        panel.add(Box.createVerticalStrut(4))
        panel.add(descLabel)
        panel.add(Box.createVerticalStrut(16))
        
        return panel
    }
    
    private fun createCardsScrollPane(): JScrollPane {
        cardsContainer = JPanel()
        cardsContainer.layout = GridBagLayout()
        cardsContainer.background = PANEL_BACKGROUND
        
        val scrollPane = JBScrollPane(cardsContainer)
        scrollPane.border = null
        scrollPane.background = PANEL_BACKGROUND
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        
        return scrollPane
    }
    
    private fun refreshCards() {
        cardsContainer.removeAll()

        // 平铺六宫格（按供应商固定一张卡）
        val allProviders = listOf(
            AIProvider.OPENAI,
            AIProvider.GOOGLE,
            AIProvider.DEEPSEEK,
            AIProvider.ALIBABA_TONGYI,
            AIProvider.ANTHROPIC,
            AIProvider.OPENROUTER
        )

        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(8)
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0

        var row = 0
        var col = 0
        val cols = 1

        allProviders.forEach { provider ->
            val config = aiConfigs.find { it.provider == provider } ?: AIModelConfig(
                id = provider.name,
                name = provider.displayName,
                provider = provider,
                modelName = defaultModelFor(provider),
                baseUrl = defaultBaseUrlFor(provider),
                enabled = false
            )

            val providerCard = createProviderEditCard(config)
            providerCards[provider] = providerCard

            gbc.gridx = col
            gbc.gridy = row
            cardsContainer.add(providerCard.panel, gbc)

            col++
            if (col >= cols) {
                col = 0
                row++
            }
        }

        cardsContainer.revalidate()
        cardsContainer.repaint()
    }
    
    private fun createEmptyStatePanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = PANEL_BACKGROUND
        panel.border = JBUI.Borders.empty(60, 40)
        
        // 图标
        val iconLabel = JBLabel("🤖")
        iconLabel.font = iconLabel.font.deriveFont(64f)
        iconLabel.alignmentX = Component.CENTER_ALIGNMENT
        iconLabel.foreground = SECONDARY_TEXT_COLOR
        
        // 标题
        val titleLabel = JBLabel("暂无AI配置")
        titleLabel.font = JBUI.Fonts.label(16f).asBold()
        titleLabel.foreground = TEXT_COLOR
        titleLabel.alignmentX = Component.CENTER_ALIGNMENT
        
        // 描述
        val descLabel = JBLabel("点击「添加新配置」开始配置您的第一个AI服务")
        descLabel.font = JBUI.Fonts.label(13f)
        descLabel.foreground = SECONDARY_TEXT_COLOR
        descLabel.alignmentX = Component.CENTER_ALIGNMENT
        
        panel.add(iconLabel)
        panel.add(Box.createVerticalStrut(16))
        panel.add(titleLabel)
        panel.add(Box.createVerticalStrut(8))
        panel.add(descLabel)
        
        return panel
    }
    
    private fun createProviderEditCard(config: AIModelConfig): ProviderCard {
        val cardPanel = JPanel(BorderLayout())
        cardPanel.background = CARD_BACKGROUND
        cardPanel.border = createCardBorder()

        // 头部
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        header.background = CARD_BACKGROUND
        val title = JBLabel("${config.provider.emoji} ${config.provider.displayName}")
        title.font = JBUI.Fonts.label(14f).asBold()
        val enabledCheck = JBCheckBox("启用")
        enabledCheck.isSelected = config.enabled
        header.add(title)
        header.add(Box.createHorizontalStrut(12))
        header.add(enabledCheck)
        cardPanel.add(header, BorderLayout.NORTH)

        // 表单
        val form = JPanel(GridBagLayout())
        form.background = CARD_BACKGROUND
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(4)
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0

        var row = 0
        fun addRow(label: String, comp: JComponent) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
            form.add(JBLabel(label), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            comp.preferredSize = Dimension(280, comp.preferredSize.height)
            form.add(comp, gbc)
            row++
        }

        val modelCombo = JComboBox(modelOptionsFor(config.provider))
        modelCombo.selectedItem = config.modelName.ifBlank { defaultModelFor(config.provider) }

        val apiKeyField = JBPasswordField()
        apiKeyField.text = config.apiKey
        val baseUrlField = JBTextField(config.baseUrl.ifBlank { defaultBaseUrlFor(config.provider) })
        val timeoutSpinner = JSpinner(SpinnerNumberModel(config.timeout.toInt(), 1000, 300000, 1000))

        val testButton = createActionButton("验证密钥", SUCCESS_COLOR)
        val testResultLabel = JBLabel(" ")
        testResultLabel.foreground = SECONDARY_TEXT_COLOR
        testButton.addActionListener {
            val tempConfig = config.copy(
                modelName = modelCombo.selectedItem as String,
                apiKey = String(apiKeyField.password),
                baseUrl = baseUrlField.text.trim(),
                timeout = (timeoutSpinner.value as Int).toLong(),
                enabled = enabledCheck.isSelected
            )
            testButton.isEnabled = false
            testResultLabel.text = "验证中..."
            thread {
                val result = performApiKeyValidation(tempConfig)
                SwingUtilities.invokeLater {
                    testButton.isEnabled = true
                    testResultLabel.text = if (result.success) "✅ 可用" else "❌ ${result.message}"
                    testResultLabel.foreground = if (result.success) SUCCESS_COLOR else DANGER_COLOR
                }
            }
        }

        addRow("模型:", modelCombo)
        addRow("API Key:", apiKeyField)
        addRow("Base URL:", baseUrlField)
        addRow("超时(ms):", timeoutSpinner)

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        actions.background = CARD_BACKGROUND
        actions.add(testButton)
        actions.add(Box.createHorizontalStrut(8))
        actions.add(testResultLabel)
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        form.add(actions, gbc)
        row++

        cardPanel.add(form, BorderLayout.CENTER)

        return ProviderCard(cardPanel, provider = config.provider, modelCombo = modelCombo, apiKey = apiKeyField, baseUrl = baseUrlField, timeoutSpinner = timeoutSpinner, enabledCheck = enabledCheck)
    }
    
    private fun createCardBorder(): Border? {
        return JBUI.Borders.compound(
            JBUI.Borders.customLine(BORDER_COLOR, 1),
            JBUI.Borders.empty(16, 20)
        )
    }
    
    private fun createHoverCardBorder(): Border? {
        return JBUI.Borders.compound(
            JBUI.Borders.customLine(HOVER_BORDER_COLOR, 1),
            JBUI.Borders.empty(16, 20)
        )
    }
    
    private fun createCardContentPanel(config: AIModelConfig): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = null
        
        // 标题行
        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        titlePanel.background = null
        
        val providerLabel = JBLabel("${config.provider.emoji} ${config.provider.displayName}")
        providerLabel.font = JBUI.Fonts.label(14f).asBold()
        providerLabel.foreground = TEXT_COLOR
        
        val nameLabel = JBLabel("· ${config.name}")
        nameLabel.font = JBUI.Fonts.label(14f)
        nameLabel.foreground = TEXT_COLOR
        
        val statusIcon = JBLabel(if (config.enabled) "●" else "○")
        statusIcon.foreground = if (config.enabled) SUCCESS_COLOR else SECONDARY_TEXT_COLOR
        
        titlePanel.add(providerLabel)
        titlePanel.add(Box.createHorizontalStrut(8))
        titlePanel.add(nameLabel)
        titlePanel.add(Box.createHorizontalStrut(8))
        titlePanel.add(statusIcon)
        
        // 详细信息行
        val detailsLabel = JBLabel("${config.modelName} · ${config.maxTokens} tokens · temp ${config.temperature}")
        detailsLabel.font = JBUI.Fonts.label(12f)
        detailsLabel.foreground = SECONDARY_TEXT_COLOR
        
        // URL信息
        val urlLabel = JBLabel(config.baseUrl)
        urlLabel.font = JBUI.Fonts.label(11f)
        urlLabel.foreground = SECONDARY_TEXT_COLOR
        
        titlePanel.alignmentX = Component.LEFT_ALIGNMENT
        detailsLabel.alignmentX = Component.LEFT_ALIGNMENT
        urlLabel.alignmentX = Component.LEFT_ALIGNMENT
        
        panel.add(titlePanel)
        panel.add(Box.createVerticalStrut(4))
        panel.add(detailsLabel)
        panel.add(Box.createVerticalStrut(2))
        panel.add(urlLabel)
        
        return panel
    }
    
    private fun createCardActionPanel(config: AIModelConfig): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = null
        
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(2)
        gbc.fill = GridBagConstraints.HORIZONTAL
        
        // 仅保留删除按钮（针对旧卡片回退路径，实际已改为固定供应商卡）
        gbc.gridy = 0
        val deleteButton = createActionButton("删除", DANGER_COLOR)
        deleteButton.addActionListener { deleteConfig(config) }
        panel.add(deleteButton, gbc)
        
        return panel
    }
    
    private fun createStyledButton(text: String, bgColor: Color, iconType: String = ""): JButton {
        val button = JButton(text)
        button.font = JBUI.Fonts.label(13f)
        button.background = bgColor
        button.foreground = Color.WHITE
        button.border = JBUI.Borders.empty(8, 16)
        button.isFocusPainted = false
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        
        // 添加悬停效果
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                button.background = bgColor.brighter()
            }
            override fun mouseExited(e: MouseEvent?) {
                button.background = bgColor
            }
        })
        
        return button
    }
    
    private fun createActionButton(text: String, bgColor: Color): JButton {
        val button = JButton(text)
        button.font = JBUI.Fonts.label(11f)
        button.background = bgColor
        button.foreground = Color.WHITE
        button.border = JBUI.Borders.empty(4, 12)
        button.isFocusPainted = false
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.preferredSize = Dimension(60, 24)
        
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                button.background = bgColor.brighter()
            }
            override fun mouseExited(e: MouseEvent?) {
                button.background = bgColor
            }
        })
        
        return button
    }
    
    private fun testConnection(config: AIModelConfig, button: JButton) {
        val originalText = button.text
        button.text = "..."
        button.isEnabled = false
        
        thread {
            try {
                val result = performConnectionTest(config)
                SwingUtilities.invokeLater {
                    button.text = originalText
                    button.isEnabled = true
                    showTestResult(config, result)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    button.text = originalText
                    button.isEnabled = true
                    showTestResult(config, TestResult(false, "测试异常: ${e.message}"))
                }
            }
        }
    }
    
    private fun performConnectionTest(config: AIModelConfig): TestResult {
        if (config.name.isBlank()) return TestResult(false, "配置名称不能为空")
        if (config.modelName.isBlank()) return TestResult(false, "模型名称不能为空")
        if (config.baseUrl.isBlank()) return TestResult(false, "Base URL不能为空")
        if (config.apiKey.isBlank()) return TestResult(false, "API Key不能为空")
        
        try {
            java.net.URL(config.baseUrl)
        } catch (e: Exception) {
            return TestResult(false, "Base URL格式不正确")
        }
        
        try {
            val url = java.net.URL(config.baseUrl)
            val host = url.host
            val port = if (url.port != -1) url.port else (if (url.protocol == "https") 443 else 80)
            
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(host, port), 5000)
            socket.close()
            
            return TestResult(true, "✅ 配置验证通过，网络连接正常")
        } catch (e: Exception) {
            return TestResult(false, "网络连接失败: ${e.message}")
        }
    }
    
    private fun showTestResult(config: AIModelConfig, result: TestResult) {
        val icon = if (result.success) JOptionPane.INFORMATION_MESSAGE else JOptionPane.ERROR_MESSAGE
        val title = if (result.success) "连接测试成功" else "连接测试失败"
        
        JOptionPane.showMessageDialog(
            cardsContainer,
            "${config.provider.emoji} ${config.name}\n\n${result.message}",
            title,
            icon
        )
    }
    
    // 平铺模式下不再使用新增/编辑弹窗
    
    private fun deleteConfig(config: AIModelConfig) {
        val result = JOptionPane.showConfirmDialog(
            cardsContainer,
            "确定要删除配置「${config.name}」吗？\n\n${config.provider.emoji} ${config.provider.displayName}",
            "确认删除",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )
        
        if (result == JOptionPane.YES_OPTION) {
            aiConfigs.remove(config)
            refreshCards()
        }
    }
    
    fun loadFrom(configs: List<AIModelConfig>) {
        aiConfigs.clear()
        aiConfigs.addAll(configs)
        refreshCards()
    }
    
    fun getConfigs(): List<AIModelConfig> {
        // 从卡片实时收集（覆盖 aiConfigs）
        val list = mutableListOf<AIModelConfig>()
        providerCards.values.forEach { card ->
            val enabled = card.enabledCheck.isSelected
            val model = card.modelCombo.selectedItem as? String ?: ""
            val apiKey = String(card.apiKey.password)
            val baseUrl = card.baseUrl.text.trim()
            val timeout = (card.timeoutSpinner.value as Int).toLong()
            val cfg = AIModelConfig(
                id = card.provider.name,
                name = card.provider.displayName,
                provider = card.provider,
                modelName = model,
                apiKey = apiKey,
                baseUrl = baseUrl,
                maxTokens = 4000,
                temperature = 0.1,
                timeout = timeout,
                enabled = enabled
            )
            list.add(cfg)
        }
        aiConfigs.clear()
        aiConfigs.addAll(list)
        return list
    }
    
    fun isModified(originalConfigs: List<AIModelConfig>): Boolean {
        val currentList = getConfigs()
        if (currentList.size != originalConfigs.size) return true
        return currentList.any { current ->
            val original = originalConfigs.find { it.id == current.id }
            original == null || !configsEqual(current, original)
        }
    }
    
    private fun configsEqual(a: AIModelConfig, b: AIModelConfig): Boolean {
        return a.name == b.name &&
               a.provider == b.provider &&
               a.modelName == b.modelName &&
               a.apiKey == b.apiKey &&
               a.baseUrl == b.baseUrl &&
               a.maxTokens == b.maxTokens &&
               a.temperature == b.temperature &&
               a.timeout == b.timeout &&
               a.enabled == b.enabled
    }

    fun validateBeforeApply(): List<String> {
        val errors = mutableListOf<String>()
        // 针对启用的配置，强制在线验证 API Key
        providerCards.values.forEach { card ->
            if (card.enabledCheck.isSelected) {
                val config = AIModelConfig(
                    id = card.provider.name,
                    name = card.provider.displayName,
                    provider = card.provider,
                    modelName = card.modelCombo.selectedItem as? String ?: "",
                    apiKey = String(card.apiKey.password),
                    baseUrl = card.baseUrl.text.trim(),
                    timeout = (card.timeoutSpinner.value as Int).toLong(),
                    enabled = true
                )
                val prelim = performConnectionTest(config)
                if (!prelim.success) {
                    errors.add("[${config.provider.displayName}] ${prelim.message}")
                } else {
                    val result = performApiKeyValidation(config)
                    if (!result.success) {
                        errors.add("[${config.provider.displayName}] ${result.message}")
                    }
                }
            }
        }
        return errors
    }

    private fun modelOptionsFor(provider: AIProvider): Array<String> = when (provider) {
        AIProvider.OPENAI -> arrayOf("gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-3.5-turbo")
        AIProvider.GOOGLE -> arrayOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-pro")
        AIProvider.DEEPSEEK -> arrayOf("deepseek-chat", "deepseek-coder")
        AIProvider.ALIBABA_TONGYI -> arrayOf("qwen2.5-7b-instruct", "qwen-turbo", "qwen-plus")
        AIProvider.ANTHROPIC -> arrayOf("claude-3-5-sonnet", "claude-3-opus", "claude-3-haiku")
        AIProvider.OPENROUTER -> arrayOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet", "google/gemini-1.5-pro")
    }

    private fun defaultModelFor(provider: AIProvider): String = modelOptionsFor(provider).first()
    private fun defaultBaseUrlFor(provider: AIProvider): String = when (provider) {
        AIProvider.OPENAI -> "https://api.openai.com/v1"
        AIProvider.GOOGLE -> "https://generativelanguage.googleapis.com"
        AIProvider.DEEPSEEK -> "https://api.deepseek.com"
        AIProvider.ALIBABA_TONGYI -> "https://dashscope.aliyuncs.com"
        AIProvider.ANTHROPIC -> "https://api.anthropic.com"
        AIProvider.OPENROUTER -> "https://openrouter.ai/api/v1"
    }

    private fun performApiKeyValidation(config: AIModelConfig): TestResult {
        // 轻量验证：对不同提供商调用需要鉴权的 models 列表端点，确保无效密钥返回 401/403
        return try {
            val validationUrl = buildValidationUrl(config.provider, config.baseUrl, config.apiKey)
            val connection = (validationUrl.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = config.timeout.toInt()
                readTimeout = config.timeout.toInt()
                when (config.provider) {
                    AIProvider.OPENAI, AIProvider.DEEPSEEK, AIProvider.OPENROUTER -> setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                    AIProvider.GOOGLE -> { /* key 已在查询参数 */ }
                    AIProvider.ALIBABA_TONGYI -> setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                    AIProvider.ANTHROPIC -> {
                        setRequestProperty("x-api-key", config.apiKey)
                        setRequestProperty("anthropic-version", "2023-06-01")
                    }
                }
            }
            val code = connection.responseCode
            return when (code) {
                200 -> TestResult(true, "API Key 验证通过")
                401, 403 -> TestResult(false, "API Key 无效或权限不足 (HTTP $code)")
                else -> TestResult(true, "收到响应 (HTTP $code)；如异常请检查网络/域名与网关设置")
            }
        } catch (e: Exception) {
            TestResult(false, "密钥验证失败: ${e.message}")
        }
    }

    private fun buildValidationUrl(provider: AIProvider, baseUrlRaw: String, apiKey: String): java.net.URL {
        val base = baseUrlRaw.trimEnd('/')
        val urlStr = when (provider) {
            AIProvider.OPENAI, AIProvider.DEEPSEEK -> if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
            AIProvider.OPENROUTER -> if (base.contains("/v1")) "$base/models" else "$base/api/v1/models"
            AIProvider.ALIBABA_TONGYI -> when {
                base.contains("compatible-mode") -> "$base/v1/models"
                base.endsWith("/v1") -> "$base/models"
                else -> "$base/compatible-mode/v1/models"
            }
            AIProvider.ANTHROPIC -> "$base/v1/models"
            AIProvider.GOOGLE -> "$base/v1/models?key=$apiKey"
        }
        return java.net.URL(urlStr)
    }

    private data class ProviderCard(
        val panel: JPanel,
        val provider: AIProvider,
        val modelCombo: JComboBox<String>,
        val apiKey: JBPasswordField,
        val baseUrl: JBTextField,
        val timeoutSpinner: JSpinner,
        val enabledCheck: JBCheckBox
    )
}

/**
 * 连接测试结果
 */
private data class TestResult(val success: Boolean, val message: String)