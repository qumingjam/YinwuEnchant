package YinwuEnchant.manager;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.enchantments.*;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;

public class EnchantmentManager {
    private final YinwuEnchantments plugin;
    private final ConfigManager configManager;
    private final Map<String, CustomEnchantment> enchantments = new HashMap<>();
    
    // 事件订阅者模式 - 性能优化
    private final Map<Class<? extends Event>, List<Consumer<Event>>> eventSubscribers = new HashMap<>();
    
    public EnchantmentManager(YinwuEnchantments plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        registerEnchantments();
    }
    
    private void registerEnchantments() {
        // 创建并注册所有附魔
        enchantments.put("clearsight", new Clearsight(plugin));
        enchantments.put("darkspeed", new Darkspeed(plugin));
        enchantments.put("resonate", new Resonate(plugin));
        enchantments.put("safefall", new Safefall(plugin));
        enchantments.put("shrieker_sense", new ShriekerSense(plugin));
        enchantments.put("sonic_boom", new SonicBoom(plugin));
        enchantments.put("undermine", new Undermine(plugin));
        enchantments.put("cats_paw", new CatsPaw(plugin));
        enchantments.put("nasus", new Nasus(plugin));
        enchantments.put("master_of_beef_slicing", new MasterOfBeefSlicing(plugin));
        enchantments.put("phantom", new PhantomProtection(plugin));
        enchantments.put("harvest", new Harvest(plugin));
    }
    
    public void enableAll() {
        // ✅ 清除旧的事件订阅
        clearEventSubscribers();
        
        for (CustomEnchantment enchantment : enchantments.values()) {
            if (configManager.isEnchantmentEnabled(enchantment.getId())) {
                enchantment.onEnable();
                // ✅ 注册事件订阅
                enchantment.registerEventSubscribers();
                // ✅ 日志规范化：只在调试模式下输出
                if (plugin.getConfigManager().getBoolean("debug")) {
                    plugin.getLogger().fine("启用附魔: " + enchantment.getId());
                }
            }
        }
    }
    
    public void disableAll() {
        for (CustomEnchantment enchantment : enchantments.values()) {
            enchantment.onDisable();
        }
    }
    
    public CustomEnchantment getEnchantment(String id) {
        return enchantments.get(id.toLowerCase());
    }
    
    public Map<String, CustomEnchantment> getAllEnchantments() {
        // ✅ 返回不可变视图，避免每次创建副本
        return Collections.unmodifiableMap(enchantments);
    }
    
    public ItemStack applyEnchantmentToItem(ItemStack item, String enchantmentId, int level) {
        CustomEnchantment enchantment = getEnchantment(enchantmentId);
        if (enchantment != null) {
            return enchantment.applyEnchantment(item, level);
        }
        return item;
    }
    
    public boolean isValidEnchantmentForItem(ItemStack item, String enchantmentId) {
        CustomEnchantment enchantment = getEnchantment(enchantmentId);
        if (enchantment == null) return false;
        return enchantment.canApplyTo(item);
    }
    
    public String[] getEnchantmentIds() {
        return enchantments.keySet().toArray(new String[0]);
    }
    
    public String getEnchantmentDisplayName(String id) {
        CustomEnchantment enchantment = getEnchantment(id);
        return enchantment != null ? enchantment.getDisplayName() : id;
    }
    
    public int getMaxLevel(String id) {
        CustomEnchantment enchantment = getEnchantment(id);
        return enchantment != null ? enchantment.getMaxLevel() : 0;
    }
    
    public Material[] getApplicableItems(String id) {
        CustomEnchantment enchantment = getEnchantment(id);
        return enchantment != null ? enchantment.getApplicableItems() : new Material[0];
    }
    
    // ==================== 事件订阅者模式 API ====================
    
    /**
     * 订阅事件（泛型版本，提供类型安全）
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @param <T> 事件类型参数
     */
    public <T extends Event> void subscribeEvent(Class<T> eventType, Consumer<T> handler) {
        eventSubscribers.computeIfAbsent(eventType, k -> new ArrayList<>())
            .add(event -> handler.accept(eventType.cast(event)));
    }
    
    /**
     * 分发事件给所有订阅者
     * @param event 事件对象
     */
    public void dispatchEvent(Event event) {
        List<Consumer<Event>> handlers = eventSubscribers.get(event.getClass());
        if (handlers != null) {
            for (Consumer<Event> handler : handlers) {
                try {
                    handler.accept(event);
                } catch (Exception e) {
                    plugin.getLogger().warning("事件处理异常: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 清除所有事件订阅（用于重载）
     */
    public void clearEventSubscribers() {
        eventSubscribers.clear();
    }
}