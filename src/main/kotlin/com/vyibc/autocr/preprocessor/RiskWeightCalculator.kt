package com.vyibc.autocr.preprocessor

import com.vyibc.autocr.model.*
import com.vyibc.autocr.psi.FileAnalysisResult
import org.slf4j.LoggerFactory

/**
 * 风险权重计算器
 * 分析代码变更的安全性、性能和稳定性风险
 */
class RiskWeightCalculator {
    private val logger = LoggerFactory.getLogger(RiskWeightCalculator::class.java)
    
    /**
     * 计算文件变更的风险权重
     */
    fun calculateFileRiskWeight(fileChange: com.vyibc.autocr.model.FileChange): Double {
        var riskWeight = 0.0
        
        // 基于文件类型的风险权重
        riskWeight += calculateFileTypeRiskWeight(fileChange.filePath)
        
        // 基于变更规模的风险评估
        val totalChanges = fileChange.addedMethods.size + 
                          fileChange.modifiedMethods.size + 
                          fileChange.deletedMethods.size
        riskWeight += when {
            totalChanges >= 20 -> 0.8  // 大规模变更风险很高
            totalChanges >= 10 -> 0.6  // 中等规模变更风险较高
            totalChanges >= 5 -> 0.4   // 小规模变更风险适中
            else -> 0.2
        }
        
        // 基于删除操作的风险评估（删除比新增和修改风险更高）
        if (fileChange.deletedMethods.isNotEmpty()) {
            riskWeight += 0.3 + (fileChange.deletedMethods.size * 0.1)
        }
        
        return minOf(1.0, riskWeight)
    }
    
    /**
     * 计算方法变更的风险权重
     */
    fun calculateMethodRiskWeight(method: MethodNode, changeContext: ChangeContext): Double {
        var riskWeight = 0.0
        
        // 基于方法复杂度的风险评估
        riskWeight += calculateComplexityRiskWeight(method)
        
        // 基于安全敏感性的风险评估
        riskWeight += calculateSecurityRiskWeight(method)
        
        // 基于性能敏感性的风险评估
        riskWeight += calculatePerformanceRiskWeight(method)
        
        // 基于稳定性的风险评估
        riskWeight += calculateStabilityRiskWeight(method, changeContext)
        
        // 基于数据操作的风险评估
        riskWeight += calculateDataOperationRiskWeight(method)
        
        // 基于外部依赖的风险评估
        riskWeight += calculateExternalDependencyRiskWeight(method)
        
        return minOf(1.0, riskWeight)
    }
    
    /**
     * 计算调用关系的风险权重
     */
    fun calculateCallRiskWeight(callEdge: CallsEdge, sourceMethod: MethodNode, targetMethod: MethodNode): Double {
        var riskWeight = 0.0
        
        // 跨服务调用风险更高
        if (isCrossServiceCall(sourceMethod, targetMethod)) {
            riskWeight += 0.6
        }
        
        // 数据库操作调用风险较高
        if (isDatabaseOperation(targetMethod)) {
            riskWeight += 0.4
        }
        
        // 异步调用风险
        if (isAsyncCall(targetMethod)) {
            riskWeight += 0.3
        }
        
        // 外部API调用风险
        if (isExternalApiCall(targetMethod)) {
            riskWeight += 0.5
        }
        
        // 新增调用的风险
        if (callEdge.isNewInMR) {
            riskWeight += 0.4
        }
        
        // 调用深度风险（深层调用链风险更高）
        riskWeight += calculateCallDepthRisk(sourceMethod, targetMethod)
        
        return minOf(1.0, riskWeight)
    }
    
    /**
     * 基于文件类型计算风险权重
     */
    private fun calculateFileTypeRiskWeight(filePath: String): Double {
        return when {
            filePath.contains("/config/", ignoreCase = true) -> 0.8 // 配置文件变更风险很高
            filePath.contains("/security/", ignoreCase = true) -> 0.9 // 安全相关文件风险极高
            filePath.contains("/controller/", ignoreCase = true) -> 0.6 // API接口变更风险较高
            filePath.contains("/service/", ignoreCase = true) -> 0.5 // 业务逻辑变更风险中等
            filePath.contains("/repository/", ignoreCase = true) -> 0.7 // 数据访问变更风险较高
            filePath.contains("/entity/", ignoreCase = true) -> 0.6 // 实体变更风险较高
            filePath.contains("/util/", ignoreCase = true) -> 0.4 // 工具类变更风险较低
            filePath.contains("/test/", ignoreCase = true) -> 0.1 // 测试文件变更风险很低
            else -> 0.3
        }
    }
    
