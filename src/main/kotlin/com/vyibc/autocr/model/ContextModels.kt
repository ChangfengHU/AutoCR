package com.vyibc.autocr.model

/**
 * 完整的分析上下文
 */
data class AnalysisContext(
    val gitContext: GitDiffContext,
    val selectedPaths: SelectedPaths,
    val analysisType: AnalysisType,
    val methodBodies: List<MethodBodyContext>,
    val testCoverage: TestCoverageContext,
    val businessContext: BusinessContext,
    val architectureContext: ArchitectureContext,
    val impactAnalysis: ImpactAnalysis,
    val buildTimestamp: Long
)

/**
 * 选中的路径
 */
data class SelectedPaths(
    val goldenPaths: List<SelectedPath>,
    val riskPaths: List<SelectedPath>
)

/**
 * 选中的单个路径
 */
data class SelectedPath(
    val pathId: String,
    val reason: String,
    val confidence: Double
)

/**
 * 方法体上下文
 */
data class MethodBodyContext(
    val signature: String,
    val filePath: String,
    val addedLines: List<DiffLine>,
    val deletedLines: List<DiffLine>,
    val complexity: Int,
    val changeType: String // ADDED, MODIFIED, DELETED
)

/**
 * 测试覆盖上下文
 */
data class TestCoverageContext(
    val fileCoverage: List<TestFileCoverage>,
    val overallCoverageRatio: Double,
    val missingTestFiles: List<String>
)

/**
 * 单个文件的测试覆盖情况
 */
data class TestFileCoverage(
    val businessFile: String,
    val relatedTestFiles: List<String>,
    val coverageType: TestCoverageType,
    val testCompletenesScore: Double
)

/**
 * 测试覆盖类型
 */
enum class TestCoverageType {
    NO_TESTS,               // 无测试
    BASIC_COVERAGE,         // 基础覆盖
    PARTIAL_COVERAGE,       // 部分覆盖
    COMPREHENSIVE_COVERAGE  // 全面覆盖
}

/**
 * 业务上下文
 */
data class BusinessContext(
    val businessKeywords: List<String>,
    val intentEvolution: IntentEvolution,
    val domainEntities: List<String>,
    val businessProcesses: List<String>
)

/**
 * 意图演进
 */
data class IntentEvolution(
    val initialIntent: String,
    val finalIntent: String,
    val intentShifts: List<IntentShift>
)

/**
 * 意图变化
 */
data class IntentShift(
    val fromIntent: String,
    val toIntent: String,
    val similarity: Double
)

/**
 * 架构上下文
 */
data class ArchitectureContext(
    val involvedLayers: Set<String>,
    val moduleBoundaries: List<String>,
    val designPatterns: List<String>,
    val dependencyChanges: List<DependencyChange>
)

/**
 * 依赖变更
 */
data class DependencyChange(
    val changeType: String, // ADDED, REMOVED, MODIFIED
    val dependency: String
)

/**
 * 影响分析
 */
data class ImpactAnalysis(
    val changeScope: ChangeScope,
    val impactRadius: Int,
    val riskHotspots: List<String>,
    val regressionRisk: Double
)

/**
 * 变更范围
 */
data class ChangeScope(
    val modifiedFiles: Int,
    val addedFiles: Int,
    val deletedFiles: Int,
    val totalLinesChanged: Int
)

/**
 * AI分析结果（从AI服务返回的结构化结果）
 */
data class AIAnalysisResult(
    val intentAnalysis: List<AIIntentResult>,
    val riskAnalysis: List<AIRiskResult>,
    val overallRecommendation: AIOverallRecommendation,
    val modelUsed: String,
    val tokensUsed: Int
)

/**
 * AI意图分析结果
 */
data class AIIntentResult(
    val description: String,
    val businessValue: Double,
    val implementationSummary: String,
    val relatedPaths: List<String>,
    val confidence: Double
)

/**
 * AI风险分析结果
 */
data class AIRiskResult(
    val description: String,
    val category: String,
    val severity: String,
    val impact: String,
    val recommendation: String,
    val location: String
)

/**
 * AI整体推荐
 */
data class AIOverallRecommendation(
    val approvalStatus: String,
    val reasoning: String,
    val criticalIssues: List<String>,
    val suggestions: List<String>
)