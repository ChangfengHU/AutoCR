package com.vyibc.autocr.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.vyibc.autocr.dialog.BranchSelectionDialog
import com.vyibc.autocr.service.CodeReviewOrchestrator
import com.vyibc.autocr.model.BranchComparisonRequest
import org.slf4j.LoggerFactory

/**
 * 启动AI代码评审的主要入口
 * 实现技术方案V5.1的核心用户工作流
 */
class StartCodeReviewAction : AnAction("🤖 AI代码评审", "启动智能代码评审分析", null) {
    
    private val logger = LoggerFactory.getLogger(StartCodeReviewAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        logger.info("用户触发AI代码评审")
        
        // 步骤1: 显示分支选择对话框
        val dialog = BranchSelectionDialog(project)
        if (!dialog.showAndGet()) {
            logger.info("用户取消了分支选择")
            return
        }
        
        val request = dialog.getBranchComparisonRequest()
        logger.info("用户选择分析: ${request.sourceBranch} -> ${request.targetBranch}")
        
        // 步骤2: 启动后台分析任务
        startBackgroundAnalysis(project, request)
    }
    
    private fun startBackgroundAnalysis(project: Project, request: BranchComparisonRequest) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, 
            "AI代码评审分析中...", 
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "正在初始化分析引擎..."
                    indicator.fraction = 0.0
                    
                    val orchestrator = CodeReviewOrchestrator(project)
                    
