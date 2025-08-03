package com.vyibc.autocr.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.vyibc.autocr.indexing.ProjectIndexingService
import com.vyibc.autocr.neo4j.Neo4jService
import com.vyibc.autocr.settings.AutoCRSettingsState
import org.slf4j.LoggerFactory
import javax.swing.JOptionPane

/**
 * 同步知识图谱到Neo4j的Action
 */
class SyncToNeo4jAction : AnAction("同步到Neo4j", "将项目知识图谱同步到Neo4j数据库", null) {
    
    private val logger = LoggerFactory.getLogger(SyncToNeo4jAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val settings = AutoCRSettingsState.getInstance(project)
        val indexingService = ProjectIndexingService.getInstance(project)
        val neo4jService = Neo4jService.getInstance(project)
        
        // 检查Neo4j是否配置
        if (!settings.neo4jConfig.enabled) {
            val result = JOptionPane.showConfirmDialog(
                null,
                """
                Neo4j数据库未启用。
                
                是否现在配置Neo4j连接？
                """.trimIndent(),
                "Neo4j未配置",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )
            
            if (result == JOptionPane.YES_OPTION) {
                // 打开设置页面
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, "AutoCR")
            }
            return
        }
        
        // 检查项目是否已索引
        val indexingStatus = indexingService.getIndexingStatus()
        if (!indexingStatus.isIndexed) {
            val result = JOptionPane.showConfirmDialog(
                null,
                """
                项目尚未完成索引。
                
                需要先进行项目索引才能同步到Neo4j。
                是否现在开始索引？
                """.trimIndent(),
                "需要先索引项目",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            )
            
            if (result == JOptionPane.YES_OPTION) {
                indexingService.startProjectIndexing(false)
            }
            return
        }
        
        // 显示确认对话框
        val confirmed = JOptionPane.showConfirmDialog(
            null,
            """
            <html>
            <h3>🔄 同步知识图谱到Neo4j</h3>
            <p>此操作将：</p>
            <ul>
            <li>• 清空Neo4j数据库中的现有数据</li>
            <li>• 将当前项目的所有类、方法和关系同步到Neo4j</li>
            <li>• 可能需要1-5分钟，请耐心等待</li>
            </ul>
            <br>
            <p><b>📊 当前项目统计：</b></p>
            <p>• 类数量: ${indexingStatus.totalClasses}</p>
            <p>• 方法数量: ${indexingStatus.totalMethods}</p>
            <p>• 关系数量: ${indexingStatus.totalEdges}</p>
            <br>
            <p><b>是否继续同步？</b></p>
            </html>
            """.trimIndent(),
            "确认同步到Neo4j",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        ) == JOptionPane.YES_OPTION
        
        if (!confirmed) return
        
        // 使用进度条显示同步过程
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在同步到Neo4j数据库...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "正在准备同步数据..."
                    indicator.fraction = 0.0
                    
                    // 获取代码图谱数据
                    val psiService = project.service<com.vyibc.autocr.psi.PSIService>()
                    val codeGraph = psiService.getCodeGraph()
                    
                    indicator.text = "正在连接Neo4j数据库..."
                    indicator.fraction = 0.2
                    
                    // 执行同步
                    val result = neo4jService.syncCodeGraphToNeo4j(codeGraph)
                    
                    indicator.text = "同步完成，正在验证数据..."
                    indicator.fraction = 0.9
                    
                    // 等待一秒让用户看到完成状态
                    Thread.sleep(1000)
                    
                    indicator.fraction = 1.0
                    
                    // 显示结果
                    javax.swing.SwingUtilities.invokeLater {
                        val messageType = if (result.success) {
                            JOptionPane.INFORMATION_MESSAGE
                        } else {
                            JOptionPane.ERROR_MESSAGE
                        }
                        
                        val message = if (result.success) {
                            """
                            <html>
                            <h3>✅ 同步成功！</h3>
                            <p><b>同步结果统计：</b></p>
                            <table>
                            <tr><td>• 节点数量: </td><td><b>${result.syncedNodes}</b></td></tr>
                            <tr><td>• 关系数量: </td><td><b>${result.syncedEdges}</b></td></tr>
                            <tr><td>• 耗时: </td><td><b>${result.duration}ms</b></td></tr>
                            </table>
                            <br>
                            <p>🌐 您现在可以在Neo4j Browser中查看知识图谱：</p>
                            <p><code>http://localhost:7474</code></p>
                            <br>
                            <p>💡 <i>建议查询语句：</i></p>
                            <p><code>MATCH (n) RETURN n LIMIT 25</code></p>
                            </html>
                            """.trimIndent()
                        } else {
                            """
                            <html>
                            <h3>❌ 同步失败</h3>
                            <p><b>错误信息：</b></p>
                            <p style='color: red;'>${result.message}</p>
                            <br>
                            <p><b>可能的解决方案：</b></p>
                            <ul>
                            <li>• 检查Neo4j服务是否正在运行</li>
                            <li>• 验证连接配置是否正确</li>
                            <li>• 查看网络连接是否正常</li>
                            </ul>
                            </html>
                            """.trimIndent()
                        }
                        
                        JOptionPane.showMessageDialog(
                            null,
                            message,
                            if (result.success) "Neo4j同步成功" else "Neo4j同步失败",
                            messageType
                        )
                    }
                    
                } catch (e: Exception) {
                    logger.error("Failed to sync to Neo4j", e)
                    
                    javax.swing.SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(
                            null,
                            """
                            <html>
                            <h3>💥 同步过程中发生错误</h3>
                            <p><b>错误详情：</b></p>
                            <p style='color: red;'>${e.message}</p>
                            <br>
                            <p>请检查日志文件获取更多信息。</p>
                            </html>
                            """.trimIndent(),
                            "同步错误",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        })
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        
        if (project != null) {
            val settings = AutoCRSettingsState.getInstance(project)
            val indexingService = ProjectIndexingService.getInstance(project)
            
            // 根据状态更新按钮文本和图标
            when {
                !settings.neo4jConfig.enabled -> {
                    e.presentation.text = "配置Neo4j"
                    e.presentation.description = "需要先配置Neo4j连接"
                }
                !indexingService.getIndexingStatus().isIndexed -> {
                    e.presentation.text = "先索引项目"
                    e.presentation.description = "需要先完成项目索引"
                }
                indexingService.getIndexingStatus().isIndexing -> {
                    e.presentation.text = "索引进行中..."
                    e.presentation.description = "等待项目索引完成"
                    e.presentation.isEnabled = false
                }
                else -> {
                    e.presentation.text = "同步到Neo4j"
                    e.presentation.description = "将项目知识图谱同步到Neo4j数据库"
                    e.presentation.isEnabled = true
                }
            }
        }
    }
}