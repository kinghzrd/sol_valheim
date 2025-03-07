package vice.sol_valheim;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

import java.util.*;


@Config(name = SOLValheim.MOD_ID)
@Config.Gui.Background("minecraft:textures/block/stone.png")
public class ModConfig extends PartitioningSerializer.GlobalData {

    public static Common.FoodConfig getFoodConfig(Item item) {
        var isDrink = item.getDefaultInstance().getUseAnimation() == UseAnim.DRINK;
        if(item != Items.CAKE && !item.isEdible() && !isDrink)
            return null;

        var existing = SOLValheim.Config.common.foodConfigs.get(item.arch$registryName().toString());
        if (existing == null)
        {
            var registry = item.arch$registryName().toString();

            var food = item == Items.CAKE
                    ? new FoodProperties.Builder().nutrition(10).saturationMod(0.7f).build()
                    : item.getFoodProperties();

            if (isDrink) {
                if (registry.contains("potion")) {
                    food = new FoodProperties.Builder().nutrition(4).saturationMod(0.75f).build();
                }
                else if (registry.contains("milk")) {
                    food = new FoodProperties.Builder().nutrition(6).saturationMod(1f).build();
                }
                else {
                    food = new FoodProperties.Builder().nutrition(2).saturationMod(0.5f).build();
                }
            }

            existing = new Common.FoodConfig();
            existing.nutrition = food.getNutrition();
            existing.healthRegenModifier = 1f;
            existing.saturationModifier = food.getSaturationModifier();

            if (registry.startsWith("farmers"))
            {
                existing.nutrition = (int) ((existing.nutrition * 1.25));
                existing.saturationModifier = existing.saturationModifier * 1.10f;
                existing.healthRegenModifier = 1.25f;
            }

            if (registry.equals("minecraft:golden_apple") || registry.equals("minecraft:enchanted_golden_apple")) {
                existing.nutrition = 10;
                existing.healthRegenModifier = 1.5f;
            }

//            if (registry.equals("minecraft:beetroot_soup")) {
//                var effectConfig = new Common.MobEffectConfig();
//                effectConfig.ID = BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.MOVEMENT_SPEED).toString();
//                existing.extraEffects.add(effectConfig);
//            }

            SOLValheim.Config.common.foodConfigs.put(item.arch$registryName().toString(), existing);
        }

        return existing;
    }


    @ConfigEntry.Category("common")
    @ConfigEntry.Gui.TransitiveObject()
    public Common common = new Common();

    @ConfigEntry.Category("client")
    @ConfigEntry.Gui.TransitiveObject()
    public Client client = new Client();

    @Config(name = "common")
    public static final class Common implements ConfigData {


        @ConfigEntry.Gui.Tooltip() @Comment("Default time in seconds that food should last per saturation level")
        public int defaultTimer = 180;

        @ConfigEntry.Gui.Tooltip() @Comment("Maximum number of hearts achievable via food")
        public int maxFoodHealth = 40;

        @ConfigEntry.Gui.Tooltip() @Comment("Multiplier for health gained from food")
        public float nutritionHealthModifier = 1f;

        @ConfigEntry.Gui.Tooltip() @Comment("Speed at which regeneration should occur")
        public int regenSpeedModifier = 5;

        @ConfigEntry.Gui.Tooltip() @Comment("Time in ticks that regeneration should wait after taking damage")
        public int regenDelay = 20 * 10;

        @ConfigEntry.Gui.Tooltip() @Comment("Time in seconds after spawning before sprinting is disabled")
        public int respawnGracePeriod = 60 * 5;

        @ConfigEntry.Gui.Tooltip() @Comment("Extra speed given when your hearts are full (0 to disable)")
        public float speedBoost = 0.20f;

        @ConfigEntry.Gui.Tooltip() @Comment("Number of hearts to start with")
        public int startingHealth = 3;

        @ConfigEntry.Gui.Tooltip() @Comment("Number of food slots (range 2-5, default 3)")
        public int maxSlots = 3;

        @ConfigEntry.Gui.Tooltip() @Comment("Percentage remaining before you can eat again")
        public float eatAgainPercentage = 0.2F;

        @ConfigEntry.Gui.Tooltip() @Comment("Boost given to other foods when drinking")
        public float drinkSlotFoodEffectivenessBonus = 0.10F;

        @ConfigEntry.Gui.Tooltip() @Comment("Simulate food ticking down during night")
        public boolean passTicksDuringNight = true;

        @ConfigEntry.Gui.Tooltip(count = 5) @Comment("""
            Food nutrition and effect overrides (Auto Generated if Empty)
            - nutrition: Affects Heart Gain & Health Regen
            - saturationModifier: Affects Food Duration & Player Speed
            - healthRegenModifier: Multiplies health regen speed
            - extraEffects: Extra effects provided by eating the food. Format: { String ID, float duration, int amplifier }
            - overrides: Ignore doing calculations and set the value explicitly. Values can be set to null when not overriding. Format: { int time, int health, float regen }
            
            Behaviours controlled by tags:
            #sol_valheim:resets_food - Resets all active food
            #sol_valheim:can_eat_early - Food that can be eaten prematurely. Note: Some food can be eaten early even without this tag.
        """)
        public LinkedHashMap<String, FoodConfig> foodConfigs = new LinkedHashMap<>();

