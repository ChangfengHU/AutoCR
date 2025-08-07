package com.vyibc.autocr.preprocessor

import com.vyibc.autocr.model.*
import com.vyibc.autocr.psi.FileAnalysisResult
import org.slf4j.LoggerFactory

/**
 * 意图权重计算器
 * 分析代码变更的业务意图和重要性
 */
class IntentWeightCalculator {
    private val logger = LoggerFactory.getLogger(IntentWeightCalculator::class.java)
    
    /**
     * 计算文件变更的意图权重
     */
    fun calculateFileIntentWeight(fileChange: com.vyibc.autocr.model.FileChange): Double {
        var intentWeight = 0.0
        
        // 基于变更类型的基础权重
        intentWeight += when (fileChange.changeType) {
            com.vyibc.autocr.model.ChangeType.ADDED -> 0.8    // 新增文件通常表示新功能
            com.vyibc.autocr.model.ChangeType.MODIFIED -> 0.6 // 修改文件表示功能调整
            com.vyibc.autocr.model.ChangeType.DELETED -> 0.4  // 删除文件表示功能移除
        }
        
        // 基于文件路径的权重调整
        intentWeight += calculatePathIntentWeight(fileChange.filePath)
        
        // 基于变更规模的权重调整
        val totalChanges = fileChange.addedMethods.size + 
                          fileChange.modifiedMethods.size + 
                          fileChange.deletedMethods.size
        intentWeight += when {
            totalChanges >= 10 -> 0.3  // 大规模变更
            totalChanges >= 5 -> 0.2   // 中等规模变更
            totalChanges >= 1 -> 0.1   // 小规模变更
            else -> 0.0
        }
        
        return minOf(1.0, intentWeight)
    }
    
    /**
     * 计算方法变更的意图权重
     */
    fun calculateMethodIntentWeight(method: MethodNode, changeContext: ChangeContext): Double {
        var intentWeight = 0.0
        
        // 基于方法类型的权重
        intentWeight += when (method.blockType) {
            BlockType.CONTROLLER -> 0.9  // API接口变更影响最大
            BlockType.SERVICE -> 0.8     // 业务逻辑变更很重要
            BlockType.REPOSITORY -> 0.7  // 数据访问变更重要
            BlockType.ENTITY -> 0.6      // 实体变更影响数据模型
            BlockType.CONFIG -> 0.5      // 配置变更需要关注
            BlockType.UTIL -> 0.3        // 工具类变更影响相对较小
            BlockType.DTO -> 0.4         // DTO变更影响接口
            else -> 0.2
        }
        
        // 基于注解的权重调整
        intentWeight += calculateAnnotationIntentWeight(method.annotations)
        
        // 基于方法名的权重调整
        intentWeight += calculateMethodNameIntentWeight(method.methodName)
        
        // 基于变更类型的权重调整
        intentWeight += when (changeContext.changeType) {
            "NEW_METHOD" -> 0.3       // 新增方法
            "METHOD_MODIFIED" -> 0.2  // 方法修改
            "METHOD_DELETED" -> 0.4   // 方法删除
            else -> 0.1
        }
        
        // 基于业务术语的权重调整
        intentWeight += calculateBusinessTermWeight(method.methodName, method.signature)
        
        return minOf(1.0, intentWeight)
    }
    
    /**
     * 计算调用关系的意图权重
     */
    fun calculateCallIntentWeight(callEdge: CallsEdge, sourceMethod: MethodNode, targetMethod: MethodNode): Double {
        var intentWeight = 0.0
        
        // 跨层调用的权重更高
        if (isArchitecturalLayerCrossing(sourceMethod.blockType, targetMethod.blockType)) {
            intentWeight += 0.4
        }
        
        // 外部API调用权重更高
        if (isExternalApiCall(callEdge, targetMethod)) {
            intentWeight += 0.5
        }
        
        // 数据库操作调用权重较高
        if (isDatabaseOperation(targetMethod)) {
            intentWeight += 0.3
        }
        
        // 事务性操作权重较高
        if (isTransactionalOperation(targetMethod)) {
            intentWeight += 0.3
        }
        
        // 新增或修改的调用权重更高
        if (callEdge.isNewInMR) {
            intentWeight += 0.4
        }
        if (callEdge.isModifiedInMR) {
            intentWeight += 0.3
        }
        
        return minOf(1.0, intentWeight)
    }
    
    /**
     * 基于文件路径计算意图权重
     */
    private fun calculatePathIntentWeight(filePath: String): Double {
        return when {
            filePath.contains("/controller/", ignoreCase = true) -> 0.3
            filePath.contains("/service/", ignoreCase = true) -> 0.25
            filePath.contains("/repository/", ignoreCase = true) -> 0.2
            filePath.contains("/config/", ignoreCase = true) -> 0.15
            filePath.contains("/entity/", ignoreCase = true) -> 0.2
            filePath.contains("/dto/", ignoreCase = true) -> 0.15
            filePath.contains("/util/", ignoreCase = true) -> 0.05
            filePath.contains("/test/", ignoreCase = true) -> 0.1
            else -> 0.0
        }
    }
    
