package YinwuEnchant.enchantments;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class Undermine extends CustomEnchantment {
    private final ConfigManager configManager;
    private ScheduledTask checkTask;
    
    public Undermine(YinwuEnchantments plugin) {
        super(plugin, "undermine", "深层矿工", 5, new Material[] {
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
            Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            Material.WOODEN_AXE, Material.STONE_AXE,
            Material.IRON_AXE, Material.GOLDEN_AXE,
            Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL,
            Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL,
            Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL
        });
        this.configManager = plugin.getConfigManager();
    }
    
    public Component displayName(int level) {
        return Component.text("深层矿工 " + getRomanNumeral(level));
    }
    

    
    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("undermine")) {
            return;
        }
        
        int interval = configManager.getInt("undermine.check-interval");
        checkTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead() || !player.isOnline()) continue;
                
                // 在玩家所在区域执行效果应用
                player.getScheduler().run(plugin, (t) -> checkAndApplyEffect(player), null);
            }
        }, 1L, interval);
    }
    
    /**
     * ✅ 注册事件订阅者（支持 reload）
     */
    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.block.BlockBreakEvent.class,
            event -> {
                org.bukkit.event.block.BlockBreakEvent blockEvent = (org.bukkit.event.block.BlockBreakEvent) event;
                onBlockBreak(blockEvent.getPlayer(), blockEvent);
            }
        );
    }
    
    @Override
    public void onDisable() {
        if (checkTask != null) {
            checkTask.cancel();
        }
    }
    
    private void checkAndApplyEffect(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        
        if (hasEnchantment(mainHand)) {
            int level = getEnchantmentLevel(mainHand);
            if (level > 0) {
                // 检查触发条件
                if (isTriggerConditionMet(player)) {
                    applyMiningSpeedEffect(player, level);
                }
            }
        }
    }
    
    private boolean isTriggerConditionMet(Player player) {
        int triggerYMax = configManager.getInt("undermine.trigger-y-max");
        int triggerLightMax = configManager.getInt("undermine.trigger-light-max");
        
        return player.getLocation().getY() <= triggerYMax &&
               player.getLocation().getBlock().getLightLevel() <= triggerLightMax;
    }
    
    private void applyMiningSpeedEffect(Player player, int level) {
        // 转换为急迫等级（1级=急迫I，2级=急迫II，3级=急迫III）
        int effectLevel = Math.min(level - 1, 2); // Undermine I-II-III -> Haste I-II-III
        
        // 只在效果不存在或即将结束时才重新添加，避免频繁刷新
        PotionEffect existingEffect = player.getPotionEffect(PotionEffectType.HASTE);
        if (existingEffect == null || existingEffect.getDuration() <= 40) { // 剩余时间少于2秒时刷新
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.HASTE,
                200, // 10秒持续时间，减少刷新频率
                effectLevel,
                true,  // 隐藏粒子效果
                false, // 不显示图标
                false  // 固定持续时间
            ));
        }
    }
    
    @Override
    public void onBlockBreak(org.bukkit.entity.Player player, org.bukkit.event.block.BlockBreakEvent event) {
        if (!configManager.isEnchantmentEnabled("undermine")) return;
        
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        if (hasEnchantment(tool)) {
            int level = getEnchantmentLevel(tool);
            if (level > 0 && isTriggerConditionMet(player)) {
                // 增加掉落物（可选）
                // event.setDropItems(false);
                // 这里可以添加自定义掉落逻辑
                
                // 播放效果
                player.getWorld().playSound(
                    player.getLocation(),
                    org.bukkit.Sound.BLOCK_STONE_BREAK,
                    0.5f,
                    0.8f + (level * 0.1f)
                );
            }
        }
    }
}
