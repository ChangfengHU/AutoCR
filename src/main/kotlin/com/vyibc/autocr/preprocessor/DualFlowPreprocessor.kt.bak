package com.vyibc.autocr.preprocessor

import com.vyibc.autocr.model.*
import com.vyibc.autocr.psi.FileAnalysisResult
import com.vyibc.autocr.git.GitDiffAnalyzer
import com.vyibc.autocr.cache.CacheService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.slf4j.LoggerFactory

/**
 * 双流智能预处理器
 * 集成意图权重计算器和风险权重计算器，为代码评审提供智能化的优先级排序
 */
@Service(Service.Level.PROJECT)
class DualFlowPreprocessor(private val project: Project) {
    private val logger = LoggerFactory.getLogger(DualFlowPreprocessor::class.java)
    private val intentCalculator = IntentWeightCalculator()
    private val riskCalculator = RiskWeightCalculator()
    private val cacheService = CacheService.getInstance(project)
    
    /**
     * 分析文件变更并计算综合权重
     */
    fun analyzeFileChanges(fileChanges: List<com.vyibc.autocr.model.FileChange>): DualFlowAnalysisReport {
        logger.info("Processing {} file changes with dual-flow analysis", fileChanges.size)
        
        val fileAnalyses = fileChanges.map { fileChange ->
            analyzeFileChange(fileChange)
        }
        
        return generateAnalysisReport(fileAnalyses, emptyList())
    }
    
    /**
     * 分析单个文件变更
     */
    fun analyzeFileChange(fileChange: com.vyibc.autocr.model.FileChange): FileChangeAnalysis {
        val cacheKey = "file_analysis_${fileChange.filePath}_${fileChange.hashCode()}"
        
        // 尝试从缓存获取结果（简化版本，暂时跳过缓存）
        /*
        cacheService.get(cacheKey)?.let { cachedResult ->
            if (cachedResult is FileChangeAnalysis) {
                logger.debug("Cache hit for file change analysis: {}", fileChange.filePath)
                return cachedResult
            }
        }
        */
        
        // 计算意图权重
        val intentWeight = intentCalculator.calculateFileIntentWeight(fileChange)
        
        // 计算风险权重
        val riskWeight = riskCalculator.calculateFileRiskWeight(fileChange)
        
        // 分析方法级别的变更
        val methodAnalyses = analyzeMethodChanges(fileChange)
        
        // 计算综合权重（意图权重 * 0.4 + 风险权重 * 0.6）
        val combinedWeight = (intentWeight * 0.4) + (riskWeight * 0.6)
        
        val analysis = FileChangeAnalysis(
            fileChange = fileChange,
            intentWeight = intentWeight,
            riskWeight = riskWeight,
            combinedWeight = combinedWeight,
            methodAnalyses = methodAnalyses,
            priorityLevel = determinePriorityLevel(combinedWeight),
            recommendedReviewTime = estimateReviewTime(fileChange, combinedWeight),
            suggestedReviewers = suggestReviewers(fileChange),
            analysisTimestamp = System.currentTimeMillis()
        )
        
        // 缓存结果（简化版本，暂时跳过缓存）
        // cacheService.put(cacheKey, analysis)
        
        return analysis
    }
    
    /**
     * 分析方法级别的变更
     */
    private fun analyzeMethodChanges(fileChange: com.vyibc.autocr.model.FileChange): List<MethodChangeAnalysis> {
        val methodAnalyses = mutableListOf<MethodChangeAnalysis>()
        
        // 分析新增方法
        fileChange.addedMethods.forEach { method ->
            val context = ChangeContext("NEW_METHOD", true)
            methodAnalyses.add(analyzeMethodChange(method, context))
        }
        
        // 分析修改的方法
        fileChange.modifiedMethods.forEach { method ->
            val context = ChangeContext("METHOD_MODIFIED", false)
            methodAnalyses.add(analyzeMethodChange(method, context))
        }
        
        // 分析删除的方法
        fileChange.deletedMethods.forEach { method ->
            val context = ChangeContext("METHOD_DELETED", false)
            methodAnalyses.add(analyzeMethodChange(method, context))
        }
        
        return methodAnalyses.sortedByDescending { it.combinedWeight }
    }
    
    /**
     * 分析单个方法变更
     */
    private fun analyzeMethodChange(method: MethodNode, context: ChangeContext): MethodChangeAnalysis {
        val intentWeight = intentCalculator.calculateMethodIntentWeight(method, context)
        val riskWeight = riskCalculator.calculateMethodRiskWeight(method, context)
        val combinedWeight = (intentWeight * 0.4) + (riskWeight * 0.6)
        
        return MethodChangeAnalysis(
            method = method,
            changeContext = context,
            intentWeight = intentWeight,
            riskWeight = riskWeight,
            combinedWeight = combinedWeight,
            riskFactors = identifyRiskFactors(method, context),
            intentFactors = identifyIntentFactors(method, context),
            reviewRequirements = generateReviewRequirements(method, context, combinedWeight)
        )
    }
    
