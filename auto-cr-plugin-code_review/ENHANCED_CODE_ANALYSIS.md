# 增强的代码分析功能

## 🚨 解决的问题

### 问题1: Git变更检测不准确
**原问题**:
```
显示的变更: 只是一些括号和空行
+     }
+ 
+ 
+ }

实际的变更: Set<String> allValues = RedisUtils.getValue(openId);
```

**问题原因**: 简单的行号对比算法，没有过滤空行和无意义的变更

### 问题2: 缺少关联方法分析
**原问题**:
```java
// 调用代码
Set<String> allValues = RedisUtils.getValue(openId);

// 实际实现（未被分析）
public static Set<String> getValue(String key) {
    Set<String> keys = stringRedisTemplate.keys(key);  // 危险的keys命令
    return keys;
}
```

**问题原因**: AI只分析了调用代码，没有分析被调用方法的具体实现

## 🔧 解决方案

### 1. 改进的Git变更检测算法

#### 原算法问题
```kotlin
// 简单的行号对比
for (i in 0 until maxLines) {
    val oldLine = oldLines.getOrNull(i)
    val newLine = newLines.getOrNull(i)
    // 简单对比，容易误判
}
```

#### 新算法改进
```kotlin
// 智能差异分析
private fun computeDiff(oldLines: List<String>, newLines: List<String>): List<DiffChange> {
    val oldSet = oldLines.toSet()
    val newSet = newLines.toSet()
    
    // 找出真正新增的行（过滤空行）
    newLines.forEachIndexed { index, line ->
        if (!oldSet.contains(line)) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty()) {  // 过滤空行
                changes.add(DiffChange(DiffType.ADDED, line, null, index))
            }
        }
    }
    
    // 找出真正删除的行
    oldLines.forEachIndexed { index, line ->
        if (!newSet.contains(line)) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty()) {  // 过滤空行
                changes.add(DiffChange(DiffType.REMOVED, line, null, index))
            }
        }
    }
}
```

### 2. 新增方法调用分析器

#### 核心功能
```kotlin
class MethodCallAnalyzer(private val project: Project) {
    
    /**
     * 分析代码变更中的方法调用
     */
    fun analyzeMethodCalls(changes: List<CodeChange>): List<MethodCallInfo>
    
    /**
     * 查找方法实现
     */
    private fun findMethodImplementation(className: String, methodName: String): MethodImplementation?
    
    /**
     * 检查方法实现中是否包含危险操作
     */
    private fun checkForDangerousOperations(methodCode: String): List<String>
}
```

#### 方法调用识别
```kotlin
// 使用正则表达式匹配方法调用
val methodCallPattern = Regex("""(\w+)\.(\w+)\s*\(""")
val matches = methodCallPattern.findAll(line)

// 示例匹配:
// RedisUtils.getValue(openId) -> className: RedisUtils, methodName: getValue
```

#### 危险操作检测
```kotlin
// 检查Redis危险命令
val redisDangerousCommands = listOf(
    "keys(", "flushdb(", "flushall(", "config(", 
    ".keys(", ".flushdb(", ".flushall(", ".config("
)

// 检查SQL危险操作
val sqlDangerousPatterns = listOf(
    "select \\* from", "delete from.*where", "update.*set.*where"
)
```

## 🚀 新的分析流程

### 1. 增强的变更检测
```
=== 📊 代码变更统计 ===
变更文件数量: 1
• UserService.java (修改)
  新增行: 1, 删除行: 0, 修改行: 0
  新增内容:
    + Set<String> allValues = RedisUtils.getValue(openId);
    + log.info("wxxcxLogin,allValues:{}", JSON.toJSONString(allValues));
```

### 2. 方法调用分析
```
=== 🔍 方法调用分析 ===
发现 1 个方法调用需要分析:
• RedisUtils.getValue()
  调用位置: Set<String> allValues = RedisUtils.getValue(openId);
  实现文件: /src/main/java/utils/RedisUtils.java
  ⚠️ 危险操作: Redis危险命令: keys(
```

