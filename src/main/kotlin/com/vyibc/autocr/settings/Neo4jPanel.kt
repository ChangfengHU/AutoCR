package com.vyibc.autocr.settings

import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * Neo4j配置面板
 */
class Neo4jPanel {
    
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var uriField: JBTextField
    private lateinit var usernameField: JBTextField
    private lateinit var passwordField: JBPasswordField
    private lateinit var databaseField: JBTextField
    private lateinit var connectionPoolSizeSpinner: JSpinner
    private lateinit var connectionTimeoutSpinner: JSpinner
    private lateinit var maxTransactionRetryTimeSpinner: JSpinner
    private lateinit var testConnectionButton: JButton
    private lateinit var statusLabel: JLabel
    
    fun createPanel(): JPanel {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(20)
        
        // 创建标题
        val titlePanel = JPanel()
        titlePanel.layout = BoxLayout(titlePanel, BoxLayout.Y_AXIS)
        val titleLabel = JBLabel("<html><h2>Neo4j 数据库配置</h2></html>")
        titleLabel.font = titleLabel.font.deriveFont(16f)
        val descLabel = JBLabel("<html><p style='color: #666666;'>配置Neo4j数据库连接，用于存储和查询知识图谱数据。</p></html>")
        
        titlePanel.add(titleLabel)
        titlePanel.add(Box.createVerticalStrut(5))
        titlePanel.add(descLabel)
        titlePanel.add(Box.createVerticalStrut(20))
        
        mainPanel.add(titlePanel, BorderLayout.NORTH)
        
        // 创建配置表单
        val formPanel = createFormPanel()
        mainPanel.add(formPanel, BorderLayout.CENTER)
        
        return mainPanel
    }
    
    private fun createFormPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("连接设置")
        
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST
        
        var row = 0
        
        // 启用复选框
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        enabledCheckBox = JBCheckBox("启用Neo4j数据库")
        enabledCheckBox.toolTipText = "启用后才能使用Neo4j相关功能"
        enabledCheckBox.addActionListener { updateFieldsEnabled() }
        panel.add(enabledCheckBox, gbc)
        row++
        
