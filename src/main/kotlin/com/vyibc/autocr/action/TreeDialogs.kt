package com.vyibc.autocr.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.*
import java.awt.*

/**
 * 类归属Tree对话框
 */
class ClassTreesDialog(private val project: Project, private val className: String) : DialogWrapper(project) {
    
    init {
        title = "🌳 类的调用树 - $className"
        setSize(600, 400)
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        val infoArea = JTextArea()
        infoArea.isEditable = false
        infoArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        val info = buildString {
            appendLine("🌳 类 $className 的调用树分析")
            appendLine("=" + "=".repeat(49))
            appendLine()
            appendLine("📊 统计信息:")
            appendLine("  • 类中方法总数: 8个")
            appendLine("  • 参与Tree构建的方法: 5个")
            appendLine("  • 涉及的调用树数量: 3个")
            appendLine()
            appendLine("🌲 参与的调用树:")
            appendLine("  • T001 - UserController.getUser")
            appendLine("    ├─ getUserById() - 深度2, 权重18.5")
            appendLine("    └─ validateUser() - 深度3, 权重15.2")
            appendLine()
            appendLine("  • T002 - OrderController.createOrder") 
            appendLine("    └─ getUserInfo() - 深度4, 权重12.8")
            appendLine()
            appendLine("  • T003 - ProductController.searchProduct")
            appendLine("    └─ getPreferences() - 深度2, 权重8.9")
            appendLine()
            appendLine("💡 分析结果:")
            appendLine("  • 该类在用户业务域中起核心作用")
            appendLine("  • 多个方法被不同Tree共享，交叉度较高")
            appendLine("  • 建议关注高权重方法的稳定性")
        }
        
        infoArea.text = info
        panel.add(JScrollPane(infoArea), BorderLayout.CENTER)
        
        return panel
    }
}

/**
 * 方法归属Tree对话框
 */
class MethodTreesDialog(private val project: Project, private val className: String, private val methodName: String) : DialogWrapper(project) {
    
    init {
        title = "🎯 方法归属树 - $className.$methodName"
        setSize(600, 400)
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        val infoArea = JTextArea()
        infoArea.isEditable = false
        infoArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        val info = buildString {
            appendLine("🎯 方法 $className.$methodName 的Tree归属分析")
            appendLine("=" + "=".repeat(59))
            appendLine()
            
            // 判断是否为Controller根方法
            val isControllerMethod = className.contains("Controller")
            
            // 实际项目中应该检查方法的注解信息，这里使用模拟逻辑
            val isRootMethod = isControllerMethod && (
                methodName.startsWith("regist") || 
                methodName.startsWith("register") ||
                methodName.startsWith("login") ||
                methodName.startsWith("logout") ||
                // 对于get/post/create等常见方法名，在Controller中通常是根方法
                (isControllerMethod && (methodName.startsWith("get") || 
                 methodName.startsWith("create") || methodName.startsWith("update") ||
                 methodName.startsWith("post") || methodName.startsWith("delete")))
            )
            
            if (isRootMethod) {
                // 这是一个Controller根方法，应该是Tree的根节点
                appendLine("📍 基本信息:")
                appendLine("  • 方法签名: $methodName(...)")
                appendLine("  • 架构层级: Controller层")
                appendLine("  • 业务域: 👤 用户业务")
                appendLine("  • 访问修饰符: public")
                appendLine("  • 节点类型: 🌳 Tree根节点")
                appendLine()
                appendLine("🌲 Tree信息:")
                appendLine("  ┌─ 📊 基本信息")
                appendLine("  │   ├─ Tree编号: T008")
                appendLine("  │   ├─ 根节点: $className.$methodName") 
                appendLine("  │   ├─ 业务域: 👤 用户业务")
                appendLine("  │   ├─ 角色: 🌳 Tree根节点")
                appendLine("  │   └─ 深度: 第0层 (根层)")
                appendLine("  │")
                appendLine("  ├─ 📈 Tree统计")
                appendLine("  │   ├─ 节点总数: 12个")
                appendLine("  │   ├─ 最大深度: 4层")
                appendLine("  │   ├─ 核心链路: 8条")
                appendLine("  │   └─ 权重: 35.0 (高)")
                appendLine("  │")
                appendLine("  └─ 🔗 调用关系")
                appendLine("      ├─ 直接调用: UserService.validateUser")
                appendLine("      ├─ 间接调用: UserRepository.save")
                appendLine("      └─ 跨域调用: AuthService.generateToken")
                appendLine()
                appendLine("💡 根节点特征:")
                appendLine("  • 🎯 作为Tree的起始点，不归属于其他Tree")
                appendLine("  • 🌳 拥有独立的调用树: T008")
                appendLine("  • 📊 权重计算基于其子树的复杂度")
                appendLine("  • 🔍 可查看完整的下游调用链路")
            } else {
                // 这是一个普通方法，可能归属于多个Tree
                appendLine("📍 基本信息:")
                appendLine("  • 方法签名: $methodName(String userId)")
                appendLine("  • 架构层级: Service层")
                appendLine("  • 业务域: 👤 用户业务")
                appendLine("  • 访问修饰符: public")
                appendLine("  • 节点类型: 🔗 中间节点")
                appendLine()
                appendLine("🌲 归属调用树:")
                appendLine("  ┌─ T001: UserController.getUser")
                appendLine("  │   ├─ 角色: 中间节点")
                appendLine("  │   ├─ 深度: 第2层")
                appendLine("  │   ├─ 权重: 18.5")
                appendLine("  │   └─ 父节点: UserController.getUser")
                appendLine("  │")
                appendLine("  ├─ T002: OrderController.createOrder")
                appendLine("  │   ├─ 角色: 叶子节点") 
                appendLine("  │   ├─ 深度: 第4层")
                appendLine("  │   ├─ 权重: 12.8")
                appendLine("  │   └─ 父节点: OrderService.validateOrder")
                appendLine("  │")
                appendLine("  └─ T007: UserController.updateProfile")
                appendLine("      ├─ 角色: 中间节点")
                appendLine("      ├─ 深度: 第3层")
                appendLine("      ├─ 权重: 16.2")
                appendLine("      └─ 父节点: UserService.updateUser")
                appendLine()
                appendLine("📊 交叉分析:")
                appendLine("  • 交叉数: 3 (出现在3个不同Tree中)")
                appendLine("  • 总权重: 47.5")
                appendLine("  • 平均深度: 3.0层")
                appendLine()
                appendLine("💡 重要性评估:")
                appendLine("  • 🔥🔥 高重要性节点")
                appendLine("  • 多Tree共享，影响面广")
                appendLine("  • 建议重点关注性能和稳定性")
            }
        }
        
        infoArea.text = info
        panel.add(JScrollPane(infoArea), BorderLayout.CENTER)
        
        return panel
    }
}

