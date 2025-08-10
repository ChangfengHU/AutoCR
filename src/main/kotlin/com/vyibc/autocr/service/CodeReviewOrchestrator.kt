package com.vyibc.autocr.service

import com.intellij.openapi.project.Project
import com.vyibc.autocr.action.CodeReviewResult
import com.vyibc.autocr.action.IntentAnalysisResult
import com.vyibc.autocr.action.RiskAnalysisResult
import com.vyibc.autocr.action.OverallRecommendation
import com.vyibc.autocr.action.AnalysisMetadata
import com.vyibc.autocr.model.*
import com.vyibc.autocr.settings.AutoCRSettingsState
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

/**
 * 代码评审编排器
 * 实现技术方案V5.1的三阶段AI分析流程：
 * 1. 快速筛选：轻量级AI模型筛选关键路径
 * 2. 深度研判：强大AI模型进行深度分析
 * 3. 异步可视化：同步结果到Neo4j
 */
class CodeReviewOrchestrator(private val project: Project) {
    
    private val logger = LoggerFactory.getLogger(CodeReviewOrchestrator::class.java)
    private val gitService = GitService(project)
    private val contextAggregator = ContextAggregator(project)
    private val pathAnalyzer = CallPathAnalyzer(project)
    private val aiOrchestrator = AIOrchestrator(project)
    private val settings = AutoCRSettingsState.getInstance(project)
    
