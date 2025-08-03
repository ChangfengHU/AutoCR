package com.vyibc.autocr.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.slf4j.LoggerFactory

/**
 * 缓存服务
 * 管理项目级别的多级缓存（简化版本）
 */
@Service(Service.Level.PROJECT)
class CacheService(private val project: Project) {
    private val logger = LoggerFactory.getLogger(CacheService::class.java)
    
    init {
        logger.info("CacheService initialized for project: {}", project.name)
    }
    
    // 简单的内存缓存存储
    private val cache = mutableMapOf<String, Any>()
    
    /**
     * 存储缓存项
     */
    fun put(key: String, value: Any) {
        cache[key] = value
        logger.debug("Cached item with key: {}", key)
    }
    
    /**
     * 获取缓存项
     */
    fun get(key: String): Any? {
        val value = cache[key]
        logger.debug("Retrieved cache item with key: {}, found: {}", key, value != null)
        return value
    }
    
    /**
     * 移除缓存项
     */
    fun remove(key: String): Any? {
        val removed = cache.remove(key)
        logger.debug("Removed cache item with key: {}", key)
        return removed
    }
    
    /**
     * 清空缓存
     */
    fun clear() {
        cache.clear()
        logger.debug("Cache cleared")
    }
    
    /**
     * 获取缓存摘要信息
     */
    fun getCacheSummary(): CacheSummary {
        return CacheSummary(
            totalCacheTypes = 5,
            totalHits = 0L,
            totalMisses = 0L,
            totalSize = cache.size.toLong(),
            totalEvictions = 0L,
            overallHitRate = 0.0
        )
    }
    
    companion object {
        fun getInstance(project: Project): CacheService {
            return project.service()
        }
    }
}

/**
 * 缓存摘要信息
 */
data class CacheSummary(
    val totalCacheTypes: Int,
    val totalHits: Long,
    val totalMisses: Long,
    val totalSize: Long,
    val totalEvictions: Long,
    val overallHitRate: Double
)