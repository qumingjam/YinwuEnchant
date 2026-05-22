# YinwuEnchant 插件优化清单

> 本清单基于代码分析生成，包含可优化项、优先级、问题描述、优化方案和预期收益。

---

## 📋 目录

1. [代码重复与冗余优化](#code-duplication)
2. [性能优化](#performance)
3. [架构优化](#architecture)
4. [代码质量改进](#code-quality)
5. [可维护性改进](#maintainability)

---

## <a name="code-duplication"></a>一、代码重复与冗余优化

### 1.1 统一事件订阅模式

| 项目 | 详情 |
|------|------|
| **优先级** | 🔴 高 |
| **涉及文件** | `CatsPaw.java`, `Nasus.java`, `ShriekerSense.java`, `Harvest.java`, `PhantomProtection.java` |
| **当前问题** | 部分附魔使用传统 `@EventHandler` 注解，部分使用事件订阅者模式，风格不统一 |
| **优化方案** | 将所有附魔的事件处理统一改为订阅者模式，在 `registerEventSubscribers()` 方法中注册 |
| **预期收益** | 统一代码风格，便于维护和热重载支持 |

**示例优化：**
```java
// 优化前（CatsPaw.java）
@EventHandler
public void onCreeperSpawn(CreatureSpawnEvent event) { ... }

// 优化后
@Override
public void registerEventSubscribers() {
    plugin.getEnchantmentManager().subscribeEvent(
        CreatureSpawnEvent.class,
        event -> onCreeperSpawn((CreatureSpawnEvent) event)
    );
}
```

---

### 1.2 提取重复的罗马数字转换方法

| 项目 | 详情 |
|------|------|
| **优先级** | 🔴 高 |
| **涉及文件** | 所有附魔类 |
| **当前问题** | 每个附魔类都实现了相同的 `getRomanNumeral()` 方法 |
| **优化方案** | 在 `CustomEnchantment` 基类中实现此方法，子类直接调用 |
| **预期收益** | 消除代码重复，提升可维护性 |

**示例优化：**
```java
// 在 CustomEnchantment.java 中添加
protected String getRomanNumeral(int number) {
    return switch (number) {
        case 1 -> "I";
        case 2 -> "II";
        case 3 -> "III";
        case 4 -> "IV";
        case 5 -> "V";
        default -> String.valueOf(number);
    };
}

// 子类直接调用即可，无需重复实现
```

---

## <a name="performance"></a>二、性能优化

### 2.1 全局调度器任务统一管理

| 项目 | 详情 |
|------|------|
| **优先级** | 🟡 中 |
| **涉及文件** | `Darkspeed.java`, `Clearsight.java`, `SonicBoom.java`, `Undermine.java` |
| **当前问题** | 每个附魔独立创建全局定时任务，任务管理分散 |
| **优化方案** | 创建 `TaskManager` 类统一管理所有定时任务 |
| **预期收益** | 提升性能，便于任务监控和管理 |

---

### 2.2 配置读取优化

| 项目 | 详情 |
|------|------|
| **优先级** | 🟡 中 |
| **涉及文件** | `Harvest.java` |
| **当前问题** | `Harvest.onEnable()` 直接读取原始配置而非缓存 |
| **优化方案** | 在 `ConfigManager` 中添加 `harvest.range` 配置项的缓存 |
| **预期收益** | 提升配置读取性能 |

**示例优化：**
```java
// 在 ConfigManager.loadConfig() 中添加
settings.put("harvest.range", config.getInt("enchantments.harvest.range", 2));

// 在 Harvest.java 中使用
harvestRange = configManager.getInt("harvest.range");
```

---

### 2.3 玩家数据缓存清理机制

| 项目 | 详情 |
|------|------|
| **优先级** | 🟡 中 |
| **涉及文件** | `Darkspeed.java` |
| **当前问题** | `playerSpeedLevels` 缓存没有清理机制，可能导致内存泄漏 |
| **优化方案** | 在玩家离线时清理相关缓存 |
| **预期收益** | 防止内存泄漏，提升资源管理效率 |

**示例优化：**
```java
// 在 Darkspeed.onDisable() 中添加
playerSpeedLevels.clear();

// 或在玩家离线事件中清理（需要注册事件）
public void onPlayerQuit(Player player) {
    playerSpeedLevels.remove(player.getUniqueId());
}
```

---

## <a name="architecture"></a>三、架构优化

### 3.1 事件订阅模式类型安全改进

| 项目 | 详情 |
|------|------|
| **优先级** | 🟢 低 |
| **涉及文件** | `EnchantmentManager.java` |
| **当前问题** | 事件订阅缺少泛型支持，需要强制类型转换 |
| **优化方案** | 添加泛型支持 |
| **预期收益** | 提升类型安全性，减少运行时错误 |

**示例优化：**
```java
// 优化前
public void subscribeEvent(Class<? extends Event> eventType, Consumer<Event> handler) { ... }

// 优化后
public <T extends Event> void subscribeEvent(Class<T> eventType, Consumer<T> handler) {
    eventSubscribers.computeIfAbsent(eventType, k -> new ArrayList<>())
        .add(event -> handler.accept(eventType.cast(event)));
}
```

---

### 3.2 附魔生命周期管理统一

| 项目 | 详情 |
|------|------|
| **优先级** | 🟡 中 |
| **涉及文件** | 所有附魔类 |
| **当前问题** | 部分附魔在 `onEnable()` 中注册事件，部分在 `registerEventSubscribers()` 中注册 |
| **优化方案** | 统一在 `registerEventSubscribers()` 中注册事件 |
| **预期收益** | 统一生命周期管理，便于维护 |

---

## <a name="code-quality"></a>四、代码质量改进

### 4.1 消除魔法数字

| 项目 | 详情 |
|------|------|
| **优先级** | 🟢 低 |
| **涉及文件** | `Harvest.java`, `CatsPaw.java`, `Nasus.java` |
| **当前问题** | 代码中存在魔法数字（如 `3000`、`40L`） |
| **优化方案** | 定义常量或从配置读取 |
| **预期收益** | 提升代码可读性和可配置性 |

**示例优化：**
```java
// 优化前
private static final long COOLDOWN_MS = 3000; // 3秒

// 优化后（如果适合配置化）
// 在 config.yml 中添加配置
// enchantments.harvest.cooldown: 3
// 在 ConfigManager 中添加缓存
// 在 Harvest.java 中读取
long cooldownMs = configManager.getLong("harvest.cooldown") * 1000L;
```

---

### 4.2 空值检查一致性

| 项目 | 详情 |
|------|------|
| **优先级** | 🟢 低 |
| **涉及文件** | 多个附魔类 |
| **当前问题** | 部分方法缺少空值检查 |
| **优化方案** | 添加统一的空值检查机制 |
| **预期收益** | 提升代码健壮性 |

---

## <a name="maintainability"></a>五、可维护性改进

### 5.1 配置验证增强

| 项目 | 详情 |
|------|------|
| **优先级** | 🟢 低 |
| **涉及文件** | `ConfigManager.java` |
| **当前问题** | `validateConfig()` 只验证了部分配置项 |
| **优化方案** | 增加更多配置项的合法性验证 |
| **预期收益** | 提升配置健壮性，减少运行时错误 |

---

### 5.2 日志级别统一

| 项目 | 详情 |
|------|------|
| **优先级** | 🟢 低 |
| **涉及文件** | 多个附魔类 |
| **当前问题** | 部分调试信息使用 `info` 级别 |
| **优化方案** | 统一使用 `fine` 级别用于调试日志 |
| **预期收益** | 统一日志规范，便于生产环境日志管理 |

**示例优化：**
```java
// 优化前
if (plugin.getConfigManager().getBoolean("debug")) {
    plugin.getLogger().info("[猫爪] 已启用");
}

// 优化后
if (plugin.getConfigManager().getBoolean("debug")) {
    plugin.getLogger().fine("[猫爪] 已启用");
}
```

---

## 📊 优化进度追踪

| 序号 | 优化项 | 状态 | 负责人 | 完成日期 |
|------|--------|------|--------|----------|
| 1 | 统一事件订阅模式 | ⏳ 待处理 | | |
| 2 | 提取罗马数字转换方法 | ⏳ 待处理 | | |
| 3 | 创建统一任务管理器 | ⏳ 待处理 | | |
| 4 | 配置读取优化 | ⏳ 待处理 | | |
| 5 | 玩家数据缓存清理 | ⏳ 待处理 | | |
| 6 | 事件订阅类型安全改进 | ⏳ 待处理 | | |
| 7 | 附魔生命周期管理统一 | ⏳ 待处理 | | |
| 8 | 消除魔法数字 | ⏳ 待处理 | | |
| 9 | 空值检查一致性 | ⏳ 待处理 | | |
| 10 | 配置验证增强 | ⏳ 待处理 | | |
| 11 | 日志级别统一 | ⏳ 待处理 | | |

---

## ✅ 优化完成标准

- [ ] 所有事件处理统一使用订阅者模式
- [ ] 无重复代码（DRY原则）
- [ ] 所有全局任务统一管理
- [ ] 配置读取使用缓存机制
- [ ] 无内存泄漏风险
- [ ] 代码风格统一
- [ ] 日志级别规范

---

> 📝 **备注**：建议按优先级顺序进行优化，先处理高优先级项，再逐步处理中低优先级项。

---

文件路径：`D:\software\YinwuEnchant-plugin\OPTIMIZATION_CHECKLIST.md`
