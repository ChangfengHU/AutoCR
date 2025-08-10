package com.vyibc.autocr.service

import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory

/**
 * 提示词模板管理器
 * 实现技术方案V5.1中的AI Prompt模板系统
 * 负责构建结构化的、针对不同AI模型优化的提示词
 */
class PromptTemplateManager {
    
    private val logger = LoggerFactory.getLogger(PromptTemplateManager::class.java)
    
    /**
     * 构建快速筛选提示词（阶段1）
     */
    fun buildQuickScreeningPrompt(context: ScreeningContext): String {
        logger.debug("构建快速筛选提示词")
        
        return """
# Role: Senior Code Reviewer (Quick Screening)

You are performing a rapid initial screening of code changes to identify the most important paths for detailed analysis.

## Input Context
**Total Methods Analyzed**: ${context.allPaths.size}
**Changed Files**: ${context.changedFiles.size}
**Commit Messages**: ${context.commitMessages.joinToString("; ")}

## All Detected Paths
${formatPathsForScreening(context.allPaths)}

## Your Task
Based on the provided paths and their preliminary weights, select:

1. **Top 2 Intent Paths** (Golden Paths): The paths most likely representing the core feature/functionality being implemented
2. **Top 3-5 Risk Paths** (High-Risk Paths): The paths with highest potential for introducing bugs or architectural issues

## Output Format (JSON only, no explanation)
```json
{
  "golden_paths": [
    {
      "path_id": "path_001",
      "reason": "Main user registration flow with new endpoint",
      "confidence": 0.9
    }
  ],
  "risk_paths": [
    {
      "path_id": "path_003", 
      "reason": "Direct controller to DAO access bypassing service layer",
      "risk_level": "HIGH",
      "confidence": 0.85
    }
  ]
}
```

Be concise and focus on the most critical paths only.
        """.trimIndent()
    }
    
