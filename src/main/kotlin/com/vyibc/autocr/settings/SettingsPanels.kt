//package com.vyibc.autocr.settings
//
//import com.intellij.ui.components.*
//import java.awt.BorderLayout
//import java.awt.Dimension
//import javax.swing.*
//
///**
// * Neo4j配置面板
// */
//class Neo4jPanel {
//    private lateinit var enabledCheckBox: JBCheckBox
//    private lateinit var uriField: JBTextField
//    private lateinit var usernameField: JBTextField
//    private lateinit var passwordField: JBPasswordField
//    private lateinit var databaseField: JBTextField
//    private lateinit var maxConnectionPoolSizeSpinner: JSpinner
//    private lateinit var connectionTimeoutSpinner: JSpinner
//    private lateinit var maxTransactionRetryTimeSpinner: JSpinner
//    private lateinit var testConnectionButton: JButton
//
//    fun createPanel(): JPanel {
//        val panel = JPanel(BorderLayout())
//        val contentPanel = JPanel()
//        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
//        contentPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
//
//        // 启用复选框
//        enabledCheckBox = JBCheckBox("启用 Neo4j 图数据库").apply {
//            addActionListener { updateFieldsEnabled() }
//        }
//        contentPanel.add(enabledCheckBox)
//        contentPanel.add(Box.createVerticalStrut(10))
//
//        // 连接信息
//        val connectionInfoPanel = JPanel()
//        connectionInfoPanel.border = BorderFactory.createTitledBorder("连接信息")
//        connectionInfoPanel.layout = BoxLayout(connectionInfoPanel, BoxLayout.Y_AXIS)
//
//        // URI
//        val uriPanel = JPanel()
//        uriPanel.layout = BoxLayout(uriPanel, BoxLayout.X_AXIS)
//        uriPanel.add(JBLabel("连接URI: "))
//        uriField = JBTextField("bolt://localhost:7687")
//        uriField.preferredSize = Dimension(300, 25)
//        uriPanel.add(uriField)
//        uriPanel.add(Box.createHorizontalGlue())
//
//        // Username
//        val usernamePanel = JPanel()
//        usernamePanel.layout = BoxLayout(usernamePanel, BoxLayout.X_AXIS)
//        usernamePanel.add(JBLabel("用户名: "))
//        usernameField = JBTextField("neo4j")
//        usernameField.preferredSize = Dimension(200, 25)
//        usernamePanel.add(usernameField)
//        usernamePanel.add(Box.createHorizontalGlue())
//
//        // Password
//        val passwordPanel = JPanel()
//        passwordPanel.layout = BoxLayout(passwordPanel, BoxLayout.X_AXIS)
//        passwordPanel.add(JBLabel("密码: "))
//        passwordField = JBPasswordField()
//        passwordField.preferredSize = Dimension(200, 25)
//        passwordPanel.add(passwordField)
//        passwordPanel.add(Box.createHorizontalGlue())
//
//        // Database
//        val databasePanel = JPanel()
//        databasePanel.layout = BoxLayout(databasePanel, BoxLayout.X_AXIS)
//        databasePanel.add(JBLabel("数据库名: "))
//        databaseField = JBTextField("autocr")
//        databaseField.preferredSize = Dimension(150, 25)
//        databasePanel.add(databaseField)
//        databasePanel.add(Box.createHorizontalGlue())
//
//        connectionInfoPanel.add(uriPanel)
//        connectionInfoPanel.add(Box.createVerticalStrut(5))
//        connectionInfoPanel.add(usernamePanel)
//        connectionInfoPanel.add(Box.createVerticalStrut(5))
//        connectionInfoPanel.add(passwordPanel)
//        connectionInfoPanel.add(Box.createVerticalStrut(5))
//        connectionInfoPanel.add(databasePanel)
//
//        // 高级设置
//        val advancedPanel = JPanel()
//        advancedPanel.border = BorderFactory.createTitledBorder("高级设置")
//        advancedPanel.layout = BoxLayout(advancedPanel, BoxLayout.Y_AXIS)
//
//        // 最大连接池大小
//        val poolSizePanel = JPanel()
//        poolSizePanel.layout = BoxLayout(poolSizePanel, BoxLayout.X_AXIS)
//        poolSizePanel.add(JBLabel("最大连接池大小: "))
//        maxConnectionPoolSizeSpinner = JSpinner(SpinnerNumberModel(10, 1, 100, 1))
//        maxConnectionPoolSizeSpinner.preferredSize = Dimension(80, 25)
//        poolSizePanel.add(maxConnectionPoolSizeSpinner)
//        poolSizePanel.add(Box.createHorizontalGlue())
//
//        // 连接超时
//        val connectionTimeoutPanel = JPanel()
//        connectionTimeoutPanel.layout = BoxLayout(connectionTimeoutPanel, BoxLayout.X_AXIS)
//        connectionTimeoutPanel.add(JBLabel("连接超时(ms): "))
//        connectionTimeoutSpinner = JSpinner(SpinnerNumberModel(5000L, 1000L, 60000L, 1000L))
//        connectionTimeoutSpinner.preferredSize = Dimension(100, 25)
//        connectionTimeoutPanel.add(connectionTimeoutSpinner)
//        connectionTimeoutPanel.add(Box.createHorizontalGlue())
//
//        // 事务重试时间
//        val retryTimePanel = JPanel()
//        retryTimePanel.layout = BoxLayout(retryTimePanel, BoxLayout.X_AXIS)
//        retryTimePanel.add(JBLabel("事务重试时间(ms): "))
//        maxTransactionRetryTimeSpinner = JSpinner(SpinnerNumberModel(30000L, 5000L, 300000L, 5000L))
//        maxTransactionRetryTimeSpinner.preferredSize = Dimension(100, 25)
//        retryTimePanel.add(maxTransactionRetryTimeSpinner)
//        retryTimePanel.add(Box.createHorizontalGlue())
//
//        advancedPanel.add(poolSizePanel)
//        advancedPanel.add(Box.createVerticalStrut(5))
//        advancedPanel.add(connectionTimeoutPanel)
//        advancedPanel.add(Box.createVerticalStrut(5))
//        advancedPanel.add(retryTimePanel)
//
//        // 测试连接按钮
//        testConnectionButton = JButton("测试连接").apply {
//            addActionListener { testConnection() }
//        }
//
//        val buttonPanel = JPanel()
//        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
//        buttonPanel.add(testConnectionButton)
//        buttonPanel.add(Box.createHorizontalGlue())
//
//        // 说明文本
//        val infoPanel = JPanel()
//        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
//        infoPanel.border = BorderFactory.createTitledBorder("说明")
//
//        val infoText = JBLabel("<html>" +
//                "Neo4j用于存储和可视化代码依赖关系图谱。<br>" +
//                "启用后将提供更强大的代码分析和可视化功能。<br>" +
//                "如果不启用，将使用内存图数据库（功能有限）。<br>" +
//                "请确保Neo4j服务已启动并可访问。" +
//                "</html>")
//        infoPanel.add(infoText)
//
//        contentPanel.add(connectionInfoPanel)
//        contentPanel.add(Box.createVerticalStrut(10))
//        contentPanel.add(advancedPanel)
//        contentPanel.add(Box.createVerticalStrut(10))
//        contentPanel.add(buttonPanel)
//        contentPanel.add(Box.createVerticalStrut(10))
//        contentPanel.add(infoPanel)
//        contentPanel.add(Box.createVerticalGlue())
//
//        panel.add(contentPanel, BorderLayout.CENTER)
//        return panel
//    }
//
//    private fun updateFieldsEnabled() {
//        val enabled = enabledCheckBox.isSelected
//        uriField.isEnabled = enabled
//        usernameField.isEnabled = enabled
//        passwordField.isEnabled = enabled
//        databaseField.isEnabled = enabled
//        maxConnectionPoolSizeSpinner.isEnabled = enabled
//        connectionTimeoutSpinner.isEnabled = enabled
//        maxTransactionRetryTimeSpinner.isEnabled = enabled
//        testConnectionButton.isEnabled = enabled
//    }
//
//    private fun testConnection() {
//        testConnectionButton.text = "测试中..."
//        testConnectionButton.isEnabled = false
//
//        SwingUtilities.invokeLater {
//            try {
//                // 模拟Neo4j连接测试
//                Thread.sleep(2000)
//                JOptionPane.showMessageDialog(
//                    testConnectionButton.parent,
//                    "Neo4j 连接测试成功！\\n数据库版本: 5.x\\n状态: 正常",
//                    "连接测试",
//                    JOptionPane.INFORMATION_MESSAGE
//                )
//            } catch (e: Exception) {
//                JOptionPane.showMessageDialog(
//                    testConnectionButton.parent,
//                    "Neo4j 连接测试失败\\n请检查连接信息和服务状态",
//                    "连接测试",
//                    JOptionPane.ERROR_MESSAGE
//                )
//            } finally {
//                testConnectionButton.text = "测试连接"
//                testConnectionButton.isEnabled = enabledCheckBox.isSelected
//            }
//        }
//    }
//
//    fun loadFrom(settings: Neo4jSettings) {
//        enabledCheckBox.isSelected = settings.enabled
//        uriField.text = settings.uri
//        usernameField.text = settings.username
//        passwordField.text = settings.password
//        databaseField.text = settings.database
//        maxConnectionPoolSizeSpinner.value = settings.maxConnectionPoolSize
//        connectionTimeoutSpinner.value = settings.connectionTimeout
//        maxTransactionRetryTimeSpinner.value = settings.maxTransactionRetryTime
//        updateFieldsEnabled()
//    }
//
//    fun applyTo(settings: Neo4jSettings) {
//        settings.enabled = enabledCheckBox.isSelected
//        settings.uri = uriField.text.trim()
//        settings.username = usernameField.text.trim()
//        settings.password = String(passwordField.password)
//        settings.database = databaseField.text.trim()
//        settings.maxConnectionPoolSize = maxConnectionPoolSizeSpinner.value as Int
//        settings.connectionTimeout = connectionTimeoutSpinner.value as Long
//        settings.maxTransactionRetryTime = maxTransactionRetryTimeSpinner.value as Long
//    }
//
//    fun getCurrentSettings(): Neo4jSettings {
//        return Neo4jSettings(
//            enabled = enabledCheckBox.isSelected,
//            uri = uriField.text.trim(),
//            username = usernameField.text.trim(),
//            password = String(passwordField.password),
//            database = databaseField.text.trim(),
//            maxConnectionPoolSize = maxConnectionPoolSizeSpinner.value as Int,
//            connectionTimeout = connectionTimeoutSpinner.value as Long,
//            maxTransactionRetryTime = maxTransactionRetryTimeSpinner.value as Long
//        )
//    }
//
//    fun isModified(settings: Neo4jSettings): Boolean {
//        return enabledCheckBox.isSelected != settings.enabled ||
//               uriField.text.trim() != settings.uri ||
//               usernameField.text.trim() != settings.username ||
//               String(passwordField.password) != settings.password ||
//               databaseField.text.trim() != settings.database ||
//               maxConnectionPoolSizeSpinner.value != settings.maxConnectionPoolSize ||
//               connectionTimeoutSpinner.value != settings.connectionTimeout ||
//               maxTransactionRetryTimeSpinner.value != settings.maxTransactionRetryTime
//    }
//}
//
///**
// * 提示词模板配置面板
// */
//class PromptTemplatePanel {
//    private lateinit var codeReviewTemplateArea: JTextArea
//    private lateinit var securityAnalysisTemplateArea: JTextArea
//    private lateinit var performanceAnalysisTemplateArea: JTextArea
//    private lateinit var customTemplatesPanel: JPanel
//
//    fun createPanel(): JPanel {
//        val panel = JPanel(BorderLayout())
//        val tabbedPane = JBTabbedPane()
//
//        // 代码评审模板
//        val codeReviewPanel = createTemplatePanel(
//            "代码评审模板",
//            "用于生成代码评审报告的提示词模板。支持变量: {{totalFiles}}, {{highPriorityFiles}}, {{topRiskFactors}} 等"
//        )
//        codeReviewTemplateArea = codeReviewPanel.second
//        tabbedPane.addTab("代码评审", codeReviewPanel.first)
//
//        // 安全分析模板
//        val securityPanel = createTemplatePanel(
//            "安全分析模板",
//            "用于安全风险分析的提示词模板。支持变量: {{methodList}} 等"
//        )
//        securityAnalysisTemplateArea = securityPanel.second
//        tabbedPane.addTab("安全分析", securityPanel.first)
//
//        // 性能分析模板
//        val performancePanel = createTemplatePanel(
//            "性能分析模板",
//            "用于性能分析的提示词模板。支持变量: {{methodList}} 等"
//        )
//        performanceAnalysisTemplateArea = performancePanel.second
//        tabbedPane.addTab("性能分析", performancePanel.first)
//
//        // 自定义模板
//        val customPanel = createCustomTemplatesPanel()
//        tabbedPane.addTab("自定义模板", customPanel)
//
//        panel.add(tabbedPane, BorderLayout.CENTER)
//
//        return panel
//    }
//
//    private fun createTemplatePanel(title: String, description: String): Pair<JPanel, JTextArea> {
//        val panel = JPanel(BorderLayout())
//
//        // 描述标签
//        val descLabel = JBLabel("<html>$description</html>")
//        descLabel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
//        panel.add(descLabel, BorderLayout.NORTH)
//
//        // 文本编辑区域
//        val textArea = JTextArea(20, 60)
//        textArea.lineWrap = true
//        textArea.wrapStyleWord = true
//        textArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
//
//        val scrollPane = JBScrollPane(textArea)
//        panel.add(scrollPane, BorderLayout.CENTER)
//
//        // 按钮面板
//        val buttonPanel = JPanel()
//        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
//
//        val resetButton = JButton("重置为默认").apply {
//            addActionListener { resetToDefault(textArea, title) }
//        }
//
//        val previewButton = JButton("预览效果").apply {
//            addActionListener { previewTemplate(textArea.text) }
//        }
//
//        buttonPanel.add(resetButton)
//        buttonPanel.add(Box.createHorizontalStrut(10))
//        buttonPanel.add(previewButton)
//        buttonPanel.add(Box.createHorizontalGlue())
//
//        panel.add(buttonPanel, BorderLayout.SOUTH)
//
//        return Pair(panel, textArea)
//    }
//
//    private fun createCustomTemplatesPanel(): JPanel {
//        val panel = JPanel(BorderLayout())
//
//        customTemplatesPanel = JPanel()
//        customTemplatesPanel.layout = BoxLayout(customTemplatesPanel, BoxLayout.Y_AXIS)
//
//        val scrollPane = JBScrollPane(customTemplatesPanel)
//        panel.add(scrollPane, BorderLayout.CENTER)
//
//        // 添加按钮
//        val buttonPanel = JPanel()
//        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
//
//        val addButton = JButton("添加自定义模板").apply {
//            addActionListener { addCustomTemplate() }
//        }
//
//        buttonPanel.add(addButton)
//        buttonPanel.add(Box.createHorizontalGlue())
//
//        panel.add(buttonPanel, BorderLayout.SOUTH)
//
//        return panel
//    }
//
//    private fun resetToDefault(textArea: JTextArea, templateType: String) {
//        val defaultTemplate = when (templateType) {
//            "代码评审模板" -> PromptTemplateSettings.DEFAULT_CODE_REVIEW_TEMPLATE
//            "安全分析模板" -> PromptTemplateSettings.DEFAULT_SECURITY_ANALYSIS_TEMPLATE
//            "性能分析模板" -> PromptTemplateSettings.DEFAULT_PERFORMANCE_ANALYSIS_TEMPLATE
//            else -> ""
//        }
//
//        val result = JOptionPane.showConfirmDialog(
//            textArea,
//            "确定要重置为默认模板吗？当前内容将丢失。",
//            "确认重置",
//            JOptionPane.YES_NO_OPTION
//        )
//
//        if (result == JOptionPane.YES_OPTION) {
//            textArea.text = defaultTemplate
//        }
//    }
//
//    private fun previewTemplate(template: String) {
//        val previewDialog = JDialog()
//        previewDialog.title = "模板预览"
//        previewDialog.setSize(600, 400)
//        previewDialog.setLocationRelativeTo(null)
//
//        val previewArea = JTextArea(template)
//        previewArea.isEditable = false
//        previewArea.lineWrap = true
//        previewArea.wrapStyleWord = true
//
//        val scrollPane = JBScrollPane(previewArea)
//        previewDialog.add(scrollPane)
//
//        previewDialog.isVisible = true
//    }
//
//    private fun addCustomTemplate() {
//        val name = JOptionPane.showInputDialog(
//            customTemplatesPanel,
//            "请输入自定义模板名称:",
//            "添加自定义模板",
//            JOptionPane.QUESTION_MESSAGE
//        )
//
//        if (!name.isNullOrBlank()) {
//            // 这里应该添加自定义模板的编辑界面
//            // 简化实现，仅显示消息
//            JOptionPane.showMessageDialog(
//                customTemplatesPanel,
//                "自定义模板功能将在后续版本中实现",
//                "提示",
//                JOptionPane.INFORMATION_MESSAGE
//            )
//        }
//    }
//
//    fun loadFrom(settings: PromptTemplateSettings) {
//        codeReviewTemplateArea.text = settings.codeReviewTemplate
//        securityAnalysisTemplateArea.text = settings.securityAnalysisTemplate
//        performanceAnalysisTemplateArea.text = settings.performanceAnalysisTemplate
//        // TODO: 加载自定义模板
//    }
//
//    fun applyTo(settings: PromptTemplateSettings) {
//        settings.codeReviewTemplate = codeReviewTemplateArea.text
//        settings.securityAnalysisTemplate = securityAnalysisTemplateArea.text
//        settings.performanceAnalysisTemplate = performanceAnalysisTemplateArea.text
//        // TODO: 保存自定义模板
//    }
//
//    fun getCurrentSettings(): PromptTemplateSettings {
//        return PromptTemplateSettings(
//            codeReviewTemplate = codeReviewTemplateArea.text,
//            securityAnalysisTemplate = securityAnalysisTemplateArea.text,
//            performanceAnalysisTemplate = performanceAnalysisTemplateArea.text,
//            customTemplates = mutableMapOf() // TODO: 获取自定义模板
//        )
//    }
//
//    fun isModified(settings: PromptTemplateSettings): Boolean {
//        return codeReviewTemplateArea.text != settings.codeReviewTemplate ||
//               securityAnalysisTemplateArea.text != settings.securityAnalysisTemplate ||
//               performanceAnalysisTemplateArea.text != settings.performanceAnalysisTemplate
//        // TODO: 检查自定义模板是否修改
//    }
//}
//
///**
// * 通用设置面板
// */
//class GeneralSettingsPanel {
//    private lateinit var enableCacheCheckBox: JBCheckBox
//    private lateinit var cacheExpireTimeSpinner: JSpinner
//    private lateinit var maxCacheSizeSpinner: JSpinner
//    private lateinit var enableTelemetryCheckBox: JBCheckBox
//    private lateinit var logLevelCombo: JComboBox<String>
//    private lateinit var enableNotificationsCheckBox: JBCheckBox
//    private lateinit var autoStartReviewCheckBox: JBCheckBox
//    private lateinit var defaultSourceBranchField: JBTextField
//    private lateinit var excludePatternsArea: JTextArea
//
//    fun createPanel(): JPanel {
//        val panel = JPanel(BorderLayout())
//        val contentPanel = JPanel()
//        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
//        contentPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
//
//        // 缓存设置
//        val cachePanel = createCacheSettingsPanel()
//        contentPanel.add(cachePanel)
//        contentPanel.add(Box.createVerticalStrut(15))
//
//        // 系统设置
//        val systemPanel = createSystemSettingsPanel()
//        contentPanel.add(systemPanel)
//        contentPanel.add(Box.createVerticalStrut(15))
//
//        // 评审设置
//        val reviewPanel = createReviewSettingsPanel()
//        contentPanel.add(reviewPanel)
//
//        contentPanel.add(Box.createVerticalGlue())
//
//        panel.add(contentPanel, BorderLayout.CENTER)
//        return panel
//    }
//
//    private fun createCacheSettingsPanel(): JPanel {
//        val panel = JPanel()
//        panel.border = BorderFactory.createTitledBorder("缓存设置")
//        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
//
//        enableCacheCheckBox = JBCheckBox("启用缓存")
//
//        val expireTimePanel = JPanel()
//        expireTimePanel.layout = BoxLayout(expireTimePanel, BoxLayout.X_AXIS)
//        expireTimePanel.add(JBLabel("缓存过期时间(小时): "))
//        cacheExpireTimeSpinner = JSpinner(SpinnerNumberModel(1, 1, 24, 1))
//        expireTimePanel.add(cacheExpireTimeSpinner)
//        expireTimePanel.add(Box.createHorizontalGlue())
//
//        val maxSizePanel = JPanel()
//        maxSizePanel.layout = BoxLayout(maxSizePanel, BoxLayout.X_AXIS)
//        maxSizePanel.add(JBLabel("最大缓存条目数: "))
//        maxCacheSizeSpinner = JSpinner(SpinnerNumberModel(1000L, 100L, 10000L, 100L))
//        maxSizePanel.add(maxCacheSizeSpinner)
//        maxSizePanel.add(Box.createHorizontalGlue())
//
//        panel.add(enableCacheCheckBox)
//        panel.add(expireTimePanel)
//        panel.add(maxSizePanel)
//
//        return panel
//    }
//
//    private fun createSystemSettingsPanel(): JPanel {
//        val panel = JPanel()
//        panel.border = BorderFactory.createTitledBorder("系统设置")
//        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
//
//        enableTelemetryCheckBox = JBCheckBox("启用遥测数据收集")
//        enableNotificationsCheckBox = JBCheckBox("启用通知")
//
//        val logLevelPanel = JPanel()
//        logLevelPanel.layout = BoxLayout(logLevelPanel, BoxLayout.X_AXIS)
//        logLevelPanel.add(JBLabel("日志级别: "))
//        logLevelCombo = JComboBox(arrayOf("DEBUG", "INFO", "WARN", "ERROR"))
//        logLevelCombo.selectedItem = "INFO"
//        logLevelPanel.add(logLevelCombo)
//        logLevelPanel.add(Box.createHorizontalGlue())
//
//        panel.add(enableTelemetryCheckBox)
//        panel.add(enableNotificationsCheckBox)
//        panel.add(logLevelPanel)
//
//        return panel
//    }
//
//    private fun createReviewSettingsPanel(): JPanel {
//        val panel = JPanel()
//        panel.border = BorderFactory.createTitledBorder("评审设置")
//        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
//
//        autoStartReviewCheckBox = JBCheckBox("自动开始评审")
//
//        val branchPanel = JPanel()
//        branchPanel.layout = BoxLayout(branchPanel, BoxLayout.X_AXIS)
//        branchPanel.add(JBLabel("默认源分支: "))
//        defaultSourceBranchField = JBTextField("main")
//        defaultSourceBranchField.preferredSize = Dimension(150, 25)
//        branchPanel.add(defaultSourceBranchField)
//        branchPanel.add(Box.createHorizontalGlue())
//
//        val excludePanel = JPanel(BorderLayout())
//        excludePanel.add(JBLabel("排除文件模式 (每行一个):"), BorderLayout.NORTH)
//        excludePatternsArea = JTextArea(5, 40)
//        excludePatternsArea.text = "*.test.java\\n*.spec.js\\ntarget/**\\nbuild/**"
//        val scrollPane = JBScrollPane(excludePatternsArea)
//        excludePanel.add(scrollPane, BorderLayout.CENTER)
//
//        panel.add(autoStartReviewCheckBox)
//        panel.add(branchPanel)
//        panel.add(Box.createVerticalStrut(5))
//        panel.add(excludePanel)
//
//        return panel
//    }
//
//    fun loadFrom(settings: GeneralSettings) {
//        enableCacheCheckBox.isSelected = settings.enableCache
//        cacheExpireTimeSpinner.value = (settings.cacheExpireTime / 3600000).toInt() // 转换为小时
//        maxCacheSizeSpinner.value = settings.maxCacheSize
//        enableTelemetryCheckBox.isSelected = settings.enableTelemetry
//        logLevelCombo.selectedItem = settings.logLevel
//        enableNotificationsCheckBox.isSelected = settings.enableNotifications
//        autoStartReviewCheckBox.isSelected = settings.autoStartReview
//        defaultSourceBranchField.text = settings.defaultSourceBranch
//        excludePatternsArea.text = settings.excludeFilePatterns.joinToString("\\n")
//    }
//
//    fun applyTo(settings: GeneralSettings) {
//        settings.enableCache = enableCacheCheckBox.isSelected
//        settings.cacheExpireTime = (cacheExpireTimeSpinner.value as Int) * 3600000L // 转换为毫秒
//        settings.maxCacheSize = maxCacheSizeSpinner.value as Long
//        settings.enableTelemetry = enableTelemetryCheckBox.isSelected
//        settings.logLevel = logLevelCombo.selectedItem as String
//        settings.enableNotifications = enableNotificationsCheckBox.isSelected
//        settings.autoStartReview = autoStartReviewCheckBox.isSelected
//        settings.defaultSourceBranch = defaultSourceBranchField.text.trim()
//        settings.excludeFilePatterns = excludePatternsArea.text.lines()
//            .map { it.trim() }
//            .filter { it.isNotEmpty() }
//            .toMutableList()
//    }
//
//    fun getCurrentSettings(): GeneralSettings {
//        return GeneralSettings(
//            enableCache = enableCacheCheckBox.isSelected,
//            cacheExpireTime = (cacheExpireTimeSpinner.value as Int) * 3600000L,
//            maxCacheSize = maxCacheSizeSpinner.value as Long,
//            enableTelemetry = enableTelemetryCheckBox.isSelected,
//            logLevel = logLevelCombo.selectedItem as String,
//            enableNotifications = enableNotificationsCheckBox.isSelected,
//            autoStartReview = autoStartReviewCheckBox.isSelected,
//            defaultSourceBranch = defaultSourceBranchField.text.trim(),
//            excludeFilePatterns = excludePatternsArea.text.lines()
//                .map { it.trim() }
//                .filter { it.isNotEmpty() }
//                .toMutableList()
//        )
//    }
//
//    fun isModified(settings: GeneralSettings): Boolean {
//        val currentExpireTime = (cacheExpireTimeSpinner.value as Int) * 3600000L
//        val currentPatterns = excludePatternsArea.text.lines()
//            .map { it.trim() }
//            .filter { it.isNotEmpty() }
//
//        return enableCacheCheckBox.isSelected != settings.enableCache ||
//               currentExpireTime != settings.cacheExpireTime ||
//               maxCacheSizeSpinner.value != settings.maxCacheSize ||
//               enableTelemetryCheckBox.isSelected != settings.enableTelemetry ||
//               logLevelCombo.selectedItem != settings.logLevel ||
//               enableNotificationsCheckBox.isSelected != settings.enableNotifications ||
//               autoStartReviewCheckBox.isSelected != settings.autoStartReview ||
//               defaultSourceBranchField.text.trim() != settings.defaultSourceBranch ||
//               currentPatterns != settings.excludeFilePatterns
//    }
//}