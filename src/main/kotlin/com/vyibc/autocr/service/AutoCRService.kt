package com.vyibc.autocr.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.vyibc.autocr.model.FileChange
import com.vyibc.autocr.preprocessor.DualFlowPreprocessor
import com.vyibc.autocr.ai.MultiAIAdapterService
import com.vyibc.autocr.ai.AIRequestContext
import com.vyibc.autocr.ai.AIRequestType
import com.vyibc.autocr.psi.PSIService
import com.vyibc.autocr.ui.AutoCRToolWindowFactory
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import com.vyibc.autocr.cache.CacheService

/**
 * AutoCR核心服务
 * 协调各个组件完成代码评审流程
 */
@Service(Service.Level.PROJECT)
class AutoCRService(private val project: Project) {
    private val logger = LoggerFactory.getLogger(AutoCRService::class.java)
    
    // 核心组件
    private val preprocessor = DualFlowPreprocessor(project)
    private val aiService = MultiAIAdapterService.getInstance(project)
    private val psiService = PSIService.getInstance(project)
    private val cacheService = CacheService.getInstance(project)
    
    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 启动代码评审流程
     */
    fun startCodeReview(
        sourceBranch: String,
        targetBranch: String,
        fileChanges: List<FileChange>
    ) {
        logger.info("Starting code review: {} -> {}, {} files", 
            sourceBranch, targetBranch, fileChanges.size)
        
        serviceScope.launch {
            try {
                // 1. 双流智能预处理
                logger.info("Phase 1: Dual-flow preprocessing")
                val analysisReport = preprocessor.analyzeFileChanges(fileChanges)
                
                // 2. 更新UI - 显示分析报告
                withContext(Dispatchers.Main) {
                    updateReportWindow { reportWindow ->
                        reportWindow.updateAnalysisReport(analysisReport)
                    }
                }
                
                // 3. AI分析
                logger.info("Phase 2: AI analysis")
                val aiResponses = performAIAnalysis(analysisReport)
                
                // 4. 更新UI - 显示AI评审结果
                withContext(Dispatchers.Main) {
                    updateReportWindow { reportWindow ->
                        reportWindow.updateAIReview(aiResponses)
                    }
                    
                    // 显示工具窗口
                    showToolWindow()
                }
                
                logger.info("Code review completed successfully")
                
            } catch (e: Exception) {
                logger.error("Code review failed", e)
                withContext(Dispatchers.Main) {
                    showErrorNotification("代码评审失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 执行AI分析
     */
    private suspend fun performAIAnalysis(analysisReport: com.vyibc.autocr.preprocessor.DualFlowAnalysisReport): List<com.vyibc.autocr.ai.AIResponse> {
        val responses = mutableListOf<com.vyibc.autocr.ai.AIResponse>()
        
        try {
            // 代码评审
            val codeReviewContext = AIRequestContext(
                requestId = "review_${System.currentTimeMillis()}",
                requestType = AIRequestType.CODE_REVIEW,
                priority = 8
            )
            val codeReviewResponse = aiService.performCodeReview(analysisReport, codeReviewContext)
            responses.add(codeReviewResponse)
            
            // 安全分析（如果有高风险文件）
            if (analysisReport.highPriorityFiles.isNotEmpty()) {
                val securityMethods = analysisReport.highPriorityFiles
                    .flatMap { it.methodAnalyses }
                    .map { it.method }
                    .filter { it.riskScore > 0.7 }
                
                if (securityMethods.isNotEmpty()) {
                    val securityContext = AIRequestContext(
                        requestId = "security_${System.currentTimeMillis()}",
                        requestType = AIRequestType.SECURITY_ANALYSIS,
                        priority = 9
                    )
                    val securityResponse = aiService.performSecurityAnalysis(securityMethods, securityContext)
                    responses.add(securityResponse)
                }
            }
            
            // 性能分析（如果有复杂方法）
            val complexMethods = analysisReport.highPriorityFiles
                .flatMap { it.methodAnalyses }
                .map { it.method }
                .filter { it.cyclomaticComplexity > 10 }
            
            if (complexMethods.isNotEmpty()) {
                val performanceContext = AIRequestContext(
                    requestId = "performance_${System.currentTimeMillis()}",
                    requestType = AIRequestType.PERFORMANCE_ANALYSIS,
                    priority = 7
                )
                val performanceResponse = aiService.performPerformanceAnalysis(complexMethods, performanceContext)
                responses.add(performanceResponse)
            }
            
        } catch (e: Exception) {
            logger.error("AI analysis failed", e)
            throw e
        }
        
        return responses
    }
    
    /**
     * 更新报告窗口
     */
    private fun updateReportWindow(action: (com.vyibc.autocr.ui.CodeReviewReportWindow) -> Unit) {
        val reportWindow = project.getUserData(AutoCRToolWindowFactory.CODE_REVIEW_REPORT_WINDOW_KEY)
        if (reportWindow != null) {
            action(reportWindow)
        } else {
            logger.warn("Report window not found")
        }
    }
    
    /**
     * 显示工具窗口
     */
    private fun showToolWindow() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("AutoCR")
        toolWindow?.show()
    }
    
    /**
     * 显示错误通知
     */
    private fun showErrorNotification(message: String) {
        com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("AutoCR")
            .createNotification("AutoCR Error", message, com.intellij.notification.NotificationType.ERROR)
            .notify(project)
    }
    
    /**
     * 获取服务使用统计
     */
    fun getServiceStatistics(): ServiceStatistics {
        val aiStats = aiService.getUsageStatistics()
        val cacheStats = cacheService.getCacheSummary()
        
        return ServiceStatistics(
            aiRequestCount = aiStats.totalRequests,
            aiTotalTokens = aiStats.totalTokenUsage,
            aiAverageResponseTime = aiStats.averageResponseTime,
            cacheHitRate = cacheStats.overallHitRate,
            cacheSize = cacheStats.totalSize
        )
    }
    
    /**
     * 清理资源
     */
    fun dispose() {
        serviceScope.cancel()
        logger.info("AutoCR service disposed")
    }
    
    companion object {
        fun getInstance(project: Project): AutoCRService {
            return project.service()
        }
    }
}

/**
 * 服务统计信息
 */
data class ServiceStatistics(
    val aiRequestCount: Long,
    val aiTotalTokens: Long,
    val aiAverageResponseTime: Double,
    val cacheHitRate: Double,
    val cacheSize: Long
)