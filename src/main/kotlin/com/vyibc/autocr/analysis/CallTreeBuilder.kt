package com.vyibc.autocr.analysis

import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 调用树构建器
 * 从Controller方法开始构建调用树
 */
class CallTreeBuilder {
    
    private val logger = LoggerFactory.getLogger(CallTreeBuilder::class.java)
    private val businessDomainDetector = BusinessDomainDetector()
    private val treeCounter = AtomicInteger(1)
    private val pathCounter = AtomicInteger(1)
    
    /**
     * 构建所有调用树
     */
    fun buildCallTrees(graph: KnowledgeGraph): CallTreeBuildResult {
        logger.info("开始构建调用树...")
        
        val trees = mutableListOf<CallTree>()
        val treeRelations = mutableListOf<TreeNodeRelation>()
        val corePaths = mutableListOf<CorePath>()
        val visitedMethods = mutableSetOf<String>()
        
        // 1. 找到所有Controller根节点方法
        val rootMethods = findControllerRootMethods(graph)
        logger.info("找到${rootMethods.size}个Controller根方法")
        
        // 2. 为每个根方法构建调用树
        rootMethods.forEach { rootMethod ->
            if (!visitedMethods.contains(rootMethod.id)) {
                logger.debug("构建以${rootMethod.name}为根的调用树")
                
                val buildResult = buildSingleCallTree(rootMethod, graph, visitedMethods)
                if (buildResult != null) {
                    trees.add(buildResult.tree)
                    treeRelations.addAll(buildResult.relations)
                    corePaths.addAll(buildResult.corePaths)
                    
                    // 标记已访问的方法
                    buildResult.visitedInThisTree.forEach { methodId ->
                        visitedMethods.add(methodId)
                    }
                }
            }
        }
        
        logger.info("调用树构建完成: 共${trees.size}个树, ${treeRelations.size}个关系, ${corePaths.size}个核心路径")
        
        return CallTreeBuildResult(
            trees = trees,
            treeRelations = treeRelations,
            corePaths = corePaths
        )
    }
    
    /**
     * 找到Controller的根方法
     */
    private fun findControllerRootMethods(graph: KnowledgeGraph): List<MethodNode> {
        val controllerClasses = graph.getClassesByLayer(LayerType.CONTROLLER)
        val rootMethods = mutableListOf<MethodNode>()
        
        controllerClasses.forEach { controllerClass ->
            val methods = graph.getMethodsByClass(controllerClass.id)
            
            methods.forEach { method ->
                if (isControllerRootMethod(method, controllerClass)) {
                    rootMethods.add(method)
                    logger.debug("发现根方法: ${controllerClass.name}.${method.name}")
                }
            }
        }
        
        return rootMethods
    }
    
    /**
     * 判断是否为Controller根方法
     */
    private fun isControllerRootMethod(method: MethodNode, controllerClass: ClassBlock): Boolean {
        // 必须是public方法
        if (!method.isPublic) {
            return false
        }
        
        // 不能是构造函数
        if (method.isConstructor) {
            return false
        }
        
        // 应该是有意义的业务方法（通过方法名判断）
        val methodName = method.name.lowercase()
        val hasControllerMethodPattern = methodName.startsWith("handle") ||
                methodName.startsWith("process") ||
                methodName.startsWith("get") ||
                methodName.startsWith("post") ||
                methodName.startsWith("put") ||
                methodName.startsWith("delete") ||
                methodName.startsWith("create") ||
                methodName.startsWith("update") ||
                methodName.startsWith("remove") ||
                methodName.startsWith("find") ||
                methodName.startsWith("search") ||
                methodName.startsWith("list")
        
        return hasControllerMethodPattern
    }
    
