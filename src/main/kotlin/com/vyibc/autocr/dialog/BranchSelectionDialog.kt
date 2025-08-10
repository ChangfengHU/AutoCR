package com.vyibc.autocr.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.vyibc.autocr.model.BranchComparisonRequest
import com.vyibc.autocr.model.AnalysisType
import com.vyibc.autocr.service.GitService
import java.awt.*
import javax.swing.*

/**
 * 分支选择对话框
 * 用户可以选择源分支和目标分支进行代码评审分析
 */
class BranchSelectionDialog(private val project: Project) : DialogWrapper(project) {
    
    private lateinit var sourceBranchList: JBList<String>
    private lateinit var targetBranchList: JBList<String>
    private lateinit var analysisTypeComboBox: JComboBox<AnalysisType>
    private var branches: List<String> = emptyList()
    
    init {
        title = "AI代码评审 - 选择分支"
        init()
        loadBranches()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 500)
        
        // 顶部说明
        val headerPanel = createHeaderPanel()
        panel.add(headerPanel, BorderLayout.NORTH)
        
        // 主要内容区域
        val contentPanel = createContentPanel()
        panel.add(contentPanel, BorderLayout.CENTER)
        
        // 底部选项
        val optionsPanel = createOptionsPanel()
        panel.add(optionsPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createHeaderPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10, 15, 15, 15)
        panel.background = JBUI.CurrentTheme.BigPopup.headerBackground()
        
        val titleLabel = JBLabel("🤖 AI智能代码评审")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        
        val descLabel = JBLabel("<html>选择要对比的分支，AI将分析代码变更的<b>功能意图</b>和<b>潜在风险</b></html>")
        descLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
        
        panel.add(titleLabel, BorderLayout.NORTH)
        panel.add(descLabel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createContentPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(10, 15, 10, 15)
        
        val gbc = GridBagConstraints()
        
        // 源分支选择
        gbc.gridx = 0; gbc.gridy = 0
        gbc.weightx = 1.0; gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel("📤 源分支 (要评审的分支):"), gbc)
        
        sourceBranchList = JBList<String>()
        sourceBranchList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sourceBranchList.cellRenderer = BranchListCellRenderer()
        
        gbc.gridy = 1
        gbc.weighty = 0.4
        gbc.fill = GridBagConstraints.BOTH
        val sourceScrollPane = JBScrollPane(sourceBranchList)
        sourceScrollPane.preferredSize = Dimension(250, 150)
        panel.add(sourceScrollPane, gbc)
        
        // 箭头指示
        gbc.gridy = 2
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.CENTER
        val arrowLabel = JBLabel("⬇️ 合并到")
        arrowLabel.font = arrowLabel.font.deriveFont(Font.BOLD)
        panel.add(arrowLabel, gbc)
        
        // 目标分支选择
        gbc.gridy = 3
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel("📥 目标分支 (合并目标):"), gbc)
        
        targetBranchList = JBList<String>()
        targetBranchList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        targetBranchList.cellRenderer = BranchListCellRenderer()
        
        gbc.gridy = 4
        gbc.weighty = 0.4
        gbc.fill = GridBagConstraints.BOTH
        val targetScrollPane = JBScrollPane(targetBranchList)
        targetScrollPane.preferredSize = Dimension(250, 150)
        panel.add(targetScrollPane, gbc)
        
        return panel
    }
    
    private fun createOptionsPanel(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.border = JBUI.Borders.empty(5, 15, 5, 15)
        
        panel.add(JBLabel("分析模式: "))
        
        analysisTypeComboBox = JComboBox(AnalysisType.values())
        analysisTypeComboBox.renderer = AnalysisTypeRenderer()
        panel.add(analysisTypeComboBox)
        
        return panel
    }
    
    private fun loadBranches() {
        try {
            val gitService = GitService(project)
            branches = gitService.getAllBranches()
            
            val listModel = DefaultListModel<String>()
            branches.forEach { listModel.addElement(it) }
            
            sourceBranchList.model = listModel
            targetBranchList.model = listModel
            
            // 设置默认选择
            val currentBranch = gitService.getCurrentBranch()
            val mainBranches = listOf("main", "master", "develop", "dev")
            
            // 默认源分支为当前分支
            if (currentBranch != null && branches.contains(currentBranch)) {
                sourceBranchList.setSelectedValue(currentBranch, true)
            }
            
            // 默认目标分支为main/master
            val defaultTarget = mainBranches.firstOrNull { branches.contains(it) }
            if (defaultTarget != null && defaultTarget != currentBranch) {
                targetBranchList.setSelectedValue(defaultTarget, true)
            }
            
        } catch (e: Exception) {
            // 如果无法获取分支，显示错误消息
            val errorModel = DefaultListModel<String>()
            errorModel.addElement("❌ 无法获取Git分支信息")
            sourceBranchList.model = errorModel
            targetBranchList.model = errorModel
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        val sourceBranch = sourceBranchList.selectedValue
        val targetBranch = targetBranchList.selectedValue
        
        return when {
            sourceBranch == null -> ValidationInfo("请选择源分支", sourceBranchList)
            targetBranch == null -> ValidationInfo("请选择目标分支", targetBranchList)
            sourceBranch == targetBranch -> ValidationInfo("源分支和目标分支不能相同", sourceBranchList)
            else -> null
        }
    }
    
    fun getBranchComparisonRequest(): BranchComparisonRequest {
        return BranchComparisonRequest(
            sourceBranch = sourceBranchList.selectedValue,
            targetBranch = targetBranchList.selectedValue,
            analysisType = analysisTypeComboBox.selectedItem as AnalysisType
        )
    }
    
    /**
     * 分支列表渲染器
     */
    private class BranchListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            if (value is String) {
                val branchName = value
                text = when {
                    branchName.contains("main") || branchName.contains("master") -> "🌳 $branchName"
                    branchName.contains("develop") || branchName.contains("dev") -> "🚧 $branchName"
                    branchName.startsWith("feature/") -> "✨ $branchName"
                    branchName.startsWith("bugfix/") -> "🐛 $branchName"
                    branchName.startsWith("hotfix/") -> "🔥 $branchName"
                    else -> "📋 $branchName"
                }
            }
            
            return this
        }
    }
    
    /**
     * 分析类型渲染器
     */
    private class AnalysisTypeRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            if (value is AnalysisType) {
                text = "${value.icon} ${value.displayName}"
                toolTipText = value.description
            }
            
            return this
        }
    }
}