package com.vyibc.autocr.service

import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory
import kotlin.math.min

/**
 * 风险权重计算器 - 基于Neo4j图数据库的精确风险分析
 * 通过查询调用关系、架构层级、爆炸半径来准确评估技术风险
 */
class RiskWeightCalculator(private val neo4jQueryService: Neo4jQueryService) {
    
    private val logger = LoggerFactory.getLogger(RiskWeightCalculator::class.java)
    
    /**
     * 计算调用路径的风险权重 - 以Neo4j图分析为核心
     * @param path 调用路径
     * @param context Git差异上下文
     * @return 风险权重 (0-100)
     */
    fun calculateRiskWeight(path: CallPath, context: GitDiffContext): Double {
        logger.debug("开始基于Neo4j图数据库计算路径 ${path.id} 的风险权重")
        
        // 核心：基于图数据库的分析 (80%)
        val architecturalRiskFromGraph = calculateArchitecturalRiskFromGraph(path)
        val blastRadiusFromGraph = calculateBlastRadiusFromGraph(path)
        val layerViolationRiskFromGraph = calculateLayerViolationRiskFromGraph(path)
        
        // 辅助：基于Git变更的分析 (20%)
        val changeComplexityRisk = calculateGitChangeRisk(path, context)
        
        // 权重分配：图数据库80%，Git变更20%
        val graphWeight = (architecturalRiskFromGraph * 0.35 + blastRadiusFromGraph * 0.3 + layerViolationRiskFromGraph * 0.15) * 0.8
        val gitWeight = changeComplexityRisk * 0.2
        val totalWeight = graphWeight + gitWeight
        
        logger.debug("路径 ${path.id} 风险权重计算:")
        logger.debug("  - 图数据库分析 (80%): 架构风险=${"%.1f".format(architecturalRiskFromGraph)}, 爆炸半径=${"%.1f".format(blastRadiusFromGraph)}, 层级违规=${"%.1f".format(layerViolationRiskFromGraph)}")
        logger.debug("  - Git变更分析 (20%): ${"%.1f".format(changeComplexityRisk)}")
        logger.debug("  - 最终权重: ${"%.1f".format(totalWeight)}")
        
        return totalWeight
    }
    
    /**
     * 基于图数据库计算架构风险 (0-100分)
     * 通过分析类的架构位置、依赖关系来评估架构风险
     */
    private fun calculateArchitecturalRiskFromGraph(path: CallPath): Double {
        var risk = 0.0
        logger.debug("计算路径 ${path.id} 的架构风险")
        
        val classNames = path.methods.map { it.substringBeforeLast(".") }.distinct()
        
        classNames.forEach { className ->
            val archInfo = neo4jQueryService.queryClassArchitecture(className)
            
            // 1. 依赖复杂度风险
            val dependencyRisk = calculateDependencyComplexityRisk(archInfo)
            risk += dependencyRisk
            logger.debug("类 $className 依赖复杂度风险: $dependencyRisk")
            
            // 2. 接口实现风险
            val interfaceRisk = calculateInterfaceImplementationRisk(archInfo)
            risk += interfaceRisk
            logger.debug("类 $className 接口实现风险: $interfaceRisk")
            
            // 3. 继承层次风险
            val inheritanceRisk = calculateInheritanceHierarchyRisk(archInfo)
            risk += inheritanceRisk
            logger.debug("类 $className 继承层次风险: $inheritanceRisk")
        }
        
        val finalRisk = min(risk / classNames.size, 100.0)
        logger.debug("路径 ${path.id} 架构风险总分: $finalRisk")
        return finalRisk
    }
    
    private fun calculateDependencyComplexityRisk(archInfo: ClassArchitectureInfo): Double {
        val dependencyCount = archInfo.dependencies.size
        
        return when {
            dependencyCount > 15 -> 40.0  // 过度依赖，高风险
            dependencyCount > 10 -> 25.0  // 较高依赖，中等风险
            dependencyCount > 5 -> 10.0   // 适度依赖，较低风险
            else -> 5.0                   // 低依赖，较低风险
        }
    }
    
    private fun calculateInterfaceImplementationRisk(archInfo: ClassArchitectureInfo): Double {
        var risk = 0.0
        
        // 实现过多接口的风险
        if (archInfo.interfaces.size > 3) {
            risk += (archInfo.interfaces.size - 3) * 8.0
        }
        
        // 被过多类实现的接口风险
        if (archInfo.implementations.size > 5) {
            risk += (archInfo.implementations.size - 5) * 5.0
        }
        
        return min(risk, 30.0)
    }
    
