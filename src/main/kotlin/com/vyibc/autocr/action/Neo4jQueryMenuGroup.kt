package com.vyibc.autocr.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase
import com.vyibc.autocr.util.PsiElementAnalyzer
import org.slf4j.LoggerFactory

/**
 * Neo4j查询菜单组 - 分为Table和Graph两个模块
 */
class Neo4jQueryMenuGroup : ActionGroup() {
    
    private val logger = LoggerFactory.getLogger(Neo4jQueryMenuGroup::class.java)
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) return emptyArray()
        
        val element = getCurrentPsiElement(e) ?: return emptyArray()
        val elementInfo = PsiElementAnalyzer.analyzeElement(element)
        
        logger.debug("为元素生成菜单: ${elementInfo.type} - ${elementInfo.className} - ${elementInfo.methodName}")
        
        return when (elementInfo.type) {
            PsiElementAnalyzer.ElementType.CLASS -> arrayOf(
                createTableQueryGroup(elementInfo),
                createGraphQueryGroup(elementInfo)
            )
            PsiElementAnalyzer.ElementType.METHOD -> arrayOf(
                createMethodTableQueryGroup(elementInfo),
                createMethodGraphQueryGroup(elementInfo)
            )
            else -> emptyArray()
        }
    }
    
    private fun createTableQueryGroup(elementInfo: PsiElementAnalyzer.ElementInfo): ActionGroup {
        return object : ActionGroup("📊 Table查询", "获取统计分析数据", null) {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                return createClassTableActions(elementInfo)
            }
        }
    }
    
    private fun createGraphQueryGroup(elementInfo: PsiElementAnalyzer.ElementInfo): ActionGroup {
        return object : ActionGroup("🌐 Graph查询", "获取图形关系数据", null) {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                return createClassGraphActions(elementInfo)
            }
        }
    }
    
    private fun createMethodTableQueryGroup(elementInfo: PsiElementAnalyzer.ElementInfo): ActionGroup {
        return object : ActionGroup("📊 Table查询", "获取方法统计分析数据", null) {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                return createMethodTableActions(elementInfo)
            }
        }
    }
    
    private fun createMethodGraphQueryGroup(elementInfo: PsiElementAnalyzer.ElementInfo): ActionGroup {
        return object : ActionGroup("🌐 Graph查询", "获取方法图形关系数据", null) {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                return createMethodGraphActions(elementInfo)
            }
        }
    }
    
    // ============== Table查询Actions ==============
    
    private fun createClassTableActions(elementInfo: PsiElementAnalyzer.ElementInfo): Array<AnAction> {
        val className = elementInfo.qualifiedClassName ?: return emptyArray()
        
        return arrayOf(
            Neo4jQueryAction("📋 类基本信息", "查看${elementInfo.className}的详细信息") {
                generateClassInfoTableQuery(className)
            },
            Neo4jQueryAction("🔍 方法统计", "统计${elementInfo.className}的方法信息") {
                generateClassMethodsTableQuery(className)
            },
            Neo4jQueryAction("📞 调用统计", "统计${elementInfo.className}的调用关系") {
                generateClassCallStatsQuery(className)
            },
            Neo4jQueryAction("🏗️ 架构分析", "分析${elementInfo.className}在架构中的位置") {
                generateArchitectureStatsQuery(className)
            }
        )
    }
    
    private fun createMethodTableActions(elementInfo: PsiElementAnalyzer.ElementInfo): Array<AnAction> {
        val methodId = elementInfo.fullMethodId ?: return emptyArray()
        val methodName = elementInfo.methodName ?: "未知方法"
        
        return arrayOf(
            Neo4jQueryAction("📋 方法信息", "查看${methodName}的详细信息") {
                generateMethodInfoTableQuery(methodId)
            },
            Neo4jQueryAction("📊 调用统计", "统计${methodName}的调用关系") {
                generateMethodCallStatsQuery(methodId)
            }
        )
    }
    
    // ============== Graph查询Actions ==============
    
    private fun createClassGraphActions(elementInfo: PsiElementAnalyzer.ElementInfo): Array<AnAction> {
        val className = elementInfo.qualifiedClassName ?: return emptyArray()
        val simpleClassName = elementInfo.className ?: "UnknownClass"
        
        return arrayOf(
            Neo4jQueryAction("🔄 调用关系图", "显示${simpleClassName}的完整调用关系") {
                generateClassCallGraphQuery(className, simpleClassName)
            },
            Neo4jQueryAction("🔗 接口实现关系图", "显示${simpleClassName}的接口实现关系") {
                generateInterfaceImplementationGraphQuery(className, simpleClassName)
            },
            Neo4jQueryAction("🌳 继承关系图", "显示${simpleClassName}的继承层次") {
                generateInheritanceGraphQuery(className, simpleClassName)
            },
            Neo4jQueryAction("🕸️ 完整依赖图", "显示${simpleClassName}的完整依赖网络") {
                generateFullDependencyGraphQuery(className, simpleClassName)
            },
            Neo4jQueryAction("🏛️ 架构层级图", "显示${simpleClassName}的架构层级关系") {
                generateArchitectureLayerGraphQuery(className, simpleClassName)
            }
        )
    }
    
    private fun createMethodGraphActions(elementInfo: PsiElementAnalyzer.ElementInfo): Array<AnAction> {
        val methodId = elementInfo.fullMethodId ?: return emptyArray()
        val methodName = elementInfo.methodName ?: "未知方法"
        
        return arrayOf(
            Neo4jQueryAction("📤 方法调用图", "显示${methodName}的调用关系") {
                generateMethodCallGraphQuery(methodId, methodName)
            },
            Neo4jQueryAction("📞 被调用关系图", "显示谁调用了${methodName}") {
                generateMethodCallerGraphQuery(methodId, methodName)
            },
            Neo4jQueryAction("🔄 双向调用图", "显示${methodName}的双向调用关系") {
                generateBidirectionalCallGraphQuery(methodId, methodName)
            },
            Neo4jQueryAction("🌐 方法上下文图", "显示${methodName}的完整上下文") {
                generateMethodContextGraphQuery(methodId, methodName)
            }
        )
    }
    
    private fun getCurrentPsiElement(e: AnActionEvent): PsiElement? {
        val project = e.project ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        
        return when {
            editor != null && psiFile != null -> {
                val offset = editor.caretModel.offset
                psiFile.findElementAt(offset)
            }
            psiFile != null -> psiFile
            else -> null
        }
    }
    
    override fun update(e: AnActionEvent) {
        val element = getCurrentPsiElement(e)
        val canGenerate = element?.let { PsiElementAnalyzer.canGenerateQueries(it) } ?: false
        
        e.presentation.isVisible = canGenerate
        e.presentation.text = "🔍 Neo4j查询"
    }
    
    // ============== Table查询生成方法 ==============
    
    private fun generateClassInfoTableQuery(className: String): String {
        val simpleClassName = className.substringAfterLast(".")
        return """
            // 📋 类基本信息 - Table查询
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
                c.superClass as 父类
        """.trimIndent()
    }
    
    private fun generateClassMethodsTableQuery(className: String): String {
        val simpleClassName = className.substringAfterLast(".")
        return """
            // 🔍 方法统计 - Table查询
            MATCH (c:Class)-[:CONTAINS]->(m:Method)
            WHERE c.name = "$simpleClassName" OR c.qualifiedName = "$className"
            RETURN 
                m.name as 方法名,
                m.signature as 方法签名,
                m.returnType as 返回类型,
                m.visibility as 可见性,
                m.isStatic as 是否静态,
                m.isAbstract as 是否抽象,
                m.lineNumber as 行号,
                size(m.parameterTypes) as 参数个数
            ORDER BY m.name
        """.trimIndent()
    }
    
    private fun generateClassCallStatsQuery(className: String): String {
        val simpleClassName = className.substringAfterLast(".")
        return """
            // 📞 调用统计 - Table查询
            MATCH (c:Class)
            WHERE c.name = "$simpleClassName" OR c.qualifiedName = "$className"
            
            // 统计被调用情况
            OPTIONAL MATCH (c)-[:CONTAINS]->(targetMethod:Method)<-[:CALLS]-(callerMethod:Method)<-[:CONTAINS]-(callerClass:Class)
            
            // 统计调用其他类的情况
            OPTIONAL MATCH (c)-[:CONTAINS]->(sourceMethod:Method)-[:CALLS]->(targetMethod2:Method)<-[:CONTAINS]-(targetClass:Class)
            
            RETURN 
                c.name as 类名,
                count(DISTINCT callerClass) as 被多少个类调用,
                count(DISTINCT callerMethod) as 被多少个方法调用,
                count(DISTINCT targetClass) as 调用了多少个类,
                count(DISTINCT targetMethod2) as 调用了多少个方法,
                collect(DISTINCT callerClass.layer)[..3] as 调用者层级,
                collect(DISTINCT targetClass.layer)[..3] as 被调用者层级
        """.trimIndent()
    }
    
    private fun generateArchitectureStatsQuery(className: String): String {
        val simpleClassName = className.substringAfterLast(".")
        return """
            // 🏗️ 架构分析 - Table查询
            MATCH (center:Class)
            WHERE center.name = "$simpleClassName" OR center.qualifiedName = "$className"
            
            // 上游调用者统计
            OPTIONAL MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod:Method)-[:CALLS]->(centerMethod:Method)<-[:CONTAINS]-(center)
            
            // 下游被调用者统计
            OPTIONAL MATCH (center)-[:CONTAINS]->(centerMethod2:Method)-[:CALLS]->(targetMethod:Method)<-[:CONTAINS]-(targetClass:Class)
            
            WITH center,
                 callerClass.layer as 上游层级, count(DISTINCT callerClass) as 上游类数量,
                 targetClass.layer as 下游层级, count(DISTINCT targetClass) as 下游类数量
            
            RETURN 
                center.name as 当前类,
                center.layer as 当前层级,
                上游层级,
                上游类数量,
                下游层级,
                下游类数量
            ORDER BY 上游类数量 DESC, 下游类数量 DESC
        """.trimIndent()
    }
    
    private fun generateMethodInfoTableQuery(methodId: String): String {
        return """
            // 📋 方法信息 - Table查询
            MATCH (m:Method {id: "$methodId"})
            MATCH (c:Class)-[:CONTAINS]->(m)
            RETURN 
                c.name as 所属类,
                m.name as 方法名,
                m.signature as 方法签名,
                m.returnType as 返回类型,
                m.parameterTypes as 参数类型,
                m.parameterNames as 参数名称,
                m.modifiers as 修饰符,
                m.visibility as 可见性,
                m.lineNumber as 行号,
                m.isStatic as 是否静态,
                m.isConstructor as 是否构造函数,
                m.isAbstract as 是否抽象
        """.trimIndent()
    }
    
    private fun generateMethodCallStatsQuery(methodId: String): String {
        return """
            // 📊 调用统计 - Table查询
            MATCH (m:Method {id: "$methodId"})
            
            // 统计调用了多少方法
            OPTIONAL MATCH (m)-[:CALLS]->(targetMethod:Method)<-[:CONTAINS]-(targetClass:Class)
            
            // 统计被多少方法调用
            OPTIONAL MATCH (callerMethod:Method)-[:CALLS]->(m)
            OPTIONAL MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod)
            
            RETURN 
                m.name as 方法名,
                count(DISTINCT targetMethod) as 调用方法数量,
                count(DISTINCT targetClass) as 调用类数量,
                count(DISTINCT callerMethod) as 被调用次数,
                count(DISTINCT callerClass) as 调用者数量,
                collect(DISTINCT targetClass.layer)[..3] as 调用层级分布,
                collect(DISTINCT callerClass.layer)[..3] as 调用者层级分布
        """.trimIndent()
    }
    
    // ============== Graph查询生成方法 ==============
    
    private fun generateClassCallGraphQuery(className: String, simpleClassName: String): String {
        return """
            // 🔄 调用关系图 - Graph查询
            MATCH (center:Class {name: "$simpleClassName"})
            
            // 调用者
            OPTIONAL MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod:Method)-[:CALLS]->(centerMethod:Method)<-[:CONTAINS]-(center)
            
            // 被调用者  
            OPTIONAL MATCH (center)-[:CONTAINS]->(centerMethod2:Method)-[:CALLS]->(targetMethod:Method)<-[:CONTAINS]-(targetClass:Class)
            
            RETURN center, callerClass, callerMethod, centerMethod, centerMethod2, targetMethod, targetClass
            LIMIT 50
        """.trimIndent()
    }
    
    private fun generateInterfaceImplementationGraphQuery(className: String, simpleClassName: String): String {
        return """
            // 🔗 接口实现关系图 - Graph查询
            MATCH (center:Class)
            WHERE center.name = "$simpleClassName" OR center.qualifiedName = "$className"
            
            // 如果是接口，查找实现类
            OPTIONAL MATCH (impl:Class)-[:IMPLEMENTS]->(center)
            OPTIONAL MATCH (impl)-[:CONTAINS]->(implMethod:Method)
            
            // 如果是实现类，查找接口
            OPTIONAL MATCH (center)-[:IMPLEMENTS]->(interface:Class)
            OPTIONAL MATCH (interface)-[:CONTAINS]->(interfaceMethod:Method)
            OPTIONAL MATCH (center)-[:CONTAINS]->(centerMethod:Method)
            WHERE interfaceMethod.name = centerMethod.name
            
            // 调用者
            OPTIONAL MATCH (callerMethod:Method)-[:CALLS]->(interfaceMethod)
            OPTIONAL MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod)
            
            // 实现方法的调用
            OPTIONAL MATCH (centerMethod)-[:CALLS]->(targetMethod:Method)
            OPTIONAL MATCH (targetClass:Class)-[:CONTAINS]->(targetMethod)
            
            RETURN center, interface, interfaceMethod, impl, implMethod, centerMethod, 
                   callerClass, callerMethod, targetClass, targetMethod
            LIMIT 30
        """.trimIndent()
    }
    
    private fun generateInheritanceGraphQuery(className: String, simpleClassName: String): String {
        return """
            // 🌳 继承关系图 - Graph查询
            MATCH (center:Class {name: "$simpleClassName"})
            
            // 父类链
            OPTIONAL MATCH path1 = (center)-[:EXTENDS*1..3]->(parentClass:Class)
            
            // 子类链
            OPTIONAL MATCH path2 = (childClass:Class)-[:EXTENDS*1..3]->(center)
            
            // 接口关系
            OPTIONAL MATCH (center)-[:IMPLEMENTS]->(interface:Class)
            OPTIONAL MATCH (subImpl:Class)-[:IMPLEMENTS]->(interface)
            
            RETURN center, parentClass, childClass, interface, subImpl, path1, path2
            LIMIT 40
        """.trimIndent()
    }
    
    private fun generateFullDependencyGraphQuery(className: String, simpleClassName: String): String {
        return """
            // 🕸️ 完整依赖图 - Graph查询
            MATCH (center:Class {name: "$simpleClassName"})
            
            // 第一层：直接调用关系
            OPTIONAL MATCH (center)-[:CONTAINS]->(centerMethod:Method)-[:CALLS]->(targetMethod1:Method)<-[:CONTAINS]-(target1:Class)
            
            // 第二层：间接调用关系
            OPTIONAL MATCH (target1)-[:CONTAINS]->(target1Method:Method)-[:CALLS]->(targetMethod2:Method)<-[:CONTAINS]-(target2:Class)
            WHERE target2 <> center
            
            // 调用者第一层
            OPTIONAL MATCH (caller1:Class)-[:CONTAINS]->(caller1Method:Method)-[:CALLS]->(centerMethod2:Method)<-[:CONTAINS]-(center)
            
            // 调用者第二层
            OPTIONAL MATCH (caller2:Class)-[:CONTAINS]->(caller2Method:Method)-[:CALLS]->(caller1Method2:Method)<-[:CONTAINS]-(caller1)
            WHERE caller2 <> center
            
            // 接口和继承关系
            OPTIONAL MATCH (center)-[:IMPLEMENTS|:EXTENDS]->(related:Class)
            OPTIONAL MATCH (relatedImpl:Class)-[:IMPLEMENTS|:EXTENDS]->(center)
            
            RETURN center, centerMethod, centerMethod2,
                   target1, targetMethod1, target1Method, target2, targetMethod2,
                   caller1, caller1Method, caller1Method2, caller2, caller2Method,
                   related, relatedImpl
            LIMIT 60
        """.trimIndent()
    }
    
    private fun generateArchitectureLayerGraphQuery(className: String, simpleClassName: String): String {
        return """
            // 🏛️ 架构层级图 - Graph查询
            MATCH (center:Class {name: "$simpleClassName"})
            
            // 同层级的类
            OPTIONAL MATCH (sameLayer:Class)
            WHERE sameLayer.layer = center.layer AND sameLayer <> center
            OPTIONAL MATCH (center)-[:CONTAINS]->(centerMethod:Method)-[:CALLS]->(sameLayerMethod:Method)<-[:CONTAINS]-(sameLayer)
            
            // 上层调用者（Controller -> Service, Service -> Repository等）
            OPTIONAL MATCH (upperLayer:Class)-[:CONTAINS]->(upperMethod:Method)-[:CALLS]->(centerMethod2:Method)<-[:CONTAINS]-(center)
            WHERE upperLayer.layer <> center.layer
            
            // 下层被调用者
            OPTIONAL MATCH (center)-[:CONTAINS]->(centerMethod3:Method)-[:CALLS]->(lowerMethod:Method)<-[:CONTAINS]-(lowerLayer:Class)
            WHERE lowerLayer.layer <> center.layer
            
            RETURN center, centerMethod, centerMethod2, centerMethod3,
                   sameLayer, sameLayerMethod,
                   upperLayer, upperMethod, 
                   lowerLayer, lowerMethod
            LIMIT 45
        """.trimIndent()
    }
    
    private fun generateMethodCallGraphQuery(methodId: String, methodName: String): String {
        return """
            // 📤 方法调用图 - Graph查询
            MATCH (sourceMethod:Method {id: "$methodId"})-[:CALLS]->(targetMethod:Method)
            MATCH (sourceClass:Class)-[:CONTAINS]->(sourceMethod)
            MATCH (targetClass:Class)-[:CONTAINS]->(targetMethod)
            
            // 二级调用
            OPTIONAL MATCH (targetMethod)-[:CALLS]->(secondLevelMethod:Method)
            OPTIONAL MATCH (secondLevelClass:Class)-[:CONTAINS]->(secondLevelMethod)
            
            RETURN sourceMethod, sourceClass, targetMethod, targetClass, 
                   secondLevelMethod, secondLevelClass
            LIMIT 40
        """.trimIndent()
    }
    
    private fun generateMethodCallerGraphQuery(methodId: String, methodName: String): String {
        return """
            // 📞 被调用关系图 - Graph查询
            MATCH (callerMethod:Method)-[:CALLS]->(targetMethod:Method {id: "$methodId"})
            MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod)
            MATCH (targetClass:Class)-[:CONTAINS]->(targetMethod)
            
            // 二级调用者
            OPTIONAL MATCH (secondLevelCaller:Method)-[:CALLS]->(callerMethod)
            OPTIONAL MATCH (secondLevelClass:Class)-[:CONTAINS]->(secondLevelCaller)
            
            RETURN targetMethod, targetClass, callerMethod, callerClass,
                   secondLevelCaller, secondLevelClass
            LIMIT 40
        """.trimIndent()
    }
    
    private fun generateBidirectionalCallGraphQuery(methodId: String, methodName: String): String {
        return """
            // 🔄 双向调用图 - Graph查询
            MATCH (centerMethod:Method {id: "$methodId"})
            MATCH (centerClass:Class)-[:CONTAINS]->(centerMethod)
            
            // 调用的方法
            OPTIONAL MATCH (centerMethod)-[:CALLS]->(targetMethod:Method)
            OPTIONAL MATCH (targetClass:Class)-[:CONTAINS]->(targetMethod)
            
            // 调用者
            OPTIONAL MATCH (callerMethod:Method)-[:CALLS]->(centerMethod)
            OPTIONAL MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod)
            
            // 调用链扩展
            OPTIONAL MATCH (targetMethod)-[:CALLS]->(thirdMethod:Method)
            OPTIONAL MATCH (thirdClass:Class)-[:CONTAINS]->(thirdMethod)
            
            OPTIONAL MATCH (zeroMethod:Method)-[:CALLS]->(callerMethod)
            OPTIONAL MATCH (zeroClass:Class)-[:CONTAINS]->(zeroMethod)
            
            RETURN centerMethod, centerClass,
                   targetMethod, targetClass, thirdMethod, thirdClass,
                   callerMethod, callerClass, zeroMethod, zeroClass
            LIMIT 50
        """.trimIndent()
    }
    
    private fun generateMethodContextGraphQuery(methodId: String, methodName: String): String {
        return """
            // 🌐 方法上下文图 - Graph查询
            MATCH (centerMethod:Method {id: "$methodId"})
            MATCH (centerClass:Class)-[:CONTAINS]->(centerMethod)
            
            // 同类的其他方法
            OPTIONAL MATCH (centerClass)-[:CONTAINS]->(siblingMethod:Method)
            WHERE siblingMethod <> centerMethod
            
            // 方法之间的调用关系
            OPTIONAL MATCH (centerMethod)-[:CALLS]->(siblingMethod)
            OPTIONAL MATCH (siblingMethod)-[:CALLS]->(centerMethod)
            
            // 外部调用
            OPTIONAL MATCH (centerMethod)-[:CALLS]->(externalMethod:Method)
            OPTIONAL MATCH (externalClass:Class)-[:CONTAINS]->(externalMethod)
            WHERE externalClass <> centerClass
            
            // 外部调用者
            OPTIONAL MATCH (externalCaller:Method)-[:CALLS]->(centerMethod)
            OPTIONAL MATCH (externalCallerClass:Class)-[:CONTAINS]->(externalCaller)
            WHERE externalCallerClass <> centerClass
            
            // 接口关系
            OPTIONAL MATCH (centerClass)-[:IMPLEMENTS]->(interface:Class)
            OPTIONAL MATCH (interface)-[:CONTAINS]->(interfaceMethod:Method)
            WHERE interfaceMethod.name = centerMethod.name
            
            RETURN centerMethod, centerClass, siblingMethod,
                   externalMethod, externalClass, 
                   externalCaller, externalCallerClass,
                   interface, interfaceMethod
            LIMIT 35
        """.trimIndent()
    }
}