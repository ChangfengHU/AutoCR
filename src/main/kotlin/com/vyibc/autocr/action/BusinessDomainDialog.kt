package com.vyibc.autocr.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.vyibc.autocr.analysis.BusinessDomainDetector
import com.vyibc.autocr.analysis.LayerDetector
import com.vyibc.autocr.model.*
import com.vyibc.autocr.service.TreeQueryService
import com.vyibc.autocr.util.PsiElementAnalyzer
import javax.swing.*
import java.awt.*

/**
 * 业务域信息对话框
 */
class BusinessDomainDialog(private val project: Project, private val className: String) : DialogWrapper(project) {
    
    private val businessDomainDetector = BusinessDomainDetector()
    private val layerDetector = LayerDetector()
    
    init {
        title = "🏢 业务域信息 - $className"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 400)
        
        // 创建内容面板
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        
        // 添加类基本信息
        contentPanel.add(createBasicInfoPanel())
        
        // 添加业务域详情
        contentPanel.add(createBusinessDomainPanel())
        
        // 添加相关统计
        contentPanel.add(createStatisticsPanel())
        
        panel.add(JScrollPane(contentPanel), BorderLayout.CENTER)
        return panel
    }
    
    private fun createBasicInfoPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder("📋 类基本信息")
        val gbc = GridBagConstraints()
        
        // 模拟获取类信息（实际应该从知识图谱中获取）
        val layer = detectLayerFromClassName(className)
        val businessDomain = detectBusinessDomainFromClassName(className)
        
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST
        
        // 类名
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("类名:"), gbc)
        gbc.gridx = 1
        panel.add(JLabel(className), gbc)
        
        // 架构层级
        gbc.gridx = 0; gbc.gridy = 1
        panel.add(JLabel("架构层级:"), gbc)
        gbc.gridx = 1
        val layerLabel = JLabel("${layer.emoji} ${layer.displayName}")
        layerLabel.foreground = getLayerColor(layer)
        panel.add(layerLabel, gbc)
        
        // 业务域
        gbc.gridx = 0; gbc.gridy = 2
        panel.add(JLabel("业务域:"), gbc)
        gbc.gridx = 1
        val domainLabel = JLabel("${businessDomain.emoji} ${businessDomain.displayName}")
        domainLabel.foreground = getDomainColor(businessDomain)
        panel.add(domainLabel, gbc)
        
        return panel
    }
    
    private fun createBusinessDomainPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("🎯 业务域分析")
        
        val businessDomain = detectBusinessDomainFromClassName(className)
        val textArea = JTextArea()
        textArea.isEditable = false
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        val analysis = buildString {
            appendLine("业务域: ${businessDomain.displayName}")
            appendLine()
            
            when (businessDomain) {
                BusinessDomain.USER -> {
                    appendLine("📝 用户业务域特征:")
                    appendLine("  • 管理用户账户、身份认证")
                    appendLine("  • 用户信息的增删改查")
                    appendLine("  • 用户权限和角色管理")
                    appendLine("  • 与认证业务域高度相关 (90%)")
                    appendLine("  • 与订单业务域中度相关 (70%)")
                }
                BusinessDomain.ORDER -> {
                    appendLine("📝 订单业务域特征:")
                    appendLine("  • 处理订单生命周期管理")
                    appendLine("  • 购物车、下单、支付流程")
                    appendLine("  • 订单状态跟踪")
                    appendLine("  • 与支付业务域高度相关 (90%)")
                    appendLine("  • 与产品业务域高度相关 (80%)")
                }
                BusinessDomain.PRODUCT -> {
                    appendLine("📝 产品业务域特征:")
                    appendLine("  • 商品信息管理")
                    appendLine("  • 库存管理")
                    appendLine("  • 产品分类和搜索")
                    appendLine("  • 与订单业务域高度相关 (80%)")
                }
                BusinessDomain.PAYMENT -> {
                    appendLine("📝 支付业务域特征:")
                    appendLine("  • 支付流程处理")
                    appendLine("  • 支付方式管理")
                    appendLine("  • 退款和对账")
                    appendLine("  • 与订单业务域高度相关 (90%)")
                    appendLine("  • 与用户业务域中度相关 (60%)")
                }
                BusinessDomain.AUTH -> {
                    appendLine("📝 认证业务域特征:")
                    appendLine("  • 身份认证和授权")
                    appendLine("  • Token管理")
                    appendLine("  • 安全策略")
                    appendLine("  • 与用户业务域高度相关 (90%)")
                }
                BusinessDomain.SYSTEM -> {
                    appendLine("📝 系统业务域特征:")
                    appendLine("  • 系统配置和管理")
                    appendLine("  • 监控和日志")
                    appendLine("  • 基础设施服务")
                }
                BusinessDomain.COMMON -> {
                    appendLine("📝 通用业务域特征:")
                    appendLine("  • 通用工具和助手类")
                    appendLine("  • 跨业务域的公共功能")
                    appendLine("  • 基础数据结构")
                }
                BusinessDomain.UNKNOWN -> {
                    appendLine("❓ 未识别业务域")
                    appendLine("  • 可能是新业务模块")
                    appendLine("  • 建议进一步分析确定业务归属")
                }
            }
            
            appendLine()
            appendLine("🔍 业务域识别规则:")
            appendLine("  1. 基于包名模式匹配")
            appendLine("  2. 基于类名语义分析")
            appendLine("  3. 基于注解信息推断")
            appendLine("  4. 基于架构层级推断")
        }
        
        textArea.text = analysis
        panel.add(JScrollPane(textArea), BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createStatisticsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("📊 统计信息")
        
        val textArea = JTextArea()
        textArea.isEditable = false
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        val businessDomain = detectBusinessDomainFromClassName(className)
        val priority = businessDomainDetector.getBusinessDomainPriority(businessDomain)
        
        val statistics = buildString {
            appendLine("业务优先级: $priority/10")
            appendLine()
            appendLine("业务域关联性分析:")
            BusinessDomain.values().forEach { domain ->
                if (domain != businessDomain && domain != BusinessDomain.UNKNOWN) {
                    val relatedness = businessDomainDetector.detectBusinessDomainRelatedness(businessDomain, domain)
                    val percentage = (relatedness * 100).toInt()
                    if (percentage > 20) {
                        appendLine("  • ${domain.displayName}: ${percentage}%")
                    }
                }
            }
            
            appendLine()
            appendLine("推荐Tree构建策略:")
            appendLine("  • 同业务域方法优先组织在同一Tree")
            appendLine("  • 高相关性业务域可以跨树调用")
            appendLine("  • 优先级高的业务域作为Tree根节点")
        }
        
        textArea.text = statistics
        textArea.rows = 8
        panel.add(JScrollPane(textArea), BorderLayout.CENTER)
        
        return panel
    }
    
    private fun detectLayerFromClassName(className: String): LayerType {
        val lowerName = className.lowercase()
        return when {
            lowerName.contains("controller") -> LayerType.CONTROLLER
            lowerName.contains("service") -> LayerType.SERVICE
            lowerName.contains("repository") || lowerName.contains("dao") -> LayerType.REPOSITORY
            lowerName.contains("mapper") -> LayerType.MAPPER
            lowerName.contains("util") || lowerName.contains("helper") -> LayerType.UTIL
            lowerName.contains("config") -> LayerType.CONFIG
            lowerName.contains("component") -> LayerType.COMPONENT
            lowerName.contains("entity") || lowerName.contains("model") -> LayerType.ENTITY
            else -> LayerType.UNKNOWN
        }
    }
    
    private fun detectBusinessDomainFromClassName(className: String): BusinessDomain {
        val lowerName = className.lowercase()
        return when {
            lowerName.contains("user") || lowerName.contains("account") -> BusinessDomain.USER
            lowerName.contains("order") || lowerName.contains("purchase") -> BusinessDomain.ORDER
            lowerName.contains("product") || lowerName.contains("goods") -> BusinessDomain.PRODUCT
            lowerName.contains("payment") || lowerName.contains("pay") -> BusinessDomain.PAYMENT
            lowerName.contains("auth") || lowerName.contains("security") -> BusinessDomain.AUTH
            lowerName.contains("system") || lowerName.contains("admin") -> BusinessDomain.SYSTEM
            lowerName.contains("common") || lowerName.contains("util") -> BusinessDomain.COMMON
            else -> BusinessDomain.UNKNOWN
        }
    }
    
    private fun getLayerColor(layer: LayerType): Color {
        return when (layer) {
            LayerType.CONTROLLER -> Color(0, 123, 255)      // 蓝色
            LayerType.SERVICE -> Color(40, 167, 69)         // 绿色
            LayerType.REPOSITORY -> Color(255, 193, 7)      // 黄色
            LayerType.MAPPER -> Color(255, 133, 27)         // 橙色
            LayerType.UTIL -> Color(108, 117, 125)          // 灰色
            LayerType.CONFIG -> Color(111, 66, 193)         // 紫色
            LayerType.COMPONENT -> Color(23, 162, 184)      // 青色
            LayerType.ENTITY -> Color(220, 53, 69)          // 红色
            LayerType.UNKNOWN -> Color(108, 117, 125)       // 灰色
        }
    }
    
    private fun getDomainColor(domain: BusinessDomain): Color {
        return when (domain) {
            BusinessDomain.USER -> Color(0, 123, 255)       // 蓝色
            BusinessDomain.ORDER -> Color(40, 167, 69)      // 绿色
            BusinessDomain.PRODUCT -> Color(255, 193, 7)    // 黄色
            BusinessDomain.PAYMENT -> Color(220, 53, 69)    // 红色
            BusinessDomain.AUTH -> Color(111, 66, 193)      // 紫色
            BusinessDomain.SYSTEM -> Color(108, 117, 125)   // 灰色
            BusinessDomain.COMMON -> Color(23, 162, 184)    // 青色
            BusinessDomain.UNKNOWN -> Color(108, 117, 125)  // 灰色
        }
    }
}