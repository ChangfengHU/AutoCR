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
 * 代码评估结果对话框
 */
class CodeReviewDialog(
    project: Project?,
    private val reviewResult: CodeReviewResult
) : DialogWrapper(project) {

    private lateinit var scoreLabel: JLabel
    private lateinit var riskLabel: JLabel
    private lateinit var summaryArea: JBTextArea
    private lateinit var issuesTable: JBTable
    private lateinit var suggestionsArea: JBTextArea

    init {
        title = "代码评估结果"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(800, 600)

        // 创建顶部信息面板
        val topPanel = createTopPanel()
        mainPanel.add(topPanel, BorderLayout.NORTH)

        // 创建中心内容面板
        val centerPanel = createContentPanel()
        mainPanel.add(centerPanel, BorderLayout.CENTER)

        return mainPanel
    }

    /**
     * 创建顶部信息面板
     */
    private fun createTopPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("评估概览")
        
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)

        // 评分
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        panel.add(JLabel("总体评分:"), gbc)

        gbc.gridx = 1
        scoreLabel = JLabel("${reviewResult.overallScore}/100")
        scoreLabel.foreground = getScoreColor(reviewResult.overallScore)
        scoreLabel.font = scoreLabel.font.deriveFont(Font.BOLD, 16f)
        panel.add(scoreLabel, gbc)

        // 风险等级
        gbc.gridx = 2
        gbc.insets = Insets(5, 20, 5, 5)
        panel.add(JLabel("风险等级:"), gbc)

        gbc.gridx = 3
        riskLabel = JLabel(getRiskLevelText(reviewResult.riskLevel))
        riskLabel.foreground = getRiskColor(reviewResult.riskLevel)
        riskLabel.font = riskLabel.font.deriveFont(Font.BOLD, 16f)
        panel.add(riskLabel, gbc)

        return panel
    }

    /**
     * 创建中心内容面板
     */
    private fun createContentPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 创建选项卡面板
        val tabbedPane = JTabbedPane()
        
        // 总结选项卡
        val summaryPanel = createSummaryPanel()
        tabbedPane.addTab("总结", summaryPanel)
        
        // 问题列表选项卡
        val issuesPanel = createIssuesPanel()
        tabbedPane.addTab("问题列表 (${reviewResult.issues.size})", issuesPanel)
        
        // 建议选项卡
        val suggestionsPanel = createSuggestionsPanel()
        tabbedPane.addTab("改进建议", suggestionsPanel)
        
        panel.add(tabbedPane, BorderLayout.CENTER)
        
        return panel
    }

    /**
     * 创建总结面板
     */
    private fun createSummaryPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        summaryArea = JBTextArea(reviewResult.summary)
        summaryArea.isEditable = false
        summaryArea.lineWrap = true
        summaryArea.wrapStyleWord = true
        summaryArea.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
        
        val scrollPane = JBScrollPane(summaryArea)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }

    /**
     * 创建问题列表面板
     */
    private fun createIssuesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        // 创建表格模型
        val columnNames = arrayOf("文件", "行号", "严重程度", "分类", "问题描述", "建议")
        val tableModel = DefaultTableModel(columnNames, 0)
        
        // 添加数据
        reviewResult.issues.forEach { issue ->
            tableModel.addRow(arrayOf(
                issue.filePath,
                issue.lineNumber?.toString() ?: "-",
                getSeverityText(issue.severity),
                getCategoryText(issue.category),
                issue.message,
                issue.suggestion ?: "-"
            ))
        }
        
        issuesTable = JBTable(tableModel)
        issuesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        
        // 设置列宽
        val columnModel = issuesTable.columnModel
        columnModel.getColumn(0).preferredWidth = 150 // 文件
        columnModel.getColumn(1).preferredWidth = 50  // 行号
        columnModel.getColumn(2).preferredWidth = 80  // 严重程度
        columnModel.getColumn(3).preferredWidth = 100 // 分类
        columnModel.getColumn(4).preferredWidth = 200 // 问题描述
        columnModel.getColumn(5).preferredWidth = 200 // 建议
        
        // 设置行高
        issuesTable.rowHeight = 25
        
        val scrollPane = JBScrollPane(issuesTable)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }

    /**
     * 创建建议面板
     */
    private fun createSuggestionsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        val suggestionsText = reviewResult.suggestions.joinToString("\n\n") { "• $it" }
        
        suggestionsArea = JBTextArea(suggestionsText)
        suggestionsArea.isEditable = false
        suggestionsArea.lineWrap = true
        suggestionsArea.wrapStyleWord = true
        suggestionsArea.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
        
        val scrollPane = JBScrollPane(suggestionsArea)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }

    /**
     * 获取评分颜色
     */
    private fun getScoreColor(score: Int): Color {
        return when {
            score >= 90 -> Color(0, 150, 0)    // 绿色
            score >= 70 -> Color(255, 165, 0)  // 橙色
            score >= 50 -> Color(255, 140, 0)  // 深橙色
            else -> Color(220, 20, 60)         // 红色
        }
    }

    /**
     * 获取风险等级颜色
     */
    private fun getRiskColor(riskLevel: RiskLevel): Color {
        return when (riskLevel) {
            RiskLevel.LOW -> Color(0, 150, 0)      // 绿色
            RiskLevel.MEDIUM -> Color(255, 165, 0) // 橙色
            RiskLevel.HIGH -> Color(255, 69, 0)    // 红橙色
            RiskLevel.CRITICAL -> Color(220, 20, 60) // 红色
        }
    }

    /**
     * 获取风险等级文本
     */
    private fun getRiskLevelText(riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.LOW -> "低风险"
            RiskLevel.MEDIUM -> "中等风险"
            RiskLevel.HIGH -> "高风险"
            RiskLevel.CRITICAL -> "严重风险"
        }
    }

    /**
     * 获取严重程度文本
     */
    private fun getSeverityText(severity: IssueSeverity): String {
        return when (severity) {
            IssueSeverity.CRITICAL -> "严重"
            IssueSeverity.MAJOR -> "重要"
            IssueSeverity.MINOR -> "轻微"
            IssueSeverity.INFO -> "信息"
        }
    }

    /**
     * 获取分类文本
     */
    private fun getCategoryText(category: IssueCategory): String {
        return when (category) {
            IssueCategory.CODE_STYLE -> "代码风格"
            IssueCategory.PERFORMANCE -> "性能"
            IssueCategory.SECURITY -> "安全"
            IssueCategory.BUG_RISK -> "Bug风险"
            IssueCategory.MAINTAINABILITY -> "可维护性"
            IssueCategory.DOCUMENTATION -> "文档"
            IssueCategory.BEST_PRACTICE -> "最佳实践"
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}