    /**
     * 构建深度分析提示词（阶段2）
     */
    fun buildDeepAnalysisPrompt(context: AnalysisContext): String {
        logger.debug("构建深度分析提示词")
        
        return """
# Role: Senior Software Architect (Deep Analysis)

You are conducting a comprehensive code review for a Merge Request. Your task is to analyze the developer's intended functionality and potential risks, then provide a balanced review.

## Project Context
**Branch**: ${context.gitContext.sourceBranch} → ${context.gitContext.targetBranch}
**Total Changes**: ${context.gitContext.changedFiles.size} files, +${context.gitContext.addedLines}/-${context.gitContext.deletedLines} lines
**Analysis Type**: ${context.analysisType.displayName}
**Build Time**: ${java.time.Instant.ofEpochMilli(context.buildTimestamp)}

# ==========================================
# Part 1: Intent Analysis (The "What")
# ==========================================

## Context for Intent Analysis
${formatIntentContext(context)}

## Business Context
- **Business Keywords**: ${context.businessContext.businessKeywords.joinToString(", ")}
- **Domain Entities**: ${context.businessContext.domainEntities.joinToString(", ")}
- **Business Processes**: ${context.businessContext.businessProcesses.joinToString(", ")}

### 1.1 Functionality Implemented
Describe the business feature or technical functionality the developer has added or changed. Focus on the "what" and "why".

### 1.2 Implementation Summary  
Briefly explain how this functionality was technically realized. Include:
- Key design decisions
- Technology choices
- Integration patterns used

### 1.3 Business Value Assessment
Evaluate the value this change brings:
- **User Impact**: How does this benefit end users?
- **Technical Debt**: Does this reduce or increase technical debt?
- **Maintainability**: How will this affect future development?

# ==========================================
# Part 2: Impact & Risk Analysis (The "How")
# ==========================================

## Context for Risk Analysis
${formatRiskContext(context)}

## Architecture Context
- **Involved Layers**: ${context.architectureContext.involvedLayers.joinToString(", ")}
- **Module Boundaries**: ${context.architectureContext.moduleBoundaries.joinToString(", ")}
- **Design Patterns**: ${context.architectureContext.designPatterns.joinToString(", ")}

## Test Coverage Analysis
- **Overall Coverage Ratio**: ${(context.testCoverage.overallCoverageRatio * 100).toInt()}%
- **Missing Test Files**: ${context.testCoverage.missingTestFiles.size}

### 2.1 Potential Bugs & Issues
Identify specific potential bugs, logical errors, or edge cases:
- **Logic Errors**: Flawed conditional logic, null pointer risks, etc.
- **Concurrency Issues**: Race conditions, deadlock risks, thread safety
- **Data Integrity**: Validation gaps, data corruption risks
- **Security Vulnerabilities**: Authentication bypass, data exposure

### 2.2 Architectural Concerns
Point out violations of software architecture principles:
- **Layer Violations**: Cross-layer dependencies, bypassed abstractions
- **SOLID Violations**: Single responsibility, open/closed, etc.
- **Design Pattern Misuse**: Inappropriate or incorrect pattern usage
- **Coupling Issues**: Tight coupling, circular dependencies

### 2.3 Maintenance & Operations Issues
Highlight changes that impact maintainability:
- **Code Complexity**: Overly complex methods, deep nesting
- **Performance Impact**: Potential bottlenecks, resource usage
- **Monitoring Gaps**: Missing logging, error handling
- **Documentation Debt**: Undocumented complex logic

# ==========================================
# Part 3: Holistic Review & Final Verdict
# ==========================================

### 3.1 Overall Summary
Acknowledge the value of the implemented feature while recognizing the identified risks. Be balanced and constructive.

### 3.2 Actionable Recommendations
Provide a prioritized list of concrete suggestions:

**🔴 Critical - Must Fix Before Merge**:
- Issue: [Specific problem]
- Impact: [Why this is critical]
- Solution: [Concrete fix]
- Location: [File:line reference]

**🟡 Important - Should Fix Soon**:
- Issue: [Specific problem]
- Impact: [Potential consequences]
- Solution: [Recommended approach]

**🟢 Suggestion - Consider for Future**:
- Opportunity: [Improvement area]
- Benefit: [Expected value]
- Approach: [How to implement]

### 3.3 Test Strategy Recommendations
Based on the identified risks, suggest:
- **Missing Test Cases**: Specific scenarios that need testing
- **Test Types**: Unit, integration, end-to-end requirements
- **Edge Cases**: Boundary conditions to validate

### 3.4 Final Approval Status
Choose one and provide brief reasoning:

- **✅ Approved - Ready to Merge**: Code meets quality standards with minor or no issues
- **⚠️ Approved with Conditions**: Can merge after addressing critical issues listed above  
- **❌ Requires Rework**: Significant issues need resolution before merge consideration

**Reasoning**: [1-2 sentences explaining your decision]

## Output Guidelines (JSON Format)
Return your analysis as a structured JSON response:
```json
{
  "intent_analysis": [
    {
      "description": "...",
      "business_value": 85.0,
      "implementation_summary": "...",
      "related_paths": ["path1", "path2"],
      "confidence": 0.9
    }
  ],
  "risk_analysis": [
    {
      "description": "...",
      "category": "ARCHITECTURE",
      "severity": "HIGH",
      "impact": "...",
      "recommendation": "...",
      "location": "file.java:123"
    }
  ],
  "overall_recommendation": {
    "approval_status": "APPROVED_WITH_CONDITIONS",
    "reasoning": "...",
    "critical_issues": ["..."],
    "suggestions": ["..."]
  }
}
```
        """.trimIndent()
    }
    
    /**
     * 格式化路径信息用于筛选
     */
    private fun formatPathsForScreening(paths: List<CallPath>): String {
        return paths.take(20).mapIndexed { index, path ->
            """
Path ${index + 1} (ID: ${path.id}):
- Description: ${path.description}
- Methods: ${path.methods.take(5).joinToString(" -> ")}
- Intent Weight: ${path.intentWeight}
- Risk Weight: ${path.riskWeight}
            """.trimIndent()
        }.joinToString("\n\n")
    }
    
