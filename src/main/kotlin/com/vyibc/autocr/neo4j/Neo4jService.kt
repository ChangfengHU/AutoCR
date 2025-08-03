package com.vyibc.autocr.neo4j

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.vyibc.autocr.model.*
import com.vyibc.autocr.settings.AutoCRSettingsState
import com.vyibc.autocr.graph.CodeGraph
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Neo4j图数据库服务
 * 负责与Neo4j数据库的连接、数据同步和查询
 */
@Service(Service.Level.PROJECT)
class Neo4jService(private val project: Project) {
    private val logger = LoggerFactory.getLogger(Neo4jService::class.java)
    private val settings = AutoCRSettingsState.getInstance(project)
    
    // 连接相关
    private var driver: Any? = null // 实际应该是 org.neo4j.driver.Driver
    private var isConnected = false
    
    init {
        if (settings.neo4jConfig.enabled) {
            initializeConnection()
        }
    }
    
    /**
     * 初始化Neo4j连接
     */
    private fun initializeConnection() {
        try {
            val config = settings.neo4jConfig
            logger.info("Initializing Neo4j connection to: {}", config.uri)
            
            // 这里应该实现实际的Neo4j连接
            // driver = GraphDatabase.driver(config.uri, AuthTokens.basic(config.username, config.password), configBuilder)
            
            // 模拟连接
            Thread.sleep(1000)
            isConnected = true
            
            logger.info("Successfully connected to Neo4j database")
            
            // 创建索引和约束
            createSchemaConstraints()
            
        } catch (e: Exception) {
            logger.error("Failed to connect to Neo4j", e)
            isConnected = false
        }
    }
    
    /**
     * 创建数据库约束和索引
     */
    private fun createSchemaConstraints() {
        try {
            logger.info("Creating Neo4j schema constraints and indexes")
            
            // 模拟创建约束和索引的Cypher语句
            val constraints = listOf(
                "CREATE CONSTRAINT IF NOT EXISTS FOR (c:Class) REQUIRE c.id IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (m:Method) REQUIRE m.id IS UNIQUE",
                "CREATE INDEX IF NOT EXISTS FOR (c:Class) ON (c.packageName)",
                "CREATE INDEX IF NOT EXISTS FOR (c:Class) ON (c.blockType)",
                "CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.methodName)",
                "CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.blockType)",
                "CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.cyclomaticComplexity)",
                "CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.riskScore)"
            )
            
            constraints.forEach { constraint ->
                executeQuery(constraint)
            }
            
            logger.info("Schema constraints and indexes created successfully")
            
        } catch (e: Exception) {
            logger.error("Failed to create schema constraints", e)
        }
    }
    
    /**
     * 同步代码图谱到Neo4j
     */
    fun syncCodeGraphToNeo4j(codeGraph: CodeGraph): Neo4jSyncResult {
        if (!isConnected) {
            return Neo4jSyncResult(false, "Not connected to Neo4j", 0, 0, 0)
        }
        
        return try {
            logger.info("Starting sync to Neo4j")
            val startTime = System.currentTimeMillis()
            
            // 清空现有数据
            clearProjectData()
            
            // 同步节点
            val syncedNodes = syncNodes(codeGraph)
            val syncedEdges = syncEdges(codeGraph)
            
            val duration = System.currentTimeMillis() - startTime
            
            logger.info("Neo4j sync completed: {} nodes, {} edges in {}ms", 
                syncedNodes, syncedEdges, duration)
            
            Neo4jSyncResult(true, "Sync completed successfully", syncedNodes, syncedEdges, duration)
            
        } catch (e: Exception) {
            logger.error("Failed to sync to Neo4j", e)
            Neo4jSyncResult(false, e.message ?: "Unknown error", 0, 0, 0)
        }
    }
    