    /**
     * 基于方法复杂度计算风险权重
     */
    private fun calculateComplexityRiskWeight(method: MethodNode): Double {
        var riskWeight = 0.0
        
        // 圈复杂度风险评估
        riskWeight += when {
            method.cyclomaticComplexity >= 20 -> 0.8 // 极高复杂度
            method.cyclomaticComplexity >= 15 -> 0.6 // 高复杂度
            method.cyclomaticComplexity >= 10 -> 0.4 // 中等复杂度
            method.cyclomaticComplexity >= 5 -> 0.2  // 低复杂度
            else -> 0.0
        }
        
        // 代码行数风险评估
        riskWeight += when {
            method.linesOfCode >= 100 -> 0.6 // 超长方法
            method.linesOfCode >= 50 -> 0.4  // 长方法
            method.linesOfCode >= 30 -> 0.2  // 中等长度方法
            else -> 0.0
        }
        
        return riskWeight
    }
    
    /**
     * 基于安全敏感性计算风险权重
     */
    private fun calculateSecurityRiskWeight(method: MethodNode): Double {
        var riskWeight = 0.0
        
        // 安全相关注解
        method.annotations.forEach { annotation ->
            riskWeight += when {
                annotation.contains("@PreAuthorize") || annotation.contains("@PostAuthorize") -> 0.6
                annotation.contains("@Secured") -> 0.5
                annotation.contains("@RolesAllowed") -> 0.4
                annotation.contains("@EnableGlobalMethodSecurity") -> 0.8
                else -> 0.0
            }
        }
        
        // 方法名包含安全敏感词汇
        val securityKeywords = listOf(
            "authenticate", "authorize", "login", "logout", "password", "token", 
            "encrypt", "decrypt", "security", "permission", "access", "credential",
            "validate", "verify", "sanitize"
        )
        
        val methodNameLower = method.methodName.lowercase()
        val signatureLower = method.signature.lowercase()
        
        securityKeywords.forEach { keyword ->
            if (methodNameLower.contains(keyword) || signatureLower.contains(keyword)) {
                riskWeight += 0.3
            }
        }
        
        return minOf(0.8, riskWeight)
    }
    
    /**
     * 基于性能敏感性计算风险权重
     */
    private fun calculatePerformanceRiskWeight(method: MethodNode): Double {
        var riskWeight = 0.0
        
        // 性能相关注解
        method.annotations.forEach { annotation ->
            riskWeight += when {
                annotation.contains("@Async") -> 0.4
                annotation.contains("@Cacheable") || annotation.contains("@CacheEvict") -> 0.3
                annotation.contains("@Transactional") -> 0.5
                annotation.contains("@Scheduled") -> 0.4
                else -> 0.0
            }
        }
        
        // 性能敏感的方法名模式
        val performanceKeywords = listOf(
            "batch", "bulk", "process", "execute", "calculate", "compute",
            "generate", "transform", "parse", "serialize", "deserialize"
        )
        
        val methodNameLower = method.methodName.lowercase()
        performanceKeywords.forEach { keyword ->
            if (methodNameLower.contains(keyword)) {
                riskWeight += 0.2
            }
        }
        
        // 循环和递归风险（基于复杂度间接评估）
        if (method.cyclomaticComplexity > 10) {
            riskWeight += 0.3
        }
        
        return minOf(0.6, riskWeight)
    }
    
    /**
     * 基于稳定性计算风险权重
     */
    private fun calculateStabilityRiskWeight(method: MethodNode, changeContext: ChangeContext): Double {
        var riskWeight = 0.0
        
        // 核心业务方法风险更高
        if (method.blockType in listOf(BlockType.SERVICE, BlockType.CONTROLLER)) {
            riskWeight += 0.4
        }
        
        // 高调用频率方法风险更高
        if (method.inDegree > 10) {
            riskWeight += 0.5
        }
        
        // 异常处理相关
        val exceptionKeywords = listOf("exception", "error", "fail", "throw", "catch", "try")
        val methodNameLower = method.methodName.lowercase()
        exceptionKeywords.forEach { keyword ->
            if (methodNameLower.contains(keyword)) {
                riskWeight += 0.2
            }
        }
        
        // 变更类型风险评估
        riskWeight += when (changeContext.changeType) {
            "METHOD_DELETED" -> 0.8    // 删除方法风险最高
            "METHOD_MODIFIED" -> 0.4   // 修改方法风险中等
            "NEW_METHOD" -> 0.2        // 新增方法风险较低
            else -> 0.1
        }
        
        return riskWeight
    }
    