    private fun calculateInheritanceHierarchyRisk(archInfo: ClassArchitectureInfo): Double {
        var risk = 0.0
        
        // 继承层次过深的风险
        val totalHierarchy = archInfo.parents.size + archInfo.children.size
        if (totalHierarchy > 4) {
            risk += (totalHierarchy - 4) * 6.0
        }
        
        return min(risk, 25.0)
    }
    
    /**
     * 基于图数据库计算爆炸半径风险 (0-100分)
     * 通过查询实际的调用关系来评估变更的影响范围
     */
    private fun calculateBlastRadiusFromGraph(path: CallPath): Double {
        var risk = 0.0
        logger.debug("计算路径 ${path.id} 的爆炸半径风险")
        
        path.methods.forEach { methodPath ->
            if (methodPath.contains(".")) {
                val className = methodPath.substringBeforeLast(".")
                val methodName = methodPath.substringAfterLast(".")
                
                // 查询方法的实际影响范围
                val blastRadius = neo4jQueryService.queryBlastRadius(className, methodName)
                val methodRisk = calculateSingleMethodBlastRisk(blastRadius)
                risk += methodRisk
                logger.debug("方法 $methodPath 爆炸半径风险: $methodRisk")
            }
        }
        
        val finalRisk = min(risk / path.methods.size, 100.0)
        logger.debug("路径 ${path.id} 爆炸半径风险总分: $finalRisk")
        return finalRisk
    }
    
    private fun calculateSingleMethodBlastRisk(blastRadius: BlastRadiusInfo): Double {
        var risk = 0.0
        
        // 直接调用者数量风险
        risk += when {
            blastRadius.directCallers > 20 -> 40.0  // 高影响
            blastRadius.directCallers > 10 -> 30.0  // 中等影响
            blastRadius.directCallers > 5 -> 20.0   // 较低影响
            else -> 10.0                            // 最低影响
        }
        
        // 间接调用者数量风险
        risk += when {
            blastRadius.indirectCallers > 50 -> 30.0
            blastRadius.indirectCallers > 20 -> 20.0
            blastRadius.indirectCallers > 10 -> 10.0
            else -> 5.0
        }
        
        // 跨层级影响风险
        val layerCount = blastRadius.affectedLayers.size
        if (layerCount > 3) {
            risk += (layerCount - 3) * 8.0
        }
        
        // 总影响类数量风险
        if (blastRadius.totalAffectedClasses > 30) {
            risk += 20.0
        }
        
        return min(risk, 80.0)
    }
    