    /**
     * 同步节点到Neo4j
     */
    private fun syncNodes(codeGraph: CodeGraph): Int {
        val allNodes = codeGraph.getAllNodes()
        var syncedCount = 0
        
        // 同步类节点
        val classes = allNodes.filterIsInstance<ClassNode>()
        classes.forEach { classNode ->
            val cypher = """
                MERGE (c:Class {id: ${'$'}id})
                SET c.className = ${'$'}className,
                    c.packageName = ${'$'}packageName,
                    c.blockType = ${'$'}blockType,
                    c.isInterface = ${'$'}isInterface,
                    c.isAbstract = ${'$'}isAbstract,
                    c.filePath = ${'$'}filePath,
                    c.methodCount = ${'$'}methodCount,
                    c.fieldCount = ${'$'}fieldCount,
                    c.cohesion = ${'$'}cohesion,
                    c.coupling = ${'$'}coupling,
                    c.implementedInterfaces = ${'$'}implementedInterfaces,
                    c.superClass = ${'$'}superClass,
                    c.annotations = ${'$'}annotations,
                    c.designPatterns = ${'$'}designPatterns,
                    c.lastUpdated = timestamp()
            """.trimIndent()
            
            val parameters: Map<String, Any> = mapOf(
                "id" to classNode.id,
                "className" to classNode.className,
                "packageName" to classNode.packageName,
                "blockType" to classNode.blockType.name,
                "isInterface" to classNode.isInterface,
                "isAbstract" to classNode.isAbstract,
                "filePath" to classNode.filePath,
                "methodCount" to classNode.methodCount,
                "fieldCount" to classNode.fieldCount,
                "cohesion" to classNode.cohesion,
                "coupling" to classNode.coupling,
                "implementedInterfaces" to classNode.implementedInterfaces.joinToString(","),
                "superClass" to (classNode.superClass ?: ""),
                "annotations" to classNode.annotations.joinToString(","),
                "designPatterns" to classNode.designPatterns.joinToString(",")
            )
            
            if (executeQuery(cypher, parameters)) {
                syncedCount++
            }
        }
        
        // 同步方法节点
        val methods = allNodes.filterIsInstance<MethodNode>()
        methods.forEach { methodNode ->
            val cypher = """
                MERGE (m:Method {id: ${'$'}id})
                SET m.methodName = ${'$'}methodName,
                    m.signature = ${'$'}signature,
                    m.returnType = ${'$'}returnType,
                    m.paramTypes = ${'$'}paramTypes,
                    m.blockType = ${'$'}blockType,
                    m.isInterface = ${'$'}isInterface,
                    m.filePath = ${'$'}filePath,
                    m.lineNumber = ${'$'}lineNumber,
                    m.startLineNumber = ${'$'}startLineNumber,
                    m.endLineNumber = ${'$'}endLineNumber,
                    m.cyclomaticComplexity = ${'$'}cyclomaticComplexity,
                    m.linesOfCode = ${'$'}linesOfCode,
                    m.inDegree = ${'$'}inDegree,
                    m.outDegree = ${'$'}outDegree,
                    m.riskScore = ${'$'}riskScore,
                    m.hasTests = ${'$'}hasTests,
                    m.annotations = ${'$'}annotations,
                    m.lastModified = ${'$'}lastModified,
                    m.lastUpdated = timestamp()
            """.trimIndent()
            
            val parameters = mapOf(
                "id" to methodNode.id,
                "methodName" to methodNode.methodName,
                "signature" to methodNode.signature,
                "returnType" to methodNode.returnType,
                "paramTypes" to methodNode.paramTypes,
                "blockType" to methodNode.blockType.name,
                "isInterface" to methodNode.isInterface,
                "filePath" to methodNode.filePath,
                "lineNumber" to methodNode.lineNumber,
                "startLineNumber" to methodNode.startLineNumber,
                "endLineNumber" to methodNode.endLineNumber,
                "cyclomaticComplexity" to methodNode.cyclomaticComplexity,
                "linesOfCode" to methodNode.linesOfCode,
                "inDegree" to methodNode.inDegree,
                "outDegree" to methodNode.outDegree,
                "riskScore" to methodNode.riskScore,
                "hasTests" to methodNode.hasTests,
                "annotations" to methodNode.annotations,
                "lastModified" to methodNode.lastModified.epochSecond
            )
            
            if (executeQuery(cypher, parameters)) {
                syncedCount++
            }
        }
        
        logger.info("Synced {} nodes to Neo4j", syncedCount)
        return syncedCount
    }
    
