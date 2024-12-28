package vice.sol_valheim;

#if PRE_CURRENT_MC_1_19_2
import dev.architectury.registry.registries.Registries;
import net.minecraft.core.Registry;
#elif POST_CURRENT_MC_1_20_1
import net.minecraft.core.registries.Registries;
#endif
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ValheimFoodData
{
    #if PRE_CURRENT_MC_1_19_2
    public static final TagKey<Item> RESETS_FOOD = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("sol_valheim", "resets_food"));
    public static final TagKey<Item> CAN_EAT_EARLY = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("sol_valheim", "can_eat_early"));
	#elif POST_CURRENT_MC_1_20_1
    public static final TagKey<Item> RESETS_FOOD = TagKey.create(Registries.ITEM, new ResourceLocation("sol_valheim", "resets_food"));
    public static final TagKey<Item> CAN_EAT_EARLY = TagKey.create(Registries.ITEM, new ResourceLocation("sol_valheim", "can_eat_early"));

    #endif
    public static final EntityDataSerializer<ValheimFoodData> FOOD_DATA_SERIALIZER = new EntityDataSerializer<>(){
        @Override
        public void write(FriendlyByteBuf buffer, ValheimFoodData value)
        {
            buffer.writeNbt(value.save(new CompoundTag()));
        }

        @Override
        public ValheimFoodData read(FriendlyByteBuf buffer) {
            return ValheimFoodData.read(buffer.readNbt());
        }

        @Override
        public ValheimFoodData copy(ValheimFoodData value)
        {
            var ret = new ValheimFoodData();
            ret.MaxItemSlots = value.MaxItemSlots;
            ret.ItemEntries = value.ItemEntries.stream().map(EatenFoodItem::new).collect(Collectors.toCollection(ArrayList::new));
            if (value.DrinkSlot != null)
                ret.DrinkSlot = new EatenFoodItem(value.DrinkSlot);
            return ret;
        }

    };

    public List<EatenFoodItem> ItemEntries = new ArrayList<>();
    public EatenFoodItem DrinkSlot;
    public int MaxItemSlots = SOLValheim.Config.common.maxSlots;

    public void eatItem(Item food)
    {
        if (food.getDefaultInstance().is(RESETS_FOOD))
            return;

        var config = ModConfig.getFoodConfig(food);
        if (config == null)
            return;

        var isDrink = food.getDefaultInstance().getUseAnimation() == UseAnim.DRINK;
        if (isDrink) {
            if (DrinkSlot != null && !DrinkSlot.canEatEarly())
                return;

            if (DrinkSlot == null)
                DrinkSlot = new EatenFoodItem(food, config.getTime());
            else {
                DrinkSlot.ticksLeft = config.getTime();
                DrinkSlot.item = food;
            }

            return;
        }

        var existing = getEatenFood(food);
        if (existing != null)
        {
            if (!existing.canEatEarly())
                return;

            existing.ticksLeft = config.getTime();
            return;
        }

        if (ItemEntries.size() < MaxItemSlots)
        {
            ItemEntries.add(new EatenFoodItem(food, config.getTime()));
            return;
        }

        for (var item : ItemEntries)
        {
            if (item.canEatEarly())
            {
                item.ticksLeft = config.getTime();
                item.item = food;
                return;
            }
        }
    }

    public boolean canEat(Item food)
    {
        var foodItem = food.getDefaultInstance();
        if (foodItem.is(RESETS_FOOD))
            return true;

        var isConsumablePotion = (foodItem.getUseAnimation() == UseAnim.DRINK && !(food.getClass().equals(SplashPotionItem.class) || food.getClass().equals(LingeringPotionItem.class)));
        if (isConsumablePotion)
            return DrinkSlot == null || DrinkSlot.canEatEarly();

        var existing = getEatenFood(food);
        if (existing != null)
            return existing.canEatEarly();

        if (ItemEntries.size() < MaxItemSlots)
            return true;

        return ItemEntries.stream().anyMatch(EatenFoodItem::canEatEarly);
    }

    public EatenFoodItem getEatenFood(Item food) {
        return ItemEntries.stream()
                .filter((item) -> item.item == food)
                .findFirst()
                .orElse(null);
    }


    public void clear()
    {
        ItemEntries.clear();
        DrinkSlot = null;
    }


    public void tick()
    {
        // todo replace with a timestamp and have ticksLeft maybe return timestamp diff
        // might provide better compat for carpet tick freeze
        for (var item : ItemEntries)
        {
            item.ticksLeft--;
        }

        if (DrinkSlot != null) {
            DrinkSlot.ticksLeft--;
            if (DrinkSlot.ticksLeft <= 0)
                DrinkSlot = null;
        }

        ItemEntries.removeIf(item -> item.ticksLeft <= 0);
        ItemEntries.sort(Comparator.comparingInt(a -> a.ticksLeft));
    }


    public float getTotalFoodNutrition()
    {
        float nutrition = 0f;
        for (var item : ItemEntries)
        {
            ModConfig.Common.FoodConfig food = ModConfig.getFoodConfig(item.item);
            if (food == null)
                continue;

            nutrition += food.getHearts();
        }

        if (DrinkSlot != null)
        {
            ModConfig.Common.FoodConfig food = ModConfig.getFoodConfig(DrinkSlot.item);
            if (food != null)
            {
                nutrition += food.getHearts();
            }

            nutrition = nutrition * (1.0f + SOLValheim.Config.common.drinkSlotFoodEffectivenessBonus);
        }

        return nutrition;
    }


    public float getRegenSpeed()
    {
        float regen = 0.25f;
        for (var item : ItemEntries)
        {
            ModConfig.Common.FoodConfig food = ModConfig.getFoodConfig(item.item);
            if (food == null)
                continue;

            regen += food.getHealthRegen();
        }

        if (DrinkSlot != null)
        {
            ModConfig.Common.FoodConfig food = ModConfig.getFoodConfig(DrinkSlot.item);
            if (food != null)
            {
                regen += food.getHealthRegen();
            }

            regen = regen * (1.0f + SOLValheim.Config.common.drinkSlotFoodEffectivenessBonus);
        }

        return regen;
    }


    public CompoundTag save(CompoundTag tag) {
        int count = 0;
        tag.putInt("max_slots", MaxItemSlots);
        tag.putInt("count", ItemEntries.size());
        for (var item : ItemEntries)
        {
            tag.putString("id" + count, item.item.arch$registryName().toString());
            tag.putInt("ticks" + count, item.ticksLeft);
            count++;
        }

        if (DrinkSlot != null)
        {
            tag.putString("drink", DrinkSlot.item.arch$registryName().toString());
            tag.putInt("drinkticks", DrinkSlot.ticksLeft);
        }

        return tag;
    }

    public static ValheimFoodData read(CompoundTag tag) {
        var instance = new ValheimFoodData();
        instance.MaxItemSlots = tag.getInt("max_slots");

        var size = tag.getInt("count");
        for (int count = 0; count < size; count++)
        {
            var str = tag.getString("id" + count);
            var ticks = tag.getInt("ticks" + count);
            var item = SOLValheim.ITEMS.getRegistrar().get(new ResourceLocation(str));

            instance.ItemEntries.add(new EatenFoodItem(item, ticks));
        }

        var drink = tag.getString("drink");
        var drinkTicks = tag.getInt("drinkticks");

        if (!drink.isBlank())
        {
            var item = SOLValheim.ITEMS.getRegistrar().get(new ResourceLocation(drink));
            instance.DrinkSlot = new EatenFoodItem(item, drinkTicks);
        }

        return instance;
    }


    public static class EatenFoodItem {
        public Item item;
        public int ticksLeft;

        public boolean canEatEarly() {
            var isAlwaysEdible = (item.isEdible() && (item.getFoodProperties().canAlwaysEat() || item.getDefaultInstance().is(CAN_EAT_EARLY)));
            var isConsumablePotion = (item.getDefaultInstance().getUseAnimation() == UseAnim.DRINK && !(item.getClass().equals(SplashPotionItem.class) || item.getClass().equals(LingeringPotionItem.class)));
            if (ticksLeft < 1200 || isAlwaysEdible || isConsumablePotion)
                return true;

            var config = ModConfig.getFoodConfig(item);
            if (config == null)
                return false;

            return ((float) this.ticksLeft / config.getTime()) < SOLValheim.Config.common.eatAgainPercentage;
        }

        public EatenFoodItem(Item item, int ticksLeft)
        {
            this.item = item;
            this.ticksLeft = ticksLeft;
        }

        public EatenFoodItem(EatenFoodItem eaten)
        {
            this.item = eaten.item;
            this.ticksLeft = eaten.ticksLeft;
        }
    }
}