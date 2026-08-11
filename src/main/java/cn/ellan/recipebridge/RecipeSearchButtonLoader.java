package cn.ellan.recipebridge;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

final class RecipeSearchButtonLoader extends ButtonLoader {
    private final RecipeSearchService searchService;

    RecipeSearchButtonLoader(Plugin plugin, RecipeSearchService searchService) {
        super(plugin, "ELLAN_RECIPE_SEARCH");
        this.searchService = searchService;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaults) {
        String sourceMenu = configuration.getString(path + "source-menu", "");
        List<String> prefixes = configuration.getStringList(path + "button-prefixes");
        return new RecipeSearchButton(this.searchService, sourceMenu, prefixes);
    }
}