    /**
     * 构建单个调用树
     */
    private fun buildSingleCallTree(
        rootMethod: MethodNode,
        graph: KnowledgeGraph,
        globalVisited: Set<String>
    ): SingleTreeBuildResult? {
        
        val treeId = generateTreeId(rootMethod)
        val treeNumber = generateTreeNumber()
        val relations = mutableListOf<TreeNodeRelation>()
        val corePaths = mutableListOf<CorePath>()
        val visitedInThisTree = mutableSetOf<String>()
        val nodeDepthMap = mutableMapOf<String, Int>()
        val crossNodeCount = AtomicInteger(0)
        
        // 使用BFS构建树，避免无限递归
        val queue: Queue<TreeBuildContext> = LinkedList()
        queue.offer(TreeBuildContext(rootMethod, null, 0, mutableListOf(rootMethod.id)))
        
        var maxDepth = 0
        var totalNodes = 0
        
        while (queue.isNotEmpty()) {
            val context = queue.poll()
            val currentMethod = context.method
            val parentMethod = context.parent
            val depth = context.depth
            val currentPath = context.pathToRoot
            
            // 防止无限循环
            if (depth > 10) {
                logger.warn("达到最大深度限制，停止构建: ${currentMethod.name}")
                continue
            }
            
            // 检查是否已在此树中访问过
            if (visitedInThisTree.contains(currentMethod.id)) {
                // 这是一个交叉节点
                crossNodeCount.incrementAndGet()
                continue
            }
            
            visitedInThisTree.add(currentMethod.id)
            nodeDepthMap[currentMethod.id] = depth
            maxDepth = maxOf(maxDepth, depth)
            totalNodes++
            
            // 创建树关系
            if (parentMethod != null) {
                val relationId = generateTreeRelationId(treeId, parentMethod.id, currentMethod.id)
                val relation = TreeNodeRelation(
                    id = relationId,
                    treeId = treeId,
                    parentMethodId = parentMethod.id,
                    childMethodId = currentMethod.id,
                    depth = depth,
                    pathIndex = relations.size,
                    relationshipType = "TREE_CALLS"
                )
                relations.add(relation)
            }
            
            // 创建核心路径（从当前节点到根节点）
            if (currentPath.size > 1) {
                val corePathId = generateCorePathId(currentMethod.id, rootMethod.id)
                val corePathNumber = generateCorePathNumber(treeNumber)
                val corePath = CorePath(
                    id = corePathId,
                    pathNumber = corePathNumber,
                    fromMethodId = currentMethod.id,
                    toTreeRootId = rootMethod.id,
                    treeId = treeId,
                    pathNodes = currentPath.toList(),
                    pathLength = currentPath.size - 1,
                    layerCrossCount = calculateLayerCrossCount(currentPath, graph),
                    weight = calculatePathWeight(currentPath, graph)
                )
                corePaths.add(corePath)
            }
            
            // 找到当前方法的所有调用
            val outgoingEdges = graph.getOutgoingEdges(currentMethod.id)
            outgoingEdges.forEach { edge ->
                val targetMethod = graph.getMethodById(edge.toMethodId)
                if (targetMethod != null && shouldIncludeInTree(targetMethod, currentPath, graph)) {
                    val newPath = currentPath.toMutableList()
                    newPath.add(targetMethod.id)
                    
                    queue.offer(TreeBuildContext(targetMethod, currentMethod, depth + 1, newPath))
                }
            }
        }
        
        if (totalNodes == 0) {
            logger.warn("树构建失败，没有有效节点: ${rootMethod.name}")
            return null
        }
        
        // 确定业务域
        val businessDomain = determineTreeBusinessDomain(rootMethod, graph)
        
        // 创建调用树
        val tree = CallTree(
            id = treeId,
            treeNumber = treeNumber,
            rootMethodId = rootMethod.id,
            rootClassId = rootMethod.classId,
            businessDomain = businessDomain,
            depth = maxDepth,
            nodeCount = totalNodes,
            crossNodeCount = crossNodeCount.get(),
            pathCount = corePaths.size,
            description = "以${rootMethod.name}为根的调用树"
        )
        
        logger.debug("构建完成树${treeNumber}: 深度${maxDepth}, 节点${totalNodes}, 路径${corePaths.size}")
        
        return SingleTreeBuildResult(
            tree = tree,
            relations = relations,
            corePaths = corePaths,
            visitedInThisTree = visitedInThisTree
        )
    }
    
