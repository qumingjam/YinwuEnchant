package YinwuEnchant.enchantments;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PhantomProtection（幻影守护者）附魔
 * 功能：防止幻翼攻击穿戴此附魔的玩家
 * 实现原理：
 * 1. 使用线程安全的缓存记录受保护玩家（在玩家区域线程刷新）
 * 2. 每 2 秒主动扫描受保护玩家附近的幻翼，直接清除其目标
 * 3. 避免依赖 EntityTargetEvent（Luminol/Folia 下不可靠）
 */
public class PhantomProtection extends CustomEnchantment {
    private final ConfigManager configManager;
    
    private boolean enabled;
    
    // 线程安全的保护缓存：在玩家区域线程刷新，扫描线程只读查询
    private final Set<UUID> protectionCache = ConcurrentHashMap.newKeySet();
    private ScheduledTask scanTask;
    private ScheduledTask refreshTask;
    
    // 扫描半径
    private static final double SCAN_RANGE = 64.0;
    
    public PhantomProtection(YinwuEnchantments plugin) {
        super(plugin, "phantom", "幻影守护者", 1, new Material[] {
            Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE,
            Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
            Material.ELYTRA,
            Material.COPPER_CHESTPLATE
        });
        this.configManager = plugin.getConfigManager();
        loadConfig();
    }
    
    private void loadConfig() {
        enabled = configManager.isEnchantmentEnabled("phantom");
    }
    
    @Override
    public Component displayName(int level) {
        return Component.text("幻影守护者");
    }
    
    @Override
    public void onEnable() {
        if (!enabled) {
            return;
        }
        
        // 启动定期缓存刷新（每 5 秒保底）
        refreshTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(plugin, t -> refreshPlayerCache(player), null);
            }
        }, 1L, 100L);
        
        // 启动幻翼扫描任务（每 2 秒 = 40 tick 扫描一次）
        scanTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!protectionCache.contains(player.getUniqueId())) continue;
                if (player.isDead() || !player.isOnline()) continue;
                
                player.getScheduler().run(plugin, t -> repelPhantoms(player), null);
            }
        }, 1L, 40L);
        
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[幻影守护者] 已启用幻翼防护系统");
        }
    }
    
    @Override
    public void onDisable() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        protectionCache.clear();
        
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[幻影守护者] 已禁用");
        }
    }
    
    @Override
    public void registerEventSubscribers() {
        // 不需要事件订阅，使用主动扫描机制
    }
    
    /**
     * 驱散玩家附近的幻翼
     * 必须在玩家区域线程调用
     */
    private void repelPhantoms(Player player) {
        Location playerLoc = player.getLocation();
        
        for (Entity entity : playerLoc.getWorld().getNearbyEntities(
                playerLoc, SCAN_RANGE, SCAN_RANGE, SCAN_RANGE,
                e -> e instanceof Phantom && e.isValid())) {
            
            Phantom phantom = (Phantom) entity;
            final UUID playerUUID = player.getUniqueId();
            
            // 切换到幻翼所在区域线程检查并清除目标
            phantom.getScheduler().run(plugin, task -> {
                LivingEntity target = phantom.getTarget();
                if (target != null && target.getUniqueId().equals(playerUUID)) {
                    phantom.setTarget(null);
                    
                    if (plugin.getConfigManager().getBoolean("debug")) {
                        plugin.getLogger().fine("[幻影守护者] 幻翼 #" + phantom.getUniqueId() + 
                            " 目标已被清除");
                    }
                }
            }, null);
        }
    }
    
    /**
     * 在玩家区域线程刷新保护缓存
     * @param player 要检查的玩家
     */
    private void refreshPlayerCache(Player player) {
        if (!player.isOnline()) {
            protectionCache.remove(player.getUniqueId());
            return;
        }
        ItemStack chestplate = player.getInventory().getChestplate();
        boolean hasProtection = chestplate != null && hasEnchantment(chestplate);
        if (hasProtection) {
            protectionCache.add(player.getUniqueId());
        } else {
            protectionCache.remove(player.getUniqueId());
        }
    }
}
