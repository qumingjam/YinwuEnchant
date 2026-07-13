package YinwuEnchant.enchantments;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CatsPaw extends CustomEnchantment {
    private final ConfigManager configManager;
    
    // 配置参数
    private double rangeLevel1;
    private double rangeLevel2;
    private double rangeLevel3;
    private int checkInterval;
    
    // 追踪正在逃跑的苦力怕（防止重复处理）
    private final Set<UUID> fleeingCreepers = ConcurrentHashMap.newKeySet();
    
    public CatsPaw(YinwuEnchantments plugin) {
        super(plugin, "cats_paw", "猫爪", 3, new Material[] {
            Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS,
            Material.IRON_BOOTS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
            Material.COPPER_BOOTS // ✅ 添加铜制靴子
        });
        this.configManager = plugin.getConfigManager();
        loadConfig();
    }
    
    /**
     * 加载配置参数
     */
    private void loadConfig() {
        rangeLevel1 = configManager.getDouble("cats_paw.range-level-1");
        rangeLevel2 = configManager.getDouble("cats_paw.range-level-2");
        rangeLevel3 = configManager.getDouble("cats_paw.range-level-3");
        checkInterval = configManager.getInt("cats_paw.check-interval");
    }
    
    @Override
    public Component displayName(int level) {
        return Component.text("猫爪");
    }
    
    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("cats_paw")) {
            return;
        }
        
        // ✅ 重载时重新读取配置（支持热重载）
        loadConfig();
        
        // ⚠️ 事件注册由 registerEventSubscribers() 统一管理，不再单独注册 Bukkit 监听器
        
        // 启动定时任务，模拟原版猫的AI检测频率（每checkInterval刻）
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                
                ItemStack boots = player.getInventory().getBoots();
                if (boots == null || !hasEnchantment(boots)) continue;
                
                int level = getEnchantmentLevel(boots);
                if (level <= 0) continue;
                
                // 在玩家所在区域线程执行
                player.getScheduler().run(plugin, (t) -> makeCreepersFlee(player, level), null);
            }
        }, 1L, checkInterval);
        
        // ✅ 日志规范化：只在调试模式下输出
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[猫爪] 已启用智能恐吓系统（检测间隔: " + checkInterval + "刻）");
        }
    }
    
    @Override
    public void onDisable() {
        // 清理所有追踪数据
        fleeingCreepers.clear();
        
        // ✅ 日志规范化：只在调试模式下输出
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[猫爪] 已禁用");
        }
    }
    
    @Override
    public void registerEventSubscribers() {
        // 苦力怕生成事件
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.entity.CreatureSpawnEvent.class,
            event -> {
                if (event.getEntity() instanceof Creeper creeper) {
                    // 新苦力怕生成时，立即检查附近是否有猫爪玩家
                    creeper.getScheduler().runDelayed(plugin, (task) -> checkNearbyPlayers(creeper), null, 5L);
                }
            }
        );
        
        // 玩家装备变化事件
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.player.PlayerItemBreakEvent.class,
            event -> {
                if (event.getBrokenItem().getType().name().endsWith("_BOOTS")) {
                    // 使用玩家调度器延迟处理，确保在正确的线程
                    event.getPlayer().getScheduler().runDelayed(plugin, (task) -> updateNearbyCreepers(event.getPlayer().getLocation()), null, 1L);
                }
            }
        );
        
        // 玩家离线事件
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.player.PlayerQuitEvent.class,
            event -> {
                // ✅ 修复：在玩家所在区域线程执行清理，避免 Folia 线程安全检查失败
                org.bukkit.entity.Player player = event.getPlayer();
                plugin.getServer().getRegionScheduler().run(plugin, player.getLocation(), (task) -> updateNearbyCreepers(player.getLocation()));
            }
        );
    }
    
    /**
     * 让范围内的苦力怕逃离玩家（模拟原版猫的恐吓效果）
     * 注意：此方法必须在玩家所在区域线程调用
     */
    private void makeCreepersFlee(Player player, int level) {
        Location playerLoc = player.getLocation();
        double range = getRangeByLevel(level);
        
        // 获取范围内的苦力怕
        List<Creeper> creepers = playerLoc.getWorld().getNearbyEntities(
            playerLoc, range, range, range,
            entity -> entity instanceof Creeper && entity.isValid()
        ).stream()
            .map(entity -> (Creeper) entity)
            .toList();
        
        for (Creeper creeper : creepers) {
            if (fleeingCreepers.contains(creeper.getUniqueId())) {
                continue; // 已经在逃跑中，跳过
            }
            
            // 标记为逃跑状态
            fleeingCreepers.add(creeper.getUniqueId());
            
            // ⚠️ 关键修复：每个苦力怕的操作必须在其所在区域线程执行
            // ✅ 修复：使用 run() 替代 runDelayed(0)，因为 Folia 不允许延迟 <= 0
            creeper.getScheduler().run(plugin, (task) -> {
                // 计算逃跑方向（远离玩家）
                org.bukkit.util.Vector direction = creeper.getLocation().toVector()
                    .subtract(playerLoc.toVector());
                
                // 归一化并设置速度（模拟惊吓反应）
                double speed = 0.8 + (level * 0.2); // 每级增加速度
                direction.normalize().multiply(speed);
                direction.setY(0.4); // 轻微向上跳起
                
                // 应用速度（在苦力怕的区域线程）
                creeper.setVelocity(direction);
                
                // 播放猫嘶吼音效（在苦力怕的区域线程）
                creeper.getLocation().getWorld().playSound(
                    creeper.getLocation(),
                    Sound.ENTITY_CAT_HISS,
                    1.0f,
                    1.0f
                );
                
                // 生成心碎粒子（在苦力怕的区域线程）
                creeper.getLocation().getWorld().spawnParticle(
                    Particle.HEART,
                    creeper.getLocation().add(0, 1, 0),
                    3,
                    0.3, 0.3, 0.3,
                    0.05
                );
            }, null);
            
            // 2秒后移除逃跑标记（允许再次被恐吓）
            creeper.getScheduler().runDelayed(plugin, (task) -> fleeingCreepers.remove(creeper.getUniqueId()), null, 40L);
        }
        
        if (!creepers.isEmpty() && plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[猫爪调试] 玩家 " + player.getName() + 
                " 恐吓了 " + creepers.size() + " 只苦力怕（等级" + level + "）");
        }
    }
    
    /**
     * 检查苦力怕附近是否有猫爪玩家
     * 注意：此方法必须在苦力怕所在区域线程调用
     */
    private void checkNearbyPlayers(Creeper creeper) {
        Location creeperLoc = creeper.getLocation();
        
        // 查找附近的玩家
        List<Player> nearbyPlayers = creeperLoc.getWorld().getNearbyEntities(
            creeperLoc, 16, 16, 16,
            entity -> entity instanceof Player && entity.isValid()
        ).stream()
            .map(entity -> (Player) entity)
            .toList();
        
        for (Player player : nearbyPlayers) {
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && hasEnchantment(boots)) {
                int level = getEnchantmentLevel(boots);
                if (level > 0) {
                    // ⚠️ 关键修复：切换到玩家所在区域线程执行恐吓逻辑
                    player.getScheduler().run(plugin, (task) -> makeCreepersFlee(player, level), null);
                    break;
                }
            }
        }
    }
    
    /**
     * 更新附近苦力怕的状态
     * 注意：此方法可能跨区域操作，需要谨慎处理
     */
    private void updateNearbyCreepers(Location location) {
        // 先收集所有需要更新的苦力怕
        List<Creeper> creepersToUpdate = location.getWorld().getNearbyEntities(
            location, 16, 16, 16,
            entity -> entity instanceof Creeper
        ).stream()
            .map(entity -> (Creeper) entity)
            .toList();
        
        // 对每个苦力怕，在其所在区域线程执行更新
        for (Creeper creeper : creepersToUpdate) {
            // 清除逃跑标记（线程安全操作）
            fleeingCreepers.remove(creeper.getUniqueId());
            
            // 在苦力怕的区域线程中延迟检查
            creeper.getScheduler().runDelayed(plugin, (task) -> checkNearbyPlayers(creeper), null, 10L);
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
            default -> 5.0;
        };
    }
    

}