    /**
     * 判断方法是否应该包含在树中
     */
    private fun shouldIncludeInTree(
        method: MethodNode,
        currentPath: List<String>,
        graph: KnowledgeGraph
    ): Boolean {
        // 防止循环调用
        if (currentPath.contains(method.id)) {
            return false
        }
        
        // 必须是业务方法
        if (!method.isBusinessMethod()) {
            return false
        }
        
        // 检查所属类是否合适
        val methodClass = graph.getClassById(method.classId)
        if (methodClass != null) {
            // 排除某些类型的类
            if (isExcludedFromTree(methodClass)) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * 判断类是否应该从树中排除
     */
    private fun isExcludedFromTree(classBlock: ClassBlock): Boolean {
        val className = classBlock.name.lowercase()
        val packageName = classBlock.packageName.lowercase()
        
        // 排除工具类和基础框架类
        return className.contains("util") ||
                className.contains("helper") ||
                packageName.contains("util") ||
                packageName.contains("common") && classBlock.layer == LayerType.UTIL
    }
    
    /**
     * 确定树的业务域
     */
    private fun determineTreeBusinessDomain(rootMethod: MethodNode, graph: KnowledgeGraph): BusinessDomain {
        val rootClass = graph.getClassById(rootMethod.classId)
        if (rootClass != null && rootClass.businessDomain != BusinessDomain.UNKNOWN) {
            return rootClass.businessDomain
        }
        
        // 从方法名推断业务域
        val methodName = rootMethod.name.lowercase()
        return when {
            methodName.contains("user") || methodName.contains("account") -> BusinessDomain.USER
            methodName.contains("order") || methodName.contains("purchase") -> BusinessDomain.ORDER
            methodName.contains("product") || methodName.contains("goods") -> BusinessDomain.PRODUCT
            methodName.contains("payment") || methodName.contains("pay") -> BusinessDomain.PAYMENT
            methodName.contains("auth") || methodName.contains("login") -> BusinessDomain.AUTH
            methodName.contains("system") || methodName.contains("admin") -> BusinessDomain.SYSTEM
            else -> BusinessDomain.UNKNOWN
        }
    }
    
    /**
     * 计算路径的层级跨越数
     */
    private fun calculateLayerCrossCount(pathNodes: List<String>, graph: KnowledgeGraph): Int {
        val layers = pathNodes.mapNotNull { nodeId ->
            val method = graph.getMethodById(nodeId)
            val classBlock = method?.let { graph.getClassById(it.classId) }
            classBlock?.layer
        }.distinct()
        
        return maxOf(0, layers.size - 1)
    }
    
    /**
     * 计算路径权重
     */
    private fun calculatePathWeight(pathNodes: List<String>, graph: KnowledgeGraph): Double {
        var weight = 0.0
        
        pathNodes.forEach { nodeId ->
            val method = graph.getMethodById(nodeId)
            if (method != null) {
                val methodClass = graph.getClassById(method.classId)
                if (methodClass != null) {
                    // 基于层级的权重
                    weight += when (methodClass.layer) {
                        LayerType.CONTROLLER -> 10.0
                        LayerType.SERVICE -> 8.0
                        LayerType.REPOSITORY -> 6.0
                        LayerType.COMPONENT -> 4.0
                        LayerType.UTIL -> 2.0
                        else -> 1.0
                    }
                    
                    // 基于业务域的权重
                    weight += businessDomainDetector.getBusinessDomainPriority(methodClass.businessDomain) * 0.1
                }
            }
        }
        
        return weight
    }
    
    // ====== ID生成方法 ======
    
    private fun generateTreeId(rootMethod: MethodNode): String {
        return "tree_${md5Hash(rootMethod.id)}"
    }
    
    private fun generateTreeNumber(): String {
        return "T${treeCounter.getAndIncrement().toString().padStart(3, '0')}"
    }
    
    private fun generateTreeRelationId(treeId: String, parentId: String, childId: String): String {
        return "tree_rel_${md5Hash("${treeId}_${parentId}_${childId}")}"
    }
    
    private fun generateCorePathId(fromMethodId: String, toRootId: String): String {
        return "core_path_${md5Hash("${fromMethodId}_${toRootId}")}"
    }
    
    private fun generateCorePathNumber(treeNumber: String): String {
        return "${treeNumber}-CP${pathCounter.getAndIncrement().toString().padStart(3, '0')}"
    }
    
    private fun md5Hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(8)
    }
}

/**
 * 树构建上下文
 */
private data class TreeBuildContext(
    val method: MethodNode,
    val parent: MethodNode?,
    val depth: Int,
    val pathToRoot: MutableList<String>
)

/**
 * 单个树构建结果
 */
private data class SingleTreeBuildResult(
    val tree: CallTree,
    val relations: List<TreeNodeRelation>,
    val corePaths: List<CorePath>,
    val visitedInThisTree: Set<String>
)

/**
 * 调用树构建结果
 */
data class CallTreeBuildResult(
    val trees: List<CallTree>,
    val treeRelations: List<TreeNodeRelation>,
    val corePaths: List<CorePath>
)