        // URI
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        panel.add(JBLabel("URI:"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        uriField = JBTextField("bolt://localhost:7687")
        uriField.toolTipText = "Neo4j数据库连接URI，如 bolt://localhost:7687"
        panel.add(uriField, gbc)
        row++
        
        // 用户名
        gbc.gridx = 0
        gbc.gridy = row
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JBLabel("用户名:"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        usernameField = JBTextField("neo4j")
        usernameField.toolTipText = "Neo4j数据库用户名"
        panel.add(usernameField, gbc)
        row++
        
        // 密码
        gbc.gridx = 0
        gbc.gridy = row
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JBLabel("密码:"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        passwordField = JBPasswordField()
        passwordField.toolTipText = "Neo4j数据库密码"
        panel.add(passwordField, gbc)
        row++
        
        // 数据库名
        gbc.gridx = 0
        gbc.gridy = row
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JBLabel("数据库:"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        databaseField = JBTextField("autocr")
        databaseField.toolTipText = "要使用的数据库名称"
        panel.add(databaseField, gbc)
        row++
        
        // 高级设置标题
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        val advancedLabel = JBLabel("<html><h3>高级设置</h3></html>")
        advancedLabel.font = advancedLabel.font.deriveFont(14f)
        panel.add(advancedLabel, gbc)
        row++
        
        // 连接池大小
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        panel.add(JBLabel("最大连接池大小:"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        connectionPoolSizeSpinner = JSpinner(SpinnerNumberModel(10, 1, 100, 1))
        connectionPoolSizeSpinner.toolTipText = "数据库连接池的最大连接数"
        panel.add(connectionPoolSizeSpinner, gbc)
        row++
        
        // 连接超时
        gbc.gridx = 0
        gbc.gridy = row
        gbc.fill = GridBagConstraints.NONE
        panel.add(JBLabel("连接超时(ms):"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        connectionTimeoutSpinner = JSpinner(SpinnerNumberModel(5000, 1000, 60000, 1000))
        connectionTimeoutSpinner.toolTipText = "数据库连接超时时间（毫秒）"
        panel.add(connectionTimeoutSpinner, gbc)
        row++
        
        // 最大事务重试时间
        gbc.gridx = 0
        gbc.gridy = row
        gbc.fill = GridBagConstraints.NONE
        panel.add(JBLabel("最大事务重试时间(ms):"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        maxTransactionRetryTimeSpinner = JSpinner(SpinnerNumberModel(30000, 5000, 300000, 5000))
        maxTransactionRetryTimeSpinner.toolTipText = "事务失败时的最大重试时间（毫秒）"
        panel.add(maxTransactionRetryTimeSpinner, gbc)
        row++
        
        // 测试连接按钮和状态
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.CENTER
        
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.Y_AXIS)
        
        testConnectionButton = JButton("测试连接")
        testConnectionButton.addActionListener { testConnection() }
        testConnectionButton.toolTipText = "测试Neo4j数据库连接是否正常"
        
        statusLabel = JLabel(" ")
        statusLabel.horizontalAlignment = SwingConstants.CENTER
        
        buttonPanel.add(testConnectionButton)
        buttonPanel.add(Box.createVerticalStrut(10))
        buttonPanel.add(statusLabel)
        
        panel.add(buttonPanel, gbc)
        
        updateFieldsEnabled()
        
        return panel
    }
    
    private fun updateFieldsEnabled() {
        val enabled = enabledCheckBox.isSelected
        
        uriField.isEnabled = enabled
        usernameField.isEnabled = enabled
        passwordField.isEnabled = enabled
        databaseField.isEnabled = enabled
        connectionPoolSizeSpinner.isEnabled = enabled
        connectionTimeoutSpinner.isEnabled = enabled
        maxTransactionRetryTimeSpinner.isEnabled = enabled
        testConnectionButton.isEnabled = enabled
    }
    
    private fun testConnection() {
        if (!enabledCheckBox.isSelected) {
            statusLabel.text = "❌ 请先启用Neo4j连接"
            return
        }
        
        testConnectionButton.text = "测试中..."
        testConnectionButton.isEnabled = false
        statusLabel.text = "🔄 正在测试连接..."
        
        // 在后台线程中测试连接
        Thread {
            try {
                val uri = uriField.text.trim()
                val username = usernameField.text.trim()
                val password = String(passwordField.password)
                val database = databaseField.text.trim()
                
                SwingUtilities.invokeLater {
                    // 基本验证
                    if (uri.isBlank() || username.isBlank() || password.isBlank()) {
                        testConnectionButton.text = "测试连接"
                        testConnectionButton.isEnabled = true
                        statusLabel.text = "❌ 请填写完整的连接信息"
                        return@invokeLater
                    }
                    
                    if (!uri.startsWith("bolt://") && !uri.startsWith("neo4j://")) {
                        testConnectionButton.text = "测试连接"
                        testConnectionButton.isEnabled = true
                        statusLabel.text = "❌ URI格式不正确，应以bolt://或neo4j://开头"
                        return@invokeLater
                    }
                    
                    // 真正的连接测试
                    try {
                        val result = testNeo4jConnection(uri, username, password, database)
                        testConnectionButton.text = "测试连接"
                        testConnectionButton.isEnabled = true
                        
                        if (result.success) {
                            statusLabel.text = "✅ 连接测试成功！${result.message}"
                        } else {
                            statusLabel.text = "❌ 连接测试失败: ${result.message}"
                        }
                    } catch (e: Exception) {
                        testConnectionButton.text = "测试连接"
                        testConnectionButton.isEnabled = true
                        statusLabel.text = "❌ 连接测试异常: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    testConnectionButton.text = "测试连接"
                    testConnectionButton.isEnabled = true
                    statusLabel.text = "❌ 连接测试失败: ${e.message}"
                }
            }
        }.start()
    }
    
    data class ConnectionTestResult(val success: Boolean, val message: String)
    
    private fun testNeo4jConnection(uri: String, username: String, password: String, database: String): ConnectionTestResult {
        return try {
            // 使用Neo4j官方驱动进行真实的连接和认证测试
            val driver = org.neo4j.driver.GraphDatabase.driver(
                uri,
                org.neo4j.driver.AuthTokens.basic(username, password),
                org.neo4j.driver.Config.builder()
                    .withConnectionTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .withMaxConnectionLifetime(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            )
            
            // 尝试验证连接和认证
            driver.use { driverInstance ->
                driverInstance.verifyConnectivity()
                
                // 尝试执行一个简单查询来验证数据库访问
                driverInstance.session(
                    org.neo4j.driver.SessionConfig.forDatabase(database)
                ).use { session ->
                    val result = session.run("RETURN 1 as test")
                    val record = result.single()
                    val testValue = record.get("test").asInt()
                    
                    if (testValue == 1) {
                        ConnectionTestResult(true, " 连接成功，认证通过，数据库'$database'可访问")
                    } else {
                        ConnectionTestResult(false, "连接成功但查询测试失败")
                    }
                }
            }
            
        } catch (e: org.neo4j.driver.exceptions.AuthenticationException) {
            ConnectionTestResult(false, "认证失败，请检查用户名和密码")
        } catch (e: org.neo4j.driver.exceptions.ServiceUnavailableException) {
            ConnectionTestResult(false, "无法连接到Neo4j服务，请检查服务是否启动和URI是否正确")
        } catch (e: org.neo4j.driver.exceptions.ClientException) {
            if (e.message?.contains("database does not exist") == true) {
                ConnectionTestResult(false, "数据库'$database'不存在，请检查数据库名称")
            } else {
                ConnectionTestResult(false, "客户端错误: ${e.message}")
            }
        } catch (e: java.net.ConnectException) {
            ConnectionTestResult(false, "无法连接到 $uri，请检查Neo4j服务是否启动")
        } catch (e: java.net.SocketTimeoutException) {
            ConnectionTestResult(false, "连接超时，请检查网络或Neo4j服务状态")
        } catch (e: java.net.UnknownHostException) {
            ConnectionTestResult(false, "无法解析主机名，请检查URI格式")
        } catch (e: Exception) {
            ConnectionTestResult(false, "连接错误: ${e.message}")
        }
    }
    
    fun loadFrom(settings: Neo4jSettings) {
        enabledCheckBox.isSelected = settings.enabled
        uriField.text = settings.uri
        usernameField.text = settings.username
        passwordField.text = settings.password
        databaseField.text = settings.database
        connectionPoolSizeSpinner.value = settings.maxConnectionPoolSize
        connectionTimeoutSpinner.value = settings.connectionTimeout.toInt()
        maxTransactionRetryTimeSpinner.value = settings.maxTransactionRetryTime.toInt()
        updateFieldsEnabled()
    }
    
    fun applyTo(settings: Neo4jSettings) {
        settings.enabled = enabledCheckBox.isSelected
        settings.uri = uriField.text.trim()
        settings.username = usernameField.text.trim()
        settings.password = String(passwordField.password)
        settings.database = databaseField.text.trim()
        settings.maxConnectionPoolSize = connectionPoolSizeSpinner.value as Int
        settings.connectionTimeout = (connectionTimeoutSpinner.value as Int).toLong()
        settings.maxTransactionRetryTime = (maxTransactionRetryTimeSpinner.value as Int).toLong()
    }
    
    fun getCurrentSettings(): Neo4jSettings {
        return Neo4jSettings(
            enabled = enabledCheckBox.isSelected,
            uri = uriField.text.trim(),
            username = usernameField.text.trim(),
            password = String(passwordField.password),
            database = databaseField.text.trim(),
            maxConnectionPoolSize = connectionPoolSizeSpinner.value as Int,
            connectionTimeout = (connectionTimeoutSpinner.value as Int).toLong(),
            maxTransactionRetryTime = (maxTransactionRetryTimeSpinner.value as Int).toLong()
        )
    }
    
    fun isModified(settings: Neo4jSettings): Boolean {
        return enabledCheckBox.isSelected != settings.enabled ||
               uriField.text.trim() != settings.uri ||
               usernameField.text.trim() != settings.username ||
               String(passwordField.password) != settings.password ||
               databaseField.text.trim() != settings.database ||
               connectionPoolSizeSpinner.value != settings.maxConnectionPoolSize ||
               (connectionTimeoutSpinner.value as Int).toLong() != settings.connectionTimeout ||
               (maxTransactionRetryTimeSpinner.value as Int).toLong() != settings.maxTransactionRetryTime
    }
}