    /**
     * 执行完整的代码评审分析
     */
    suspend fun performCodeReview(
        request: BranchComparisonRequest,
        progressCallback: (AnalysisProgress) -> Unit
    ): CodeReviewResult = withContext(Dispatchers.IO) {
        
        logger.info("开始AI代码评审: ${request.sourceBranch} -> ${request.targetBranch}")
        
        // 初始化调试信息收集器
        val debugInfoCollector = AnalysisDebugInfoCollector()
        val overallStartTime = System.currentTimeMillis()
        
        val result = try {
            // 阶段0: 初始化和Git分析 (10%)
            progressCallback(AnalysisProgress("准备阶段", "分析Git差异...", 10))
            val gitStageStart = System.currentTimeMillis()
            
            val gitDiffContext = gitService.analyzeBranchDifferences(request.sourceBranch, request.targetBranch)
            
            val gitStageEnd = System.currentTimeMillis()
            debugInfoCollector.addStageInfo("Git差异分析", gitStageStart, gitStageEnd, "SUCCESS", "成功分析了${gitDiffContext.changedFiles.size}个变更文件")
            debugInfoCollector.setGitDiffDetails(gitDiffContext)
            
            logger.info("🔍 Git差异分析完成：变更文件${gitDiffContext.changedFiles.size}个，新增${gitDiffContext.addedLines}行，删除${gitDiffContext.deletedLines}行")
            
            if (gitDiffContext.changedFiles.isEmpty()) {
                return@withContext createEmptyResult(request, "两个分支之间没有代码差异")
            }
            
            // 阶段1: 快速路径发现和预筛选 (20%)
            progressCallback(AnalysisProgress("路径发现", "识别相关调用路径...", 20))
            val pathStageStart = System.currentTimeMillis()
            
            val allPaths = pathAnalyzer.findRelevantPaths(gitDiffContext)
            
            val pathStageEnd = System.currentTimeMillis()
            debugInfoCollector.addStageInfo("路径发现", pathStageStart, pathStageEnd, "SUCCESS", "发现了${allPaths.size}个相关调用路径")
            debugInfoCollector.setDiscoveredPaths(allPaths)
            
            logger.info("🔗 路径发现完成：识别${allPaths.size}个调用路径")
            
            if (allPaths.isEmpty()) {
                return@withContext createEmptyResult(request, "未发现相关的调用路径")
            }
            
            // 阶段2: 双流智能预处理 (40%)
            progressCallback(AnalysisProgress("智能分析", "计算意图和风险权重...", 40))
            val preprocessStageStart = System.currentTimeMillis()
            
            val preprocessedPaths = performDualStreamPreprocessing(allPaths, gitDiffContext, request.analysisType, debugInfoCollector)
            
            val preprocessStageEnd = System.currentTimeMillis()
            debugInfoCollector.addStageInfo("双流预处理", preprocessStageStart, preprocessStageEnd, "SUCCESS", "完成${preprocessedPaths.size}个路径的权重计算")
            
            logger.info("⚖️ 双流预处理完成：计算了${preprocessedPaths.size}个路径的意图和风险权重")
            
            // 阶段3: AI快速筛选 (60%)
            progressCallback(AnalysisProgress("AI筛选", "筛选关键分析路径...", 60))
            val screeningStageStart = System.currentTimeMillis()
            
            val selectedPaths = performQuickAIScreening(preprocessedPaths, gitDiffContext, debugInfoCollector)
            
            val screeningStageEnd = System.currentTimeMillis()
            debugInfoCollector.addStageInfo("AI快速筛选", screeningStageStart, screeningStageEnd, "SUCCESS", "筛选出${selectedPaths.goldenPaths.size}个意图路径和${selectedPaths.riskPaths.size}个风险路径")
            
            logger.info("🤖 AI筛选完成：选择${selectedPaths.goldenPaths.size}个意图路径，${selectedPaths.riskPaths.size}个风险路径")
            
            // 阶段4: AI深度分析 (80%)
            progressCallback(AnalysisProgress("深度分析", "AI深度评审分析中...", 80))
            val analysisStageStart = System.currentTimeMillis()
            
            val analysisResult = performDeepAIAnalysis(selectedPaths, gitDiffContext, request, debugInfoCollector)
            
            val analysisStageEnd = System.currentTimeMillis()
            debugInfoCollector.addStageInfo("AI深度分析", analysisStageStart, analysisStageEnd, "SUCCESS", "完成深度分析，生成了${analysisResult.intentAnalysis.size}个意图分析和${analysisResult.riskAnalysis.size}个风险分析")
            
            // 阶段5: 异步Neo4j同步 (90%)
            progressCallback(AnalysisProgress("同步图谱", "更新知识图谱...", 90))
            val syncStageStart = System.currentTimeMillis()
            
            launch { 
                syncToNeo4j(gitDiffContext, selectedPaths) 
                val syncStageEnd = System.currentTimeMillis()
                debugInfoCollector.addStageInfo("Neo4j同步", syncStageStart, syncStageEnd, "SUCCESS", "异步同步到知识图谱")
            }
            
            progressCallback(AnalysisProgress("完成", "AI代码评审完成", 100))
            
            // 构建包含调试信息的结果
            analysisResult.copy(debugInfo = debugInfoCollector.build())
            
        } catch (e: Exception) {
            logger.error("代码评审失败", e)
            debugInfoCollector.addStageInfo("错误处理", System.currentTimeMillis(), System.currentTimeMillis(), "FAILED", "评审失败: ${e.message}")
            throw RuntimeException("AI代码评审失败: ${e.message}", e)
        }
        
        val overallEndTime = System.currentTimeMillis()
        logger.info("🎉 AI代码评审完成，总耗时: ${overallEndTime - overallStartTime}ms")
        
        return@withContext result
    }
    
