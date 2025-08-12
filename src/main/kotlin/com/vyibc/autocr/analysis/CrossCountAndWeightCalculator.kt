package com.vyibc.autocr.analysis

import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory
import kotlin.math.*

/**
 * 交叉数和权重计算器
 * 计算节点在多个Tree中的交叉数，并基于各种因素计算权重
 */
class CrossCountAndWeightCalculator {
    
    private val logger = LoggerFactory.getLogger(CrossCountAndWeightCalculator::class.java)
    private val businessDomainDetector = BusinessDomainDetector()
    
    /**
     * 计算并更新所有节点的交叉数和权重
     */
    fun calculateCrossCountAndWeight(graph: KnowledgeGraph): WeightCalculationResult {
        logger.info("开始计算节点交叉数和权重...")
        
        val methodCrossCountMap = mutableMapOf<String, Int>()
        val classCrossCountMap = mutableMapOf<String, Int>()
        val updatedMethods = mutableListOf<MethodNode>()
        val updatedClasses = mutableListOf<ClassBlock>()
        
        // 1. 计算方法的交叉数
        calculateMethodCrossCount(graph, methodCrossCountMap)
        
        // 2. 计算类的交叉数
        calculateClassCrossCount(graph, classCrossCountMap, methodCrossCountMap)
        
        // 3. 计算方法权重并更新
        graph.methods.forEach { method ->
            val crossCount = methodCrossCountMap[method.id] ?: 0
            val weight = calculateMethodWeight(method, crossCount, graph)
            val treeIds = getMethodTreeIds(method.id, graph)
            
            val updatedMethod = method.copy(
                crossCount = crossCount,
                weight = weight,
                treeIds = treeIds,
                isRootNode = isRootNode(method.id, graph)
            )
            updatedMethods.add(updatedMethod)
        }
        
        // 4. 计算类权重并更新
        graph.classes.forEach { classBlock ->
            val crossCount = classCrossCountMap[classBlock.id] ?: 0
            val weight = calculateClassWeight(classBlock, crossCount, graph)
            
            val updatedClass = classBlock.copy(
                crossCount = crossCount,
                weight = weight
            )
            updatedClasses.add(updatedClass)
        }
        
        // 5. 更新图谱中的节点
        updatedMethods.forEach { method ->
            graph.addMethod(method)
        }
        
        updatedClasses.forEach { classBlock ->
            graph.addClass(classBlock)
        }
        
        val result = WeightCalculationResult(
            totalMethods = updatedMethods.size,
            totalClasses = updatedClasses.size,
            methodsWithCrossCount = methodCrossCountMap.values.count { it > 1 },
            classesWithCrossCount = classCrossCountMap.values.count { it > 1 },
            maxMethodCrossCount = methodCrossCountMap.values.maxOrNull() ?: 0,
            maxClassCrossCount = classCrossCountMap.values.maxOrNull() ?: 0,
            avgMethodWeight = updatedMethods.map { it.weight }.average(),
            avgClassWeight = updatedClasses.map { it.weight }.average()
        )
        
        logger.info("权重计算完成: ${result}")
        return result
    }
    
    /**
     * 计算方法的交叉数
     */
    private fun calculateMethodCrossCount(graph: KnowledgeGraph, crossCountMap: MutableMap<String, Int>) {
        val methodTreeMap = mutableMapOf<String, MutableSet<String>>()
        
        // 遍历所有树关系，统计每个方法出现在哪些树中
        graph.treeRelations.forEach { relation ->
            // 父节点
            relation.parentMethodId?.let { parentId ->
                methodTreeMap.computeIfAbsent(parentId) { mutableSetOf() }.add(relation.treeId)
            }
            // 子节点
            methodTreeMap.computeIfAbsent(relation.childMethodId) { mutableSetOf() }.add(relation.treeId)
        }
        
        // 也要统计根节点
        graph.trees.forEach { tree ->
            methodTreeMap.computeIfAbsent(tree.rootMethodId) { mutableSetOf() }.add(tree.id)
        }
        
        // 计算交叉数
        methodTreeMap.forEach { (methodId, treeIds) ->
            crossCountMap[methodId] = treeIds.size
        }
        
        logger.debug("方法交叉数计算完成: 共${crossCountMap.size}个方法, 最大交叉数${crossCountMap.values.maxOrNull() ?: 0}")
    }
    
    /**
     * 计算类的交叉数
     */
    private fun calculateClassCrossCount(
        graph: KnowledgeGraph, 
        classCrossCountMap: MutableMap<String, Int>,
        methodCrossCountMap: Map<String, Int>
    ) {
        val classTreeMap = mutableMapOf<String, MutableSet<String>>()
        
        // 基于方法的树归属计算类的树归属
        graph.methods.forEach { method ->
            val methodCrossCount = methodCrossCountMap[method.id] ?: 0
            if (methodCrossCount > 0) {
                val methodTrees = getMethodTreeIds(method.id, graph)
                classTreeMap.computeIfAbsent(method.classId) { mutableSetOf() }.addAll(methodTrees)
            }
        }
        
        // 计算类的交叉数
        classTreeMap.forEach { (classId, treeIds) ->
            classCrossCountMap[classId] = treeIds.size
        }
        
        logger.debug("类交叉数计算完成: 共${classCrossCountMap.size}个类, 最大交叉数${classCrossCountMap.values.maxOrNull() ?: 0}")
    }
    