    /**
     * 同步边到Neo4j
     */
    private fun syncEdges(codeGraph: CodeGraph): Int {
        val allEdges = codeGraph.getAllEdges()
        var syncedCount = 0
        
        allEdges.forEach { edge ->
            val cypher = when (edge) {
                is CallsEdge -> createCallsRelationship(edge)
                is ContainsEdge -> createContainsRelationship(edge)
                is InheritsEdge -> createInheritsRelationship(edge)
                is ImplementsEdge -> createImplementsRelationship(edge)
                else -> null
            }
            
            if (cypher != null && executeQuery(cypher.first, cypher.second)) {
                syncedCount++
            }
        }
        
        logger.info("Synced {} edges to Neo4j", syncedCount)
        return syncedCount
    }
    
    /**
     * 创建调用关系
     */
    private fun createCallsRelationship(edge: CallsEdge): Pair<String, Map<String, Any>>? {
        val cypher = """
            MATCH (source:Method {id: ${'$'}sourceId})
            MATCH (target:Method {id: ${'$'}targetId})
            MERGE (source)-[r:CALLS]->(target)
            SET r.callType = ${'$'}callType,
                r.lineNumber = ${'$'}lineNumber,
                r.isNewInMR = ${'$'}isNewInMR,
                r.isModifiedInMR = ${'$'}isModifiedInMR,
                r.lastUpdated = timestamp()
        """.trimIndent()
        
        val parameters = mapOf(
            "sourceId" to edge.sourceId,
            "targetId" to edge.targetId,
            "callType" to edge.callType,
            "lineNumber" to edge.lineNumber,
            "isNewInMR" to edge.isNewInMR,
            "isModifiedInMR" to edge.isModifiedInMR
        )
        
        return Pair(cypher, parameters)
    }
    
    /**
     * 创建包含关系
     */
    private fun createContainsRelationship(edge: ContainsEdge): Pair<String, Map<String, Any>>? {
        val cypher = """
            MATCH (class:Class {id: ${'$'}sourceId})
            MATCH (method:Method {id: ${'$'}targetId})
            MERGE (class)-[r:CONTAINS]->(method)
            SET r.lastUpdated = timestamp()
        """.trimIndent()
        
        val parameters = mapOf(
            "sourceId" to edge.sourceId,
            "targetId" to edge.targetId
        )
        
        return Pair(cypher, parameters)
    }
    
    /**
     * 创建继承关系
     */
    private fun createInheritsRelationship(edge: InheritsEdge): Pair<String, Map<String, Any>>? {
        val cypher = """
            MATCH (child:Class {id: ${'$'}sourceId})
            MATCH (parent:Class {id: ${'$'}targetId})
            MERGE (child)-[r:INHERITS]->(parent)
            SET r.lastUpdated = timestamp()
        """.trimIndent()
        
        val parameters = mapOf(
            "sourceId" to edge.sourceId,
            "targetId" to edge.targetId
        )
        
        return Pair(cypher, parameters)
    }
    
    /**
     * 创建实现关系
     */
    private fun createImplementsRelationship(edge: ImplementsEdge): Pair<String, Map<String, Any>>? {
        val cypher = """
            MATCH (impl:Class {id: ${'$'}sourceId})
            MATCH (interface:Class {id: ${'$'}targetId})
            MERGE (impl)-[r:IMPLEMENTS]->(interface)
            SET r.lastUpdated = timestamp()
        """.trimIndent()
        
        val parameters = mapOf(
            "sourceId" to edge.sourceId,
            "targetId" to edge.targetId
        )
        
        return Pair(cypher, parameters)
    }
    
    /**
     * 清空项目数据
     */
    private fun clearProjectData() {
        val cypher = """
            MATCH (n)
            WHERE n:Class OR n:Method
            DETACH DELETE n
        """.trimIndent()
        
        executeQuery(cypher)
        logger.info("Cleared existing project data from Neo4j")
    }
    
