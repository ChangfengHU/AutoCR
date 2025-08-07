package com.vyibc.autocr.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.vyibc.autocr.analysis.KnowledgeGraphBuilder
import com.vyibc.autocr.export.Neo4jCypherGenerator
import com.vyibc.autocr.model.KnowledgeGraph
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JOptionPane

/**
 * 生成知识图谱并创建Neo4j插入语句的Action
 */
class GenerateKnowledgeGraphAction : AnAction(
    "生成知识图谱数据",
    "扫描项目结构，分析调用关系，生成Neo4j导入脚本",
    null
) {
    
    private val logger = LoggerFactory.getLogger(GenerateKnowledgeGraphAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 显示确认对话框
        val confirmed = JOptionPane.showConfirmDialog(
            null,
            """
            <html>
            <h3>🔍 生成知识图谱数据</h3>
            <p>此操作将：</p>
            <ul>
            <li>• 扫描项目中的所有Java类</li>
            <li>• 识别类的层级归属（Controller、Service等）</li>
            <li>• 分析方法间的调用关系</li>
            <li>• 生成完整的Neo4j Cypher导入脚本</li>
            <li>• 创建项目结构分析报告</li>
            </ul>
            <br>
            <p><b>⏱️ 预计耗时：2-10分钟（根据项目大小）</b></p>
            <p>是否继续？</p>
            </html>
            """.trimIndent(),
            "确认生成知识图谱",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        ) == JOptionPane.YES_OPTION
        
        if (!confirmed) return
        
        // 使用进度条显示构建过程
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, 
            "正在构建知识图谱...", 
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "正在初始化知识图谱构建器..."
                    indicator.fraction = 0.0
                    
                    // 1. 构建知识图谱
                    val builder = KnowledgeGraphBuilder(project)
                    val graph = builder.buildGraph(indicator)
                    
                    indicator.text = "正在生成Neo4j导入脚本..."
                    indicator.fraction = 0.8
                    
                    // 2. 生成文件
                    val files = generateOutputFiles(project, graph)
                    
                    indicator.text = "正在保存文件..."
                    indicator.fraction = 0.95
                    
                    // 3. 打开生成的文件
                    ApplicationManager.getApplication().invokeLater {
                        openGeneratedFiles(project, files)
                        showSuccessDialog(graph, files)
                    }
                    
                    indicator.fraction = 1.0
                    
                } catch (e: Exception) {
                    logger.error("Failed to generate knowledge graph", e)
                    
                    ApplicationManager.getApplication().invokeLater {
                        showErrorDialog(e)
                    }
                }
            }
        })
    }
    
    private fun generateOutputFiles(project: Project, graph: KnowledgeGraph): GeneratedFiles {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val basePath = project.basePath ?: ""
        
        // 1. 生成 Neo4j Cypher 脚本
        val cypherGenerator = Neo4jCypherGenerator()
        val cypherScript = cypherGenerator.generateCypherScript(graph)
        val cypherFile = File(basePath, "AutoCR_Neo4j_Import_$timestamp.cypher")
        cypherFile.writeText(cypherScript)
        
        // 2. 生成详细分析报告
        val reportContent = generateAnalysisReport(graph)
        val reportFile = File(basePath, "AutoCR_Knowledge_Analysis_$timestamp.md")
        reportFile.writeText(reportContent)
        
        // 3. 生成JSON数据文件（用于其他工具）
        val jsonContent = generateJsonData(graph)
        val jsonFile = File(basePath, "AutoCR_Graph_Data_$timestamp.json")
        jsonFile.writeText(jsonContent)
        
        return GeneratedFiles(cypherFile, reportFile, jsonFile)
    }
    
    private fun generateAnalysisReport(graph: KnowledgeGraph): String {
        val stats = graph.getStatistics()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        
        val report = StringBuilder()
        report.appendLine("# 🔍 AutoCR 知识图谱分析报告")
        report.appendLine()
        report.appendLine("**项目名称**: ${graph.metadata.projectName}")
        report.appendLine("**生成时间**: $timestamp")
        report.appendLine("**版本**: ${graph.metadata.version}")
        report.appendLine()
        report.appendLine("---")
        report.appendLine()
        
        // 总体统计
        report.appendLine("## 📊 总体统计")
        report.appendLine()
        report.appendLine("| 指标 | 数量 |")
        report.appendLine("|------|------|")
        report.appendLine("| **总类数** | ${stats.totalClasses} |")
        report.appendLine("| **总方法数** | ${stats.totalMethods} |")
        report.appendLine("| **调用关系数** | ${stats.totalEdges} |")
        report.appendLine("| **平均方法数/类** | ${"%.2f".format(stats.avgMethodsPerClass)} |")
        report.appendLine()
        
        // 层级分布
        report.appendLine("## 🏗️ 架构层级分布")
        report.appendLine()
        report.appendLine("| 层级 | 类数量 | 占比 |")
        report.appendLine("|------|--------|------|")
        stats.layerDistribution.filter { it.value > 0 }.forEach { (layer, count) ->
            val percentage = if (stats.totalClasses > 0) "%.1f%%".format(100.0 * count / stats.totalClasses) else "0%"
            report.appendLine("| ${layer.emoji} **${layer.displayName}** | $count | $percentage |")
        }
        report.appendLine()
        
        // 调用类型分布
        report.appendLine("## 🔗 调用关系类型分布")
        report.appendLine()
        report.appendLine("| 调用类型 | 数量 | 占比 |")
        report.appendLine("|----------|------|------|")
        stats.callTypeDistribution.filter { it.value > 0 }.forEach { (callType, count) ->
            val percentage = if (stats.totalEdges > 0) "%.1f%%".format(100.0 * count / stats.totalEdges) else "0%"
            report.appendLine("| **${callType.displayName}** | $count | $percentage |")
        }
        report.appendLine()
        
        // 复杂度分析
        report.appendLine("## 📈 复杂度分析")
        report.appendLine()
        
        // 最复杂的类
        val complexClasses = graph.classes.sortedByDescending { it.methodCount }.take(10)
        report.appendLine("### 方法数最多的类")
        report.appendLine("| 类名 | 包名 | 层级 | 方法数 |")
        report.appendLine("|------|------|------|--------|")
        complexClasses.forEach { classBlock ->
            report.appendLine("| `${classBlock.name}` | `${classBlock.packageName}` | ${classBlock.layer.displayName} | ${classBlock.methodCount} |")
        }
        report.appendLine()
        
        // 被调用最多的方法
        val methodCallCounts = graph.methods.map { method ->
            val incomingCalls = graph.getIncomingEdges(method.id).size
            method to incomingCalls
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(10)
        
        if (methodCallCounts.isNotEmpty()) {
            report.appendLine("### 被调用最频繁的方法")
            report.appendLine("| 方法名 | 所属类 | 被调用次数 |")
            report.appendLine("|--------|--------|------------|")
            methodCallCounts.forEach { (method, callCount) ->
                val className = graph.getClassById(method.classId)?.name ?: "Unknown"
                report.appendLine("| `${method.name}` | `$className` | $callCount |")
            }
            report.appendLine()
        }
        
        // 跨层调用分析
        report.appendLine("## 🔄 跨层调用分析")
        report.appendLine()
        val crossLayerCalls = analyzeCrossLayerCalls(graph)
        if (crossLayerCalls.isNotEmpty()) {
            report.appendLine("| 调用方向 | 次数 | 说明 |")
            report.appendLine("|----------|------|------|")
            crossLayerCalls.forEach { (direction, count) ->
                val explanation = explainCrossLayerCall(direction)
                report.appendLine("| $direction | $count | $explanation |")
            }
        } else {
            report.appendLine("未发现跨层调用关系")
        }
        report.appendLine()
        
        // Neo4j 使用建议
        report.appendLine("## 💡 Neo4j 使用建议")
        report.appendLine()
        report.appendLine("### 导入数据")
        report.appendLine("1. 确保Neo4j服务正在运行")
        report.appendLine("2. 在Neo4j Browser中执行生成的Cypher脚本")
        report.appendLine("3. 或使用命令行：`cypher-shell -f AutoCR_Neo4j_Import_时间戳.cypher`")
        report.appendLine()
        
        report.appendLine("### 推荐查询")
        report.appendLine("```cypher")
        report.appendLine("// 查看所有层级")
        report.appendLine("MATCH (c:Class)")
        report.appendLine("RETURN c.layer, count(c) as count")
        report.appendLine("ORDER BY count DESC")
        report.appendLine()
        report.appendLine("// 查找调用链路")
        report.appendLine("MATCH path = (start:Method)-[:CALLS*1..5]->(end:Method)")
        report.appendLine("WHERE start.name = '你的方法名'")
        report.appendLine("RETURN path")
        report.appendLine()
        report.appendLine("// 分析类依赖关系")
        report.appendLine("MATCH (from:Class)-[:CONTAINS]->()-[:CALLS]->()<-[:CONTAINS]-(to:Class)")
        report.appendLine("WHERE from <> to")
        report.appendLine("RETURN from.name, to.name, count(*) as callCount")
        report.appendLine("ORDER BY callCount DESC")
        report.appendLine("```")
        report.appendLine()
        
        report.appendLine("---")
        report.appendLine("*本报告由 AutoCR 插件自动生成*")
        
        return report.toString()
    }
    
    private fun generateJsonData(graph: KnowledgeGraph): String {
        // 简单的JSON生成，实际项目中可以使用Jackson或Gson
        val json = StringBuilder()
        json.appendLine("{")
        json.appendLine("  \"metadata\": {")
        json.appendLine("    \"projectName\": \"${graph.metadata.projectName}\",")
        json.appendLine("    \"buildTime\": ${graph.metadata.buildTime},")
        json.appendLine("    \"version\": \"${graph.metadata.version}\"")
        json.appendLine("  },")
        json.appendLine("  \"statistics\": {")
        val stats = graph.getStatistics()
        json.appendLine("    \"totalClasses\": ${stats.totalClasses},")
        json.appendLine("    \"totalMethods\": ${stats.totalMethods},")
        json.appendLine("    \"totalEdges\": ${stats.totalEdges},")
        json.appendLine("    \"avgMethodsPerClass\": ${stats.avgMethodsPerClass}")
        json.appendLine("  },")
        json.appendLine("  \"layerDistribution\": {")
        stats.layerDistribution.filter { it.value > 0 }.let { dist ->
            dist.entries.forEachIndexed { index, (layer, count) ->
                val comma = if (index < dist.size - 1) "," else ""
                json.appendLine("    \"${layer.name}\": $count$comma")
            }
        }
        json.appendLine("  }")
        json.appendLine("}")
        return json.toString()
    }
    
    private fun analyzeCrossLayerCalls(graph: KnowledgeGraph): Map<String, Int> {
        val crossLayerCalls = mutableMapOf<String, Int>()
        
        graph.edges.forEach { edge ->
            val fromClass = graph.getClassById(edge.fromClassId)
            val toClass = graph.getClassById(edge.toClassId)
            
            if (fromClass != null && toClass != null && fromClass.layer != toClass.layer) {
                val direction = "${fromClass.layer.displayName} → ${toClass.layer.displayName}"
                crossLayerCalls[direction] = crossLayerCalls.getOrDefault(direction, 0) + 1
            }
        }
        
        return crossLayerCalls.toList().sortedByDescending { it.second }.toMap()
    }
    
    private fun explainCrossLayerCall(direction: String): String {
        return when {
            direction.contains("Controller") && direction.contains("Service") -> "✅ 正常"
            direction.contains("Service") && direction.contains("Repository") -> "✅ 正常"
            direction.contains("Service") && direction.contains("Mapper") -> "✅ 正常"
            direction.contains("Controller") && direction.contains("Repository") -> "⚠️ 应通过Service层"
            direction.contains("Controller") && direction.contains("Mapper") -> "⚠️ 应通过Service层"
            direction.contains("Repository") && direction.contains("Service") -> "❌ 反向依赖"
            direction.contains("Service") && direction.contains("Controller") -> "❌ 反向依赖"
            else -> "❓ 需要检查"
        }
    }
    
    private fun openGeneratedFiles(project: Project, files: GeneratedFiles) {
        val virtualFiles = listOfNotNull(
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(files.cypherFile),
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(files.reportFile)
        )
        
        virtualFiles.forEach { virtualFile ->
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }
    }
    
    private fun showSuccessDialog(graph: KnowledgeGraph, files: GeneratedFiles) {
        val stats = graph.getStatistics()
        
        JOptionPane.showMessageDialog(
            null,
            """
            <html>
            <h3>✅ 知识图谱生成成功！</h3>
            <br>
            <p><b>📊 统计结果：</b></p>
            <table>
            <tr><td>• 类数量: </td><td><b>${stats.totalClasses}</b></td></tr>
            <tr><td>• 方法数量: </td><td><b>${stats.totalMethods}</b></td></tr>
            <tr><td>• 调用关系: </td><td><b>${stats.totalEdges}</b></td></tr>
            </table>
            <br>
            <p><b>📁 生成的文件：</b></p>
            <ul>
            <li><b>Neo4j脚本</b>: ${files.cypherFile.name}</li>
            <li><b>分析报告</b>: ${files.reportFile.name}</li>
            <li><b>JSON数据</b>: ${files.jsonFile.name}</li>
            </ul>
            <br>
            <p>文件已自动打开，您可以：</p>
            <ul>
            <li>• 将Cypher脚本导入Neo4j数据库</li>
            <li>• 查看详细的项目结构分析</li>
            <li>• 使用JSON数据进行进一步分析</li>
            </ul>
            </html>
            """.trimIndent(),
            "知识图谱生成成功",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
    
    private fun showErrorDialog(error: Exception) {
        JOptionPane.showMessageDialog(
            null,
            """
            <html>
            <h3>❌ 知识图谱生成失败</h3>
            <p><b>错误信息：</b></p>
            <p style='color: red;'>${error.message}</p>
            <br>
            <p><b>可能的解决方案：</b></p>
            <ul>
            <li>• 确保项目已经完成编译</li>
            <li>• 检查项目中是否有语法错误</li>
            <li>• 尝试重新打开项目后再试</li>
            </ul>
            <p>详细错误信息请查看IDEA日志。</p>
            </html>
            """.trimIndent(),
            "生成失败",
            JOptionPane.ERROR_MESSAGE
        )
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
    
    private data class GeneratedFiles(
        val cypherFile: File,
        val reportFile: File,
        val jsonFile: File
    )
}