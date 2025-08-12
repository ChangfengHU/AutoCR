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
 * 代码评估过程展示对话框（迁移版，精简依赖）
 */
class CodeReviewProcessDialog(
    private val project: Project?,
    private val changes: List<CodeChange>,
    private val commitMessage: String
) : DialogWrapper(project) {

    private lateinit var processArea: JBTextArea
    private lateinit var progressBar: JProgressBar
    private lateinit var statusLabel: JLabel
    private lateinit var scoreLabel: JLabel
    private lateinit var riskLabel: JLabel
    private lateinit var startAnalysisButton: JButton
    private lateinit var commitButton: JButton

    private var reviewResult: CodeReviewResult? = null
    private var canCommit = false
    private var codeReviewService: CodeReviewService? = null

    init {
        title = "Code Review"
        setSize(900, 640)
        init()
        SwingUtilities.invokeLater {
            appendProcess("准备开始AI代码评估\n")
        }
    }

    fun setCodeReviewService(service: CodeReviewService) { this.codeReviewService = service }

    override fun createCenterPanel(): JComponent {
        val main = JPanel(BorderLayout())

        // 顶部结果条
        val header = JPanel(FlowLayout(FlowLayout.LEFT))
        header.add(JLabel("总体评分:"))
        scoreLabel = JLabel("--/100").apply { font = font.deriveFont(Font.BOLD, 16f) }
        header.add(scoreLabel)
        header.add(Box.createHorizontalStrut(24))
        header.add(JLabel("风险等级:"))
        riskLabel = JLabel("--").apply { font = font.deriveFont(Font.BOLD, 16f) }
        header.add(riskLabel)
        main.add(header, BorderLayout.NORTH)

        // 中部过程
        processArea = JBTextArea().apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            text = "等待开始分析...\n"
        }
        main.add(JBScrollPane(processArea), BorderLayout.CENTER)

        // 底部状态与按钮
        val bottom = JPanel(BorderLayout())
        val statusPanel = JPanel(BorderLayout())
        statusLabel = JLabel("准备开始分析...")
        statusPanel.add(statusLabel, BorderLayout.NORTH)
        progressBar = JProgressBar(0, 100).apply { isStringPainted = true; string = "0%" }
        statusPanel.add(progressBar, BorderLayout.CENTER)
        bottom.add(statusPanel, BorderLayout.CENTER)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT))
        startAnalysisButton = JButton("🚀 开始AI分析")
        commitButton = JButton("提交代码").apply { isEnabled = false }
        val cancelBtn = JButton("取消").apply { addActionListener { close(CANCEL_EXIT_CODE) } }
        buttons.add(startAnalysisButton)
        buttons.add(commitButton)
        buttons.add(cancelBtn)
        bottom.add(buttons, BorderLayout.EAST)

        main.add(bottom, BorderLayout.SOUTH)

        // 绑定事件
        startAnalysisButton.addActionListener {
            startAnalysisButton.isEnabled = false
            appendProcess("开始AI分析...\n")
            performAIAnalysis()
        }

        commitButton.addActionListener {
            com.intellij.openapi.ui.Messages.showInfoMessage(project, "演示入口：此处应执行 git commit。", "提交")
        }

        return main
    }

    fun setCommitEnabled(enabled: Boolean) { SwingUtilities.invokeLater { commitButton.isEnabled = enabled } }

    fun startReview(service: CodeReviewService, onComplete: (Boolean, CodeReviewResult?) -> Unit) {
        setCodeReviewService(service)
        // 仅弹窗等待用户点击“开始AI分析”
        show()
    }

    private fun updateProgress(p: Int, msg: String) {
        SwingUtilities.invokeLater {
            progressBar.value = p
            progressBar.string = "$p%"
            statusLabel.text = msg
        }
    }

    private fun appendProcess(text: String) {
        SwingUtilities.invokeLater {
            processArea.append(text)
            processArea.caretPosition = processArea.document.length
        }
    }

    private fun updateResultDisplay(result: CodeReviewResult) {
        scoreLabel.text = "${result.overallScore}/100"
        riskLabel.text = result.riskLevel.name
    }

    private fun performAIAnalysis() {
        val service = codeReviewService ?: run {
            appendProcess("❌ 错误: AI服务未初始化\n")
            return
        }
        updateProgress(30, "准备AI分析...")
        Thread {
            try {
                updateProgress(60, "AI分析中...")
                val result = kotlinx.coroutines.runBlocking { service.reviewCode(changes, commitMessage) }
                updateProgress(90, "解析结果...")
                reviewResult = result
                updateResultDisplay(result)
                val settings = com.vyibc.autocrplugin.settings.CodeReviewSettings.getInstance()
                canCommit = result.overallScore >= settings.minimumScore &&
                        (!settings.blockHighRiskCommits || (result.riskLevel != RiskLevel.CRITICAL && result.riskLevel != RiskLevel.HIGH)) &&
                        result.issues.none { it.severity == IssueSeverity.CRITICAL }
                setCommitEnabled(canCommit)
                updateProgress(100, if (canCommit) "评估完成，可提交" else "评估完成，不可提交")
                appendProcess("评估完成：分数=${result.overallScore} 风险=${result.riskLevel} 问题=${result.issues.size}\n")
            } catch (e: Exception) {
                updateProgress(0, "评估失败")
                appendProcess("❌ 评估失败: ${e.message}\n")
            } finally {
                SwingUtilities.invokeLater { startAnalysisButton.isEnabled = true }
            }
        }.start()
    }
}
