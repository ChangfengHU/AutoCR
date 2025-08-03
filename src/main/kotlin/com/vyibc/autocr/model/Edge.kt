package com.vyibc.autocr.model

/**
 * Base interface for all graph edges (relationships)
 */
sealed interface Edge {
    val id: String
    val sourceId: String
    val targetId: String
    val edgeType: EdgeType
}

/**
 * Edge types enumeration
 */
enum class EdgeType {
    CALLS,
    IMPLEMENTS,
    INHERITS,
    DATA_FLOW,
    CONTAINS
}

/**
 * Represents a method call relationship
 */
data class CallsEdge(
    override val id: String,
    override val sourceId: String, // Caller method ID
    override val targetId: String, // Callee method ID
    override val edgeType: EdgeType = EdgeType.CALLS,
    val callType: String = "direct", // Type of call
    val lineNumber: Int = 0, // Line number where the call occurs
    val frequency: Int = 1, // Call frequency (estimated from static analysis)
    val isConditional: Boolean = false, // Whether the call is conditional
    val context: String = "normal", // Call context
    
    // V5.1 additional fields
    val riskWeight: Double = 0.0, // Risk weight of this call
    val intentWeight: Double = 0.0, // Intent weight of this call
    val isNewInMR: Boolean = false, // Whether this is a new call in the MR
    val isModifiedInMR: Boolean = false // Whether this call was modified in the MR
) : Edge

/**
 * Represents an interface implementation relationship
 */
data class ImplementsEdge(
    override val id: String,
    override val sourceId: String, // Implementing class ID
    override val targetId: String, // Interface ID
    override val edgeType: EdgeType = EdgeType.IMPLEMENTS,
    val isOverride: Boolean = false,
    
    // V5.1 additional fields
    val implementationQuality: Double = 0.0, // Implementation quality score
    val followsContract: Boolean = true // Whether it follows interface contract
) : Edge

/**
 * Represents data flow between methods (V5.1 new)
 */
data class DataFlowEdge(
    override val id: String,
    override val sourceId: String, // Source method ID
    override val targetId: String, // Target method ID
    override val edgeType: EdgeType = EdgeType.DATA_FLOW,
    val dataType: String, // Type of data being passed
    val flowType: String, // Flow type
    val isSensitive: Boolean = false // Whether this is sensitive data
) : Edge

/**
 * Represents inheritance relationship between classes
 */
data class InheritsEdge(
    override val id: String,
    override val sourceId: String, // Child class ID
    override val targetId: String, // Parent class ID
    override val edgeType: EdgeType = EdgeType.INHERITS,
    val inheritanceType: String = "extends"
) : Edge

/**
 * Represents containment relationship (class contains method)
 */
data class ContainsEdge(
    override val id: String,
    override val sourceId: String, // Container (class) ID
    override val targetId: String, // Contained (method) ID
    override val edgeType: EdgeType = EdgeType.CONTAINS
) : Edge