    /**
     * 双流智能预处理：计算意图权重和风险权重
     */
    private suspend fun performDualStreamPreprocessing(
        paths: List<CallPath>,
        gitContext: GitDiffContext,
        analysisType: AnalysisType,
        debugInfoCollector: AnalysisDebugInfoCollector
    ): List<CallPath> = withContext(Dispatchers.Default) {
        
        logger.info("开始双流预处理，分析 ${paths.size} 条路径")
        
        val neo4jQuery = Neo4jQueryService()
        val intentCalculator = IntentWeightCalculator(neo4jQuery)
        val riskCalculator = RiskWeightCalculator(neo4jQuery)
        
        val intentCalculations = mutableListOf<com.vyibc.autocr.action.WeightCalculationDetail>()
        val riskCalculations = mutableListOf<com.vyibc.autocr.action.WeightCalculationDetail>()
        
        // 并行计算意图权重和风险权重
        val processedPaths = paths.map { path ->
            async {
                val intentWeight = if (analysisType != AnalysisType.RISK_FOCUSED) {
                    val weight = intentCalculator.calculateIntentWeight(path, gitContext)
                    
                    // 收集意图权重计算详情
                    intentCalculations.add(com.vyibc.autocr.action.WeightCalculationDetail(
                        pathId = path.id,
                        calculationType = "INTENT",
                        baseScore = weight,
                        adjustmentFactors = mapOf(
                            "businessValue" to 0.4,
                            "completeness" to 0.35,
                            "codeQuality" to 0.25
                        ),
                        finalScore = weight,
                        reasoning = "基于业务价值、完整性和代码质量的综合计算"
                    ))
                    
                    weight
                } else 0.0
                
                val riskWeight = if (analysisType != AnalysisType.INTENT_FOCUSED) {
                    val weight = riskCalculator.calculateRiskWeight(path, gitContext)
                    
                    // 收集风险权重计算详情
                    riskCalculations.add(com.vyibc.autocr.action.WeightCalculationDetail(
                        pathId = path.id,
                        calculationType = "RISK",
                        baseScore = weight,
                        adjustmentFactors = mapOf(
                            "architecturalRisk" to 0.4,
                            "blastRadius" to 0.35,
                            "changeComplexity" to 0.25
                        ),
                        finalScore = weight,
                        reasoning = "基于架构风险、影响半径和变更复杂度的综合计算"
                    ))
                    
                    weight
                } else 0.0
                
                path.copy(
                    intentWeight = intentWeight,
                    riskWeight = riskWeight
                )
            }
        }.awaitAll()
        
        // 根据分析类型排序
        val sortedPaths = when (analysisType) {
            AnalysisType.INTENT_FOCUSED -> processedPaths.sortedByDescending { it.intentWeight }
            AnalysisType.RISK_FOCUSED -> processedPaths.sortedByDescending { it.riskWeight }
            else -> processedPaths.sortedByDescending { it.intentWeight + it.riskWeight }
        }
        
        val avgIntentWeight = processedPaths.map { it.intentWeight }.average()
        val avgRiskWeight = processedPaths.map { it.riskWeight }.average()
        
        logger.info("双流预处理完成，平均意图权重: ${"%.2f".format(avgIntentWeight)}，平均风险权重: ${"%.2f".format(avgRiskWeight)}")
        
        // 设置预处理调试信息
        val selectionCriteria = when (analysisType) {
            AnalysisType.INTENT_FOCUSED -> "按意图权重降序排列，阈值>60.0"
            AnalysisType.RISK_FOCUSED -> "按风险权重降序排列，阈值>70.0"
            else -> "按意图+风险权重综合排列"
        }
        
        debugInfoCollector.setPreprocessingDetails(
            intentCalculations = intentCalculations,
            riskCalculations = riskCalculations,
            selectionCriteria = selectionCriteria,
            selectedPathsReason = "基于${analysisType.name}分析类型的智能路径筛选",
            calculationTimeMs = 0L // 实际应该是计算时间
        )
        
        return@withContext sortedPaths
    }
    
    /**
     * AI快速筛选：使用轻量级AI模型筛选关键路径
     */
    private suspend fun performQuickAIScreening(
        paths: List<CallPath>,
        gitContext: GitDiffContext,
        debugInfoCollector: AnalysisDebugInfoCollector
    ): com.vyibc.autocr.model.SelectedPaths {
        
        logger.info("开始AI快速筛选")
        
        // 选择轻量级AI模型进行快速筛选
        val screeningContext = ScreeningContext(
            allPaths = paths.take(20), // 限制输入路径数量
            changedFiles = gitContext.changedFiles,
            commitMessages = gitContext.commits.map { it.message }
        )
        
        return try {
            val result = aiOrchestrator.performQuickScreening(screeningContext, debugInfoCollector)
            logger.info("🤖 AI筛选完成：选择 ${result.goldenPaths.size} 条意图路径，${result.riskPaths.size} 条风险路径")
            result
        } catch (e: Exception) {
            logger.warn("AI筛选失败，使用规则筛选", e)
            
            // 记录AI筛选失败的调试信息
            debugInfoCollector.setAIInteractionDetails(
                quickPrompt = "AI筛选失败",
                quickResponse = "错误: ${e.message}",
                deepPrompt = "",
                deepResponse = "",
                provider = "fallback-rules",
                model = "rule-based",
                compressionApplied = false,
                tokenUsage = com.vyibc.autocr.action.TokenUsageInfo(0, 0, 0, 0.0),
                parsingDetails = "AI筛选失败，使用规则基础筛选"
            )
            
            performRuleBasedScreening(paths)
        }
    }
    