                    // 执行三阶段分析流程
                    kotlinx.coroutines.runBlocking {
                        val result = orchestrator.performCodeReview(request) { progress ->
                            indicator.text = progress.message
                            indicator.fraction = progress.percentage / 100.0
                            indicator.checkCanceled()
                        }
                        
                        // 在EDT线程中显示结果
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            displayReviewResult(project, result)
                        }
                    }
                    
                } catch (e: Exception) {
                    logger.error("AI代码评审失败", e)
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        showErrorNotification(project, "代码评审失败: ${e.message}")
                    }
                }
            }
        })
    }
    
    private fun displayReviewResult(project: Project, result: CodeReviewResult) {
        // 显示评审结果工具窗口
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("AI代码评审") 
            ?: toolWindowManager.registerToolWindow("AI代码评审", true, com.intellij.openapi.wm.ToolWindowAnchor.BOTTOM)
        
        // 更新工具窗口内容
        val contentManager = toolWindow.contentManager
        contentManager.removeAllContents(true)
        
        // 构建详细的评审报告
        val reportContent = buildDetailedReviewReport(result)
        
        // 创建文本区域显示结果
        val textArea = javax.swing.JTextArea()
        textArea.text = reportContent
        textArea.isEditable = false
        textArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        
        val scrollPane = javax.swing.JScrollPane(textArea)
        val content = contentManager.factory.createContent(scrollPane, "评审结果", false)
        contentManager.addContent(content)
        
        // 如果有调试信息，创建单独的调试标签页
        result.debugInfo?.let { debugInfo ->
            val debugContent = buildDebugReport(debugInfo)
            val debugTextArea = javax.swing.JTextArea()
            debugTextArea.text = debugContent
            debugTextArea.isEditable = false
            debugTextArea.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 10)
            
            val debugScrollPane = javax.swing.JScrollPane(debugTextArea)
            val debugContentTab = contentManager.factory.createContent(debugScrollPane, "调试详情", false)
            contentManager.addContent(debugContentTab)
        }
        
        // 显示工具窗口
        toolWindow.activate(null)
        
        // 发送通知
        showSuccessNotification(project, "AI代码评审完成", "请查看详细分析报告")
        
        logger.info("AI代码评审完成，发现 ${result.intentAnalysis.size} 个功能意图，${result.riskAnalysis.size} 个潜在风险")
    }
    
    private fun showSuccessNotification(project: Project, title: String, message: String) {
        com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("AutoCR.Notifications")
            .createNotification(title, message, com.intellij.notification.NotificationType.INFORMATION)
            .notify(project)
    }
    
    private fun showErrorNotification(project: Project, message: String) {
        com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("AutoCR.Notifications")
            .createNotification("AI代码评审", message, com.intellij.notification.NotificationType.ERROR)
            .notify(project)
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        e.presentation.isEnabled = project != null
    }
    
    /**
     * 构建详细的评审报告
     */
    private fun buildDetailedReviewReport(result: CodeReviewResult): String {
        val report = StringBuilder()
        
        report.appendLine("AI代码评审报告")
        report.appendLine("================")
        report.appendLine()
        report.appendLine("分支对比: ${result.sourceBranch} → ${result.targetBranch}")
        report.appendLine()
        
        // 功能意图分析
        if (result.intentAnalysis.isNotEmpty()) {
            report.appendLine("## 功能意图分析")
            report.appendLine("💡 以下是AI识别的开发者功能意图和业务价值评估：")
            report.appendLine()
            result.intentAnalysis.forEach { intent ->
                report.appendLine("### 意图：${intent.description}")
                report.appendLine("📊 **业务价值评分**: ${intent.businessValue}%")
                report.appendLine("📋 **实现概述**: ${intent.implementationSummary}")
                if (intent.relatedPaths.isNotEmpty()) {
                    report.appendLine("🔗 **影响的代码路径**: ${intent.relatedPaths.joinToString(", ")}")
                }
                report.appendLine("🎯 **AI置信度**: ${(intent.confidence * 100).toInt()}%")
                
                // 根据业务价值给出建议
                val valueAssessment = when {
                    intent.businessValue >= 80 -> "⭐⭐⭐ 高价值功能，建议优先通过"
                    intent.businessValue >= 60 -> "⭐⭐ 中等价值功能，可按计划推进"
                    intent.businessValue >= 40 -> "⭐ 较低价值功能，考虑优先级"
                    else -> "❓ 价值不明确，建议与产品确认需求"
                }
                report.appendLine("💭 **价值评估**: $valueAssessment")
                report.appendLine()
            }
        } else {
            report.appendLine("## 功能意图分析")
            report.appendLine("❓ **AI未能识别明确的功能意图**")
            report.appendLine("🔍 可能原因：")
            report.appendLine("• 变更过于技术性（重构、格式化等）")
            report.appendLine("• 变更范围过小或过于分散")
            report.appendLine("• 缺乏相关的业务上下文信息")
            report.appendLine("💡 建议：在提交信息中添加更详细的功能描述")
            report.appendLine()
        }
        
        // 风险分析
        if (result.riskAnalysis.isNotEmpty()) {
            report.appendLine("## 风险分析")
            report.appendLine("⚠️ 以下是AI识别的潜在风险和建议措施：")
            report.appendLine()
            result.riskAnalysis.forEach { risk ->
                // 风险等级图标
                val severityIcon = when (risk.severity.uppercase()) {
                    "CRITICAL" -> "🚨"
                    "HIGH" -> "🔴"
                    "MEDIUM" -> "🟡"
                    "LOW" -> "🟢"
                    else -> "⚪"
                }
                
                // 风险分类图标
                val categoryIcon = when (risk.category.uppercase()) {
                    "ARCHITECTURE" -> "🏗️"
                    "SECURITY" -> "🔒"
                    "PERFORMANCE" -> "⚡"
                    "LOGIC" -> "🧠"
                    "DATA" -> "💾"
                    else -> "⚙️"
                }
                
                report.appendLine("### $severityIcon ${risk.severity} - $categoryIcon ${risk.category}")
                report.appendLine("📝 **风险描述**: ${risk.description}")
                report.appendLine("💥 **影响范围**: ${risk.impact}")
                report.appendLine("🎯 **建议措施**: ${risk.recommendation}")
                if (risk.location.isNotBlank()) {
                    report.appendLine("📍 **具体位置**: ${risk.location}")
                }
                
                // 根据严重程度给出操作建议
                val actionRecommendation = when (risk.severity.uppercase()) {
                    "CRITICAL" -> "🚫 **立即处理**：强烈建议修复后再合并"
                    "HIGH" -> "⏰ **优先处理**：建议在合并前修复"
                    "MEDIUM" -> "📋 **计划处理**：可合并但需要跟进修复"
                    "LOW" -> "💡 **建议改进**：可选择性修复"
                    else -> "ℹ️ **留意关注**：保持关注"
                }
                report.appendLine(actionRecommendation)
                report.appendLine()
            }
        } else {
            report.appendLine("## 风险分析")
            report.appendLine("✅ **AI未发现显著风险**")
            report.appendLine("🔍 这表明：")
            report.appendLine("• 代码变更遵循了良好的实践")
            report.appendLine("• 变更范围较小，影响可控")
            report.appendLine("• 没有触及敏感的架构或逻辑")
            report.appendLine("💡 仍建议进行常规的代码复查和测试")
            report.appendLine()
        }
        
        // 总体建议
        report.appendLine("## 总体建议")
        report.appendLine("状态: ${result.overallRecommendation.approvalStatus}")
        report.appendLine("理由: ${result.overallRecommendation.reasoning}")
        report.appendLine()
        
        if (result.overallRecommendation.criticalIssues.isNotEmpty()) {
            report.appendLine("关键问题:")
            result.overallRecommendation.criticalIssues.forEach { issue ->
                report.appendLine("- $issue")
            }
            report.appendLine()
        }
        
        if (result.overallRecommendation.suggestions.isNotEmpty()) {
            report.appendLine("改进建议:")
            result.overallRecommendation.suggestions.forEach { suggestion ->
                report.appendLine("- $suggestion")
            }
            report.appendLine()
        }
        
        // 元数据
        report.appendLine("---")
        report.appendLine("处理时间: ${result.processingTime}ms")
        report.appendLine("分析元数据: ${result.analysisMetadata.analyzedPaths} 条路径，使用模型 ${result.analysisMetadata.aiModel}")
        if (result.analysisMetadata.tokensUsed > 0) {
            report.appendLine("Token使用: ${result.analysisMetadata.tokensUsed}")
        }
        
        return report.toString()
    }
    
    /**
     * 构建调试报告
     */
    private fun buildDebugReport(debugInfo: AnalysisDebugInfo): String {
        val report = StringBuilder()
        
        report.appendLine("AI代码评审调试详情")
        report.appendLine("===================")
        report.appendLine()
        
        // Git差异分析详情
        report.appendLine("## 1. Git差异分析详情")
        report.appendLine("变更文件总数: ${debugInfo.gitDiffDetails.totalChangedFiles}")
        report.appendLine("新增行数: ${debugInfo.gitDiffDetails.addedLines}")
        report.appendLine("删除行数: ${debugInfo.gitDiffDetails.deletedLines}")
        if (debugInfo.gitDiffDetails.analysisTimeMs > 0) {
            report.appendLine("分析耗时: ${debugInfo.gitDiffDetails.analysisTimeMs}ms")
        }
        report.appendLine()
        
        if (debugInfo.gitDiffDetails.commitMessages.isNotEmpty()) {
            report.appendLine("提交信息:")
            debugInfo.gitDiffDetails.commitMessages.forEach { commit ->
                report.appendLine("• $commit")
            }
            report.appendLine()
        }
        
        if (debugInfo.gitDiffDetails.changedFileDetails.isNotEmpty()) {
            report.appendLine("变更文件详情:")
            debugInfo.gitDiffDetails.changedFileDetails.forEach { file ->
                report.appendLine("📁 ${file.path} [${file.changeType}]")
                report.appendLine("   新增: ${file.addedLines}行, 删除: ${file.deletedLines}行")
                if (file.keyChanges.isNotEmpty()) {
                    report.appendLine("   关键变更: ${file.keyChanges.take(3).joinToString("; ")}")
                }
            }
            report.appendLine()
        }
        
        // 发现的调用路径详情
        report.appendLine("## 2. 发现的调用路径详情")
        report.appendLine("发现路径总数: ${debugInfo.discoveredPaths.size}")
        report.appendLine()
        
        debugInfo.discoveredPaths.take(10).forEach { path -> // 限制显示前10个路径
            report.appendLine("🔗 路径 ${path.pathId}")
            report.appendLine("   描述: ${path.description}")
            report.appendLine("   意图权重: ${"%.2f".format(path.intentWeight)}, 风险权重: ${"%.2f".format(path.riskWeight)}")
            report.appendLine("   发现原因: ${path.discoveryReason}")
            if (path.methods.isNotEmpty()) {
                report.appendLine("   调用链: ${path.methods.take(5).joinToString(" -> ")}")
            }
            if (path.relatedFiles.isNotEmpty()) {
                report.appendLine("   相关文件: ${path.relatedFiles.take(3).joinToString(", ")}")
            }
            
            // 生成对应的Neo4j查询语句
            report.appendLine("   📋 Neo4j查询语句（可复制）:")
            val neo4jQueries = generateNeo4jQueries(path)
            neo4jQueries.forEach { query ->
                report.appendLine("   $query")
            }
            
            report.appendLine()
        }
        
        if (debugInfo.discoveredPaths.size > 10) {
            report.appendLine("... 还有 ${debugInfo.discoveredPaths.size - 10} 个路径未显示")
            report.appendLine()
        }
        
        // 预处理计算详情
        report.appendLine("## 3. 双流预处理计算详情")
        report.appendLine("选择标准: ${debugInfo.preprocessingDetails.selectionCriteria}")
        report.appendLine("选择原因: ${debugInfo.preprocessingDetails.selectedPathsReason}")
        if (debugInfo.preprocessingDetails.calculationTimeMs > 0) {
            report.appendLine("计算耗时: ${debugInfo.preprocessingDetails.calculationTimeMs}ms")
        }
        report.appendLine()
        
        if (debugInfo.preprocessingDetails.intentWeightCalculation.isNotEmpty()) {
            report.appendLine("意图权重计算详情:")
            debugInfo.preprocessingDetails.intentWeightCalculation.take(5).forEach { calc ->
                report.appendLine("• 路径 ${calc.pathId}: ${"%.2f".format(calc.baseScore)} -> ${"%.2f".format(calc.finalScore)}")
                report.appendLine("  调整因子: ${calc.adjustmentFactors}")
                report.appendLine("  推理: ${calc.reasoning}")
            }
            report.appendLine()
        }
        
        if (debugInfo.preprocessingDetails.riskWeightCalculation.isNotEmpty()) {
            report.appendLine("风险权重计算详情:")
            debugInfo.preprocessingDetails.riskWeightCalculation.take(5).forEach { calc ->
                report.appendLine("• 路径 ${calc.pathId}: ${"%.2f".format(calc.baseScore)} -> ${"%.2f".format(calc.finalScore)}")
                report.appendLine("  调整因子: ${calc.adjustmentFactors}")
                report.appendLine("  推理: ${calc.reasoning}")
            }
            report.appendLine()
        }
        
        // AI交互详情
        report.appendLine("## 4. AI交互详情")
        report.appendLine("AI提供商: ${debugInfo.aiInteractionDetails.aiProvider}")
        report.appendLine("使用模型: ${debugInfo.aiInteractionDetails.modelUsed}")
        report.appendLine("上下文压缩: ${if (debugInfo.aiInteractionDetails.contextCompressionApplied) "是" else "否"}")
        report.appendLine()
        
        val tokenUsage = debugInfo.aiInteractionDetails.tokenUsageBreakdown
        if (tokenUsage.totalTokens > 0) {
            report.appendLine("Token使用情况:")
            report.appendLine("• 提示Token: ${tokenUsage.promptTokens}")
            report.appendLine("• 完成Token: ${tokenUsage.completionTokens}")
            report.appendLine("• 总计Token: ${tokenUsage.totalTokens}")
            if (tokenUsage.estimatedCost > 0) {
                report.appendLine("• 估计成本: $${String.format("%.4f", tokenUsage.estimatedCost)}")
            }
            report.appendLine()
        }
        
        if (debugInfo.aiInteractionDetails.quickScreeningPrompt.isNotEmpty()) {
            report.appendLine("=== 快速筛选提示词（完整内容，长度: ${debugInfo.aiInteractionDetails.quickScreeningPrompt.length}）===")
            report.appendLine(debugInfo.aiInteractionDetails.quickScreeningPrompt)
            report.appendLine("=== 快速筛选提示词结束 ===")
            report.appendLine()
            
            report.appendLine("=== 快速筛选AI响应（完整内容，长度: ${debugInfo.aiInteractionDetails.quickScreeningResponse.length}）===")
            report.appendLine(debugInfo.aiInteractionDetails.quickScreeningResponse)
            report.appendLine("=== 快速筛选AI响应结束 ===")
            report.appendLine()
        }
        
        if (debugInfo.aiInteractionDetails.deepAnalysisPrompt.isNotEmpty()) {
            report.appendLine("=== 深度分析提示词（完整内容，长度: ${debugInfo.aiInteractionDetails.deepAnalysisPrompt.length}）===")
            report.appendLine(debugInfo.aiInteractionDetails.deepAnalysisPrompt)
            report.appendLine("=== 深度分析提示词结束 ===")
            report.appendLine()
            
            report.appendLine("=== 深度分析AI响应（完整内容，长度: ${debugInfo.aiInteractionDetails.deepAnalysisResponse.length}）===")
            report.appendLine(debugInfo.aiInteractionDetails.deepAnalysisResponse)
            report.appendLine("=== 深度分析AI响应结束 ===")
            report.appendLine()
        }
        
        if (debugInfo.aiInteractionDetails.responseParsingDetails.isNotEmpty()) {
            report.appendLine("响应解析详情: ${debugInfo.aiInteractionDetails.responseParsingDetails}")
            report.appendLine()
        }
        
        // 分析阶段详情
        report.appendLine("## 5. 分析阶段执行详情")
        debugInfo.stageDetails.forEach { stage ->
            report.appendLine("🎯 阶段: ${stage.stageName}")
            report.appendLine("   状态: ${stage.status}")
            if (stage.startTime > 0) {
                report.appendLine("   开始时间: ${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date(stage.startTime))}")
                report.appendLine("   结束时间: ${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date(stage.endTime))}")
                report.appendLine("   耗时: ${stage.durationMs}ms")
            }
            report.appendLine("   详情: ${stage.details}")
            if (stage.outputs.isNotEmpty()) {
                report.appendLine("   输出: ${stage.outputs}")
            }
            report.appendLine()
        }
        
        return report.toString()
    }
    
    /**
     * 为路径生成Neo4j查询语句 - 使用正确的Class/Method模式
     */
    private fun generateNeo4jQueries(path: CallPathDebugInfo): List<String> {
        val queries = mutableListOf<String>()
        
        queries.add("// 📋 路径详情: ${path.description}")
        queries.add("// 🎯 意图权重: ${"%.2f".format(path.intentWeight)}, 风险权重: ${"%.2f".format(path.riskWeight)}")
        queries.add("// 🔍 发现原因: ${path.discoveryReason}")
        queries.add("")
        
        // 1. 基于方法调用链生成查询
        if (path.methods.isNotEmpty()) {
            path.methods.forEach { methodPath ->
                if (methodPath.contains(".")) {
                    val parts = methodPath.split(".")
                    if (parts.size >= 2) {
                        val className = parts.dropLast(1).joinToString(".")
                        val methodName = parts.last().removeSuffix("()")
                        
                        queries.add("// 查询类 $className 中的方法 $methodName")
                        queries.add("""
                            MATCH (c:Class)-[:CONTAINS]->(m:Method)
                            WHERE c.name = "${className.substringAfterLast(".")}" 
                               OR c.qualifiedName = "$className"
                            AND m.name = "$methodName"
                            RETURN c.name as 类名,
                                   c.qualifiedName as 完整类名,
                                   m.name as 方法名,
                                   m.signature as 方法签名,
                                   m.lineNumber as 行号,
                                   c.layer as 架构层级
                        """.trimIndent())
                        queries.add("")
                    }
                }
            }
        }
        
        // 2. 查询方法间的调用关系
        if (path.methods.size > 1) {
            val firstMethod = path.methods.first()
            val lastMethod = path.methods.last()
            
            if (firstMethod.contains(".") && lastMethod.contains(".")) {
                val sourceClass = firstMethod.substringBeforeLast(".").substringAfterLast(".")
                val sourceMethod = firstMethod.substringAfterLast(".").removeSuffix("()")
                val targetClass = lastMethod.substringBeforeLast(".").substringAfterLast(".")
                val targetMethod = lastMethod.substringAfterLast(".").removeSuffix("()")
                
                queries.add("// 查询调用关系: $sourceClass.$sourceMethod -> $targetClass.$targetMethod")
                queries.add("""
                    MATCH (sourceClass:Class)-[:CONTAINS]->(sourceMethod:Method)
                    MATCH (targetClass:Class)-[:CONTAINS]->(targetMethod:Method)
                    MATCH (sourceMethod)-[:CALLS]->(targetMethod)
                    WHERE sourceClass.name = "$sourceClass" 
                      AND sourceMethod.name = "$sourceMethod"
                      AND targetClass.name = "$targetClass"
                      AND targetMethod.name = "$targetMethod"
                    RETURN sourceClass.name as 调用者类,
                           sourceMethod.name as 调用者方法,
                           targetClass.name as 被调用者类,
                           targetMethod.name as 被调用者方法,
                           sourceClass.layer as 调用者层级,
                           targetClass.layer as 被调用者层级
                """.trimIndent())
                queries.add("")
            }
        }
        
        // 3. 查询相关文件的类结构
        path.relatedFiles.forEach { filePath ->
            val fileName = filePath.substringAfterLast("/").substringBefore(".")
            if (fileName.isNotEmpty()) {
                queries.add("// 查询文件 $filePath 中的类结构")
                queries.add("""
                    MATCH (c:Class)-[:CONTAINS]->(m:Method)
                    WHERE c.name CONTAINS "$fileName" OR c.qualifiedName CONTAINS "$fileName"
                    RETURN c.name as 类名,
                           c.qualifiedName as 完整类名,
                           count(m) as 方法数量,
                           collect(m.name)[..5] as 方法列表,
                           c.layer as 架构层级,
                           c.package as 包名
                """.trimIndent())
                queries.add("")
            }
        }
        
        // 4. 高权重路径的上下文分析
        if (path.intentWeight > 0.5 || path.riskWeight > 0.5) {
            queries.add("// 高权重路径 - 深度上下文分析")
            
            // 基于第一个方法的上下文查询
            if (path.methods.isNotEmpty()) {
                val primaryMethod = path.methods.first()
                if (primaryMethod.contains(".")) {
                    val className = primaryMethod.substringBeforeLast(".").substringAfterLast(".")
                    val methodName = primaryMethod.substringAfterLast(".").removeSuffix("()")
                    
                    queries.add("""
                        // 查询 $className.$methodName 的完整调用上下文
                        MATCH (centerClass:Class {name: "$className"})-[:CONTAINS]->(centerMethod:Method {name: "$methodName"})
                        
                        // 谁调用了这个方法
                        OPTIONAL MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod:Method)-[:CALLS]->(centerMethod)
                        
                        // 这个方法调用了谁
                        OPTIONAL MATCH (centerMethod)-[:CALLS]->(targetMethod:Method)<-[:CONTAINS]-(targetClass:Class)
                        
                        RETURN centerClass.name as 中心类,
                               centerMethod.name as 中心方法,
                               collect(DISTINCT callerClass.name + "." + callerMethod.name) as 调用者,
                               collect(DISTINCT targetClass.name + "." + targetMethod.name) as 被调用者,
                               centerClass.layer as 中心层级,
                               collect(DISTINCT callerClass.layer) as 调用者层级,
                               collect(DISTINCT targetClass.layer) as 被调用者层级
                    """.trimIndent())
                    queries.add("")
                }
            }
        }
        
        // 5. 架构层级影响分析
        queries.add("// 路径涉及的架构层级影响分析")
        queries.add("""
            // 统计路径涉及的不同架构层级
            MATCH (c:Class)
            WHERE c.layer IS NOT NULL
            RETURN c.layer as 架构层级,
                   count(c) as 类数量,
                   collect(c.name)[..3] as 示例类名
            ORDER BY 类数量 DESC
        """.trimIndent())
        
        return queries
    }
}

