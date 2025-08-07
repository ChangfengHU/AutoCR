package com.vyibc.autocr.model

import java.time.Instant

/**
 * Base interface for all graph nodes
 */
sealed interface Node {
    val id: String
}

/**
 * Represents a method node in the code graph
 */
data class MethodNode(
    override val id: String, // Format: {packageName}.{className}#{methodName}({paramTypes})
    val methodName: String,
    val signature: String, // Complete method signature
    val returnType: String,
    val paramTypes: List<String>,
    val blockType: BlockType,
    val isInterface: Boolean,
    val annotations: List<String>,
    val filePath: String, // Absolute path
    val lineNumber: Int,
    val startLineNumber: Int,
    val endLineNumber: Int,
    
    // V5.1 performance-related fields
    val cyclomaticComplexity: Int = 0,
    val linesOfCode: Int = 0,
    val inDegree: Int = 0, // Number of methods calling this method
    val outDegree: Int = 0, // Number of methods this method calls
    val riskScore: Double = 0.0, // Pre-calculated risk score
    val hasTests: Boolean = false,
    val lastModified: Instant = Instant.now()
) : Node

/**
 * Represents a class node in the code graph
 */
data class ClassNode(
    override val id: String, // Format: {packageName}.{className}
    val className: String,
    val packageName: String,
    val blockType: BlockType,
    val isInterface: Boolean,
    val isAbstract: Boolean,
    val filePath: String,
    val implementedInterfaces: List<String>,
    val superClass: String?,
    val annotations: List<String>,
    
    // V5.1 additional fields
    val methodCount: Int = 0,
    val fieldCount: Int = 0,
    val cohesion: Double = 0.0, // Internal cohesion metric
    val coupling: Double = 0.0, // External coupling metric
    val designPatterns: List<String> = emptyList() // Detected design patterns
) : Node

/**
 * Block type classification for architectural layer identification
 */
enum class BlockType {
    CONTROLLER,
    SERVICE,
    MAPPER,
    REPOSITORY,
    UTIL,
    CONFIG,
    ENTITY,
    DTO,
    UNKNOWN
}

/**
 * Extension functions for Node operations
 */
fun MethodNode.getFullyQualifiedName(): String {
    return id
}

fun ClassNode.getFullyQualifiedName(): String {
    return id
}

fun MethodNode.getClassName(): String {
    val parts = id.split("#")
    return if (parts.size >= 2) parts[0] else ""
}

fun MethodNode.getPackageName(): String {
    val className = getClassName()
    val lastDot = className.lastIndexOf('.')
    return if (lastDot > 0) className.substring(0, lastDot) else ""
}