    /**
     * 基于图数据库计算层级违规风险 (0-100分)
     * 通过查询实际的调用路径来检测架构层级违规
     */
    private fun calculateLayerViolationRiskFromGraph(path: CallPath): Double {
        var risk = 0.0
        logger.debug("计算路径 ${path.id} 的层级违规风险")
        
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
                    
                    if (chainInfo.hasLayerViolations) {
                        val violationRisk = calculateLayerViolationSeverity(chainInfo)
                        risk += violationRisk
                        logger.debug("调用链 $sourceMethod -> $targetMethod 层级违规风险: $violationRisk")
                    }
                }
            }
        } else {
            // 单方法路径，检查类的层级合理性
            val method = path.methods.firstOrNull()
            if (method?.contains(".") == true) {
                val className = method.substringBeforeLast(".")
                val archInfo = neo4jQueryService.queryClassArchitecture(className)
                risk = calculateSingleClassLayerRisk(archInfo)
                logger.debug("单方法 $method 层级风险: $risk")
            }
        }
        
        val finalRisk = min(risk / maxOf(path.methods.size - 1, 1), 100.0)
        logger.debug("路径 ${path.id} 层级违规风险总分: $finalRisk")
        return finalRisk
    }
    
    private fun calculateLayerViolationSeverity(chainInfo: CallPathChainInfo): Double {
        var severity = 0.0
        
        // 基础违规风险
        if (chainInfo.hasLayerViolations) {
            severity += 30.0
        }
        
        // 路径长度增加违规严重性
        if (chainInfo.pathLength > 3) {
            severity += (chainInfo.pathLength - 3) * 5.0
        }
        
        // 跨越层级数量
        val layerSpan = chainInfo.layersInPath.distinct().size
        if (layerSpan > 4) {
            severity += 15.0  // 跨越过多层级
        }
        
        return min(severity, 60.0)
    }
    
    private fun calculateSingleClassLayerRisk(archInfo: ClassArchitectureInfo): Double {
        var risk = 0.0
        
        // 检查依赖的层级合理性
        val validLayerTransitions = mapOf(
            "CONTROLLER" to setOf("SERVICE"),
            "SERVICE" to setOf("REPOSITORY", "UTIL"),
            "REPOSITORY" to setOf(),
            "UTIL" to setOf()
        )
        
        val allowedLayers = validLayerTransitions[archInfo.layer] ?: emptySet()
        val invalidDependencies = archInfo.dependencyLayers.count { it !in allowedLayers }
        
        if (invalidDependencies > 0) {
            risk += invalidDependencies * 15.0
        }
        
        return min(risk, 45.0)
    }
    
    /**
     * 基于Git变更计算风险 - 作为图数据库分析的补充 (0-100分)
     */
    private fun calculateGitChangeRisk(path: CallPath, context: GitDiffContext): Double {
        var risk = 0.0
        logger.debug("计算路径 ${path.id} 的Git变更补充风险")
        
        // 1. 敏感文件类型风险 (0-30分)
        val fileTypeRisk = calculateSensitiveFileTypeRisk(path)
        risk += fileTypeRisk
        logger.debug("敏感文件类型风险: $fileTypeRisk")
        
        // 2. 变更规模风险 (0-25分)
        val changeSizeRisk = calculateChangeSizeRisk(context)
        risk += changeSizeRisk
        logger.debug("变更规模风险: $changeSizeRisk")
        
        // 3. 敏感操作检测 (0-20分)
        val sensitiveOperationRisk = detectSensitiveOperations(path, context)
        risk += sensitiveOperationRisk
        logger.debug("敏感操作风险: $sensitiveOperationRisk")
        
        // 4. 删除操作风险 (0-15分)
        val deletionRisk = calculateDeletionRisk(context)
        risk += deletionRisk
        logger.debug("删除操作风险: $deletionRisk")
        
        // 5. 配置变更风险 (0-10分)
        val configChangeRisk = calculateConfigChangeRisk(context)
        risk += configChangeRisk
        logger.debug("配置变更风险: $configChangeRisk")
        
        val finalRisk = min(risk, 100.0)
        logger.debug("路径 ${path.id} Git变更补充风险总分: $finalRisk")
        return finalRisk
    }
    
    private fun calculateSensitiveFileTypeRisk(path: CallPath): Double {
        var risk = 0.0
        
        path.relatedChanges.forEach { file ->
            val fileName = file.path.lowercase()
            risk += when {
                fileName.contains("config") || fileName.endsWith(".properties") ||
                fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> 25.0
                fileName.contains("security") || fileName.contains("auth") -> 20.0
                fileName.contains("controller") || fileName.contains("rest") -> 12.0
                fileName.contains("database") || fileName.contains("migration") ||
                fileName.endsWith(".sql") -> 15.0
                fileName.contains("util") || fileName.contains("common") -> 18.0
                fileName.contains("service") -> 8.0
                else -> 3.0
            }
        }
        
        return min(risk, 30.0)
    }
    
    private fun calculateChangeSizeRisk(context: GitDiffContext): Double {
        val totalChanges = context.addedLines + context.deletedLines
        val fileCount = context.changedFiles.size
        
        var risk = 0.0
        
        // 基于变更行数的风险
        risk += when {
            totalChanges > 1000 -> 20.0  // 超大规模变更
            totalChanges > 500 -> 15.0   // 大规模变更
            totalChanges > 200 -> 12.0   // 中等规模变更
            totalChanges > 100 -> 8.0    // 较小规模变更
            else -> 5.0                  // 微小变更
        }
        
        // 基于文件数量的风险
        risk += when {
            fileCount > 20 -> 5.0
            fileCount > 10 -> 3.0
            else -> 0.0
        }
        
        return min(risk, 25.0)
    }
    
    private fun detectSensitiveOperations(path: CallPath, context: GitDiffContext): Double {
        var risk = 0.0
        
        val allContent = path.relatedChanges.flatMap { file ->
            file.hunks.flatMap { hunk ->
                hunk.lines.filter { it.type == DiffLineType.ADDED }.map { it.content }
            }
        }.joinToString(" ").lowercase()
        
        val sensitiveOperations = mapOf(
            "@transactional" to 8.0,
            "delete" to 6.0,
            "drop" to 10.0,
            "truncate" to 9.0,
            "alter" to 5.0,
            "@async" to 4.0,
            "@scheduled" to 4.0,
            "@preauthorize" to 7.0,
            "@postauthorize" to 6.0,
            "password" to 5.0,
            "token" to 4.0,
            "secret" to 8.0
        )
        
        sensitiveOperations.forEach { (operation, riskScore) ->
            if (allContent.contains(operation)) {
                risk += riskScore
            }
        }
        
        return min(risk, 20.0)
    }
    
    private fun calculateDeletionRisk(context: GitDiffContext): Double {
        var risk = 0.0
        
        val deletedFiles = context.changedFiles.count { it.changeType == FileChangeType.DELETED }
        if (deletedFiles > 0) {
            risk += deletedFiles * 5.0
        }
        
        val totalDeletedLines = context.changedFiles.sumOf { it.deletedLines }
        risk += when {
            totalDeletedLines > 500 -> 10.0
            totalDeletedLines > 200 -> 8.0
            totalDeletedLines > 100 -> 6.0
            totalDeletedLines > 50 -> 4.0
            else -> 2.0
        }
        
        return min(risk, 15.0)
    }
    
    private fun calculateConfigChangeRisk(context: GitDiffContext): Double {
        val configFiles = context.changedFiles.filter { file ->
            val fileName = file.path.lowercase()
            fileName.endsWith(".properties") || fileName.endsWith(".yml") ||
            fileName.endsWith(".yaml") || fileName.endsWith(".xml") ||
            fileName.contains("config")
        }
        
        return if (configFiles.isNotEmpty()) {
            min(configFiles.size * 5.0, 10.0)
        } else 0.0
    }
    private fun calculateArchitecturalRisk(path: CallPath, context: GitDiffContext): Double {
        var risk = 0.0
        logger.debug("计算路径 ${path.id} 的架构风险")
        
        // 1. 文件类型风险评估
        val fileTypeRisk = calculateFileTypeRisk(path, context)
        risk += fileTypeRisk
        logger.debug("文件类型风险: $fileTypeRisk")
        
        // 2. 层次架构风险
        val layerRisk = calculateLayerRisk(path)
        risk += layerRisk  
        logger.debug("层次架构风险: $layerRisk")
        
        // 3. 敏感注解风险
        val annotationRisk = calculateAnnotationRisk(path, context)
        risk += annotationRisk
        logger.debug("敏感注解风险: $annotationRisk")
        
        // 4. 配置变更风险
        val configRisk = calculateConfigurationRisk(context)
        risk += configRisk
        logger.debug("配置变更风险: $configRisk")
        
        val finalRisk = min(risk, 100.0)
        logger.debug("路径 ${path.id} 架构风险总分: $finalRisk")
        return finalRisk
    }
    
    private fun calculateFileTypeRisk(path: CallPath, context: GitDiffContext): Double {
        var risk = 0.0
        
        path.relatedChanges.forEach { file ->
            val fileName = file.path.lowercase()
            risk += when {
                fileName.contains("config") || fileName.endsWith(".properties") || 
                fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> 25.0
                fileName.contains("security") -> 20.0
                fileName.contains("controller") || fileName.contains("rest") -> 15.0
                fileName.contains("service") -> 12.0
                fileName.contains("repository") || fileName.contains("dao") -> 10.0
                fileName.contains("entity") || fileName.contains("model") -> 8.0
                fileName.contains("util") || fileName.contains("helper") -> 5.0
                else -> 2.0
            }
        }
        
        return min(risk, 30.0)
    }
    
    private fun calculateLayerRisk(path: CallPath): Double {
        if (path.methods.size < 2) return 0.0
        
        // 检查层次违规
        val layers = path.methods.map { getMethodLayer(it) }
        var violations = 0
        
        for (i in 0 until layers.size - 1) {
            val currentLayer = layers[i]
            val nextLayer = layers[i + 1]
            
            if (isLayerViolation(currentLayer, nextLayer)) {
                violations++
            }
        }
        
        return violations * 15.0 // 每个违规扣15分
    }
    
    private fun calculateAnnotationRisk(path: CallPath, context: GitDiffContext): Double {
        var risk = 0.0
        
        // 从变更内容中检查敏感注解
        val allContent = path.relatedChanges.flatMap { file ->
            file.hunks.flatMap { hunk ->
                hunk.lines.filter { it.type == DiffLineType.ADDED }.map { it.content }
            }
        }.joinToString(" ").lowercase()
        
        val riskAnnotations = mapOf(
            "@transactional" to 15.0,
            "@async" to 12.0,
            "@scheduled" to 10.0,
            "@preauthorize" to 18.0,
            "@postauthorize" to 16.0,
            "@cacheable" to 8.0,
            "@cacheevict" to 10.0,
            "@retryable" to 12.0,
            "@lock" to 20.0
        )
        
        riskAnnotations.forEach { (annotation, score) ->
            if (allContent.contains(annotation)) {
                risk += score
            }
        }
        
        return min(risk, 25.0)
    }
    
    private fun calculateConfigurationRisk(context: GitDiffContext): Double {
        val configFiles = context.changedFiles.filter { file ->
            val fileName = file.path.lowercase()
            fileName.endsWith(".properties") || fileName.endsWith(".yml") || 
            fileName.endsWith(".yaml") || fileName.endsWith(".xml") ||
            fileName.contains("config")
        }
        
        return if (configFiles.isNotEmpty()) {
            min(configFiles.size * 10.0, 20.0)
        } else 0.0
    }
    
    /**
     * 计算爆炸半径 (0-100分)
     */
    private fun calculateBlastRadius(path: CallPath, context: GitDiffContext): Double {
        var blastRadius = 0.0
        logger.debug("计算路径 ${path.id} 的爆炸半径")
        
        // 基础分数
        blastRadius += 5.0
        
        // 1. 基于文件重要性的影响半径
        val fileImportanceImpact = calculateFileImportanceImpact(path)
        blastRadius += fileImportanceImpact
        logger.debug("文件重要性影响: $fileImportanceImpact")
        
        // 2. 基于变更范围的影响
        val changeScopeImpact = calculateChangeScopeImpact(path, context)
        blastRadius += changeScopeImpact
        logger.debug("变更范围影响: $changeScopeImpact")
        
        // 3. 公共组件风险
        val publicComponentRisk = calculatePublicComponentRisk(path)
        blastRadius += publicComponentRisk
        logger.debug("公共组件风险: $publicComponentRisk")
        
        // 4. 数据操作风险
        val dataOperationRisk = calculateDataOperationRisk(path, context)
        blastRadius += dataOperationRisk
        logger.debug("数据操作风险: $dataOperationRisk")
        
        val finalRadius = min(blastRadius, 100.0)
        logger.debug("路径 ${path.id} 爆炸半径总分: $finalRadius")
        return finalRadius
    }
    
    private fun calculateFileImportanceImpact(path: CallPath): Double {
        var impact = 0.0
        
        path.relatedChanges.forEach { file ->
            val fileName = file.path.lowercase()
            impact += when {
                fileName.contains("controller") -> 20.0  // 控制层影响大
                fileName.contains("config") -> 25.0     // 配置变更影响最大
                fileName.contains("security") -> 22.0   // 安全相关影响大
                fileName.contains("service") -> 15.0    // 服务层中等影响
                fileName.contains("entity") -> 18.0     // 实体变更影响大
                fileName.contains("repository") -> 12.0 // 数据层中等影响
                fileName.contains("util") -> 30.0       // 工具类影响最大
                fileName.contains("common") -> 28.0     // 公共组件影响大
                else -> 5.0
            }
        }
        
        return min(impact, 40.0)
    }
    
    private fun calculateChangeScopeImpact(path: CallPath, context: GitDiffContext): Double {
        val totalChangedLines = context.changedFiles.sumOf { it.addedLines + it.deletedLines }
        val changedFileCount = context.changedFiles.size
        
        var impact = 0.0
        
        // 基于变更行数
        impact += when {
            totalChangedLines > 500 -> 25.0
            totalChangedLines > 200 -> 20.0
            totalChangedLines > 100 -> 15.0
            totalChangedLines > 50 -> 10.0
            else -> 5.0
        }
        
        // 基于变更文件数
        impact += when {
            changedFileCount > 10 -> 15.0
            changedFileCount > 5 -> 12.0
            changedFileCount > 3 -> 8.0
            else -> 3.0
        }
        
        return impact
    }
    
    private fun calculatePublicComponentRisk(path: CallPath): Double {
        var risk = 0.0
        
        val allText = (path.description + " " + path.methods.joinToString(" ")).lowercase()
        
        // 检查是否涉及公共组件
        val publicKeywords = listOf("util", "helper", "common", "shared", "base", "abstract")
        publicKeywords.forEach { keyword ->
            if (allText.contains(keyword)) {
                risk += 8.0
            }
        }
        
        return min(risk, 20.0)
    }
    
    private fun calculateDataOperationRisk(path: CallPath, context: GitDiffContext): Double {
        var risk = 0.0
        
        // 检查是否涉及数据库操作
        val allContent = path.relatedChanges.flatMap { file ->
            file.hunks.flatMap { hunk ->
                hunk.lines.map { it.content }
            }
        }.joinToString(" ").lowercase()
        
        val dataKeywords = mapOf(
            "@transactional" to 12.0,
            "delete" to 15.0,
            "drop" to 20.0,
            "truncate" to 18.0,
            "alter" to 10.0,
            "update" to 8.0,
            "insert" to 5.0
        )
        
        dataKeywords.forEach { (keyword, score) ->
            if (allContent.contains(keyword)) {
                risk += score
            }
        }
        
        return min(risk, 25.0)
    }
    
    /**
     * 计算变更复杂度 (0-100分)
     */
    private fun calculateChangeComplexity(path: CallPath, context: GitDiffContext): Double {
        var complexity = 0.0
        logger.debug("计算路径 ${path.id} 的变更复杂度")
        
        // 基础复杂度
        complexity += 8.0
        
        // 1. 方法数量复杂度
        val methodComplexity = calculateMethodComplexity(path)
        complexity += methodComplexity
        logger.debug("方法数量复杂度: $methodComplexity")
        
        // 2. 变更行数复杂度  
        val lineComplexity = calculateLineComplexity(context)
        complexity += lineComplexity
        logger.debug("变更行数复杂度: $lineComplexity")
        
        // 3. 文件类型复杂度
        val fileTypeComplexity = calculateFileTypeComplexity(path)
        complexity += fileTypeComplexity
        logger.debug("文件类型复杂度: $fileTypeComplexity")
        
        // 4. 变更操作复杂度
        val operationComplexity = calculateOperationComplexity(context)
        complexity += operationComplexity
        logger.debug("变更操作复杂度: $operationComplexity")
        
        val finalComplexity = min(complexity, 100.0)
        logger.debug("路径 ${path.id} 变更复杂度总分: $finalComplexity")
        return finalComplexity
    }
    
    private fun calculateMethodComplexity(path: CallPath): Double {
        val methodCount = path.methods.size
        
        return when {
            methodCount > 10 -> 25.0
            methodCount > 6 -> 20.0
            methodCount > 3 -> 15.0
            methodCount > 1 -> 10.0
            else -> 5.0
        }
    }
    
    private fun calculateLineComplexity(context: GitDiffContext): Double {
        val totalLines = context.changedFiles.sumOf { it.addedLines + it.deletedLines }
        
        return when {
            totalLines > 1000 -> 30.0
            totalLines > 500 -> 25.0
            totalLines > 200 -> 20.0
            totalLines > 100 -> 15.0
            totalLines > 50 -> 10.0
            else -> 5.0
        }
    }
    
    private fun calculateFileTypeComplexity(path: CallPath): Double {
        var complexity = 0.0
        
        val fileTypes = path.relatedChanges.map { file ->
            val fileName = file.path.lowercase()
            when {
                fileName.endsWith(".sql") -> "DATABASE"
                fileName.contains("config") || fileName.endsWith(".properties") || 
                fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> "CONFIG"
                fileName.contains("security") -> "SECURITY"
                fileName.contains("controller") -> "CONTROLLER"
                fileName.contains("service") -> "SERVICE"
                else -> "REGULAR"
            }
        }.distinct()
        
        // 不同类型文件的复杂度权重
        fileTypes.forEach { type ->
            complexity += when (type) {
                "DATABASE" -> 15.0
                "CONFIG" -> 12.0
                "SECURITY" -> 10.0
                "CONTROLLER" -> 8.0
                "SERVICE" -> 6.0
                else -> 3.0
            }
        }
        
        return min(complexity, 25.0)
    }
    
    private fun calculateOperationComplexity(context: GitDiffContext): Double {
        var complexity = 0.0
        
        // 统计操作类型
        val hasAdditions = context.changedFiles.any { it.changeType == FileChangeType.ADDED }
        val hasDeletions = context.changedFiles.any { it.changeType == FileChangeType.DELETED }
        val hasModifications = context.changedFiles.any { it.changeType == FileChangeType.MODIFIED }
        
        if (hasAdditions) complexity += 5.0
        if (hasDeletions) complexity += 10.0  // 删除操作更复杂
        if (hasModifications) complexity += 7.0
        
        // 混合操作额外复杂度
        val operationTypes = listOf(hasAdditions, hasDeletions, hasModifications).count { it }
        if (operationTypes > 1) {
            complexity += operationTypes * 3.0
        }
        
        return complexity
    }
    
    // === 架构风险评估辅助方法 ===
    
    private fun hasLayerViolation(path: CallPath): Boolean {
        val layers = path.methods.map { getMethodLayer(it) }
        
        // 检查是否有跨层调用
        for (i in 0 until layers.size - 1) {
            val currentLayer = layers[i]
            val nextLayer = layers[i + 1]
            
            if (isLayerViolation(currentLayer, nextLayer)) {
                return true
            }
        }
        return false
    }
    
    private fun getLayerViolationType(path: CallPath): LayerViolationType {
        val layers = path.methods.map { getMethodLayer(it) }
        
        for (i in 0 until layers.size - 1) {
            val currentLayer = layers[i]
            val nextLayer = layers[i + 1]
            
            when {
                currentLayer == "CONTROLLER" && nextLayer == "DAO" -> return LayerViolationType.CONTROLLER_TO_DAO
                currentLayer == "SERVICE" && nextLayer == "CONTROLLER" -> return LayerViolationType.SERVICE_TO_CONTROLLER
                currentLayer == "UTIL" && nextLayer == "SERVICE" -> return LayerViolationType.UTIL_TO_SERVICE
            }
        }
        return LayerViolationType.NONE
    }
    
    private fun getSensitiveAnnotations(path: CallPath, context: GitDiffContext): List<String> {
        val annotations = mutableSetOf<String>()
        
        // 从方法签名中提取注解
        path.methods.forEach { method ->
            val sensitivePatterns = listOf(
                "@Transactional", "@Async", "@Scheduled", "@Cacheable", "@CacheEvict",
                "@PreAuthorize", "@PostAuthorize", "@Lock", "@Retryable"
            )
            sensitivePatterns.forEach { pattern ->
                if (method.contains(pattern, ignoreCase = true)) {
                    annotations.add(pattern)
                }
            }
        }
        
        // 从变更内容中提取注解
        context.changedFiles.forEach { file ->
            file.hunks.forEach { hunk ->
                hunk.lines.filter { it.type == DiffLineType.ADDED }.forEach { line ->
                    val sensitivePatterns = listOf(
                        "@Transactional", "@Async", "@Scheduled", "@Cacheable", "@CacheEvict",
                        "@PreAuthorize", "@PostAuthorize", "@Lock", "@Retryable"
                    )
                    sensitivePatterns.forEach { pattern ->
                        if (line.content.contains(pattern, ignoreCase = true)) {
                            annotations.add(pattern)
                        }
                    }
                }
            }
        }
        
        return annotations.toList()
    }
    
    private fun hasCircularDependency(path: CallPath): Boolean {
        val methodClasses = path.methods.map { extractClassName(it) }
        
        // 简单检测：如果同一个类在路径中出现多次，可能存在循环依赖
        return methodClasses.distinct().size < methodClasses.size
    }
    
    private fun getCircularDependencyLength(path: CallPath): Int {
        val methodClasses = path.methods.map { extractClassName(it) }
        val classOccurrences = methodClasses.groupingBy { it }.eachCount()
        
        return classOccurrences.values.maxOrNull() ?: 1
    }
    
    private fun evaluateSOLIDViolations(path: CallPath, context: GitDiffContext): List<String> {
        val violations = mutableListOf<String>()
        
        // 单一责任原则违规：一个类包含太多不同类型的方法
        val classMethods = path.methods.groupBy { extractClassName(it) }
        classMethods.forEach { (className, methods) ->
            val methodTypes = methods.map { getMethodType(it) }.distinct()
            if (methodTypes.size > 3) {
                violations.add("SRP: $className has too many responsibilities")
            }
        }
        
        // 开放封闭原则违规：直接修改现有类而不是扩展
        val modifiedFiles = context.changedFiles.filter { it.changeType == FileChangeType.MODIFIED }
        if (modifiedFiles.size > context.changedFiles.filter { it.changeType == FileChangeType.ADDED }.size) {
            violations.add("OCP: More modifications than extensions")
        }
        
        return violations
    }
    
    private fun evaluateConcurrencyRisk(path: CallPath, context: GitDiffContext): Double {
        var risk = 0.0
        
        // 检查共享状态访问
        val hasSharedState = path.methods.any { method ->
            val lowerMethod = method.lowercase()
            lowerMethod.contains("static") || 
            lowerMethod.contains("singleton") ||
            lowerMethod.contains("volatile") ||
            lowerMethod.contains("synchronized")
        }
        if (hasSharedState) risk += 0.5
        
        // 检查并发相关的变更
        val hasConcurrencyChanges = context.changedFiles.any { file ->
            file.hunks.any { hunk ->
                hunk.lines.any { line ->
                    line.type == DiffLineType.ADDED &&
                    (line.content.contains("Thread") ||
                     line.content.contains("Concurrent") ||
                     line.content.contains("synchronized") ||
                     line.content.contains("volatile"))
                }
            }
        }
        if (hasConcurrencyChanges) risk += 0.5
        
        return risk
    }
    
    // === 爆炸半径评估辅助方法 ===
    
    private fun calculateReferenceImpact(path: CallPath): Double {
        // 基于方法名估算被引用程度
        val publicMethods = path.methods.count { 
            !it.contains("private", ignoreCase = true) && 
            !it.contains("protected", ignoreCase = true)
        }
        
        val totalMethods = path.methods.size
        return if (totalMethods > 0) {
            publicMethods.toDouble() / totalMethods
        } else 0.0
    }
    
    private fun hasUtilityMethods(path: CallPath): Boolean {
        return path.methods.any { method ->
            val lowerMethod = method.lowercase()
            lowerMethod.contains("util") ||
            lowerMethod.contains("helper") ||
            lowerMethod.contains("common") ||
            lowerMethod.contains("tool")
        }
    }
    
    private fun hasCoreBusinessMethods(path: CallPath): Boolean {
        val coreBusinessTerms = setOf("user", "order", "product", "payment", "customer", "account")
        return path.methods.any { method ->
            val lowerMethod = method.lowercase()
            coreBusinessTerms.any { term -> lowerMethod.contains(term) }
        }
    }
    
    private fun hasInterfaceImplementations(path: CallPath): Boolean {
        return path.methods.any { method ->
            method.contains("implements", ignoreCase = true) ||
            method.contains("interface", ignoreCase = true) ||
            method.contains("@Override", ignoreCase = true)
        }
    }
    
    private fun hasDatabaseSchemaChanges(context: GitDiffContext): Boolean {
        return context.changedFiles.any { file ->
            val fileName = file.path.lowercase()
            fileName.contains("migration") ||
            fileName.contains("schema") ||
            fileName.contains("ddl") ||
            fileName.endsWith(".sql") ||
            (fileName.contains("entity") && file.hunks.any { hunk ->
                hunk.lines.any { line ->
                    line.type == DiffLineType.ADDED && 
                    (line.content.contains("@Column") || line.content.contains("@Table"))
                }
            })
        }
    }
    
    // === 变更复杂度评估辅助方法 ===
    
    private fun getChangedMethodCount(path: CallPath, context: GitDiffContext): Int {
        return context.changedFiles.sumOf { file ->
            file.hunks.count { hunk ->
                hunk.lines.any { line ->
                    line.type == DiffLineType.ADDED &&
                    path.methods.any { method ->
                        line.content.contains(method.substringAfterLast("."))
                    }
                }
            }
        }
    }
    
    private fun getTotalChangedLines(context: GitDiffContext): Int {
        return context.addedLines + context.deletedLines
    }
    
    private fun getNewDependencies(context: GitDiffContext): List<String> {
        val dependencies = mutableSetOf<String>()
        
        context.changedFiles.forEach { file ->
            file.hunks.forEach { hunk ->
                hunk.lines.filter { it.type == DiffLineType.ADDED }.forEach { line ->
                    if (line.content.contains("import ") && !line.content.contains("java.lang")) {
                        dependencies.add(line.content.substringAfter("import ").trim())
                    }
                }
            }
        }
        
        return dependencies.toList()
    }
    
    private fun hasConfigurationChanges(context: GitDiffContext): Boolean {
        return context.changedFiles.any { file ->
            val fileName = file.path.lowercase()
            fileName.endsWith(".properties") ||
            fileName.endsWith(".yml") ||
            fileName.endsWith(".yaml") ||
            fileName.endsWith(".xml") ||
            fileName.contains("config")
        }
    }
    
    private fun getAffectedModules(context: GitDiffContext): Set<String> {
        return context.changedFiles.map { file ->
            // 提取模块名称（基于路径的第一级目录）
            file.path.split("/").getOrNull(0) ?: "root"
        }.toSet()
    }
    
    private fun hasDeletionOperations(context: GitDiffContext): Boolean {
        return context.changedFiles.any { it.changeType == FileChangeType.DELETED } ||
               context.changedFiles.any { file ->
                   file.hunks.any { hunk ->
                       hunk.lines.count { it.type == DiffLineType.DELETED } > 
                       hunk.lines.count { it.type == DiffLineType.ADDED }
                   }
               }
    }
    
    // === 工具方法 ===
    
    private fun getMethodLayer(method: String): String {
        val lowerMethod = method.lowercase()
        return when {
            lowerMethod.contains("controller") -> "CONTROLLER"
            lowerMethod.contains("service") -> "SERVICE"
            lowerMethod.contains("repository") || lowerMethod.contains("dao") -> "DAO"
            lowerMethod.contains("util") || lowerMethod.contains("helper") -> "UTIL"
            else -> "UNKNOWN"
        }
    }
    
    private fun isLayerViolation(fromLayer: String, toLayer: String): Boolean {
        val validTransitions = mapOf(
            "CONTROLLER" to setOf("SERVICE"),
            "SERVICE" to setOf("REPOSITORY", "DAO", "UTIL"),
            "REPOSITORY" to setOf(),
            "DAO" to setOf(),
            "UTIL" to setOf()
        )
        
        return toLayer !in (validTransitions[fromLayer] ?: emptySet())
    }
    
    private fun extractClassName(method: String): String {
        return method.substringBeforeLast(".").substringAfterLast(".")
    }
    
    private fun getMethodType(method: String): String {
        val lowerMethod = method.lowercase()
        return when {
            lowerMethod.contains("get") -> "QUERY"
            lowerMethod.contains("set") || lowerMethod.contains("save") || lowerMethod.contains("create") -> "COMMAND"
            lowerMethod.contains("delete") || lowerMethod.contains("remove") -> "DELETE"
            lowerMethod.contains("update") || lowerMethod.contains("modify") -> "UPDATE"
            else -> "OTHER"
        }
    }
}

/**
 * 层级违规类型
 */
enum class LayerViolationType {
    NONE,
    CONTROLLER_TO_DAO,      // 控制器直接访问数据层
    SERVICE_TO_CONTROLLER,  // 服务层调用控制器层
    UTIL_TO_SERVICE        // 工具类调用服务层
}