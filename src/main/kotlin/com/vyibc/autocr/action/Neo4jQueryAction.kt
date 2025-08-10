package com.vyibc.autocr.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.slf4j.LoggerFactory
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Neo4j查询Action - 执行查询并自动复制到剪贴板
 */
class Neo4jQueryAction(
    private val displayText: String,
    private val description: String,
    private val queryGenerator: () -> String
) : AnAction(displayText, description, null) {
    
    private val logger = LoggerFactory.getLogger(Neo4jQueryAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        try {
            val query = queryGenerator()
            
            if (query.isNotBlank()) {
                // 复制到剪贴板
                copyToClipboard(query)
                
                // 显示成功通知
                showSuccessNotification(project, displayText, query)
                
                logger.info("Neo4j查询已生成并复制: $displayText")
            } else {
                showErrorNotification(project, "查询生成失败", "生成的查询为空")
            }
        } catch (e: Exception) {
            logger.error("生成Neo4j查询时出错", e)
            showErrorNotification(project, "查询生成失败", e.message ?: "未知错误")
        }
    }
    
    private fun copyToClipboard(text: String) {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
    }
    
    private fun showSuccessNotification(project: Project, title: String, query: String) {
        val preview = if (query.length > 100) {
            query.substring(0, 97) + "..."
        } else {
            query
        }
        
        val content = """
            <html>
            <body>
            <p><b>✅ 查询已复制到剪贴板!</b></p>
            <p><small>现在可以直接在Neo4j Browser中粘贴使用</small></p>
            <br>
            <p><b>查询预览:</b></p>
            <pre style='font-size: 11px; color: #666; background: #f5f5f5; padding: 8px; border-radius: 4px; max-width: 400px; white-space: pre-wrap;'>$preview</pre>
            </body>
            </html>
        """.trimIndent()
        
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AutoCR.Notifications")
                .createNotification(title, content, NotificationType.INFORMATION)
                .notify(project)
        } catch (e: Exception) {
            logger.error("显示通知失败", e)
            // 回退到简单通知
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AutoCR.Notifications")
                .createNotification(
                    "Neo4j查询已复制", 
                    "查询已复制到剪贴板，可在Neo4j Browser中使用", 
                    NotificationType.INFORMATION
                )
                .notify(project)
        }
    }
    
    private fun showErrorNotification(project: Project, title: String, message: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AutoCR.Notifications")
                .createNotification(title, message, NotificationType.ERROR)
                .notify(project)
        } catch (e: Exception) {
            logger.error("显示错误通知失败", e)
        }
    }
}