package YinwuEnchant.enchantments;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Nasus extends CustomEnchantment {
    private final ConfigManager configManager;
    
    // 配置参数
    private double rangeLevel1;
    private double rangeLevel2;
    private double rangeLevel3;
    private int checkInterval;
    
    // 追踪正在逃跑的骷髅（防止重复处理）
    private final Set<UUID> fleeingSkeletons = ConcurrentHashMap.newKeySet();
    
    public Nasus(YinwuEnchantments plugin) {
        super(plugin, "nasus", "狗头", 3, new Material[] {
            Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET,
            Material.IRON_HELMET, Material.GOLDEN_HELMET,
            Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
            Material.TURTLE_HELMET,
            Material.COPPER_HELMET // ✅ 添加铜制头盔
        });
        this.configManager = plugin.getConfigManager();
        loadConfig();
    }
    
    /**
     * 加载配置参数
     */
    private void loadConfig() {
        rangeLevel1 = configManager.getDouble("nasus.range-level-1");
        rangeLevel2 = configManager.getDouble("nasus.range-level-2");
        rangeLevel3 = configManager.getDouble("nasus.range-level-3");
        checkInterval = configManager.getInt("nasus.check-interval");
    }
    
    @Override
    public Component displayName(int level) {
        return Component.text("狗头");
    }
    
    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("nasus")) {
            return;
        }
        
        // ✅ 重载时重新读取配置（支持热重载）
        loadConfig();
        
        // ⚠️ 事件注册由 registerEventSubscribers() 统一管理，不再单独注册 Bukkit 监听器
        
        // 启动定时任务，模拟原版狼的AI检测频率（每checkInterval刻）
        // ✅ Folia 兼容：初始延迟为 1L，不使用 0
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                
                ItemStack helmet = player.getInventory().getHelmet();
                if (helmet == null || !hasEnchantment(helmet)) continue;
                
                int level = getEnchantmentLevel(helmet);
                if (level <= 0) continue;
                
                // 在玩家所在区域线程执行
                player.getScheduler().run(plugin, (t) -> makeSkeletonsFlee(player, level), null);
            }
        }, 1L, checkInterval);
        
        // ✅ 日志规范化：只在调试模式下输出
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[狗头] 已启用智能恐吓系统（检测间隔: " + checkInterval + "刻）");  // ✅ 使用 fine 级别
        }
    }
    
    @Override
    public void onDisable() {
        // 清理所有追踪数据
        fleeingSkeletons.clear();
        
        // ✅ 日志规范化：只在调试模式下输出
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[狗头] 已禁用");  // ✅ 使用 fine 级别
        }
    }
    
    @Override
    public void registerEventSubscribers() {
        // 骷髅生成事件
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.entity.CreatureSpawnEvent.class,
            event -> {
                if (event.getEntity() instanceof AbstractSkeleton skeleton) {
                    // 新骷髅生成时，立即检查附近是否有狗头玩家
                    skeleton.getScheduler().runDelayed(plugin, task -> checkNearbyPlayers(skeleton), null, 5L);
                }
            }
        );
        
        // 玩家装备变化事件
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.player.PlayerItemBreakEvent.class,
            event -> {
                if (event.getBrokenItem().getType().name().endsWith("_HELMET") || 
                    event.getBrokenItem().getType() == Material.TURTLE_HELMET) {
                    // 头盔损坏时，触发附近骷髅重新评估
                    event.getPlayer().getScheduler().runDelayed(plugin, task -> updateNearbySkeletons(event.getPlayer().getLocation()), null, 1L);
                }
            }
        );
        
        // 玩家离线事件
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.player.PlayerQuitEvent.class,
            event -> {
                // ✅ 修复：在玩家所在区域线程执行清理，避免 Folia 线程安全检查失败
                org.bukkit.entity.Player player = event.getPlayer();
                plugin.getServer().getRegionScheduler().run(plugin, player.getLocation(), task -> updateNearbySkeletons(player.getLocation()));
            }
        );
    }
    
    /**
     * 让范围内的骷髅逃离玩家（模拟原版狼的恐吓效果）
     * 注意：此方法必须在玩家所在区域线程调用
     */
    private void makeSkeletonsFlee(Player player, int level) {
        Location playerLoc = player.getLocation();
        double range = getRangeByLevel(level);
        
        // 获取范围内的骷髅类怪物（骷髅、凋灵骷髅、流浪者、沼骸、焦骸）
        List<AbstractSkeleton> skeletons = playerLoc.getWorld().getNearbyEntities(
            playerLoc, range, range, range,
            entity -> entity instanceof AbstractSkeleton && entity.isValid()
        ).stream()
            .map(entity -> (AbstractSkeleton) entity)
            .toList();
        
        for (AbstractSkeleton skeleton : skeletons) {
            if (fleeingSkeletons.contains(skeleton.getUniqueId())) {
                continue; // 已经在逃跑中，跳过
            }
            
            // 标记为逃跑状态
            fleeingSkeletons.add(skeleton.getUniqueId());
            
            // ⚠️ 关键修复：每个骷髅的操作必须在其所在区域线程执行
            // ✅ 修复：使用 run() 替代 runDelayed(0)，因为 Folia 不允许延迟 <= 0
            skeleton.getScheduler().run(plugin, (task) -> {
                // 计算逃跑方向（远离玩家）
                org.bukkit.util.Vector direction = skeleton.getLocation().toVector()
                    .subtract(playerLoc.toVector());
                
                // 归一化并设置速度（模拟惊吓反应）
                double speed = 0.8 + (level * 0.2); // 每级增加速度
                direction.normalize().multiply(speed);
                direction.setY(0.4); // 轻微向上跳起
                
                // 应用速度（在骷髅的区域线程）
                skeleton.setVelocity(direction);
                
                // 播放狼嚎音效（在骷髅的区域线程）
                skeleton.getLocation().getWorld().playSound(
                    skeleton.getLocation(),
                    Sound.ENTITY_WOLF_GROWL,
                    1.0f,
                    1.0f
                );
                
                // 生成心碎粒子（表示害怕）
                skeleton.getLocation().getWorld().spawnParticle(
                    Particle.HEART,
                    skeleton.getLocation().add(0, 1, 0),
                    3,
                    0.3, 0.3, 0.3,
                    0.05
                );
            }, null);
            
            // 2秒后移除逃跑标记（允许再次被恐吓）
            skeleton.getScheduler().runDelayed(plugin, task -> fleeingSkeletons.remove(skeleton.getUniqueId()), null, 40L);
        }
        
        if (!skeletons.isEmpty() && plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[狗头调试] 玩家 " + player.getName() + 
                " 恐吓了 " + skeletons.size() + " 只骷髅类怪物（等级" + level + "）");  // ✅ 使用 fine 级别
        }
    }
    
    /**
     * 检查骷髅附近是否有狗头玩家
     * 注意：此方法必须在骷髅所在区域线程调用
     */
    private void checkNearbyPlayers(AbstractSkeleton skeleton) {
        Location skeletonLoc = skeleton.getLocation();
        
        // 查找附近的玩家
        List<Player> nearbyPlayers = skeletonLoc.getWorld().getNearbyEntities(
            skeletonLoc, 16, 16, 16,
            entity -> entity instanceof Player && entity.isValid()
        ).stream()
            .map(entity -> (Player) entity)
            .toList();
        
        for (Player player : nearbyPlayers) {
            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet != null && hasEnchantment(helmet)) {
                int level = getEnchantmentLevel(helmet);
                if (level > 0) {
                    // ⚠️ 关键修复：切换到玩家所在区域线程执行恐吓逻辑
                    player.getScheduler().run(plugin, (task) -> makeSkeletonsFlee(player, level), null);
                    break;
                }
            }
        }
    }
    
    /**
     * 更新附近骷髅的状态
     * 注意：此方法可能跨区域操作，需要谨慎处理
     */
    private void updateNearbySkeletons(Location location) {
        // 先收集所有需要更新的骷髅
        List<AbstractSkeleton> skeletonsToUpdate = location.getWorld().getNearbyEntities(
            location, 16, 16, 16,
            entity -> entity instanceof AbstractSkeleton
        ).stream()
            .map(entity -> (AbstractSkeleton) entity)
            .toList();
        
        // 对每个骷髅，在其所在区域线程执行更新
        for (AbstractSkeleton skeleton : skeletonsToUpdate) {
            // 清除逃跑标记（线程安全操作）
            fleeingSkeletons.remove(skeleton.getUniqueId());
            
            // 在骷髅的区域线程中延迟检查
            skeleton.getScheduler().runDelayed(plugin, task -> checkNearbyPlayers(skeleton), null, 10L);
        }
    }
    
    /**
     * 根据等级获取检测范围
     */
    private double getRangeByLevel(int level) {
        return switch (level) {
            case 1 -> rangeLevel1;
            case 2 -> rangeLevel2;
            case 3 -> rangeLevel3;
            default -> 10.0;
        };
    }
}
