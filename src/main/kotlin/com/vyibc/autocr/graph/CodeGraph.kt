package com.vyibc.autocr.graph

import com.vyibc.autocr.model.*

/**
 * Interface for code graph operations
 */
interface CodeGraph {
    // Node operations
    fun addNode(node: Node): Boolean
    fun getNode(id: String): Node?
    fun updateNode(node: Node): Boolean
    fun removeNode(nodeId: String): Boolean
    fun getAllNodes(): List<Node>
    fun getNodeCount(): Int
    
    // Edge operations
    fun addEdge(edge: Edge): Boolean
    fun getEdge(id: String): Edge?
    fun removeEdge(edgeId: String): Boolean
    fun removeRelatedEdges(nodeId: String): Int
    fun getAllEdges(): List<Edge>
    
    // Query operations
    fun getIncomingEdges(nodeId: String): List<Edge>
    fun getOutgoingEdges(nodeId: String): List<Edge>
    fun getConnectedNodes(nodeId: String, depth: Int = 1): Set<Node>
    fun findPaths(startNodeId: String, endNodeId: String, maxDepth: Int = 5): List<CallPath>
    
    // Analysis operations
    fun getMethodsByClass(classId: String): List<MethodNode>
    fun getMethodsByPackage(packageName: String): List<MethodNode>
    fun getClassesByPackage(packageName: String): List<ClassNode>
    fun getMethodsByBlockType(blockType: BlockType): List<MethodNode>
    
    // Persistence operations
    fun saveToFile(filePath: String): Boolean
    fun loadFromFile(filePath: String): Boolean
    fun clear()
    
    // Update operations for file changes
    fun updateEdges(filePath: String, updatedEdges: List<Edge>)
}

/**
 * Graph query result
 */
data class GraphQueryResult<T>(
    val results: List<T>,
    val totalCount: Int,
    val queryTime: Long // in milliseconds
)

/**
 * Graph statistics
 */
data class GraphStats(
    val nodeCount: Int,
    val edgeCount: Int,
    val averageInDegree: Double,
    val averageOutDegree: Double,
    val maxInDegree: Int,
    val maxOutDegree: Int,
    val connectedComponents: Int,
    val cyclomaticComplexity: Int
)