package com.vyibc.autocr.analysis

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.vyibc.autocr.model.BusinessDomain
import com.vyibc.autocr.model.LayerType
import org.slf4j.LoggerFactory

/**
 * 业务域检测器
 * 负责识别类所属的业务域
 */
class BusinessDomainDetector {
    
    private val logger = LoggerFactory.getLogger(BusinessDomainDetector::class.java)
    
    /**
     * 检测类的业务域
     */
    fun detectBusinessDomain(psiClass: PsiClass, layer: LayerType): BusinessDomain {
        // 1. 基于包名检测（最准确）
        val packageDomain = detectByPackageName(psiClass)
        if (packageDomain != BusinessDomain.UNKNOWN) {
            return packageDomain
        }
        
        // 2. 基于类名检测
        val classNameDomain = detectByClassName(psiClass)
        if (classNameDomain != BusinessDomain.UNKNOWN) {
            return classNameDomain
        }
        
        // 3. 基于注解检测
        val annotationDomain = detectByAnnotations(psiClass)
        if (annotationDomain != BusinessDomain.UNKNOWN) {
            return annotationDomain
        }
        
        // 4. 基于层级推断通用业务
        val layerDomain = detectByLayer(layer)
        if (layerDomain != BusinessDomain.UNKNOWN) {
            return layerDomain
        }
        
        return BusinessDomain.UNKNOWN
    }
    
    /**
     * 基于包名检测业务域
     */
    private fun detectByPackageName(psiClass: PsiClass): BusinessDomain {
        val packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName?.lowercase() ?: ""
        
        return when {
            packageName.contains("user") || packageName.contains("account") || packageName.contains("profile") -> BusinessDomain.USER
            packageName.contains("order") || packageName.contains("purchase") || packageName.contains("cart") -> BusinessDomain.ORDER
            packageName.contains("product") || packageName.contains("goods") || packageName.contains("item") || packageName.contains("catalog") -> BusinessDomain.PRODUCT
            packageName.contains("payment") || packageName.contains("pay") || packageName.contains("billing") || packageName.contains("finance") -> BusinessDomain.PAYMENT
            packageName.contains("auth") || packageName.contains("security") || packageName.contains("login") || packageName.contains("permission") -> BusinessDomain.AUTH
            packageName.contains("system") || packageName.contains("admin") || packageName.contains("manage") -> BusinessDomain.SYSTEM
            packageName.contains("common") || packageName.contains("util") || packageName.contains("base") -> BusinessDomain.COMMON
            else -> BusinessDomain.UNKNOWN
        }
    }
    
    /**
     * 基于类名检测业务域
     */
    private fun detectByClassName(psiClass: PsiClass): BusinessDomain {
        val className = psiClass.name?.lowercase() ?: ""
        
        return when {
            // 用户相关
            className.contains("user") || className.contains("account") || className.contains("member") || 
            className.contains("customer") || className.contains("profile") || className.contains("person") -> BusinessDomain.USER
            
            // 订单相关
            className.contains("order") || className.contains("purchase") || className.contains("cart") || 
            className.contains("shopping") || className.contains("checkout") -> BusinessDomain.ORDER
            
            // 产品相关
            className.contains("product") || className.contains("goods") || className.contains("item") || 
            className.contains("sku") || className.contains("catalog") || className.contains("inventory") -> BusinessDomain.PRODUCT
            
            // 支付相关
            className.contains("payment") || className.contains("pay") || className.contains("billing") || 
            className.contains("invoice") || className.contains("refund") || className.contains("wallet") -> BusinessDomain.PAYMENT
            
            // 认证相关
            className.contains("auth") || className.contains("login") || className.contains("security") || 
            className.contains("permission") || className.contains("role") || className.contains("token") -> BusinessDomain.AUTH
            
            // 系统相关
            className.contains("system") || className.contains("admin") || className.contains("config") || 
            className.contains("setting") || className.contains("monitor") -> BusinessDomain.SYSTEM
            
            // 通用相关
            className.contains("common") || className.contains("util") || className.contains("helper") || 
            className.contains("base") || className.contains("abstract") -> BusinessDomain.COMMON
            
            else -> BusinessDomain.UNKNOWN
        }
    }
    
