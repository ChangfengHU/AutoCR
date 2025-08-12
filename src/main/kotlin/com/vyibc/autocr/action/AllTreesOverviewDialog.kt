package com.vyibc.autocr.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.vyibc.autocr.model.*
import javax.swing.*
import javax.swing.table.DefaultTableModel
import java.awt.*

/**
 * 所有调用树概览对话框
 */
class AllTreesOverviewDialog(private val project: Project) : DialogWrapper(project) {
    
    init {
        title = "🌴 系统调用树概览"
        setSize(800, 600)
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 创建标签页
        val tabbedPane = JTabbedPane()
        tabbedPane.addTab("📊 Tree统计", createStatisticsPanel())
        tabbedPane.addTab("🌲 Tree列表", createTreeListPanel())
        tabbedPane.addTab("🏢 业务域分布", createBusinessDomainPanel())
        
        panel.add(tabbedPane, BorderLayout.CENTER)
        return panel
    }
    
    private fun createStatisticsPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 上半部分：总体统计卡片
        val statsPanel = JPanel(GridLayout(2, 3, 10, 10))
        statsPanel.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)
        
        // 模拟数据（实际应该从TreeQueryService获取）
        val mockStats = mapOf(
            "总调用树" to "12个",
            "总节点数" to "156个", 
            "总关系数" to "189个",
            "交叉节点" to "23个",
            "平均深度" to "4.2层",
            "核心链路" to "67条"
        )
        
        mockStats.forEach { (label, value) ->
            statsPanel.add(createStatCard(label, value))
        }
        
        panel.add(statsPanel, BorderLayout.NORTH)
        
        // 下半部分：详细信息
        val detailsArea = JTextArea()
        detailsArea.isEditable = false
        detailsArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        val details = buildString {
            appendLine("🎯 系统调用树分析报告")
            appendLine("=" + "=".repeat(49))
            appendLine()
            appendLine("📈 Tree构建统计:")
            appendLine("  • Controller根节点: 8个")
            appendLine("  • 最深调用链: 7层")
            appendLine("  • 最大Tree节点数: 23个")
            appendLine("  • 平均每个Tree节点数: 13个")
            appendLine()
            appendLine("🔗 调用关系分析:")
            appendLine("  • 直接调用: 124条")
            appendLine("  • 接口调用: 35条")
            appendLine("  • 静态调用: 18条")
            appendLine("  • 继承调用: 12条")
            appendLine()
            appendLine("⭐ 重要性分析:")
            appendLine("  • 高权重节点(>50): 8个")
            appendLine("  • 交叉节点(>2 Tree): 23个")
            appendLine("  • 关键路径: 12条")
            appendLine()
            appendLine("💡 优化建议:")
            appendLine("  • 建议拆分过大的Tree (>20节点)")
            appendLine("  • 关注高交叉数节点的设计")
            appendLine("  • 考虑重构深层调用链 (>5层)")
        }
        
        detailsArea.text = details
        panel.add(JScrollPane(detailsArea), BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createTreeListPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 创建表格
        val columnNames = arrayOf("Tree编号", "根节点", "业务域", "节点数", "深度", "交叉节点", "状态")
        val data = arrayOf(
            arrayOf("T001", "UserController.getUser", "👤 用户业务", "15", "4", "3", "✅ 正常"),
            arrayOf("T002", "OrderController.createOrder", "📦 订单业务", "23", "6", "5", "⚠️ 复杂"),
            arrayOf("T003", "ProductController.searchProduct", "🛍️ 产品业务", "18", "3", "2", "✅ 正常"),
            arrayOf("T004", "PaymentController.processPayment", "💳 支付业务", "12", "5", "4", "✅ 正常"),
            arrayOf("T005", "AuthController.login", "🔐 认证业务", "8", "3", "1", "✅ 正常"),
            arrayOf("T006", "SystemController.getHealth", "⚙️ 系统业务", "5", "2", "0", "✅ 正常"),
            arrayOf("T007", "UserController.updateProfile", "👤 用户业务", "12", "4", "2", "✅ 正常"),
            arrayOf("T008", "OrderController.cancelOrder", "📦 订单业务", "19", "5", "3", "✅ 正常"),
        )
        
        val tableModel = DefaultTableModel(data, columnNames)
        val table = JTable(tableModel)
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        
        // 设置列宽
        table.columnModel.getColumn(0).preferredWidth = 80   // Tree编号
        table.columnModel.getColumn(1).preferredWidth = 200  // 根节点
        table.columnModel.getColumn(2).preferredWidth = 120  // 业务域
        table.columnModel.getColumn(3).preferredWidth = 60   // 节点数
        table.columnModel.getColumn(4).preferredWidth = 50   // 深度
        table.columnModel.getColumn(5).preferredWidth = 80   // 交叉节点
        table.columnModel.getColumn(6).preferredWidth = 80   // 状态
        
        // 添加双击事件
        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedRow = table.selectedRow
                    if (selectedRow >= 0) {
                        val treeNumber = table.getValueAt(selectedRow, 0) as String
                        val rootNode = table.getValueAt(selectedRow, 1) as String
                        showTreeDetails(treeNumber, rootNode)
                    }
                }
            }
        })
        
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        
        // 添加底部操作按钮
        val buttonPanel = JPanel(FlowLayout())
        val detailsButton = JButton("📋 查看详情")
        val exportButton = JButton("📤 导出数据")
        
        detailsButton.addActionListener {
            val selectedRow = table.selectedRow
            if (selectedRow >= 0) {
                val treeNumber = table.getValueAt(selectedRow, 0) as String
                val rootNode = table.getValueAt(selectedRow, 1) as String
                showTreeDetails(treeNumber, rootNode)
            } else {
                JOptionPane.showMessageDialog(panel, "请选择一个Tree")
            }
        }
        
        exportButton.addActionListener {
            JOptionPane.showMessageDialog(panel, "导出功能开发中...")
        }
        
        buttonPanel.add(detailsButton)
        buttonPanel.add(exportButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createBusinessDomainPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 上半部分：业务域分布饼图（用文本模拟）
        val chartPanel = JPanel(BorderLayout())
        chartPanel.border = BorderFactory.createTitledBorder("📊 业务域分布")
        
        val chartArea = JTextArea()
        chartArea.isEditable = false
        chartArea.font = Font(Font.MONOSPACED, Font.PLAIN, 14)
        
        val chartText = buildString {
            appendLine("业务域分布统计:")
            appendLine()
            appendLine("👤 用户业务    ████████████ 35% (3个Tree)")
            appendLine("📦 订单业务    ████████     25% (2个Tree)")
            appendLine("🛍️ 产品业务    ██████       20% (1个Tree)")
            appendLine("💳 支付业务    ████         15% (1个Tree)")
            appendLine("🔐 认证业务    ██           5%  (1个Tree)")
        }
        
        chartArea.text = chartText
        chartArea.rows = 8
        chartPanel.add(JScrollPane(chartArea), BorderLayout.CENTER)
        
        panel.add(chartPanel, BorderLayout.NORTH)
        
        // 下半部分：业务域详细分析
        val analysisPanel = JPanel(BorderLayout())
        analysisPanel.border = BorderFactory.createTitledBorder("🔍 业务域分析")
        
        val analysisArea = JTextArea()
        analysisArea.isEditable = false
        analysisArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        val analysis = buildString {
            appendLine("🎯 业务域调用关系分析:")
            appendLine()
            appendLine("👤 用户业务域:")
            appendLine("  • 包含3个调用树，覆盖用户管理全流程")
            appendLine("  • 与认证业务高度耦合 (90%)")
            appendLine("  • 与订单业务中度关联 (70%)")
            appendLine("  • 平均调用深度: 3.7层")
            appendLine()
            appendLine("📦 订单业务域:")
            appendLine("  • 包含2个主要调用树")
            appendLine("  • 与支付业务强关联 (90%)")
            appendLine("  • 与产品业务强关联 (80%)")
            appendLine("  • 调用链最复杂，平均深度: 5.5层")
            appendLine()
            appendLine("🛍️ 产品业务域:")
            appendLine("  • 相对独立的业务域")
            appendLine("  • 主要被订单业务调用")
            appendLine("  • 调用链简单，平均深度: 3层")
            appendLine()
            appendLine("💡 跨业务域调用热点:")
            appendLine("  • UserService.getUserById (被4个业务域调用)")
            appendLine("  • ProductService.getProductInfo (被3个业务域调用)")
            appendLine("  • PaymentService.validatePayment (被2个业务域调用)")
        }
        
        analysisArea.text = analysis
        analysisPanel.add(JScrollPane(analysisArea), BorderLayout.CENTER)
        
        panel.add(analysisPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createStatCard(label: String, value: String): JPanel {
        val card = JPanel(BorderLayout())
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        )
        card.background = Color.WHITE
        
        val valueLabel = JLabel(value, SwingConstants.CENTER)
        valueLabel.font = Font(Font.SANS_SERIF, Font.BOLD, 24)
        valueLabel.foreground = Color(0, 123, 255)
        
        val labelLabel = JLabel(label, SwingConstants.CENTER)
        labelLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        labelLabel.foreground = Color.GRAY
        
        card.add(valueLabel, BorderLayout.CENTER)
        card.add(labelLabel, BorderLayout.SOUTH)
        
        return card
    }
    
    private fun showTreeDetails(treeNumber: String, rootNode: String) {
        val message = """
            🌲 调用树详情
            
            Tree编号: $treeNumber
            根节点: $rootNode
            
            这里将显示该Tree的详细结构，包括:
            • 完整的节点层次结构
            • 调用关系图
            • 核心链路信息
            • 交叉节点分析
            
            (详细视图开发中...)
        """.trimIndent()
        
        JOptionPane.showMessageDialog(this.contentPane, message, "Tree详情", JOptionPane.INFORMATION_MESSAGE)
    }
}