    /**
     * 格式化意图上下文
     */
    private fun formatIntentContext(context: AnalysisContext): String {
        val goldenPaths = context.selectedPaths.goldenPaths
        
        return if (goldenPaths.isNotEmpty()) {
            """
**Golden Path Details**:
${goldenPaths.joinToString("\n") { "- ${it.reason} (confidence: ${it.confidence})" }}

**Related Code Changes**:
${formatMethodBodies(context.methodBodies.take(5))}

**Developer's Stated Intent (Commit Messages)**:
${context.gitContext.commits.map { it.message }.joinToString("; ")}
            """.trimIndent()
        } else {
            "No specific golden paths identified for intent analysis."
        }
    }
    
    /**
     * 格式化风险上下文
     */
    private fun formatRiskContext(context: AnalysisContext): String {
        val riskPaths = context.selectedPaths.riskPaths
        
        return if (riskPaths.isNotEmpty()) {
            """
**High-Risk Paths**:
${riskPaths.joinToString("\n") { "- ${it.reason} (confidence: ${it.confidence})" }}

**Impact Analysis**:
- Change Scope: ${context.impactAnalysis.changeScope.totalLinesChanged} lines changed
- Impact Radius: ${context.impactAnalysis.impactRadius}
- Regression Risk: ${(context.impactAnalysis.regressionRisk * 100).toInt()}%
- Risk Hotspots: ${context.impactAnalysis.riskHotspots.joinToString(", ")}
            """.trimIndent()
        } else {
            "No specific risk paths identified for analysis."
        }
    }
    
    /**
     * 格式化方法体信息
     */
    private fun formatMethodBodies(methodBodies: List<MethodBodyContext>): String {
        return methodBodies.mapIndexed { index, method ->
            """
Method ${index + 1}: ${method.signature}
- File: ${method.filePath}
- Change Type: ${method.changeType}
- Complexity: ${method.complexity}
- Added Lines: ${method.addedLines.size}
- Deleted Lines: ${method.deletedLines.size}
            """.trimIndent()
        }.joinToString("\n\n")
    }
    
    /**
     * 构建功能价值分析提示词（三阶段分析第一阶段：功）
     */
    fun buildMeritAnalysisPrompt(context: AnalysisContext): String {
        logger.debug("构建功能价值分析提示词")
        
        return """
# Role: Senior Business Analyst & Software Architect

You are performing the first stage of a three-stage code review analysis: **Merit Analysis (功)**
Your focus is to identify and analyze the **functional value and business benefits** of this code change.

## Code Change Context
${formatIntentContext(context)}

## Your Specific Tasks

### 1. Functional Value Assessment
Analyze the actual functional improvements:
- What new capabilities are being added?
- What existing features are being enhanced?
- How does this change serve user needs?

### 2. Business Impact Analysis  
Evaluate the business value:
- Revenue impact (if applicable)
- User experience improvements
- Operational efficiency gains
- Technical debt reduction

### 3. Implementation Quality
Assess how well the functionality is implemented:
- Code clarity and maintainability
- Architecture alignment
- Performance considerations
- Scalability factors

## Output Requirements

Provide a structured analysis focusing ONLY on positive aspects and benefits:

**FUNCTIONAL MERITS:**
- Merit 1: [Description] (Business Value: X/100)
- Merit 2: [Description] (Business Value: X/100)
- Merit 3: [Description] (Business Value: X/100)

**OVERALL BUSINESS VALUE:** X/100

**FUNCTIONAL COMPLETENESS:** [0-1 rating]

**USER EXPERIENCE IMPACT:** [0-1 rating]

**IMPLEMENTATION DETAILS:**
- Key implementation highlights
- Architecture benefits
- Technical advantages

Be specific, objective, and focus on measurable benefits.
        """.trimIndent()
    }
    
