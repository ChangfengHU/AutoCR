package com.vyibc.autocr.service

import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory

/**
 * Tree查询服务
 * 提供Tree相关的查询功能
 */
class TreeQueryService {
    
    private val logger = LoggerFactory.getLogger(TreeQueryService::class.java)
    
    /**
     * 查询节点归属的所有Tree
     */
    fun queryNodeTrees(methodId: String, graph: KnowledgeGraph): NodeTreesResult {
        logger.debug("查询方法${methodId}归属的Tree")
        
        val belongingTrees = mutableListOf<CallTree>()
        val treeRoles = mutableMapOf<String, String>()
        
        // 1. 检查是否为根节点
        graph.trees.forEach { tree ->
            if (tree.rootMethodId == methodId) {
                belongingTrees.add(tree)
                treeRoles[tree.id] = "ROOT"
            }
        }
        
        // 2. 从树关系中查找
        graph.treeRelations.forEach { relation ->
            if (relation.parentMethodId == methodId || relation.childMethodId == methodId) {
                val tree = graph.getTreeById(relation.treeId)
                if (tree != null && !belongingTrees.any { it.id == tree.id }) {
                    belongingTrees.add(tree)
                    treeRoles[tree.id] = when {
                        relation.parentMethodId == methodId -> "PARENT"
                        relation.childMethodId == methodId && relation.depth == 1 -> "DIRECT_CHILD"
                        relation.childMethodId == methodId -> "DESCENDANT"
                        else -> "MEMBER"
                    }
                }
            }
        }
        
        // 3. 获取方法详细信息
        val method = graph.getMethodById(methodId)
        val methodClass = method?.let { graph.getClassById(it.classId) }
        
        val result = NodeTreesResult(
            methodId = methodId,
            methodName = method?.name ?: "Unknown",
            className = methodClass?.name ?: "Unknown",
            layer = methodClass?.layer ?: LayerType.UNKNOWN,
            businessDomain = methodClass?.businessDomain ?: BusinessDomain.UNKNOWN,
            belongingTrees = belongingTrees,
            treeRoles = treeRoles,
            crossCount = belongingTrees.size
        )
        
        logger.debug("方法${methodId}属于${result.crossCount}个Tree")
        return result
    }
    
    /**
     * 查询节点的核心链路集合
     */
    fun queryCorePathsForNode(methodId: String, graph: KnowledgeGraph): CorePathsResult {
        logger.debug("查询方法${methodId}的核心链路")
        
        val corePaths = graph.getCorePathsByMethod(methodId)
        val pathDetails = mutableListOf<CorePathDetail>()
        
        corePaths.forEach { corePath ->
            val tree = graph.getTreeById(corePath.treeId)
            val rootMethod = graph.getMethodById(corePath.toTreeRootId)
            val rootClass = rootMethod?.let { graph.getClassById(it.classId) }
            
            val pathNodes = mutableListOf<PathNodeDetail>()
            corePath.pathNodes.forEach { nodeId ->
                val pathMethod = graph.getMethodById(nodeId)
                val pathMethodClass = pathMethod?.let { graph.getClassById(it.classId) }
                
                if (pathMethod != null && pathMethodClass != null) {
                    pathNodes.add(PathNodeDetail(
                        methodId = pathMethod.id,
                        methodName = pathMethod.name,
                        className = pathMethodClass.name,
                        layer = pathMethodClass.layer,
                        businessDomain = pathMethodClass.businessDomain
                    ))
                }
            }
            
            pathDetails.add(CorePathDetail(
                corePath = corePath,
                tree = tree,
                rootMethodName = rootMethod?.name ?: "Unknown",
                rootClassName = rootClass?.name ?: "Unknown",
                pathNodes = pathNodes
            ))
        }
        
        // 按权重排序
        val sortedPathDetails = pathDetails.sortedByDescending { it.corePath.weight }
        
        val result = CorePathsResult(
            methodId = methodId,
            totalCorePaths = sortedPathDetails.size,
            corePathDetails = sortedPathDetails,
            averagePathLength = corePaths.map { it.pathLength }.average(),
            maxPathWeight = corePaths.maxOfOrNull { it.weight } ?: 0.0,
            involvedBusinessDomains = pathDetails.flatMap { detail ->
                detail.pathNodes.map { it.businessDomain }
            }.distinct()
        )
        
        logger.debug("方法${methodId}有${result.totalCorePaths}条核心链路")
        return result
    }
    
