package com.vyibc.autocr.export

import com.vyibc.autocr.model.CallEdge
import com.vyibc.autocr.model.ClassBlock
import com.vyibc.autocr.model.KnowledgeGraph
import com.vyibc.autocr.model.MethodNode
import java.text.SimpleDateFormat
import java.util.*

/**
 * Neo4j Cypher 语句生成器
 */
class Neo4jCypherGenerator {
    
    fun generateCypherScript(graph: KnowledgeGraph): String {
        val script = StringBuilder()
        
        // 添加脚本头部
        addHeader(script, graph)
        
        // 清理现有数据
        addCleanupCommands(script)
        
        // 创建索引
        addIndexes(script)
        
        // 创建约束
        addConstraints(script)
        
        // 创建类节点
        addClassNodes(script, graph.classes)
        
        // 创建方法节点
        addMethodNodes(script, graph.methods)
        
        // 创建包含关系 (Class)-[:CONTAINS]->(Method)
        addContainsRelationships(script, graph)
        
        // 创建调用关系 (Method)-[:CALLS]->(Method)
        addCallRelationships(script, graph.edges)
        
        // 创建继承关系
        addInheritanceRelationships(script, graph.classes)
        
        // 添加统计查询
        addStatisticsQueries(script)
        
        return script.toString()
    }
    
