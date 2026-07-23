/*
 * Copyright (c) 2026 EllanServer contributors.
 * Licensed under the MIT License.
 */
package cn.ellan.recipebridge;

import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.players.Data;
import fr.maxlego08.menu.api.players.DataManager;
import fr.maxlego08.menu.players.ZData;
import io.papermc.paper.persistence.PersistentDataContainerView;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.momirealms.craftengine.bukkit.item.BukkitItem;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class EllanRecipeBridge
extends JavaPlugin
implements Listener {
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern BREW_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final NamespacedKey BREWERY_TAG = new NamespacedKey("brewery", "tag");
    private static final NamespacedKey BREWERY_SCORE = new NamespacedKey("brewery", "score");
    private static final NamespacedKey BREWERY_VERSION = new NamespacedKey("brewery", "version");
    private DataManager dataManager;
    private InventoryManager inventoryManager;
    private boolean playerDataReloadScheduled;

    public void onEnable() {
        this.dataManager = (DataManager)Bukkit.getServicesManager().load(DataManager.class);
        this.inventoryManager = (InventoryManager)Bukkit.getServicesManager().load(InventoryManager.class);
        if (this.dataManager == null || this.inventoryManager == null) {
            this.getLogger().severe("zMenu data services are unavailable; disabling to prevent item loss.");
            Bukkit.getPluginManager().disablePlugin((Plugin)this);
            return;
        }
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this);
        this.getLogger().info("\u827e\u5c14\u5c9a\u98df\u8c31 CE \u7269\u54c1\u63d0\u4ea4\u6865\u63a5\u5df2\u542f\u7528\u3002");
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new RecipePlaceholderExpansion().register();
        } else {
            this.getLogger().warning("PlaceholderAPI \u672a\u542f\u7528\uff0c\u9152\u8c31\u7814\u7a76\u8fdb\u5ea6\u6761\u5c06\u65e0\u6cd5\u663e\u793a\u3002");
        }
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player)commandSender;
        if (command.getName().equalsIgnoreCase("ellanbrewsubmit")) {
            return this.submitSealedBrew(player, stringArray);
        }
        return this.submitCraftEngineItem(player, stringArray);
    }

    private boolean submitCraftEngineItem(Player player, String[] stringArray) {
        if (stringArray.length != 1) {
            player.sendMessage("\u00a7c\u98df\u8c31\u63d0\u4ea4\u53c2\u6570\u65e0\u6548\uff0c\u8bf7\u4ece\u827e\u5c14\u5c9a\u98df\u8c31\u83dc\u5355\u63d0\u4ea4\u3002");
            return true;
        }
        String string = stringArray[0].toLowerCase(Locale.ROOT);
        if (!ITEM_ID.matcher(string).matches()) {
            player.sendMessage("\u00a7c\u98df\u8c31\u63d0\u4ea4\u53c2\u6570\u65e0\u6548\uff0c\u8bf7\u4ece\u827e\u5c14\u5c9a\u98df\u8c31\u83dc\u5355\u63d0\u4ea4\u3002");
            return true;
        }
        if (!this.hasOne(player, string)) {
            player.sendMessage("\u00a7c\u80cc\u5305\u4e2d\u6ca1\u6709\u53ef\u63d0\u4ea4\u7684\u8be5\u6210\u54c1\u3002");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }
        String string2 = "ellan_recipe_" + string.replaceAll("[^a-z0-9_]", "_");
        if (!this.setPlayerValue(player, string2, "true")) {
            player.sendMessage("\u00a7c\u98df\u8c31\u8bb0\u5f55\u5931\u8d25\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u3002");
            return true;
        }
        this.consumeOne(player, string);
        this.refreshOpenMenu(player);
        player.sendMessage("\u00a76\u827e\u5c14\u5c9a\u98df\u8c31 \u00a78| \u00a7a\u5df2\u8bb0\u5f55\u8be5\u6210\u54c1\u7684\u5236\u4f5c\u65b9\u6cd5\u3002");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        return true;
    }

    private boolean submitSealedBrew(Player player, String[] stringArray) {
        if (stringArray.length != 1 || !BREW_ID.matcher(stringArray.length == 0 ? "" : stringArray[0]).matches()) {
            player.sendMessage("\u00a7c\u9152\u8c31\u63d0\u4ea4\u53c2\u6570\u65e0\u6548\uff0c\u8bf7\u4ece\u827e\u5c14\u5c9a\u917f\u9020\u5fd7\u63d0\u4ea4\u3002");
            return true;
        }
        String string = stringArray[0].toLowerCase(Locale.ROOT);
        BrewSample brewSample = this.findSealedBrew(player, string);
        if (brewSample == null) {
            if (this.hasUnsealedBrew(player, string)) {
                player.sendMessage("\u00a7e\u8bf7\u4e3b\u624b\u62ff\u8be5\u9152\u6837\u3001\u526f\u624b\u62ff\u7eb8\uff0c\u00a76\u6f5c\u884c\u53f3\u952e\u5de5\u4f5c\u53f0\u00a7e \u5b8c\u6210\u5c01\u53e3\u540e\u518d\u63d0\u4ea4\u9152\u8c31\u3002");
            } else if (this.hasUnidentifiedZeroBrew(player)) {
                player.sendMessage("\u00a7e\u8fd9\u74f6\u96f6\u661f\u9152\u6837\u6ca1\u6709\u4fdd\u7559\u53ef\u8fa8\u8ba4\u7684\u914d\u65b9\u8eab\u4efd\uff0c\u65e0\u6cd5\u5b89\u5168\u5f52\u6863\uff1b\u5b83\u4e0d\u4f1a\u88ab\u6d88\u8017\u3002");
            } else {
                player.sendMessage("\u00a7c\u80cc\u5305\u4e2d\u6ca1\u6709\u53ef\u63d0\u4ea4\u7684\u8be5\u914d\u65b9\u5c01\u53e3\u9152\u6837\u3002");
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }
        int n = this.halfStarsForScore(brewSample.score());
        int n2 = this.getBrewProgressUnits(player, string);
        if (n == 0) {
            if (this.hasBrewZeroNote(player, string)) {
                player.sendMessage("\u00a7e\u8be5\u9152\u8c31\u7684\u96f6\u661f\u5931\u8d25\u7b14\u8bb0\u5df2\u5f52\u6863\uff1b\u8bf7\u63d0\u4ea4\u81f3\u5c11\u534a\u661f\u7684\u5c01\u53e3\u9152\u6837\u63a8\u8fdb\u7814\u7a76\u3002");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }
            if (!this.setPlayerValue(player, this.brewZeroKey(string), "true")) {
                player.sendMessage("\u00a7c\u9152\u8c31\u8bb0\u5f55\u5931\u8d25\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u3002");
                return true;
            }
            this.consumeSlot(player, brewSample.slot());
            this.refreshOpenMenu(player);
            player.sendMessage("\u00a76\u827e\u5c14\u5c9a\u917f\u9020\u5fd7 \u00a78| \u00a7e\u5df2\u5f52\u6863\u96f6\u661f\u5931\u8d25\u7b14\u8bb0\u3002\u5b83\u4e0d\u4f1a\u63a8\u8fdb\u661f\u7ea7\uff0c\u4f46\u7559\u4e0b\u4e86\u7b2c\u4e00\u6761\u7ebf\u7d22\u3002 ");
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.0f);
            return true;
        }
        if (n <= n2) {
            player.sendMessage("\u00a7e\u8be5\u9152\u8c31\u5f53\u524d\u5df2\u7814\u7a76\u81f3 \u00a76" + this.formatHalfStars(n2) + " \u661f\u00a7e\uff0c\u9700\u8981\u66f4\u9ad8\u661f\u7ea7\u7684\u5c01\u53e3\u9152\u6837\u624d\u80fd\u63a8\u8fdb\u3002");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }
        if (!this.setPlayerValue(player, this.brewProgressKey(string), Integer.toString(n))) {
            player.sendMessage("\u00a7c\u9152\u8c31\u8bb0\u5f55\u5931\u8d25\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u3002");
            return true;
        }
        if (n == 10) {
            this.setPlayerValue(player, this.legacyBrewKey(string), "true");
        }
        this.consumeSlot(player, brewSample.slot());
        this.refreshOpenMenu(player);
        String string2 = this.starsForUnits(n);
        if (n == 10) {
            player.sendMessage("\u00a76\u827e\u5c14\u5c9a\u917f\u9020\u5fd7 \u00a78| \u00a7a\u5df2\u767b\u8bb0\u4e94\u661f\u5b8c\u7f8e\u9152\u6837 \u00a7e" + string2 + "\u00a7a\uff0c\u5b8c\u6574\u5de5\u827a\u5df2\u89e3\u9501\u3002");
        } else {
            player.sendMessage("\u00a76\u827e\u5c14\u5c9a\u917f\u9020\u5fd7 \u00a78| \u00a7a\u7814\u7a76\u63a8\u8fdb\u81f3 \u00a7e" + this.formatHalfStars(n) + " \u661f " + string2 + "\u00a7a\uff0c\u65b0\u7684\u7ebf\u7d22\u5df2\u8bb0\u5f55\u3002");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        return true;
    }

    private boolean consumeOne(Player player, String string) {
        PlayerInventory playerInventory = player.getInventory();
        for (int i = 0; i < 36; ++i) {
            BukkitItem bukkitItem;
            ItemStack itemStack = playerInventory.getItem(i);
            if (itemStack == null || itemStack.getType().isAir() || (bukkitItem = BukkitItemManager.instance().wrap((Object)itemStack)) == null || !string.equals(bukkitItem.id().asString())) continue;
            if (itemStack.getAmount() <= 1) {
                playerInventory.setItem(i, null);
            } else {
                itemStack.setAmount(itemStack.getAmount() - 1);
            }
            return true;
        }
        return false;
    }

    private BrewSample findSealedBrew(Player player, String string) {
        PlayerInventory playerInventory = player.getInventory();
        for (int i = 0; i < 36; ++i) {
            ItemStack itemStack = playerInventory.getItem(i);
            if (itemStack == null || itemStack.getType().isAir()) continue;
            PersistentDataContainerView persistentDataContainerView = itemStack.getPersistentDataContainer();
            String string2 = (String)persistentDataContainerView.get(BREWERY_TAG, PersistentDataType.STRING);
            Double d = (Double)persistentDataContainerView.get(BREWERY_SCORE, PersistentDataType.DOUBLE);
            if (!this.matchesRecipeTag(string2, string) || d == null || persistentDataContainerView.has(BREWERY_VERSION, PersistentDataType.INTEGER)) continue;
            return new BrewSample(i, d);
        }
        return null;
    }

    private boolean hasUnsealedBrew(Player player, String string) {
        for (int i = 0; i < 36; ++i) {
            PersistentDataContainerView persistentDataContainerView;
            ItemStack itemStack = player.getInventory().getItem(i);
            if (itemStack == null || itemStack.getType().isAir() || !this.matchesRecipeTag((String)(persistentDataContainerView = itemStack.getPersistentDataContainer()).get(BREWERY_TAG, PersistentDataType.STRING), string) || !persistentDataContainerView.has(BREWERY_VERSION, PersistentDataType.INTEGER)) continue;
            return true;
        }
        return false;
    }

    private boolean hasUnidentifiedZeroBrew(Player player) {
        for (int i = 0; i < 36; ++i) {
            ItemStack itemStack = player.getInventory().getItem(i);
            if (itemStack == null || itemStack.getType().isAir()) continue;
            PersistentDataContainerView persistentDataContainerView = itemStack.getPersistentDataContainer();
            Double d = (Double)persistentDataContainerView.get(BREWERY_SCORE, PersistentDataType.DOUBLE);
            String string = (String)persistentDataContainerView.get(BREWERY_TAG, PersistentDataType.STRING);
            if (d == null || !(d <= 1.0E-9) || string != null || persistentDataContainerView.has(BREWERY_VERSION, PersistentDataType.INTEGER)) continue;
            return true;
        }
        return false;
    }

    private boolean matchesRecipeTag(String string, String string2) {
        return string2.equals(string) || ("brewery:" + string2).equals(string);
    }

    private int halfStarsForScore(double d) {
        return Math.max(0, Math.min(10, (int)Math.floor(d * 10.0 + 1.0E-9)));
    }

    private String brewProgressKey(String string) {
        return this.legacyBrewKey(string) + "_half_stars";
    }

    private String oldBrewProgressKey(String string) {
        return this.legacyBrewKey(string) + "_stars";
    }

    private String brewZeroKey(String string) {
        return this.legacyBrewKey(string) + "_zero";
    }

    private String legacyBrewKey(String string) {
        return "ellan_brew_" + string.replaceAll("[^a-z0-9_]", "_");
    }

    private int getBrewProgressUnits(Player player, String string) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return 0;
        }
        String string2 = PlaceholderAPI.setPlaceholders((Player)player, (String)("%zmenu_player_value_" + this.legacyBrewKey(string) + "%"));
        if ("true".equalsIgnoreCase(string2)) {
            return 10;
        }
        String string3 = PlaceholderAPI.setPlaceholders((Player)player, (String)("%zmenu_player_value_" + this.brewProgressKey(string) + "%"));
        try {
            int n = Integer.parseInt(string3);
            if (n > 0) {
                return Math.max(0, Math.min(10, n));
            }
        }
        catch (NumberFormatException numberFormatException) {
            // empty catch block
        }
        String string4 = PlaceholderAPI.setPlaceholders((Player)player, (String)("%zmenu_player_value_" + this.oldBrewProgressKey(string) + "%"));
        try {
            return Math.max(0, Math.min(5, Integer.parseInt(string4))) * 2;
        }
        catch (NumberFormatException numberFormatException) {
            return 0;
        }
    }

    private boolean hasBrewZeroNote(Player player, String string) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return false;
        }
        String string2 = PlaceholderAPI.setPlaceholders((Player)player, (String)("%zmenu_player_value_" + this.brewZeroKey(string) + "%"));
        return "true".equalsIgnoreCase(string2);
    }

    private boolean setPlayerValue(Player player, String string, String string2) {
        try {
            this.dataManager.addData(player.getUniqueId(), (Data)new ZData(string, (Object)string2, 0L));
            return this.dataManager.getData(player.getUniqueId(), string).map(data -> string2.equalsIgnoreCase(String.valueOf(data.getValue()))).orElse(false);
        }
        catch (RuntimeException runtimeException) {
            this.getLogger().log(Level.SEVERE, "Could not persist zMenu value " + string + " for " + String.valueOf(player.getUniqueId()), runtimeException);
            return false;
        }
    }

    private void refreshOpenMenu(Player player) {
        this.inventoryManager.updateInventory(player);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent playerJoinEvent) {
        if (this.playerDataReloadScheduled) {
            return;
        }
        this.playerDataReloadScheduled = true;
        Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
            this.playerDataReloadScheduled = false;
            try {
                this.dataManager.loadPlayers();
            }
            catch (RuntimeException runtimeException) {
                this.getLogger().log(Level.WARNING, "Could not refresh shared zMenu player data after a server transfer", runtimeException);
            }
        }, 40L);
    }

    private String formatHalfStars(int n) {
        int n2 = n / 2;
        return n % 2 == 0 ? Integer.toString(n2) : n2 + ".5";
    }

    private String starsForUnits(int n) {
        int n2 = n / 2;
        String string = n % 2 == 0 ? "" : "\u2726";
        return "\u2605".repeat(n2) + string + "\u2606".repeat(5 - n2 - n % 2);
    }

    private void consumeSlot(Player player, int n) {
        ItemStack itemStack = player.getInventory().getItem(n);
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        if (itemStack.getAmount() <= 1) {
            player.getInventory().setItem(n, null);
        } else {
            itemStack.setAmount(itemStack.getAmount() - 1);
        }
    }

    private boolean hasOne(Player player, String string) {
        PlayerInventory playerInventory = player.getInventory();
        for (int i = 0; i < 36; ++i) {
            BukkitItem bukkitItem;
            ItemStack itemStack = playerInventory.getItem(i);
            if (itemStack == null || itemStack.getType().isAir() || (bukkitItem = BukkitItemManager.instance().wrap((Object)itemStack)) == null || !string.equals(bukkitItem.id().asString())) continue;
            return true;
        }
        return false;
    }

    private final class RecipePlaceholderExpansion
    extends PlaceholderExpansion {
        private RecipePlaceholderExpansion() {
        }

        public String getIdentifier() {
            return "ellanrecipe";
        }

        public String getAuthor() {
            return "Ellan";
        }

        public String getVersion() {
            return EllanRecipeBridge.this.getDescription().getVersion();
        }

        public boolean persist() {
            return true;
        }

        public String onPlaceholderRequest(Player player, String string) {
            if (player == null) {
                return null;
            }
            if (string.startsWith("brew_progress_")) {
                int n = EllanRecipeBridge.this.getBrewProgressUnits(player, string.substring("brew_progress_".length()));
                return "[" + "\u25a0".repeat(n) + "\u25a1".repeat(10 - n) + "] " + EllanRecipeBridge.this.formatHalfStars(n) + "/5 \u661f";
            }
            if (string.startsWith("brew_stage_")) {
                String string2 = string.substring("brew_stage_".length());
                return this.stageText(EllanRecipeBridge.this.getBrewProgressUnits(player, string2), EllanRecipeBridge.this.hasBrewZeroNote(player, string2));
            }
            if (string.startsWith("brew_target_")) {
                String string3 = string.substring("brew_target_".length());
                return this.targetText(EllanRecipeBridge.this.getBrewProgressUnits(player, string3), EllanRecipeBridge.this.hasBrewZeroNote(player, string3));
            }
            return null;
        }

        private String stageText(int n, boolean bl) {
            return switch (n) {
                case 0 -> {
                    if (bl) {
                        yield "\u96f6\u661f\u5931\u8d25\u7b14\u8bb0\u5df2\u5f52\u6863\uff0c\u5df2\u6392\u9664\u4e00\u6761\u9519\u8bef\u8def\u7ebf\u3002";
                    }
                    yield "\u5c1a\u672a\u7559\u4e0b\u53ef\u8fa8\u8ba4\u7684\u9152\u6837\u3002";
                }
                case 1 -> "\u5df2\u6355\u6349\u5230\u6a21\u7cca\u98ce\u5473\uff0c\u6b63\u5728\u8fa8\u8ba4\u7b2c\u4e00\u9053\u5de5\u827a\u3002";
                case 2 -> "\u5df2\u9a8c\u8bc1\u57fa\u7840\u98ce\u5473\u4e0e\u9996\u9053\u5de5\u827a\u3002";
                case 3 -> "\u5df2\u9501\u5b9a\u4e3b\u8981\u539f\u6599\uff0c\u6b63\u5728\u6821\u51c6\u7528\u91cf\u3002";
                case 4 -> "\u539f\u6599\u4e0e\u7528\u91cf\u5df2\u8bb0\u5f55\uff0c\u6b63\u5728\u6821\u51c6\u65f6\u95f4\u4e0e\u540e\u7eed\u5904\u7406\u3002";
                case 5, 6 -> "\u5de5\u827a\u6846\u67b6\u9010\u6e10\u6e05\u6670\uff0c\u4ecd\u9700\u66f4\u9ad8\u54c1\u8d28\u9152\u6837\u6821\u9a8c\u3002";
                case 7, 8 -> "\u5173\u952e\u6b65\u9aa4\u5df2\u88ab\u786e\u8ba4\uff0c\u6b63\u5728\u6536\u675f\u6700\u7ec8\u5de5\u827a\u3002";
                case 9 -> "\u5de5\u827a\u6846\u67b6\u5df2\u5b8c\u6574\uff0c\u7b49\u5f85\u5b8c\u7f8e\u9152\u6837\u8fdb\u884c\u6700\u7ec8\u6821\u9a8c\u3002";
                default -> "\u5b8c\u6574\u914d\u65b9\u5df2\u5f52\u6863\u3002";
            };
        }

        private String targetText(int n, boolean bl) {
            return switch (n) {
                case 0 -> {
                    if (bl) {
                        yield "\u4e0b\u4e00\u76ee\u6807\uff1a\u63d0\u4ea4\u81f3\u5c11\u534a\u661f\u7684\u5c01\u53e3\u9152\u6837\u3002";
                    }
                    yield "\u63d0\u4ea4\u534a\u661f\u6216\u66f4\u9ad8\u7684\u5c01\u53e3\u9152\u6837\uff0c\u5f00\u542f\u7814\u7a76\u3002";
                }
                case 1 -> "\u4e0b\u4e00\u76ee\u6807\uff1a\u63d0\u4ea4\u81f3\u5c11\u4e00\u661f\u7684\u5c01\u53e3\u9152\u6837\u3002";
                case 2 -> "\u4e0b\u4e00\u76ee\u6807\uff1a\u63d0\u4ea4\u81f3\u5c11 1.5 \u661f\u7684\u5c01\u53e3\u9152\u6837\u3002";
                case 3 -> "\u4e0b\u4e00\u76ee\u6807\uff1a\u63d0\u4ea4\u81f3\u5c11\u4e8c\u661f\u7684\u5c01\u53e3\u9152\u6837\u3002";
                case 4 -> "\u4e0b\u4e00\u76ee\u6807\uff1a\u63d0\u4ea4\u81f3\u5c11 2.5 \u661f\u7684\u5c01\u53e3\u9152\u6837\u3002";
                case 5 -> "\u4e0b\u4e00\u76ee\u6807\uff1a\u63d0\u4ea4\u81f3\u5c11\u4e09\u661f\u7684\u5c01\u53e3\u9152\u6837\u3002";
                case 6 -> "\u4e0b\u4e00\u76ee\u6807\uff1a\u63d0\u4ea4\u81f3\u5c11 3.5 \u661f\u7684\u5c01\u53e3\u9152\u6837\u3002";
                case 7 -> "\u4e0b\u4e00\u76ee\u6807\uff1a\u63d0\u4ea4\u81f3\u5c11\u56db\u661f\u7684\u5c01\u53e3\u9152\u6837\u3002";
                case 8 -> "\u4e0b\u4e00\u76ee\u6807\uff1a\u63d0\u4ea4\u81f3\u5c11 4.5 \u661f\u7684\u5c01\u53e3\u9152\u6837\u3002";
                case 9 -> "\u6700\u7ec8\u76ee\u6807\uff1a\u63d0\u4ea4\u4e94\u661f\u5b8c\u7f8e\u5c01\u53e3\u9152\u6837\u3002";
                default -> "\u7814\u7a76\u5df2\u5b8c\u6210\uff0c\u65e0\u9700\u518d\u63d0\u4ea4\u9152\u6837\u3002";
            };
        }
    }

    private record BrewSample(int slot, double score) {
    }
}