    /**
     * 计算方法权重
     */
    private fun calculateMethodWeight(method: MethodNode, crossCount: Int, graph: KnowledgeGraph): Double {
        val methodClass = graph.getClassById(method.classId)
        var weight = 0.0
        
        // 1. 基础权重（基于层级）
        val layerWeight = when (methodClass?.layer) {
            LayerType.CONTROLLER -> 10.0
            LayerType.SERVICE -> 8.0
            LayerType.REPOSITORY -> 6.0
            LayerType.MAPPER -> 5.0
            LayerType.COMPONENT -> 4.0
            LayerType.CONFIG -> 3.0
            LayerType.UTIL -> 2.0
            LayerType.ENTITY -> 1.0
            null, LayerType.UNKNOWN -> 0.5
        }
        weight += layerWeight
        
        // 2. 交叉数权重（核心算法）
        val crossWeight = when {
            crossCount >= 5 -> 20.0  // 高度重要的交叉节点
            crossCount >= 3 -> 15.0  // 重要的交叉节点
            crossCount >= 2 -> 10.0  // 一般交叉节点
            crossCount == 1 -> 5.0   // 单树节点
            else -> 0.0              // 未包含在任何树中
        }
        weight += crossWeight
        
        // 3. 业务域权重
        val businessWeight = if (methodClass != null) {
            businessDomainDetector.getBusinessDomainPriority(methodClass.businessDomain).toDouble()
        } else {
            0.0
        }
        weight += businessWeight * 0.5
        
        // 4. 方法特性权重
        if (method.isPublic) weight += 2.0
        if (method.isStatic) weight += 1.0
        if (method.isAbstract) weight += 1.5
        if (method.isConstructor) weight -= 1.0  // 构造函数权重较低
        
        // 5. 调用复杂度权重
        val incomingCalls = graph.getIncomingEdges(method.id).size
        val outgoingCalls = graph.getOutgoingEdges(method.id).size
        val callComplexityWeight = sqrt((incomingCalls + outgoingCalls).toDouble()) * 0.5
        weight += callComplexityWeight
        
        // 6. 根节点特殊加权
        if (isRootNode(method.id, graph)) {
            weight += 15.0
        }
        
        // 7. 方法名权重（基于业务重要性）
        val methodNameWeight = calculateMethodNameWeight(method.name)
        weight += methodNameWeight
        
        return weight.coerceAtLeast(0.0)
    }
    
    /**
     * 计算类权重
     */
    private fun calculateClassWeight(classBlock: ClassBlock, crossCount: Int, graph: KnowledgeGraph): Double {
        var weight = 0.0
        
        // 1. 基础权重（基于层级）
        val layerWeight = when (classBlock.layer) {
            LayerType.CONTROLLER -> 15.0
            LayerType.SERVICE -> 12.0
            LayerType.REPOSITORY -> 9.0
            LayerType.MAPPER -> 7.0
            LayerType.COMPONENT -> 6.0
            LayerType.CONFIG -> 4.0
            LayerType.UTIL -> 3.0
            LayerType.ENTITY -> 2.0
            LayerType.UNKNOWN -> 1.0
        }
        weight += layerWeight
        
        // 2. 交叉数权重
        val crossWeight = when {
            crossCount >= 5 -> 25.0
            crossCount >= 3 -> 18.0
            crossCount >= 2 -> 12.0
            crossCount == 1 -> 6.0
            else -> 0.0
        }
        weight += crossWeight
        
        // 3. 业务域权重
        val businessWeight = businessDomainDetector.getBusinessDomainPriority(classBlock.businessDomain).toDouble()
        weight += businessWeight
        
        // 4. 类特性权重
        if (classBlock.isInterface) weight += 5.0
        if (classBlock.isAbstract) weight += 3.0
        
        // 5. 方法数量权重
        val methodCountWeight = log10(classBlock.methodCount.toDouble() + 1) * 2
        weight += methodCountWeight
        
        // 6. 类的实际方法权重（基于图谱中的方法）
        val actualMethods = graph.getMethodsByClass(classBlock.id)
        val actualMethodsWeight = actualMethods.sumOf { it.weight * 0.1 }
        weight += actualMethodsWeight
        
        // 7. 继承关系权重
        val hasInheritance = classBlock.superClass != null || classBlock.interfaces.isNotEmpty()
        if (hasInheritance) weight += 2.0
        
        return weight.coerceAtLeast(0.0)
    }
    
