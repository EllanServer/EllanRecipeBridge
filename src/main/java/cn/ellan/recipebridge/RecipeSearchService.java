package cn.ellan.recipebridge;

import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.buttons.SwitchButton;
import fr.maxlego08.menu.api.exceptions.InventoryException;
import fr.maxlego08.menu.api.utils.SwitchCaseButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

final class RecipeSearchService implements Listener {
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)[&\u00a7][0-9A-FK-ORX]");
    private static final Pattern STATUS_SUFFIX = Pattern.compile("[（(][^）)]*(?:解锁|发现|研究)[^）)]*[）)]");
    private static final Map<String, SearchDefinition> DEFINITIONS = Map.of(
            "recipebook", new SearchDefinition(
                    "recipebook",
                    "inventories/recipebook_search.yml",
                    List.of("recipe_")),
            "ellan_recipe", new SearchDefinition(
                    "ellan_recipe",
                    "inventories/ellan_recipe_search.yml",
                    List.of("recipe_")),
            "ellan_brewing", new SearchDefinition(
                    "ellan_brewing",
                    "inventories/ellan_brewing_search.yml",
                    List.of("brew_", "tavern_")));

    private final EllanRecipeBridge plugin;
    private final InventoryManager inventoryManager;
    private final ButtonManager buttonManager;
    private final Map<UUID, SearchContext> contexts = new HashMap<>();
    private final Map<String, Inventory> searchInventories = new HashMap<>();

    RecipeSearchService(EllanRecipeBridge plugin, InventoryManager inventoryManager) {
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
        this.buttonManager = inventoryManager.getPlugin().getButtonManager();
    }

    void enable() {
        this.buttonManager.register(new RecipeSearchButtonLoader(this.plugin, this));
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        this.loadSearchInventories();
    }

    void disable() {
        HandlerList.unregisterAll(this);
        this.buttonManager.unregisters(this.plugin);
        this.inventoryManager.deleteInventories(this.plugin);
        this.contexts.clear();
        this.searchInventories.clear();
    }

    boolean search(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("\u00a7c搜索参数无效，请从配方菜单的搜索按钮开始搜索。");
            return true;
        }

        String sourceName = args[0].toLowerCase(Locale.ROOT);
        SearchDefinition definition = DEFINITIONS.get(sourceName);
        if (definition == null) {
            player.sendMessage("\u00a7c未知的配方菜单，无法开始搜索。");
            return true;
        }

        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (query.isEmpty() || query.length() > 32) {
            player.sendMessage("\u00a7c请输入 1 至 32 个字符的配方名称。");
            return true;
        }

        this.contexts.put(player.getUniqueId(), new SearchContext(sourceName, query));
        int resultCount = this.findMatches(player, sourceName, definition.buttonPrefixes()).size();
        Inventory searchInventory = this.searchInventories.get(sourceName);
        if (searchInventory == null) {
            player.sendMessage("\u00a7c搜索页面尚未加载，请联系管理员执行 /ellanrecipereload。");
            return true;
        }

        Bukkit.getScheduler().runTask(this.plugin, () ->
                this.inventoryManager.openInventory(player, searchInventory, 1));
        if (resultCount == 0) {
            player.sendMessage("\u00a76艾尔岚配方检索 \u00a78| \u00a7e没有找到包含“" + query + "”的配方。");
        } else {
            player.sendMessage("\u00a76艾尔岚配方检索 \u00a78| \u00a7a找到 \u00a7e" + resultCount + " \u00a7a项相关配方。");
        }
        return true;
    }

    boolean reload(CommandSender sender) {
        if (!sender.hasPermission("ellanrecipe.reload")) {
            sender.sendMessage("\u00a7c你没有重载艾尔岚配方菜单的权限。");
            return true;
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "zmenu reload");
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.loadSearchInventories();
            sender.sendMessage("\u00a76艾尔岚配方检索 \u00a78| \u00a7a三个配方菜单及搜索页面已重载。");
        }, 2L);
        return true;
    }

    List<Button> findMatches(Player player, String sourceName, List<String> buttonPrefixes) {
        SearchContext context = this.contexts.get(player.getUniqueId());
        if (context == null || !context.sourceName().equals(sourceName)) {
            return List.of();
        }

        Inventory source = this.inventoryManager.findInventory(sourceName).orElse(null);
        if (source == null) {
            return List.of();
        }

        String normalizedQuery = normalize(context.query());
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }
        List<String> queryParts = Arrays.stream(normalizedQuery.split("\\s+"))
                .filter(part -> !part.isBlank())
                .toList();

        return source.getButtons().stream()
                .filter(button -> hasPrefix(button.getName(), buttonPrefixes))
                .filter(button -> {
                    String haystack = searchableText(player, button);
                    return queryParts.stream().allMatch(haystack::contains);
                })
                .sorted(Comparator.comparingInt(Button::getPage).thenComparingInt(Button::getSlot))
                .toList();
    }

    SearchContext context(Player player, String sourceName) {
        SearchContext context = this.contexts.get(player.getUniqueId());
        return context != null && context.sourceName().equals(sourceName) ? context : null;
    }

    private void loadSearchInventories() {
        this.inventoryManager.deleteInventories(this.plugin);
        this.searchInventories.clear();
        for (SearchDefinition definition : DEFINITIONS.values()) {
            try {
                Inventory inventory = this.inventoryManager.loadInventoryOrSaveResource(
                        this.plugin,
                        definition.resourcePath());
                this.searchInventories.put(definition.sourceName(), inventory);
            } catch (InventoryException | RuntimeException exception) {
                this.plugin.getLogger().log(
                        Level.SEVERE,
                        "Could not load recipe search inventory " + definition.resourcePath(),
                        exception);
            }
        }
    }

    private static boolean hasPrefix(String buttonName, List<String> prefixes) {
        if (buttonName == null) {
            return false;
        }
        return prefixes.stream().anyMatch(buttonName::startsWith);
    }

    private static String searchableText(Player player, Button button) {
        List<String> names = new ArrayList<>();
        appendButtonNames(player, button, names);
        return normalize(String.join(" ", names));
    }

    private static void appendButtonNames(Player player, Button button, List<String> names) {
        try {
            String displayName = button.buildDisplayName(player);
            if (displayName != null) {
                names.add(displayName);
            }
        } catch (RuntimeException ignored) {
        }

        if (button.hasElseButton()) {
            appendButtonNames(player, button.getElseButton(), names);
        }
        if (button instanceof SwitchButton switchButton) {
            for (SwitchCaseButton switchCase : switchButton.getButtons()) {
                appendButtonNames(player, switchCase.button(), names);
            }
        }
    }

    private static String normalize(String value) {
        String cleaned = MINI_MESSAGE_TAG.matcher(value).replaceAll("");
        cleaned = LEGACY_COLOR.matcher(cleaned).replaceAll("");
        cleaned = STATUS_SUFFIX.matcher(cleaned).replaceAll("");
        return cleaned
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.contexts.remove(event.getPlayer().getUniqueId());
    }

    record SearchContext(String sourceName, String query) {
    }

    private record SearchDefinition(String sourceName, String resourcePath, List<String> buttonPrefixes) {
    }
}
