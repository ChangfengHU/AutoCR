package com.vyibc.autocr.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.slf4j.LoggerFactory

/**
 * AutoCR工具窗口工厂
 * 创建代码评审报告窗口
 */
class AutoCRToolWindowFactory : ToolWindowFactory {
    private val logger = LoggerFactory.getLogger(AutoCRToolWindowFactory::class.java)
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("Creating AutoCR tool window for project: {}", project.name)
        
        // 创建评审报告窗口
        val reportWindow = CodeReviewReportWindow()
        val content = reportWindow.createContent()
        
        // 创建内容并添加到工具窗口
        val contentFactory = ContentFactory.getInstance()
        val toolWindowContent = contentFactory.createContent(content, "代码评审报告", false)
        
        toolWindow.contentManager.addContent(toolWindowContent)
        
        // 将报告窗口实例保存到项目中，以便其他组件可以访问
        project.putUserData(CODE_REVIEW_REPORT_WINDOW_KEY, reportWindow)
        
        logger.info("AutoCR tool window created successfully")
    }
    
    companion object {
        val CODE_REVIEW_REPORT_WINDOW_KEY = com.intellij.openapi.util.Key.create<CodeReviewReportWindow>("AutoCR.ReportWindow")
    }
}