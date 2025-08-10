package com.vyibc.autocr.model

/**
 * 分支对比请求数据模型
 */
data class BranchComparisonRequest(
    val sourceBranch: String,
    val targetBranch: String,
    val analysisType: AnalysisType
)

/**
 * Git变更上下文
 */
data class GitDiffContext(
    val sourceBranch: String,
    val targetBranch: String,
    val changedFiles: List<ChangedFile>,
    val addedLines: Int,
    val deletedLines: Int,
    val commits: List<GitCommit>
)

/**
 * 变更文件信息
 */
data class ChangedFile(
    val path: String,
    val changeType: FileChangeType,
    val addedLines: Int,
    val deletedLines: Int,
    val hunks: List<DiffHunk>
)

/**
 * Git提交信息
 */
data class GitCommit(
    val hash: String,
    val author: String,
    val date: String,
    val message: String,
    val businessKeywords: List<String> = emptyList()
)

/**
 * 差异代码块
 */
data class DiffHunk(
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    val header: String,
    val lines: List<DiffLine>
)

/**
 * 差异行
 */
data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val lineNumber: Int? = null
)

/**
 * 文件变更类型
 */
enum class FileChangeType {
    ADDED,      // 新增文件
    MODIFIED,   // 修改文件
    DELETED,    // 删除文件
    RENAMED,    // 重命名文件
    COPIED      // 复制文件
}

/**
 * 差异行类型
 */
enum class DiffLineType {
    CONTEXT,    // 上下文行
    ADDED,      // 新增行
    DELETED     // 删除行
}

/**
 * 调用路径
 */
data class CallPath(
    val id: String,
    val description: String,
    val methods: List<String>,
    val intentWeight: Double = 0.0,
    val riskWeight: Double = 0.0,
    val relatedChanges: List<ChangedFile> = emptyList(),
    val changeDetails: String = "" // 新增变更详情字段
)

/**
 * 分析进度信息
 */
data class AnalysisProgress(
    val stage: String,
    val message: String,
    val percentage: Int,
    val details: String? = null
)

/**
 * 分析模式枚举
 */
enum class AnalysisType(val icon: String, val displayName: String, val description: String) {
    FULL_REVIEW("🔍", "完整评审", "深度分析所有代码变更，包括意图识别和风险评估"),
    RISK_FOCUSED("⚠️", "风险为主", "重点关注潜在风险和架构问题"),
    INTENT_FOCUSED("💡", "意图为主", "重点分析功能实现和业务价值"),
    QUICK_SCAN("⚡", "快速扫描", "快速概览，适用于小型变更")
}