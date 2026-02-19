package com.defendablebases;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public final class RepairResources {
    private RepairResources() {}

    /**
     * Points per item:
     * - If datapack defines it -> use datapack value
     * - else fallback to FortressTier.healValue(item)
     */
    public static float getPoints(Item item) {
        if (item == null) return 0f;

        Float pack = RepairResourcesData.getItemPoints(item);
        if (pack != null) return Math.max(0f, pack);

        int fallback = FortressTier.healValue(item);
        return Math.max(0f, (float) fallback);
    }

    /**
     * Priority order:
     * 1) datapack "priority" list (valid items only)
     * 2) then any datapack repair_items keys not already included
     * 3) then FortressTier.REPAIR_PRIORITY items not already included
     */
    public static List<Item> getPriorityItems() {
        List<Item> out = new ArrayList<>(64);
        Set<Item> seen = new HashSet<>();

        // (1) JSON priority
        for (ResourceLocation id : RepairResourcesData.getPriorityIdsCopy()) {
            Item it = ForgeRegistries.ITEMS.getValue(id);
            if (it == null) continue;
            if (seen.add(it)) out.add(it);
        }

        // (2) JSON repair_items keys not already in priority
        for (ResourceLocation id : RepairResourcesData.getMappedItemIdsCopy()) {
            Item it = ForgeRegistries.ITEMS.getValue(id);
            if (it == null) continue;
            if (seen.add(it)) out.add(it);
        }

        // (3) Default tier priority list
        for (Item it : FortressTier.REPAIR_PRIORITY) {
            if (it == null) continue;
            if (seen.add(it)) out.add(it);
        }

        return out;
    }
}
