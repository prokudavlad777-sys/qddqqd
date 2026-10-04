package dev.build;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class BuildPlugin extends JavaPlugin {
    private PieceManager pieces;
    private PlanItem plan;
    private NamespacedKey recipeKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pieces = new PieceManager(this);
        pieces.load();
        plan = new PlanItem(this);

        registerRecipe();

        BuildCommands commands = new BuildCommands(this);
        getCommand("upgrade").setExecutor(commands);
        getCommand("demolish").setExecutor(commands);
        getCommand("plan").setExecutor(commands);

        getServer().getPluginManager().registerEvents(new PlanListener(this), this);
    }

    @Override
    public void onDisable() {
        if (pieces != null) pieces.save();
    }

    /** Крафт: одне дерево (будь-яка колода або дошки) рівно у 5-му слоті (центр) верстака 3x3. */
    private void registerRecipe() {
        recipeKey = new NamespacedKey(this, "building_plan");
        List<Material> wood = new ArrayList<>();
        wood.addAll(Tag.PLANKS.getValues());
        wood.addAll(Tag.LOGS.getValues());

        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, plan.create());
        recipe.addIngredient(new RecipeChoice.MaterialChoice(wood));
        getServer().addRecipe(recipe);
    }

    public PieceManager pieces() {
        return pieces;
    }

    public PlanItem plan() {
        return plan;
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }
}