    /**
     * 基于注解检测业务域
     */
    private fun detectByAnnotations(psiClass: PsiClass): BusinessDomain {
        val annotations = psiClass.annotations.mapNotNull { it.qualifiedName }
        
        // 检查是否有业务相关的注解
        for (annotation in annotations) {
            when {
                annotation.contains("User") || annotation.contains("Account") -> return BusinessDomain.USER
                annotation.contains("Order") || annotation.contains("Purchase") -> return BusinessDomain.ORDER
                annotation.contains("Product") || annotation.contains("Catalog") -> return BusinessDomain.PRODUCT
                annotation.contains("Payment") || annotation.contains("Billing") -> return BusinessDomain.PAYMENT
                annotation.contains("Security") || annotation.contains("Auth") -> return BusinessDomain.AUTH
                annotation.contains("System") || annotation.contains("Admin") -> return BusinessDomain.SYSTEM
            }
        }
        
        return BusinessDomain.UNKNOWN
    }
    
    /**
     * 基于层级推断业务域
     */
    private fun detectByLayer(layer: LayerType): BusinessDomain {
        return when (layer) {
            LayerType.CONFIG, LayerType.UTIL -> BusinessDomain.COMMON
            else -> BusinessDomain.UNKNOWN
        }
    }
    
    /**
     * 检测业务域之间的相关性
     */
    fun detectBusinessDomainRelatedness(domain1: BusinessDomain, domain2: BusinessDomain): Double {
        return when {
            domain1 == domain2 -> 1.0
            
            // 高相关性
            (domain1 == BusinessDomain.USER && domain2 == BusinessDomain.AUTH) ||
            (domain1 == BusinessDomain.AUTH && domain2 == BusinessDomain.USER) -> 0.9
            
            (domain1 == BusinessDomain.ORDER && domain2 == BusinessDomain.PAYMENT) ||
            (domain1 == BusinessDomain.PAYMENT && domain2 == BusinessDomain.ORDER) -> 0.9
            
            (domain1 == BusinessDomain.ORDER && domain2 == BusinessDomain.PRODUCT) ||
            (domain1 == BusinessDomain.PRODUCT && domain2 == BusinessDomain.ORDER) -> 0.8
            
            // 中等相关性
            (domain1 == BusinessDomain.USER && domain2 == BusinessDomain.ORDER) ||
            (domain1 == BusinessDomain.ORDER && domain2 == BusinessDomain.USER) -> 0.7
            
            (domain1 == BusinessDomain.USER && domain2 == BusinessDomain.PAYMENT) ||
            (domain1 == BusinessDomain.PAYMENT && domain2 == BusinessDomain.USER) -> 0.6
            
            // COMMON域与其他域的相关性
            domain1 == BusinessDomain.COMMON || domain2 == BusinessDomain.COMMON -> 0.5
            
            // SYSTEM域与其他域的相关性
            domain1 == BusinessDomain.SYSTEM || domain2 == BusinessDomain.SYSTEM -> 0.4
            
            // 未知域
            domain1 == BusinessDomain.UNKNOWN || domain2 == BusinessDomain.UNKNOWN -> 0.1
            
            else -> 0.2 // 低相关性
        }
    }
    
    /**
     * 基于业务相关性判断是否应该在同一个Tree中
     */
    fun shouldBeInSameTree(domain1: BusinessDomain, domain2: BusinessDomain): Boolean {
        val relatedness = detectBusinessDomainRelatedness(domain1, domain2)
        return relatedness >= 0.6 // 相关性阈值
    }
    
    /**
     * 获取业务域的优先级（用于Tree构建时的权重计算）
     */
    fun getBusinessDomainPriority(domain: BusinessDomain): Int {
        return when (domain) {
            BusinessDomain.USER -> 10      // 用户业务最重要
            BusinessDomain.ORDER -> 9      // 订单业务次之
            BusinessDomain.PAYMENT -> 8    // 支付业务
            BusinessDomain.PRODUCT -> 7    // 产品业务
            BusinessDomain.AUTH -> 6       // 认证业务
            BusinessDomain.SYSTEM -> 3     // 系统业务
            BusinessDomain.COMMON -> 2     // 通用业务
            BusinessDomain.UNKNOWN -> 1    // 未知业务优先级最低
        }
    }
}