    /**
     * 查询Tree的完整结构
     */
    fun queryTreeStructure(treeId: String, graph: KnowledgeGraph): TreeStructureResult {
        logger.debug("查询Tree${treeId}的完整结构")
        
        val tree = graph.getTreeById(treeId) ?: return TreeStructureResult.empty(treeId)
        val relations = graph.getTreeRelationsByTree(treeId)
        
        // 构建树结构
        val rootMethod = graph.getMethodById(tree.rootMethodId)
        val rootClass = rootMethod?.let { graph.getClassById(it.classId) }
        
        val treeNodes = mutableMapOf<String, TreeNodeDetail>()
        val nodesByDepth = mutableMapOf<Int, MutableList<TreeNodeDetail>>()
        
        // 添加根节点
        if (rootMethod != null && rootClass != null) {
            val rootNodeDetail = TreeNodeDetail(
                method = rootMethod,
                classBlock = rootClass,
                depth = 0,
                children = mutableListOf(),
                isRoot = true,
                isCrossNode = rootMethod.crossCount > 1
            )
            treeNodes[rootMethod.id] = rootNodeDetail
            nodesByDepth.computeIfAbsent(0) { mutableListOf() }.add(rootNodeDetail)
        }
        
        // 添加其他节点
        relations.forEach { relation ->
            val childMethod = graph.getMethodById(relation.childMethodId)
            val childClass = childMethod?.let { graph.getClassById(it.classId) }
            
            if (childMethod != null && childClass != null && !treeNodes.containsKey(childMethod.id)) {
                val childNodeDetail = TreeNodeDetail(
                    method = childMethod,
                    classBlock = childClass,
                    depth = relation.depth,
                    children = mutableListOf(),
                    isRoot = false,
                    isCrossNode = childMethod.crossCount > 1
                )
                treeNodes[childMethod.id] = childNodeDetail
                nodesByDepth.computeIfAbsent(relation.depth) { mutableListOf() }.add(childNodeDetail)
                
                // 建立父子关系
                relation.parentMethodId?.let { parentId ->
                    treeNodes[parentId]?.children?.add(childNodeDetail)
                }
            }
        }
        
        // 统计信息
        val layerDistribution = treeNodes.values.groupingBy { it.classBlock.layer }.eachCount()
        val businessDomainDistribution = treeNodes.values.groupingBy { it.classBlock.businessDomain }.eachCount()
        val crossNodesCount = treeNodes.values.count { it.isCrossNode }
        
        val result = TreeStructureResult(
            tree = tree,
            rootNode = treeNodes[tree.rootMethodId],
            allNodes = treeNodes.values.toList(),
            nodesByDepth = nodesByDepth,
            layerDistribution = layerDistribution,
            businessDomainDistribution = businessDomainDistribution,
            crossNodesCount = crossNodesCount,
            totalRelations = relations.size
        )
        
        logger.debug("Tree${treeId}结构查询完成: ${result.allNodes.size}个节点, ${result.totalRelations}个关系")
        return result
    }
    
