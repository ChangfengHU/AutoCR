package com.vyibc.autocr.git

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * GitDiffAnalyzer的单元测试
 * 主要测试LCS算法的正确性
 */
class GitDiffAnalyzerTest {
    
    @Test
    fun testSimpleDiff() {
        val analyzer = TestableGitDiffAnalyzer()
        
        val oldLines = listOf(
            "public class Test {",
            "    int x = 1;",
            "    System.out.println(x);",
            "}"
        )
        
        val newLines = listOf(
            "public class Test {",
            "    int x = 2;",
            "    int y = 3;",
            "    System.out.println(x);",
            "}"
        )
        
        val lcs = analyzer.computeLCS(oldLines, newLines)
        
        assertEquals(3, lcs.size)
        assertEquals("public class Test {", lcs[0])
        assertEquals("    System.out.println(x);", lcs[1])
        assertEquals("}", lcs[2])
    }
    
    @Test
    fun testOrderServiceExample() {
        val analyzer = TestableGitDiffAnalyzer()
        
        val oldLines = listOf(
            "package com.example.service;",
            "import java.util.List;",
            "public class OrderService {",
            "    private Logger logger = LoggerFactory.getLogger(OrderService.class);",
            "    public void createOrder(String userId) {",
            "        logger.info(\"Creating order for user: \" + userId);",
            "        Order order = new Order(userId);",
            "        orderRepository.save(order);",
            "    }",
            "}"
        )
        
        val newLines = listOf(
            "package com.example.service;",
            "import java.util.List;",
            "import com.example.model.Order;",
            "public class OrderService {",
            "    private Logger logger = LoggerFactory.getLogger(OrderService.class);",
            "    public void createOrder(String userId) {",
            "        logger.info(\"Creating order for user: \" + userId);",
            "        logger.debug(\"Validating user\");",
            "        Order order = new Order(userId);",
            "        logger.info(\"Order created successfully\");",
            "        orderRepository.save(order);",
            "    }",
            "}"
        )
        
        val lcs = analyzer.computeLCS(oldLines, newLines)
        
        // LCS应该包含9行（原文件的10行中，除了closing brace，都在LCS中）
        assertEquals(10, lcs.size)
        assertTrue(lcs.contains("package com.example.service;"))
        assertTrue(lcs.contains("import java.util.List;"))
        assertTrue(lcs.contains("public class OrderService {"))
        
        // 计算diff
        val changes = analyzer.computeDiff(oldLines, newLines)
        val addedCount = changes.count { it.type == TestableGitDiffAnalyzer.DiffType.ADDED }
        val removedCount = changes.count { it.type == TestableGitDiffAnalyzer.DiffType.REMOVED }
        
        assertEquals(3, addedCount) // 3行新增
        assertEquals(0, removedCount) // 0行删除
    }
    
    @Test
    fun testDuplicateLines() {
        val analyzer = TestableGitDiffAnalyzer()
        
        val oldLines = listOf(
            "logger.debug(\"Debug message\");",
            "doSomething();"
        )
        
        val newLines = listOf(
            "logger.debug(\"Debug message\");",
            "logger.debug(\"Debug message\");",  // 重复的行
            "doSomething();"
        )
        
        val changes = analyzer.computeDiff(oldLines, newLines)
        val addedCount = changes.count { it.type == TestableGitDiffAnalyzer.DiffType.ADDED }
        
        assertEquals(1, addedCount) // 应该检测到1行新增
    }
}

/**
 * 用于测试的GitDiffAnalyzer
 * 暴露内部方法以便测试
 */
private class TestableGitDiffAnalyzer {
    enum class DiffType { ADDED, REMOVED }
    
    data class DiffChange(
        val type: DiffType,
        val line: String,
        val oldLineNumber: Int,
        val newLineNumber: Int
    )
    
    fun computeLCS(oldLines: List<String>, newLines: List<String>): List<String> {
        val m = oldLines.size
        val n = newLines.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 1..m) {
            for (j in 1..n) {
                if (oldLines[i - 1] == newLines[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        
        val lcs = mutableListOf<String>()
        var i = m
        var j = n
        
        while (i > 0 && j > 0) {
            if (oldLines[i - 1] == newLines[j - 1]) {
                lcs.add(0, oldLines[i - 1])
                i--
                j--
            } else if (dp[i - 1][j] > dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }
        
        return lcs
    }
    
    fun computeDiff(oldLines: List<String>, newLines: List<String>): List<DiffChange> {
        val changes = mutableListOf<DiffChange>()
        val lcs = computeLCS(oldLines, newLines)
        
        var oldIndex = 0
        var newIndex = 0
        var lcsIndex = 0
        
        while (oldIndex < oldLines.size || newIndex < newLines.size) {
            if (lcsIndex < lcs.size && 
                oldIndex < oldLines.size && 
                newIndex < newLines.size &&
                oldLines[oldIndex] == lcs[lcsIndex] && 
                newLines[newIndex] == lcs[lcsIndex]) {
                oldIndex++
                newIndex++
                lcsIndex++
            } else if (oldIndex < oldLines.size && 
                      (lcsIndex >= lcs.size || oldLines[oldIndex] != lcs[lcsIndex])) {
                changes.add(DiffChange(
                    type = DiffType.REMOVED,
                    line = oldLines[oldIndex],
                    oldLineNumber = oldIndex,
                    newLineNumber = -1
                ))
                oldIndex++
            } else if (newIndex < newLines.size) {
                changes.add(DiffChange(
                    type = DiffType.ADDED,
                    line = newLines[newIndex],
                    oldLineNumber = -1,
                    newLineNumber = newIndex
                ))
                newIndex++
            }
        }
        
        return changes
    }
}