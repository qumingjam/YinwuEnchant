package YinwuEnchant.enchantments;

import YinwuEnchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public abstract class CustomEnchantment {
    protected final YinwuEnchantments plugin;
    protected final String id;
    protected final String displayName;
    protected final int maxLevel;
    protected final Material[] applicableItems;
    protected final NamespacedKey enchantmentKey;
    
    public CustomEnchantment(YinwuEnchantments plugin, String id, String displayName, int maxLevel, Material[] applicableItems) {
        this.plugin = plugin;
        this.id = id;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.applicableItems = applicableItems;
        this.enchantmentKey = new NamespacedKey(plugin, "enchantment_" + id.replace("-", "_"));
    }
    
    public String getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getMaxLevel() {
        return maxLevel;
    }
    
    public Material[] getApplicableItems() {
        return applicableItems;
    }
    
    public boolean canApplyTo(ItemStack item) {
        if (item == null) return false;
        Material material = item.getType();
        for (Material applicable : applicableItems) {
            if (applicable == material) return true;
        }
        return false;
    }
    
    /**
     * 检测物品是否拥有自定义附魔
     * @param item 要检查的物品
     * @return 是否拥有附魔
     */
    public boolean hasEnchantment(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return getEnchantmentLevel(item) > 0;
    }
    
    public int getEnchantmentLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        
        Integer level = meta.getPersistentDataContainer().get(
            enchantmentKey,
            PersistentDataType.INTEGER
        );
        return level != null ? level : 0;
    }
    
    public ItemStack applyEnchantment(ItemStack item, int level) {
        if (item == null || level <= 0 || level > maxLevel) return item;
        if (!canApplyTo(item)) return item;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        meta.getPersistentDataContainer().set(
            enchantmentKey,
            PersistentDataType.INTEGER,
            level
        );
        
        // ✅ 添加附魔光效（Paper 1.21+ API，不添加虚拟附魔，不影响原版附魔 lore）
        meta.setEnchantmentGlintOverride(true);
        
        List<String> lore = meta.getLore();
        // 1级附魔不显示等级，多级附魔显示罗马数字
        String loreLine = maxLevel == 1 ? "§7" + displayName : "§7" + displayName + " " + getRomanNumeral(level);
        
        // ⚠️ 先移除该附魔可能存在的所有旧 lore 行（避免重复应用不同等级时 multiple 行堆积）
        if (lore != null) {
            lore.removeIf(line -> line.contains("§7" + displayName));
            if (lore.isEmpty()) {
                lore = null;
            }
        }
        
        if (lore == null) {
            meta.setLore(List.of(loreLine));
        } else {
            lore.addFirst(loreLine);
            meta.setLore(lore);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    @SuppressWarnings("unused")
    public ItemStack removeEnchantment(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        meta.getPersistentDataContainer().remove(enchantmentKey);
        
        List<String> lore = meta.getLore();
        if (lore != null) {
            lore.removeIf(line -> line.contains(displayName));
            if (lore.isEmpty()) {
                meta.setLore(null);
            } else {
                meta.setLore(lore);
            }
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * 将数字转换为罗马数字
     * @param number 要转换的数字（1-5）
     * @return 罗马数字字符串
     */
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
    
    // 抽象方法：附魔的具体效果逻辑
    public abstract void onEnable();
    public abstract void onDisable();
    
    /**
     * 注册事件订阅（可选）
     * 子类可以重写此方法来订阅特定事件
     */
    public void registerEventSubscribers() {
        // 默认空实现，子类可选择性重写
    }
    
    // 抽象方法：获取附魔显示名称（Adventure API）
    public abstract Component displayName(int level);
    
    // 可选的生命周期方法
    @SuppressWarnings("unused")
    public void onTick() {}
    @SuppressWarnings("unused")
    public void onPlayerMove(org.bukkit.entity.Player player) {}
    @SuppressWarnings("unused")
    public void onPlayerDamage(org.bukkit.entity.Player player, org.bukkit.event.entity.EntityDamageEvent event) {}
    @SuppressWarnings("unused")
    public void onBlockBreak(org.bukkit.entity.Player player, org.bukkit.event.block.BlockBreakEvent event) {}
    @SuppressWarnings("unused")
    public void onPlayerInteract(org.bukkit.entity.Player player, org.bukkit.event.player.PlayerInteractEvent event) {}
    @SuppressWarnings("unused")
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {}
}