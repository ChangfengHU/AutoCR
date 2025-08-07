package com.vyibc.autocr.graph

import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory implementation of CodeGraph for initial development
 */
class SimpleCodeGraph : CodeGraph {
    private val logger = LoggerFactory.getLogger(SimpleCodeGraph::class.java)
    
    private val nodes = ConcurrentHashMap<String, Node>()
    private val edges = ConcurrentHashMap<String, Edge>()
    private val nodeEdges = ConcurrentHashMap<String, MutableSet<String>>() // nodeId -> set of edgeIds
    
    override fun addNode(node: Node): Boolean {
        return try {
            nodes[node.id] = node
            nodeEdges.putIfAbsent(node.id, ConcurrentHashMap.newKeySet())
            true
        } catch (e: Exception) {
            logger.error("Failed to add node: ${node.id}", e)
            false
        }
    }
    
    override fun getNode(id: String): Node? {
        return nodes[id]
    }
    
    override fun updateNode(node: Node): Boolean {
        return try {
            nodes[node.id] = node
            true
        } catch (e: Exception) {
            logger.error("Failed to update node: ${node.id}", e)
            false
        }
    }
    
    override fun removeNode(nodeId: String): Boolean {
        return try {
            nodes.remove(nodeId)
            // Remove all edges connected to this node
            nodeEdges[nodeId]?.forEach { edgeId ->
                edges.remove(edgeId)
            }
            nodeEdges.remove(nodeId)
            true
        } catch (e: Exception) {
            logger.error("Failed to remove node: $nodeId", e)
            false
        }
    }
    
    override fun getAllNodes(): List<Node> {
        return nodes.values.toList()
    }
    
    override fun getNodeCount(): Int {
        return nodes.size
    }
    
    override fun addEdge(edge: Edge): Boolean {
        return try {
            edges[edge.id] = edge
            nodeEdges.computeIfAbsent(edge.sourceId) { ConcurrentHashMap.newKeySet() }.add(edge.id)
            nodeEdges.computeIfAbsent(edge.targetId) { ConcurrentHashMap.newKeySet() }.add(edge.id)
            true
        } catch (e: Exception) {
            logger.error("Failed to add edge: ${edge.id}", e)
            false
        }
    }
    
    override fun getEdge(id: String): Edge? {
        return edges[id]
    }
    
    override fun removeEdge(edgeId: String): Boolean {
        return try {
            val edge = edges.remove(edgeId)
            if (edge != null) {
                nodeEdges[edge.sourceId]?.remove(edgeId)
                nodeEdges[edge.targetId]?.remove(edgeId)
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to remove edge: $edgeId", e)
            false
        }
    }
    
    override fun removeRelatedEdges(nodeId: String): Int {
        val edgeIds = nodeEdges[nodeId] ?: return 0
        var count = 0
        edgeIds.toList().forEach { edgeId ->
            if (removeEdge(edgeId)) count++
        }
        return count
    }
    
    override fun getAllEdges(): List<Edge> {
        return edges.values.toList()
    }
    
    override fun getIncomingEdges(nodeId: String): List<Edge> {
        val edgeIds = nodeEdges[nodeId] ?: return emptyList()
        return edgeIds.mapNotNull { edgeId ->
            val edge = edges[edgeId]
            if (edge?.targetId == nodeId) edge else null
        }
    }
    
    override fun getOutgoingEdges(nodeId: String): List<Edge> {
        val edgeIds = nodeEdges[nodeId] ?: return emptyList()
        return edgeIds.mapNotNull { edgeId ->
            val edge = edges[edgeId]
            if (edge?.sourceId == nodeId) edge else null
        }
    }
    
    override fun getConnectedNodes(nodeId: String, depth: Int): Set<Node> {
        val visited = mutableSetOf<String>()
        val result = mutableSetOf<Node>()
        
        fun explore(currentId: String, currentDepth: Int) {
            if (currentDepth > depth || visited.contains(currentId)) return
            visited.add(currentId)
            
            nodes[currentId]?.let { result.add(it) }
            
            if (currentDepth < depth) {
                val edges = nodeEdges[currentId] ?: return
                edges.forEach { edgeId ->
                    val edge = this.edges[edgeId] ?: return@forEach
                    val nextId = if (edge.sourceId == currentId) edge.targetId else edge.sourceId
                    explore(nextId, currentDepth + 1)
                }
            }
        }
        
        explore(nodeId, 0)
        result.remove(nodes[nodeId]) // Remove the starting node
        return result
    }
    
    override fun findPaths(startNodeId: String, endNodeId: String, maxDepth: Int): List<CallPath> {
        val paths = mutableListOf<CallPath>()
        val visited = mutableSetOf<String>()
        
        fun dfs(currentId: String, path: MutableList<MethodNode>, edgePath: MutableList<CallsEdge>, depth: Int) {
            if (depth > maxDepth) return
            
            if (currentId == endNodeId) {
                paths.add(CallPath(
                    id = "path_${startNodeId}_${endNodeId}_${paths.size}",
                    methods = path.toList(),
                    edges = edgePath.toList()
                ))
                return
            }
            
            visited.add(currentId)
            
            getOutgoingEdges(currentId).forEach { edge ->
                if (edge is CallsEdge && !visited.contains(edge.targetId)) {
                    val targetNode = nodes[edge.targetId]
                    if (targetNode is MethodNode) {
                        path.add(targetNode)
                        edgePath.add(edge)
                        dfs(edge.targetId, path, edgePath, depth + 1)
                        path.removeAt(path.size - 1)
                        edgePath.removeAt(edgePath.size - 1)
                    }
                }
            }
            
            visited.remove(currentId)
        }
        
        val startNode = nodes[startNodeId] as? MethodNode ?: return emptyList()
        dfs(startNodeId, mutableListOf(startNode), mutableListOf(), 0)
        
        return paths
    }
    
    override fun getMethodsByClass(classId: String): List<MethodNode> {
        return nodes.values
            .filterIsInstance<MethodNode>()
            .filter { it.id.startsWith("$classId#") }
    }
    
    override fun getMethodsByPackage(packageName: String): List<MethodNode> {
        return nodes.values
            .filterIsInstance<MethodNode>()
            .filter { it.getPackageName() == packageName }
    }
    
    override fun getClassesByPackage(packageName: String): List<ClassNode> {
        return nodes.values
            .filterIsInstance<ClassNode>()
            .filter { it.packageName == packageName }
    }
    
    override fun getMethodsByBlockType(blockType: BlockType): List<MethodNode> {
        return nodes.values
            .filterIsInstance<MethodNode>()
            .filter { it.blockType == blockType }
    }
    
    override fun saveToFile(filePath: String): Boolean {
        // TODO: Implement serialization
        logger.warn("saveToFile not implemented yet")
        return false
    }
    
    override fun loadFromFile(filePath: String): Boolean {
        // TODO: Implement deserialization
        logger.warn("loadFromFile not implemented yet")
        return false
    }
    
    override fun clear() {
        nodes.clear()
        edges.clear()
        nodeEdges.clear()
    }
    
    override fun updateEdges(filePath: String, updatedEdges: List<Edge>) {
        // Remove existing edges for the file
        edges.values
            .filter { edge ->
                val sourceNode = nodes[edge.sourceId]
                val targetNode = nodes[edge.targetId]
                (sourceNode as? MethodNode)?.filePath == filePath || 
                (targetNode as? MethodNode)?.filePath == filePath
            }
            .forEach { edge ->
                removeEdge(edge.id)
            }
        
        // Add updated edges
        updatedEdges.forEach { edge ->
            addEdge(edge)
        }
    }
}