/**
 * 代码评审结果数据模型
 */
data class CodeReviewResult(
    val sourceBranch: String,
    val targetBranch: String,
    val intentAnalysis: List<IntentAnalysisResult>,
    val riskAnalysis: List<RiskAnalysisResult>,
    val overallRecommendation: OverallRecommendation,
    val processingTime: Long,
    val analysisMetadata: AnalysisMetadata,
    val debugInfo: AnalysisDebugInfo? = null
)

data class IntentAnalysisResult(
    val description: String,
    val businessValue: Double,
    val implementationSummary: String,
    val relatedPaths: List<String>,
    val confidence: Double
)

data class RiskAnalysisResult(
    val description: String,
    val category: String, // ARCHITECTURE, SECURITY, PERFORMANCE, LOGIC
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    val impact: String,
    val recommendation: String,
    val location: String
)

data class OverallRecommendation(
    val approvalStatus: String, // APPROVED, APPROVED_WITH_CONDITIONS, REQUIRES_REWORK
    val reasoning: String,
    val criticalIssues: List<String>,
    val suggestions: List<String>
)

data class AnalysisMetadata(
    val analyzedPaths: Int,
    val aiModel: String,
    val tokensUsed: Int
)

/**
 * 详细的分析调试信息
 */
data class AnalysisDebugInfo(
    val gitDiffDetails: GitDiffDebugInfo,
    val discoveredPaths: List<CallPathDebugInfo>,
    val preprocessingDetails: PreprocessingDebugInfo,
    val aiInteractionDetails: AIInteractionDebugInfo,
    val stageDetails: List<AnalysisStageInfo>
)

