package com.vyibc.autocrplugin.util

import com.vyibc.autocrplugin.service.*

/**
 * 代码评估工具类
 */
object CodeReviewUtils {
    
    /**
     * 创建模拟的代码变更用于测试
     */
    fun createMockCodeChanges(): List<CodeChange> {
        return listOf(
            CodeChange(
                filePath = "src/main/kotlin/Example.kt",
                changeType = ChangeType.MODIFIED,
                oldContent = """
                    class Example {
                        fun test() {
                            val data = getData()
                            println(data)
                        }
                    }
                """.trimIndent(),
                newContent = """
                    class Example {
                        fun test() {
                            val userData = getData()!!
                            println(userData)
                            // TODO: Add error handling
                        }
                        
                        private fun getData(): String? {
                            return "test data"
                        }
                    }
                """.trimIndent(),
                addedLines = listOf(
                    "private fun getData(): String? {",
                    "    return \"test data\"",
                    "}",
                    "// TODO: Add error handling"
                ),
                removedLines = listOf(),
                modifiedLines = listOf(
                    "val data = getData()" to "val userData = getData()!!",
                    "println(data)" to "println(userData)"
                )
            ),
            CodeChange(
                filePath = "src/main/kotlin/NewFile.kt",
                changeType = ChangeType.ADDED,
                oldContent = null,
                newContent = """
                    class NewFile {
                        fun processData(input: String): String {
                            // This line is very long and exceeds the recommended line length limit of 120 characters which is not good for readability
                            return input.uppercase()
                        }
                    }
                """.trimIndent(),
                addedLines = listOf(
                    "class NewFile {",
                    "    fun processData(input: String): String {",
                    "        // This line is very long and exceeds the recommended line length limit of 120 characters which is not good for readability",
                    "        return input.uppercase()",
                    "    }",
                    "}"
                ),
                removedLines = listOf(),
                modifiedLines = listOf()
            )
        )
    }
    
    /**
     * 创建模拟的评估结果用于测试
     */
    fun createMockReviewResult(): CodeReviewResult {
        return CodeReviewResult(
            overallScore = 75,
            issues = listOf(
                CodeIssue(
                    filePath = "src/main/kotlin/Example.kt",
                    lineNumber = 4,
                    severity = IssueSeverity.MAJOR,
                    category = IssueCategory.BUG_RISK,
                    message = "使用了非空断言操作符(!!)",
                    suggestion = "考虑使用安全调用(?.)或适当的空值检查"
                ),
                CodeIssue(
                    filePath = "src/main/kotlin/Example.kt",
                    lineNumber = 6,
                    severity = IssueSeverity.INFO,
                    category = IssueCategory.DOCUMENTATION,
                    message = "发现TODO注释",
                    suggestion = "考虑完成TODO项目或创建相应的任务"
                ),
                CodeIssue(
                    filePath = "src/main/kotlin/NewFile.kt",
                    lineNumber = 3,
                    severity = IssueSeverity.MINOR,
                    category = IssueCategory.CODE_STYLE,
                    message = "行长度超过120字符",
                    suggestion = "考虑将长行拆分为多行"
                )
            ),
            suggestions = listOf(
                "代码整体结构良好，但需要注意空值安全",
                "建议完成TODO项目",
                "注意代码行长度限制",
                "考虑添加更多的错误处理机制"
            ),
            riskLevel = RiskLevel.MEDIUM,
            summary = "代码质量中等，发现了一些需要改进的地方。主要问题是空值安全和代码风格。建议修复后提交。"
        )
    }
    
    /**
     * 格式化代码变更信息
     */
    fun formatCodeChanges(changes: List<CodeChange>): String {
        val sb = StringBuilder()
        sb.append("代码变更摘要:\n")
        sb.append("=".repeat(50)).append("\n")
        
        changes.forEach { change ->
            sb.append("文件: ${change.filePath}\n")
            sb.append("类型: ${formatChangeType(change.changeType)}\n")
            sb.append("新增: ${change.addedLines.size} 行\n")
            sb.append("删除: ${change.removedLines.size} 行\n")
            sb.append("修改: ${change.modifiedLines.size} 行\n")
            sb.append("-".repeat(30)).append("\n")
        }
        
        return sb.toString()
    }
    
    /**
     * 格式化变更类型
     */
    private fun formatChangeType(changeType: ChangeType): String {
        return when (changeType) {
            ChangeType.ADDED -> "新增文件"
            ChangeType.MODIFIED -> "修改文件"
            ChangeType.DELETED -> "删除文件"
            ChangeType.RENAMED -> "重命名文件"
        }
    }
    
    /**
     * 检查评估结果是否通过阈值
     */
    fun checkThreshold(result: CodeReviewResult, minimumScore: Int, blockHighRisk: Boolean): Pair<Boolean, String> {
        val reasons = mutableListOf<String>()
        
        // 检查评分
        if (result.overallScore < minimumScore) {
            reasons.add("评分 ${result.overallScore} 低于最低要求 $minimumScore")
        }
        
        // 检查风险等级
        if (blockHighRisk) {
            when (result.riskLevel) {
                RiskLevel.CRITICAL -> reasons.add("存在严重风险")
                RiskLevel.HIGH -> reasons.add("存在高风险")
                else -> {}
            }
        }
        
        // 检查严重问题
        val criticalIssues = result.issues.filter { it.severity == IssueSeverity.CRITICAL }
        if (criticalIssues.isNotEmpty()) {
            reasons.add("发现 ${criticalIssues.size} 个严重问题")
        }
        
        val passed = reasons.isEmpty()
        val message = if (passed) {
            "代码质量检查通过"
        } else {
            "代码质量检查未通过:\n${reasons.joinToString("\n") { "• $it" }}"
        }
        
        return Pair(passed, message)
    }
}
