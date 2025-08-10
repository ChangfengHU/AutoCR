package com.vyibc.autocr.service

import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory
import kotlin.math.min

/**
 * 意图权重计算器 - 基于Neo4j图数据库的精确分析
 * 通过查询调用关系、业务影响范围、架构层级来准确评估业务价值
 */
class IntentWeightCalculator(private val neo4jQueryService: Neo4jQueryService) {
    
    private val logger = LoggerFactory.getLogger(IntentWeightCalculator::class.java)
    
    /**
     * 计算调用路径的意图权重 - 以Neo4j图分析为核心
     * @param path 调用路径
     * @param context Git差异上下文
     * @return 意图权重 (0-100)
     */
    fun calculateIntentWeight(path: CallPath, context: GitDiffContext): Double {
        logger.debug("开始基于Neo4j图数据库计算路径 ${path.id} 的意图权重")
        
        // 核心：基于图数据库的分析 (80%)
        val businessImpactFromGraph = calculateBusinessImpactFromGraph(path, context)
        val architecturalValueFromGraph = calculateArchitecturalValueFromGraph(path)
        val callChainCompletenessFromGraph = calculateCallChainCompletenessFromGraph(path)
        
        // 辅助：基于Git变更的分析 (20%)
        val changeContextValue = calculateGitChangeValue(path, context)
        
        // 权重分配：图数据库80%，Git变更20%
        val graphWeight = (businessImpactFromGraph * 0.4 + architecturalValueFromGraph * 0.25 + callChainCompletenessFromGraph * 0.15) * 0.8
        val gitWeight = changeContextValue * 0.2
        val totalWeight = graphWeight + gitWeight
        
        logger.debug("路径 ${path.id} 意图权重计算:")
        logger.debug("  - 图数据库分析 (80%): 业务影响=${"%.1f".format(businessImpactFromGraph)}, 架构价值=${"%.1f".format(architecturalValueFromGraph)}, 调用链完整性=${"%.1f".format(callChainCompletenessFromGraph)}")
        logger.debug("  - Git变更分析 (20%): ${"%.1f".format(changeContextValue)}")
        logger.debug("  - 最终权重: ${"%.1f".format(totalWeight)}")
        
        return totalWeight
    }
    
    /**
     * 基于图数据库计算业务影响权重 (0-100分)
     * 通过分析调用关系的业务价值来评估
     */
    private fun calculateBusinessImpactFromGraph(path: CallPath, context: GitDiffContext): Double {
        var score = 0.0
        logger.debug("计算路径 ${path.id} 的业务影响权重")
        
        path.methods.forEach { methodPath ->
            if (methodPath.contains(".")) {
                val className = methodPath.substringBeforeLast(".")
                val methodName = methodPath.substringAfterLast(".")
                
                // 1. 查询方法的被调用者（下游影响）
                val calleeInfo = neo4jQueryService.queryMethodCallees(className, methodName)
                val downstreamScore = calculateDownstreamBusinessValue(calleeInfo)
                score += downstreamScore
                logger.debug("方法 $methodPath 下游业务价值: $downstreamScore")
                
                // 2. 查询方法的调用者（上游依赖）
                val callerInfo = neo4jQueryService.queryMethodCallers(className, methodName)  
                val upstreamScore = calculateUpstreamBusinessValue(callerInfo)
                score += upstreamScore
                logger.debug("方法 $methodPath 上游业务价值: $upstreamScore")
                
                // 3. 查询类的架构位置
                val archInfo = neo4jQueryService.queryClassArchitecture(className)
                val positionScore = calculateArchitecturalPositionValue(archInfo)
                score += positionScore
                logger.debug("类 $className 架构位置价值: $positionScore")
            }
        }
        
        // 4. 基于Git变更的业务上下文
        val changeContextScore = calculateGitChangeValue(path, context)
        score += changeContextScore
        logger.debug("变更上下文业务价值: $changeContextScore")
        
        val finalScore = min(score / path.methods.size, 100.0) // 按方法数量平均
        logger.debug("路径 ${path.id} 业务影响总分: $finalScore")
        return finalScore
    }
    