    /**
     * 规则基础的路径筛选（AI失败时的fallback）
     */
    private fun performRuleBasedScreening(paths: List<CallPath>): com.vyibc.autocr.model.SelectedPaths {
        val goldenPaths = paths
            .filter { it.intentWeight > 60.0 }
            .take(2)
            .map { com.vyibc.autocr.model.SelectedPath(it.id, "高意图权重路径", 0.8) }
        
        val riskPaths = paths
            .filter { it.riskWeight > 70.0 }
            .take(5)
            .map { com.vyibc.autocr.model.SelectedPath(it.id, "高风险权重路径", 0.7) }
        
        return com.vyibc.autocr.model.SelectedPaths(goldenPaths, riskPaths)
    }
    
    /**
     * AI深度分析：使用强大AI模型进行深入分析
     */
    private suspend fun performDeepAIAnalysis(
        selectedPaths: com.vyibc.autocr.model.SelectedPaths,
        gitContext: GitDiffContext,
        request: BranchComparisonRequest,
        debugInfoCollector: AnalysisDebugInfoCollector
    ): CodeReviewResult {
        
        logger.info("开始AI深度分析")
        
        // 聚合上下文信息
        val analysisContext = contextAggregator.buildAnalysisContext(
            gitContext = gitContext,
            selectedPaths = selectedPaths,
            analysisType = request.analysisType
        )
        
        return try {
            // 使用强大的AI模型进行深度分析
            val aiResult = aiOrchestrator.performDeepAnalysis(analysisContext, debugInfoCollector)
            
            logger.info("✅ AI深度分析完成：生成${aiResult.intentAnalysis.size}个意图分析，${aiResult.riskAnalysis.size}个风险分析")
            
            // 转换AI结果为标准格式
            CodeReviewResult(
                sourceBranch = request.sourceBranch,
                targetBranch = request.targetBranch,
                intentAnalysis = aiResult.intentAnalysis.map { 
                    IntentAnalysisResult(
                        description = it.description,
                        businessValue = it.businessValue,
                        implementationSummary = it.implementationSummary,
                        relatedPaths = it.relatedPaths,
                        confidence = it.confidence
                    )
                },
                riskAnalysis = aiResult.riskAnalysis.map {
                    RiskAnalysisResult(
                        description = it.description,
                        category = it.category,
                        severity = it.severity,
                        impact = it.impact,
                        recommendation = it.recommendation,
                        location = it.location
                    )
                },
                overallRecommendation = OverallRecommendation(
                    approvalStatus = aiResult.overallRecommendation.approvalStatus,
                    reasoning = aiResult.overallRecommendation.reasoning,
                    criticalIssues = aiResult.overallRecommendation.criticalIssues,
                    suggestions = aiResult.overallRecommendation.suggestions
                ),
                processingTime = System.currentTimeMillis(),
                analysisMetadata = AnalysisMetadata(
                    analyzedPaths = selectedPaths.goldenPaths.size + selectedPaths.riskPaths.size,
                    aiModel = aiResult.modelUsed,
                    tokensUsed = aiResult.tokensUsed
                )
            )
        } catch (e: Exception) {
            logger.error("❌ AI深度分析失败", e)
            
            // 记录深度分析失败的调试信息
            debugInfoCollector.setAIInteractionDetails(
                quickPrompt = "",
                quickResponse = "",
                deepPrompt = "深度分析失败",
                deepResponse = "错误: ${e.message}",
                provider = "failed",
                model = "none",
                compressionApplied = false,
                tokenUsage = com.vyibc.autocr.action.TokenUsageInfo(0, 0, 0, 0.0),
                parsingDetails = "AI深度分析失败: ${e.message}"
            )
            
            throw RuntimeException("AI分析失败: ${e.message}", e)
        }
    }
    