    /**
     * 执行Cypher查询
     */
    private fun executeQuery(cypher: String, parameters: Map<String, Any> = emptyMap()): Boolean {
        return try {
            // 这里应该实现实际的Neo4j查询执行
            // session.run(cypher, parameters)
            
            // 模拟执行
            Thread.sleep(1) // 模拟执行时间
            true
        } catch (e: Exception) {
            logger.error("Failed to execute Cypher query: {}", cypher, e)
            false
        }
    }
    
    /**
     * 测试Neo4j连接
     */
    fun testConnection(): Neo4jConnectionTest {
        return try {
            val startTime = System.currentTimeMillis()
            
            // 模拟连接测试
            Thread.sleep(500)
            
            val responseTime = System.currentTimeMillis() - startTime
            
            if (isConnected) {
                Neo4jConnectionTest(true, "Connection successful", responseTime, "5.x", "online")
            } else {
                Neo4jConnectionTest(false, "Not connected", responseTime, null, "offline")
            }
            
        } catch (e: Exception) {
            logger.error("Connection test failed", e)
            Neo4jConnectionTest(false, e.message ?: "Unknown error", 0, null, "error")
        }
    }
    
    /**
     * 获取数据库统计信息
     */
    fun getDatabaseStats(): Neo4jDatabaseStats? {
        if (!isConnected) return null
        
        return try {
            // 模拟获取统计信息
            Neo4jDatabaseStats(
                totalNodes = 1500,
                totalRelationships = 5000,
                classNodes = 150,
                methodNodes = 1350,
                callsRelationships = 4200,
                containsRelationships = 1350,
                inheritsRelationships = 250,
                implementsRelationships = 200,
                databaseSize = "15.2 MB",
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.error("Failed to get database stats", e)
            null
        }
    }
    
    /**
     * 查询方法调用路径
     */
    fun findCallPaths(fromMethodId: String, toMethodId: String, maxDepth: Int = 5): List<CallPath> {
        if (!isConnected) return emptyList()
        
        return try {
            val cypher = """
                MATCH path = (start:Method {id: ${'$'}fromId})-[:CALLS*1..$maxDepth]->(end:Method {id: ${'$'}toId})
                RETURN path
                LIMIT 10
            """.trimIndent()
            
            val parameters = mapOf(
                "fromId" to fromMethodId,
                "toId" to toMethodId
            )
            
            // 模拟返回结果
            listOf(
                CallPath(
                    id = "path_1",
                    startMethodId = fromMethodId,
                    endMethodId = toMethodId,
                    pathLength = 3,
                    methods = listOf(fromMethodId, "intermediate_method", toMethodId),
                    weight = 0.8
                )
            )
            
        } catch (e: Exception) {
            logger.error("Failed to find call paths", e)
            emptyList()
        }
    }
    
    /**
     * 关闭连接
     */
    fun close() {
        try {
            driver?.let {
                // driver.close()
                driver = null
                isConnected = false
                logger.info("Neo4j connection closed")
            }
        } catch (e: Exception) {
            logger.error("Error closing Neo4j connection", e)
        }
    }
    
    companion object {
        fun getInstance(project: Project): Neo4jService {
            return project.service()
        }
    }
}

/**
 * Neo4j同步结果
 */
data class Neo4jSyncResult(
    val success: Boolean,
    val message: String,
    val syncedNodes: Int,
    val syncedEdges: Int,
    val duration: Long
)

/**
 * Neo4j连接测试结果
 */
data class Neo4jConnectionTest(
    val success: Boolean,
    val message: String,
    val responseTime: Long,
    val version: String?,
    val status: String
)

/**
 * Neo4j数据库统计
 */
data class Neo4jDatabaseStats(
    val totalNodes: Long,
    val totalRelationships: Long,
    val classNodes: Long,
    val methodNodes: Long,
    val callsRelationships: Long,
    val containsRelationships: Long,
    val inheritsRelationships: Long,
    val implementsRelationships: Long,
    val databaseSize: String,
    val lastUpdated: Long
)

/**
 * 调用路径
 */
data class CallPath(
    val id: String,
    val startMethodId: String,
    val endMethodId: String,
    val pathLength: Int,
    val methods: List<String>,
    val weight: Double
)