package com.vyibc.autocrplugin.service.impl

import com.vyibc.autocrplugin.service.*
import com.vyibc.autocrplugin.settings.CodeReviewSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * AI代码评估服务实现
 * 使用多种AI服务进行代码评估，支持故障转移
 */
class AICodeReviewService : CodeReviewService {

    private val gson = Gson()
    private val aiServiceManager = AIServiceManager()

    override suspend fun reviewCode(
        changes: List<CodeChange>,
        commitMessage: String
    ): CodeReviewResult = reviewCode(changes, commitMessage, null)

    override suspend fun reviewCode(
        changes: List<CodeChange>,
        commitMessage: String,
        debugCallback: AIDebugCallback?
    ): CodeReviewResult = withContext(Dispatchers.IO) {

        try {
            val prompt = buildReviewPrompt(changes, commitMessage)
            val requestTime = java.time.LocalDateTime.now().toString()

            // 记录请求信息
            debugCallback?.onAIRequest(getServiceName(), prompt, requestTime)

            val aiResponse = aiServiceManager.callAIWithFallback(prompt)
            val responseTime = java.time.LocalDateTime.now().toString()

            // 记录响应信息
            debugCallback?.onAIResponse(aiResponse, responseTime)

            parseAIResponse(aiResponse, debugCallback)
        } catch (e: Exception) {
            debugCallback?.onParsingResult(false, null, e.message)
            // 如果AI服务失败，返回基础的静态分析结果
            performBasicAnalysis(changes, commitMessage)
        }
    }

    /**
     * 构建AI评估提示词
     */
    private fun buildReviewPrompt(changes: List<CodeChange>, commitMessage: String): String {
        val settings = CodeReviewSettings.getInstance()
        val prompt = StringBuilder()

        // 使用自定义提示词或默认提示词
        val basePrompt = if (settings.customPrompt.isNotEmpty()) {
            settings.customPrompt
        } else {
            getDefaultPrompt()
        }

        prompt.append(basePrompt)
        prompt.append("\n\n## 📋 代码变更详情：\n\n")
        prompt.append("提交信息: $commitMessage\n\n")
        
        changes.forEach { change ->
            prompt.append("文件: ${change.filePath}\n")
            prompt.append("变更类型: ${change.changeType}\n")
            
            when (change.changeType) {
                ChangeType.ADDED -> {
                    prompt.append("新增内容:\n")
                    prompt.append(change.newContent ?: "")
                }
                ChangeType.MODIFIED -> {
                    prompt.append("新增行:\n")
                    change.addedLines.forEach { line ->
                        prompt.append("+ $line\n")
                    }
                    prompt.append("删除行:\n")
                    change.removedLines.forEach { line ->
                        prompt.append("- $line\n")
                    }
                    prompt.append("修改行:\n")
                    change.modifiedLines.forEach { (old, new) ->
                        prompt.append("- $old\n")
                        prompt.append("+ $new\n")
                    }
                }
                ChangeType.DELETED -> {
                    prompt.append("删除内容:\n")
                    prompt.append(change.oldContent ?: "")
                }
                ChangeType.RENAMED -> {
                    prompt.append("文件重命名\n")
                }
            }
            prompt.append("\n---\n\n")
        }
        
        // 添加方法调用分析结果
        val project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
        if (project != null) {
            val methodAnalyzer = com.vyibc.autocrplugin.service.MethodCallAnalyzer(project, maxCascadeDepth = settings.maxCascadeDepth)
            val methodCalls = methodAnalyzer.analyzeMethodCalls(changes)

            if (methodCalls.isNotEmpty()) {
                prompt.append("## 🔍 **方法实现安全分析**\n\n")
                prompt.append("""
**📋 以下是代码变更中调用的方法的完整实现，请基于自动化工具的预检测结果进行深度分析：**

**🎯 分析重点：**
1. **预检测危险操作** - 重点关注标记为"已检测到潜在危险操作"的代码段
2. **生产环境影响** - 评估每种危险操作在高并发环境下的具体影响
3. **风险等级判定** - 根据影响程度确定CRITICAL/HIGH/MEDIUM/LOW等级
4. **解决方案制定** - 针对发现的问题提供具体的技术改进方案

**⚠️ 评估依据：**
- 系统预检测到的危险操作类型和描述
- 方法实现的完整源代码
- 生产环境下的潜在影响分析

                """.trimIndent())

                methodCalls.forEach { call ->
                    appendMethodImplementation(prompt, call.implementation, 1)
                }
            }
        }
        
        prompt.append("""

## 📤 严格返回格式要求：
请严格按照以下JSON格式返回，不要添加任何其他文字：

```json
{
  "overallScore": 25,
  "riskLevel": "CRITICAL",
  "issues": [
    {
      "filePath": "具体文件路径",
      "lineNumber": "具体行号或代码段",
      "severity": "CRITICAL",
      "category": "生产环境危险操作",
      "message": "根据系统预检测结果，发现[具体危险操作类型]，在生产环境中会导致[具体影响描述]",
      "suggestion": "基于危险操作类型提供针对性的技术解决方案"
    }
  ],
  "suggestions": [
    "基于预检测结果提供的具体技术改进建议",
    "针对发现的危险操作类型的最佳实践建议",
    "生产环境优化和监控建议"
  ],
  "summary": "基于系统预检测的危险操作进行风险评估，详细说明对生产环境的影响和修复紧急程度",
  "commitMessage": "根据实际检测到的问题生成相应的提交信息"
}
```

**评分标准：**
- **0-30分**：包含CRITICAL风险，立即阻止部署
- **31-60分**：包含HIGH风险，需要修复后部署  
- **61-80分**：包含MEDIUM风险，建议优化
- **81-100分**：低风险或无风险

**风险等级判定：**
- **CRITICAL**：Redis keys()、数据库全表扫描、敏感信息泄露等生产致命问题
- **HIGH**：SQL注入、权限绕过、严重性能问题
- **MEDIUM**：一般性能问题、代码质量问题
- **LOW**：轻微改进建议

**重要要求：**
- overallScore: 0-100整数，根据最高风险等级确定分数范围
- riskLevel: 必须是 LOW|MEDIUM|HIGH|CRITICAL 之一
- severity: 必须是 CRITICAL|MAJOR|MINOR|INFO 之一  
- 如果发现任何Redis keys()、数据库全表扫描等问题，riskLevel必须是CRITICAL
- issues数组必须包含发现的所有问题，包括方法实现中的问题
- 请确保返回有效的JSON格式
        """.trimIndent())
        
        return prompt.toString()
    }