    /**
     * 异步同步到Neo4j
     */
    private suspend fun syncToNeo4j(gitContext: GitDiffContext, selectedPaths: com.vyibc.autocr.model.SelectedPaths) {
        try {
            logger.info("开始同步分析结果到Neo4j")
            // 这里可以调用现有的Neo4j导入服务
            // 将分析结果和路径信息同步到图数据库
            // TODO: 实现Neo4j同步逻辑
        } catch (e: Exception) {
            logger.warn("Neo4j同步失败", e)
        }
    }
    
    /**
     * 创建空结果（无变更情况）
     */
    private fun createEmptyResult(request: BranchComparisonRequest, reason: String): CodeReviewResult {
        return CodeReviewResult(
            sourceBranch = request.sourceBranch,
            targetBranch = request.targetBranch,
            intentAnalysis = emptyList(),
            riskAnalysis = emptyList(),
            overallRecommendation = OverallRecommendation(
                approvalStatus = "NO_CHANGES",
                reasoning = reason,
                criticalIssues = emptyList(),
                suggestions = emptyList()
            ),
            processingTime = System.currentTimeMillis(),
            analysisMetadata = AnalysisMetadata(0, "none", 0)
        )
    }
}

/**
 * 筛选上下文
 */
data class ScreeningContext(
    val allPaths: List<CallPath>,
    val changedFiles: List<ChangedFile>,
    val commitMessages: List<String>
)

/**
 * 分析调试信息收集器
 */
class AnalysisDebugInfoCollector {
    
    private val stageInfos = mutableListOf<com.vyibc.autocr.action.AnalysisStageInfo>()
    private var gitDiffDetails: com.vyibc.autocr.action.GitDiffDebugInfo? = null
    private val discoveredPaths = mutableListOf<com.vyibc.autocr.action.CallPathDebugInfo>()
    private var preprocessingDetails: com.vyibc.autocr.action.PreprocessingDebugInfo? = null
    private var aiInteractionDetails: com.vyibc.autocr.action.AIInteractionDebugInfo? = null
    
    fun addStageInfo(stageName: String, startTime: Long, endTime: Long, status: String, details: String) {
        stageInfos.add(com.vyibc.autocr.action.AnalysisStageInfo(
            stageName = stageName,
            startTime = startTime,
            endTime = endTime,
            durationMs = endTime - startTime,
            status = status,
            details = details,
            outputs = emptyMap()
        ))
    }
    
    fun setGitDiffDetails(gitContext: GitDiffContext) {
        gitDiffDetails = com.vyibc.autocr.action.GitDiffDebugInfo(
            totalChangedFiles = gitContext.changedFiles.size,
            addedLines = gitContext.addedLines,
            deletedLines = gitContext.deletedLines,
            changedFileDetails = gitContext.changedFiles.map { file ->
                com.vyibc.autocr.action.ChangedFileInfo(
                    path = file.path,
                    changeType = file.changeType.name,
                    addedLines = file.addedLines,
                    deletedLines = file.deletedLines,
                    keyChanges = extractKeyChanges(file)
                )
            },
            commitMessages = gitContext.commits.map { it.message },
            analysisTimeMs = 0L // 将在构建时设置
        )
    }
    
    fun setDiscoveredPaths(paths: List<CallPath>) {
        discoveredPaths.clear()
        discoveredPaths.addAll(paths.map { path ->
            com.vyibc.autocr.action.CallPathDebugInfo(
                pathId = path.id,
                description = path.description,
                methods = path.methods,
                intentWeight = path.intentWeight,
                riskWeight = path.riskWeight,
                discoveryReason = "通过静态分析发现的调用链路",
                relatedFiles = extractRelatedFiles(path)
            )
        })
    }
    
