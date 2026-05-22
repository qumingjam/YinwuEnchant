---
trigger: always_on
---

## ⚠️ 注意事项

### 1. Maven 未配置到环境变量
- ❌ 不要直接使用 `mvn` 命令（会报错：无法识别为 cmdlet）
- ✅ 必须使用完整路径调用 IDEA 内置的 Maven

### 2. PowerShell 语法
- 使用 `&` 符号调用外部命令
- 路径包含空格时必须用双引号包裹
- 参数之间用空格分隔

### 3. 输出位置
编译成功后，jar 文件生成在：
```
D:\software\YinwuRaid\target\YinwuRaid-1.1.0.jar
```

---

## 🎯 替代方案

如果不想每次输入完整路径，可以选择以下方案之一：

### 方案 1：创建 PowerShell 别名（推荐）
在 PowerShell 配置文件 (`$PROFILE`) 中添加：
```powershell
Set-Alias mvn-idea "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd"
```
然后可以使用：
```powershell
mvn-idea clean package -DskipTests
```

### 方案 2：配置系统环境变量
1. 添加环境变量 `MAVEN_HOME`：
   ```
   C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3
   ```
2. 在 `Path` 中添加：
   ```
   %MAVEN_HOME%\bin
   ```
3. 重启终端后可以直接使用 `mvn` 命令

### 方案 3：使用 IntelliJ IDEA 界面
- 打开右侧 **Maven** 面板
- 展开 **Lifecycle**
- 双击 **package** 或 **clean package**

---

## 📝 版本信息

- **IntelliJ IDEA 版本**：2026.1
- **Maven 版本**：IDEA 内置 Maven3
- **项目路径**：`D:\software\YinwuRaid`
- **Java 版本**：21

---

## ✅ 验证配置

运行以下命令验证 Maven 是否正常工作：
```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd" --version
```

预期输出应包含：
- Apache Maven 版本号
- Java 版本信息
- OS 信息

---

**最后更新**：2026-05-14  
**维护者**：YinwuRaid 开发团队