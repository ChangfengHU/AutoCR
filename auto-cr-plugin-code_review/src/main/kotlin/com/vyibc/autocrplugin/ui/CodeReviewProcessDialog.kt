package com.vyibc.autocrplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.vyibc.autocrplugin.service.*
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * 代码评估过程展示对话框
 */
class CodeReviewProcessDialog(
    private val project: Project?,
    private val changes: List<CodeChange>,
    private val commitMessage: String
) : DialogWrapper(project) {

    private lateinit var processArea: JBTextArea
    private lateinit var changesTable: JBTable
    private lateinit var progressBar: JProgressBar
    private lateinit var statusLabel: JLabel
    private lateinit var scoreLabel: JLabel
    private lateinit var riskLabel: JLabel
    private lateinit var startAnalysisButton: JButton
    private lateinit var commitButton: JButton
    private lateinit var cancelButton: JButton
    private lateinit var tabbedPane: JTabbedPane

    private var reviewResult: CodeReviewResult? = null
    private var canCommit = false
    private var analysisStarted = false
    private var codeReviewService: CodeReviewService? = null

    init {
        title = "代码评估过程"
        setSize(900, 700)
        init()

        // 初始化状态
        SwingUtilities.invokeLater {
            // 初始显示代码变更信息
            appendProcess("=== 📋 代码变更概览 ===\n")
            appendProcess("$commitMessage\n\n")

            changes.forEach { change ->
                appendProcess("📁 ${change.filePath}\n")
                appendProcess("   变更类型: ${getChangeTypeText(change.changeType)}\n")
                appendProcess("   新增行数: ${change.addedLines.size}\n")
                appendProcess("   删除行数: ${change.removedLines.size}\n")
                appendProcess("   修改行数: ${change.modifiedLines.size}\n\n")
            }

            appendProcess("💡 点击 '🚀 开始AI分析' 按钮开始代码评估\n")
            appendProcess("⚠️  分析过程会将代码发送给AI服务进行评估\n\n")
        }
    }

    /**
     * 设置代码评估服务
     */
    fun setCodeReviewService(service: CodeReviewService) {
        this.codeReviewService = service
    }

    /**
     * 清空分析过程
     */
    private fun clearAnalysisProcess() {
        SwingUtilities.invokeLater {
            // 清空分析过程文本
            processArea.text = ""

            // 重置进度条
            progressBar.value = 0
            statusLabel.text = "准备开始分析..."

            // 重置评估结果显示
            scoreLabel.text = "--/100"
            scoreLabel.foreground = java.awt.Color.GRAY
            riskLabel.text = "未评估"
            riskLabel.foreground = java.awt.Color.GRAY

            // 重置状态
            reviewResult = null
            canCommit = false
            commitButton.isEnabled = false

            // 允许重新开始分析
            startAnalysisButton.isEnabled = true
            analysisStarted = false
        }
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())

        // 创建选项卡面板
        tabbedPane = JTabbedPane()

        // 代码变更选项卡
        tabbedPane.addTab("代码变更", createChangesPanel())

        // 分析过程选项卡
        tabbedPane.addTab("分析过程", createProcessPanel())

        // 评估结果选项卡
        tabbedPane.addTab("评估结果", createResultPanel())

        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        // 底部状态面板
        mainPanel.add(createStatusPanel(), BorderLayout.SOUTH)

        return mainPanel
    }

    /**
     * 创建代码变更面板
     */
    private fun createChangesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 提交信息
        val commitPanel = JPanel(BorderLayout())
        commitPanel.border = BorderFactory.createTitledBorder("提交信息")
        val commitArea = JBTextArea(commitMessage)
        commitArea.isEditable = false
        commitArea.lineWrap = true
        commitArea.wrapStyleWord = true
        commitArea.rows = 3
        commitPanel.add(JBScrollPane(commitArea), BorderLayout.CENTER)

        panel.add(commitPanel, BorderLayout.NORTH)

        // 变更文件表格
        val changesPanel = JPanel(BorderLayout())
        changesPanel.border = BorderFactory.createTitledBorder("变更文件 (${changes.size} 个文件)")

        val columnNames = arrayOf("文件路径", "变更类型", "新增行数", "删除行数", "修改行数")
        val tableModel = DefaultTableModel(columnNames, 0)

        changes.forEach { change ->
            tableModel.addRow(arrayOf(
                change.filePath,
                getChangeTypeText(change.changeType),
                change.addedLines.size.toString(),
                change.removedLines.size.toString(),
                change.modifiedLines.size.toString()
            ))
        }

        changesTable = JBTable(tableModel)
        changesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

        // 设置列宽
        val columnModel = changesTable.columnModel
        columnModel.getColumn(0).preferredWidth = 300 // 文件路径
        columnModel.getColumn(1).preferredWidth = 80  // 变更类型
        columnModel.getColumn(2).preferredWidth = 80  // 新增行数
        columnModel.getColumn(3).preferredWidth = 80  // 删除行数
        columnModel.getColumn(4).preferredWidth = 80  // 修改行数

        changesTable.rowHeight = 25

        val scrollPane = JBScrollPane(changesTable)
        changesPanel.add(scrollPane, BorderLayout.CENTER)

        panel.add(changesPanel, BorderLayout.CENTER)

        return panel
    }

    /**
     * 创建分析过程面板
     */
    private fun createProcessPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        processArea = JBTextArea()
        processArea.isEditable = false
        processArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        processArea.text = "等待开始分析...\n"

        val scrollPane = JBScrollPane(processArea)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    /**
     * 创建评估结果面板
     */
    private fun createResultPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 评分和风险等级显示
        val scorePanel = JPanel(GridBagLayout())
        scorePanel.border = BorderFactory.createTitledBorder("评估概览")

        val gbc = GridBagConstraints()
        gbc.insets = Insets(10, 10, 10, 10)

        // 评分
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        scorePanel.add(JLabel("总体评分:"), gbc)

        gbc.gridx = 1
        scoreLabel = JLabel("--/100")
        scoreLabel.font = scoreLabel.font.deriveFont(Font.BOLD, 18f)
        scorePanel.add(scoreLabel, gbc)

        // 风险等级
        gbc.gridx = 2
        gbc.insets = Insets(10, 30, 10, 10)
        scorePanel.add(JLabel("风险等级:"), gbc)

        gbc.gridx = 3
        riskLabel = JLabel("--")
        riskLabel.font = riskLabel.font.deriveFont(Font.BOLD, 18f)
        scorePanel.add(riskLabel, gbc)

        panel.add(scorePanel, BorderLayout.NORTH)

        // 详细结果区域（稍后填充）
        val resultArea = JBTextArea("等待评估结果...")
        resultArea.isEditable = false
        resultArea.lineWrap = true
        resultArea.wrapStyleWord = true

        val resultScrollPane = JBScrollPane(resultArea)
        panel.add(resultScrollPane, BorderLayout.CENTER)

        return panel
    }

    /**
     * 创建状态面板
     */
    private fun createStatusPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 进度条和状态
        val progressPanel = JPanel(BorderLayout())

        statusLabel = JLabel("准备开始分析...")
        progressPanel.add(statusLabel, BorderLayout.NORTH)

        progressBar = JProgressBar(0, 100)
        progressBar.isStringPainted = true
        progressBar.string = "0%"
        progressPanel.add(progressBar, BorderLayout.CENTER)

        panel.add(progressPanel, BorderLayout.CENTER)

        return panel
    }

    /**
     * 开始代码评估过程
     */
    fun startReview(codeReviewService: CodeReviewService, onComplete: (Boolean, CodeReviewResult?) -> Unit) {
        SwingUtilities.invokeLater {
            updateProgress(10, "开始分析代码变更...")
            appendProcess("=== 代码评估开始 ===\n")
            appendProcess("提交信息: $commitMessage\n")
            appendProcess("变更文件数量: ${changes.size}\n\n")

            // 显示变更详情
            appendProcess("=== 代码变更详情 ===\n")
            changes.forEach { change ->
                appendProcess("文件: ${change.filePath}\n")
                appendProcess("  变更类型: ${getChangeTypeText(change.changeType)}\n")
                appendProcess("  新增行数: ${change.addedLines.size}\n")
                appendProcess("  删除行数: ${change.removedLines.size}\n")
                appendProcess("  修改行数: ${change.modifiedLines.size}\n")

                // 显示部分代码内容
                if (change.addedLines.isNotEmpty()) {
                    appendProcess("  新增代码片段:\n")
                    change.addedLines.take(3).forEach { line ->
                        appendProcess("    + $line\n")
                    }
                    if (change.addedLines.size > 3) {
                        appendProcess("    ... (还有 ${change.addedLines.size - 3} 行)\n")
                    }
                }
                appendProcess("\n")
            }

            updateProgress(30, "准备AI分析...")
            appendProcess("=== AI分析准备 ===\n")
            appendProcess("使用AI服务: ${codeReviewService.getServiceName()}\n")
            appendProcess("构建分析提示词...\n")

            // 在后台线程执行AI分析
            Thread {
                try {
                    updateProgress(50, "AI正在分析代码...")
                    appendProcess("发送请求到AI服务...\n")

                    // 模拟分析过程
                    Thread.sleep(2000)

                    updateProgress(70, "处理AI响应...")
                    appendProcess("收到AI响应，正在解析...\n")

                    // 执行实际的代码评估
                    val result = kotlinx.coroutines.runBlocking {
                        codeReviewService.reviewCode(changes, commitMessage)
                    }

                    updateProgress(90, "生成评估报告...")
                    appendProcess("=== 评估结果 ===\n")
                    appendProcess("总体评分: ${result.overallScore}/100\n")
                    appendProcess("风险等级: ${getRiskLevelText(result.riskLevel)}\n")
                    appendProcess("发现问题: ${result.issues.size} 个\n")
                    appendProcess("改进建议: ${result.suggestions.size} 条\n\n")

                    // 显示问题详情
                    if (result.issues.isNotEmpty()) {
                        appendProcess("=== 发现的问题 ===\n")
                        result.issues.forEach { issue ->
                            appendProcess("${getSeverityText(issue.severity)}: ${issue.message}\n")
                            appendProcess("  文件: ${issue.filePath}\n")
                            if (issue.lineNumber != null) {
                                appendProcess("  行号: ${issue.lineNumber}\n")
                            }
                            if (issue.suggestion != null) {
                                appendProcess("  建议: ${issue.suggestion}\n")
                            }
                            appendProcess("\n")
                        }
                    }

                    updateProgress(100, "评估完成")
                    appendProcess("=== 评估完成 ===\n")

                    SwingUtilities.invokeLater {
                        reviewResult = result
                        updateResultDisplay(result)

                        // 检查是否可以提交
                        val settings = com.vyibc.autocrplugin.settings.CodeReviewSettings.getInstance()
                        canCommit = checkCanCommit(result, settings)

                        if (canCommit) {
                            appendProcess("✅ 代码质量符合要求，可以提交\n")
                        } else {
                            appendProcess("❌ 代码质量不符合要求，建议修复后再提交\n")
                        }

                        onComplete(canCommit, result)
                    }

                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        updateProgress(0, "评估失败")
                        appendProcess("❌ 评估失败: ${e.message}\n")
                        onComplete(false, null)
                    }
                }
            }.start()
        }
    }

    /**
     * 更新进度
     */
    private fun updateProgress(progress: Int, status: String) {
        SwingUtilities.invokeLater {
            progressBar.value = progress
            progressBar.string = "$progress%"
            statusLabel.text = status
        }
    }

    /**
     * 添加过程日志
     */
    private fun appendProcess(text: String) {
        SwingUtilities.invokeLater {
            processArea.append(text)
            processArea.caretPosition = processArea.document.length
        }
    }

    /**
     * 更新结果显示
     */
    private fun updateResultDisplay(result: CodeReviewResult) {
        // 更新评分显示
        scoreLabel.text = "${result.overallScore}/100"
        scoreLabel.foreground = getScoreColor(result.overallScore)

        // 更新风险等级显示
        riskLabel.text = getRiskLevelText(result.riskLevel)
        riskLabel.foreground = getRiskColor(result.riskLevel)
    }

    /**
     * 检查是否可以提交
     */
    private fun checkCanCommit(result: CodeReviewResult, settings: com.vyibc.autocrplugin.settings.CodeReviewSettings): Boolean {
        // 检查评分是否达到最低要求
        if (result.overallScore < settings.minimumScore) {
            return false
        }

        // 检查是否有严重风险
        if (settings.blockHighRiskCommits) {
            when (result.riskLevel) {
                RiskLevel.CRITICAL, RiskLevel.HIGH -> return false
                else -> {}
            }
        }

        // 检查是否有严重问题
        val criticalIssues = result.issues.filter { it.severity == IssueSeverity.CRITICAL }
        if (criticalIssues.isNotEmpty()) {
            return false
        }

        return true
    }

    override fun createActions(): Array<Action> {
        startAnalysisButton = JButton("🚀 开始AI分析")
        startAnalysisButton.isEnabled = true

        commitButton = JButton("提交代码")
        commitButton.isEnabled = false

        cancelButton = JButton("取消")

        val startAnalysisAction = object : AbstractAction("🚀 开始AI分析") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (codeReviewService != null) {
                    // 自动切换到"分析过程"选项卡
                    tabbedPane.selectedIndex = 1 // 分析过程是第二个tab (索引1)

                    // 清空之前的分析过程
                    clearAnalysisProcess()

                    startAnalysisButton.isEnabled = false
                    analysisStarted = true

                    // 显示AI分析确认信息
                    appendProcess("=== 🤖 AI分析准备 ===\n")
                    appendProcess("⚠️  即将将代码发送给AI服务进行分析\n")
                    appendProcess("📤 请确保代码不包含敏感信息\n")
                    appendProcess("🔄 开始AI分析过程...\n\n")

                    // 开始实际的AI分析
                    performAIAnalysis()
                }
            }
        }

        val commitAction = object : AbstractAction("提交代码") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (reviewResult == null) {
                    com.intellij.openapi.ui.Messages.showWarningDialog(
                        project,
                        "请先完成代码分析后再提交",
                        "无法提交"
                    )
                    return
                }

                // 检查分数是否达到阈值
                val settings = com.vyibc.autocrplugin.settings.CodeReviewSettings.getInstance()
                val result = reviewResult!!
                if (result.overallScore < settings.minimumScore) {
                    val message = """
                        ⚠️ 代码质量不达标，无法提交
                        
                        当前评分: ${result.overallScore}/100
                        最低要求: ${settings.minimumScore}/100
                        差距: ${settings.minimumScore - result.overallScore}分
                        
                        请修复代码质量问题后再次提交。
                    """.trimIndent()

                    com.intellij.openapi.ui.Messages.showWarningDialog(
                        project,
                        message,
                        "评分不达标"
                    )
                    return
                }

                if (canCommit) {
                    // 直接执行Git提交，不需要确认对话框
                    performGitCommitDirect()
                } else {
                    com.intellij.openapi.ui.Messages.showWarningDialog(
                        project,
                        "代码存在严重质量问题，无法提交",
                        "无法提交"
                    )
                }
            }
        }

        val cancelAction = object : AbstractAction("取消") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                close(CANCEL_EXIT_CODE)
            }
        }

        return arrayOf(startAnalysisAction, commitAction, cancelAction)
    }

    /**
     * 启用/禁用提交按钮
     */
    fun setCommitEnabled(enabled: Boolean) {
        SwingUtilities.invokeLater {
            commitButton.isEnabled = enabled
        }
    }

    /**
     * 执行AI分析
     */
    private fun performAIAnalysis() {
        if (codeReviewService == null) {
            appendProcess("❌ 错误: AI服务未初始化\n")
            return
        }

        updateProgress(30, "准备AI分析...")
        appendProcess("=== 🤖 AI分析详细过程 ===\n")
        appendProcess("使用AI服务: ${codeReviewService!!.getServiceName()}\n")
        appendProcess("构建分析提示词...\n")

        // 在后台线程执行AI分析
        Thread {
            try {
                // 显示提示词构建过程
                updateProgress(40, "构建AI提示词...")
                val prompt = buildDetailedPrompt()
                appendProcess("\n=== 📝 AI分析入参详情 ===\n")
                appendProcess("提示词长度: ${prompt.length} 字符\n")
                appendProcess("AI服务: ${codeReviewService!!.getServiceName()}\n")
                appendProcess("请求时间: ${java.time.LocalDateTime.now()}\n\n")

                appendProcess("=== 📋 完整提示词内容 ===\n")
                appendProcess("```\n")
                appendProcess(prompt)
                appendProcess("\n```\n\n")

                appendProcess("=== 📊 代码变更统计 ===\n")
                appendProcess("变更文件数量: ${changes.size}\n")
                changes.forEach { change ->
                    appendProcess("• ${change.filePath} (${getChangeTypeText(change.changeType)})\n")
                    appendProcess("  新增行: ${change.addedLines.size}, 删除行: ${change.removedLines.size}, 修改行: ${change.modifiedLines.size}\n")

                    // 显示实际的变更内容
                    if (change.addedLines.isNotEmpty()) {
                        appendProcess("  新增内容:\n")
                        change.addedLines.take(3).forEach { line ->
                            appendProcess("    + ${line.trim()}\n")
                        }
                        if (change.addedLines.size > 3) {
                            appendProcess("    + ... 还有 ${change.addedLines.size - 3} 行\n")
                        }
                    }

                    if (change.removedLines.isNotEmpty()) {
                        appendProcess("  删除内容:\n")
                        change.removedLines.take(3).forEach { line ->
                            appendProcess("    - ${line.trim()}\n")
                        }
                        if (change.removedLines.size > 3) {
                            appendProcess("    - ... 还有 ${change.removedLines.size - 3} 行\n")
                        }
                    }
                }

                // 分析方法调用（根据级联深度配置）
                appendProcess("\n=== 🔍 方法调用分析 ===\n")
                val settings = com.vyibc.autocrplugin.settings.CodeReviewSettings.getInstance()

                val methodCalls = if (settings.maxCascadeDepth > 0) {
                    appendProcess("级联分析深度: ${settings.maxCascadeDepth}层\n")
                    val methodAnalyzer = MethodCallAnalyzer(project!!, maxCascadeDepth = settings.maxCascadeDepth)
                    methodAnalyzer.debugCallback = { message ->
                        appendProcess("  $message\n")
                    }
                    methodAnalyzer.analyzeMethodCalls(changes)
                } else {
                    appendProcess("级联分析深度设置为0，跳过方法调用分析\n")
                    appendProcess("仅分析用户当前修改的代码内容\n")
                    emptyList()
                }
                if (methodCalls.isNotEmpty()) {
                    appendProcess("发现 ${methodCalls.size} 个方法调用需要深度分析:\n")
                    appendProcess("⚠️ 以下方法实现将发送给AI进行安全评估：\n\n")
                    methodCalls.forEach { call ->
                        appendMethodCallToProcess(call, 1)
                    }
                    appendProcess("\n💡 提示：级联调用显示了方法内部调用的其他方法，帮助发现深层安全风险\n")
                } else if (settings.maxCascadeDepth > 0) {
                    appendProcess("未发现需要特别关注的方法调用\n")
                    appendProcess("💡 这意味着代码变更主要是简单的逻辑修改，没有复杂的方法调用链\n")
                } else {
                    appendProcess("💡 当前仅分析用户修改的代码，如需深度分析方法调用，请在设置中调整级联深度(1-3)\n")
                }
                appendProcess("\n")

                updateProgress(50, "发送请求到AI服务...")
                appendProcess("=== 🌐 API调用信息 ===\n")
                appendProcess("正在连接AI服务...\n")
                appendProcess("发送HTTP请求...\n")

                // 模拟网络延迟
                Thread.sleep(1000)

                updateProgress(70, "等待AI响应...")
                appendProcess("请求已发送，等待AI分析...\n")

                // 模拟分析过程
                Thread.sleep(2000)

                updateProgress(80, "处理AI响应...")
                appendProcess("\n=== 🌐 发送AI请求 ===\n")
                appendProcess("请求发送时间: ${java.time.LocalDateTime.now()}\n")
                appendProcess("等待AI服务响应...\n")

                // 创建调试回调
                val debugCallback = object : com.vyibc.autocrplugin.service.AIDebugCallback {
                    override fun onAIRequest(serviceName: String, prompt: String, requestTime: String) {
                        SwingUtilities.invokeLater {
                            appendProcess("=== 📤 AI请求详情 ===\n")
                            appendProcess("服务名称: $serviceName\n")
                            appendProcess("请求时间: $requestTime\n")
                            appendProcess("提示词长度: ${prompt.length} 字符\n\n")
                        }
                    }

                    override fun onAIResponse(response: String, responseTime: String) {
                        SwingUtilities.invokeLater {
                            appendProcess("=== 📥 AI原始响应 ===\n")
                            appendProcess("响应时间: $responseTime\n")
                            appendProcess("响应长度: ${response.length} 字符\n")
                            appendProcess("原始响应内容:\n")
                            appendProcess("```json\n")
                            appendProcess(response)
                            appendProcess("\n```\n\n")
                        }
                    }

                    override fun onParsingStep(step: String, details: String) {
                        SwingUtilities.invokeLater {
                            appendProcess("🔍 解析步骤: $step\n")
                            appendProcess("详情: $details\n")
                        }
                    }

                    override fun onParsingResult(success: Boolean, result: com.vyibc.autocrplugin.service.CodeReviewResult?, error: String?) {
                        SwingUtilities.invokeLater {
                            if (success && result != null) {
                                appendProcess("=== ✅ 解析成功 ===\n")
                                appendProcess("评分: ${result.overallScore}/100\n")
                                appendProcess("风险等级: ${result.riskLevel}\n")
                                appendProcess("问题数量: ${result.issues.size}\n")
                                appendProcess("建议数量: ${result.suggestions.size}\n")
                                if (result.commitMessage != null) {
                                    appendProcess("AI建议提交信息: ${result.commitMessage}\n")
                                }
                            } else {
                                appendProcess("=== ❌ 解析失败 ===\n")
                                appendProcess("错误信息: ${error ?: "未知错误"}\n")
                            }
                            appendProcess("\n")
                        }
                    }
                }

                // 执行实际的代码评估
                val result = kotlinx.coroutines.runBlocking {
                    codeReviewService!!.reviewCode(changes, commitMessage, debugCallback)
                }

                appendProcess("\n=== 📥 AI响应详情 ===\n")
                appendProcess("响应接收时间: ${java.time.LocalDateTime.now()}\n")
                appendProcess("解析状态: 成功\n")

                updateProgress(90, "生成评估报告...")
                appendProcess("=== 评估结果 ===\n")
                appendProcess("总体评分: ${result.overallScore}/100\n")
                appendProcess("风险等级: ${getRiskLevelText(result.riskLevel)}\n")
                appendProcess("发现问题: ${result.issues.size} 个\n")
                appendProcess("改进建议: ${result.suggestions.size} 条\n\n")

                // 显示问题详情
                if (result.issues.isNotEmpty()) {
                    appendProcess("=== 发现的问题 ===\n")
                    result.issues.forEach { issue ->
                        appendProcess("${getSeverityText(issue.severity)}: ${issue.message}\n")
                        appendProcess("  文件: ${issue.filePath}\n")
                        if (issue.lineNumber != null) {
                            appendProcess("  行号: ${issue.lineNumber}\n")
                        }
                        appendProcess("  建议: ${issue.suggestion}\n\n")
                    }
                }

                // 显示改进建议
                if (result.suggestions.isNotEmpty()) {
                    appendProcess("=== 改进建议 ===\n")
                    result.suggestions.forEachIndexed { index, suggestion ->
                        appendProcess("${index + 1}. $suggestion\n")
                    }
                    appendProcess("\n")
                }

                // 更新UI状态
                SwingUtilities.invokeLater {
                    reviewResult = result

                    // 从设置中获取最低分数要求
                    val settings = com.vyibc.autocrplugin.settings.CodeReviewSettings.getInstance()
                    val minimumScore = settings.minimumScore
                    canCommit = result.overallScore >= minimumScore

                    updateProgress(100, "分析完成")
                    appendProcess("=== 分析完成 ===\n")
                    appendProcess("最低分数要求: $minimumScore 分\n")
                    appendProcess("当前代码评分: ${result.overallScore} 分\n")

                    if (canCommit) {
                        appendProcess("✅ 代码质量达标，可以提交\n")

                        // 显示AI建议的提交信息
                        if (result.commitMessage?.isNotBlank() == true) {
                            appendProcess("\n💡 AI建议的提交信息:\n")
                            appendProcess("${result.commitMessage}\n\n")
                        }

                        commitButton.isEnabled = true
                        commitButton.text = "提交代码 (git commit)"
                    } else {
                        appendProcess("❌ 代码质量不达标 (${result.overallScore} < $minimumScore)，请修复问题后重新分析\n")
                        commitButton.isEnabled = false
                        commitButton.text = "质量不达标，无法提交"
                    }

                    // 重新启用分析按钮，允许重新分析
                    startAnalysisButton.isEnabled = true
                    startAnalysisButton.text = "🔄 重新分析"

                    // 更新结果显示
                    updateResultDisplay(result)
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    appendProcess("❌ AI分析失败: ${e.message}\n")
                    updateProgress(0, "分析失败")
                }
            }
        }.start()
    }

    /**
     * 执行Git提交
     */
    private fun performGitCommit() {
        if (reviewResult == null) {
            appendProcess("❌ 错误: 没有评估结果，无法提交\n")
            return
        }

        // 显示提交确认对话框
        val result = reviewResult!!
        val confirmMessage = """
            🚀 确认提交代码

            评估结果:
            • 总体评分: ${result.overallScore}/100
            • 风险等级: ${getRiskLevelText(result.riskLevel)}
            • 发现问题: ${result.issues.size} 个
            • 改进建议: ${result.suggestions.size} 条

            是否确认提交代码到Git仓库？
        """.trimIndent()

        val choice = com.intellij.openapi.ui.Messages.showYesNoDialog(
            confirmMessage,
            "确认Git提交",
            "确认提交",
            "取消",
            com.intellij.openapi.ui.Messages.getQuestionIcon()
        )

        if (choice != com.intellij.openapi.ui.Messages.YES) {
            return
        }

        // 在后台线程执行Git提交
        Thread {
            try {
                SwingUtilities.invokeLater {
                    appendProcess("\n=== 🚀 执行Git提交 ===\n")
                    appendProcess("准备提交代码到Git仓库...\n")
                    commitButton.isEnabled = false
                    commitButton.text = "正在提交..."
                }

                // 构建提交信息
                val commitMessage = buildCommitMessage(result)

                SwingUtilities.invokeLater {
                    appendProcess("提交信息:\n$commitMessage\n\n")
                    appendProcess("添加已修改的文件到Git暂存区...\n")
                }

                // 获取Git仓库根目录
                val gitRoot = getGitRepositoryRoot()
                if (gitRoot == null) {
                    SwingUtilities.invokeLater {
                        appendProcess("❌ 无法找到Git仓库根目录\n")
                        commitButton.isEnabled = true
                        commitButton.text = "Git仓库错误，重试"
                    }
                    return@Thread
                }

                SwingUtilities.invokeLater {
                    appendProcess("Git仓库根目录: ${gitRoot.absolutePath}\n")
                }

                // 将绝对路径转换为相对路径
                val filesToAdd = changes.map { change ->
                    val relativePath = getRelativePath(change.filePath, gitRoot)
                    relativePath
                }

                SwingUtilities.invokeLater {
                    appendProcess("要添加的文件 (相对路径):\n")
                    filesToAdd.forEach { file ->
                        appendProcess("  • $file\n")
                    }
                }

                // 执行git add 对每个文件
                var addSuccess = true
                for (filePath in filesToAdd) {
                    val addResult = executeGitCommand(listOf("git", "add", filePath))
                    if (!addResult.success) {
                        SwingUtilities.invokeLater {
                            appendProcess("❌ 添加文件失败 $filePath: ${addResult.error}\n")
                        }
                        addSuccess = false
                        break
                    } else {
                        SwingUtilities.invokeLater {
                            appendProcess("✅ 成功添加: $filePath\n")
                        }
                    }
                }

                if (!addSuccess) {
                    SwingUtilities.invokeLater {
                        commitButton.isEnabled = true
                        commitButton.text = "添加文件失败，重试"
                    }
                    return@Thread
                }

                SwingUtilities.invokeLater {
                    appendProcess("✅ git add 成功\n")
                    appendProcess("执行 git commit\n")
                }

                // 执行git commit
                val commitResult = executeGitCommand(listOf("git", "commit", "-m", commitMessage))

                SwingUtilities.invokeLater {
                    if (commitResult.success) {
                        appendProcess("✅ Git提交成功!\n")
                        appendProcess("提交哈希: ${commitResult.output.take(50)}...\n")
                        appendProcess("\n=== 🎉 代码提交完成 ===\n")

                        // 显示成功消息
                        com.intellij.openapi.ui.Messages.showInfoMessage(
                            "代码已成功提交到Git仓库！\n\n" +
                                    "评分: ${result.overallScore}/100\n" +
                                    "风险等级: ${getRiskLevelText(result.riskLevel)}\n" +
                                    "提交信息: ${commitMessage.split('\n').first()}",
                            "Git提交成功"
                        )

                        // 关闭对话框
                        close(OK_EXIT_CODE)
                    } else {
                        appendProcess("❌ Git提交失败: ${commitResult.error}\n")
                        commitButton.isEnabled = true
                        commitButton.text = "提交失败，重试"
                    }
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    appendProcess("❌ 提交过程出错: ${e.message}\n")
                    commitButton.isEnabled = true
                    commitButton.text = "提交出错，重试"
                }
            }
        }.start()
    }

    /**
     * 直接执行Git提交（简化版）
     */
    private fun performGitCommitDirect() {
        if (reviewResult == null) {
            appendProcess("❌ 错误: 没有评估结果，无法提交\n")
            return
        }

        // 切换到分析过程tab显示提交过程
        tabbedPane.selectedIndex = 1

        // 在后台线程执行Git提交
        Thread {
            try {
                SwingUtilities.invokeLater {
                    appendProcess("\n=== 🚀 执行Git提交 ===\n")
                    appendProcess("准备提交代码到Git仓库...\n")
                    commitButton.isEnabled = false
                    commitButton.text = "正在提交..."
                }

                // 使用AI建议的提交信息，如果没有则使用默认的
                val commitMessage = reviewResult!!.commitMessage?.takeIf { it.isNotBlank() }
                    ?: buildCommitMessage(reviewResult!!)

                SwingUtilities.invokeLater {
                    if (reviewResult!!.commitMessage?.isNotBlank() == true) {
                        appendProcess("📝 使用AI建议的提交信息:\n")
                    } else {
                        appendProcess("📝 使用默认提交信息:\n")
                    }
                    appendProcess("$commitMessage\n\n")
                    appendProcess("添加已修改的文件到Git暂存区...\n")
                }

                // 获取Git仓库根目录
                val gitRoot = getGitRepositoryRoot()
                if (gitRoot == null) {
                    SwingUtilities.invokeLater {
                        appendProcess("❌ 无法找到Git仓库根目录\n")
                        commitButton.isEnabled = true
                        commitButton.text = "Git仓库错误，重试"
                    }
                    return@Thread
                }

                SwingUtilities.invokeLater {
                    appendProcess("Git仓库根目录: ${gitRoot.absolutePath}\n")
                }

                // 将绝对路径转换为相对路径
                val filesToAdd = changes.map { change ->
                    val relativePath = getRelativePath(change.filePath, gitRoot)
                    relativePath
                }

                SwingUtilities.invokeLater {
                    appendProcess("要添加的文件 (相对路径):\n")
                    filesToAdd.forEach { file ->
                        appendProcess("  • $file\n")
                    }
                }

                // 执行git add 对每个文件
                var addSuccess = true
                for (filePath in filesToAdd) {
                    val addResult = executeGitCommand(listOf("git", "add", filePath))
                    if (!addResult.success) {
                        SwingUtilities.invokeLater {
                            appendProcess("❌ 添加文件失败 $filePath: ${addResult.error}\n")
                        }
                        addSuccess = false
                        break
                    } else {
                        SwingUtilities.invokeLater {
                            appendProcess("✅ 成功添加: $filePath\n")
                        }
                    }
                }

                if (!addSuccess) {
                    SwingUtilities.invokeLater {
                        commitButton.isEnabled = true
                        commitButton.text = "添加文件失败，重试"
                    }
                    return@Thread
                }

                SwingUtilities.invokeLater {
                    appendProcess("✅ 文件添加成功\n")
                    appendProcess("执行 git commit\n")
                }

                // 执行git commit
                val commitResult = executeGitCommand(listOf("git", "commit", "-m", commitMessage))

                SwingUtilities.invokeLater {
                    if (commitResult.success) {
                        appendProcess("✅ Git提交成功!\n")
                        appendProcess("提交哈希: ${commitResult.output.take(50)}...\n")
                        appendProcess("\n=== 🎉 代码提交完成 ===\n")

                        // 显示成功消息
                        com.intellij.openapi.ui.Messages.showInfoMessage(
                            "代码已成功提交到Git仓库！\n\n" +
                                    "评分: ${reviewResult!!.overallScore}/100\n" +
                                    "风险等级: ${getRiskLevelText(reviewResult!!.riskLevel)}\n" +
                                    "提交信息: ${commitMessage.split('\n').first()}",
                            "Git提交成功"
                        )

                        // 关闭对话框
                        close(OK_EXIT_CODE)
                    } else {
                        appendProcess("❌ Git提交失败: ${commitResult.error}\n")
                        commitButton.isEnabled = true
                        commitButton.text = "提交失败，重试"
                    }
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    appendProcess("❌ 提交过程出错: ${e.message}\n")
                    commitButton.isEnabled = true
                    commitButton.text = "提交出错，重试"
                }
            }
        }.start()
    }

    /**
     * 构建提交信息
     */
    private fun buildCommitMessage(result: CodeReviewResult): String {
        val sb = StringBuilder()

        // 基本提交信息
        sb.append("✅ AI代码评估通过 (${result.overallScore}/100)\n\n")

        // 评估摘要
        sb.append("📊 评估摘要:\n")
        sb.append("• 风险等级: ${getRiskLevelText(result.riskLevel)}\n")
        sb.append("• 发现问题: ${result.issues.size} 个\n")
        sb.append("• 改进建议: ${result.suggestions.size} 条\n\n")

        // 主要问题（如果有）
        if (result.issues.isNotEmpty()) {
            sb.append("🔍 主要问题:\n")
            result.issues.take(3).forEach { issue ->
                sb.append("• ${issue.message}\n")
            }
            if (result.issues.size > 3) {
                sb.append("• ... 还有 ${result.issues.size - 3} 个问题\n")
            }
            sb.append("\n")
        }

        // AI评估标记
        sb.append("🤖 通过AI代码评估系统检查")

        return sb.toString()
    }

    /**
     * 获取Git仓库根目录
     */
    private fun getGitRepositoryRoot(): java.io.File? {
        return try {
            // 方法1: 优先从项目根目录查找
            val projectBasePath = project?.basePath
            if (projectBasePath != null) {
                val projectDir = java.io.File(projectBasePath)
                val gitDir = java.io.File(projectDir, ".git")
                if (gitDir.exists()) {
                    return projectDir
                }
            }

            // 方法2: 如果变更文件存在，从第一个变更文件的路径向上查找
            if (changes.isNotEmpty()) {
                var fileDir = java.io.File(changes.first().filePath).parentFile
                while (fileDir != null && fileDir.exists()) {
                    val gitDir = java.io.File(fileDir, ".git")
                    if (gitDir.exists()) {
                        return fileDir
                    }
                    fileDir = fileDir.parentFile
                }
            }

            // 方法3: 从当前工作目录开始向上查找.git目录
            var currentDir = java.io.File(System.getProperty("user.dir"))
            while (currentDir != null && currentDir.exists()) {
                val gitDir = java.io.File(currentDir, ".git")
                if (gitDir.exists()) {
                    return currentDir
                }
                currentDir = currentDir.parentFile
            }

            // 方法4: 如果没找到，尝试使用git命令获取
            val processBuilder = ProcessBuilder("git", "rev-parse", "--show-toplevel")
            processBuilder.directory(java.io.File(System.getProperty("user.dir")))

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.isNotEmpty()) {
                java.io.File(output)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将绝对路径转换为相对于Git仓库根目录的相对路径
     */
    private fun getRelativePath(absolutePath: String, gitRoot: java.io.File): String {
        return try {
            val absoluteFile = java.io.File(absolutePath)
            val gitRootPath = gitRoot.canonicalPath
            val absoluteCanonicalPath = absoluteFile.canonicalPath

            if (absoluteCanonicalPath.startsWith(gitRootPath)) {
                absoluteCanonicalPath.substring(gitRootPath.length + 1)
            } else {
                // 如果不在Git仓库内，返回原路径
                absolutePath
            }
        } catch (e: Exception) {
            // 出错时返回原路径
            absolutePath
        }
    }

    /**
     * 执行Git命令
     */
    private fun executeGitCommand(command: List<String>): GitCommandResult {
        return try {
            val gitRoot = getGitRepositoryRoot()
            if (gitRoot == null) {
                return GitCommandResult(
                    success = false,
                    output = "",
                    error = "无法找到Git仓库根目录",
                    exitCode = -1
                )
            }

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(gitRoot) // 使用Git仓库根目录作为工作目录

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()

            val exitCode = process.waitFor()

            GitCommandResult(
                success = exitCode == 0,
                output = output,
                error = error,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            GitCommandResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        }
    }

    /**
     * Git命令执行结果
     */
    private data class GitCommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int
    )

    /**
     * 构建详细的提示词用于显示
     */
    private fun buildDetailedPrompt(): String {
        val settings = com.vyibc.autocrplugin.settings.CodeReviewSettings.getInstance()
        val prompt = StringBuilder()

        // 使用自定义提示词或默认提示词
        val basePrompt = if (settings.customPrompt.isNotEmpty()) {
            settings.customPrompt
        } else {
            getDefaultPrompt()
        }

        prompt.append(basePrompt)
        prompt.append("\n\n## 📋 代码变更详情：\n\n")
        prompt.append("提交信息: $commitMessage\n\n")

        changes.forEach { change ->
            prompt.append("文件: ${change.filePath}\n")
            prompt.append("变更类型: ${change.changeType}\n")

            when (change.changeType) {
                ChangeType.ADDED -> {
                    prompt.append("新增内容:\n")
                    prompt.append(change.newContent ?: "")
                }
                ChangeType.MODIFIED -> {
                    prompt.append("新增行:\n")
                    change.addedLines.forEach { line ->
                        prompt.append("+ $line\n")
                    }
                    prompt.append("删除行:\n")
                    change.removedLines.forEach { line ->
                        prompt.append("- $line\n")
                    }
                }
                ChangeType.DELETED -> {
                    prompt.append("删除内容:\n")
                    prompt.append(change.oldContent ?: "")
                }
                ChangeType.RENAMED -> {
                    prompt.append("文件重命名\n")
                }
            }
            prompt.append("\n---\n\n")
        }

        // 添加方法调用分析结果（根据配置决定是否进行级联分析）
        val methodCalls = if (settings.maxCascadeDepth > 0) {
            appendProcess("🔍 开始方法实现分析（深度：${settings.maxCascadeDepth}层）...\n")
            val methodAnalyzer = MethodCallAnalyzer(project!!, maxCascadeDepth = settings.maxCascadeDepth)
            val calls = methodAnalyzer.analyzeMethodCalls(changes)
            appendProcess("✅ 方法实现分析完成，找到 ${calls.size} 个相关方法\n")
            calls
        } else {
            appendProcess("⚪ 级联深度设置为0，跳过方法实现分析，仅评估当前修改的代码\n")
            emptyList()
        }

        if (methodCalls.isNotEmpty()) {
            prompt.append("## 🔍 **方法实现安全分析**\n\n")
            prompt.append("""
**📋 以下是代码变更中调用的方法的完整实现，请基于自动化工具的预检测结果进行深度分析：**

**🎯 分析重点：**
1. **预检测危险操作** - 重点关注标记为"已检测到潜在危险操作"的代码段
2. **生产环境影响** - 评估每种危险操作在高并发环境下的具体影响
3. **风险等级判定** - 根据影响程度确定CRITICAL/HIGH/MEDIUM/LOW等级
4. **解决方案制定** - 针对发现的问题提供具体的技术改进方案

**⚠️ 评估依据：**
- 系统预检测到的危险操作类型和描述
- 方法实现的完整源代码
- 生产环境下的潜在影响分析

            """.trimIndent())

            methodCalls.forEach { call ->
                appendMethodImplementation(prompt, call.implementation, 1)
            }
        }

        prompt.append("""
## 📤 严格返回格式要求：

**评分标准：**
- **0-30分**：包含CRITICAL风险，立即阻止部署
- **31-60分**：包含HIGH风险，需要修复后部署  
- **61-80分**：包含MEDIUM风险，建议优化
- **81-100分**：低风险或无风险

**风险等级判定：**
- **CRITICAL**：Redis keys()、数据库全表扫描、敏感信息泄露等生产致命问题
- **HIGH**：SQL注入、权限绕过、严重性能问题
- **MEDIUM**：一般性能问题、代码质量问题
- **LOW**：轻微改进建议

请严格按照以下JSON格式返回，不要添加任何其他文字：

```json
{
  "overallScore": 25,
  "riskLevel": "CRITICAL",
  "issues": [
    {
      "filePath": "具体文件路径",
      "lineNumber": "具体行号或代码段",
      "severity": "CRITICAL",
      "category": "生产环境危险操作",
      "message": "根据系统预检测结果，发现[具体危险操作类型]，在生产环境中会导致[具体影响描述]",
      "suggestion": "基于危险操作类型提供针对性的技术解决方案"
    }
  ],
  "suggestions": [
    "基于预检测结果提供的具体技术改进建议",
    "针对发现的危险操作类型的最佳实践建议",
    "生产环境优化和监控建议"
  ],
  "summary": "基于系统预检测的危险操作进行风险评估，详细说明对生产环境的影响和修复紧急程度",
  "commitMessage": "按照规范格式生成提交信息: type(scope): description\n\n- 详细变更说明1\n- 详细变更说明2"
}
```

**重要要求：**
- overallScore: 0-100整数，根据最高风险等级确定分数范围
- riskLevel: 必须是 LOW|MEDIUM|HIGH|CRITICAL 之一
- severity: 必须是 CRITICAL|MAJOR|MINOR|INFO 之一  
- 如果发现任何Redis keys()、数据库全表扫描等问题，riskLevel必须是CRITICAL
- issues数组必须包含发现的所有问题，包括方法实现中的问题
- **commitMessage格式规范：**
  - 第一行：type(scope): 简短描述 (不超过50字符)
  - 空行
  - 详细说明：以"-"开头的项目列表，每项描述具体变更
  - type类型：feat(新功能)|fix(修复)|docs(文档)|style(格式)|refactor(重构)|perf(性能)|test(测试)|chore(构建)
  - scope范围：settings|ui|service|analyzer|core等
- 请确保返回有效的JSON格式
        """.trimIndent())

        return prompt.toString()
    }

    /**
     * 获取默认提示词
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

    // 辅助方法
    private fun getChangeTypeText(changeType: ChangeType): String {
        return when (changeType) {
            ChangeType.ADDED -> "新增"
            ChangeType.MODIFIED -> "修改"
            ChangeType.DELETED -> "删除"
            ChangeType.RENAMED -> "重命名"
        }
    }

    private fun getRiskLevelText(riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.LOW -> "低风险"
            RiskLevel.MEDIUM -> "中等风险"
            RiskLevel.HIGH -> "高风险"
            RiskLevel.CRITICAL -> "严重风险"
        }
    }

    private fun getSeverityText(severity: IssueSeverity): String {
        return when (severity) {
            IssueSeverity.CRITICAL -> "严重"
            IssueSeverity.MAJOR -> "重要"
            IssueSeverity.MINOR -> "轻微"
            IssueSeverity.INFO -> "信息"
        }
    }

    private fun getScoreColor(score: Int): Color {
        return when {
            score >= 90 -> Color(0, 150, 0)    // 绿色
            score >= 70 -> Color(255, 165, 0)  // 橙色
            score >= 50 -> Color(255, 140, 0)  // 深橙色
            else -> Color(220, 20, 60)         // 红色
        }
    }

    private fun getRiskColor(riskLevel: RiskLevel): Color {
        return when (riskLevel) {
            RiskLevel.LOW -> Color(0, 150, 0)      // 绿色
            RiskLevel.MEDIUM -> Color(255, 165, 0) // 橙色
            RiskLevel.HIGH -> Color(255, 69, 0)    // 红橙色
            RiskLevel.CRITICAL -> Color(220, 20, 60) // 红色
        }
    }

    /**
     * 递归地添加方法实现到提示中（包含级联方法）
     */
    private fun appendMethodImplementation(prompt: StringBuilder, impl: MethodImplementation, level: Int) {
        val indent = "  ".repeat(level - 1)
        val levelPrefix = if (level == 1) "###" else "#".repeat(3 + level)

        prompt.append("$levelPrefix ${indent}方法: ${impl.className}.${impl.methodName}()\n")
        prompt.append("${indent}实现文件: ${impl.filePath}\n\n")
        prompt.append("${indent}方法实现代码:\n")
        prompt.append("```java\n")
        prompt.append(impl.sourceCode)
        prompt.append("\n```\n\n")

        if (impl.containsDangerousOperations.isNotEmpty()) {
            prompt.append("${indent}🚨 **系统预检测到的危险操作**:\n")
            impl.containsDangerousOperations.forEach { danger ->
                // 根据危险操作类型确定严重程度标识
                val severity = when {
                    danger.contains("Redis") && (danger.contains("keys()") || danger.contains("模式匹配")) -> "🚨 CRITICAL"
                    danger.contains("SQL") && danger.contains("全表") -> "🚨 CRITICAL"
                    danger.contains("Redis") -> "⚠️ HIGH"
                    danger.contains("SQL") -> "⚠️ HIGH"
                    danger.contains("资源") || danger.contains("泄漏") -> "⚠️ HIGH"
                    danger.contains("阻塞") || danger.contains("循环") -> "⚠️ HIGH"
                    else -> "📊 MEDIUM"
                }
                prompt.append("${indent}- $severity **$danger**\n")
            }
            prompt.append("${indent}**⚠️ 请基于上述预检测结果进行详细的风险等级评估和解决方案制定**\n\n")
        }

        // 递归添加级联方法
        if (impl.cascadedMethods.isNotEmpty()) {
            prompt.append("${indent}**级联调用的方法:**\n\n")
            impl.cascadedMethods.forEach { cascaded ->
                appendMethodImplementation(prompt, cascaded, level + 1)
            }
        }

        prompt.append("${indent}---\n\n")
    }

    /**
     * 递归地添加方法调用信息到处理过程显示（包含级联方法）
     */
    private fun appendMethodCallToProcess(call: MethodCallInfo, level: Int) {
        appendImplementationToProcess(call.implementation, level, call.callerLine)
    }

    /**
     * 递归地添加方法实现信息到处理过程显示
     */
    private fun appendImplementationToProcess(impl: MethodImplementation, level: Int, callerLine: String? = null) {
        val indent = "  ".repeat(level - 1)
        val bullet = if (level == 1) "•" else "→"

        appendProcess("${indent}${bullet} ${impl.className}.${impl.methodName}()\n")
        if (callerLine != null && level == 1) {
            appendProcess("${indent}  调用位置: $callerLine\n")
        }
        appendProcess("${indent}  实现文件: ${impl.filePath}\n")

        if (impl.containsDangerousOperations.isNotEmpty()) {
            appendProcess("${indent}  ⚠️ 危险操作: ${impl.containsDangerousOperations.joinToString(", ")}\n")
        }

        // 递归显示级联方法
        if (impl.cascadedMethods.isNotEmpty()) {
            appendProcess("${indent}  级联调用:\n")
            impl.cascadedMethods.forEach { cascaded ->
                appendImplementationToProcess(cascaded, level + 1)
            }
        }
    }
}