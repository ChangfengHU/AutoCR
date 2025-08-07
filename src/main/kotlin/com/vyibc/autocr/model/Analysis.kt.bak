package com.vyibc.autocr.model

import java.time.Duration
import java.time.Instant

/**
 * Represents a call path in the code graph
 */
data class CallPath(
    val id: String,
    val methods: List<MethodNode>,
    val edges: List<CallsEdge>,
    val depth: Int = methods.size,
    val startMethod: MethodNode = methods.first(),
    val endMethod: MethodNode = methods.last()
) {
    fun hasNewEndpoint(): Boolean = methods.any { it.isNewInMR() }
    
    fun isRESTfulEndpoint(): Boolean = startMethod.annotations.any { 
        it.contains("@RequestMapping") || 
        it.contains("@GetMapping") || 
        it.contains("@PostMapping") ||
        it.contains("@PutMapping") ||
        it.contains("@DeleteMapping")
    }
    
    fun hasDataModelChanges(): Boolean = methods.any { 
        it.blockType == BlockType.ENTITY || it.blockType == BlockType.DTO 
    }
    
    fun isCoreBusinessEntity(): Boolean = methods.any { 
        it.blockType == BlockType.ENTITY && it.annotations.contains("@Entity")
    }
    
    fun hasDatabaseOperations(): Boolean = methods.any { 
        it.blockType == BlockType.REPOSITORY || it.blockType == BlockType.MAPPER
    }
    
    fun hasTransactionalOperations(): Boolean = methods.any { 
        it.annotations.contains("@Transactional")
    }
    
    fun hasExternalApiCalls(): Boolean = methods.any {
        it.methodName.contains("RestTemplate") || 
        it.methodName.contains("WebClient") ||
        it.methodName.contains("HttpClient")
    }
    
    fun getAverageComplexity(): Double = methods.map { it.cyclomaticComplexity }.average()
    
    fun getAverageMethodLength(): Double = methods.map { it.linesOfCode }.average()
    
    fun getDuplicationRatio(): Double = 0.0 // TODO: Implement duplication detection
    
    fun getNamingQuality(): Double = 1.0 // TODO: Implement naming quality analysis
    
    fun hasLayerViolation(): Boolean = false // TODO: Implement layer violation detection
    
    fun getLayerViolationType(): LayerViolationType? = null // TODO: Implement
    
    fun getSensitiveAnnotations(): List<String> = methods.flatMap { it.annotations }
        .filter { annotation ->
            annotation in listOf(
                "@Transactional", "@Async", "@Scheduled", 
                "@Cacheable", "@PreAuthorize", "@PostAuthorize", "@Lock"
            )
        }
    
    fun hasCircularDependency(): Boolean = false // TODO: Implement circular dependency detection
    
    fun getCircularDependencyLength(): Int = 0 // TODO: Implement
    
    fun hasUtilityMethods(): Boolean = methods.any { it.blockType == BlockType.UTIL }
    
    fun hasCoreBusinessMethods(): Boolean = methods.any { 
        it.blockType == BlockType.SERVICE && it.annotations.isNotEmpty()
    }
    
    fun hasInterfaceImplementations(): Boolean = methods.any { it.isInterface }
    
    fun getChangedMethodCount(): Int = methods.count { it.isModifiedInMR() || it.isNewInMR() }
    
    fun getTotalChangedLines(): Int = 0 // TODO: Implement from git diff
    
    fun getNewDependencies(): List<String> = emptyList() // TODO: Implement
    
    fun hasConfigurationChanges(): Boolean = methods.any { it.blockType == BlockType.CONFIG }
    
    fun getAffectedModules(): Set<String> = methods.map { it.getPackageName() }.toSet()
    
    fun getBusinessTerms(): List<String> = methods.flatMap { method ->
        extractBusinessTerms(method.methodName) + 
        extractBusinessTerms(method.getClassName())
    }
}

/**
 * Layer violation types
 */
enum class LayerViolationType {
    CONTROLLER_TO_DAO,    // Controller directly accessing DAO
    SERVICE_TO_CONTROLLER, // Service depending on Controller
    UTIL_TO_SERVICE,      // Utility depending on Service
    ENTITY_TO_SERVICE,    // Entity depending on Service
    DTO_TO_REPOSITORY     // DTO depending on Repository
}

/**
 * Extension functions for MethodNode analysis
 */
private fun MethodNode.isNewInMR(): Boolean = false // TODO: Implement based on git diff

private fun MethodNode.isModifiedInMR(): Boolean = false // TODO: Implement based on git diff

private fun extractBusinessTerms(text: String): List<String> {
    // Simple business term extraction from camelCase/PascalCase
    return text.split("(?=[A-Z])".toRegex())
        .filter { it.isNotBlank() && it.length > 2 }
        .map { it.lowercase() }
}

/**
 * Change type enumeration
 */
enum class ChangeType {
    ADDED,
    MODIFIED,
    DELETED
}

/**
 * File change information
 */
data class FileChange(
    val filePath: String,
    val changeType: ChangeType,
    val addedMethods: List<MethodNode>,
    val modifiedMethods: List<MethodNode>,
    val deletedMethods: List<MethodNode>,
    val hunks: List<DiffHunk>
)

/**
 * Diff hunk information
 */
data class DiffHunk(
    val startLine: Int,
    val endLine: Int,
    val addedLines: Int,
    val deletedLines: Int,
    val content: String
)