    /**
     * 分析调用关系变更
     */
    fun analyzeCallChanges(
        callEdges: List<CallsEdge>,
        sourceMethod: MethodNode,
        targetMethods: List<MethodNode>
    ): List<CallChangeAnalysis> {
        return callEdges.mapIndexed { index, callEdge ->
            val targetMethod = targetMethods.getOrNull(index) ?: return@mapIndexed null
            
            val intentWeight = intentCalculator.calculateCallIntentWeight(callEdge, sourceMethod, targetMethod)
            val riskWeight = riskCalculator.calculateCallRiskWeight(callEdge, sourceMethod, targetMethod)
            val combinedWeight = (intentWeight * 0.4) + (riskWeight * 0.6)
            
            CallChangeAnalysis(
                callEdge = callEdge,
                sourceMethod = sourceMethod,
                targetMethod = targetMethod,
                intentWeight = intentWeight,
                riskWeight = riskWeight,
                combinedWeight = combinedWeight,
                callType = callEdge.callType,
                isNewCall = callEdge.isNewInMR,
                isModifiedCall = callEdge.isModifiedInMR
            )
        }.filterNotNull().sortedByDescending { it.combinedWeight }
    }
    
    /**
     * 生成综合分析报告
     */
    fun generateAnalysisReport(
        fileAnalyses: List<FileChangeAnalysis>,
        callAnalyses: List<CallChangeAnalysis>
    ): DualFlowAnalysisReport {
        val highPriorityFiles = fileAnalyses.filter { it.priorityLevel == PriorityLevel.HIGH }
        val mediumPriorityFiles = fileAnalyses.filter { it.priorityLevel == PriorityLevel.MEDIUM }
        val lowPriorityFiles = fileAnalyses.filter { it.priorityLevel == PriorityLevel.LOW }
        
        val totalEstimatedTime = fileAnalyses.sumOf { it.recommendedReviewTime }
        val avgCombinedWeight = fileAnalyses.map { it.combinedWeight }.average()
        
        val topRiskFactors = fileAnalyses
            .flatMap { it.methodAnalyses }
            .flatMap { it.riskFactors }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(10)
        
        return DualFlowAnalysisReport(
            totalFiles = fileAnalyses.size,
            highPriorityFiles = highPriorityFiles,
            mediumPriorityFiles = mediumPriorityFiles,
            lowPriorityFiles = lowPriorityFiles,
            totalCallChanges = callAnalyses.size,
            estimatedTotalReviewTime = totalEstimatedTime,
            averageCombinedWeight = avgCombinedWeight,
            topRiskFactors = topRiskFactors,
            recommendedReviewOrder = generateReviewOrder(fileAnalyses),
            analysisTimestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 确定优先级级别
     */
    private fun determinePriorityLevel(combinedWeight: Double): PriorityLevel {
        return when {
            combinedWeight >= 0.8 -> PriorityLevel.HIGH
            combinedWeight >= 0.5 -> PriorityLevel.MEDIUM
            else -> PriorityLevel.LOW
        }
    }
    
    /**
     * 估算评审时间（分钟）
     */
    private fun estimateReviewTime(fileChange: com.vyibc.autocr.model.FileChange, combinedWeight: Double): Int {
        val baseTime = when (fileChange.changeType) {
            com.vyibc.autocr.model.ChangeType.ADDED -> 20    // 新文件需要更多时间
            com.vyibc.autocr.model.ChangeType.MODIFIED -> 15 // 修改文件时间中等
            com.vyibc.autocr.model.ChangeType.DELETED -> 10  // 删除文件时间较少
        }
        
        val methodCount = fileChange.addedMethods.size + 
                         fileChange.modifiedMethods.size + 
                         fileChange.deletedMethods.size
        
        val methodTime = methodCount * 5 // 每个方法5分钟
        val weightMultiplier = 1.0 + combinedWeight // 权重越高，时间越长
        
        return ((baseTime + methodTime) * weightMultiplier).toInt()
    }
    
    /**
     * 建议评审员
     */
    private fun suggestReviewers(fileChange: com.vyibc.autocr.model.FileChange): List<String> {
        val reviewers = mutableListOf<String>()
        
        // 基于文件路径建议评审员
        when {
            fileChange.filePath.contains("/security/", ignoreCase = true) -> {
                reviewers.add("security-expert")
            }
            fileChange.filePath.contains("/config/", ignoreCase = true) -> {
                reviewers.add("devops-engineer")
            }
            fileChange.filePath.contains("/controller/", ignoreCase = true) -> {
                reviewers.add("api-expert")
            }
            fileChange.filePath.contains("/service/", ignoreCase = true) -> {
                reviewers.add("business-analyst")
            }
            fileChange.filePath.contains("/repository/", ignoreCase = true) -> {
                reviewers.add("database-expert")
            }
        }
        
        // 如果没有特定专家，添加通用评审员
        if (reviewers.isEmpty()) {
            reviewers.add("senior-developer")
        }
        
        return reviewers
    }
    
    /**
     * 识别风险因素
     */
    private fun identifyRiskFactors(method: MethodNode, context: ChangeContext): List<String> {
        val riskFactors = mutableListOf<String>()
        
        if (method.cyclomaticComplexity > 10) {
            riskFactors.add("HIGH_COMPLEXITY")
        }
        
        if (method.linesOfCode > 50) {
            riskFactors.add("LONG_METHOD")
        }
        
        if (context.changeType == "METHOD_DELETED") {
            riskFactors.add("METHOD_DELETION")
        }
        
        if (method.annotations.any { it.contains("@Transactional") }) {
            riskFactors.add("TRANSACTIONAL_OPERATION")
        }
        
        if (method.blockType in listOf(BlockType.CONTROLLER, BlockType.SERVICE)) {
            riskFactors.add("CORE_BUSINESS_LOGIC")
        }
        
        return riskFactors
    }
    
    /**
     * 识别意图因素
     */
    private fun identifyIntentFactors(method: MethodNode, context: ChangeContext): List<String> {
        val intentFactors = mutableListOf<String>()
        
        if (method.blockType == BlockType.CONTROLLER) {
            intentFactors.add("API_INTERFACE")
        }
        
        if (method.methodName.startsWith("create") || method.methodName.startsWith("save")) {
            intentFactors.add("DATA_CREATION")
        }
        
        if (method.methodName.startsWith("delete") || method.methodName.startsWith("remove")) {
            intentFactors.add("DATA_DELETION")
        }
        
        if (context.changeType == "NEW_METHOD") {
            intentFactors.add("NEW_FUNCTIONALITY")
        }
        
        return intentFactors
    }
    
    /**
     * 生成评审要求
     */
    private fun generateReviewRequirements(
        method: MethodNode,
        context: ChangeContext,
        combinedWeight: Double
    ): List<String> {
        val requirements = mutableListOf<String>()
        
        if (combinedWeight > 0.8) {
            requirements.add("SENIOR_REVIEWER_REQUIRED")
            requirements.add("DETAILED_TESTING_REQUIRED")
        }
        
        if (method.blockType == BlockType.CONTROLLER) {
            requirements.add("API_DOCUMENTATION_UPDATE")
        }
        
        if (method.annotations.any { it.contains("@Transactional") }) {
            requirements.add("DATABASE_IMPACT_REVIEW")
        }
        
        if (context.changeType == "METHOD_DELETED") {
            requirements.add("BREAKING_CHANGE_ANALYSIS")
        }
        
        return requirements
    }
    
    /**
     * 生成评审顺序
     */
    private fun generateReviewOrder(fileAnalyses: List<FileChangeAnalysis>): List<String> {
        return fileAnalyses
            .sortedWith(compareByDescending<FileChangeAnalysis> { it.combinedWeight }
                .thenBy { it.recommendedReviewTime })
            .map { it.fileChange.filePath }
    }
}

/**
 * 文件变更分析结果
 */
data class FileChangeAnalysis(
    val fileChange: com.vyibc.autocr.model.FileChange,
    val intentWeight: Double,
    val riskWeight: Double,
    val combinedWeight: Double,
    val methodAnalyses: List<MethodChangeAnalysis>,
    val priorityLevel: PriorityLevel,
    val recommendedReviewTime: Int, // 分钟
    val suggestedReviewers: List<String>,
    val analysisTimestamp: Long
)

/**
 * 方法变更分析结果
 */
data class MethodChangeAnalysis(
    val method: MethodNode,
    val changeContext: ChangeContext,
    val intentWeight: Double,
    val riskWeight: Double,
    val combinedWeight: Double,
    val riskFactors: List<String>,
    val intentFactors: List<String>,
    val reviewRequirements: List<String>
)

/**
 * 调用关系变更分析结果
 */
data class CallChangeAnalysis(
    val callEdge: CallsEdge,
    val sourceMethod: MethodNode,
    val targetMethod: MethodNode,
    val intentWeight: Double,
    val riskWeight: Double,
    val combinedWeight: Double,
    val callType: String,
    val isNewCall: Boolean,
    val isModifiedCall: Boolean
)

/**
 * 双流分析报告
 */
data class DualFlowAnalysisReport(
    val totalFiles: Int,
    val highPriorityFiles: List<FileChangeAnalysis>,
    val mediumPriorityFiles: List<FileChangeAnalysis>,
    val lowPriorityFiles: List<FileChangeAnalysis>,
    val totalCallChanges: Int,
    val estimatedTotalReviewTime: Int,
    val averageCombinedWeight: Double,
    val topRiskFactors: List<Pair<String, Int>>,
    val recommendedReviewOrder: List<String>,
    val analysisTimestamp: Long
)

/**
 * 优先级级别
 */
enum class PriorityLevel {
    HIGH,    // 高优先级：需要立即关注
    MEDIUM,  // 中优先级：需要及时关注
    LOW      // 低优先级：可以延后关注
}