/**
 * Git差异分析调试信息
 */
data class GitDiffDebugInfo(
    val totalChangedFiles: Int,
    val addedLines: Int,
    val deletedLines: Int,
    val changedFileDetails: List<ChangedFileInfo>,
    val commitMessages: List<String>,
    val analysisTimeMs: Long
)

/**
 * 变更文件详细信息
 */
data class ChangedFileInfo(
    val path: String,
    val changeType: String, // ADDED, MODIFIED, DELETED
    val addedLines: Int,
    val deletedLines: Int,
    val keyChanges: List<String> // 关键变更摘要
)

/**
 * 调用路径调试信息
 */
data class CallPathDebugInfo(
    val pathId: String,
    val description: String,
    val methods: List<String>,
    val intentWeight: Double,
    val riskWeight: Double,
    val discoveryReason: String,
    val relatedFiles: List<String>
)

/**
 * 预处理调试信息
 */
data class PreprocessingDebugInfo(
    val intentWeightCalculation: List<WeightCalculationDetail>,
    val riskWeightCalculation: List<WeightCalculationDetail>,
    val selectionCriteria: String,
    val selectedPathsReason: String,
    val calculationTimeMs: Long
)

/**
 * 权重计算详情
 */
data class WeightCalculationDetail(
    val pathId: String,
    val calculationType: String, // INTENT, RISK
    val baseScore: Double,
    val adjustmentFactors: Map<String, Double>,
    val finalScore: Double,
    val reasoning: String
)

/**
 * AI交互调试信息
 */
data class AIInteractionDebugInfo(
    val quickScreeningPrompt: String,
    val quickScreeningResponse: String,
    val deepAnalysisPrompt: String,
    val deepAnalysisResponse: String,
    val aiProvider: String,
    val modelUsed: String,
    val contextCompressionApplied: Boolean,
    val tokenUsageBreakdown: TokenUsageInfo,
    val responseParsingDetails: String
)

/**
 * Token使用详情
 */
data class TokenUsageInfo(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val estimatedCost: Double
)

/**
 * 分析阶段信息
 */
data class AnalysisStageInfo(
    val stageName: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val status: String, // SUCCESS, FAILED, PARTIAL
    val details: String,
    val outputs: Map<String, Any>
)