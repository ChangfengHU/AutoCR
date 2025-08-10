package com.vyibc.autocr.service

/**
 * Neo4j查询模板服务
 * 根据用户输入生成对应的Cypher查询语句
 */
class Neo4jQueryTemplateService {
    
    /**
     * 生成类基本信息查询
     */
    fun generateClassInfoQuery(className: String): String {
        val simpleClassName = className.substringAfterLast(".")
        return """
            // 查询类基本信息
            MATCH (c:Class)
            WHERE c.name = "$simpleClassName" OR c.qualifiedName = "$className"
            RETURN 
                c.name as 类名,
                c.qualifiedName as 完整类名,
                c.package as 包名,
                c.layer as 架构层级,
                c.layerDisplay as 层级显示,
                c.methodCount as 方法数量,
                c.annotations as 注解列表,
                c.isInterface as 是否接口,
                c.interfaces as 实现接口,
                c.superClass as 父类,
                c.filePath as 文件路径
        """.trimIndent()
    }
    
    /**
     * 生成类的所有方法查询
     */
    fun generateClassMethodsQuery(className: String): String {
        val simpleClassName = className.substringAfterLast(".")
        return """
            // 查询类的所有方法
            MATCH (c:Class)-[:CONTAINS]->(m:Method)
            WHERE c.name = "$simpleClassName" OR c.qualifiedName = "$className"
            RETURN 
                c.name as 类名,
                m.name as 方法名,
                m.signature as 方法签名,
                m.returnType as 返回类型,
                m.visibility as 可见性,
                m.isStatic as 是否静态,
                m.isAbstract as 是否抽象,
                m.lineNumber as 行号,
                m.id as 方法完整ID
            ORDER BY m.name
        """.trimIndent()
    }
    
    /**
     * 生成方法调用关系查询 - 这个方法调用了谁
     */
    fun generateMethodCallsQuery(methodId: String): String {
        return """
            // 查询方法调用了哪些其他方法
            MATCH (sourceMethod:Method {id: "$methodId"})-[:CALLS]->(targetMethod:Method)
            MATCH (sourceClass:Class)-[:CONTAINS]->(sourceMethod)
            MATCH (targetClass:Class)-[:CONTAINS]->(targetMethod)
            RETURN 
                sourceClass.name as 当前类,
                sourceMethod.name as 当前方法,
                targetClass.name as 被调用类,
                targetClass.layer as 被调用类层级,
                targetMethod.name as 被调用方法,
                targetMethod.signature as 被调用方法签名,
                count(*) as 调用次数
            ORDER BY 调用次数 DESC, 被调用类, 被调用方法
        """.trimIndent()
    }
    
    /**
     * 生成方法调用者查询 - 谁调用了这个方法
     */
    fun generateMethodCallersQuery(methodId: String): String {
        return """
            // 查询谁调用了这个方法
            MATCH (callerMethod:Method)-[:CALLS]->(targetMethod:Method {id: "$methodId"})
            MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod)
            MATCH (targetClass:Class)-[:CONTAINS]->(targetMethod)
            RETURN 
                callerClass.name as 调用者类,
                callerClass.layer as 调用者层级,
                callerMethod.name as 调用者方法,
                callerMethod.signature as 调用者方法签名,
                targetClass.name as 目标类,
                targetMethod.name as 目标方法,
                count(*) as 调用次数
            ORDER BY 调用次数 DESC, 调用者层级, 调用者类
        """.trimIndent()
    }
    
    /**
     * 生成两方法间调用链路查询
     */
    fun generateCallPathQuery(sourceMethodId: String, targetMethodId: String): String {
        return """
            // 查询两方法间的调用链路（最多5层深度）
            MATCH path = (sourceMethod:Method {id: "$sourceMethodId"})-[:CALLS*1..5]->(targetMethod:Method {id: "$targetMethodId"})
            WITH path, length(path) as pathLength
            ORDER BY pathLength
            LIMIT 10
            
            // 提取路径中的详细信息
            UNWIND nodes(path) as methodNode
            MATCH (classNode:Class)-[:CONTAINS]->(methodNode)
            WITH path, pathLength, collect({
                className: classNode.name,
                methodName: methodNode.name,
                layer: classNode.layer
            }) as pathDetails
            
            RETURN 
                pathLength as 调用链长度,
                pathDetails as 调用路径详情,
                [n in pathDetails | n.className + "." + n.methodName] as 简化路径
            ORDER BY 调用链长度
            
            // 如果没有直接路径，查找可能的间接调用
            UNION
            
            MATCH (sourceMethod:Method {id: "$sourceMethodId"})-[:CALLS]->(intermediate:Method)
            MATCH (intermediate)-[:CALLS]->(targetMethod:Method {id: "$targetMethodId"})
            MATCH (sourceClass:Class)-[:CONTAINS]->(sourceMethod)
            MATCH (intermediateClass:Class)-[:CONTAINS]->(intermediate)
            MATCH (targetClass:Class)-[:CONTAINS]->(targetMethod)
            
            RETURN 
                2 as 调用链长度,
                [
                    {className: sourceClass.name, methodName: sourceMethod.name, layer: sourceClass.layer},
                    {className: intermediateClass.name, methodName: intermediate.name, layer: intermediateClass.layer},
                    {className: targetClass.name, methodName: targetMethod.name, layer: targetClass.layer}
                ] as 调用路径详情,
                [sourceClass.name + "." + sourceMethod.name, 
                 intermediateClass.name + "." + intermediate.name, 
                 targetClass.name + "." + targetMethod.name] as 简化路径
            
            LIMIT 5
        """.trimIndent()
    }
    