    fun setPreprocessingDetails(
        intentCalculations: List<com.vyibc.autocr.action.WeightCalculationDetail>,
        riskCalculations: List<com.vyibc.autocr.action.WeightCalculationDetail>,
        selectionCriteria: String,
        selectedPathsReason: String,
        calculationTimeMs: Long
    ) {
        preprocessingDetails = com.vyibc.autocr.action.PreprocessingDebugInfo(
            intentWeightCalculation = intentCalculations,
            riskWeightCalculation = riskCalculations,
            selectionCriteria = selectionCriteria,
            selectedPathsReason = selectedPathsReason,
            calculationTimeMs = calculationTimeMs
        )
    }
    
    fun setAIInteractionDetails(
        quickPrompt: String,
        quickResponse: String,
        deepPrompt: String,
        deepResponse: String,
        provider: String,
        model: String,
        compressionApplied: Boolean,
        tokenUsage: com.vyibc.autocr.action.TokenUsageInfo,
        parsingDetails: String
    ) {
        aiInteractionDetails = com.vyibc.autocr.action.AIInteractionDebugInfo(
            quickScreeningPrompt = quickPrompt,
            quickScreeningResponse = quickResponse,
            deepAnalysisPrompt = deepPrompt,
            deepAnalysisResponse = deepResponse,
            aiProvider = provider,
            modelUsed = model,
            contextCompressionApplied = compressionApplied,
            tokenUsageBreakdown = tokenUsage,
            responseParsingDetails = parsingDetails
        )
    }
    
    fun build(): com.vyibc.autocr.action.AnalysisDebugInfo {
        return com.vyibc.autocr.action.AnalysisDebugInfo(
            gitDiffDetails = gitDiffDetails ?: createEmptyGitDiffDetails(),
            discoveredPaths = discoveredPaths.toList(),
            preprocessingDetails = preprocessingDetails ?: createEmptyPreprocessingDetails(),
            aiInteractionDetails = aiInteractionDetails ?: createEmptyAIInteractionDetails(),
            stageDetails = stageInfos.toList()
        )
    }
    
    private fun extractKeyChanges(file: ChangedFile): List<String> {
        val keyChanges = mutableListOf<String>()
        file.hunks.forEach { hunk ->
            hunk.lines.filter { it.type == DiffLineType.ADDED }
                .take(3) // 只取前3个关键变更
                .forEach { line ->
                    keyChanges.add(line.content.trim().take(100)) // 限制长度
                }
        }
        return keyChanges
    }
    
    private fun extractRelatedFiles(path: CallPath): List<String> {
        return path.methods.mapNotNull { method ->
            // 从方法签名中提取可能的文件路径
            if (method.contains(".")) {
                method.substringBeforeLast(".").replace(".", "/") + ".java"
            } else null
        }.distinct()
    }
    
    private fun createEmptyGitDiffDetails(): com.vyibc.autocr.action.GitDiffDebugInfo {
        return com.vyibc.autocr.action.GitDiffDebugInfo(
            totalChangedFiles = 0,
            addedLines = 0,
            deletedLines = 0,
            changedFileDetails = emptyList(),
            commitMessages = emptyList(),
            analysisTimeMs = 0L
        )
    }
    
    private fun createEmptyPreprocessingDetails(): com.vyibc.autocr.action.PreprocessingDebugInfo {
        return com.vyibc.autocr.action.PreprocessingDebugInfo(
            intentWeightCalculation = emptyList(),
            riskWeightCalculation = emptyList(),
            selectionCriteria = "未执行预处理",
            selectedPathsReason = "未选择路径",
            calculationTimeMs = 0L
        )
    }
    
    private fun createEmptyAIInteractionDetails(): com.vyibc.autocr.action.AIInteractionDebugInfo {
        return com.vyibc.autocr.action.AIInteractionDebugInfo(
            quickScreeningPrompt = "",
            quickScreeningResponse = "",
            deepAnalysisPrompt = "",
            deepAnalysisResponse = "",
            aiProvider = "none",
            modelUsed = "none",
            contextCompressionApplied = false,
            tokenUsageBreakdown = com.vyibc.autocr.action.TokenUsageInfo(0, 0, 0, 0.0),
            responseParsingDetails = ""
        )
    }
}