package com.vyibc.autocr.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.vyibc.autocr.ai.AIResponse
import com.vyibc.autocr.preprocessor.DualFlowAnalysisReport
import com.vyibc.autocr.preprocessor.FileChangeAnalysis
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.text.SimpleDateFormat
import java.util.*

/**
 * 代码评审报告窗口
 * 显示AI分析结果和详细的评审报告
 */
class CodeReviewReportWindow {
    private val logger = LoggerFactory.getLogger(CodeReviewReportWindow::class.java)
    
    // 主面板
    private lateinit var mainPanel: JPanel
    private lateinit var tabbedPane: JBTabbedPane
    
    // 概览标签页
    private lateinit var overviewPanel: JPanel
    private lateinit var summaryLabel: JBLabel
    private lateinit var priorityStatsPanel: JPanel
    private lateinit var riskFactorsPanel: JPanel
    
    // 文件分析标签页
    private lateinit var fileAnalysisPanel: JPanel
    private lateinit var fileAnalysisTable: JBTable
    private lateinit var fileAnalysisModel: FileAnalysisTableModel
    
    // AI评审标签页
    private lateinit var aiReviewPanel: JPanel
    private lateinit var aiReviewTextArea: JTextArea
    
    // 详细报告标签页
    private lateinit var detailReportPanel: JPanel
    private lateinit var detailReportTextArea: JTextArea
    
    // 数据
    private var analysisReport: DualFlowAnalysisReport? = null
    private var aiResponses: List<AIResponse> = emptyList()
    
    /**
     * 创建主面板
     */
    fun createContent(): JComponent {
        mainPanel = JPanel(BorderLayout())
        
        // 创建标签页
        tabbedPane = JBTabbedPane()
        
        // 概览标签页
        overviewPanel = createOverviewPanel()
        tabbedPane.addTab("评审概览", overviewPanel)
        
        // 文件分析标签页
        fileAnalysisPanel = createFileAnalysisPanel()
        tabbedPane.addTab("文件分析", fileAnalysisPanel)
        
        // AI评审标签页
        aiReviewPanel = createAIReviewPanel()
        tabbedPane.addTab("AI评审", aiReviewPanel)
        
        // 详细报告标签页
        detailReportPanel = createDetailReportPanel()
        tabbedPane.addTab("详细报告", detailReportPanel)
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
        
        return mainPanel
    }
    
    /**
     * 创建概览面板
     */
    private fun createOverviewPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 顶部摘要
        summaryLabel = JBLabel("等待评审报告...").apply {
            font = font.deriveFont(Font.BOLD, 16f)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        panel.add(summaryLabel, BorderLayout.NORTH)
        
        // 中央区域
        val centerPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        
        // 优先级统计面板
        priorityStatsPanel = createPriorityStatsPanel()
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.5
        gbc.weighty = 0.5
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = java.awt.Insets(5, 5, 5, 5)
        centerPanel.add(priorityStatsPanel, gbc)
        
        // 风险因素面板
        riskFactorsPanel = createRiskFactorsPanel()
        gbc.gridx = 1
        gbc.gridy = 0
        centerPanel.add(riskFactorsPanel, gbc)
        
        // 评审建议面板
        val recommendationsPanel = createRecommendationsPanel()
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 2
        centerPanel.add(recommendationsPanel, gbc)
        
        panel.add(centerPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 创建优先级统计面板
     */
    private fun createPriorityStatsPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("优先级分布")
        }
        
        val highPriorityLabel = JBLabel("高优先级: 0 个文件")
        val mediumPriorityLabel = JBLabel("中优先级: 0 个文件")
        val lowPriorityLabel = JBLabel("低优先级: 0 个文件")
        
        panel.add(highPriorityLabel)
        panel.add(mediumPriorityLabel)
        panel.add(lowPriorityLabel)
        
        return panel
    }
    