    /**
     * 获取默认的AI分析提示词
     */
    private fun getDefaultPrompt(): String {
        return """
🚨 **生产环境安全代码审查专家 - 严格风险评估标准**

**核心任务：基于方法实现中检测到的危险操作进行精确风险评估**

## 🔍 **风险评估方法论：**

### 第一步：危险操作检测分析
**重点关注系统预检测标记的"已检测到潜在危险操作"，这些是自动化工具识别的高风险模式：**
- 🚨 如标记为"Redis危险操作" → 分析具体影响和阻塞风险
- 🚨 如标记为"SQL危险操作" → 分析查询性能和注入风险  
- 🚨 如标记为"资源泄漏风险" → 分析内存和连接泄漏影响
- 🚨 如标记为"阻塞操作" → 分析并发性能和响应时间影响

### 第二步：生产环境影响评估
**针对检测到的每种危险操作，评估其在高并发生产环境下的影响：**
- **服务可用性影响** - 是否会导致服务不可用？
- **性能影响程度** - 对系统整体性能的影响范围？
- **故障传播风险** - 是否会引发连锁故障？
- **恢复难度评估** - 故障后恢复的复杂度？

### 第三步：风险等级判定标准
**基于影响程度确定风险等级：**

#### 🚨 CRITICAL (0-30分)：
- 会导致服务完全不可用的操作
- 可能引发系统宕机的风险
- 影响所有用户的致命问题
- 数据安全威胁

#### ⚠️ HIGH (31-60分)：
- 严重影响性能但不至于宕机
- 安全漏洞但影响范围有限
- 需要紧急修复的问题

#### 📊 MEDIUM (61-80分)：
- 一般性能问题
- 代码质量问题
- 建议优化的改进点

#### 💡 LOW (81-100分)：
- 轻微改进建议
- 最佳实践推荐
- 代码规范问题

## 🎯 **分析执行原则：**

1. **基于事实评估** - 严格基于方法实现代码和检测到的危险操作进行评估
2. **影响导向评估** - 重点关注对生产环境的实际影响程度
3. **具体化建议** - 提供针对性的技术解决方案
4. **严格等级标准** - 严格按照风险等级对应的评分范围给分
        """.trimIndent()
    }
    
