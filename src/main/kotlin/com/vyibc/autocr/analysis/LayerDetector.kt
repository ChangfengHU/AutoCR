package com.vyibc.autocr.analysis

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifier
import com.vyibc.autocr.model.LayerType

/**
 * 层级检测器
 */
class LayerDetector {
    
    fun detectLayer(psiClass: PsiClass): LayerType {
        // 1. 基于注解检测（最准确）
        val annotationLayer = detectByAnnotations(psiClass)
        if (annotationLayer != LayerType.UNKNOWN) {
            return annotationLayer
        }
        
        // 2. 基于包名检测
        val packageLayer = detectByPackage(psiClass)
        if (packageLayer != LayerType.UNKNOWN) {
            return packageLayer
        }
        
        // 3. 基于类名检测
        val nameLayer = detectByClassName(psiClass)
        if (nameLayer != LayerType.UNKNOWN) {
            return nameLayer
        }
        
        // 4. 基于继承关系检测
        val inheritanceLayer = detectByInheritance(psiClass)
        if (inheritanceLayer != LayerType.UNKNOWN) {
            return inheritanceLayer
        }
        
        // 5. 基于方法特征检测
        val methodLayer = detectByMethods(psiClass)
        if (methodLayer != LayerType.UNKNOWN) {
            return methodLayer
        }
        
        return LayerType.UNKNOWN
    }
    
    private fun detectByAnnotations(psiClass: PsiClass): LayerType {
        val annotations = psiClass.annotations
        
        for (annotation in annotations) {
            when (annotation.qualifiedName) {
                // Controller 注解
                "org.springframework.stereotype.Controller",
                "org.springframework.web.bind.annotation.RestController",
                "javax.ws.rs.Path",
                "org.springframework.web.bind.annotation.ControllerAdvice" -> return LayerType.CONTROLLER
                
                // Service 注解
                "org.springframework.stereotype.Service",
                "javax.ejb.Stateless",
                "javax.ejb.Stateful",
                "javax.ejb.Singleton" -> return LayerType.SERVICE
                
                // Repository 注解
                "org.springframework.stereotype.Repository",
                "org.springframework.data.repository.Repository",
                "org.springframework.data.jpa.repository.JpaRepository",
                "org.springframework.data.mongodb.repository.MongoRepository" -> return LayerType.REPOSITORY
                
                // Mapper 注解
                "org.apache.ibatis.annotations.Mapper",
                "org.mybatis.spring.annotation.MapperScan" -> return LayerType.MAPPER
                
                // Configuration 注解
                "org.springframework.context.annotation.Configuration",
                "org.springframework.boot.context.properties.ConfigurationProperties",
                "org.springframework.boot.autoconfigure.SpringBootApplication" -> return LayerType.CONFIG
                
                // Component 注解
                "org.springframework.stereotype.Component" -> return LayerType.COMPONENT
                
                // Entity 注解
                "javax.persistence.Entity",
                "org.springframework.data.mongodb.core.mapping.Document",
                "org.hibernate.annotations.Entity" -> return LayerType.ENTITY
            }
        }
        
        return LayerType.UNKNOWN
    }
    
    private fun detectByPackage(psiClass: PsiClass): LayerType {
        val packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName?.lowercase() ?: ""
        
        return when {
            packageName.contains("controller") || packageName.contains("web") -> LayerType.CONTROLLER
            packageName.contains("service") || packageName.contains("business") -> LayerType.SERVICE
            packageName.contains("mapper") || packageName.contains("dao") -> LayerType.MAPPER
            packageName.contains("repository") || packageName.contains("repo") -> LayerType.REPOSITORY
            packageName.contains("util") || packageName.contains("utils") || packageName.contains("helper") -> LayerType.UTIL
            packageName.contains("entity") || packageName.contains("model") || packageName.contains("domain") -> LayerType.ENTITY
            packageName.contains("config") || packageName.contains("configuration") -> LayerType.CONFIG
            packageName.contains("component") -> LayerType.COMPONENT
            else -> LayerType.UNKNOWN
        }
    }
    
    private fun detectByClassName(psiClass: PsiClass): LayerType {
        val className = psiClass.name?.lowercase() ?: ""
        
        return when {
            className.endsWith("controller") -> LayerType.CONTROLLER
            className.endsWith("service") || className.endsWith("serviceimpl") -> LayerType.SERVICE
            className.endsWith("mapper") || className.endsWith("dao") -> LayerType.MAPPER
            className.endsWith("repository") || className.endsWith("repo") -> LayerType.REPOSITORY
            className.endsWith("util") || className.endsWith("utils") || className.endsWith("helper") -> LayerType.UTIL
            className.endsWith("entity") || className.endsWith("model") || className.endsWith("do") || 
            className.endsWith("dto") || className.endsWith("vo") || className.endsWith("po") -> LayerType.ENTITY
            className.endsWith("config") || className.endsWith("configuration") -> LayerType.CONFIG
            className.endsWith("component") -> LayerType.COMPONENT
            else -> LayerType.UNKNOWN
        }
    }
    
    private fun detectByInheritance(psiClass: PsiClass): LayerType {
        // 检查父类
        val superClass = psiClass.superClass
        if (superClass != null) {
            val superClassName = superClass.name?.lowercase() ?: ""
            when {
                superClassName.contains("controller") -> return LayerType.CONTROLLER
                superClassName.contains("service") -> return LayerType.SERVICE
                superClassName.contains("repository") -> return LayerType.REPOSITORY
                superClassName.contains("mapper") -> return LayerType.MAPPER
            }
        }
        
        // 检查实现的接口
        for (interfaceClass in psiClass.interfaces) {
            val interfaceName = interfaceClass.name?.lowercase() ?: ""
            when {
                interfaceName.contains("controller") -> return LayerType.CONTROLLER
                interfaceName.contains("service") -> return LayerType.SERVICE
                interfaceName.contains("repository") -> return LayerType.REPOSITORY
                interfaceName.contains("mapper") -> return LayerType.MAPPER
            }
        }
        
        return LayerType.UNKNOWN
    }
    
    private fun detectByMethods(psiClass: PsiClass): LayerType {
        val methods = psiClass.methods
        val methodNames = methods.map { it.name.lowercase() }
        
        // 分析方法名模式
        val hasWebMethods = methodNames.any { 
            it.startsWith("get") || it.startsWith("post") || it.startsWith("put") || 
            it.startsWith("delete") || it.contains("mapping") 
        }
        
        val hasServiceMethods = methodNames.any { 
            it.contains("process") || it.contains("handle") || it.contains("execute") || 
            it.contains("business") 
        }
        
        val hasDataMethods = methodNames.any { 
            it.startsWith("find") || it.startsWith("save") || it.startsWith("update") || 
            it.startsWith("delete") || it.startsWith("insert") || it.startsWith("select") 
        }
        
        val hasUtilMethods = methods.count { 
            it.hasModifierProperty(PsiModifier.STATIC) && it.hasModifierProperty(PsiModifier.PUBLIC) 
        } > methods.size * 0.7
        
        return when {
            hasWebMethods -> LayerType.CONTROLLER
            hasServiceMethods -> LayerType.SERVICE  
            hasDataMethods -> if (methodNames.any { it.contains("mapper") }) LayerType.MAPPER else LayerType.REPOSITORY
            hasUtilMethods -> LayerType.UTIL
            else -> LayerType.UNKNOWN
        }
    }
}