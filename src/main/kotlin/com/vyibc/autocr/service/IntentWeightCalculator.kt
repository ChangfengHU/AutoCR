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
     * 计算调用路径的意图权重 - 基于Neo4j图的上游连通性分析
     * 核心思路：修改点的上游链路越多 = 影响的业务入口越多 = 业务权重越高
     * @param path 调用路径
     * @param context Git差异上下文  
     * @return 意图权重 (0-100)
     */
    suspend fun calculateIntentWeight(path: CallPath, context: GitDiffContext): Double {
        logger.debug("开始基于Neo4j图计算路径 ${path.id} 的业务意图权重")
        
        try {
            // 1. 获取修改点的所有方法节点
            val methodNodes = extractMethodNodesFromPath(path)
            
            if (methodNodes.isEmpty()) {
                logger.warn("路径 ${path.id} 没有找到具体的方法节点")
                return 0.0
            }
            
            // 2. 计算每个修改点的上游连通性（向上追踪业务入口）
            var totalUpstreamConnectivity = 0.0
            var totalBusinessImportance = 0.0
            var totalCallFrequency = 0.0
            
            methodNodes.forEach { (className, methodName) ->
                logger.info("🔍 开始计算方法意图权重: $className.$methodName")
                
                // 计算上游连通性：有多少业务入口会调用到这个修改点
                val upstreamConnectivity = calculateUpstreamConnectivity(className, methodName)
                totalUpstreamConnectivity += upstreamConnectivity
                
                // 计算业务重要性：该节点在业务流程中的重要程度
                val businessImportance = calculateBusinessImportance(className, methodName)
                totalBusinessImportance += businessImportance
                
                // 计算调用频次：通过该节点的调用链条密度
                val callFrequency = calculateCallFrequency(className, methodName)
                totalCallFrequency += callFrequency
                
                logger.debug("方法 $className.$methodName: 上游连通性=$upstreamConnectivity, 业务重要性=$businessImportance, 调用频次=$callFrequency")
            }
            
            // 3. 加权平均计算最终业务权重
            val avgUpstreamConnectivity = totalUpstreamConnectivity / methodNodes.size
            val avgBusinessImportance = totalBusinessImportance / methodNodes.size  
            val avgCallFrequency = totalCallFrequency / methodNodes.size
            
            // 权重公式：上游连通性40% + 业务重要性35% + 调用频次25%
            val finalWeight = (avgUpstreamConnectivity * 0.4 + 
                             avgBusinessImportance * 0.35 + 
                             avgCallFrequency * 0.25).coerceIn(0.0, 100.0)
            
            logger.info("路径 ${path.id} 业务意图权重计算完成: $finalWeight")
            logger.debug("  - 平均上游连通性: $avgUpstreamConnectivity")  
            logger.debug("  - 平均业务重要性: $avgBusinessImportance")
            logger.debug("  - 平均调用频次: $avgCallFrequency")
            
            return finalWeight
            
        } catch (e: Exception) {
            logger.warn("计算路径 ${path.id} 业务意图权重失败: ${e.message}")
            return calculateFallbackIntentWeight(path, context)
        }
    }
    
    /**
     * 计算上游连通性：从当前节点向上遍历，统计能到达的业务入口数量
     * 业务入口包括：Controller、API、定时任务、消息队列监听器等
     */
    private suspend fun calculateUpstreamConnectivity(className: String, methodName: String): Double {
        try {
            logger.info("📈 开始计算上游连通性: $className.$methodName")
            
            // 查询该方法的所有调用者（递归查询2-3层）
            val callersInfo = neo4jQueryService.queryMethodCallers(className, methodName)
            
            logger.info("✅ 上游连通性查询完成: 发现${callersInfo.totalCallers}个调用者")
            
            var connectivityScore = 0.0
            
            // 1. 直接调用者权重
            val directCallerScore = minOf(callersInfo.totalCallers * 8.0, 40.0) // 每个直接调用者8分，最高40分
            connectivityScore += directCallerScore
            
            // 2. 业务入口层调用者加权（Controller、API等）
            val businessEntryScore = callersInfo.callerDetails.sumOf { caller ->
                when {
                    caller.layer.contains("CONTROLLER", ignoreCase = true) -> 15.0
                    caller.layer.contains("API", ignoreCase = true) -> 12.0
                    caller.layer.contains("SCHEDULER", ignoreCase = true) -> 10.0
                    caller.layer.contains("LISTENER", ignoreCase = true) -> 8.0
                    caller.layer.contains("SERVICE", ignoreCase = true) -> 5.0
                    else -> 2.0
                }
            }.coerceAtMost(35.0)
            connectivityScore += businessEntryScore
            
            // 3. 跨层级调用链奖励（体现业务流程重要性）
            val crossLayerScore = minOf(callersInfo.callerDetails.map { it.layer }.distinct().size * 5.0, 25.0)
            connectivityScore += crossLayerScore
            
            logger.debug("$className.$methodName 上游连通性: 直接调用者=$directCallerScore, 业务入口=$businessEntryScore, 跨层级=$crossLayerScore")
            
            return connectivityScore.coerceIn(0.0, 100.0)
            
        } catch (e: Exception) {
            logger.debug("计算上游连通性失败: ${e.message}")
            return 0.0
        }
    }
    
    /**
     * 计算业务重要性：基于节点在业务流程中的位置和角色
     */
    private suspend fun calculateBusinessImportance(className: String, methodName: String): Double {
        try {
            val archInfo = neo4jQueryService.queryClassArchitecture(className)
            
            var importanceScore = 0.0
            
            // 1. 基于架构层级的重要性
            val layerImportance = when (archInfo.layer) {
                "CONTROLLER" -> 25.0  // 控制层：直接面向用户/API，重要性最高
                "SERVICE" -> 35.0     // 服务层：业务逻辑核心，重要性很高
                "REPOSITORY" -> 20.0  // 数据层：数据操作，重要性中等
                "UTIL" -> 10.0        // 工具层：辅助功能，重要性较低
                else -> 15.0
            }
            importanceScore += layerImportance
            
            // 2. 基于业务关键词的重要性加权
            val businessKeywordScore = calculateBusinessKeywordImportance(className, methodName)
            importanceScore += businessKeywordScore
            
            // 3. 基于依赖复杂度的重要性（依赖越多可能越重要）
            val dependencyImportance = minOf(archInfo.dependencies.size * 3.0, 20.0)
            importanceScore += dependencyImportance
            
            // 4. 基于接口实现的重要性（实现接口的类通常更重要）
            val interfaceImportance = minOf(archInfo.interfaces.size * 8.0, 15.0)
            importanceScore += interfaceImportance
            
            logger.debug("$className.$methodName 业务重要性: 层级=$layerImportance, 关键词=$businessKeywordScore, 依赖=$dependencyImportance, 接口=$interfaceImportance")
            
            return importanceScore.coerceIn(0.0, 100.0)
            
        } catch (e: Exception) {
            logger.debug("计算业务重要性失败: ${e.message}")
            return 0.0
        }
    }
    
    /**
     * 计算调用频次：基于通过该节点的调用链条密度
     */
    private suspend fun calculateCallFrequency(className: String, methodName: String): Double {
        try {
            // 同时查询上游调用者和下游被调用者，计算调用链条的总密度
            val callersInfo = neo4jQueryService.queryMethodCallers(className, methodName)
            val calleesInfo = neo4jQueryService.queryMethodCallees(className, methodName)
            
            var frequencyScore = 0.0
            
            // 1. 上游调用频次贡献
            val upstreamFrequency = callersInfo.callerDetails.sumOf { it.callCount }.toDouble().coerceAtMost(50.0)
            frequencyScore += upstreamFrequency * 0.3
            
            // 2. 下游调用频次贡献  
            val downstreamFrequency = calleesInfo.calleeDetails.sumOf { it.callCount }.toDouble().coerceAtMost(50.0)
            frequencyScore += downstreamFrequency * 0.2
            
            // 3. 调用链条长度奖励（长链条意味着该节点是重要的中转站）
            val chainLength = callersInfo.totalCallers + calleesInfo.totalCallees
            val chainLengthScore = minOf(chainLength * 2.0, 30.0)
            frequencyScore += chainLengthScore * 0.5
            
            logger.debug("$className.$methodName 调用频次: 上游=$upstreamFrequency, 下游=$downstreamFrequency, 链条长度=$chainLengthScore")
            
            return frequencyScore.coerceIn(0.0, 100.0)
            
        } catch (e: Exception) {
            logger.debug("计算调用频次失败: ${e.message}")
            return 0.0
        }
    }
    
    /**
     * 基于业务关键词计算重要性得分
     */
    private fun calculateBusinessKeywordImportance(className: String, methodName: String): Double {
        val fullName = "$className.$methodName".lowercase()
        var score = 0.0
        
        // 核心业务关键词权重表
        val coreBusinessKeywords = mapOf(
            "user" to 8.0, "order" to 8.0, "payment" to 10.0, "transaction" to 10.0,
            "auth" to 9.0, "login" to 7.0, "security" to 9.0, "account" to 6.0,
            "product" to 6.0, "inventory" to 7.0, "checkout" to 8.0, "cart" to 5.0
        )
        
        // 操作类型关键词权重表  
        val operationKeywords = mapOf(
            "create" to 6.0, "save" to 6.0, "update" to 7.0, "delete" to 8.0,
            "process" to 7.0, "execute" to 6.0, "handle" to 5.0, "validate" to 6.0
        )
        
        coreBusinessKeywords.forEach { (keyword, weight) ->
            if (fullName.contains(keyword)) score += weight
        }
        
        operationKeywords.forEach { (keyword, weight) ->
            if (fullName.contains(keyword)) score += weight
        }
        
        return minOf(score, 25.0) // 最高25分
    }
    
    /**
     * 从调用路径中提取方法节点信息
     */
    private fun extractMethodNodesFromPath(path: CallPath): List<Pair<String, String>> {
        val methodNodes = mutableListOf<Pair<String, String>>()
        
        path.methods.forEach { methodPath ->
            if (methodPath.contains(".")) {
                val className = methodPath.substringBeforeLast(".")
                val methodName = methodPath.substringAfterLast(".")
                if (className.isNotBlank() && methodName.isNotBlank()) {
                    methodNodes.add(className to methodName)
                }
            }
        }
        
        return methodNodes
    }
    
    /**
     * Fallback计算（当Neo4j查询失败时）
     */
    private fun calculateFallbackIntentWeight(path: CallPath, context: GitDiffContext): Double {
        var score = 20.0 // 基础分
        
        // 基于路径描述的简单分析
        val description = path.description.lowercase()
        
        // 业务关键词匹配
        val businessKeywords = listOf("user", "order", "payment", "controller", "service", "transaction")
        val matchCount = businessKeywords.count { description.contains(it) }
        score += matchCount * 5.0
        
        // 变更规模考虑
        val totalChanges = path.relatedChanges.sumOf { it.addedLines + it.deletedLines }
        score += when {
            totalChanges > 100 -> 15.0
            totalChanges > 50 -> 10.0
            totalChanges > 10 -> 5.0
            else -> 2.0
        }
        
        return score.coerceIn(0.0, 100.0)
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