    /**
     * 基于数据操作计算风险权重
     */
    private fun calculateDataOperationRiskWeight(method: MethodNode): Double {
        var riskWeight = 0.0
        
        // 数据库操作注解
        method.annotations.forEach { annotation ->
            riskWeight += when {
                annotation.contains("@Modifying") -> 0.7  // 数据修改操作风险很高
                annotation.contains("@Query") -> 0.4      // 查询操作风险中等
                annotation.contains("@Transactional") -> 0.5 // 事务操作风险较高
                else -> 0.0
            }
        }
        
        // 数据操作方法名模式
        val dataOperationKeywords = listOf(
            "save", "update", "delete", "remove", "insert", "create",
            "modify", "change", "alter", "drop", "truncate"
        )
        
        val methodNameLower = method.methodName.lowercase()
        dataOperationKeywords.forEach { keyword ->
            if (methodNameLower.startsWith(keyword)) {
                riskWeight += when (keyword) {
                    "delete", "remove", "drop", "truncate" -> 0.8 // 删除操作风险最高
                    "update", "modify", "alter" -> 0.6           // 更新操作风险较高
                    "save", "insert", "create" -> 0.4            // 创建操作风险中等
                    else -> 0.2
                }
            }
        }
        
        return minOf(0.8, riskWeight)
    }
    
    /**
     * 基于外部依赖计算风险权重
     */
    private fun calculateExternalDependencyRiskWeight(method: MethodNode): Double {
        var riskWeight = 0.0
        
        // 外部服务调用注解
        method.annotations.forEach { annotation ->
            riskWeight += when {
                annotation.contains("@FeignClient") -> 0.6    // Feign客户端调用
                annotation.contains("@RestTemplate") -> 0.5   // REST调用
                annotation.contains("@RabbitListener") -> 0.4 // 消息队列
                annotation.contains("@KafkaListener") -> 0.4  // Kafka消费
                else -> 0.0
            }
        }
        
        // 外部依赖相关方法名
        val externalKeywords = listOf(
            "remote", "external", "api", "client", "service", "call",
            "request", "response", "http", "rest", "rpc"
        )
        
        val methodNameLower = method.methodName.lowercase()
        externalKeywords.forEach { keyword ->
            if (methodNameLower.contains(keyword)) {
                riskWeight += 0.2
            }
        }
        
        return minOf(0.6, riskWeight)
    }
    
    /**
     * 计算调用深度风险
     */
    private fun calculateCallDepthRisk(sourceMethod: MethodNode, targetMethod: MethodNode): Double {
        // 基于方法的出度间接评估调用深度风险
        return when {
            targetMethod.outDegree > 15 -> 0.4  // 深层调用链
            targetMethod.outDegree > 10 -> 0.3  // 中等深度调用链
            targetMethod.outDegree > 5 -> 0.2   // 较浅调用链
            else -> 0.1
        }
    }
    
    /**
     * 检查是否为跨服务调用
     */
    private fun isCrossServiceCall(sourceMethod: MethodNode, targetMethod: MethodNode): Boolean {
        val sourcePackage = sourceMethod.getPackageName()
        val targetPackage = targetMethod.getPackageName()
        
        // 简单的包名比较，实际可能需要更复杂的服务边界识别
        return sourcePackage != targetPackage && 
               (sourcePackage.split(".").size >= 4 && targetPackage.split(".").size >= 4)
    }
    
    /**
     * 检查是否为数据库操作
     */
    private fun isDatabaseOperation(method: MethodNode): Boolean {
        return method.blockType in listOf(BlockType.REPOSITORY, BlockType.MAPPER) ||
               method.annotations.any { 
                   it.contains("@Query") || it.contains("@Modifying") || 
                   it.contains("@Repository") || it.contains("@Mapper")
               }
    }
    
    /**
     * 检查是否为异步调用
     */
    private fun isAsyncCall(method: MethodNode): Boolean {
        return method.annotations.any { it.contains("@Async") } ||
               method.returnType.contains("CompletableFuture") ||
               method.returnType.contains("Future") ||
               method.methodName.contains("async", ignoreCase = true)
    }
    
    /**
     * 检查是否为外部API调用
     */
    private fun isExternalApiCall(method: MethodNode): Boolean {
        return method.annotations.any { 
            it.contains("@FeignClient") || it.contains("@RestTemplate") ||
            it.contains("@RequestMapping") || it.contains("@GetMapping") ||
            it.contains("@PostMapping") || it.contains("@PutMapping") ||
            it.contains("@DeleteMapping")
        }
    }
}

/**
 * 文件变更信息
 */
// 使用 com.vyibc.autocr.model.FileChange 替代

/**
 * 变更类型
 */
// 使用 com.vyibc.autocr.model.ChangeType 替代