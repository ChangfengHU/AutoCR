package com.vyibc.autocrplugin.service

/**
 * 代码变更信息
 */
data class CodeChange(
    val filePath: String,
    val changeType: ChangeType,
    val oldContent: String?,
    val newContent: String?,
    val addedLines: List<String>,
    val removedLines: List<String>,
    val modifiedLines: List<Pair<String, String>> // old -> new
)

/**
 * 变更类型
 */
enum class ChangeType {
    ADDED,      // 新增文件
    MODIFIED,   // 修改文件
    DELETED,    // 删除文件
    RENAMED     // 重命名文件
}

/**
 * 代码评估结果
 */
data class CodeReviewResult(
    val overallScore: Int, // 总体评分 0-100
    val issues: List<CodeIssue>,
    val suggestions: List<String>,
    val riskLevel: RiskLevel,
    val summary: String,
    val commitMessage: String? = null // AI建议的提交信息
)

/**
 * 代码问题
 */
data class CodeIssue(
    val filePath: String,
    val lineNumber: Int?,
    val severity: IssueSeverity,
    val category: IssueCategory,
    val message: String,
    val suggestion: String?
)

/**
 * 问题严重程度
 */
enum class IssueSeverity {
    CRITICAL,   // 严重
    MAJOR,      // 重要
    MINOR,      // 轻微
    INFO        // 信息
}

/**
 * 问题分类
 */
enum class IssueCategory {
    CODE_STYLE,         // 代码风格
    PERFORMANCE,        // 性能问题
    SECURITY,           // 安全问题
    BUG_RISK,          // Bug风险
    MAINTAINABILITY,    // 可维护性
    DOCUMENTATION,      // 文档问题
    BEST_PRACTICE      // 最佳实践
}

/**
 * 风险等级
 */
enum class RiskLevel {
    LOW,        // 低风险
    MEDIUM,     // 中等风险
    HIGH,       // 高风险
    CRITICAL    // 严重风险
}

/**
 * 代码评估服务接口
 */
interface CodeReviewService {

    /**
     * 评估代码变更
     * @param changes 代码变更列表
     * @param commitMessage 提交信息
     * @return 评估结果
     */
    suspend fun reviewCode(
        changes: List<CodeChange>,
        commitMessage: String
    ): CodeReviewResult

    /**
     * 评估代码变更（带调试回调）
     * @param changes 代码变更列表
     * @param commitMessage 提交信息
     * @param debugCallback 调试信息回调
     * @return 评估结果
     */
    suspend fun reviewCode(
        changes: List<CodeChange>,
        commitMessage: String,
        debugCallback: AIDebugCallback?
    ): CodeReviewResult

    /**
     * 获取服务名称
     */
    fun getServiceName(): String
}
