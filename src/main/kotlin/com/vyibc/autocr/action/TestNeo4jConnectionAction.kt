package com.vyibc.autocr.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.vyibc.autocr.settings.AutoCRSettingsState
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

/**
 * 测试Neo4j连接的Action
 */
class TestNeo4jConnectionAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = AutoCRSettingsState.getInstance(project)
        
        if (!settings.neo4jConfig.enabled) {
            showNotification(project, "Neo4j未启用", "请先在设置中启用Neo4j", NotificationType.WARNING)
            return
        }
        
        try {
            val driver = GraphDatabase.driver(
                settings.neo4jConfig.uri,
                AuthTokens.basic(settings.neo4jConfig.username, settings.neo4jConfig.password)
            )
            
            driver.use { driverInstance ->
                driverInstance.verifyConnectivity()
                
                val sessionConfig = if (settings.neo4jConfig.database.isNotBlank()) {
                    org.neo4j.driver.SessionConfig.forDatabase(settings.neo4jConfig.database)
                } else {
                    org.neo4j.driver.SessionConfig.defaultConfig()
                }
                
                driverInstance.session(sessionConfig).use { session ->
                    // 测试简单查询
                    val result = session.run("RETURN 'Hello Neo4j' as message")
                    val message = result.single().get("message").asString()
                    
                    // 检查现有数据
                    val classCount = try {
                        session.run("MATCH (c:Class) RETURN count(c) as count").single().get("count").asInt()
                    } catch (e: Exception) {
                        0
                    }
                    
                    val methodCount = try {
                        session.run("MATCH (m:Method) RETURN count(m) as count").single().get("count").asInt()
                    } catch (e: Exception) {
                        0
                    }
                    
                    showNotification(
                        project,
                        "Neo4j连接成功 ✅",
                        "连接信息:\n" +
                                "• URI: ${settings.neo4jConfig.uri}\n" +
                                "• 数据库: ${settings.neo4jConfig.database.ifBlank { "默认" }}\n" +
                                "• 测试消息: $message\n" +
                                "• 现有类数量: $classCount\n" +
                                "• 现有方法数量: $methodCount",
                        NotificationType.INFORMATION
                    )
                }
            }
        } catch (e: Exception) {
            showNotification(
                project,
                "Neo4j连接失败 ❌",
                "错误详情: ${e.message}\n\n" +
                        "请检查:\n" +
                        "• Neo4j服务是否启动\n" +
                        "• 连接配置是否正确\n" +
                        "• 用户名密码是否正确\n" +
                        "• 数据库名称是否存在",
                NotificationType.ERROR
            )
        }
    }

    private fun showNotification(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("AutoCR.Notifications")
            .createNotification(title, content, type)
            .notify(project)
    }

    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        e.presentation.isEnabledAndVisible = project != null
    }
}