    /**
     * 递归地添加方法实现到提示中（包含级联方法）
     */
    private fun appendMethodImplementation(prompt: StringBuilder, impl: com.vyibc.autocrplugin.service.MethodImplementation, level: Int) {
        val indent = "  ".repeat(level - 1)
        val levelPrefix = if (level == 1) "###" else "#".repeat(3 + level)
        
        prompt.append("$levelPrefix ${indent}方法: ${impl.className}.${impl.methodName}()\n")
        prompt.append("${indent}实现文件: ${impl.filePath}\n\n")
        prompt.append("${indent}方法实现代码:\n")
        prompt.append("```java\n")
        prompt.append(impl.sourceCode)
        prompt.append("\n```\n\n")

        if (impl.containsDangerousOperations.isNotEmpty()) {
            prompt.append("${indent}🚨 **系统预检测到的危险操作**:\n")
            impl.containsDangerousOperations.forEach { danger ->
                // 根据危险操作类型确定严重程度标识
                val severity = when {
                    danger.contains("Redis") && (danger.contains("keys()") || danger.contains("模式匹配")) -> "🚨 CRITICAL"
                    danger.contains("SQL") && danger.contains("全表") -> "🚨 CRITICAL" 
                    danger.contains("Redis") -> "⚠️ HIGH"
                    danger.contains("SQL") -> "⚠️ HIGH"
                    danger.contains("资源") || danger.contains("泄漏") -> "⚠️ HIGH"
                    danger.contains("阻塞") || danger.contains("循环") -> "⚠️ HIGH"
                    else -> "📊 MEDIUM"
                }
                prompt.append("${indent}- $severity **$danger**\n")
            }
            prompt.append("${indent}**⚠️ 请基于上述预检测结果进行详细的风险等级评估和解决方案制定**\n\n")
        }
        
        // 递归添加级联方法
        if (impl.cascadedMethods.isNotEmpty()) {
            prompt.append("${indent}**级联调用的方法:**\n\n")
            impl.cascadedMethods.forEach { cascaded ->
                appendMethodImplementation(prompt, cascaded, level + 1)
            }
        }

        prompt.append("${indent}---\n\n")
    }

    /**
     * 生成模拟AI响应（用于演示）
     */
    private fun generateMockAIResponse(prompt: String): String {
        return """
        {
            "overallScore": 85,
            "riskLevel": "LOW",
            "issues": [
                {
                    "filePath": "example.kt",
                    "lineNumber": 10,
                    "severity": "MINOR",
                    "category": "CODE_STYLE",
                    "message": "建议使用更具描述性的变量名",
                    "suggestion": "将变量名从 'data' 改为 'userData' 或 'responseData'"
                }
            ],
            "suggestions": [
                "代码整体结构良好",
                "建议添加更多的单元测试",
                "考虑添加错误处理机制"
            ],
            "summary": "代码质量良好，有一些小的改进空间。主要关注代码风格和错误处理。"
        }
        """.trimIndent()
    }

