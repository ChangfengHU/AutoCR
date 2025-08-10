# AutoCR 权重计算逻辑文档

> **版本**: v2.0 (基于Neo4j图数据库)  
> **更新时间**: 2025-08-10  
> **核心理念**: 以Neo4j图数据库查询为核心(80%) + Git变更分析为辅助(20%)

---

## 🎯 核心设计理念

### 设计原则
- **图数据库优先**: Neo4j查询提供真实的调用关系、架构层级、依赖关系
- **精确性**: 基于实际代码结构，不依赖文件名推测或启发式规则
- **平衡性**: 保留Git变更分析的合理部分作为补充
- **可扩展性**: 图数据库schema可根据需要添加业务字段

### 权重分配原则
```
总权重 = 图数据库分析(80%) + Git变更分析(20%)
```

---

## 📊 意图权重计算逻辑

### 权重分配公式
```
意图权重 = [业务影响(40%) + 架构价值(25%) + 调用链完整性(15%)] × 80% + Git变更分析 × 20%
```

### 1. 业务影响分析 (40% × 80% = 32%)

#### 1.1 下游业务价值计算
**Neo4j查询**: `queryMethodCallees(className, methodName)`
```cypher
MATCH (sourceClass:Class {name: "$className"})-[:CONTAINS]->(sourceMethod:Method {name: "$methodName"})
MATCH (sourceMethod)-[:CALLS]->(targetMethod:Method)<-[:CONTAINS]-(targetClass:Class)
RETURN targetClass.layer, targetMethod.name, count(*) as callCount
```

**评分规则**:
- **数据访问层** (`REPOSITORY`, `DAO`): 20分/个调用
- **业务逻辑层** (`SERVICE`): 15分/个调用  
- **工具类** (`UTIL`, `HELPER`): 10分/个调用
- **外部API** (`EXTERNAL_API`): 25分/个调用
- **业务术语匹配**: 每匹配一个业务关键词+8分
- **最高50分**

#### 1.2 上游业务价值计算  
**Neo4j查询**: `queryMethodCallers(className, methodName)`
```cypher
MATCH (targetClass:Class {name: "$className"})-[:CONTAINS]->(targetMethod:Method {name: "$methodName"})
MATCH (callerClass:Class)-[:CONTAINS]->(callerMethod:Method)-[:CALLS]->(targetMethod)
RETURN callerClass.layer, callerMethod.name, count(*) as callCount
```

**评分规则**:
- **控制层** (`CONTROLLER`, `REST`): 25分/个调用者
- **定时任务** (`SCHEDULED`, `BATCH`): 20分/个调用者
- **拦截器** (`FILTER`, `INTERCEPTOR`): 18分/个调用者
- **服务层** (`SERVICE`): 15分/个调用者
- **调用频次权重**: >20次(20分), >10次(15分), >5次(10分), 其他(5分)
- **最高40分**

#### 1.3 架构位置价值计算
**Neo4j查询**: `queryClassArchitecture(className)`
```cypher
MATCH (c:Class {name: "$className"})
OPTIONAL MATCH (c)-[:EXTENDS]->(parent:Class)
OPTIONAL MATCH (c)-[:IMPLEMENTS]->(interface:Class)
OPTIONAL MATCH (c)-[:CONTAINS]->(m:Method)-[:CALLS]->(tm:Method)<-[:CONTAINS]-(dep:Class)
RETURN c.layer, collect(parent.name), collect(interface.name), collect(dep.name)
```

**评分规则**:
- **基础层级分值**: CONTROLLER(20), SERVICE(15), REPOSITORY(12), CONFIG(10), UTIL(8)
- **依赖数量权重**: 每个依赖+2分，最高15分
- **接口实现权重**: 每个接口+5分
- **继承层次权重**: 每个父类/子类+3分
- **最高30分**

### 2. 架构价值分析 (25% × 80% = 20%)

#### 2.1 跨层调用价值
**评分规则**:
- **4层架构** (CONTROLLER→SERVICE→REPOSITORY→UTIL): 40分
- **3层架构**: 30分
- **2层架构**: 20分  
- **单层**: 10分