    private fun addHeader(script: StringBuilder, graph: KnowledgeGraph) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        
        script.appendLine("// ===============================")
        script.appendLine("// AutoCR Knowledge Graph - Neo4j Import Script")
        script.appendLine("// Generated at: $timestamp")
        script.appendLine("// Project: ${graph.metadata.projectName}")
        script.appendLine("// Classes: ${graph.classes.size}")
        script.appendLine("// Methods: ${graph.methods.size}")
        script.appendLine("// Call Relations: ${graph.edges.size}")
        script.appendLine("// ===============================")
        script.appendLine()
    }
    
    private fun addCleanupCommands(script: StringBuilder) {
        script.appendLine("// 清理现有数据")
        script.appendLine("MATCH (n) DETACH DELETE n;")
        script.appendLine()
    }
    
    private fun addIndexes(script: StringBuilder) {
        script.appendLine("// 创建索引以提高查询性能")
        script.appendLine("CREATE INDEX class_id_index IF NOT EXISTS FOR (c:Class) ON (c.id);")
        script.appendLine("CREATE INDEX method_id_index IF NOT EXISTS FOR (m:Method) ON (m.id);")
        script.appendLine("CREATE INDEX class_name_index IF NOT EXISTS FOR (c:Class) ON (c.name);")
        script.appendLine("CREATE INDEX method_name_index IF NOT EXISTS FOR (m:Method) ON (m.name);")
        script.appendLine("CREATE INDEX layer_index IF NOT EXISTS FOR (c:Class) ON (c.layer);")
        script.appendLine("CREATE INDEX package_index IF NOT EXISTS FOR (c:Class) ON (c.package);")
        script.appendLine()
    }
    
    private fun addConstraints(script: StringBuilder) {
        script.appendLine("// 创建唯一性约束")
        script.appendLine("CREATE CONSTRAINT class_id_unique IF NOT EXISTS FOR (c:Class) REQUIRE c.id IS UNIQUE;")
        script.appendLine("CREATE CONSTRAINT method_id_unique IF NOT EXISTS FOR (m:Method) REQUIRE m.id IS UNIQUE;")
        script.appendLine()
    }
    
    private fun addClassNodes(script: StringBuilder, classes: List<ClassBlock>) {
        script.appendLine("// 创建类节点")
        
        classes.chunked(50).forEachIndexed { chunkIndex, chunk ->
            script.appendLine("// 批次 ${chunkIndex + 1}")
            script.appendLine("WITH [")
            
            chunk.forEachIndexed { index, classBlock ->
                val comma = if (index < chunk.size - 1) "," else ""
                script.appendLine("  {")
                script.appendLine("    id: ${escapeString(classBlock.id)},")
                script.appendLine("    name: ${escapeString(classBlock.name)},")
                script.appendLine("    qualifiedName: ${escapeString(classBlock.qualifiedName)},")
                script.appendLine("    package: ${escapeString(classBlock.packageName)},")
                script.appendLine("    layer: ${escapeString(classBlock.layer.name)},")
                script.appendLine("    layerDisplay: ${escapeString(classBlock.layer.displayName)},")
                script.appendLine("    emoji: ${escapeString(classBlock.layer.emoji)},")
                script.appendLine("    annotations: ${escapeStringArray(classBlock.annotations)},")
                script.appendLine("    superClass: ${escapeString(classBlock.superClass ?: "")},")
                script.appendLine("    interfaces: ${escapeStringArray(classBlock.interfaces)},")
                script.appendLine("    isAbstract: ${classBlock.isAbstract},")
                script.appendLine("    isInterface: ${classBlock.isInterface},")
                script.appendLine("    methodCount: ${classBlock.methodCount},")
                script.appendLine("    filePath: ${escapeString(classBlock.filePath)}")
                script.appendLine("  }$comma")
            }
            
            script.appendLine("] AS classes")
            script.appendLine("UNWIND classes AS class")
            script.appendLine("CREATE (:Class {")
            script.appendLine("  id: class.id,")
            script.appendLine("  name: class.name,")
            script.appendLine("  qualifiedName: class.qualifiedName,")
            script.appendLine("  package: class.package,")
            script.appendLine("  layer: class.layer,")
            script.appendLine("  layerDisplay: class.layerDisplay,")
            script.appendLine("  emoji: class.emoji,")
            script.appendLine("  annotations: class.annotations,")
            script.appendLine("  superClass: class.superClass,")
            script.appendLine("  interfaces: class.interfaces,")
            script.appendLine("  isAbstract: class.isAbstract,")
            script.appendLine("  isInterface: class.isInterface,")
            script.appendLine("  methodCount: class.methodCount,")
            script.appendLine("  filePath: class.filePath")
            script.appendLine("});")
            script.appendLine()
        }
    }
    
    private fun addMethodNodes(script: StringBuilder, methods: List<MethodNode>) {
        script.appendLine("// 创建方法节点")
        
        methods.chunked(50).forEachIndexed { chunkIndex, chunk ->
            script.appendLine("// 方法批次 ${chunkIndex + 1}")
            script.appendLine("WITH [")
            
            chunk.forEachIndexed { index, method ->
                val comma = if (index < chunk.size - 1) "," else ""
                val paramTypes = method.parameters.map { it.type }
                val paramNames = method.parameters.map { it.name }
                
                script.appendLine("  {")
                script.appendLine("    id: ${escapeString(method.id)},")
                script.appendLine("    name: ${escapeString(method.name)},")
                script.appendLine("    signature: ${escapeString(method.signature)},")
                script.appendLine("    classId: ${escapeString(method.classId)},")
                script.appendLine("    returnType: ${escapeString(method.returnType)},")
                script.appendLine("    parameterTypes: ${escapeStringArray(paramTypes)},")
                script.appendLine("    parameterNames: ${escapeStringArray(paramNames)},")
                script.appendLine("    modifiers: ${escapeStringArray(method.modifiers.toList())},")
                script.appendLine("    lineNumber: ${method.lineNumber},")
                script.appendLine("    isConstructor: ${method.isConstructor},")
                script.appendLine("    isStatic: ${method.isStatic},")
                script.appendLine("    isPrivate: ${method.isPrivate},")
                script.appendLine("    isPublic: ${method.isPublic},")
                script.appendLine("    isAbstract: ${method.isAbstract},")
                script.appendLine("    visibility: ${escapeString(method.getVisibility())}")
                script.appendLine("  }$comma")
            }
            
            script.appendLine("] AS methods")
            script.appendLine("UNWIND methods AS method")
            script.appendLine("CREATE (:Method {")
            script.appendLine("  id: method.id,")
            script.appendLine("  name: method.name,")
            script.appendLine("  signature: method.signature,")
            script.appendLine("  classId: method.classId,")
            script.appendLine("  returnType: method.returnType,")
            script.appendLine("  parameterTypes: method.parameterTypes,")
            script.appendLine("  parameterNames: method.parameterNames,")
            script.appendLine("  modifiers: method.modifiers,")
            script.appendLine("  lineNumber: method.lineNumber,")
            script.appendLine("  isConstructor: method.isConstructor,")
            script.appendLine("  isStatic: method.isStatic,")
            script.appendLine("  isPrivate: method.isPrivate,")
            script.appendLine("  isPublic: method.isPublic,")
            script.appendLine("  isAbstract: method.isAbstract,")
            script.appendLine("  visibility: method.visibility")
            script.appendLine("});")
            script.appendLine()
        }
    }
    
    private fun addContainsRelationships(script: StringBuilder, @Suppress("UNUSED_PARAMETER") graph: KnowledgeGraph) {
        script.appendLine("// 创建包含关系 (Class)-[:CONTAINS]->(Method)")
        script.appendLine("MATCH (c:Class), (m:Method)")
        script.appendLine("WHERE c.id = m.classId")
        script.appendLine("CREATE (c)-[:CONTAINS]->(m);")
        script.appendLine()
    }
    
    private fun addCallRelationships(script: StringBuilder, edges: List<CallEdge>) {
        if (edges.isEmpty()) return
        
        script.appendLine("// 创建调用关系 (Method)-[:CALLS]->(Method)")
        
        edges.chunked(100).forEachIndexed { chunkIndex, chunk ->
            script.appendLine("// 调用关系批次 ${chunkIndex + 1}")
            script.appendLine("WITH [")
            
            chunk.forEachIndexed { index, edge ->
                val comma = if (index < chunk.size - 1) "," else ""
                script.appendLine("  {")
                script.appendLine("    fromId: ${escapeString(edge.fromMethodId)},")
                script.appendLine("    toId: ${escapeString(edge.toMethodId)},")
                script.appendLine("    fromClass: ${escapeString(edge.fromClassId)},")
                script.appendLine("    toClass: ${escapeString(edge.toClassId)},")
                script.appendLine("    callType: ${escapeString(edge.callType.name)},")
                script.appendLine("    callTypeDisplay: ${escapeString(edge.callType.displayName)},")
                script.appendLine("    lineNumber: ${edge.lineNumber},")
                script.appendLine("    confidence: ${edge.confidence}")
                script.appendLine("  }$comma")
            }
            
            script.appendLine("] AS calls")
            script.appendLine("UNWIND calls AS call")
            script.appendLine("MATCH (from:Method {id: call.fromId})")
            script.appendLine("MATCH (to:Method {id: call.toId})")
            script.appendLine("CREATE (from)-[:CALLS {")
            script.appendLine("  fromClass: call.fromClass,")
            script.appendLine("  toClass: call.toClass,")
            script.appendLine("  callType: call.callType,")
            script.appendLine("  callTypeDisplay: call.callTypeDisplay,")
            script.appendLine("  lineNumber: call.lineNumber,")
            script.appendLine("  confidence: call.confidence")
            script.appendLine("}]->(to);")
            script.appendLine()
        }
    }
    
    private fun addInheritanceRelationships(script: StringBuilder, classes: List<ClassBlock>) {
        script.appendLine("// 创建继承关系")
        
        val inheritanceRelations = classes.filter { !it.superClass.isNullOrEmpty() }
        if (inheritanceRelations.isNotEmpty()) {
            script.appendLine("WITH [")
            inheritanceRelations.forEachIndexed { index, classBlock ->
                val comma = if (index < inheritanceRelations.size - 1) "," else ""
                script.appendLine("  {child: ${escapeString(classBlock.id)}, parent: ${escapeString(classBlock.superClass!!)}}$comma")
            }
            script.appendLine("] AS inheritances")
            script.appendLine("UNWIND inheritances AS inheritance")
            script.appendLine("MATCH (child:Class {id: inheritance.child})")
            script.appendLine("MATCH (parent:Class {id: inheritance.parent})")
            script.appendLine("CREATE (child)-[:EXTENDS]->(parent);")
            script.appendLine()
        }
        
        // 接口实现关系
        val implementationRelations = classes.filter { it.interfaces.isNotEmpty() }
        if (implementationRelations.isNotEmpty()) {
            script.appendLine("// 创建接口实现关系")
            implementationRelations.forEach { classBlock ->
                classBlock.interfaces.forEach { interfaceName ->
                    script.appendLine("MATCH (impl:Class {id: ${escapeString(classBlock.id)}})")
                    script.appendLine("MATCH (iface:Class {id: ${escapeString(interfaceName)}})")
                    script.appendLine("CREATE (impl)-[:IMPLEMENTS]->(iface);")
                }
            }
            script.appendLine()
        }
    }
    
    private fun addStatisticsQueries(script: StringBuilder) {
        script.appendLine("// ===============================")
        script.appendLine("// 统计查询（可选执行）")
        script.appendLine("// ===============================")
        script.appendLine()
        
        script.appendLine("// 1. 总体统计")
        script.appendLine("// MATCH (c:Class) RETURN 'Classes' as Type, count(c) as Count")
        script.appendLine("// UNION")
        script.appendLine("// MATCH (m:Method) RETURN 'Methods' as Type, count(m) as Count")
        script.appendLine("// UNION")
        script.appendLine("// MATCH ()-[r:CALLS]->() RETURN 'Calls' as Type, count(r) as Count;")
        script.appendLine()
        
        script.appendLine("// 2. 层级分布")
        script.appendLine("// MATCH (c:Class)")
        script.appendLine("// RETURN c.layer, c.layerDisplay, count(c) as count")
        script.appendLine("// ORDER BY count DESC;")
        script.appendLine()
        
        script.appendLine("// 3. 调用类型分布")
        script.appendLine("// MATCH ()-[r:CALLS]->()")
        script.appendLine("// RETURN r.callType, r.callTypeDisplay, count(r) as count")
        script.appendLine("// ORDER BY count DESC;")
        script.appendLine()
        
        script.appendLine("// 4. 最复杂的类（方法数最多）")
        script.appendLine("// MATCH (c:Class)")
        script.appendLine("// RETURN c.name, c.package, c.methodCount")
        script.appendLine("// ORDER BY c.methodCount DESC")
        script.appendLine("// LIMIT 10;")
        script.appendLine()
        
        script.appendLine("// 5. 最活跃的方法（被调用次数最多）")
        script.appendLine("// MATCH (m:Method)<-[r:CALLS]-()")
        script.appendLine("// RETURN m.name, m.signature, count(r) as callCount")
        script.appendLine("// ORDER BY callCount DESC")
        script.appendLine("// LIMIT 10;")
        script.appendLine()
        
        script.appendLine("// 6. 跨层调用分析")
        script.appendLine("// MATCH (from:Method)-[:CALLS]->(to:Method)")
        script.appendLine("// MATCH (fromClass:Class)-[:CONTAINS]->(from)")
        script.appendLine("// MATCH (toClass:Class)-[:CONTAINS]->(to)")
        script.appendLine("// WHERE fromClass.layer <> toClass.layer")
        script.appendLine("// RETURN fromClass.layer + ' -> ' + toClass.layer as CrossLayerCall, count(*) as count")
        script.appendLine("// ORDER BY count DESC;")
        script.appendLine()
    }
    
    private fun escapeString(value: String?): String {
        if (value == null) return "''"
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
    }
    
    private fun escapeStringArray(list: List<String>): String {
        val escaped = list.map { escapeString(it) }
        return "[" + escaped.joinToString(", ") + "]"
    }
}