    /**
     * 构建风险缺陷分析提示词（三阶段分析第二阶段：过）
     */
    fun buildFlawAnalysisPrompt(context: AnalysisContext): String {
        logger.debug("构建风险缺陷分析提示词")
        
        return """
# Role: Senior Quality Assurance Engineer & Security Expert

You are performing the second stage of a three-stage code review analysis: **Flaw Analysis (过)**
Your focus is to identify and analyze **risks, defects, and potential problems** in this code change.

## Code Change Context
${formatRiskContext(context)}

## Your Specific Tasks

### 1. Technical Risk Assessment
Identify potential technical issues:
- Code quality problems
- Performance bottlenecks
- Memory leaks or resource issues
- Error handling gaps

### 2. Architectural Concerns
Evaluate architectural risks:
- Design pattern violations
- Coupling/cohesion issues
- Layer violation risks
- Scalability limitations

### 3. Security & Reliability Analysis
Assess security and reliability risks:
- Security vulnerabilities
- Data integrity risks
- Failure scenarios
- Recovery mechanisms

## Output Requirements

Provide a structured analysis focusing ONLY on risks, problems, and potential issues:

**IDENTIFIED FLAWS:**
- Flaw 1: [Description] (Severity: HIGH/MEDIUM/LOW, Category: ARCHITECTURE/PERFORMANCE/SECURITY)
- Flaw 2: [Description] (Severity: HIGH/MEDIUM/LOW, Category: ARCHITECTURE/PERFORMANCE/SECURITY)
- Flaw 3: [Description] (Severity: HIGH/MEDIUM/LOW, Category: ARCHITECTURE/PERFORMANCE/SECURITY)

**OVERALL RISK LEVEL:** [CRITICAL/HIGH/MEDIUM/LOW]

**CRITICAL ISSUE COUNT:** X

**ARCHITECTURAL CONCERNS:**
- [Specific architectural risks]

**IMMEDIATE RECOMMENDATIONS:**
- [Urgent fixes needed]

Be thorough, specific, and prioritize issues by severity and impact.
        """.trimIndent()
    }
    
    /**
     * 构建综合决策分析提示词（三阶段分析第三阶段：策）
     */
    fun buildSuggestionAnalysisPrompt(
        context: AnalysisContext,
        meritResult: MeritAnalysisResult,
        flawResult: FlawAnalysisResult
    ): String {
        logger.debug("构建综合决策分析提示词")
        
        return """
# Role: Senior Technical Manager & Decision Maker

You are performing the final stage of a three-stage code review analysis: **Decision Analysis (策)**
Your role is to synthesize the previous analyses and make **final recommendations and decisions**.

## Previous Analysis Results

### Merit Analysis Results (功):
**Overall Business Value:** ${meritResult.overallBusinessValue}/100
**Functional Completeness:** ${meritResult.functionalCompleteness}
**User Experience Impact:** ${meritResult.userExperienceImpact}

**Identified Merits:**
${meritResult.merits.joinToString("\n") { "- ${it.description} (Value: ${it.businessValue}/100)" }}

### Flaw Analysis Results (过):
**Overall Risk Level:** ${flawResult.riskLevel}
**Critical Issues:** ${flawResult.criticalIssueCount}

**Identified Flaws:**
${flawResult.flaws.joinToString("\n") { "- ${it.description} (Severity: ${it.severity})" }}

## Your Decision Framework

### 1. Cost-Benefit Analysis
Weigh the business value against the risks:
- Is the business value (${meritResult.overallBusinessValue}/100) worth the risk level (${flawResult.riskLevel})?
- What are the opportunity costs of delaying vs. proceeding?

### 2. Risk Mitigation Assessment
Evaluate if risks can be managed:
- Can critical issues be resolved before deployment?
- Are there sufficient safeguards in place?
- What monitoring/rollback mechanisms exist?

### 3. Strategic Alignment
Consider broader implications:
- How does this align with product roadmap?
- What are the long-term architectural implications?
- Does this set good precedents for the team?

## Final Decision Requirements

Provide a comprehensive decision with clear reasoning:

**FINAL DECISION:** [APPROVED/APPROVED_WITH_CONDITIONS/REJECTED]

**DECISION REASONING:**
[Detailed explanation of why this decision was made, considering both merits and flaws]

**ACTIONABLE RECOMMENDATIONS:**
1. [Specific action item]
2. [Specific action item]  
3. [Specific action item]

**PRIORITIZED TASKS:**
1. [Highest priority task]
2. [Medium priority task]
3. [Lower priority task]

**CRITICAL ISSUES TO RESOLVE:** 
${if (flawResult.criticalIssueCount > 0) flawResult.flaws.filter { it.severity == "CRITICAL" }.joinToString("\n") { "- ${it.description}" } else "[None]"}

**APPROVAL CONDITIONS** (if applicable):
- [Condition 1]
- [Condition 2]
- [Condition 3]

Be decisive, practical, and provide clear next steps for the development team.
        """.trimIndent()
    }
}