#### 2.2 依赖复杂度价值
**评分规则**:
- **适中复杂度** (5-10个依赖): 25分 (最佳)
- **较低复杂度** (2-5个依赖): 20分
- **高复杂度** (>10个依赖): 15分 (可能过度设计)
- **最低复杂度** (1-2个依赖): 10分
- **无依赖**: 5分

### 3. 调用链完整性分析 (15% × 80% = 12%)

#### 3.1 多方法路径完整性
**Neo4j查询**: `queryCallPathChain(sourceClass, sourceMethod, targetClass, targetMethod)`
```cypher
MATCH (source:Class {name: "$sourceClass"})-[:CONTAINS]->(sourceM:Method {name: "$sourceMethod"})
MATCH (target:Class {name: "$targetClass"})-[:CONTAINS]->(targetM:Method {name: "$targetMethod"}) 
MATCH path = shortestPath((sourceM)-[:CALLS*1..5]->(targetM))
RETURN length(path), [rel in relationships(path) | type(rel)], nodes(path)
```

**评分规则**:
- **路径长度**: 2层调用(40分-最佳), 1层(30分), 3层(35分), 4层(25分), >4层(15分)
- **层级违规检测**: 无违规(+30分), 有违规(+10分)
- **涉及层级数**: 每个层级+10分
- **最高80分**

#### 3.2 单方法路径完整性
**Neo4j查询**: `queryBlastRadius(className, methodName)`
```cypher
MATCH (center:Class {name: "$className"})-[:CONTAINS]->(centerMethod:Method {name: "$methodName"})
OPTIONAL MATCH (caller:Class)-[:CONTAINS]->(callerMethod:Method)-[:CALLS]->(centerMethod)
OPTIONAL MATCH (centerMethod)-[:CALLS]->(calleeMethod:Method)<-[:CONTAINS]-(callee:Class)
RETURN count(caller), count(callee), collect(caller.layer), collect(callee.layer)
```

**评分规则**:
- **影响范围**: >20个影响(60分), >10个(50分), >5个(40分), >0个(30分), 0个(20分)
- **架构层级**: 每个层级+8分
- **最高70分**

### 4. Git变更补充分析 (20%)

#### 4.1 文件类型影响 (30分)
- Controller/REST: 20分
- Service: 15分  
- DTO/VO/Model: 12分
- Repository/DAO: 12分
- Entity: 10分
- Config: 8分
- Util/Helper: 5分

#### 4.2 变更规模影响 (25分)
- **变更行数**: >500行(15分), >200行(12分), >100行(10分), >50行(8分), 其他(5分)
- **文件数量**: >10个(10分), >5个(8分), >3个(6分), 其他(3分)

#### 4.3 业务术语匹配 (20分)
关键词: user, customer, order, product, payment, cart, checkout, account, profile, auth, login, register, inventory
每匹配一个+4分

#### 4.4 新功能检测 (15分)
- Spring MVC注解(@PostMapping等): 8分
- 新类定义: 5分
- Spring组件注解: 2分

#### 4.5 API端点价值 (10分)
- 包含"controller": 6分
- 包含"endpoint"或"api": 4分

---

## ⚠️ 风险权重计算逻辑

### 权重分配公式
```
风险权重 = [架构风险(35%) + 爆炸半径(30%) + 层级违规(15%)] × 80% + Git变更分析 × 20%
```

### 1. 架构风险分析 (35% × 80% = 28%)

#### 1.1 依赖复杂度风险
**Neo4j查询**: 同架构信息查询
**评分规则**:
- **过度依赖** (>15个): 40分 (高风险)
- **较高依赖** (>10个): 25分 (中等风险)
- **适度依赖** (>5个): 10分 (较低风险)
- **低依赖** (≤5个): 5分 (较低风险)

#### 1.2 接口实现风险
- **实现过多接口**: >3个接口，每多1个+8分
- **被过多类实现**: >5个实现类，每多1个+5分
- **最高30分**

#### 1.3 继承层次风险
- **层次过深**: >4层继承关系，每多1层+6分
- **最高25分**