    /**
     * 解析AI响应
     */
    private fun parseAIResponse(response: String, debugCallback: AIDebugCallback? = null): CodeReviewResult {
        // 将详细信息输出到控制台和UI
        val logMessage = buildString {
            appendLine("=== 🔍 AI响应解析过程 ===")
            appendLine("原始响应长度: ${response.length} 字符")
            appendLine("响应接收时间: ${java.time.LocalDateTime.now()}")
            appendLine()
            appendLine("=== 📄 AI原始响应内容 ===")
            appendLine("```json")
            appendLine(response)
            appendLine("```")
            appendLine("========================")
        }

        // 输出到控制台
        println(logMessage)

        // 如果有UI回调，也输出到UI（这里先输出到控制台，后面会改进）
        logAIResponse(response)

        try {

            // 清理响应内容，移除可能的markdown代码块标记
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()


            println("清理后的响应:")
            println(cleanedResponse)
            println("========================")

            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            println("JSON解析成功")

            val overallScore = jsonObject.get("overallScore")?.asInt ?: 70
            println("解析评分: $overallScore")

            val riskLevelStr = jsonObject.get("riskLevel")?.asString ?: "MEDIUM"
            val riskLevel = try {
                RiskLevel.valueOf(riskLevelStr)
            } catch (e: Exception) {
                println("风险等级解析失败: $riskLevelStr，使用默认值 MEDIUM")
                RiskLevel.MEDIUM
            }
            println("解析风险等级: $riskLevel")

            val summary = jsonObject.get("summary")?.asString ?: "代码评估完成"
            println("解析总结: $summary")

            val commitMessage = jsonObject.get("commitMessage")?.asString
            println("解析提交信息: $commitMessage")
            
            val issues = mutableListOf<CodeIssue>()
            val issuesArray = jsonObject.getAsJsonArray("issues")
            println("解析问题列表，数量: ${issuesArray?.size() ?: 0}")

            issuesArray?.forEach { issueElement ->
                try {
                    val issueObj = issueElement.asJsonObject
                    val severityStr = issueObj.get("severity")?.asString ?: "INFO"
                    val categoryStr = issueObj.get("category")?.asString ?: "CODE_STYLE"

                    val severity = try {
                        IssueSeverity.valueOf(severityStr)
                    } catch (e: Exception) {
                        println("严重程度解析失败: $severityStr，使用默认值 INFO")
                        IssueSeverity.INFO
                    }

                    val category = try {
                        IssueCategory.valueOf(categoryStr)
                    } catch (e: Exception) {
                        // 根据中文分类映射到枚举值
                        when {
                            categoryStr.contains("生产环境") || categoryStr.contains("危险操作") -> IssueCategory.SECURITY
                            categoryStr.contains("代码质量") -> IssueCategory.BUG_RISK
                            categoryStr.contains("性能") -> IssueCategory.PERFORMANCE
                            categoryStr.contains("安全") -> IssueCategory.SECURITY
                            categoryStr.contains("风格") || categoryStr.contains("规范") -> IssueCategory.CODE_STYLE
                            categoryStr.contains("文档") -> IssueCategory.DOCUMENTATION
                            categoryStr.contains("维护") -> IssueCategory.MAINTAINABILITY
                            else -> {
                                println("问题分类解析失败: $categoryStr，使用默认值 BUG_RISK")
                                IssueCategory.BUG_RISK
                            }
                        }
                    }

                    // 处理lineNumber字段，可能是字符串或整数
                    val lineNumber = try {
                        issueObj.get("lineNumber")?.asInt
                    } catch (e: Exception) {
                        // 如果不是整数，尝试从字符串中提取行号，或者使用null
                        null
                    }

                    val issue = CodeIssue(
                        filePath = issueObj.get("filePath")?.asString ?: "",
                        lineNumber = lineNumber,
                        severity = severity,
                        category = category,
                        message = issueObj.get("message")?.asString ?: "",
                        suggestion = issueObj.get("suggestion")?.asString
                    )
                    issues.add(issue)
                    println("✅ 成功解析问题: 文件=${issue.filePath}, 严重程度=${issue.severity}, 分类=${issue.category}")
                    println("   消息: ${issue.message}")
                } catch (e: Exception) {
                    println("❌ 解析单个问题失败: ${e.message}")
                    println("   问题JSON: ${issueElement}")
                    e.printStackTrace()
                }
            }
            
            val suggestions = mutableListOf<String>()
            val suggestionsArray = jsonObject.getAsJsonArray("suggestions")
            println("解析建议列表，数量: ${suggestionsArray?.size() ?: 0}")

            suggestionsArray?.forEach { suggestion ->
                try {
                    val suggestionText = suggestion.asString
                    suggestions.add(suggestionText)
                    println("解析建议: $suggestionText")
                } catch (e: Exception) {
                    println("解析单个建议失败: ${e.message}")
                }
            }

            val result = CodeReviewResult(
                overallScore = overallScore,
                issues = issues,
                suggestions = suggestions,
                riskLevel = riskLevel,
                summary = summary,
                commitMessage = commitMessage
            )

            println("=== ✅ AI响应解析完成 ===")
            println("最终结果: 评分=$overallScore, 风险=${riskLevel}, 问题=${issues.size}个, 建议=${suggestions.size}条")

            debugCallback?.onParsingResult(true, result, null)
            return result

        } catch (e: Exception) {
            println("=== ❌ AI响应解析失败 ===")
            println("错误信息: ${e.message}")
            println("错误堆栈: ${e.stackTraceToString()}")

            debugCallback?.onParsingResult(false, null, e.message)

            // 解析失败时返回默认结果
            val fallbackResult = CodeReviewResult(
                overallScore = 70,
                issues = emptyList(),
                suggestions = listOf(
                    "AI服务响应解析失败: ${e.message}",
                    "原始响应长度: ${response.length} 字符",
                    "请检查AI服务配置和网络连接"
                ),
                riskLevel = RiskLevel.MEDIUM,
                summary = "AI响应解析失败，无法获取详细的代码评估结果",
                commitMessage = null
            )

            return fallbackResult
        }
    }