/**
 * 上下文压缩器
 * 负责智能压缩提示词上下文以适应不同AI模型的token限制
 */
class ContextCompressor {
    
    private val logger = LoggerFactory.getLogger(ContextCompressor::class.java)
    
    /**
     * 压缩提示词以适应token限制
     */
    fun compressPrompt(originalPrompt: String, maxTokens: Int): String {
        val estimatedTokens = estimateTokenCount(originalPrompt)
        
        logger.debug("原始提示词token数: $estimatedTokens, 最大限制: $maxTokens")
        
        return if (estimatedTokens <= maxTokens) {
            // 无需压缩
            originalPrompt
        } else {
            // 需要压缩
            val compressionRatio = maxTokens.toDouble() / estimatedTokens
            when {
                compressionRatio >= 0.8 -> lightCompression(originalPrompt)
                compressionRatio >= 0.5 -> mediumCompression(originalPrompt)
                else -> heavyCompression(originalPrompt)
            }
        }
    }
    
    /**
     * 轻度压缩：移除非关键信息
     */
    private fun lightCompression(prompt: String): String {
        return prompt
            .replace(Regex("\\n\\s*\\n\\s*\\n"), "\n\n") // 移除多余空行
            .replace(Regex("\\s{2,}"), " ") // 压缩多余空格
            .replace("## Output Guidelines", "## Output") // 简化标题
            .replace("Be concise and focus on the most critical paths only.", "") // 移除冗余说明
    }
    
    /**
     * 中度压缩：保留核心信息，简化描述
     */
    private fun mediumCompression(prompt: String): String {
        var compressed = lightCompression(prompt)
        
        // 简化示例代码
        compressed = compressed.replace(Regex("```json\\n[\\s\\S]*?```"), "```json\n{\"example\": \"simplified\"}\n```")
        
        // 移除详细说明段落
        compressed = compressed.replace(Regex("### \\d\\.\\d [^\\n]*\\n[^#]*"), "") 
        
        // 保留主要结构
        return compressed
    }
    
    /**
     * 重度压缩：只保留最核心的信息
     */
    private fun heavyCompression(prompt: String): String {
        // 提取关键信息
        val roleMatch = Regex("# Role: ([^\\n]+)").find(prompt)
        val contextMatch = Regex("## Project Context\\n([^#]+)").find(prompt)
        
        return """
${roleMatch?.value ?: "# Role: Code Reviewer"}

${contextMatch?.value ?: "## Context\nCode review analysis needed."}

## Task
Analyze the code changes and provide:
1. Intent analysis (what was implemented)
2. Risk analysis (potential issues)
3. Overall recommendation

## Output (JSON only)
```json
{
  "intent_analysis": [{"description": "...", "business_value": 85.0, "confidence": 0.9}],
  "risk_analysis": [{"description": "...", "category": "...", "severity": "..."}],
  "overall_recommendation": {"approval_status": "...", "reasoning": "..."}
}
```
        """.trimIndent()
    }
    
    /**
     * 估算token数量（粗略估算：4个字符约等于1个token）
     */
    private fun estimateTokenCount(text: String): Int {
        return (text.length / 4.0).toInt()
    }
}