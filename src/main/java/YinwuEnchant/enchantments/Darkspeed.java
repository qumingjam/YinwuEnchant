package YinwuEnchant.enchantments;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class Darkspeed extends CustomEnchantment {
    private final ConfigManager configManager;
    private ScheduledTask particleTask;
    private ScheduledTask soundTask;
    // 缓存玩家的速度等级，避免频繁更新
    private final java.util.Map<java.util.UUID, Integer> playerSpeedLevels = new java.util.HashMap<>();
    
    public Darkspeed(YinwuEnchantments plugin) {
        super(plugin, "darkspeed", "黑暗行者", 3, new Material[] {
            Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS,
            Material.IRON_BOOTS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
            Material.COPPER_BOOTS // ✅ 添加铜制靴子
        });
        this.configManager = plugin.getConfigManager();
    }
    
    public Component displayName(int level) {
        return Component.text("黑暗行者 " + getRomanNumeral(level));
    }
    

    
    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("darkspeed")) {
            return;
        }
        
        int particleInterval = configManager.getInt("darkspeed.particle-interval");
        int soundInterval = configManager.getInt("darkspeed.sound-interval");
        
        // 粒子效果任务
        particleTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead() || !player.isOnline()) continue;
                
                if (isInDarkness(player) && hasEnchantment(player.getInventory().getBoots())) {
                    int level = getEnchantmentLevel(player.getInventory().getBoots());
                    if (level > 0) {
                        player.getScheduler().run(plugin, (task) -> spawnParticles(player, level), null);
                    }
                }
            }
        }, 1L, particleInterval);
        
        // 音效任务
        soundTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead() || !player.isOnline()) continue;
                
                if (isInDarkness(player) && hasEnchantment(player.getInventory().getBoots())) {
                    int level = getEnchantmentLevel(player.getInventory().getBoots());
                    if (level > 0) {
                        player.getScheduler().run(plugin, (task) -> playSound(player, level), null);
                    }
                }
            }
        }, 1L, soundInterval);
        
        // 速度效果在onPlayerMove中处理（更高效）
    }
    
    @Override
    public void onDisable() {
        if (particleTask != null) {
            particleTask.cancel();
        }
        if (soundTask != null) {
            soundTask.cancel();
        }
        // ✅ 清理缓存，防止内存泄漏
        playerSpeedLevels.clear();
    }
    
    /**
     * 清理指定玩家的缓存（用于玩家离线时）
     * @param playerId 玩家UUID
     */
    public void cleanupPlayerCache(java.util.UUID playerId) {
        playerSpeedLevels.remove(playerId);
    }
    
    @Override
    public void onPlayerMove(org.bukkit.entity.Player player) {
        if (!configManager.isEnchantmentEnabled("darkspeed")) return;
        
        if (player.isDead() || !player.isOnline()) return;
        
        if (isInDarkness(player) && hasEnchantment(player.getInventory().getBoots())) {
            int level = getEnchantmentLevel(player.getInventory().getBoots());
            if (level > 0) {
                // 检查是否需要更新效果（只在等级变化时更新）
                Integer cachedLevel = playerSpeedLevels.get(player.getUniqueId());
                if (cachedLevel == null || cachedLevel != level) {
                    double speedPerLevel = configManager.getDouble("darkspeed.speed-per-level");
                    double speedAmount = speedPerLevel * level;
                    
                    // 给予速度效果（持续5秒，防止突然移除）
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, 
                        100, // 5秒
                        (int) Math.min(speedAmount * 10, 4), // 放大倍数，限制等级
                        true, 
                        false, 
                        false
                    ));
                    
                    // 更新缓存
                    playerSpeedLevels.put(player.getUniqueId(), level);
                }
            }
        } else {
            // 不在黑暗中或没有附魔，清除缓存
            playerSpeedLevels.remove(player.getUniqueId());
        }
    }
    
    private boolean isInDarkness(Player player) {
        int darkLightLevel = configManager.getInt("darkspeed.dark-light-level");
        return player.getLocation().getBlock().getLightLevel() <= darkLightLevel;
    }
    
    private void spawnParticles(Player player, int level) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        
        String particleTypeStr = configManager.getString("darkspeed.particle-type");
        Particle particle;
        try {
            particle = Particle.valueOf(particleTypeStr);
        } catch (IllegalArgumentException e) {
            particle = Particle.SOUL_FIRE_FLAME;
        }
        
        int particleCount = level * 3;
        double offsetX = 0.5;
        double offsetY = 0.2;
        double offsetZ = 0.5;
        
        world.spawnParticle(
            particle,
            loc.clone().add(0, 0.5, 0),
            particleCount,
            offsetX, offsetY, offsetZ,
            0.1
        );
    }
    
    private void playSound(Player player, int level) {
        String soundTypeStr = configManager.getString("darkspeed.sound-type");
        Sound sound;
        try {
            // 使用 NamespacedKey 从 Registry 获取声音
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(soundTypeStr.toLowerCase().replace("_", "-"));
            sound = org.bukkit.Registry.SOUNDS.get(key);
            if (sound == null) {
                // 如果找不到，静默使用默认音效
                sound = Sound.BLOCK_SOUL_SAND_STEP;
            }
        } catch (Exception e) {
            // 出错时静默使用默认音效
            sound = Sound.BLOCK_SOUL_SAND_STEP;
        }
        
        float volume = 0.5f * level;
        float pitch = 0.8f + (level * 0.1f);
        
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
