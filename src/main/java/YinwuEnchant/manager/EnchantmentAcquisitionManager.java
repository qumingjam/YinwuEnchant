package YinwuEnchant.manager;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.enchantments.CustomEnchantment;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 附魔获取管理器 - 处理创造模式物品栏、附魔台等获取方式
 */
public class EnchantmentAcquisitionManager implements Listener {
    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;
    
    public EnchantmentAcquisitionManager(YinwuEnchantments plugin, EnchantmentManager enchantmentManager) {
        this.plugin = plugin;
        this.enchantmentManager = enchantmentManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * ✅ 钓鱼获取丰收附魔（纯插件实现）
     */
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (!plugin.getConfigManager().getBoolean("acquisition.fishing-harvest-enabled")) {
            return;
        }
        
        // 只处理钓到物品的情况
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        
        Random random = new Random();
        double harvestChance = plugin.getConfigManager().getRawConfig().getDouble("acquisition.harvest-fish-chance", 0.02);
        
        if (random.nextDouble() < harvestChance) {
            // 创建丰收附魔书
            ItemStack book = createEnchantedBook("harvest", 1);
            if (book != null) {
                // 替换钓到的物品为附魔书（或者添加到背包）
                org.bukkit.entity.Player player = event.getPlayer();
                
                // 如果背包有空间，添加到背包；否则替换钓到的物品
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(book);
                    player.sendMessage("§d✨ 你钓到了一个神秘的附魔书：丰收！");
                } else {
                    // 背包满时，替换钓到的物品
                    event.setCancelled(true);
                    player.getWorld().dropItemNaturally(player.getLocation(), book);
                    player.sendMessage("§d✨ 你钓到了一个神秘的附魔书：丰收！（已掉落在地上）");
                }
                
                if (plugin.getConfigManager().getBoolean("debug")) {
                    plugin.getLogger().info("[钓鱼调试] " + player.getName() + " 钓到丰收附魔书");
                }
            }
        }
    }
    
    /**
     * ✅ 实体死亡掉落附魔书（纯插件实现）
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Random random = new Random();
        
        // 1. 幻影守护者：幻翼 5% 概率掉落
        if (entity instanceof Phantom) {
            double dropChance = plugin.getConfigManager().getRawConfig().getDouble("acquisition.phantom-drop-chance", 0.05);
            if (random.nextDouble() < dropChance) {
                ItemStack book = createEnchantedBook("phantom", 1);
                if (book != null) {
                    event.getDrops().add(book);
                    if (plugin.getConfigManager().getBoolean("debug")) {
                        plugin.getLogger().info("[掉落调试] 幻翼掉落幻影守护者附魔书");
                    }
                }
            }
        }
        
        // 2. 切肉大师：掠夺者/卫道士/唤魔者 3% 概率掉落
        if (entity instanceof Pillager || entity instanceof Vindicator || entity instanceof Evoker) {
            double dropChance = plugin.getConfigManager().getRawConfig().getDouble("acquisition.beef-slicing-drop-chance", 0.03);
            if (random.nextDouble() < dropChance) {
                int level = random.nextInt(3) + 1; // 随机 1-3 级
                ItemStack book = createEnchantedBook("master_of_beef_slicing", level);
                if (book != null) {
                    event.getDrops().add(book);
                    if (plugin.getConfigManager().getBoolean("debug")) {
                        plugin.getLogger().info("[掉落调试] " + entity.getType().name() + " 掉落切肉大师 " + level + " 级");
                    }
                }
            }
        }
        
        // 3. 猫爪：猪灵 2% 概率掉落（下界堡垒）
        if (entity.getType().name().equals("PIGLIN") || entity.getType().name().equals("PIGLIN_BRUTE")) {
            double dropChance = plugin.getConfigManager().getRawConfig().getDouble("acquisition.cats-paw-drop-chance", 0.02);
            if (random.nextDouble() < dropChance) {
                int level = random.nextInt(3) + 1; // 随机 1-3 级
                ItemStack book = createEnchantedBook("cats_paw", level);
                if (book != null) {
                    event.getDrops().add(book);
                    if (plugin.getConfigManager().getBoolean("debug")) {
                        plugin.getLogger().info("[掉落调试] 猪灵掉落猫爪 " + level + " 级");
                    }
                }
            }
        }
        
        // 4. 狗头：潜影贝 1.5% 概率掉落（末地城）
        if (entity instanceof Shulker) {
            double dropChance = plugin.getConfigManager().getRawConfig().getDouble("acquisition.nasus-drop-chance", 0.015);
            if (random.nextDouble() < dropChance) {
                int level = random.nextInt(3) + 1; // 随机 1-3 级
                ItemStack book = createEnchantedBook("nasus", level);
                if (book != null) {
                    event.getDrops().add(book);
                    if (plugin.getConfigManager().getBoolean("debug")) {
                        plugin.getLogger().info("[掉落调试] 潜影贝掉落狗头 " + level + " 级");
                    }
                }
            }
        }
    }
    

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvilInventory = event.getInventory();
        ItemStack[] items = anvilInventory.getContents();
        
        if (items.length < 2) return;
        
        ItemStack item1 = items[0]; // 左侧物品
        ItemStack item2 = items[1]; // 右侧物品
        
        if (item1 == null || item2 == null) return;
        
        // ✅ 情况1：两个附魔书合并（相同附魔升级）
        if (item1.getType() == Material.ENCHANTED_BOOK && item2.getType() == Material.ENCHANTED_BOOK) {
            handleBookMerge(event, item1, item2);
            return;
        }
        
        // ✅ 情况2：附魔书 + 物品（应用附魔）
        if (item2.getType() == Material.ENCHANTED_BOOK) {
            ItemMeta bookMeta = item2.getItemMeta();
            if (bookMeta instanceof EnchantmentStorageMeta) {
                EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) bookMeta;
                
                // 优先检查是否包含我们的自定义附魔（PersistentDataContainer）
                for (String enchantId : enchantmentManager.getEnchantmentIds()) {
                    NamespacedKey key = new NamespacedKey(plugin, "enchantment_" + enchantId.replace("-", "_"));
                    Integer level = storageMeta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
                    
                    if (level != null && level > 0) {
                        CustomEnchantment enchant = enchantmentManager.getEnchantment(enchantId);
                        if (enchant != null && enchant.canApplyTo(item1)) {
                            // 创建结果物品（复制第一个物品）
                            ItemStack result = item1.clone();
                            
                            // 应用附魔
                            enchant.applyEnchantment(result, level);
                            
                            // 设置结果
                            event.setResult(result);
                            
                            return;
                        }
                    }
                }
                
                // 如果没有找到自定义附魔，检查是否有数据包的原生附魔
                // 数据包的附魔使用 deeper_dark 命名空间
                handleDatapackEnchantment(event, item1, storageMeta);
            }
        }
    }
    
    /**
     * 处理两个附魔书合并（相同附魔升级）
     */
    private void handleBookMerge(PrepareAnvilEvent event, ItemStack book1, ItemStack book2) {
        EnchantmentStorageMeta meta1 = (EnchantmentStorageMeta) book1.getItemMeta();
        EnchantmentStorageMeta meta2 = (EnchantmentStorageMeta) book2.getItemMeta();
        
        if (meta1 == null || meta2 == null) return;
        
        // 遍历所有附魔ID，查找相同的附魔
        for (String enchantId : enchantmentManager.getEnchantmentIds()) {
            NamespacedKey key = new NamespacedKey(plugin, "enchantment_" + enchantId.replace("-", "_"));
            
            Integer level1 = meta1.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
            Integer level2 = meta2.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
            
            // 如果两个书都有这个附魔，且等级相同，可以升级
            if (level1 != null && level2 != null && level1.equals(level2)) {
                CustomEnchantment enchant = enchantmentManager.getEnchantment(enchantId);
                if (enchant == null) continue;
                
                int newLevel = level1 + 1;
                
                // 检查是否超过最大等级
                if (newLevel > enchant.getMaxLevel()) {
                    continue; // 超过最大等级，不能合并
                }
                
                // 创建结果附魔书
                ItemStack result = createEnchantedBook(enchantId, newLevel);
                if (result != null) {
                    event.setResult(result);
                    
                    if (plugin.getConfigManager().getBoolean("debug")) {
                        plugin.getLogger().info("[铁砧调试] 合并附魔书: " + enchant.getDisplayName() + 
                            " " + getRomanNumeral(level1) + " + " + getRomanNumeral(level2) + 
                            " -> " + getRomanNumeral(newLevel));
                    }
                }
                return;
            }
        }
    }
    
    /**
     * 处理数据包的原生附魔，将其转换为插件的自定义附魔系统
     */
    private void handleDatapackEnchantment(PrepareAnvilEvent event, ItemStack targetItem, EnchantmentStorageMeta bookMeta) {
        // 首先检查结果是否已经被设置（避免重复处理）
        if (event.getResult() != null && !event.getResult().getType().isAir()) {
            return;
        }
        
        // 数据包附魔的命名空间映射：deeper_dark -> 插件附魔ID
        String[] datapackEnchants = {
            "clearsight", "darkspeed", "resonate", "safefall", 
            "shrieker_sense", "sonic_boom", "undermine"
        };
        
        for (String enchantId : datapackEnchants) {
            // 检查是否包含数据包的附魔
            NamespacedKey datapackKey = NamespacedKey.fromString("deeper_dark:" + enchantId);
            if (datapackKey != null) {
                org.bukkit.enchantments.Enchantment datapackEnchant = 
                    org.bukkit.Registry.ENCHANTMENT.get(datapackKey);
                
                if (datapackEnchant != null) {
                    int level = bookMeta.getStoredEnchantLevel(datapackEnchant);
                    if (level > 0) {
                        // 获取对应的插件附魔
                        CustomEnchantment pluginEnchant = enchantmentManager.getEnchantment(enchantId);
                        if (pluginEnchant != null && pluginEnchant.canApplyTo(targetItem)) {
                            // 创建结果物品
                            ItemStack result = targetItem.clone();
                            
                            // 应用插件的自定义附魔
                            pluginEnchant.applyEnchantment(result, level);
                            
                            // 设置结果
                            event.setResult(result);
                            
                            // 只在调试模式下输出日志，避免刷屏
                            if (plugin.getConfigManager().getBoolean("debug")) {
                                plugin.getLogger().info("检测到数据包附魔: " + enchantId + " 等级: " + level + "，已转换为插件附魔");
                            }
                            return;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 为玩家创建自定义附魔书
     */
    public ItemStack createEnchantedBook(String enchantmentId, int level) {
        CustomEnchantment enchant = enchantmentManager.getEnchantment(enchantmentId);
        if (enchant == null) {
            plugin.getLogger().warning("未知的附魔ID: " + enchantmentId);
            return null;
        }
        
        if (level < 1 || level > enchant.getMaxLevel()) {
            plugin.getLogger().warning("无效的附魔等级: " + level + " (最大: " + enchant.getMaxLevel() + ")");
            return null;
        }
        
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, "enchantment_" + enchantmentId.replace("-", "_"));
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);
            
            // ✅ 单等级附魔不显示罗马数字
            String displayName = enchant.getMaxLevel() == 1 ? 
                "§d" + enchant.getDisplayName() : 
                "§d" + enchant.getDisplayName() + " " + getRomanNumeral(level);
            meta.setDisplayName(displayName);
            
            List<String> lore = new ArrayList<>();
            lore.add("§7适用物品: " + getApplicableItemsText(enchant));
            lore.add("§7等级: " + level + "/" + enchant.getMaxLevel());
            meta.setLore(lore);
            
            book.setItemMeta(meta);
        }
        
        return book;
    }
    
    /**
     * 获取所有可用的附魔ID列表（供其他插件调用）
     * @return 附魔ID数组
     */
    public String[] getAvailableEnchantmentIds() {
        return enchantmentManager.getEnchantmentIds();
    }
    
    /**
     * 获取指定附魔的最大等级（供其他插件调用）
     * @param enchantmentId 附魔ID
     * @return 最大等级，如果附魔不存在返回0
     */
    public int getMaxLevel(String enchantmentId) {
        CustomEnchantment enchant = enchantmentManager.getEnchantment(enchantmentId);
        return enchant != null ? enchant.getMaxLevel() : 0;
    }
    
    /**
     * 获取罗马数字
     */
    private String getRomanNumeral(int number) {
        switch (number) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return "" + number;
        }
    }
    
    /**
     * 获取适用物品的文本描述
     */
    private String getApplicableItemsText(CustomEnchantment enchant) {
        Material[] items = enchant.getApplicableItems();
        if (items == null || items.length == 0) {
            return "未知";
        }
        
        // 根据物品类型返回简化描述
        Set<String> categories = new HashSet<>();
        for (Material item : items) {
            if (item.name().contains("HELMET")) categories.add("头盔");
            else if (item.name().contains("CHESTPLATE")) categories.add("胸甲");
            else if (item.name().contains("LEGGINGS")) categories.add("护腿");
            else if (item.name().contains("BOOTS")) categories.add("靴子");
            else if (item.name().contains("SWORD")) categories.add("剑");
            else if (item.name().contains("PICKAXE")) categories.add("镐");
            else if (item.name().contains("AXE")) categories.add("斧");
            else if (item.name().contains("SHOVEL")) categories.add("锹");
            else if (item.name().contains("SHIELD")) categories.add("盾牌");
            else if (item.name().contains("SPYGLASS")) categories.add("望远镜");
        }
        
        return String.join("、", categories);
    }
}
