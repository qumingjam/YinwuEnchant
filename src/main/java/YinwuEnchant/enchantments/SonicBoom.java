package YinwuEnchant.enchantments;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class SonicBoom extends CustomEnchantment {
    private final ConfigManager configManager;
    private ScheduledTask task;
    
    public SonicBoom(YinwuEnchantments plugin) {
        super(plugin, "sonic_boom", "音波爆裂", 1, new Material[] {
            Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE,
            Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
            Material.COPPER_CHESTPLATE // ✅ 添加铜制胸甲
        });
        this.configManager = plugin.getConfigManager();
    }
    
    public Component displayName(int level) {
        return Component.text("音波爆裂 " + getRomanNumeral(level));
    }
    

    
    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("sonic_boom")) {
            return;
        }
        
        int interval = configManager.getInt("sonic_boom.check-interval");
        task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead() || !player.isOnline()) continue;
                
                if (hasEnchantment(player.getInventory().getChestplate())) {
                    player.getScheduler().run(plugin, (task) -> extinguishNearbySoulCampfires(player), null);
                }
            }
        }, 1L, interval);
    }
    
    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
    }
    
    private void extinguishNearbySoulCampfires(Player player) {
        int range = configManager.getInt("sonic_boom.extinguish-range");
        Location playerLoc = player.getLocation();
        
        // 优化：只检查玩家上下一定范围内的方块（灵魂营火通常在玩家附近的地面上）
        int verticalRange = Math.min(range, 4); // 垂直范围限制为4格
        
        for (int x = -range; x <= range; x++) {
            for (int y = -verticalRange; y <= verticalRange; y++) {
                for (int z = -range; z <= range; z++) {
                    Location checkLoc = playerLoc.clone().add(x, y, z);
                    
                    // 检查区块是否已加载
                    if (!player.getWorld().isChunkLoaded(checkLoc.getBlockX() >> 4, checkLoc.getBlockZ() >> 4)) {
                        continue;
                    }
                    
                    Block block = checkLoc.getBlock();
                    
                    try {
                        // 检查是否是灵魂营火
                        if (block.getType() == Material.SOUL_CAMPFIRE) {
                            Lightable lightable = (Lightable) block.getBlockData();
                            if (lightable.isLit()) {
                                // 熄灭营火
                                lightable.setLit(false);
                                block.setBlockData(lightable);
                                
                                // 播放音效和粒子效果
                                player.getWorld().playSound(
                                    checkLoc,
                                    Sound.ENTITY_GENERIC_EXTINGUISH_FIRE,
                                    0.5f,
                                    1.0f
                                );
                                
                                player.getWorld().spawnParticle(
                                    Particle.LARGE_SMOKE,
                                    checkLoc.clone().add(0.5, 0.5, 0.5),
                                    10,
                                    0.3, 0.3, 0.3,
                                    0.02
                                );
                            }
                        }
                        // 检查是否是灵魂火
                        else if (block.getType() == Material.SOUL_FIRE) {
                            // 熄灭灵魂火（设置为空气）
                            block.setType(Material.AIR);
                            
                            // 播放音效和粒子效果
                            player.getWorld().playSound(
                                checkLoc,
                                Sound.ENTITY_GENERIC_EXTINGUISH_FIRE,
                                0.5f,
                                1.0f
                            );
                            
                            player.getWorld().spawnParticle(
                                Particle.LARGE_SMOKE,
                                checkLoc.clone().add(0.5, 0.5, 0.5),
                                10,
                                0.3, 0.3, 0.3,
                                0.02
                            );
                        }
                    } catch (Exception e) {
                        // 忽略单个方块的错误，继续处理其他方块
                        if (plugin.getConfigManager().getBoolean("debug")) {
                            plugin.getLogger().warning("Error processing block at " + checkLoc + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }
}