    /**
     * 计算下游业务价值 - 基于被调用的方法类型和数量
     */
    private fun calculateDownstreamBusinessValue(calleeInfo: MethodCalleesInfo): Double {
        var score = 0.0
        
        // 基于被调用者的层级分布计算业务价值
        calleeInfo.layerDistribution.forEach { (layer, count) ->
            score += when (layer) {
                "REPOSITORY", "DAO" -> count * 20.0  // 数据访问层价值高
                "SERVICE" -> count * 15.0           // 业务逻辑层价值中等
                "UTIL", "HELPER" -> count * 10.0    // 工具类价值较低
                "EXTERNAL_API" -> count * 25.0      // 外部API集成价值很高
                else -> count * 5.0
            }
        }
        
        // 被调用方法的业务术语匹配
        val businessTermCount = calleeInfo.calleeDetails.count { calleeDetail ->
            val businessTerms = setOf("user", "order", "product", "payment", "customer", "account")
            businessTerms.any { term -> calleeDetail.className.lowercase().contains(term) }
        }
        score += businessTermCount * 8.0
        
        return min(score, 50.0)
    }
    
    /**
     * 计算上游业务价值 - 基于调用者的重要性
     */
    private fun calculateUpstreamBusinessValue(callerInfo: MethodCallersInfo): Double {
        var score = 0.0
        
        // 基于调用者的层级分布
        callerInfo.layerDistribution.forEach { (layer, count) ->
            score += when (layer) {
                "CONTROLLER", "REST" -> count * 25.0  // 控制层调用价值最高
                "SERVICE" -> count * 15.0             // 服务层调用价值中等
                "SCHEDULED", "BATCH" -> count * 20.0  // 定时任务价值较高
                "FILTER", "INTERCEPTOR" -> count * 18.0 // 拦截器价值较高
                else -> count * 5.0
            }
        }
        
        // 调用频次权重
        val totalCallCount = callerInfo.callerDetails.sumOf { it.callCount }
        score += when {
            totalCallCount > 20 -> 20.0  // 高频调用
            totalCallCount > 10 -> 15.0  // 中频调用
            totalCallCount > 5 -> 10.0   // 低频调用
            else -> 5.0
        }
        
        return min(score, 40.0)
    }
    
    /**
     * 计算架构位置价值
     */
    private fun calculateArchitecturalPositionValue(archInfo: ClassArchitectureInfo): Double {
        var score = 0.0
        
        // 基于架构层级的基础分值
        score += when (archInfo.layer) {
            "CONTROLLER" -> 20.0  // API层价值高
            "SERVICE" -> 15.0     // 业务层价值中等
            "REPOSITORY" -> 12.0  // 数据层价值较高
            "CONFIG" -> 10.0      // 配置类价值中等
            "UTIL" -> 8.0         // 工具类价值较低
            else -> 5.0
        }
        
        // 依赖数量权重（更多依赖意味着更重要）
        score += min(archInfo.dependencies.size * 2.0, 15.0)
        
        // 接口实现权重
        if (archInfo.interfaces.isNotEmpty()) {
            score += archInfo.interfaces.size * 5.0
        }
        
        // 继承层次权重
        if (archInfo.parents.isNotEmpty() || archInfo.children.isNotEmpty()) {
            score += (archInfo.parents.size + archInfo.children.size) * 3.0
        }
        
        return min(score, 30.0)
    }
    
    /**
     * 基于架构层级计算架构价值 (0-100分)
     */
    private fun calculateArchitecturalValueFromGraph(path: CallPath): Double {
        var score = 0.0
        logger.debug("计算路径 ${path.id} 的架构价值")
        
        val classNames = path.methods.map { it.substringBeforeLast(".") }.distinct()
        
        classNames.forEach { className ->
            val archInfo = neo4jQueryService.queryClassArchitecture(className)
            
            // 跨层调用的业务价值（正确的跨层调用体现业务完整性）
            val crossLayerValue = calculateCrossLayerValue(archInfo)
            score += crossLayerValue
            logger.debug("类 $className 跨层架构价值: $crossLayerValue")
            
            // 依赖复杂度价值（适度的依赖体现业务重要性）
            val dependencyValue = calculateDependencyComplexityValue(archInfo)
            score += dependencyValue
            logger.debug("类 $className 依赖复杂度价值: $dependencyValue")
        }
        
        val finalScore = min(score / classNames.size, 100.0)
        logger.debug("路径 ${path.id} 架构价值总分: $finalScore")
        return finalScore
    }
    
    private fun calculateCrossLayerValue(archInfo: ClassArchitectureInfo): Double {
        val uniqueLayers = (archInfo.dependencyLayers + archInfo.layer).distinct()
        
        return when {
            uniqueLayers.size >= 4 -> 40.0  // 完整的4层架构
            uniqueLayers.size == 3 -> 30.0  // 3层架构
            uniqueLayers.size == 2 -> 20.0  // 2层架构
            else -> 10.0                    // 单层
        }
    }
    
