# 代码评估工作流程指南

## 🎯 三种代码评估场景

### 1. 评估已暂存变更 (Review Staged Changes)
**使用场景**: 当你已经使用 `git add` 添加了文件，但还没有 `commit` 时

#### 操作步骤:
1. 修改代码文件
2. 执行 `git add <files>` 或在IDE中暂存文件
3. 点击 **"Review Staged Changes"** 按钮或按 `Ctrl+Shift+A`
4. 查看评估结果
5. 如果通过评估，执行 `git commit`

#### 快捷键: `Ctrl+Shift+A`
#### 按钮位置: Git Changes面板工具栏

---

### 2. 评估已提交变更 (Review Committed Changes)  
**使用场景**: 当你已经 `commit` 了代码，但还没有 `push` 到远程仓库时

#### 操作步骤:
1. 代码已经通过 `git commit` 提交到本地仓库
2. 点击 **"Review Committed Changes"** 按钮或按 `Ctrl+Shift+P`
3. 查看评估结果
4. 如果通过评估，可以安全地执行 `git push`
5. 如果未通过，建议使用 `git reset --soft HEAD~1` 撤销提交并修复问题

#### 快捷键: `Ctrl+Shift+P`
#### 按钮位置: Git Changes面板工具栏

---

### 3. 完整提交流程 (Commit & Review)
**使用场景**: 一站式评估并提交代码

#### 操作步骤:
1. 修改代码文件
2. 点击 **"Commit & Review"** 按钮或按 `Ctrl+Shift+M`
3. 输入提交信息
4. 查看评估结果
5. 如果通过评估，直接完成提交

#### 快捷键: `Ctrl+Shift+M`
#### 按钮位置: Git Changes面板工具栏

---

## 🔄 典型的开发工作流程

### 场景A: 谨慎开发者的工作流程
```bash
# 1. 修改代码
vim src/main/java/Example.java

# 2. 暂存文件
git add src/main/java/Example.java

# 3. 评估已暂存的变更
# 在IDE中按 Ctrl+Shift+A 或点击 "Review Staged Changes"

# 4. 如果评估通过，提交代码
git commit -m "feat: add new feature"

# 5. 评估已提交的变更
# 在IDE中按 Ctrl+Shift+P 或点击 "Review Committed Changes"

# 6. 如果评估通过，推送代码
git push origin main
```

### 场景B: 快速开发者的工作流程
```bash
# 1. 修改代码
vim src/main/java/Example.java

# 2. 直接使用完整流程
# 在IDE中按 Ctrl+Shift+M 或点击 "Commit & Review"
# 这会自动处理暂存、评估、提交的整个流程
```

---

## 🎮 快捷键总览

| 功能 | 快捷键 | 说明 |
|------|--------|------|
| 评估已暂存变更 | `Ctrl+Shift+A` | 评估 git add 后的代码 |
| 评估已提交变更 | `Ctrl+Shift+P` | 评估 git commit 后的代码 |
| 完整提交流程 | `Ctrl+Shift+M` | 一站式评估并提交 |
| 测试功能 | `Ctrl+Alt+Shift+R` | 使用模拟数据测试 |
| 翻译功能 | `Ctrl+Alt+T` | 翻译选中文本 |

---

## 📍 按钮位置

所有代码评估按钮都位于 **Git Changes面板** 中：

### 工具栏按钮 (从左到右):
```
[Commit] [Review Staged Changes] [Review Committed Changes] [Commit & Review] [Push] [...]
```

### 右键菜单:
在Git Changes面板中右键点击，可以看到：
- "Review Staged Changes"
- "Review Committed Changes"  
- "Code Review"

---

## 🔧 配置说明

### AI服务配置
1. 打开 `File` → `Settings` → `Tools` → `Auto Code Review`
2. 配置以下选项：
   - **启用自动代码评估**: 开启功能
   - **AI服务API Key**: 输入DeepSeek、通义千问或Gemini的API Key
   - **最低评分要求**: 设置代码质量阈值 (0-100)

### 评估规则
可以配置检查的维度：
- 代码风格和规范
- 性能问题
- 安全风险  
- Bug风险
- 可维护性
- 文档完整性
- 最佳实践

---

## 💡 使用建议

### 对于团队开发:
1. **统一使用"评估已暂存变更"**: 在 `git add` 后立即评估
2. **设置合理的评分阈值**: 建议设置为70-80分
3. **配置团队共享的API Key**: 确保所有成员都能使用

### 对于个人开发:
1. **使用"完整提交流程"**: 最方便的一站式操作
2. **定期评估已提交变更**: 在推送前进行最后检查
3. **利用测试功能**: 熟悉评估流程和结果解读

### 对于代码审查:
1. **评估已提交变更**: 在代码审查前先进行AI评估
2. **关注高风险问题**: 优先处理严重和重要级别的问题
3. **参考改进建议**: AI提供的建议可以作为代码审查的参考

---

## 🚨 注意事项

1. **API Key安全**: 不要在代码中硬编码API Key
2. **网络连接**: AI评估需要网络连接，离线时无法使用
3. **评估时间**: 大文件的评估可能需要较长时间
4. **评分标准**: 评分标准可能因AI服务而异，建议根据实际情况调整阈值

现在你有了三个强大的代码评估工具，可以在开发的不同阶段进行质量检查！🎉
