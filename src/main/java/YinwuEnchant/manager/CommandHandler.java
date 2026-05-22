package YinwuEnchant.manager;

import YinwuEnchant.YinwuEnchantments;
import YinwuEnchant.enchantments.CustomEnchantment;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandHandler implements TabExecutor {
    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;
    private final EnchantmentAcquisitionManager acquisitionManager;
    private final ConfigManager configManager;
    
    public CommandHandler(YinwuEnchantments plugin, EnchantmentManager enchantmentManager, EnchantmentAcquisitionManager acquisitionManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.enchantmentManager = enchantmentManager;
        this.acquisitionManager = acquisitionManager;
        this.configManager = configManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "give":
            case "givebook":
            case "reload":
                // 普通玩家看不到这些命令，直接提示未知命令
                if (!sender.hasPermission("yinwuenchant.admin")) {
                    sender.sendMessage(ChatColor.RED + "未知的子命令。请使用 /ye list 查看附魔列表。");
                    return true;
                }
                switch (args[0].toLowerCase()) {
                    case "give":
                        return handleGiveCommand(sender, args);
                    case "givebook":
                        return handleGiveBookCommand(sender, args);
                    case "reload":
                        return handleReloadCommand(sender, args);
                }
            case "list":
                return handleListCommand(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            // 只有管理员才能看到 give、givebook 和 reload 命令
            if (sender.hasPermission("yinwuenchant.admin")) {
                commands.add("give");
                commands.add("givebook");
                commands.add("reload");
            }
            // 所有有基础权限的玩家都能看到 list 命令
            if (sender.hasPermission("yinwuenchant.use")) {
                commands.add("list");
            }
            StringUtil.copyPartialMatches(args[0], commands, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("givebook")) {
                // 玩家名补全
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (StringUtil.startsWithIgnoreCase(player.getName(), args[1])) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("givebook")) {
                // 附魔ID补全
                String[] enchantmentIds = enchantmentManager.getEnchantmentIds();
                StringUtil.copyPartialMatches(args[2], Arrays.asList(enchantmentIds), completions);
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("givebook")) {
                // 等级补全
                String enchantmentId = args[2];
                int maxLevel = enchantmentManager.getMaxLevel(enchantmentId);
                // 只有多等级附魔才显示等级补全
                if (maxLevel > 1) {
                    for (int i = 1; i <= maxLevel; i++) {
                        if (StringUtil.startsWithIgnoreCase(String.valueOf(i), args[3])) {
                            completions.add(String.valueOf(i));
                        }
                    }
                }
            }
        }
        
        return completions;
    }
    
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yinwuenchant.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "用法: /ye give <玩家> <附魔> [等级]");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "玩家未找到: " + args[1]);
            return true;
        }
        
        String enchantmentId = args[2].toLowerCase();
        CustomEnchantment enchantment = enchantmentManager.getEnchantment(enchantmentId);
        if (enchantment == null) {
            sender.sendMessage(ChatColor.RED + "未知的附魔: " + args[2]);
            sender.sendMessage(ChatColor.YELLOW + "可用附魔: " + String.join(", ", enchantmentManager.getEnchantmentIds()));
            return true;
        }
        
        int level = 1;
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "无效的等级: " + args[3]);
                return true;
            }
        }
        
        if (level < 1 || level > enchantment.getMaxLevel()) {
            sender.sendMessage(ChatColor.RED + "等级必须在1到" + enchantment.getMaxLevel() + "之间。");
            return true;
        }
        
        // 获取玩家手中的物品
        ItemStack item = target.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            sender.sendMessage(ChatColor.RED + "目标玩家手中没有物品。");
            return true;
        }
        
        if (!enchantment.canApplyTo(item)) {
            sender.sendMessage(ChatColor.RED + "此附魔无法应用于该物品。");
            sender.sendMessage(ChatColor.YELLOW + "可用物品: " + getMaterialNames(enchantment.getApplicableItems()));
            return true;
        }
        
        // ✅ 规则5：在玩家区域线程执行物品操作
        final int finalLevel = level;  // 声明为 final
        target.getScheduler().run(plugin, (task) -> {
            // 应用附魔
            ItemStack enchantedItem = enchantment.applyEnchantment(item, finalLevel);
            target.getInventory().setItemInMainHand(enchantedItem);
            
            // 1级附魔不显示等级
            String levelDisplay = enchantment.getMaxLevel() == 1 ? "" : " " + finalLevel;
            sender.sendMessage(ChatColor.GREEN + "成功将 " + enchantment.getDisplayName() + levelDisplay + " 应用于 " + target.getName() + " 的物品。");
            target.sendMessage(ChatColor.GREEN + "你的物品被赋予了 " + enchantment.getDisplayName() + levelDisplay + " 附魔。");
        }, null);
        
        return true;
    }
    
    private boolean handleGiveBookCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yinwuenchant.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "用法: /ye givebook <玩家> <附魔> [等级]");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "玩家未找到: " + args[1]);
            return true;
        }
        
        String enchantmentId = args[2].toLowerCase();
        final CustomEnchantment enchantment = enchantmentManager.getEnchantment(enchantmentId);
        if (enchantment == null) {
            sender.sendMessage(ChatColor.RED + "未知的附魔: " + args[2]);
            sender.sendMessage(ChatColor.YELLOW + "可用附魔: " + String.join(", ", enchantmentManager.getEnchantmentIds()));
            return true;
        }
        
        int level = 1;
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
                if (level < 1 || level > enchantment.getMaxLevel()) {
                    sender.sendMessage(ChatColor.RED + "等级必须在 1-" + enchantment.getMaxLevel() + " 之间。");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "无效的等级: " + args[3]);
                return true;
            }
        }
        
        // 创建附魔书
        ItemStack book = acquisitionManager.createEnchantedBook(enchantmentId, level);
        if (book == null) {
            sender.sendMessage(ChatColor.RED + "无法创建附魔书。");
            return true;
        }
        
        // ✅ 规则5：在玩家区域线程执行物品操作
        final int finalLevel = level;  // 声明为 final
        target.getScheduler().run(plugin, (task) -> {
            // 给予玩家
            target.getInventory().addItem(book);
            
            // 1级附魔不显示等级
            String levelDisplay = enchantment.getMaxLevel() == 1 ? "" : " " + finalLevel;
            sender.sendMessage(ChatColor.GREEN + "已将 " + enchantment.getDisplayName() + levelDisplay + " 附魔书给予 " + target.getName());
            target.sendMessage(ChatColor.GREEN + "你收到了 " + enchantment.getDisplayName() + levelDisplay + " 附魔书！");
        }, null);
        
        return true;
    }
    
    private boolean handleReloadCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yinwuenchant.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return true;
        }
        
        // ✅ 修复：先禁用所有附魔
        enchantmentManager.disableAll();
        
        // ✅ 再重载配置（ConfigManager.reload() 内部已包含 plugin.reloadConfig()）
        configManager.reload();
        
        // ✅ 最后重新启用所有附魔（会根据新配置决定是否启用）
        enchantmentManager.enableAll();
        
        sender.sendMessage(ChatColor.GREEN + "配置文件已重载。");
        return true;
    }
    
    private boolean handleListCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yinwuenchant.use")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令仅玩家可用。");
            return true;
        }
        
        Player player = (Player) sender;
        openEnchantmentGUI(player);
        
        return true;
    }
    
    /**
     * 打开附魔GUI界面
     */
    private void openEnchantmentGUI(Player player) {
        org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(
            null,
            54, // 6行
            ChatColor.GOLD + "Yinwu附魔列表"
        );
        
        // ✅ 定义附魔ID到槽位的映射（按用户指定的布局）
        java.util.Map<String, Integer> slotMapping = new java.util.HashMap<>();
        slotMapping.put("clearsight", 0);           // 明目
        slotMapping.put("sonic_boom", 1);           // 音波爆裂
        slotMapping.put("safefall", 2);             // 外骨骼
        slotMapping.put("cats_paw", 3);             // 猫爪
        slotMapping.put("master_of_beef_slicing", 4); // 切肉大师
        slotMapping.put("resonate", 5);             // 共振
        slotMapping.put("undermine", 6);            // 深层矿工
        slotMapping.put("shrieker_sense", 7);       // 幽匿探测
        slotMapping.put("nasus", 9);                // 狗头
        slotMapping.put("phantom", 10);             // 幻影守护者
        slotMapping.put("darkspeed", 12);           // 黑暗行者
        slotMapping.put("harvest", 15);             // 丰收
        
        String[] enchantmentIds = enchantmentManager.getEnchantmentIds();
        
        // 按指定槽位放置附魔
        for (String id : enchantmentIds) {
            Integer slot = slotMapping.get(id);
            if (slot == null) continue; // 跳过未定义的附魔
            
            CustomEnchantment enchantment = enchantmentManager.getEnchantment(id);
            boolean enabled = configManager.isEnchantmentEnabled(id);
            
            // 创建附魔展示物品
            Material displayMaterial = getDisplayMaterialForEnchantment(id);
            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(displayMaterial);
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            
            if (meta != null) {
                // 设置显示名称（根据附魔ID定制）
                String displayName;
                switch (id) {
                    case "clearsight":
                        displayName = "§d明目";
                        break;
                    case "darkspeed":
                        displayName = "§d黑暗行者";
                        break;
                    case "resonate":
                        displayName = "§d共振";
                        break;
                    case "safefall":
                        displayName = "§d外骨骼";
                        break;
                    case "shrieker_sense":
                        displayName = "§d幽匿探测";
                        break;
                    case "sonic_boom":
                        displayName = "§d音波爆裂";
                        break;
                    case "undermine":
                        displayName = "§d深层矿工";
                        break;
                    case "cats_paw":
                        displayName = "§d猫爪";
                        break;
                    case "nasus":
                        displayName = "§d狗头";
                        break;
                    case "master_of_beef_slicing":
                        displayName = "§d切肉大师";
                        break;
                    case "phantom":
                        displayName = "§d幻影守护者";
                        break;
                    case "harvest":
                        displayName = "§d丰收";
                        break;
                    default:
                        displayName = "§d" + enchantment.getDisplayName();
                        break;
                }
                meta.setDisplayName(displayName);
                
                // ✅ 添加附魔光效
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                
                // 设置lore信息（根据附魔类型定制）
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
                
                // 根据附魔ID定制lore内容
                switch (id) {
                    case "clearsight":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "头盔");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "无视黑暗Buff");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "幽匿维度探索");
                        break;
                    case "darkspeed":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "鞋子");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "黑暗区域增加移动速度");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "幽匿维度探索");
                        break;
                    case "resonate":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "盾牌");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "反弹所格挡的攻击");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "幽匿维度探索");
                        break;
                    case "safefall":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "护腿");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "减免摔落伤害");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "幽匿维度探索");
                        break;
                    case "shrieker_sense":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "望远镜");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "探测周围的尖啸体与监守者");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "幽匿维度探索");
                        break;
                    case "sonic_boom":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "胸甲");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "熄灭附近所有的灵魂火");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "幽匿维度探索");
                        break;
                    case "undermine":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "镐、锹、斧");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "海平面以下挖掘加速");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "幽匿维度探索");
                        break;
                    case "cats_paw":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "鞋子");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "周期性恐吓苦力怕");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "击杀猪灵");
                        break;
                    case "nasus":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "头盔");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "周期性恐吓骷髅类怪物");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "击杀潜影贝");
                        break;
                    case "master_of_beef_slicing":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "剑");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "攻击生物额外掉落肉类");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "击杀掠夺者/卫道士/唤魔者");
                        break;
                    case "phantom":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "胸甲、鞘翅");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "防止幻翼攻击");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "击杀幻翼");
                        break;
                    case "harvest":
                        lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + "锄头");
                        lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + "右键收获成熟作物");
                        lore.add(ChatColor.YELLOW + "生效作物: " + ChatColor.WHITE + "小麦、胡萝卜、马铃薯、甜菜、浆果丛、下界疣");
                        lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + "钓鱼");
                        break;
                    default:
                        // 默认显示原有信息
                        lore.add(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + enchantment.getId());
                        lore.add(ChatColor.YELLOW + "最大等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                        lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
                        lore.add(ChatColor.AQUA + "适用物品:");
                        
                        // 显示适用物品（最多显示5个）
                        String[] materialNames = getMaterialNames(enchantment.getApplicableItems()).split("、");
                        int maxShow = Math.min(materialNames.length, 5);
                        for (int i = 0; i < maxShow; i++) {
                            lore.add(ChatColor.WHITE + "• " + materialNames[i]);
                        }
                        if (materialNames.length > 5) {
                            lore.add(ChatColor.GRAY + "... 以及 " + (materialNames.length - 5) + " 个其他物品");
                        }
                        break;
                }
                
                lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
                
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            
            gui.setItem(slot, item);
        }
        
        // ✅ 填充空白格子（所有未使用的槽位）
        org.bukkit.inventory.ItemStack filler = new org.bukkit.inventory.ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 54; i++) {
            if (!slotMapping.containsValue(i)) {
                gui.setItem(i, filler);
            }
        }
        
        player.openInventory(gui);
        
        // 启动图标循环任务（Folia兼容，使用玩家区域调度器）
        startIconCycleTask(player, enchantmentIds);
    }
    
    /**
     * 启动图标循环任务（Folia兼容）
     */
    private void startIconCycleTask(Player player, String[] enchantmentIds) {
        // ✅ 使用固定槽位映射，与 openEnchantmentGUI() 中的布局一致
        java.util.Map<String, Integer> slotMapping = new java.util.HashMap<>();
        slotMapping.put("clearsight", 0);           // 明目
        slotMapping.put("sonic_boom", 1);           // 音波爆裂
        slotMapping.put("safefall", 2);             // 外骨骼
        slotMapping.put("cats_paw", 3);             // 猫爪
        slotMapping.put("master_of_beef_slicing", 4); // 切肉大师
        slotMapping.put("resonate", 5);             // 共振
        slotMapping.put("undermine", 6);            // 深层矿工
        slotMapping.put("shrieker_sense", 7);       // 幽匿探测
        slotMapping.put("nasus", 9);                // 狗头
        slotMapping.put("phantom", 10);             // 幻影守护者
        slotMapping.put("darkspeed", 12);           // 黑暗行者
        
        // 为需要循环的附魔启动循环任务
        startSingleEnchantmentIconCycle(player, slotMapping.get("clearsight"), "clearsight", "头盔", "无视黑暗Buff", "幽匿维度探索");
        startSingleEnchantmentIconCycle(player, slotMapping.get("darkspeed"), "darkspeed", "鞋子", "黑暗区域增加移动速度", "幽匿维度探索");
        startSingleEnchantmentIconCycle(player, slotMapping.get("safefall"), "safefall", "护腿", "减免摔落伤害", "幽匿维度探索");
        startSingleEnchantmentIconCycle(player, slotMapping.get("sonic_boom"), "sonic_boom", "胸甲", "熄灭附近所有的灵魂火", "幽匿维度探索");
        startSingleEnchantmentIconCycle(player, slotMapping.get("undermine"), "undermine", "镐、锹、斧", "海平面以下挖掘加速", "幽匿维度探索");
        startSingleEnchantmentIconCycle(player, slotMapping.get("master_of_beef_slicing"), "master_of_beef_slicing", "剑", "攻击生物额外掉落肉类", "击杀掠夺者/卫道士/唤魔者");
        startSingleEnchantmentIconCycle(player, slotMapping.get("cats_paw"), "cats_paw", "鞋子", "周期性恐吓苦力怕", "击杀猪灵");
        startSingleEnchantmentIconCycle(player, slotMapping.get("nasus"), "nasus", "头盔", "周期性恐吓骷髅类怪物", "击杀潜影贝");
        startSingleEnchantmentIconCycle(player, slotMapping.get("phantom"), "phantom", "胸甲、鞘翅", "防止幻翼攻击", "击杀幻翼");
    }
    
    /**
     * 为单个附魔启动图标循环任务
     * @param player 玩家
     * @param slot 固定槽位号
     * @param enchantmentId 附魔ID
     * @param itemType 适用物品类型描述
     * @param effect 附魔效果描述
     * @param source 附魔来源描述
     */
    private void startSingleEnchantmentIconCycle(Player player, Integer slot, String enchantmentId, String itemType, String effect, String source) {
        // ✅ 使用固定槽位，不再通过查找
        if (slot == null || slot < 0 || slot >= 54) return;
        
        // 获取附魔的所有适用物品
        CustomEnchantment enchantment = enchantmentManager.getEnchantment(enchantmentId);
        if (enchantment == null) return;
        
        Material[] applicableItems = enchantment.getApplicableItems();
        if (applicableItems == null || applicableItems.length == 0) return;
        
        // 使用 AtomicInteger 存储当前索引（线程安全）
        // 初始化为随机索引，让每个附魔的起始物品不同
        java.util.concurrent.atomic.AtomicInteger currentIndex = new java.util.concurrent.atomic.AtomicInteger(
            (int)(Math.random() * applicableItems.length)
        );
        
        // 使用玩家区域调度器，确保在正确的区域线程中执行
        player.getScheduler().runAtFixedRate(plugin, (task) -> {
            // 检查玩家是否还在线且 GUI 是否还打开
            if (!player.isOnline()) {
                task.cancel();
                return;
            }
            
            String openTitle = player.getOpenInventory().getTitle();
            if (openTitle == null || !openTitle.equals(ChatColor.GOLD + "Yinwu附魔列表")) {
                task.cancel();
                return;
            }
            
            // 计算下一个索引
            int index = currentIndex.getAndIncrement() % applicableItems.length;
            Material material = applicableItems[index];
            
            // 创建新物品
            org.bukkit.inventory.ItemStack newItem = new org.bukkit.inventory.ItemStack(material);
            org.bukkit.inventory.meta.ItemMeta meta = newItem.getItemMeta();
            
            if (meta != null) {
                boolean enabled = configManager.isEnchantmentEnabled(enchantmentId);
                ChatColor statusColor = enabled ? ChatColor.GREEN : ChatColor.RED;
                String displayName = getDisplayNameForGUI(enchantmentId, statusColor);
                meta.setDisplayName(displayName);
                
                // ✅ 添加附魔光效
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
                lore.add(ChatColor.YELLOW + "适用物品: " + ChatColor.WHITE + itemType);
                lore.add(ChatColor.YELLOW + "最高等级: " + ChatColor.WHITE + enchantment.getMaxLevel());
                lore.add(ChatColor.YELLOW + "附魔效果: " + ChatColor.WHITE + effect);
                lore.add(ChatColor.YELLOW + "附魔来源: " + ChatColor.WHITE + source);
                lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
                
                meta.setLore(lore);
                newItem.setItemMeta(meta);
            }
            
            // 更新 GUI 中的物品
            player.getOpenInventory().setItem(slot, newItem);
        }, null, 1L, 20L); // 初始延迟1刻，之后每20刻（1秒）执行一次
    }
    
    /**
     * 根据附魔类型获取展示物品
     */
    private Material getDisplayMaterialForEnchantment(String enchantmentId) {
        switch (enchantmentId) {
            case "clearsight": return Material.CARVED_PUMPKIN; // 雕刻南瓜头代表明目
            case "darkspeed": return Material.DIAMOND_BOOTS; // 钻石靴子代表黑暗行者
            case "resonate": return Material.SHIELD; // 盾牌代表共振
            case "safefall": return Material.DIAMOND_LEGGINGS; // 钻石护腿代表外骨骼
            case "shrieker_sense": return Material.SPYGLASS; // 望远镜代表幽匿探测
            case "sonic_boom": return Material.DIAMOND_CHESTPLATE; // 钻石胸甲代表音波爆裂
            case "undermine": return Material.IRON_PICKAXE; // 铁镐代表挖掘
            case "cats_paw": return Material.CREEPER_HEAD; // 猫头代表猫爪
            case "nasus": return Material.SKELETON_SKULL; // 骷髅头代表狗头
            case "master_of_beef_slicing": return Material.COOKED_BEEF; // 熟牛肉代表切肉大师
            case "phantom": return Material.PHANTOM_MEMBRANE; // 幻翼膜代表幻影守护者
            case "harvest": return Material.GOLDEN_HOE; // 金锄头代表丰收
            default: return Material.PAPER;
        }
    }
    
    /**
     * 获取附魔的中文名称
     */
    private String getChineseName(String enchantmentId) {
        switch (enchantmentId) {
            case "clearsight": return "明目";
            case "darkspeed": return "黑暗行者";
            case "resonate": return "共振";
            case "safefall": return "外骨骼";
            case "shrieker_sense": return "幽匿探测";
            case "sonic_boom": return "音波爆裂";
            case "undermine": return "深层矿工";
            case "cats_paw": return "猫爪";
            case "nasus": return "狗头";
            case "master_of_beef_slicing": return "切肉大师";
            case "phantom": return "幻影守护者";
            case "harvest": return "丰收";
            default: return enchantmentId;
        }
    }
    
    /**
     * 获取GUI中显示的附魔名称（带颜色）
     */
    private String getDisplayNameForGUI(String enchantmentId, ChatColor color) {
        String chineseName = getChineseName(enchantmentId);
        return color.toString() + chineseName;
    }
    
    private void sendHelp(CommandSender sender) {
        if (!sender.hasPermission("yinwuenchant.use")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== YinwuEnchantments 帮助 ===");
        
        // 普通玩家只显示 list 命令
        if (!sender.hasPermission("yinwuenchant.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/ye list" + ChatColor.WHITE + " - 列出所有附魔");
        } else {
            // 管理员显示所有命令
            sender.sendMessage(ChatColor.YELLOW + "/ye give <玩家> <附魔> [等级]" + ChatColor.WHITE + " - 给予玩家附魔物品");
            sender.sendMessage(ChatColor.YELLOW + "/ye givebook <玩家> <附魔> [等级]" + ChatColor.WHITE + " - 给予玩家附魔书");
            sender.sendMessage(ChatColor.YELLOW + "/ye list" + ChatColor.WHITE + " - 列出所有附魔");
            sender.sendMessage(ChatColor.YELLOW + "/ye reload" + ChatColor.WHITE + " - 重载配置文件");
        }
        
        sender.sendMessage(ChatColor.GRAY + "使用Tab键自动补全命令和附魔名称。");
    }
    
    private String getMaterialNames(Material[] materials) {
        List<String> names = new ArrayList<>();
        for (Material material : materials) {
            names.add(getMaterialChineseName(material));
        }
        return String.join("、", names);
    }
    
    /**
     * 获取材料的中文名称
     */
    private String getMaterialChineseName(Material material) {
        switch (material) {
            // 头盔
            case LEATHER_HELMET: return "皮革头盔";
            case CHAINMAIL_HELMET: return "锁链头盔";
            case IRON_HELMET: return "铁头盔";
            case GOLDEN_HELMET: return "金头盔";
            case DIAMOND_HELMET: return "钻石头盔";
            case NETHERITE_HELMET: return "下界合金头盔";
            case TURTLE_HELMET: return "海龟壳";
            
            // 胸甲
            case LEATHER_CHESTPLATE: return "皮革胸甲";
            case CHAINMAIL_CHESTPLATE: return "锁链胸甲";
            case IRON_CHESTPLATE: return "铁胸甲";
            case GOLDEN_CHESTPLATE: return "金胸甲";
            case DIAMOND_CHESTPLATE: return "钻石胸甲";
            case NETHERITE_CHESTPLATE: return "下界合金胸甲";
            
            // 护腿
            case LEATHER_LEGGINGS: return "皮革护腿";
            case CHAINMAIL_LEGGINGS: return "锁链护腿";
            case IRON_LEGGINGS: return "铁护腿";
            case GOLDEN_LEGGINGS: return "金护腿";
            case DIAMOND_LEGGINGS: return "钻石护腿";
            case NETHERITE_LEGGINGS: return "下界合金护腿";
            
            // 靴子
            case LEATHER_BOOTS: return "皮革靴子";
            case CHAINMAIL_BOOTS: return "锁链靴子";
            case IRON_BOOTS: return "铁靴子";
            case GOLDEN_BOOTS: return "金靴子";
            case DIAMOND_BOOTS: return "钻石靴子";
            case NETHERITE_BOOTS: return "下界合金靴子";
            
            // 工具 - 镐
            case WOODEN_PICKAXE: return "木镐";
            case STONE_PICKAXE: return "石镐";
            case IRON_PICKAXE: return "铁镐";
            case GOLDEN_PICKAXE: return "金镐";
            case DIAMOND_PICKAXE: return "钻石镐";
            case NETHERITE_PICKAXE: return "下界合金镐";
            
            // 工具 - 斧
            case WOODEN_AXE: return "木斧";
            case STONE_AXE: return "石斧";
            case IRON_AXE: return "铁斧";
            case GOLDEN_AXE: return "金斧";
            case DIAMOND_AXE: return "钻石斧";
            case NETHERITE_AXE: return "下界合金斧";
            
            // 工具 - 锹
            case WOODEN_SHOVEL: return "木锹";
            case STONE_SHOVEL: return "石锹";
            case IRON_SHOVEL: return "铁锹";
            case GOLDEN_SHOVEL: return "金锹";
            case DIAMOND_SHOVEL: return "钻石锹";
            case NETHERITE_SHOVEL: return "下界合金锹";
            
            // 其他
            case SHIELD: return "盾牌";
            
            // 默认返回英文名称
            default: return material.name().toLowerCase().replace("_", " ");
        }
    }
}