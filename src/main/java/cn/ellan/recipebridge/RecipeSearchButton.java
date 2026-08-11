package cn.ellan.recipebridge;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.engine.ItemButton;
import fr.maxlego08.menu.api.engine.Pagination;
import fr.maxlego08.menu.api.utils.Placeholders;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

final class RecipeSearchButton extends PaginateButton {
    private final RecipeSearchService searchService;
    private final String sourceMenu;
    private final List<String> buttonPrefixes;

    RecipeSearchButton(
            RecipeSearchService searchService,
            String sourceMenu,
            List<String> buttonPrefixes) {
        this.searchService = searchService;
        this.sourceMenu = sourceMenu;
        this.buttonPrefixes = List.copyOf(buttonPrefixes);
    }

    @Override
    public boolean hasCustomRender() {
        return true;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        if (this.slots == null || this.slots.isEmpty()) {
            return;
        }

        List<Button> matches = this.searchService.findMatches(
                player,
                this.sourceMenu,
                this.buttonPrefixes);
        List<Button> pageButtons = new Pagination<Button>().paginate(
                matches,
                this.slots.size(),
                inventoryEngine.getPage());
        RecipeSearchService.SearchContext context = this.searchService.context(player, this.sourceMenu);

        for (int index = 0; index < Math.min(pageButtons.size(), this.slots.size()); index++) {
            Button sourceButton = pageButtons.get(index);
            Button displayButton = sourceButton.getDisplayButton(inventoryEngine, player);
            if (displayButton == null) {
                displayButton = sourceButton;
            }
            Placeholders placeholders = new Placeholders();
            placeholders.register("search_index", Integer.toString(
                    (inventoryEngine.getPage() - 1) * this.slots.size() + index + 1));
            placeholders.register("search_query", context == null ? "" : context.query());
            this.renderButton(
                    displayButton,
                    player,
                    inventoryEngine,
                    this.slots.get(index),
                    placeholders);
        }
    }

    @Override
    public int getPaginationSize(Player player) {
        return this.searchService.findMatches(player, this.sourceMenu, this.buttonPrefixes).size();
    }

    private void renderButton(
            Button button,
            Player player,
            InventoryEngine inventoryEngine,
            int slot,
            Placeholders placeholders) {
        if (button.hasPermission() && !button.checkPermission(player, inventoryEngine, placeholders)) {
            if (button.hasElseButton()) {
                this.renderButton(
                        button.getElseButton(),
                        player,
                        inventoryEngine,
                        slot,
                        placeholders);
            }
            return;
        }

        ItemStack itemStack = button.getCustomItemStack(player, false, placeholders);
        if (itemStack == null) {
            return;
        }
        ItemButton itemButton = inventoryEngine.addItem(slot, itemStack);
        if (itemButton == null) {
            return;
        }
        itemButton.setClick(event -> {
            if (event.getClick() == ClickType.DOUBLE_CLICK) {
                return;
            }
            event.setCancelled(true);
            button.onClick(player, event, inventoryEngine, event.getSlot(), placeholders);
        });
    }
}
