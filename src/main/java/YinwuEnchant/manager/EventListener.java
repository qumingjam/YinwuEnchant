package YinwuEnchant.manager;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.enchantments.CustomEnchantment;
import YinwuEnchant.enchantments.Darkspeed;
import YinwuEnchant.enchantments.ShriekerSense;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {
    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;
    
    public EventListener(YinwuEnchantments plugin, EnchantmentManager enchantmentManager) {
        this.plugin = plugin;
        this.enchantmentManager = enchantmentManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检查位置是否真正改变
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        // ✅ 使用事件订阅者模式，只调用订阅了该事件的附魔
        enchantmentManager.dispatchEvent(event);
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // ✅ 使用事件订阅者模式
        enchantmentManager.dispatchEvent(event);
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // ✅ 使用事件订阅者模式
        enchantmentManager.dispatchEvent(event);
    }
    
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // ✅ 使用事件订阅者模式
        enchantmentManager.dispatchEvent(event);
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // ✅ 使用事件订阅者模式
        enchantmentManager.dispatchEvent(event);
    }
    
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        // ✅ 分发给 PhantomProtection 订阅
        enchantmentManager.dispatchEvent(event);
    }
    
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // ✅ 分发给 CatsPaw/Nasus 订阅
        enchantmentManager.dispatchEvent(event);
    }
    
    @EventHandler
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        // ✅ 分发给 CatsPaw/Nasus 订阅
        enchantmentManager.dispatchEvent(event);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家离线时清理 ShriekerSense 的 TextDisplay
        CustomEnchantment shriekerSense = enchantmentManager.getEnchantment("shrieker_sense");
        if (shriekerSense instanceof ShriekerSense) {
            ((ShriekerSense) shriekerSense).cleanupPlayer(event.getPlayer().getUniqueId());
        }
        
        // ✅ 玩家离线时清理 Darkspeed 的速度缓存（防止内存泄漏）
        CustomEnchantment darkspeed = enchantmentManager.getEnchantment("darkspeed");
        if (darkspeed instanceof Darkspeed) {
            ((Darkspeed) darkspeed).cleanupPlayerCache(event.getPlayer().getUniqueId());
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 检查是否是附魔列表 GUI
        if (event.getView().getTitle().contains("Yinwu附魔列表")) {
            event.setCancelled(true); // 取消点击事件，防止物品被取出
        }
    }
}