    /**
     * 基于注解计算意图权重
     */
    private fun calculateAnnotationIntentWeight(annotations: List<String>): Double {
        var weight = 0.0
        
        annotations.forEach { annotation ->
            weight += when {
                annotation.contains("@RestController") || annotation.contains("@Controller") -> 0.3
                annotation.contains("@Service") -> 0.25
                annotation.contains("@Repository") -> 0.2
                annotation.contains("@Transactional") -> 0.2
                annotation.contains("@RequestMapping") || 
                annotation.contains("@GetMapping") || 
                annotation.contains("@PostMapping") -> 0.25
                annotation.contains("@Async") -> 0.15
                annotation.contains("@Scheduled") -> 0.15
                annotation.contains("@PreAuthorize") || annotation.contains("@PostAuthorize") -> 0.2
                else -> 0.0
            }
        }
        
        return minOf(0.5, weight)
    }
    
    /**
     * 基于方法名计算意图权重
     */
    private fun calculateMethodNameIntentWeight(methodName: String): Double {
        return when {
            // CRUD操作
            methodName.startsWith("create") || methodName.startsWith("save") -> 0.2
            methodName.startsWith("update") || methodName.startsWith("modify") -> 0.2
            methodName.startsWith("delete") || methodName.startsWith("remove") -> 0.25
            methodName.startsWith("find") || methodName.startsWith("get") || methodName.startsWith("query") -> 0.1
            
            // 业务操作
            methodName.contains("process") || methodName.contains("handle") -> 0.2
            methodName.contains("calculate") || methodName.contains("compute") -> 0.15
            methodName.contains("validate") || methodName.contains("check") -> 0.1
            methodName.contains("transform") || methodName.contains("convert") -> 0.1
            
            // 重要的生命周期方法
            methodName == "init" || methodName == "initialize" -> 0.15
            methodName == "destroy" || methodName == "close" -> 0.15
            methodName.startsWith("setup") || methodName.startsWith("configure") -> 0.1
            
            else -> 0.0
        }
    }
    
    /**
     * 基于业务术语计算权重
     */
    private fun calculateBusinessTermWeight(methodName: String, signature: String): Double {
        val businessTerms = listOf(
            "order", "payment", "user", "customer", "product", "inventory",
            "billing", "invoice", "transaction", "account", "balance",
            "notification", "email", "sms", "report", "audit", "security"
        )
        
        val text = "$methodName $signature".lowercase()
        val matchedTerms = businessTerms.count { term -> text.contains(term) }
        
        return minOf(0.3, matchedTerms * 0.1)
    }
    
    /**
     * 检查是否为架构层跨越
     */
    private fun isArchitecturalLayerCrossing(sourceType: BlockType, targetType: BlockType): Boolean {
        val layerOrder = mapOf(
            BlockType.CONTROLLER to 1,
            BlockType.SERVICE to 2,
            BlockType.REPOSITORY to 3,
            BlockType.MAPPER to 3,
            BlockType.ENTITY to 4
        )
        
        val sourceOrder = layerOrder[sourceType] ?: 0
        val targetOrder = layerOrder[targetType] ?: 0
        
        // 跨越超过1层被认为是架构层跨越
        return kotlin.math.abs(sourceOrder - targetOrder) > 1
    }
    
    /**
     * 检查是否为外部API调用
     */
    private fun isExternalApiCall(callEdge: CallsEdge, targetMethod: MethodNode): Boolean {
        return targetMethod.annotations.any { annotation ->
            annotation.contains("@RequestMapping") || 
            annotation.contains("@GetMapping") || 
            annotation.contains("@PostMapping") ||
            annotation.contains("@PutMapping") ||
            annotation.contains("@DeleteMapping")
        } || callEdge.callType == "external"
    }
    
    /**
     * 检查是否为数据库操作
     */
    private fun isDatabaseOperation(method: MethodNode): Boolean {
        return method.blockType == BlockType.REPOSITORY || 
               method.blockType == BlockType.MAPPER ||
               method.annotations.any { it.contains("@Query") || it.contains("@Modifying") }
    }
    
    /**
     * 检查是否为事务性操作
     */
    private fun isTransactionalOperation(method: MethodNode): Boolean {
        return method.annotations.any { it.contains("@Transactional") }
    }
}

/**
 * 变更上下文
 */
data class ChangeContext(
    val changeType: String,
    val isNewInMR: Boolean = false,
    val affectedFiles: List<String> = emptyList(),
    val relatedChanges: List<String> = emptyList()
)