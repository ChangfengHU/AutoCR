package com.vyibc.autocr.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.vyibc.autocr.analysis.KnowledgeGraphBuilder
import com.vyibc.autocr.export.Neo4jCypherGenerator
import com.vyibc.autocr.settings.AutoCRSettingsState
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 导入知识图谱到Neo4j数据库的Action
 */
class ImportToNeo4jAction : AnAction() {
    
    private val logger = LoggerFactory.getLogger(ImportToNeo4jAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = AutoCRSettingsState.getInstance(project)
        
        logger.info("开始导入知识图谱到Neo4j，项目：${project.name}")
        
        // 检查Neo4j配置
        if (!settings.neo4jConfig.enabled) {
            val msg = "Neo4j未启用，请先在设置中启用并配置Neo4j连接"
            logger.warn(msg)
            showNotification(project, "Neo4j未启用", msg, NotificationType.WARNING)
            return
        }
        
        if (settings.neo4jConfig.uri.isBlank() || settings.neo4jConfig.username.isBlank()) {
            val msg = "Neo4j配置不完整，请先在设置中完成Neo4j配置"
            logger.warn(msg)
            showNotification(project, "Neo4j配置不完整", msg, NotificationType.WARNING)
            return
        }
        
        logger.info("Neo4j配置检查通过：${settings.neo4jConfig.uri}")

        // 在后台任务中执行导入
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "导入知识图谱到Neo4j", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    importToNeo4j(project, settings, indicator)
                } catch (e: Exception) {
                    logger.error("导入知识图谱失败", e)
                    showNotification(project, "导入失败", "导入过程中发生错误: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun importToNeo4j(project: Project, settings: AutoCRSettingsState, indicator: ProgressIndicator) {
        logger.info("开始分析项目结构...")
        indicator.text = "正在分析项目结构..."
        indicator.fraction = 0.1
        
        // 构建知识图谱
        val builder = KnowledgeGraphBuilder(project)
        val graph = builder.buildGraph(indicator)
        
        logger.info("分析完成，找到 ${graph.classes.size} 个类，${graph.methods.size} 个方法，${graph.edges.size} 条调用关系")
        
        // 详细记录前几个类的信息用于调试
        if (graph.classes.isNotEmpty()) {
            logger.info("前5个类的详细信息：")
            graph.classes.take(5).forEach { cls ->
                logger.info("  类：${cls.id} | ${cls.name} | ${cls.packageName} | ${cls.layer}")
            }
        }
        
        if (graph.methods.isNotEmpty()) {
            logger.info("前5个方法的详细信息：")
            graph.methods.take(5).forEach { method ->
                logger.info("  方法：${method.id} | ${method.name} | ${method.classId}")
            }
        }
        
        if (graph.classes.isEmpty()) {
            val msg = "项目中没有找到Java类，请确保项目包含Java源代码"
            logger.warn(msg)
            showNotification(project, "没有找到数据", msg, NotificationType.WARNING)
            return
        }
        
        indicator.text = "正在生成Cypher脚本..."
        indicator.fraction = 0.3
        
        // 生成Cypher脚本
        val cypherGenerator = Neo4jCypherGenerator()
        val cypherScript = cypherGenerator.generateCypherScript(graph)
        
        logger.info("Cypher脚本生成完成，长度：${cypherScript.length} 字符")
        
        // 保存脚本到文件以便调试
        val scriptFile = saveCypherScript(project, cypherScript)
        logger.info("Cypher脚本已保存到：$scriptFile")
        
        indicator.text = "正在连接Neo4j数据库..."
        indicator.fraction = 0.5
        
        // 连接Neo4j并执行导入
        val driver = GraphDatabase.driver(
            settings.neo4jConfig.uri,
            AuthTokens.basic(settings.neo4jConfig.username, settings.neo4jConfig.password)
        )
        
        try {
            driver.use { driverInstance ->
                indicator.text = "正在验证数据库连接..."
                driverInstance.verifyConnectivity()
                logger.info("Neo4j连接验证成功")
                
                indicator.text = "正在执行数据导入..."
                indicator.fraction = 0.6
                
                // 使用指定的数据库
                val sessionConfig = if (settings.neo4jConfig.database.isNotBlank()) {
                    org.neo4j.driver.SessionConfig.forDatabase(settings.neo4jConfig.database)
                } else {
                    org.neo4j.driver.SessionConfig.defaultConfig()
                }
                
                logger.info("使用数据库：${settings.neo4jConfig.database.ifBlank { "默认数据库" }}")
                
                driverInstance.session(sessionConfig).use { session ->
                    // 清理现有数据
                    logger.info("开始清理现有数据...")
                    val deleteResult = session.run("MATCH (n) DETACH DELETE n")
                    logger.info("清理结果：${deleteResult.consume().counters()}")
                    
                    // 创建类节点
                    logger.info("开始创建类节点...")
                    indicator.text = "正在创建类节点..."
                    indicator.fraction = 0.65
                    
                    graph.classes.chunked(25).forEachIndexed { chunkIndex, chunk ->
                        logger.info("处理类节点批次 ${chunkIndex + 1}/${(graph.classes.size + 24) / 25}，包含 ${chunk.size} 个类")
                        
                        val query = """
                            UNWIND ${'$'}classes AS class
                            CREATE (:Class {
                                id: class.id,
                                name: class.name,
                                qualifiedName: class.qualifiedName,
                                package: class.package,
                                layer: class.layer,
                                layerDisplay: class.layerDisplay,
                                emoji: class.emoji,
                                annotations: class.annotations,
                                superClass: class.superClass,
                                interfaces: class.interfaces,
                                isAbstract: class.isAbstract,
                                isInterface: class.isInterface,
                                methodCount: class.methodCount,
                                filePath: class.filePath
                            })
                        """.trimIndent()
                        
                        val classData = chunk.map { classBlock ->
                            mapOf(
                                "id" to classBlock.id,
                                "name" to classBlock.name,
                                "qualifiedName" to classBlock.qualifiedName,
                                "package" to classBlock.packageName,
                                "layer" to classBlock.layer.name,
                                "layerDisplay" to classBlock.layer.displayName,
                                "emoji" to classBlock.layer.emoji,
                                "annotations" to classBlock.annotations,
                                "superClass" to (classBlock.superClass ?: ""),
                                "interfaces" to classBlock.interfaces,
                                "isAbstract" to classBlock.isAbstract,
                                "isInterface" to classBlock.isInterface,
                                "methodCount" to classBlock.methodCount,
                                "filePath" to classBlock.filePath
                            )
                        }
                        
                        val result = session.run(query, mapOf("classes" to classData))
                        val counters = result.consume().counters()
                        logger.info("类节点批次 ${chunkIndex + 1} 创建了 ${counters.nodesCreated()} 个节点")
                    }
                    
                    // 创建方法节点
                    logger.info("开始创建方法节点...")
                    indicator.text = "正在创建方法节点..."
                    indicator.fraction = 0.75
                    
                    graph.methods.chunked(25).forEachIndexed { chunkIndex, chunk ->
                        val query = """
                            UNWIND ${'$'}methods AS method
                            CREATE (:Method {
                                id: method.id,
                                name: method.name,
                                signature: method.signature,
                                classId: method.classId,
                                returnType: method.returnType,
                                parameterTypes: method.parameterTypes,
                                parameterNames: method.parameterNames,
                                modifiers: method.modifiers,
                                lineNumber: method.lineNumber,
                                isConstructor: method.isConstructor,
                                isStatic: method.isStatic,
                                isPrivate: method.isPrivate,
                                isPublic: method.isPublic,
                                isAbstract: method.isAbstract,
                                visibility: method.visibility
                            })
                        """.trimIndent()
                        
                        val methodData = chunk.map { method ->
                            mapOf(
                                "id" to method.id,
                                "name" to method.name,
                                "signature" to method.signature,
                                "classId" to method.classId,
                                "returnType" to method.returnType,
                                "parameterTypes" to method.parameters.map { it.type },
                                "parameterNames" to method.parameters.map { it.name },
                                "modifiers" to method.modifiers.toList(),
                                "lineNumber" to method.lineNumber,
                                "isConstructor" to method.isConstructor,
                                "isStatic" to method.isStatic,
                                "isPrivate" to method.isPrivate,
                                "isPublic" to method.isPublic,
                                "isAbstract" to method.isAbstract,
                                "visibility" to method.getVisibility()
                            )
                        }
                        
                        val result = session.run(query, mapOf("methods" to methodData))
                        val counters = result.consume().counters()
                        logger.info("方法节点批次 ${chunkIndex + 1} 创建了 ${counters.nodesCreated()} 个节点")
                    }
                    
                    // 创建包含关系
                    logger.info("开始创建包含关系...")
                    indicator.text = "正在创建包含关系..."
                    indicator.fraction = 0.85
                    
                    val containsQuery = """
                        MATCH (c:Class), (m:Method)
                        WHERE c.id = m.classId
                        CREATE (c)-[:CONTAINS]->(m)
                    """.trimIndent()
                    val containsResult = session.run(containsQuery)
                    val containsCounters = containsResult.consume().counters()
                    logger.info("创建了 ${containsCounters.relationshipsCreated()} 个包含关系")
                    
                    // 创建调用关系
                    if (graph.edges.isNotEmpty()) {
                        logger.info("开始创建调用关系...")
                        indicator.text = "正在创建调用关系..."
                        indicator.fraction = 0.9
                        
                        graph.edges.chunked(50).forEachIndexed { chunkIndex, chunk ->
                            val query = """
                                UNWIND ${'$'}calls AS call
                                MATCH (from:Method {id: call.fromId})
                                MATCH (to:Method {id: call.toId})
                                CREATE (from)-[:CALLS {
                                    fromClass: call.fromClass,
                                    toClass: call.toClass,
                                    callType: call.callType,
                                    callTypeDisplay: call.callTypeDisplay,
                                    lineNumber: call.lineNumber,
                                    confidence: call.confidence
                                }]->(to)
                            """.trimIndent()
                            
                            val callData = chunk.map { edge ->
                                mapOf(
                                    "fromId" to edge.fromMethodId,
                                    "toId" to edge.toMethodId,
                                    "fromClass" to edge.fromClassId,
                                    "toClass" to edge.toClassId,
                                    "callType" to edge.callType.name,
                                    "callTypeDisplay" to edge.callType.displayName,
                                    "lineNumber" to edge.lineNumber,
                                    "confidence" to edge.confidence
                                )
                            }
                            
                            val result = session.run(query, mapOf("calls" to callData))
                            val counters = result.consume().counters()
                            logger.info("调用关系批次 ${chunkIndex + 1} 创建了 ${counters.relationshipsCreated()} 个关系")
                        }
                    }
                    
                    logger.info("数据导入完成")
                    
                    // 验证导入结果
                    indicator.text = "正在验证导入结果..."
                    indicator.fraction = 0.95
                    
                    val classCount = session.run("MATCH (c:Class) RETURN count(c) as count").single().get("count").asInt()
                    val methodCount = session.run("MATCH (m:Method) RETURN count(m) as count").single().get("count").asInt()
                    val relationCount = session.run("MATCH ()-[r:CALLS]->() RETURN count(r) as count").single().get("count").asInt()
                    
                    logger.info("导入验证完成：类 $classCount 个，方法 $methodCount 个，调用关系 $relationCount 个")
                    
                    indicator.text = "导入完成"
                    indicator.fraction = 1.0
                    
                    val successMsg = "已成功导入到Neo4j:\n" +
                            "• 类: $classCount 个\n" +
                            "• 方法: $methodCount 个\n" +
                            "• 调用关系: $relationCount 个\n" +
                            "• 数据库: ${settings.neo4jConfig.database.ifBlank { "默认" }}\n" +
                            "• 脚本已保存: $scriptFile"
                    
                    showNotification(
                        project,
                        "导入成功 🎉",
                        successMsg,
                        NotificationType.INFORMATION
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Neo4j操作失败", e)
            throw RuntimeException("Neo4j导入失败: ${e.message}", e)
        }
    }
    
    private fun saveCypherScript(project: Project, cypherScript: String): String {
        return try {
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
            val fileName = "AutoCR_Neo4j_Import_$timestamp.cypher"
            val file = File(project.basePath, fileName)
            
            file.writeText(cypherScript)
            logger.info("Cypher脚本已保存到：${file.absolutePath}")
            
            fileName
        } catch (e: Exception) {
            logger.error("保存Cypher脚本失败", e)
            "保存失败"
        }
    }

    private fun showNotification(project: Project, title: String, content: String, type: NotificationType) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AutoCR.Notifications")
                .createNotification(title, content, type)
                .notify(project)
        } catch (e: Exception) {
            logger.error("显示通知失败", e)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        e.presentation.isEnabledAndVisible = project != null
    }
}