    private fun calculateDependencyComplexityValue(archInfo: ClassArchitectureInfo): Double {
        val dependencyCount = archInfo.dependencies.size
        
        return when {
            dependencyCount > 10 -> 15.0  // 高复杂度（可能过度设计，但体现重要性）
            dependencyCount > 5 -> 25.0   // 适中复杂度（最佳）
            dependencyCount > 2 -> 20.0   // 较低复杂度
            dependencyCount > 0 -> 10.0   // 最低复杂度
            else -> 5.0                   // 无依赖
        }
    }
    
    /**
     * 基于图数据库计算调用链完整性 (0-100分)
     */
    private fun calculateCallChainCompletenessFromGraph(path: CallPath): Double {
        var score = 0.0
        logger.debug("计算路径 ${path.id} 的调用链完整性")
        
        if (path.methods.size >= 2) {
            for (i in 0 until path.methods.size - 1) {
                val sourceMethod = path.methods[i]
                val targetMethod = path.methods[i + 1]
                
                if (sourceMethod.contains(".") && targetMethod.contains(".")) {
                    val sourceClass = sourceMethod.substringBeforeLast(".")
                    val sourceMethodName = sourceMethod.substringAfterLast(".")
                    val targetClass = targetMethod.substringBeforeLast(".")  
                    val targetMethodName = targetMethod.substringAfterLast(".")
                    
                    // 查询实际的调用路径
                    val chainInfo = neo4jQueryService.queryCallPathChain(sourceClass, sourceMethodName, targetClass, targetMethodName)
                    val chainScore = calculateSingleChainCompleteness(chainInfo)
                    score += chainScore
                    logger.debug("调用链 $sourceMethod -> $targetMethod 完整性: $chainScore")
                }
            }
        } else {
            // 单方法路径，检查其爆炸半径
            val method = path.methods.firstOrNull()
            if (method?.contains(".") == true) {
                val className = method.substringBeforeLast(".")
                val methodName = method.substringAfterLast(".")
                
                val blastRadius = neo4jQueryService.queryBlastRadius(className, methodName)
                score = calculateSingleMethodCompleteness(blastRadius)
                logger.debug("单方法 $method 完整性: $score")
            }
        }
        
        val finalScore = min(score / maxOf(path.methods.size - 1, 1), 100.0)
        logger.debug("路径 ${path.id} 调用链完整性总分: $finalScore") 
        return finalScore
    }
    
    private fun calculateSingleChainCompleteness(chainInfo: CallPathChainInfo): Double {
        var score = 0.0
        
        // 路径长度合理性
        score += when (chainInfo.pathLength) {
            1 -> 30.0    // 直接调用
            2 -> 40.0    // 2层调用（理想）
            3 -> 35.0    // 3层调用（良好）
            4 -> 25.0    // 4层调用（可接受）
            else -> 15.0 // 过长路径
        }
        
        // 层级跨越合理性
        if (!chainInfo.hasLayerViolations) {
            score += 30.0  // 无层级违规
        } else {
            score += 10.0  // 有违规但仍有价值
        }
        
        // 涉及层级数量
        val layerCount = chainInfo.layersInPath.distinct().size
        score += layerCount * 10.0
        
        return min(score, 80.0)
    }
    
    private fun calculateSingleMethodCompleteness(blastRadius: BlastRadiusInfo): Double {
        var score = 0.0
        
        // 基于影响范围
        val totalInfluence = blastRadius.directCallers + blastRadius.directCallees
        score += when {
            totalInfluence > 20 -> 60.0  // 高影响
            totalInfluence > 10 -> 50.0  // 中等影响  
            totalInfluence > 5 -> 40.0   // 较低影响
            totalInfluence > 0 -> 30.0   // 最低影响
            else -> 20.0                 // 无影响
        }
        
        // 涉及的架构层级
        score += blastRadius.affectedLayers.size * 8.0
        
        return min(score, 70.0)
    }
    
