package com.vyibc.autocr.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.vyibc.autocr.git.GitDiffAnalyzer
import com.vyibc.autocr.model.FileChange
import com.vyibc.autocr.model.ChangeType
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import java.awt.Component
import java.awt.Color

/**
 * 分支选择对话框
 * 允许用户选择要比较的Git分支并预览变更文件
 */
class BranchSelectionDialog(
    private val project: Project
) : DialogWrapper(project) {
    
    private val logger = LoggerFactory.getLogger(BranchSelectionDialog::class.java)
    
    // UI组件
    private lateinit var sourceBranchCombo: JComboBox<String>
    private lateinit var targetBranchCombo: JComboBox<String>
    private lateinit var fileChangesTable: JBTable
    private lateinit var fileChangesModel: FileChangesTableModel
    private lateinit var summaryLabel: JBLabel
    private lateinit var refreshButton: JButton
    
    // 数据
    private var availableBranches: List<String> = emptyList()
    private var fileChanges: List<FileChange> = emptyList()
    private var selectedSourceBranch: String? = null
    private var selectedTargetBranch: String? = null
    
    init {
        title = "AutoCR - 选择要评审的分支"
        init()
        loadBranches()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(800, 600)
        }
        
        // 顶部面板 - 分支选择
        val topPanel = createBranchSelectionPanel()
        mainPanel.add(topPanel, BorderLayout.NORTH)
        
        // 中央面板 - 文件变更列表
        val centerPanel = createFileChangesPanel()
        mainPanel.add(centerPanel, BorderLayout.CENTER)
        
        // 底部面板 - 摘要信息
        val bottomPanel = createSummaryPanel()
        mainPanel.add(bottomPanel, BorderLayout.SOUTH)
        
        return mainPanel
    }
    
    /**
     * 创建分支选择面板
     */
    private fun createBranchSelectionPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("分支选择")
        }
        
        // 源分支选择
        val sourceBranchPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
        }
        sourceBranchPanel.add(JBLabel("源分支 (基准): "))
        sourceBranchCombo = JComboBox<String>().apply {
            preferredSize = Dimension(200, 25)
            addActionListener { onBranchSelectionChanged() }
        }
        sourceBranchPanel.add(sourceBranchCombo)
        sourceBranchPanel.add(Box.createHorizontalGlue())
        
        // 目标分支选择
        val targetBranchPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
        }
        targetBranchPanel.add(JBLabel("目标分支 (要评审): "))
        targetBranchCombo = JComboBox<String>().apply {
            preferredSize = Dimension(200, 25)
            addActionListener { onBranchSelectionChanged() }
        }
        targetBranchPanel.add(targetBranchCombo)
        targetBranchPanel.add(Box.createHorizontalGlue())
        
        // 刷新按钮
        refreshButton = JButton("刷新分支列表").apply {
            addActionListener { 
                loadBranches()
                refreshFileChanges()
            }
        }
        
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
        }
        buttonPanel.add(Box.createHorizontalGlue())
        buttonPanel.add(refreshButton)
        
        panel.add(sourceBranchPanel)
        panel.add(Box.createVerticalStrut(10))
        panel.add(targetBranchPanel)
        panel.add(Box.createVerticalStrut(10))
        panel.add(buttonPanel)
        
        return panel
    }
    
    /**
     * 创建文件变更面板
     */
    private fun createFileChangesPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("文件变更预览")
        }
        
        fileChangesModel = FileChangesTableModel()
        fileChangesTable = JBTable(fileChangesModel).apply {
            setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
            
            // 设置列宽
            columnModel.getColumn(0).preferredWidth = 400 // 文件路径
            columnModel.getColumn(1).preferredWidth = 80  // 变更类型
            columnModel.getColumn(2).preferredWidth = 80  // 添加行数
            columnModel.getColumn(3).preferredWidth = 80  // 删除行数
            columnModel.getColumn(4).preferredWidth = 100 // 状态
            
            // 设置自定义渲染器
            setDefaultRenderer(String::class.java, FileChangeTableCellRenderer())
        }
        
        val scrollPane = JBScrollPane(fileChangesTable).apply {
            preferredSize = Dimension(780, 400)
        }
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 添加右键菜单
        val popupMenu = createFileChangesPopupMenu()
        fileChangesTable.componentPopupMenu = popupMenu
        
        return panel
    }
    
    /**
     * 创建摘要面板
     */
    private fun createSummaryPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("变更摘要")
        }
        
        summaryLabel = JBLabel("请选择分支以查看变更摘要")
        panel.add(summaryLabel)
        
        return panel
    }
    
    /**
     * 创建文件变更右键菜单
     */
    private fun createFileChangesPopupMenu(): JPopupMenu {
        val popup = JPopupMenu()
        
        val viewDiffAction = JMenuItem("查看差异").apply {
            addActionListener { viewSelectedFileDiff() }
        }
        popup.add(viewDiffAction)
        
        val excludeAction = JMenuItem("从评审中排除").apply {
            addActionListener { excludeSelectedFiles() }
        }
        popup.add(excludeAction)
        
        popup.addSeparator()
        
        val selectAllAction = JMenuItem("全选").apply {
            addActionListener { fileChangesTable.selectAll() }
        }
        popup.add(selectAllAction)
        
        return popup
    }
    
    /**
     * 加载可用分支
     */
    private fun loadBranches() {
        try {
            logger.info("Loading available branches for project: {}", project.name)
            
            // 模拟获取Git分支（实际应该调用Git命令）
            availableBranches = listOf(
                "main",
                "develop", 
                "feature/user-management",
                "feature/payment-integration",
                "hotfix/security-patch",
                "release/v2.1.0"
            )
            
            // 更新下拉框
            updateBranchComboBoxes()
            
            logger.info("Loaded {} branches", availableBranches.size)
        } catch (e: Exception) {
            logger.error("Failed to load branches", e)
            showErrorMessage("加载分支失败: ${e.message}")
        }
    }
    
    /**
     * 更新分支下拉框
     */
    private fun updateBranchComboBoxes() {
        sourceBranchCombo.removeAllItems()
        targetBranchCombo.removeAllItems()
        
        availableBranches.forEach { branch ->
            sourceBranchCombo.addItem(branch)
            targetBranchCombo.addItem(branch)
        }
        
        // 设置默认选择
        if (availableBranches.isNotEmpty()) {
            sourceBranchCombo.selectedItem = "main"
            if (availableBranches.size > 1) {
                targetBranchCombo.selectedItem = availableBranches[1]
            }
        }
    }
    
    /**
     * 分支选择变更处理
     */
    private fun onBranchSelectionChanged() {
        selectedSourceBranch = sourceBranchCombo.selectedItem as? String
        selectedTargetBranch = targetBranchCombo.selectedItem as? String
        
        if (selectedSourceBranch != null && selectedTargetBranch != null && 
            selectedSourceBranch != selectedTargetBranch) {
            refreshFileChanges()
        }
    }
    
    /**
     * 刷新文件变更列表
     */
    private fun refreshFileChanges() {
        val sourceBranch = selectedSourceBranch
        val targetBranch = selectedTargetBranch
        
        if (sourceBranch == null || targetBranch == null || sourceBranch == targetBranch) {
            fileChanges = emptyList()
            fileChangesModel.setFileChanges(fileChanges)
            updateSummary()
            return
        }
        
        try {
            logger.info("Analyzing changes between {} and {}", sourceBranch, targetBranch)
            
            // 模拟文件变更分析（实际应该调用GitDiffAnalyzer）
            fileChanges = generateMockFileChanges()
            
            fileChangesModel.setFileChanges(fileChanges)
            updateSummary()
            
            logger.info("Found {} file changes", fileChanges.size)
        } catch (e: Exception) {
            logger.error("Failed to analyze file changes", e)
            showErrorMessage("分析文件变更失败: ${e.message}")
        }
    }
    
    /**
     * 生成模拟文件变更数据
     */
    private fun generateMockFileChanges(): List<FileChange> {
        return listOf(
            com.vyibc.autocr.model.FileChange(
                filePath = "src/main/java/com/example/service/UserService.java",
                changeType = com.vyibc.autocr.model.ChangeType.MODIFIED,
                addedMethods = emptyList(),
                modifiedMethods = emptyList(),
                deletedMethods = emptyList(),
                hunks = emptyList()
            ),
            com.vyibc.autocr.model.FileChange(
                filePath = "src/main/java/com/example/controller/PaymentController.java",
                changeType = com.vyibc.autocr.model.ChangeType.ADDED,
                addedMethods = emptyList(),
                modifiedMethods = emptyList(),
                deletedMethods = emptyList(),
                hunks = emptyList()
            ),
            com.vyibc.autocr.model.FileChange(
                filePath = "src/main/java/com/example/config/SecurityConfig.java",
                changeType = com.vyibc.autocr.model.ChangeType.MODIFIED,
                addedMethods = emptyList(),
                modifiedMethods = emptyList(),
                deletedMethods = emptyList(),
                hunks = emptyList()
            ),
            com.vyibc.autocr.model.FileChange(
                filePath = "src/main/java/com/example/util/LegacyUtil.java",
                changeType = com.vyibc.autocr.model.ChangeType.DELETED,
                addedMethods = emptyList(),
                modifiedMethods = emptyList(),
                deletedMethods = emptyList(),
                hunks = emptyList()
            )
        )
    }
    
    /**
     * 更新摘要信息
     */
    private fun updateSummary() {
        if (fileChanges.isEmpty()) {
            summaryLabel.text = "没有检测到文件变更"
            return
        }
        
        val addedCount = fileChanges.count { it.changeType == com.vyibc.autocr.model.ChangeType.ADDED }
        val modifiedCount = fileChanges.count { it.changeType == com.vyibc.autocr.model.ChangeType.MODIFIED }
        val deletedCount = fileChanges.count { it.changeType == com.vyibc.autocr.model.ChangeType.DELETED }
        
        val summaryText = "共 ${fileChanges.size} 个文件变更: " +
                         "${addedCount} 个新增, ${modifiedCount} 个修改, ${deletedCount} 个删除"
        
        summaryLabel.text = summaryText
    }
    
    /**
     * 查看选中文件的差异
     */
    private fun viewSelectedFileDiff() {
        val selectedRows = fileChangesTable.selectedRows
        if (selectedRows.isEmpty()) {
            showInfoMessage("请先选择要查看的文件")
            return
        }
        
        // 打开差异查看器（简化实现）
        for (row in selectedRows) {
            val fileChange = fileChanges[row]
            logger.info("Viewing diff for file: {}", fileChange.filePath)
            // 这里应该打开IntelliJ的差异查看器
        }
    }
    
    /**
     * 排除选中的文件
     */
    private fun excludeSelectedFiles() {
        val selectedRows = fileChangesTable.selectedRows
        if (selectedRows.isEmpty()) {
            showInfoMessage("请先选择要排除的文件")
            return
        }
        
        val selectedFiles = selectedRows.map { fileChanges[it].filePath }
        val message = "确定要从评审中排除这些文件吗？\\n${selectedFiles.joinToString("\\n")}"
        
        val result = JOptionPane.showConfirmDialog(
            this.contentPane,
            message,
            "确认排除",
            JOptionPane.YES_NO_OPTION
        )
        
        if (result == JOptionPane.YES_OPTION) {
            // 实现文件排除逻辑
            logger.info("Excluding {} files from review", selectedFiles.size)
        }
    }
    
    /**
     * 验证用户输入
     */
    override fun doValidate(): ValidationInfo? {
        if (selectedSourceBranch.isNullOrEmpty()) {
            return ValidationInfo("请选择源分支", sourceBranchCombo)
        }
        
        if (selectedTargetBranch.isNullOrEmpty()) {
            return ValidationInfo("请选择目标分支", targetBranchCombo)
        }
        
        if (selectedSourceBranch == selectedTargetBranch) {
            return ValidationInfo("源分支和目标分支不能相同", targetBranchCombo)
        }
        
        if (fileChanges.isEmpty()) {
            return ValidationInfo("没有检测到文件变更，无法进行代码评审")
        }
        
        return null
    }
    
    /**
     * 获取选择的源分支
     */
    fun getSelectedSourceBranch(): String? = selectedSourceBranch
    
    /**
     * 获取选择的目标分支
     */
    fun getSelectedTargetBranch(): String? = selectedTargetBranch
    
    /**
     * 获取文件变更列表
     */
    fun getFileChanges(): List<com.vyibc.autocr.model.FileChange> = fileChanges
    
    /**
     * 显示错误消息
     */
    private fun showErrorMessage(message: String) {
        JOptionPane.showMessageDialog(
            this.contentPane,
            message,
            "错误",
            JOptionPane.ERROR_MESSAGE
        )
    }
    
    /**
     * 显示信息消息
     */
    private fun showInfoMessage(message: String) {
        JOptionPane.showMessageDialog(
            this.contentPane,
            message,
            "信息",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
}

/**
 * 文件变更表格模型
 */
class FileChangesTableModel : AbstractTableModel() {
    private var fileChanges: List<com.vyibc.autocr.model.FileChange> = emptyList()
    
    private val columnNames = arrayOf("文件路径", "变更类型", "添加行数", "删除行数", "状态")
    
    fun setFileChanges(changes: List<com.vyibc.autocr.model.FileChange>) {
        this.fileChanges = changes
        fireTableDataChanged()
    }
    
    override fun getRowCount(): Int = fileChanges.size
    
    override fun getColumnCount(): Int = columnNames.size
    
    override fun getColumnName(column: Int): String = columnNames[column]
    
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val fileChange = fileChanges[rowIndex]
        return when (columnIndex) {
            0 -> fileChange.filePath
            1 -> fileChange.changeType.name
            2 -> "+" + (10..100).random() // 模拟添加行数
            3 -> "-" + (5..50).random()   // 模拟删除行数
            4 -> "待评审"
            else -> ""
        }
    }
    
    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
}

/**
 * 文件变更表格单元格渲染器
 */
class FileChangeTableCellRenderer : DefaultTableCellRenderer() {
    
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        
        if (!isSelected) {
            // 根据变更类型设置背景色
            if (column == 1) { // 变更类型列
                when (value?.toString()) {
                    "ADDED" -> component.background = Color(220, 255, 220)    // 浅绿色
                    "MODIFIED" -> component.background = Color(255, 250, 205) // 浅黄色
                    "DELETED" -> component.background = Color(255, 220, 220)  // 浅红色
                    else -> component.background = Color.WHITE
                }
            } else {
                component.background = Color.WHITE
            }
        }
        
        return component
    }
}