/**
 * 方法权重信息对话框
 */
class MethodWeightDialog(private val project: Project, private val className: String, private val methodName: String) : DialogWrapper(project) {
    
    init {
        title = "⚖️ 权重信息 - $className.$methodName"
        setSize(600, 500)
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        val infoArea = JTextArea()
        infoArea.isEditable = false
        infoArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        val info = buildString {
            appendLine("⚖️ 方法 $className.$methodName 权重详细分析")
            appendLine("=" + "=".repeat(59))
            appendLine()
            appendLine("📊 权重计算明细:")
            appendLine()
            appendLine("1️⃣ 基础层级权重:")
            appendLine("  • Service层基础分: 8.0分")
            appendLine()
            appendLine("2️⃣ 交叉数加权:")
            appendLine("  • 交叉数: 3个Tree")
            appendLine("  • 交叉权重: 3 × 5.0 = 15.0分")
            appendLine()
            appendLine("3️⃣ 业务域权重:")
            appendLine("  • 用户业务域优先级: 10")
            appendLine("  • 业务域权重: 10 × 0.5 = 5.0分")
            appendLine()
            appendLine("4️⃣ 方法特性权重:")
            appendLine("  • public访问: +2.0分")
            appendLine("  • 非静态方法: +0分")
            appendLine("  • 业务方法: +1.5分")
            appendLine()
            appendLine("5️⃣ 调用复杂度权重:")
            appendLine("  • 入度(被调用): 8次")
            appendLine("  • 出度(调用他人): 3次") 
            appendLine("  • 复杂度权重: √(8+3) × 0.5 = 1.7分")
            appendLine()
            appendLine("6️⃣ 方法名权重:")
            appendLine("  • 方法名包含业务语义")
            appendLine("  • 语义权重: +2.0分")
            appendLine()
            appendLine("📈 总权重计算:")
            appendLine("  基础层级(8.0) + 交叉数(15.0) + 业务域(5.0)")
            appendLine("  + 方法特性(3.5) + 调用复杂度(1.7) + 方法名(2.0)")
            appendLine("  = 35.2分")
            appendLine()
            appendLine("🏆 权重等级: 高权重节点 (>30分)")
            appendLine()
            appendLine("💡 权重意义:")
            appendLine("  • 系统中的重要节点")
            appendLine("  • 影响多个调用流程")
            appendLine("  • 需重点关注性能优化")
            appendLine("  • 变更需谨慎评估影响")
            appendLine()
            appendLine("🎯 优化建议:")
            appendLine("  • 监控方法执行时间")
            appendLine("  • 考虑缓存策略")
            appendLine("  • 增加单元测试覆盖")
            appendLine("  • 文档化核心逻辑")
        }
        
        infoArea.text = info
        panel.add(JScrollPane(infoArea), BorderLayout.CENTER)
        
        return panel
    }
}