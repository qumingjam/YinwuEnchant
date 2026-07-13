package YinwuEnchant.enchantments;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class Clearsight extends CustomEnchantment {
    private final ConfigManager configManager;
    private ScheduledTask task;
    
    public Clearsight(YinwuEnchantments plugin) {
        super(plugin, "clearsight", "明目", 1, new Material[] {
            Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET,
            Material.IRON_HELMET, Material.GOLDEN_HELMET,
            Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
            Material.TURTLE_HELMET, Material.CARVED_PUMPKIN,
            Material.COPPER_HELMET // ✅ 添加铜制头盔
        });
        this.configManager = plugin.getConfigManager();
    }
    
    	@Override
    public Component displayName(int level) {
        return Component.text("明目 " + getRomanNumeral(level));
    }
    

    
    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("clearsight")) {
            return;
        }
        
        int interval = configManager.getInt("clearsight.check-interval");
        task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                
                player.getScheduler().run(plugin, (task) -> {
                    if (hasEnchantment(player.getInventory().getHelmet())) {
                        if (player.hasPotionEffect(PotionEffectType.DARKNESS)) {
                            player.removePotionEffect(PotionEffectType.DARKNESS);
                        }
                        if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
                            player.removePotionEffect(PotionEffectType.BLINDNESS);
                        }
                    }
                }, null);
            }
        }, 1L, interval);
    }
    
    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
    }
    
    @Override
    public void onTick() {
        // 不需要额外实现，已经在onEnable中设置了定时任务
    }
}
