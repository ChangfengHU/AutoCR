package com.vyibc.autocr.export

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.vyibc.autocr.graph.CodeGraph
import com.vyibc.autocr.model.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 图谱数据导出服务
 * 负责将本地图谱数据导出为各种格式文件
 */
@Service(Service.Level.PROJECT)
class GraphExportService(private val project: Project) {
    private val logger = LoggerFactory.getLogger(GraphExportService::class.java)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    
    /**
     * 导出图谱数据为JSON格式
     */
    fun exportToJson(codeGraph: CodeGraph, outputPath: String): ExportResult {
        return try {
            val startTime = System.currentTimeMillis()
            
            val exportData = JsonObject()
            
            // 基本信息
            val metadata = JsonObject()
            metadata.addProperty("projectName", project.name)
            metadata.addProperty("exportTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            metadata.addProperty("exportVersion", "1.0")
            exportData.add("metadata", metadata)
            
            // 统计信息
            val stats = JsonObject()
            val allNodes = codeGraph.getAllNodes()
            val allEdges = codeGraph.getAllEdges()
            
            val classes = allNodes.filterIsInstance<ClassNode>()
            val methods = allNodes.filterIsInstance<MethodNode>()
            
            stats.addProperty("totalNodes", allNodes.size)
            stats.addProperty("totalEdges", allEdges.size)
            stats.addProperty("totalClasses", classes.size)
            stats.addProperty("totalMethods", methods.size)
            
            // 按类型统计边
            val edgeTypeStats = JsonObject()
            allEdges.groupBy { it.edgeType }.forEach { (type, edges) ->
                edgeTypeStats.addProperty(type.name, edges.size)
            }
            stats.add("edgeTypeDistribution", edgeTypeStats)
            
            // 按块类型统计类
            val classBlockTypeStats = JsonObject()
            classes.groupBy { it.blockType }.forEach { (type, classList) ->
                classBlockTypeStats.addProperty(type.name, classList.size)
            }
            stats.add("classBlockTypeDistribution", classBlockTypeStats)
            
            // 按块类型统计方法
            val methodBlockTypeStats = JsonObject()
            methods.groupBy { it.blockType }.forEach { (type, methodList) ->
                methodBlockTypeStats.addProperty(type.name, methodList.size)
            }
            stats.add("methodBlockTypeDistribution", methodBlockTypeStats)
            
            exportData.add("statistics", stats)
            
            // 导出节点数据
            val nodesArray = JsonArray()
            
            // 导出类节点
            classes.forEach { classNode ->
                val classJson = JsonObject()
                classJson.addProperty("id", classNode.id)
                classJson.addProperty("type", "class")
                classJson.addProperty("className", classNode.className)
                classJson.addProperty("packageName", classNode.packageName)
                classJson.addProperty("blockType", classNode.blockType.name)
                classJson.addProperty("isInterface", classNode.isInterface)
                classJson.addProperty("isAbstract", classNode.isAbstract)
                classJson.addProperty("filePath", classNode.filePath)
                
                if (classNode.superClass != null) {
                    classJson.addProperty("superClass", classNode.superClass)
                }
                
                if (classNode.implementedInterfaces.isNotEmpty()) {
                    val interfacesArray = JsonArray()
                    classNode.implementedInterfaces.forEach { interfacesArray.add(it) }
                    classJson.add("implementedInterfaces", interfacesArray)
                }
                
                if (classNode.annotations.isNotEmpty()) {
                    val annotationsArray = JsonArray()
                    classNode.annotations.forEach { annotationsArray.add(it) }
                    classJson.add("annotations", annotationsArray)
                }
                
                nodesArray.add(classJson)
            }
            
            // 导出方法节点
            methods.forEach { methodNode ->
                val methodJson = JsonObject()
                methodJson.addProperty("id", methodNode.id)
                methodJson.addProperty("type", "method")
                methodJson.addProperty("methodName", methodNode.methodName)
                methodJson.addProperty("signature", methodNode.signature)
                methodJson.addProperty("returnType", methodNode.returnType)
                methodJson.addProperty("blockType", methodNode.blockType.name)
                methodJson.addProperty("filePath", methodNode.filePath)
                methodJson.addProperty("lineNumber", methodNode.lineNumber)
                methodJson.addProperty("startLineNumber", methodNode.startLineNumber)
                methodJson.addProperty("endLineNumber", methodNode.endLineNumber)
                methodJson.addProperty("cyclomaticComplexity", methodNode.cyclomaticComplexity)
                methodJson.addProperty("linesOfCode", methodNode.linesOfCode)
                methodJson.addProperty("lastModified", methodNode.lastModified.toString())
                
                // 参数类型
                if (methodNode.paramTypes.isNotEmpty()) {
                    val paramTypesArray = JsonArray()
                    methodNode.paramTypes.forEach { paramTypesArray.add(it) }
                    methodJson.add("paramTypes", paramTypesArray)
                }
                
                // 注解
                if (methodNode.annotations.isNotEmpty()) {
                    val annotationsArray = JsonArray()
                    methodNode.annotations.forEach { annotationsArray.add(it) }
                    methodJson.add("annotations", annotationsArray)
                }
                
                nodesArray.add(methodJson)
            }
            
            exportData.add("nodes", nodesArray)
            
            // 导出边数据
            val edgesArray = JsonArray()
            allEdges.forEach { edge ->
                val edgeJson = JsonObject()
                edgeJson.addProperty("id", edge.id)
                edgeJson.addProperty("sourceId", edge.sourceId)
                edgeJson.addProperty("targetId", edge.targetId)
                edgeJson.addProperty("edgeType", edge.edgeType.name)
                
                // 根据边类型添加特定属性
                when (edge) {
                    is CallsEdge -> {
                        edgeJson.addProperty("callType", edge.callType)
                        edgeJson.addProperty("lineNumber", edge.lineNumber)
                    }
                    is InheritsEdge -> {
                        // 继承边的特定属性
                    }
                    is ImplementsEdge -> {
                        // 实现边的特定属性
                    }
                    is ContainsEdge -> {
                        // 包含边的特定属性
                    }
                    is DataFlowEdge -> {
                        // 数据流边的特定属性
                    }
                }
                
                edgesArray.add(edgeJson)
            }
            
            exportData.add("edges", edgesArray)
            
            // 写入文件
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(gson.toJson(exportData))
            
            val duration = System.currentTimeMillis() - startTime
            
            logger.info("Successfully exported graph data to: $outputPath")
            logger.info("Export statistics: {} nodes, {} edges, duration: {}ms", 
                allNodes.size, allEdges.size, duration)
            
            ExportResult(
                success = true,
                filePath = outputPath,
                totalNodes = allNodes.size,
                totalEdges = allEdges.size,
                fileSize = outputFile.length(),
                duration = duration,
                message = "图谱数据成功导出到 $outputPath"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to export graph data to JSON", e)
            ExportResult(
                success = false,
                filePath = outputPath,
                totalNodes = 0,
                totalEdges = 0,
                fileSize = 0,
                duration = 0,
                message = "导出失败: ${e.message}"
            )
        }
    }
    
    /**
     * 导出图谱数据为Cypher格式（用于Neo4j导入）
     */
    fun exportToCypher(codeGraph: CodeGraph, outputPath: String): ExportResult {
        return try {
            val startTime = System.currentTimeMillis()
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            
            val writer = outputFile.bufferedWriter()
            
            writer.use { w ->
                w.write("// AutoCR 知识图谱 Cypher 导出文件\n")
                w.write("// 项目: ${project.name}\n")
                w.write("// 导出时间: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\n\n")
                
                // 清空数据库
                w.write("// 清空现有数据\n")
                w.write("MATCH (n) DETACH DELETE n;\n\n")
                
                val allNodes = codeGraph.getAllNodes()
                val allEdges = codeGraph.getAllEdges()
                
                // 创建类节点
                w.write("// 创建类节点\n")
                allNodes.filterIsInstance<ClassNode>().forEach { classNode ->
                    w.write("CREATE (c:Class {\n")
                    w.write("  id: '${classNode.id}',\n")
                    w.write("  className: '${classNode.className}',\n")
                    w.write("  packageName: '${classNode.packageName}',\n")
                    w.write("  blockType: '${classNode.blockType.name}',\n")
                    w.write("  isInterface: ${classNode.isInterface},\n")
                    w.write("  isAbstract: ${classNode.isAbstract},\n")
                    w.write("  filePath: '${classNode.filePath}'\n")
                    w.write("});\n")
                }
                
                w.write("\n// 创建方法节点\n")
                allNodes.filterIsInstance<MethodNode>().forEach { methodNode ->
                    w.write("CREATE (m:Method {\n")
                    w.write("  id: '${methodNode.id}',\n")
                    w.write("  methodName: '${methodNode.methodName}',\n")
                    w.write("  signature: '${methodNode.signature}',\n")
                    w.write("  returnType: '${methodNode.returnType}',\n")
                    w.write("  blockType: '${methodNode.blockType.name}',\n")
                    w.write("  filePath: '${methodNode.filePath}',\n")
                    w.write("  lineNumber: ${methodNode.lineNumber},\n")
                    w.write("  cyclomaticComplexity: ${methodNode.cyclomaticComplexity},\n")
                    w.write("  linesOfCode: ${methodNode.linesOfCode}\n")
                    w.write("});\n")
                }
                
                // 创建关系
                w.write("\n// 创建关系\n")
                allEdges.forEach { edge ->
                    val relationshipType = when (edge.edgeType) {
                        EdgeType.CALLS -> "CALLS"
                        EdgeType.INHERITS -> "INHERITS"
                        EdgeType.IMPLEMENTS -> "IMPLEMENTS"
                        EdgeType.CONTAINS -> "CONTAINS"
                        EdgeType.DATA_FLOW -> "DATA_FLOW"
                    }
                    
                    w.write("MATCH (source {id: '${edge.sourceId}'}), (target {id: '${edge.targetId}'})\n")
                    w.write("CREATE (source)-[:$relationshipType")
                    
                    // 添加关系属性
                    when (edge) {
                        is CallsEdge -> {
                            w.write(" {callType: '${edge.callType}', lineNumber: ${edge.lineNumber}}")
                        }
                        is ContainsEdge -> {
                            // 包含边属性
                        }
                        is DataFlowEdge -> {
                            // 数据流边属性
                        }
                        is ImplementsEdge -> {
                            // 实现边属性
                        }
                        is InheritsEdge -> {
                            // 继承边属性
                        }
                    }
                    
                    w.write("]->(target);\n")
                }
                
                w.write("\n// 创建索引\n")
                w.write("CREATE INDEX IF NOT EXISTS FOR (c:Class) ON (c.id);\n")
                w.write("CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.id);\n")
                w.write("CREATE INDEX IF NOT EXISTS FOR (c:Class) ON (c.packageName);\n")
                w.write("CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.methodName);\n")
            }
            
            val duration = System.currentTimeMillis() - startTime
            val allNodes = codeGraph.getAllNodes()
            val allEdges = codeGraph.getAllEdges()
            
            logger.info("Successfully exported graph data to Cypher format: $outputPath")
            
            ExportResult(
                success = true,
                filePath = outputPath,
                totalNodes = allNodes.size,
                totalEdges = allEdges.size,
                fileSize = outputFile.length(),
                duration = duration,
                message = "Cypher脚本成功导出到 $outputPath"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to export graph data to Cypher format", e)
            ExportResult(
                success = false,
                filePath = outputPath,
                totalNodes = 0,
                totalEdges = 0,
                fileSize = 0,
                duration = 0,
                message = "Cypher导出失败: ${e.message}"
            )
        }
    }
    
    /**
     * 生成图谱摘要报告
     */
    fun generateSummaryReport(codeGraph: CodeGraph, outputPath: String): ExportResult {
        return try {
            val startTime = System.currentTimeMillis()
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            
            val writer = outputFile.bufferedWriter()
            
            writer.use { w ->
                w.write("# AutoCR 知识图谱分析报告\n\n")
                w.write("**项目名称**: ${project.name}\n")
                w.write("**生成时间**: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
                
                val allNodes = codeGraph.getAllNodes()
                val allEdges = codeGraph.getAllEdges()
                val classes = allNodes.filterIsInstance<ClassNode>()
                val methods = allNodes.filterIsInstance<MethodNode>()
                
                w.write("## 📊 统计概览\n\n")
                w.write("| 指标 | 数量 |\n")
                w.write("|------|------|\n")
                w.write("| 总节点数 | ${allNodes.size} |\n")
                w.write("| 总边数 | ${allEdges.size} |\n")
                w.write("| 类数量 | ${classes.size} |\n")
                w.write("| 方法数量 | ${methods.size} |\n\n")
                
                // 按包统计
                w.write("## 📦 包分布\n\n")
                val packageStats = classes.groupBy { it.packageName }
                w.write("| 包名 | 类数量 |\n")
                w.write("|------|--------|\n")
                packageStats.forEach { (pkg, classList) ->
                    w.write("| $pkg | ${classList.size} |\n")
                }
                w.write("\n")
                
                // 按块类型统计
                w.write("## 🏗️ 架构分布\n\n")
                val blockTypeStats = classes.groupBy { it.blockType }
                w.write("| 架构类型 | 类数量 |\n")
                w.write("|----------|--------|\n")
                blockTypeStats.forEach { (type, classList) ->
                    w.write("| ${type.name} | ${classList.size} |\n")
                }
                w.write("\n")
                
                // 复杂度分析
                w.write("## 🔍 复杂度分析\n\n")
                val complexities = methods.map { it.cyclomaticComplexity }
                if (complexities.isNotEmpty()) {
                    val avgComplexity = complexities.average()
                    val maxComplexity = complexities.maxOrNull() ?: 0
                    val highComplexityMethods = methods.filter { it.cyclomaticComplexity > 10 }
                    
                    w.write("- **平均复杂度**: %.2f\n".format(avgComplexity))
                    w.write("- **最高复杂度**: $maxComplexity\n")
                    w.write("- **高复杂度方法数**: ${highComplexityMethods.size}\n\n")
                    
                    if (highComplexityMethods.isNotEmpty()) {
                        w.write("### 🚨 高复杂度方法 (复杂度 > 10)\n\n")
                        w.write("| 方法名 | 复杂度 | 行数 | 文件 |\n")
                        w.write("|--------|--------|------|------|\n")
                        highComplexityMethods.sortedByDescending { it.cyclomaticComplexity }.take(10).forEach { method ->
                            w.write("| ${method.methodName} | ${method.cyclomaticComplexity} | ${method.linesOfCode} | ${method.filePath.substringAfterLast("/")} |\n")
                        }
                        w.write("\n")
                    }
                }
                
                // 关系统计
                w.write("## 🔗 关系分析\n\n")
                val edgeTypeStats = allEdges.groupBy { it.edgeType }
                w.write("| 关系类型 | 数量 |\n")
                w.write("|----------|------|\n")
                edgeTypeStats.forEach { (type, edgeList) ->
                    w.write("| ${type.name} | ${edgeList.size} |\n")
                }
                w.write("\n")
                
                // 热点分析
                w.write("## 🔥 热点分析\n\n")
                val methodCallCounts = mutableMapOf<String, Int>()
                allEdges.filterIsInstance<CallsEdge>().forEach { edge ->
                    methodCallCounts[edge.targetId] = methodCallCounts.getOrDefault(edge.targetId, 0) + 1
                }
                
                val topCalledMethods = methodCallCounts.entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .mapNotNull { entry ->
                        methods.find { it.id == entry.key }?.let { it to entry.value }
                    }
                
                if (topCalledMethods.isNotEmpty()) {
                    w.write("### 📞 被调用最多的方法\n\n")
                    w.write("| 方法名 | 被调用次数 | 文件 |\n")
                    w.write("|--------|------------|------|\n")
                    topCalledMethods.forEach { (method, count) ->
                        w.write("| ${method.methodName} | $count | ${method.filePath.substringAfterLast("/")} |\n")
                    }
                    w.write("\n")
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            val allNodes = codeGraph.getAllNodes()
            val allEdges = codeGraph.getAllEdges()
            
            logger.info("Successfully generated summary report: $outputPath")
            
            ExportResult(
                success = true,
                filePath = outputPath,
                totalNodes = allNodes.size,
                totalEdges = allEdges.size,
                fileSize = outputFile.length(),
                duration = duration,
                message = "分析报告成功生成到 $outputPath"
            )
            
        } catch (e: Exception) {
            logger.error("Failed to generate summary report", e)
            ExportResult(
                success = false,
                filePath = outputPath,
                totalNodes = 0,
                totalEdges = 0,
                fileSize = 0,
                duration = 0,
                message = "报告生成失败: ${e.message}"
            )
        }
    }
    
    companion object {
        fun getInstance(project: Project): GraphExportService {
            return project.service()
        }
    }
}

/**
 * 导出结果
 */
data class ExportResult(
    val success: Boolean,
    val filePath: String,
    val totalNodes: Int,
    val totalEdges: Int,
    val fileSize: Long,
    val duration: Long,
    val message: String
)