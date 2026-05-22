package YinwuEnchant;

import YinwuEnchant.manager.CommandHandler;
import YinwuEnchant.manager.ConfigManager;
import YinwuEnchant.manager.EnchantmentAcquisitionManager;
import YinwuEnchant.manager.EnchantmentManager;
import YinwuEnchant.manager.EventListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class YinwuEnchantments extends JavaPlugin {
    private ConfigManager configManager;
    private EnchantmentManager enchantmentManager;
    private EnchantmentAcquisitionManager acquisitionManager;
    private CommandHandler commandHandler;
    private EventListener eventListener;
    
    @Override
    public void onEnable() {
        // 保存默认配置文件
        saveDefaultConfig();
        
        // 初始化管理器
        configManager = new ConfigManager(this);
        enchantmentManager = new EnchantmentManager(this, configManager);
        acquisitionManager = new EnchantmentAcquisitionManager(this, enchantmentManager);
        commandHandler = new CommandHandler(this, enchantmentManager, acquisitionManager, configManager);
        eventListener = new EventListener(this, enchantmentManager);
        
        // 注册命令
        getCommand("ye").setExecutor(commandHandler);
        getCommand("ye").setTabCompleter(commandHandler);
        
        // 启用所有附魔
        enchantmentManager.enableAll();
        
        // ✅ 日志规范化：只在调试模式下输出详细信息
        if (configManager.getBoolean("debug")) {
            getLogger().info("YinwuEnchantments 已启用！");
            getLogger().info("已加载 " + enchantmentManager.getAllEnchantments().size() + " 个附魔。");
        } else {
            getLogger().fine("YinwuEnchantments 已启用！");
        }
    }
    
    @Override
    public void onDisable() {
        // 禁用所有附魔
        if (enchantmentManager != null) {
            enchantmentManager.disableAll();
        }
        
        // ✅ 日志规范化：只在调试模式下输出
        if (configManager != null && configManager.getBoolean("debug")) {
            getLogger().info("YinwuEnchantments 已禁用。");
        } else {
            getLogger().fine("YinwuEnchantments 已禁用。");
        }
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public EnchantmentManager getEnchantmentManager() {
        return enchantmentManager;
    }
    
    /**
     * 获取附魔获取管理器（供其他插件调用API）
     * @return EnchantmentAcquisitionManager 实例
     */
    public EnchantmentAcquisitionManager getAcquisitionManager() {
        return acquisitionManager;
    }
}