### 3. AI分析包含方法实现
```
## 🔍 相关方法实现分析：

以下是代码变更中调用的方法的具体实现，请重点分析这些方法是否存在安全风险：

### 方法: RedisUtils.getValue()
调用位置: Set<String> allValues = RedisUtils.getValue(openId);
实现文件: /src/main/java/utils/RedisUtils.java

方法实现代码:
```java
/**
 * 判断key是否存在
 *
 * @param key
 * @return
 */
public static Set<String> getValue(String key) {
    Set<String> keys = stringRedisTemplate.keys(key);
    return keys;
}
```

⚠️ 已检测到潜在危险操作:
- Redis危险命令: keys(
```

## 🎯 预期的AI检测结果

现在AI应该能准确检测出：

```json
{
  "overallScore": 25,
  "riskLevel": "CRITICAL",
  "issues": [
    {
      "filePath": "UserService.java",
      "lineNumber": 45,
      "severity": "CRITICAL",
      "category": "PERFORMANCE",
      "message": "调用了RedisUtils.getValue()方法，该方法内部使用了危险的Redis keys命令",
      "suggestion": "将RedisUtils.getValue()方法中的keys命令替换为SCAN命令，避免在生产环境中阻塞Redis服务"
    },
    {
      "filePath": "RedisUtils.java", 
      "lineNumber": 15,
      "severity": "CRITICAL",
      "category": "PERFORMANCE",
      "message": "使用了Redis危险命令stringRedisTemplate.keys()，在大数据量时会导致Redis阻塞",
      "suggestion": "使用stringRedisTemplate.scan()命令替代keys命令"
    }
  ],
  "suggestions": [
    "修改RedisUtils.getValue()方法，使用SCAN命令替代keys命令",
    "添加Redis操作的性能监控和告警",
    "在生产环境配置中禁用危险的Redis命令"
  ],
  "summary": "发现严重的生产环境风险：代码调用了包含Redis keys命令的方法，需要立即修复",
  "commitMessage": "fix: 修复Redis性能问题，避免使用危险的keys命令\n\n- 识别到RedisUtils.getValue()方法使用了keys命令\n- 建议使用SCAN命令替代以避免生产环境阻塞"
}
```

## 🔧 技术实现细节

### 1. 文件搜索算法
```kotlin
private fun findClassFile(className: String): File? {
    val projectPath = project.basePath ?: return null
    val projectDir = File(projectPath)
    
    // 在src目录下递归搜索Java文件
    return findFileRecursively(projectDir, "${className}.java")
}
```

### 2. 方法提取算法
```kotlin
private fun extractMethodImplementation(classContent: String, methodName: String): String? {
    // 使用正则表达式匹配方法签名
    val methodPattern = Regex(
        """(public|private|protected)?\s*(static)?\s*\w+\s+$methodName\s*\([^)]*\)\s*\{""",
        RegexOption.MULTILINE
    )
    
    // 匹配大括号找到方法结束位置
    var braceCount = 0
    // ... 括号匹配逻辑
}
```

### 3. 危险操作模式匹配
```kotlin
// Redis危险命令检测
val redisDangerousCommands = listOf(
    "keys(", "flushdb(", "flushall(", "config("
)

// SQL危险操作检测  
val sqlDangerousPatterns = listOf(
    "select \\* from", "delete from.*where"
)
```

## 🎉 用户体验改进

### 更准确的变更检测
- ✅ 过滤无意义的空行变更
- ✅ 显示真正的代码变更内容
- ✅ 准确识别新增、删除、修改的代码

### 深度的安全分析
- ✅ 分析方法调用链
- ✅ 检测被调用方法的具体实现
- ✅ 识别隐藏在工具类中的危险操作

### 完整的风险评估
- ✅ 不仅分析调用代码，还分析实现代码
- ✅ 提供具体的修复建议
- ✅ 生成包含问题分析的提交信息

现在AI代码评估能够进行更深入、更准确的分析，真正发现隐藏的安全风险！🎯