        public static final class FoodConfig implements ConfigData {
            public int nutrition;
            public float saturationModifier = 1f;
            public float healthRegenModifier = 1f;
            public List<MobEffectConfig> extraEffects = new ArrayList<>();

            public OverridesConfig overrides = null;

            public int getTime() {
                return (overrides != null && overrides.time != null) ?
                        overrides.time : (int) Math.max(SOLValheim.Config.common.defaultTimer * 20 * saturationModifier * nutrition, 6000);
            }

            public int getHearts() {
                return (overrides != null && overrides.health != null) ?
                         overrides.health : Math.round(Math.max(nutrition * SOLValheim.Config.common.nutritionHealthModifier, 2));
            }

            public float getHealthRegen() {
                return (overrides != null && overrides.regen != null) ?
                        overrides.regen: Mth.clamp(nutrition * 0.10f * healthRegenModifier,0.25f, 2f);
            }

            @Override
            public String toString() {
                return "FoodConfig{" +
                        "nutrition=" + nutrition +
                        ", saturationModifier=" + saturationModifier +
                        ", healthRegenModifier=" + healthRegenModifier +
                        ", extraEffects=" + extraEffects +
                        '}';
            }
        }

        public static final class MobEffectConfig implements ConfigData {
            @ConfigEntry.Gui.Tooltip() @Comment("Mob Effect ID")
            public String ID;

            @ConfigEntry.Gui.Tooltip() @Comment("Effect duration percentage (1f is the entire food duration)")
            public float duration = 1f;

            @ConfigEntry.Gui.Tooltip() @Comment("Effect Level")
            public int amplifier = 1;

            public MobEffect getEffect() {
                return SOLValheim.MOB_EFFECTS.getRegistrar().get(new ResourceLocation(ID));
            }
        }

        public static final class OverridesConfig implements ConfigData {
            @ConfigEntry.Gui.Tooltip() @Comment("How long the specified food lasts in ticks")
            public Integer time;

            @ConfigEntry.Gui.Tooltip() @Comment("How much health the specified food gives (1 = half a heart)")
            public Integer health;

            @ConfigEntry.Gui.Tooltip() @Comment("How much regen the specified food gives")
            public Float regen;
        }

    }

    @Config(name = "client")
    public static final class Client implements ConfigData {
        @ConfigEntry.Gui.Tooltip @Comment("Enlarge the currently eaten food icons, small icons disable timer text")
        public boolean useLargeIcons = true;
        @ConfigEntry.Gui.Tooltip @Comment("Position configuration for the root food hud")
        public FoodComponentConfig foodHudConfig = new FoodComponentConfig();
        @ConfigEntry.Gui.Tooltip @Comment("Show regen delay meter")
        public boolean showRegenMeter = true;
        @ConfigEntry.Gui.Tooltip @Comment("Position configuration for the regen indicator")
        public RegenComponentConfig regenHudConfig = new RegenComponentConfig();

        public static class RegenComponentConfig {
            @ConfigEntry.Gui.Tooltip @Comment("X position offset in scaled pixels")
            public int xOffset = -100;
            @ConfigEntry.Gui.Tooltip @Comment("Y position offset in scaled pixels")
            public int yOffset = -39;
            @ConfigEntry.Gui.Tooltip @Comment("X position relative to screen, 0 = left, 1 = right")
            public float xAnchor = 0.5f;
            @ConfigEntry.Gui.Tooltip @Comment("Y position relative to screen, 0 = up, 1 = down")
            public float yAnchor = 1.0f;
        }

        public static class SlotComponentConfig {
            @ConfigEntry.Gui.Tooltip @Comment("X position offset in scaled pixels")
            public int xOffset = 0;
            @ConfigEntry.Gui.Tooltip @Comment("Y position offset in scaled pixels")
            public int yOffset = 0;
        }

        public static class FoodComponentConfig {
            @ConfigEntry.Gui.Tooltip @Comment("X position offset in scaled pixels")
            public int xOffset = -92;
            @ConfigEntry.Gui.Tooltip @Comment("Y position offset in scaled pixels")
            public int yOffset = -39;
            @ConfigEntry.Gui.Tooltip @Comment("X position relative to screen, 0 = left, 1 = right")
            public float xAnchor = 0.5f;
            @ConfigEntry.Gui.Tooltip @Comment("Y position relative to screen, 0 = up, 1 = down")
            public float yAnchor = 1.0f;
            @ConfigEntry.Gui.Tooltip @Comment("X position multiplier between elements, 1 = shift right, -1 = shift left, 0 = unaffected")
            public int xGap = -1;
            @ConfigEntry.Gui.Tooltip @Comment("Y position multiplier between elements, 1 = shift down, -1 = shift up, 0 = unaffected")
            public int yGap = 0;
            @ConfigEntry.Gui.Tooltip @Comment("""
                    You should have the same number of entries as your max number of slots + drink slot, otherwise some slots may be un-styled.
                    Should follow the format: {int xOffset, int yOffset} for each slot you want to style. First entry affects first/rightmost slot, second affects second slot, etc...
                    """)
            public List<SlotComponentConfig> slotOffsets = new ArrayList<>();
        }
    }
}