    /**
     * 记录AI响应日志
     */
    private fun logAIResponse(response: String) {
        // 这里可以添加UI回调或其他日志记录逻辑
        // 目前先输出到控制台
        println("📥 AI响应已记录，长度: ${response.length} 字符")
    }

    /**
     * 执行基础静态分析（当AI服务不可用时）
     */
    private fun performBasicAnalysis(changes: List<CodeChange>, commitMessage: String): CodeReviewResult {
        val issues = mutableListOf<CodeIssue>()
        val suggestions = mutableListOf<String>()
        var totalScore = 100
        var maxRisk = RiskLevel.LOW

        changes.forEach { change ->
            // 基础的静态分析规则
            analyzeBasicIssues(change, issues)
        }

        // 根据问题数量调整评分
        issues.forEach { issue ->
            when (issue.severity) {
                IssueSeverity.CRITICAL -> {
                    totalScore -= 20
                    maxRisk = RiskLevel.CRITICAL
                }
                IssueSeverity.MAJOR -> {
                    totalScore -= 10
                    if (maxRisk.ordinal < RiskLevel.HIGH.ordinal) maxRisk = RiskLevel.HIGH
                }
                IssueSeverity.MINOR -> {
                    totalScore -= 5
                    if (maxRisk.ordinal < RiskLevel.MEDIUM.ordinal) maxRisk = RiskLevel.MEDIUM
                }
                IssueSeverity.INFO -> totalScore -= 1
            }
        }

        totalScore = maxOf(0, totalScore)

        if (issues.isEmpty()) {
            suggestions.add("代码变更看起来不错！")
        } else {
            suggestions.add("发现了一些可以改进的地方，请查看详细问题列表")
        }

        return CodeReviewResult(
            overallScore = totalScore,
            issues = issues,
            suggestions = suggestions,
            riskLevel = maxRisk,
            summary = "基础静态分析完成，发现 ${issues.size} 个问题"
        )
    }

    /**
     * 分析基础问题
     */
    private fun analyzeBasicIssues(change: CodeChange, issues: MutableList<CodeIssue>) {
        val content = change.newContent ?: return
        val lines = content.lines()

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            
            // 检查长行
            if (line.length > 120) {
                issues.add(
                    CodeIssue(
                        filePath = change.filePath,
                        lineNumber = lineNumber,
                        severity = IssueSeverity.MINOR,
                        category = IssueCategory.CODE_STYLE,
                        message = "行长度超过120字符",
                        suggestion = "考虑将长行拆分为多行"
                    )
                )
            }
            
            // 检查TODO注释
            if (line.contains("TODO", ignoreCase = true)) {
                issues.add(
                    CodeIssue(
                        filePath = change.filePath,
                        lineNumber = lineNumber,
                        severity = IssueSeverity.INFO,
                        category = IssueCategory.DOCUMENTATION,
                        message = "发现TODO注释",
                        suggestion = "考虑完成TODO项目或创建相应的任务"
                    )
                )
            }
            
            // 检查潜在的空指针风险（简单检查）
            if (line.contains("!!") && change.filePath.endsWith(".kt")) {
                issues.add(
                    CodeIssue(
                        filePath = change.filePath,
                        lineNumber = lineNumber,
                        severity = IssueSeverity.MAJOR,
                        category = IssueCategory.BUG_RISK,
                        message = "使用了非空断言操作符(!!)",
                        suggestion = "考虑使用安全调用(?.)或适当的空值检查"
                    )
                )
            }
        }
    }

    override fun getServiceName(): String {
        val primaryService = aiServiceManager.getPrimaryService()
        return "AI Code Review (${primaryService?.getServiceName() ?: "No Service"})"
    }
}