    /**
     * 查询所有Tree的概览信息
     */
    fun queryAllTreesOverview(graph: KnowledgeGraph): AllTreesOverviewResult {
        logger.debug("查询所有Tree概览")
        
        val treeOverviews = mutableListOf<TreeOverview>()
        
        graph.trees.forEach { tree ->
            val rootMethod = graph.getMethodById(tree.rootMethodId)
            val rootClass = rootMethod?.let { graph.getClassById(it.classId) }
            val relations = graph.getTreeRelationsByTree(tree.id)
            val corePaths = graph.corePaths.filter { it.treeId == tree.id }
            
            val overview = TreeOverview(
                tree = tree,
                rootMethodName = rootMethod?.name ?: "Unknown",
                rootClassName = rootClass?.name ?: "Unknown",
                actualNodeCount = relations.map { it.childMethodId }.distinct().size + 1, // +1 for root
                actualRelationCount = relations.size,
                corePathsCount = corePaths.size,
                averagePathLength = corePaths.map { it.pathLength }.average(),
                maxDepth = relations.maxOfOrNull { it.depth } ?: 0
            )
            treeOverviews.add(overview)
        }
        
        // 按业务域和权重排序
        val sortedOverviews = treeOverviews.sortedWith(
            compareByDescending<TreeOverview> { it.tree.businessDomain.ordinal }
                .thenByDescending { it.actualNodeCount }
        )
        
        val result = AllTreesOverviewResult(
            totalTrees = sortedOverviews.size,
            treeOverviews = sortedOverviews,
            businessDomainDistribution = sortedOverviews.groupingBy { it.tree.businessDomain }.eachCount(),
            totalNodes = sortedOverviews.sumOf { it.actualNodeCount },
            totalRelations = sortedOverviews.sumOf { it.actualRelationCount },
            totalCorePaths = sortedOverviews.sumOf { it.corePathsCount }
        )
        
        logger.debug("Tree概览查询完成: ${result.totalTrees}个Tree, ${result.totalNodes}个节点")
        return result
    }
}

// ====== 数据模型 ======

/**
 * 节点归属Tree结果
 */
data class NodeTreesResult(
    val methodId: String,
    val methodName: String,
    val className: String,
    val layer: LayerType,
    val businessDomain: BusinessDomain,
    val belongingTrees: List<CallTree>,
    val treeRoles: Map<String, String>, // TreeId -> Role
    val crossCount: Int
)

/**
 * 核心链路结果
 */
data class CorePathsResult(
    val methodId: String,
    val totalCorePaths: Int,
    val corePathDetails: List<CorePathDetail>,
    val averagePathLength: Double,
    val maxPathWeight: Double,
    val involvedBusinessDomains: List<BusinessDomain>
)

/**
 * 核心链路详情
 */
data class CorePathDetail(
    val corePath: CorePath,
    val tree: CallTree?,
    val rootMethodName: String,
    val rootClassName: String,
    val pathNodes: List<PathNodeDetail>
)

/**
 * 路径节点详情
 */
data class PathNodeDetail(
    val methodId: String,
    val methodName: String,
    val className: String,
    val layer: LayerType,
    val businessDomain: BusinessDomain
)

/**
 * Tree结构结果
 */
data class TreeStructureResult(
    val tree: CallTree,
    val rootNode: TreeNodeDetail?,
    val allNodes: List<TreeNodeDetail>,
    val nodesByDepth: Map<Int, List<TreeNodeDetail>>,
    val layerDistribution: Map<LayerType, Int>,
    val businessDomainDistribution: Map<BusinessDomain, Int>,
    val crossNodesCount: Int,
    val totalRelations: Int
) {
    companion object {
        fun empty(treeId: String) = TreeStructureResult(
            tree = CallTree(treeId, "", "", "", BusinessDomain.UNKNOWN, 0, 0, 0, 0),
            rootNode = null,
            allNodes = emptyList(),
            nodesByDepth = emptyMap(),
            layerDistribution = emptyMap(),
            businessDomainDistribution = emptyMap(),
            crossNodesCount = 0,
            totalRelations = 0
        )
    }
}

/**
 * Tree节点详情
 */
data class TreeNodeDetail(
    val method: MethodNode,
    val classBlock: ClassBlock,
    val depth: Int,
    val children: MutableList<TreeNodeDetail>,
    val isRoot: Boolean,
    val isCrossNode: Boolean
)

/**
 * 所有Tree概览结果
 */
data class AllTreesOverviewResult(
    val totalTrees: Int,
    val treeOverviews: List<TreeOverview>,
    val businessDomainDistribution: Map<BusinessDomain, Int>,
    val totalNodes: Int,
    val totalRelations: Int,
    val totalCorePaths: Int
)

/**
 * Tree概览
 */
data class TreeOverview(
    val tree: CallTree,
    val rootMethodName: String,
    val rootClassName: String,
    val actualNodeCount: Int,
    val actualRelationCount: Int,
    val corePathsCount: Int,
    val averagePathLength: Double,
    val maxDepth: Int
)