    /**
     * 基于方法名计算权重
     */
    private fun calculateMethodNameWeight(methodName: String): Double {
        val name = methodName.lowercase()
        
        return when {
            // 高权重方法名
            name.contains("process") || name.contains("handle") || name.contains("execute") -> 3.0
            name.contains("create") || name.contains("save") || name.contains("update") || name.contains("delete") -> 2.5
            name.contains("validate") || name.contains("check") || name.contains("verify") -> 2.0
            name.contains("calculate") || name.contains("compute") || name.contains("generate") -> 2.0
            name.contains("find") || name.contains("search") || name.contains("query") -> 1.5
            name.contains("get") || name.contains("list") -> 1.0
            name.contains("set") -> 0.5
            name.startsWith("is") || name.startsWith("has") -> 0.3
            else -> 0.0
        }
    }
    
    /**
     * 获取方法所属的Tree ID集合
     */
    private fun getMethodTreeIds(methodId: String, graph: KnowledgeGraph): Set<String> {
        val treeIds = mutableSetOf<String>()
        
        // 从树关系中查找
        graph.treeRelations.forEach { relation ->
            if (relation.parentMethodId == methodId || relation.childMethodId == methodId) {
                treeIds.add(relation.treeId)
            }
        }
        
        // 检查是否为根节点
        graph.trees.forEach { tree ->
            if (tree.rootMethodId == methodId) {
                treeIds.add(tree.id)
            }
        }
        
        return treeIds
    }
    
    /**
     * 判断是否为根节点
     */
    private fun isRootNode(methodId: String, graph: KnowledgeGraph): Boolean {
        return graph.trees.any { it.rootMethodId == methodId }
    }
    
    /**
     * 获取权重统计信息
     */
    fun getWeightStatistics(graph: KnowledgeGraph): WeightStatistics {
        val methods = graph.methods
        val classes = graph.classes
        
        val methodWeights = methods.map { it.weight }
        val classWeights = classes.map { it.weight }
        
        val methodCrossCounts = methods.map { it.crossCount }
        val classCrossCounts = classes.map { it.crossCount }
        
        return WeightStatistics(
            totalMethods = methods.size,
            totalClasses = classes.size,
            
            methodWeightStats = StatisticalData(
                min = methodWeights.minOrNull() ?: 0.0,
                max = methodWeights.maxOrNull() ?: 0.0,
                avg = methodWeights.average(),
                median = calculateMedian(methodWeights)
            ),
            
            classWeightStats = StatisticalData(
                min = classWeights.minOrNull() ?: 0.0,
                max = classWeights.maxOrNull() ?: 0.0,
                avg = classWeights.average(),
                median = calculateMedian(classWeights)
            ),
            
            methodCrossCountStats = StatisticalData(
                min = methodCrossCounts.minOrNull()?.toDouble() ?: 0.0,
                max = methodCrossCounts.maxOrNull()?.toDouble() ?: 0.0,
                avg = methodCrossCounts.average(),
                median = calculateMedian(methodCrossCounts.map { it.toDouble() })
            ),
            
            classCrossCountStats = StatisticalData(
                min = classCrossCounts.minOrNull()?.toDouble() ?: 0.0,
                max = classCrossCounts.maxOrNull()?.toDouble() ?: 0.0,
                avg = classCrossCounts.average(),
                median = calculateMedian(classCrossCounts.map { it.toDouble() })
            ),
            
            crossNodesCount = methods.count { it.crossCount > 1 },
            rootNodesCount = methods.count { it.isRootNode },
            
            topWeightMethods = methods.sortedByDescending { it.weight }.take(10),
            topCrossCountMethods = methods.sortedByDescending { it.crossCount }.take(10)
        )
    }
    
    /**
     * 计算中位数
     */
    private fun calculateMedian(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        
        val sorted = values.sorted()
        val size = sorted.size
        
        return if (size % 2 == 0) {
            (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0
        } else {
            sorted[size / 2]
        }
    }
}

/**
 * 权重计算结果
 */
data class WeightCalculationResult(
    val totalMethods: Int,
    val totalClasses: Int,
    val methodsWithCrossCount: Int,
    val classesWithCrossCount: Int,
    val maxMethodCrossCount: Int,
    val maxClassCrossCount: Int,
    val avgMethodWeight: Double,
    val avgClassWeight: Double
)

/**
 * 统计数据
 */
data class StatisticalData(
    val min: Double,
    val max: Double,
    val avg: Double,
    val median: Double
)

/**
 * 权重统计信息
 */
data class WeightStatistics(
    val totalMethods: Int,
    val totalClasses: Int,
    val methodWeightStats: StatisticalData,
    val classWeightStats: StatisticalData,
    val methodCrossCountStats: StatisticalData,
    val classCrossCountStats: StatisticalData,
    val crossNodesCount: Int,
    val rootNodesCount: Int,
    val topWeightMethods: List<MethodNode>,
    val topCrossCountMethods: List<MethodNode>
)