    /**
     * 生成接口实现关系查询
     */
    fun generateInterfaceImplementationsQuery(interfaceName: String): String {
        val simpleInterfaceName = interfaceName.substringAfterLast(".")
        return """
            // 查询接口的所有实现类
            MATCH (interface:Class)-[:IMPLEMENTS]-(impl:Class)
            WHERE interface.name = "$simpleInterfaceName" OR interface.qualifiedName = "$interfaceName"
            
            // 接口信息
            WITH interface, impl
            
            // 查找接口方法和对应的实现方法
            OPTIONAL MATCH (interface)-[:CONTAINS]->(interfaceMethod:Method)
            OPTIONAL MATCH (impl)-[:CONTAINS]->(implMethod:Method)
            WHERE interfaceMethod.name = implMethod.name
            
            RETURN 
                interface.name as 接口名,
                interface.qualifiedName as 接口全名,
                impl.name as 实现类名,
                impl.qualifiedName as 实现类全名,
                impl.layer as 实现类层级,
                count(DISTINCT interfaceMethod) as 接口方法数量,
                count(DISTINCT implMethod) as 实现方法数量,
                collect(DISTINCT interfaceMethod.name) as 方法列表
            ORDER BY 实现类名
            
            // 可视化查询（用于Neo4j Browser图形显示）
            UNION
            
            MATCH (interface:Class)
            WHERE interface.name = "$simpleInterfaceName" OR interface.qualifiedName = "$interfaceName"
            MATCH (impl:Class)-[:IMPLEMENTS]->(interface)
            OPTIONAL MATCH (interface)-[:CONTAINS]->(interfaceMethod:Method)
            OPTIONAL MATCH (impl)-[:CONTAINS]->(implMethod:Method)
            WHERE interfaceMethod.name = implMethod.name
            
            // 查看调用关系
            OPTIONAL MATCH (callerMethod:Method)-[:CALLS]->(interfaceMethod)
            OPTIONAL MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod)
            
            RETURN interface, impl, interfaceMethod, implMethod, callerClass, callerMethod
            LIMIT 50
        """.trimIndent()
    }
    
    /**
     * 生成通用架构分析查询
     */
    fun generateArchitectureAnalysisQuery(className: String): String {
        val simpleClassName = className.substringAfterLast(".")
        return """
            // 架构分析查询 - 分析类在整个系统中的位置
            MATCH (center:Class)
            WHERE center.name = "$simpleClassName" OR center.qualifiedName = "$className"
            
            // 上游调用者（谁在调用这个类的方法）
            OPTIONAL MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod:Method)
                     -[:CALLS]->(centerMethod:Method)<-[:CONTAINS]-(center)
            
            // 下游被调用者（这个类调用了谁）
            OPTIONAL MATCH (center)-[:CONTAINS]->(centerMethod2:Method)
                     -[:CALLS]->(targetMethod:Method)<-[:CONTAINS]-(targetClass:Class)
            
            // 接口实现关系
            OPTIONAL MATCH (center)-[:IMPLEMENTS]->(interface:Class)
            OPTIONAL MATCH (subClass:Class)-[:EXTENDS]->(center)
            
            RETURN 
                center.name as 当前类,
                center.layer as 当前层级,
                
                // 上游统计
                count(DISTINCT callerClass) as 上游调用者类数量,
                collect(DISTINCT callerClass.layer)[..5] as 上游调用者层级,
                collect(DISTINCT callerClass.name)[..5] as 上游调用者示例,
                
                // 下游统计
                count(DISTINCT targetClass) as 下游被调用类数量, 
                collect(DISTINCT targetClass.layer)[..5] as 下游被调用层级,
                collect(DISTINCT targetClass.name)[..5] as 下游被调用示例,
                
                // 继承关系
                collect(DISTINCT interface.name) as 实现接口,
                collect(DISTINCT subClass.name) as 子类
        """.trimIndent()
    }
    
    /**
     * 生成热点方法查询（被调用最多的方法）
     */
    fun generateHotMethodsQuery(): String {
        return """
            // 查询系统中被调用最多的热点方法
            MATCH (targetMethod:Method)<-[:CALLS]-(callerMethod:Method)
            MATCH (targetClass:Class)-[:CONTAINS]->(targetMethod)
            MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod)
            
            WITH targetMethod, targetClass, count(*) as callCount
            WHERE callCount > 1
            
            RETURN 
                targetClass.name as 类名,
                targetClass.layer as 层级,
                targetMethod.name as 方法名,
                targetMethod.signature as 方法签名,
                callCount as 被调用次数,
                targetMethod.id as 方法ID
            ORDER BY 被调用次数 DESC
            LIMIT 20
        """.trimIndent()
    }
}