### 2. 爆炸半径风险分析 (30% × 80% = 24%)

#### 2.1 直接调用者风险
**Neo4j查询**: `queryBlastRadius(className, methodName)`
**评分规则**:
- **>20个调用者**: 40分 (高影响)
- **>10个调用者**: 30分 (中等影响)  
- **>5个调用者**: 20分 (较低影响)
- **≤5个调用者**: 10分 (最低影响)

#### 2.2 间接调用者风险  
- **>50个间接调用者**: 30分
- **>20个间接调用者**: 20分
- **>10个间接调用者**: 10分
- **≤10个间接调用者**: 5分

#### 2.3 跨层级影响风险
- **>3个层级**: 每多1层+8分

#### 2.4 总影响类数量风险
- **>30个影响类**: +20分

**最高80分**

### 3. 层级违规风险分析 (15% × 80% = 12%)

#### 3.1 调用路径违规检测
**Neo4j查询**: `queryCallPathChain()` 检测层级跨越
**评分规则**:
- **基础违规**: +30分
- **路径过长**: >3层，每多1层+5分  
- **跨越层级过多**: >4层+15分
- **最高60分**

#### 3.2 单类层级违规检测
**有效层级转换规则**:
```
CONTROLLER → SERVICE
SERVICE → REPOSITORY, UTIL  
REPOSITORY → (无)
UTIL → (无)
```
- **无效依赖**: 每个+15分
- **最高45分**

### 4. Git变更风险补充分析 (20%)

#### 4.1 敏感文件类型风险 (30分)
- Config/Properties/YAML: 25分
- Security/Auth: 20分
- Util/Common: 18分
- Database/SQL: 15分
- Controller/REST: 12分
- Service: 8分

#### 4.2 变更规模风险 (25分)  
- **超大规模** (>1000行): 20分
- **大规模** (>500行): 15分
- **中等规模** (>200行): 12分
- **较小规模** (>100行): 8分
- **微小变更**: 5分

#### 4.3 敏感操作检测 (20分)
敏感操作关键词及风险分值:
- DROP: 10分, TRUNCATE: 9分, @Transactional: 8分, SECRET: 8分
- @PreAuthorize: 7分, DELETE: 6分, @PostAuthorize: 6分
- PASSWORD: 5分, ALTER: 5分, @Async/@Scheduled: 4分, TOKEN: 4分

#### 4.4 删除操作风险 (15分)
- **删除文件**: 每个+5分
- **删除行数**: >500行(10分), >200行(8分), >100行(6分), >50行(4分), 其他(2分)

#### 4.5 配置变更风险 (10分)  
- **配置文件变更**: 每个+5分

---

## 🔧 Neo4j查询服务架构

### 核心服务类: `Neo4jQueryService`

#### 主要查询方法

| 方法 | 用途 | 返回数据 |
|------|------|----------|
| `queryMethodCallers()` | 查询方法调用者 | 调用者类名、层级、调用次数 |
| `queryMethodCallees()` | 查询方法被调用者 | 被调用者类名、层级、调用次数 |
| `queryClassArchitecture()` | 查询类架构信息 | 继承、接口、依赖关系 |
| `queryCallPathChain()` | 查询调用路径链 | 完整调用路径、层级违规检测 |
| `queryBlastRadius()` | 查询影响范围 | 1度、2度调用关系统计 |

#### 数据模型

```kotlin
data class MethodCallersInfo(
    val totalCallers: Int,
    val layerDistribution: Map<String, Int>,
    val callerDetails: List<CallerInfo>,
    val query: String
)

data class ClassArchitectureInfo(
    val className: String,
    val layer: String,
    val dependencies: List<String>,
    val interfaces: List<String>,
    val parents: List<String>,
    val children: List<String>,
    val query: String
)

data class BlastRadiusInfo(
    val directCallers: Int,
    val indirectCallers: Int,
    val directCallees: Int,
    val indirectCallees: Int,
    val affectedLayers: List<String>,
    val totalAffectedClasses: Int,
    val query: String
)
```

---

## 📋 使用示例

