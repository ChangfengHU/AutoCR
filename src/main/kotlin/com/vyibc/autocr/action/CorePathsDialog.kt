package com.vyibc.autocr.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.vyibc.autocr.model.*
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import java.awt.*

/**
 * 核心链路展示对话框
 */
class CorePathsDialog(private val project: Project, private val className: String, private val methodName: String) : DialogWrapper(project) {
    
    init {
        title = "🛤️ 核心链路 - $className.$methodName"
        setSize(800, 600)
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 创建分割面板
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        splitPane.leftComponent = createPathListPanel()
        splitPane.rightComponent = createPathDetailsPanel()
        splitPane.dividerLocation = 300
        
        panel.add(splitPane, BorderLayout.CENTER)
        
        // 添加顶部信息面板
        panel.add(createInfoPanel(), BorderLayout.NORTH)
        
        return panel
    }
    
    private fun createInfoPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        val infoLabel = JLabel("""
            <html>
            <h3>🎯 $className.$methodName 的核心链路</h3>
            <p>核心链路是从当前方法到各个调用树根节点的路径，展示了方法在系统中的重要调用关系</p>
            </html>
        """.trimIndent())
        
        panel.add(infoLabel, BorderLayout.CENTER)
        return panel
    }
    
    private fun createPathListPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("📋 核心链路列表")
        
        // 创建表格
        val columnNames = arrayOf("编号", "目标Tree", "长度", "权重", "业务域")
        val data = arrayOf(
            arrayOf("CP001-001", "T001", "3", "45.2", "👤 用户"),
            arrayOf("CP001-002", "T002", "4", "62.8", "📦 订单"),
            arrayOf("CP001-003", "T003", "2", "28.5", "🛍️ 产品"),
            arrayOf("CP002-001", "T004", "5", "71.3", "💳 支付"),
        )
        
        val tableModel = DefaultTableModel(data, columnNames)
        val table = JTable(tableModel)
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        
        // 设置列宽
        table.columnModel.getColumn(0).preferredWidth = 80   // 编号
        table.columnModel.getColumn(1).preferredWidth = 60   // 目标Tree
        table.columnModel.getColumn(2).preferredWidth = 50   // 长度
        table.columnModel.getColumn(3).preferredWidth = 60   // 权重
        table.columnModel.getColumn(4).preferredWidth = 80   // 业务域
        
        // 添加选择监听器
        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = table.selectedRow
                if (selectedRow >= 0) {
                    updatePathDetails(selectedRow)
                }
            }
        }
        
        panel.add(JScrollPane(table), BorderLayout.CENTER)
        
        // 默认选择第一行
        if (data.isNotEmpty()) {
            table.setRowSelectionInterval(0, 0)
        }
        
        return panel
    }
    
    private lateinit var pathDetailsArea: JTextArea
    private lateinit var pathTreePanel: JPanel
    
    private fun createPathDetailsPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        val tabbedPane = JTabbedPane()
        tabbedPane.addTab("🗺️ 路径详情", createPathDetailTab())
        tabbedPane.addTab("🌲 调用树", createCallTreeTab())
        tabbedPane.addTab("📊 权重分析", createWeightAnalysisTab())
        
        panel.add(tabbedPane, BorderLayout.CENTER)
        return panel
    }
    
    private fun createPathDetailTab(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("📝 路径详细信息")
        
        pathDetailsArea = JTextArea()
        pathDetailsArea.isEditable = false
        pathDetailsArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        panel.add(JScrollPane(pathDetailsArea), BorderLayout.CENTER)
        
        // 初始化显示第一条路径
        updatePathDetails(0)
        
        return panel
    }
    
    private fun createCallTreeTab(): JComponent {
        pathTreePanel = JPanel(BorderLayout())
        pathTreePanel.border = BorderFactory.createTitledBorder("🌳 调用链路图")
        
        // 初始化显示第一条路径的树
        updateCallTree(0)
        
        return pathTreePanel
    }
    
    private fun createWeightAnalysisTab(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("⚖️ 权重分析")
        
        val analysisArea = JTextArea()
        analysisArea.isEditable = false
        analysisArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        val analysis = buildString {
            appendLine("🎯 权重计算公式:")
            appendLine("核心链路权重 = Σ(节点权重) × 链路长度因子 × 业务重要性")
            appendLine()
            appendLine("📊 各权重因子详解:")
            appendLine()
            appendLine("1️⃣ 节点权重计算:")
            appendLine("  • Controller层: 基础分10分")
            appendLine("  • Service层: 基础分8分")
            appendLine("  • Repository层: 基础分6分")
            appendLine("  • 交叉数加权: +5分/交叉")
            appendLine("  • 业务域优先级: +1-10分")
            appendLine()
            appendLine("2️⃣ 链路长度因子:")
            appendLine("  • 长度 ≤ 3: 系数1.0")
            appendLine("  • 长度 4-5: 系数0.9")
            appendLine("  • 长度 > 5: 系数0.8")
            appendLine()
            appendLine("3️⃣ 业务重要性:")
            appendLine("  • 用户业务: 系数1.0")
            appendLine("  • 订单业务: 系数0.9")
            appendLine("  • 支付业务: 系数0.8")
            appendLine("  • 其他业务: 系数0.6")
            appendLine()
            appendLine("💡 权重意义:")
            appendLine("  • 高权重链路是系统核心调用路径")
            appendLine("  • 重点关注高权重链路的稳定性")
            appendLine("  • 优化高权重链路可显著提升系统性能")
        }
        
        analysisArea.text = analysis
        panel.add(JScrollPane(analysisArea), BorderLayout.CENTER)
        
        return panel
    }
    
    private fun updatePathDetails(selectedIndex: Int) {
        // 模拟数据，实际应该从TreeQueryService获取
        val pathDetails = arrayOf(
            """
            🛤️ 核心链路 CP001-001 详情
            
            起点: $className.$methodName
            终点: UserController.getUser (T001根节点)
            
            📍 完整路径:
            1. $className.$methodName
               ├─ 层级: Service层
               ├─ 业务域: 👤 用户业务
               └─ 权重: 15.2
            
            2. UserService.validateUser
               ├─ 层级: Service层  
               ├─ 业务域: 👤 用户业务
               └─ 权重: 18.5
            
            3. UserController.getUser ⭐ (Tree根节点)
               ├─ 层级: Controller层
               ├─ 业务域: 👤 用户业务
               └─ 权重: 25.0
            
            📊 链路统计:
            • 总长度: 3跳
            • 总权重: 45.2
            • 跨层级: Service → Controller
            • 业务域: 单一业务域 (用户)
            • 重要性: 🔥 高
            """.trimIndent(),
            
            """
            🛤️ 核心链路 CP001-002 详情
            
            起点: $className.$methodName
            终点: OrderController.createOrder (T002根节点)
            
            📍 完整路径:
            1. $className.$methodName
               ├─ 层级: Service层
               ├─ 业务域: 👤 用户业务
               └─ 权重: 15.2
            
            2. UserService.getUserInfo
               ├─ 层级: Service层
               ├─ 业务域: 👤 用户业务
               └─ 权重: 12.8
            
            3. OrderService.validateOrder
               ├─ 层级: Service层
               ├─ 业务域: 📦 订单业务
               └─ 权重: 22.3
            
            4. OrderController.createOrder ⭐ (Tree根节点)
               ├─ 层级: Controller层
               ├─ 业务域: 📦 订单业务
               └─ 权重: 28.5
            
            📊 链路统计:
            • 总长度: 4跳
            • 总权重: 62.8
            • 跨层级: Service → Controller
            • 业务域: 跨域调用 (用户→订单)
            • 重要性: 🔥🔥 极高
            """.trimIndent(),
            
            """
            🛤️ 核心链路 CP001-003 详情
            
            起点: $className.$methodName
            终点: ProductController.searchProduct (T003根节点)
            
            📍 完整路径:
            1. $className.$methodName
               ├─ 层级: Service层
               ├─ 业务域: 👤 用户业务
               └─ 权重: 15.2
            
            2. ProductController.searchProduct ⭐ (Tree根节点)
               ├─ 层级: Controller层
               ├─ 业务域: 🛍️ 产品业务
               └─ 权重: 28.5
            
            📊 链路统计:
            • 总长度: 2跳
            • 总权重: 28.5
            • 跨层级: Service → Controller
            • 业务域: 跨域调用 (用户→产品)
            • 重要性: ⚡ 中等
            """.trimIndent(),
            
            """
            🛤️ 核心链路 CP002-001 详情
            
            起点: $className.$methodName
            终点: PaymentController.processPayment (T004根节点)
            
            📍 完整路径:
            1. $className.$methodName
               ├─ 层级: Service层
               ├─ 业务域: 👤 用户业务
               └─ 权重: 15.2
            
            2. OrderService.createOrder
               ├─ 层级: Service层
               ├─ 业务域: 📦 订单业务
               └─ 权重: 18.5
            
            3. PaymentService.calculateAmount
               ├─ 层级: Service层
               ├─ 业务域: 💳 支付业务
               └─ 权重: 16.8
            
            4. PaymentService.validatePayment
               ├─ 层级: Service层
               ├─ 业务域: 💳 支付业务
               └─ 权重: 20.1
            
            5. PaymentController.processPayment ⭐ (Tree根节点)
               ├─ 层级: Controller层
               ├─ 业务域: 💳 支付业务
               └─ 权重: 32.5
            
            📊 链路统计:
            • 总长度: 5跳
            • 总权重: 71.3
            • 跨层级: Service → Controller
            • 业务域: 跨域调用 (用户→订单→支付)
            • 重要性: 🔥🔥🔥 超高
            """.trimIndent()
        )
        
        if (selectedIndex < pathDetails.size) {
            pathDetailsArea.text = pathDetails[selectedIndex]
        }
        
        updateCallTree(selectedIndex)
    }
    
    private fun updateCallTree(selectedIndex: Int) {
        pathTreePanel.removeAll()
        
        // 创建树结构
        val root = DefaultMutableTreeNode("🛤️ 核心链路 CP00${selectedIndex + 1}-001")
        
        when (selectedIndex) {
            0 -> {
                val node1 = DefaultMutableTreeNode("📍 $className.$methodName (起点)")
                val node2 = DefaultMutableTreeNode("⚙️ UserService.validateUser")
                val node3 = DefaultMutableTreeNode("🎯 UserController.getUser (T001根节点)")
                
                root.add(node1)
                root.add(node2)
                root.add(node3)
            }
            1 -> {
                val node1 = DefaultMutableTreeNode("📍 $className.$methodName (起点)")
                val node2 = DefaultMutableTreeNode("👤 UserService.getUserInfo")
                val node3 = DefaultMutableTreeNode("📦 OrderService.validateOrder")
                val node4 = DefaultMutableTreeNode("🎯 OrderController.createOrder (T002根节点)")
                
                root.add(node1)
                root.add(node2)
                root.add(node3)
                root.add(node4)
            }
            2 -> {
                val node1 = DefaultMutableTreeNode("📍 $className.$methodName (起点)")
                val node2 = DefaultMutableTreeNode("🎯 ProductController.searchProduct (T003根节点)")
                
                root.add(node1)
                root.add(node2)
            }
            3 -> {
                val node1 = DefaultMutableTreeNode("📍 $className.$methodName (起点)")
                val node2 = DefaultMutableTreeNode("📦 OrderService.createOrder")
                val node3 = DefaultMutableTreeNode("💳 PaymentService.calculateAmount")
                val node4 = DefaultMutableTreeNode("💳 PaymentService.validatePayment")
                val node5 = DefaultMutableTreeNode("🎯 PaymentController.processPayment (T004根节点)")
                
                root.add(node1)
                root.add(node2)
                root.add(node3)
                root.add(node4)
                root.add(node5)
            }
        }
        
        val treeModel = DefaultTreeModel(root)
        val tree = JTree(treeModel)
        tree.expandRow(0)
        
        pathTreePanel.add(JScrollPane(tree), BorderLayout.CENTER)
        pathTreePanel.revalidate()
        pathTreePanel.repaint()
    }
}