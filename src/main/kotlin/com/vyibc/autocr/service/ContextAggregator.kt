package com.vyibc.autocr.service

import com.intellij.openapi.project.Project
import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory

/**
 * 上下文聚合器
 * 负责聚合Git差异、调用路径、测试关联等所有分析所需的上下文信息
 * 实现技术方案V5.1中的上下文聚合器系统
 */
class ContextAggregator(private val project: Project) {
    
    private val logger = LoggerFactory.getLogger(ContextAggregator::class.java)
    
    /**
     * 构建完整的分析上下文
     */
    fun buildAnalysisContext(
        gitContext: GitDiffContext,
        selectedPaths: SelectedPaths,
        analysisType: AnalysisType
    ): AnalysisContext {
        
        logger.info("开始构建分析上下文")
        
        val startTime = System.currentTimeMillis()
        
        // 1. 提取完整方法体信息
        val methodBodies = extractMethodBodies(gitContext, selectedPaths)
        
        // 2. 关联测试用例
        val testCoverage = linkTestCases(gitContext)
        
        // 3. 提取业务上下文
        val businessContext = extractBusinessContext(gitContext)
        
        // 4. 分析架构上下文
        val architectureContext = analyzeArchitectureContext(gitContext, selectedPaths)
        
        // 5. 计算影响分析
        val impactAnalysis = calculateImpactAnalysis(gitContext, selectedPaths)
        
        val buildTime = System.currentTimeMillis() - startTime
        logger.info("上下文构建完成，耗时 ${buildTime}ms")
        
        return AnalysisContext(
            gitContext = gitContext,
            selectedPaths = selectedPaths,
            analysisType = analysisType,
            methodBodies = methodBodies,
            testCoverage = testCoverage,
            businessContext = businessContext,
            architectureContext = architectureContext,
            impactAnalysis = impactAnalysis,
            buildTimestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 提取完整方法体信息
     */
    private fun extractMethodBodies(
        gitContext: GitDiffContext, 
        selectedPaths: SelectedPaths
    ): List<MethodBodyContext> {
        
        val methodBodies = mutableListOf<MethodBodyContext>()
        
        gitContext.changedFiles.forEach { file ->
            if (isRelevantFile(file.path)) {
                val extractedMethods = extractMethodsFromFile(file)
                methodBodies.addAll(extractedMethods)
            }
        }
        
        logger.debug("提取了 ${methodBodies.size} 个方法体")
        return methodBodies
    }
    
    /**
     * 从文件中提取方法信息
     */
    private fun extractMethodsFromFile(file: ChangedFile): List<MethodBodyContext> {
        val methods = mutableListOf<MethodBodyContext>()
        
        file.hunks.forEach { hunk ->
            val addedLines = hunk.lines.filter { it.type == DiffLineType.ADDED }
            val deletedLines = hunk.lines.filter { it.type == DiffLineType.DELETED }
            
            // 识别方法边界和内容
            var currentMethod: MethodBodyBuilder? = null
            
            hunk.lines.forEach { line ->
                when {
                    isMethodStart(line.content) -> {
                        // 保存之前的方法
                        currentMethod?.let { methods.add(it.build()) }
                        
                        // 开始新方法
                        currentMethod = MethodBodyBuilder(
                            signature = line.content.trim(),
                            filePath = file.path
                        )
                    }
                    currentMethod != null -> {
                        currentMethod!!.addLine(line)
                    }
                }
            }
            
            // 保存最后一个方法
            currentMethod?.let { methods.add(it.build()) }
        }
        
        return methods
    }
    
    /**
     * 关联测试用例
     */
    private fun linkTestCases(gitContext: GitDiffContext): TestCoverageContext {
        val businessFiles = gitContext.changedFiles.filter { !isTestFile(it.path) }
        val testFiles = gitContext.changedFiles.filter { isTestFile(it.path) }
        
        val coverage = businessFiles.map { businessFile ->
            val relatedTests = findRelatedTestFiles(businessFile, testFiles)
            
            TestFileCoverage(
                businessFile = businessFile.path,
                relatedTestFiles = relatedTests.map { it.path },
                coverageType = determineCoverageType(businessFile, relatedTests),
                testCompletenesScore = calculateTestCompleteness(businessFile, relatedTests)
            )
        }
        
        val overallCoverageRatio = if (businessFiles.isNotEmpty()) {
            coverage.count { it.relatedTestFiles.isNotEmpty() }.toDouble() / businessFiles.size
        } else 1.0
        
        logger.debug("测试覆盖率: ${(overallCoverageRatio * 100).toInt()}%")
        
        return TestCoverageContext(
            fileCoverage = coverage,
            overallCoverageRatio = overallCoverageRatio,
            missingTestFiles = identifyMissingTestFiles(businessFiles, testFiles)
        )
    }
    
    /**
     * 提取业务上下文
     */
    private fun extractBusinessContext(gitContext: GitDiffContext): BusinessContext {
        // 1. 分析提交消息中的业务关键词
        val allBusinessKeywords = gitContext.commits.flatMap { it.businessKeywords }.distinct()
        
        // 2. 分析意图演进
        val intentEvolution = analyzeIntentEvolution(gitContext.commits)
        
        // 3. 提取领域实体
        val domainEntities = extractDomainEntities(gitContext)
        
        // 4. 分析业务流程
        val businessProcesses = identifyBusinessProcesses(gitContext, allBusinessKeywords)
        
        return BusinessContext(
            businessKeywords = allBusinessKeywords,
            intentEvolution = intentEvolution,
            domainEntities = domainEntities,
            businessProcesses = businessProcesses
        )
    }
    
    /**
     * 分析架构上下文
     */
    private fun analyzeArchitectureContext(
        gitContext: GitDiffContext, 
        selectedPaths: SelectedPaths
    ): ArchitectureContext {
        
        // 1. 识别涉及的架构层级
        val involvedLayers = identifyArchitectureLayers(gitContext)
        
        // 2. 分析模块边界
        val moduleBoundaries = analyzeModuleBoundaries(gitContext)
        
        // 3. 识别设计模式使用
        val designPatterns = identifyDesignPatterns(gitContext)
        
        // 4. 分析依赖关系变化
        val dependencyChanges = analyzeDependencyChanges(gitContext)
        
        return ArchitectureContext(
            involvedLayers = involvedLayers,
            moduleBoundaries = moduleBoundaries,
            designPatterns = designPatterns,
            dependencyChanges = dependencyChanges
        )
    }
    
    /**
     * 计算影响分析
     */
    private fun calculateImpactAnalysis(
        gitContext: GitDiffContext, 
        selectedPaths: SelectedPaths
    ): ImpactAnalysis {
        
        // 1. 计算变更范围
        val changeScope = ChangeScope(
            modifiedFiles = gitContext.changedFiles.count { it.changeType == FileChangeType.MODIFIED },
            addedFiles = gitContext.changedFiles.count { it.changeType == FileChangeType.ADDED },
            deletedFiles = gitContext.changedFiles.count { it.changeType == FileChangeType.DELETED },
            totalLinesChanged = gitContext.addedLines + gitContext.deletedLines
        )
        
        // 2. 估算影响半径
        val impactRadius = estimateImpactRadius(selectedPaths)
        
        // 3. 识别风险热点
        val riskHotspots = identifyRiskHotspots(gitContext)
        
        // 4. 计算回归风险
        val regressionRisk = calculateRegressionRisk(gitContext, selectedPaths)
        
        return ImpactAnalysis(
            changeScope = changeScope,
            impactRadius = impactRadius,
            riskHotspots = riskHotspots,
            regressionRisk = regressionRisk
        )
    }
    
    // === 辅助方法实现 ===
    
    private fun isRelevantFile(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".java") || lowerPath.endsWith(".kt") || lowerPath.endsWith(".scala")
    }
    
    private fun isTestFile(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.contains("test") || lowerPath.contains("spec")
    }
    
    private fun isMethodStart(line: String): Boolean {
        val trimmed = line.trim()
        return (trimmed.contains("public ") || trimmed.contains("private ") || trimmed.contains("protected ")) &&
               trimmed.contains("(") && trimmed.contains(")") && trimmed.contains("{")
    }
    
    private fun findRelatedTestFiles(businessFile: ChangedFile, testFiles: List<ChangedFile>): List<ChangedFile> {
        val businessClassName = extractClassName(businessFile.path)
        return testFiles.filter { testFile ->
            val testFileName = extractClassName(testFile.path)
            testFileName.contains(businessClassName, ignoreCase = true) ||
            businessClassName.contains(testFileName.replace("Test", "").replace("Spec", ""), ignoreCase = true)
        }
    }
    
    private fun determineCoverageType(businessFile: ChangedFile, testFiles: List<ChangedFile>): TestCoverageType {
        return when {
            testFiles.isEmpty() -> TestCoverageType.NO_TESTS
            testFiles.size == 1 -> TestCoverageType.BASIC_COVERAGE
            testFiles.any { it.path.contains("integration", ignoreCase = true) } -> TestCoverageType.COMPREHENSIVE_COVERAGE
            else -> TestCoverageType.PARTIAL_COVERAGE
        }
    }
    
    private fun calculateTestCompleteness(businessFile: ChangedFile, testFiles: List<ChangedFile>): Double {
        if (testFiles.isEmpty()) return 0.0
        
        val businessMethods = countMethodsInFile(businessFile)
        val testMethods = testFiles.sumOf { countMethodsInFile(it) }
        
        return if (businessMethods > 0) {
            minOf(testMethods.toDouble() / businessMethods, 1.0)
        } else 1.0
    }
    
    private fun countMethodsInFile(file: ChangedFile): Int {
        return file.hunks.sumOf { hunk ->
            hunk.lines.count { line ->
                line.type == DiffLineType.ADDED && isMethodStart(line.content)
            }
        }
    }
    
    private fun identifyMissingTestFiles(businessFiles: List<ChangedFile>, testFiles: List<ChangedFile>): List<String> {
        return businessFiles.filter { businessFile ->
            findRelatedTestFiles(businessFile, testFiles).isEmpty()
        }.map { it.path }
    }
    
    private fun analyzeIntentEvolution(commits: List<GitCommit>): IntentEvolution {
        val firstCommit = commits.firstOrNull()
        val lastCommit = commits.lastOrNull()
        
        return IntentEvolution(
            initialIntent = firstCommit?.message ?: "",
            finalIntent = lastCommit?.message ?: "",
            intentShifts = commits.zipWithNext().map { (prev, curr) ->
                IntentShift(prev.message, curr.message, calculateIntentSimilarity(prev, curr))
            }
        )
    }
    
    private fun calculateIntentSimilarity(commit1: GitCommit, commit2: GitCommit): Double {
        val keywords1 = commit1.businessKeywords.toSet()
        val keywords2 = commit2.businessKeywords.toSet()
        
        val intersection = keywords1.intersect(keywords2).size
        val union = keywords1.union(keywords2).size
        
        return if (union > 0) intersection.toDouble() / union else 0.0
    }
    
    private fun extractDomainEntities(gitContext: GitDiffContext): List<String> {
        val entities = mutableSetOf<String>()
        
        gitContext.changedFiles.forEach { file ->
            val fileName = extractClassName(file.path).lowercase()
            
            // 识别可能的领域实体
            val commonEntities = setOf(
                "user", "order", "product", "customer", "account", "payment",
                "invoice", "shipment", "category", "review", "cart", "item"
            )
            
            commonEntities.forEach { entity ->
                if (fileName.contains(entity)) {
                    entities.add(entity.replaceFirstChar { it.uppercase() })
                }
            }
        }
        
        return entities.toList()
    }
    
    private fun identifyBusinessProcesses(gitContext: GitDiffContext, keywords: List<String>): List<String> {
        val processes = mutableSetOf<String>()
        
        // 基于关键词识别业务流程
        val processKeywords = mapOf(
            "registration" to "用户注册流程",
            "authentication" to "用户认证流程",
            "checkout" to "订单结算流程",
            "payment" to "支付处理流程",
            "shipment" to "订单配送流程",
            "inventory" to "库存管理流程"
        )
        
        keywords.forEach { keyword ->
            processKeywords.forEach { (key, process) ->
                if (keyword.contains(key, ignoreCase = true)) {
                    processes.add(process)
                }
            }
        }
        
        return processes.toList()
    }
    
    private fun identifyArchitectureLayers(gitContext: GitDiffContext): Set<String> {
        val layers = mutableSetOf<String>()
        
        gitContext.changedFiles.forEach { file ->
            val path = file.path.lowercase()
            when {
                path.contains("controller") -> layers.add("CONTROLLER")
                path.contains("service") -> layers.add("SERVICE")
                path.contains("repository") || path.contains("dao") -> layers.add("REPOSITORY")
                path.contains("entity") || path.contains("model") -> layers.add("MODEL")
                path.contains("config") -> layers.add("CONFIG")
                path.contains("util") || path.contains("helper") -> layers.add("UTIL")
            }
        }
        
        return layers
    }
    
    private fun analyzeModuleBoundaries(gitContext: GitDiffContext): List<String> {
        return gitContext.changedFiles
            .map { it.path.split("/").firstOrNull() ?: "root" }
            .distinct()
    }
    
    private fun identifyDesignPatterns(gitContext: GitDiffContext): List<String> {
        val patterns = mutableSetOf<String>()
        
        gitContext.changedFiles.forEach { file ->
            val fileName = file.path.lowercase()
            when {
                fileName.contains("factory") -> patterns.add("Factory Pattern")
                fileName.contains("builder") -> patterns.add("Builder Pattern")
                fileName.contains("adapter") -> patterns.add("Adapter Pattern")
                fileName.contains("facade") -> patterns.add("Facade Pattern")
                fileName.contains("strategy") -> patterns.add("Strategy Pattern")
                fileName.contains("observer") -> patterns.add("Observer Pattern")
                fileName.contains("proxy") -> patterns.add("Proxy Pattern")
                fileName.contains("decorator") -> patterns.add("Decorator Pattern")
            }
        }
        
        return patterns.toList()
    }
    
    private fun analyzeDependencyChanges(gitContext: GitDiffContext): List<DependencyChange> {
        val changes = mutableListOf<DependencyChange>()
        
        gitContext.changedFiles.forEach { file ->
            file.hunks.forEach { hunk ->
                hunk.lines.forEach { line ->
                    if (line.content.contains("import ") && line.type == DiffLineType.ADDED) {
                        val dependency = line.content.substringAfter("import ").substringBefore(";").trim()
                        changes.add(DependencyChange("ADDED", dependency))
                    } else if (line.content.contains("import ") && line.type == DiffLineType.DELETED) {
                        val dependency = line.content.substringAfter("import ").substringBefore(";").trim()
                        changes.add(DependencyChange("REMOVED", dependency))
                    }
                }
            }
        }
        
        return changes
    }
    
    private fun estimateImpactRadius(selectedPaths: SelectedPaths): Int {
        return selectedPaths.goldenPaths.size + selectedPaths.riskPaths.size * 2
    }
    
    private fun identifyRiskHotspots(gitContext: GitDiffContext): List<String> {
        return gitContext.changedFiles
            .filter { it.addedLines + it.deletedLines > 50 }
            .map { it.path }
    }
    
    private fun calculateRegressionRisk(gitContext: GitDiffContext, selectedPaths: SelectedPaths): Double {
        val changeSize = gitContext.addedLines + gitContext.deletedLines
        val pathComplexity = selectedPaths.goldenPaths.size + selectedPaths.riskPaths.size
        
        return minOf((changeSize * 0.01 + pathComplexity * 0.1), 1.0)
    }
    
    private fun extractClassName(path: String): String {
        return path.substringAfterLast("/").substringBeforeLast(".")
    }
}

/**
 * 方法体构建器
 */
private class MethodBodyBuilder(
    private val signature: String,
    private val filePath: String
) {
    private val lines = mutableListOf<DiffLine>()
    
    fun addLine(line: DiffLine) {
        lines.add(line)
    }
    
    fun build(): MethodBodyContext {
        val addedLines = lines.filter { it.type == DiffLineType.ADDED }
        val deletedLines = lines.filter { it.type == DiffLineType.DELETED }
        
        return MethodBodyContext(
            signature = signature,
            filePath = filePath,
            addedLines = addedLines,
            deletedLines = deletedLines,
            complexity = estimateComplexity(),
            changeType = determineChangeType()
        )
    }
    
    private fun estimateComplexity(): Int {
        val controlFlowKeywords = listOf("if", "for", "while", "switch", "try", "catch")
        return lines.count { line ->
            controlFlowKeywords.any { keyword -> line.content.contains(keyword) }
        }
    }
    
    private fun determineChangeType(): String {
        val hasAdditions = lines.any { it.type == DiffLineType.ADDED }
        val hasDeletions = lines.any { it.type == DiffLineType.DELETED }
        
        return when {
            hasAdditions && hasDeletions -> "MODIFIED"
            hasAdditions -> "ADDED"
            hasDeletions -> "DELETED"
            else -> "UNCHANGED"
        }
    }
}