### 意图权重计算流程
```kotlin
val neo4jQueryService = Neo4jQueryService()
val intentCalculator = IntentWeightCalculator(neo4jQueryService)

// 对每个方法路径
path.methods.forEach { methodPath ->
    val className = methodPath.substringBeforeLast(".")
    val methodName = methodPath.substringAfterLast(".")
    
    // 1. 查询下游价值
    val calleeInfo = neo4jQueryService.queryMethodCallees(className, methodName)
    val downstreamScore = calculateDownstreamBusinessValue(calleeInfo)
    
    // 2. 查询上游价值  
    val callerInfo = neo4jQueryService.queryMethodCallers(className, methodName)
    val upstreamScore = calculateUpstreamBusinessValue(callerInfo)
    
    // 3. 查询架构位置
    val archInfo = neo4jQueryService.queryClassArchitecture(className)
    val positionScore = calculateArchitecturalPositionValue(archInfo)
}

// 最终权重 = 图数据库分析(80%) + Git变更分析(20%)
val totalWeight = graphWeight * 0.8 + gitWeight * 0.2
```

### 风险权重计算流程
```kotlin
val riskCalculator = RiskWeightCalculator(neo4jQueryService)

// 架构风险分析
val archInfo = neo4jQueryService.queryClassArchitecture(className)
val dependencyRisk = calculateDependencyComplexityRisk(archInfo)
val interfaceRisk = calculateInterfaceImplementationRisk(archInfo)

// 爆炸半径风险分析  
val blastRadius = neo4jQueryService.queryBlastRadius(className, methodName)
val blastRisk = calculateSingleMethodBlastRisk(blastRadius)

// 层级违规风险分析
val chainInfo = neo4jQueryService.queryCallPathChain(sourceClass, sourceMethod, targetClass, targetMethod)
val violationRisk = calculateLayerViolationSeverity(chainInfo)
```

---

## ✅ 关键优势

### 1. 精确性
- 基于实际图数据库查询，获得真实的调用关系
- 不再依赖文件名推测或字符串匹配
- 层级违规通过实际调用路径检测

### 2. 权威性  
- Neo4j图数据库包含完整的代码结构信息
- 调用关系、继承关系、接口实现关系准确
- 架构层级基于真实的package和类型信息

### 3. 可扩展性
- 图数据库schema可以根据需要扩展
- 可以添加调用频率、性能指标、业务重要性等字段
- 查询逻辑可以根据新字段调整权重

### 4. 透明性
- 每个查询都有对应的Cypher语句
- 调试时可以直接在Neo4j Browser中执行查询
- 权重计算过程有详细的调试日志

### 5. 平衡性
- 保留了Git变更分析的合理部分
- 图数据库为主(80%)，Git分析为辅(20%)
- 避免完全抛弃有价值的启发式规则

---

## 🔄 扩展建议

### 图数据库字段扩展
- **调用频率**: `callFrequency` - 运行时统计的实际调用次数
- **性能指标**: `avgExecutionTime` - 平均执行时间
- **业务重要性**: `businessCriticality` - 人工标记的业务重要性
- **测试覆盖率**: `testCoverage` - 单测覆盖率百分比
- **代码质量分**: `codeQualityScore` - 静态代码分析分数

### 查询优化建议  
- 缓存常用的架构查询结果
- 对大型项目使用分页查询
- 添加查询性能监控
- 实现查询结果的增量更新

### 权重调优建议
- 基于实际使用反馈调整权重系数
- 不同项目类型采用不同的权重策略
- 支持用户自定义权重配置
- 添加A/B测试框架验证权重效果

---

## 📚 相关文件

- **IntentWeightCalculator.kt** - 意图权重计算器
- **RiskWeightCalculator.kt** - 风险权重计算器  
- **Neo4jQueryService.kt** - Neo4j查询服务
- **CodeReviewOrchestrator.kt** - 主要编排逻辑
- **TECHNICAL_SOLUTION_V5.1_COMPLETE.md** - 技术方案文档

---

*最后更新: 2025-08-10*  
*版本: v2.0 (Neo4j驱动)*