    /**
     * 创建风险因素面板
     */
    private fun createRiskFactorsPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("主要风险因素")
        }
        
        val noRisksLabel = JBLabel("暂无风险因素数据")
        panel.add(noRisksLabel)
        
        return panel
    }
    
    /**
     * 创建评审建议面板
     */
    private fun createRecommendationsPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("评审建议")
        }
        
        val noRecommendationsLabel = JBLabel("等待AI分析生成评审建议...")
        panel.add(noRecommendationsLabel)
        
        return panel
    }
    
    /**
     * 创建文件分析面板
     */
    private fun createFileAnalysisPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 创建表格
        fileAnalysisModel = FileAnalysisTableModel()
        fileAnalysisTable = JBTable(fileAnalysisModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
            
            // 设置列宽
            columnModel.getColumn(0).preferredWidth = 300 // 文件路径
            columnModel.getColumn(1).preferredWidth = 80  // 优先级
            columnModel.getColumn(2).preferredWidth = 80  // 意图权重
            columnModel.getColumn(3).preferredWidth = 80  // 风险权重
            columnModel.getColumn(4).preferredWidth = 80  // 综合权重
            columnModel.getColumn(5).preferredWidth = 100 // 评审时间
            
            // 设置自定义渲染器
            setDefaultRenderer(String::class.java, FileAnalysisTableCellRenderer())
        }
        
        val scrollPane = JBScrollPane(fileAnalysisTable)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 添加详情面板
        val detailsPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("文件详情")
            preferredSize = Dimension(0, 150)
        }
        
        val detailsTextArea = JTextArea("选择文件以查看详细分析...").apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }
        
        detailsPanel.add(JBScrollPane(detailsTextArea), BorderLayout.CENTER)
        panel.add(detailsPanel, BorderLayout.SOUTH)
        
        // 添加选择监听器
        fileAnalysisTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = fileAnalysisTable.selectedRow
                if (selectedRow >= 0) {
                    val fileAnalysis = fileAnalysisModel.getFileAnalysis(selectedRow)
                    updateFileDetails(detailsTextArea, fileAnalysis)
                }
            }
        }
        
        return panel
    }
    
    /**
     * 创建AI评审面板
     */
    private fun createAIReviewPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 顶部控制面板
        val controlPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        }
        
        val refreshButton = JButton("重新评审").apply {
            addActionListener { refreshAIReview() }
        }
        
        val exportButton = JButton("导出报告").apply {
            addActionListener { exportReport() }
        }
        
        controlPanel.add(refreshButton)
        controlPanel.add(Box.createHorizontalStrut(10))
        controlPanel.add(exportButton)
        controlPanel.add(Box.createHorizontalGlue())
        
        panel.add(controlPanel, BorderLayout.NORTH)
        
        // AI评审结果显示区域
        aiReviewTextArea = JTextArea("正在生成AI评审报告...").apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
        
        val scrollPane = JBScrollPane(aiReviewTextArea)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 创建详细报告面板
     */
    private fun createDetailReportPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        detailReportTextArea = JTextArea("详细评审报告将在AI分析完成后显示...").apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        }
        
        val scrollPane = JBScrollPane(detailReportTextArea)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 更新分析报告
     */
    fun updateAnalysisReport(report: DualFlowAnalysisReport) {
        this.analysisReport = report
        
        // 更新概览
        updateOverview(report)
        
        // 更新文件分析表格
        updateFileAnalysisTable(report)
        
        logger.info("Updated analysis report with {} files", report.totalFiles)
    }
    
    /**
     * 更新AI评审结果
     */
    fun updateAIReview(responses: List<AIResponse>) {
        this.aiResponses = responses
        
        val combinedReview = responses.joinToString("\\n\\n" + "=".repeat(80) + "\\n\\n") { response ->
            """
            ## ${response.provider} - ${response.model}
            **响应时间**: ${response.responseTime}ms
            **置信度**: ${String.format("%.2f", response.confidence * 100)}%
            
            ${response.content}
            """.trimIndent()
        }
        
        aiReviewTextArea.text = combinedReview
        
        // 更新详细报告
        updateDetailReport()
        
        logger.info("Updated AI review with {} responses", responses.size)
    }
    
    /**
     * 更新概览信息
     */
    private fun updateOverview(report: DualFlowAnalysisReport) {
        // 更新摘要
        val summaryText = """
            <html>
            <body>
            <h2>代码评审摘要</h2>
            <p><b>总文件数:</b> ${report.totalFiles}</p>
            <p><b>预估评审时间:</b> ${report.estimatedTotalReviewTime} 分钟</p>
            <p><b>平均权重:</b> ${String.format("%.2f", report.averageCombinedWeight)}</p>
            <p><b>生成时间:</b> ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(report.analysisTimestamp))}</p>
            </body>
            </html>
        """.trimIndent()
        
        summaryLabel.text = summaryText
        
        // 更新优先级统计
        updatePriorityStats(report)
        
        // 更新风险因素
        updateRiskFactors(report)
    }
    
    /**
     * 更新优先级统计
     */
    private fun updatePriorityStats(report: DualFlowAnalysisReport) {
        priorityStatsPanel.removeAll()
        
        val highCount = report.highPriorityFiles.size
        val mediumCount = report.mediumPriorityFiles.size
        val lowCount = report.lowPriorityFiles.size
        
        val highLabel = JBLabel("高优先级: $highCount 个文件").apply {
            foreground = Color.RED
        }
        val mediumLabel = JBLabel("中优先级: $mediumCount 个文件").apply {
            foreground = Color.ORANGE
        }
        val lowLabel = JBLabel("低优先级: $lowCount 个文件").apply {
            foreground = Color.GREEN
        }
        
        priorityStatsPanel.add(highLabel)
        priorityStatsPanel.add(mediumLabel)
        priorityStatsPanel.add(lowLabel)
        
        priorityStatsPanel.revalidate()
        priorityStatsPanel.repaint()
    }
    
    /**
     * 更新风险因素
     */
    private fun updateRiskFactors(report: DualFlowAnalysisReport) {
        riskFactorsPanel.removeAll()
        
        report.topRiskFactors.take(5).forEach { (factor, count) ->
            val label = JBLabel("$factor: $count 次")
            riskFactorsPanel.add(label)
        }
        
        if (report.topRiskFactors.isEmpty()) {
            riskFactorsPanel.add(JBLabel("未发现明显风险因素"))
        }
        
        riskFactorsPanel.revalidate()
        riskFactorsPanel.repaint()
    }
    
    /**
     * 更新文件分析表格
     */
    private fun updateFileAnalysisTable(report: DualFlowAnalysisReport) {
        val allFiles = report.highPriorityFiles + report.mediumPriorityFiles + report.lowPriorityFiles
        fileAnalysisModel.setFileAnalyses(allFiles)
    }
    
    /**
     * 更新文件详情
     */
    private fun updateFileDetails(textArea: JTextArea, fileAnalysis: FileChangeAnalysis) {
        val details = """
            文件路径: ${fileAnalysis.fileChange.filePath}
            变更类型: ${fileAnalysis.fileChange.changeType}
            优先级: ${fileAnalysis.priorityLevel}
            
            权重分析:
            - 意图权重: ${String.format("%.3f", fileAnalysis.intentWeight)}
            - 风险权重: ${String.format("%.3f", fileAnalysis.riskWeight)}
            - 综合权重: ${String.format("%.3f", fileAnalysis.combinedWeight)}
            
            评审信息:
            - 预估时间: ${fileAnalysis.recommendedReviewTime} 分钟
            - 建议评审员: ${fileAnalysis.suggestedReviewers.joinToString(", ")}
            
            方法分析:
            ${fileAnalysis.methodAnalyses.joinToString("\\n") { 
                "- ${it.method.methodName}: 权重=${String.format("%.3f", it.combinedWeight)}" 
            }}
        """.trimIndent()
        
        textArea.text = details
    }
    
    /**
     * 更新详细报告
     */
    private fun updateDetailReport() {
        val report = analysisReport ?: return
        
        val detailReport = StringBuilder()
        
        detailReport.appendLine("# AutoCR 代码评审详细报告")
        detailReport.appendLine()
        detailReport.appendLine("生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}")
        detailReport.appendLine()
        
        // 执行摘要
        detailReport.appendLine("## 执行摘要")
        detailReport.appendLine()
        detailReport.appendLine("- 总文件数: ${report.totalFiles}")
        detailReport.appendLine("- 高优先级文件: ${report.highPriorityFiles.size}")
        detailReport.appendLine("- 中优先级文件: ${report.mediumPriorityFiles.size}")
        detailReport.appendLine("- 低优先级文件: ${report.lowPriorityFiles.size}")
        detailReport.appendLine("- 预估总评审时间: ${report.estimatedTotalReviewTime} 分钟")
        detailReport.appendLine("- 平均综合权重: ${String.format("%.3f", report.averageCombinedWeight)}")
        detailReport.appendLine()
        
        // 主要风险因素
        detailReport.appendLine("## 主要风险因素")
        detailReport.appendLine()
        report.topRiskFactors.take(10).forEach { (factor, count) ->
            detailReport.appendLine("- $factor: $count 次")
        }
        detailReport.appendLine()
        
        // 建议评审顺序
        detailReport.appendLine("## 建议评审顺序")
        detailReport.appendLine()
        report.recommendedReviewOrder.take(20).forEachIndexed { index, filePath ->
            detailReport.appendLine("${index + 1}. $filePath")
        }
        detailReport.appendLine()
        
        // AI评审结果
        if (aiResponses.isNotEmpty()) {
            detailReport.appendLine("## AI评审结果")
            detailReport.appendLine()
            aiResponses.forEach { response ->
                detailReport.appendLine("### ${response.provider} - ${response.model}")
                detailReport.appendLine("响应时间: ${response.responseTime}ms")
                detailReport.appendLine("置信度: ${String.format("%.2f", response.confidence * 100)}%")
                detailReport.appendLine()
                detailReport.appendLine(response.content)
                detailReport.appendLine()
            }
        }
        
        detailReportTextArea.text = detailReport.toString()
    }
    
    /**
     * 刷新AI评审
     */
    private fun refreshAIReview() {
        aiReviewTextArea.text = "正在重新生成AI评审报告..."
        // 这里应该触发AI重新分析
        logger.info("Refreshing AI review")
    }
    
    /**
     * 导出报告
     */
    private fun exportReport() {
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "导出评审报告"
        fileChooser.selectedFile = java.io.File("CodeReview_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}.md")
        
        if (fileChooser.showSaveDialog(mainPanel) == JFileChooser.APPROVE_OPTION) {
            try {
                val file = fileChooser.selectedFile
                file.writeText(detailReportTextArea.text)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "报告已成功导出到: ${file.absolutePath}",
                    "导出成功",
                    JOptionPane.INFORMATION_MESSAGE
                )
                logger.info("Report exported to: {}", file.absolutePath)
            } catch (e: Exception) {
                logger.error("Failed to export report", e)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "导出失败: ${e.message}",
                    "导出错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
}

/**
 * 文件分析表格模型
 */
class FileAnalysisTableModel : AbstractTableModel() {
    private var fileAnalyses: List<FileChangeAnalysis> = emptyList()
    
    private val columnNames = arrayOf("文件路径", "优先级", "意图权重", "风险权重", "综合权重", "评审时间(分钟)")
    
    fun setFileAnalyses(analyses: List<FileChangeAnalysis>) {
        this.fileAnalyses = analyses
        fireTableDataChanged()
    }
    
    fun getFileAnalysis(row: Int): FileChangeAnalysis = fileAnalyses[row]
    
    override fun getRowCount(): Int = fileAnalyses.size
    
    override fun getColumnCount(): Int = columnNames.size
    
    override fun getColumnName(column: Int): String = columnNames[column]
    
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val analysis = fileAnalyses[rowIndex]
        return when (columnIndex) {
            0 -> analysis.fileChange.filePath
            1 -> analysis.priorityLevel.name
            2 -> String.format("%.3f", analysis.intentWeight)
            3 -> String.format("%.3f", analysis.riskWeight)
            4 -> String.format("%.3f", analysis.combinedWeight)
            5 -> analysis.recommendedReviewTime.toString()
            else -> ""
        }
    }
}

/**
 * 文件分析表格单元格渲染器
 */
class FileAnalysisTableCellRenderer : DefaultTableCellRenderer() {
    
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        
        if (!isSelected && column == 1) { // 优先级列
            when (value?.toString()) {
                "HIGH" -> component.background = Color(255, 220, 220)   // 浅红色
                "MEDIUM" -> component.background = Color(255, 250, 205) // 浅黄色
                "LOW" -> component.background = Color(220, 255, 220)    // 浅绿色
                else -> component.background = Color.WHITE
            }
        } else if (!isSelected) {
            component.background = Color.WHITE
        }
        
        return component
    }
}