    /**
     * 基于Git变更上下文计算业务价值 - 作为图数据库分析的补充
     */
    private fun calculateGitChangeValue(path: CallPath, context: GitDiffContext): Double {
        var score = 0.0
        logger.debug("计算路径 ${path.id} 的Git变更补充价值")
        
        // 1. 文件类型影响 (0-30分)
        val fileTypeScore = calculateFileTypeImpact(path)
        score += fileTypeScore
        logger.debug("文件类型影响: $fileTypeScore")
        
        // 2. 变更规模影响 (0-25分)
        val changeSizeScore = calculateChangeSizeImpact(context)
        score += changeSizeScore
        logger.debug("变更规模影响: $changeSizeScore")
        
        // 3. 业务术语匹配 (0-20分)
        val businessTermScore = calculateBusinessTermMatching(path, context)
        score += businessTermScore
        logger.debug("业务术语匹配: $businessTermScore")
        
        // 4. 新功能检测 (0-15分)
        val newFeatureScore = detectNewFeatures(path, context)
        score += newFeatureScore
        logger.debug("新功能检测: $newFeatureScore")
        
        // 5. API端点价值 (0-10分)
        val apiEndpointScore = calculateApiEndpointValue(path)
        score += apiEndpointScore
        logger.debug("API端点价值: $apiEndpointScore")
        
        val finalScore = min(score, 100.0)
        logger.debug("路径 ${path.id} Git变更补充价值总分: $finalScore")
        return finalScore
    }
    
    private fun calculateFileTypeImpact(path: CallPath): Double {
        var score = 0.0
        
        path.relatedChanges.forEach { file ->
            val fileName = file.path.lowercase()
            score += when {
                fileName.contains("controller") || fileName.contains("rest") -> 20.0
                fileName.contains("service") -> 15.0
                fileName.contains("dto") || fileName.contains("vo") || fileName.contains("model") -> 12.0
                fileName.contains("repository") || fileName.contains("dao") -> 12.0
                fileName.contains("entity") -> 10.0
                fileName.contains("config") -> 8.0
                fileName.contains("util") || fileName.contains("helper") -> 5.0
                else -> 3.0
            }
        }
        
        return min(score, 30.0)
    }
    
    private fun calculateChangeSizeImpact(context: GitDiffContext): Double {
        val totalChanges = context.addedLines + context.deletedLines
        val fileCount = context.changedFiles.size
        
        var score = 0.0
        
        // 基于变更行数
        score += when {
            totalChanges > 500 -> 15.0
            totalChanges > 200 -> 12.0
            totalChanges > 100 -> 10.0
            totalChanges > 50 -> 8.0
            else -> 5.0
        }
        
        // 基于文件数量
        score += when {
            fileCount > 10 -> 10.0
            fileCount > 5 -> 8.0
            fileCount > 3 -> 6.0
            else -> 3.0
        }
        
        return min(score, 25.0)
    }
    
    private fun calculateBusinessTermMatching(path: CallPath, context: GitDiffContext): Double {
        val businessTerms = setOf(
            "user", "customer", "order", "product", "payment", "cart", "checkout",
            "account", "profile", "auth", "login", "register", "inventory"
        )
        
        var matches = 0
        val searchText = (path.description + " " + path.methods.joinToString(" ") + " " +
                         path.relatedChanges.joinToString(" ") { it.path } + " " +
                         context.commits.joinToString(" ") { it.message }).lowercase()
        
        businessTerms.forEach { term ->
            if (searchText.contains(term)) {
                matches++
            }
        }
        
        return min(matches * 4.0, 20.0)
    }
    
    private fun detectNewFeatures(path: CallPath, context: GitDiffContext): Double {
        var score = 0.0
        
        val allContent = path.relatedChanges.flatMap { file ->
            file.hunks.flatMap { hunk ->
                hunk.lines.filter { it.type == DiffLineType.ADDED }.map { it.content }
            }
        }.joinToString(" ").lowercase()
        
        // Spring MVC注解检测
        if (allContent.contains("@postmapping") || allContent.contains("@getmapping") ||
            allContent.contains("@putmapping") || allContent.contains("@deletemapping")) {
            score += 8.0
        }
        
        // 新类检测
        if (allContent.contains("public") && allContent.contains("class")) {
            score += 5.0
        }
        
        // Spring组件注解检测
        if (allContent.contains("@service") || allContent.contains("@component") ||
            allContent.contains("@repository")) {
            score += 2.0
        }
        
        return score
    }
    
    private fun calculateApiEndpointValue(path: CallPath): Double {
        val pathText = (path.description + " " + path.methods.joinToString(" ")).lowercase()
        
        var score = 0.0
        
        if (pathText.contains("controller")) {
            score += 6.0
        }
        
        if (pathText.contains("endpoint") || pathText.contains("api")) {
            score += 4.0
        }
        
        return score
    }
}