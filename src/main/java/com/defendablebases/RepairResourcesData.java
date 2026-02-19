package com.defendablebases;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RepairResourcesData {
    private RepairResourcesData() {}

    // item-id -> points per item
    private static final Map<ResourceLocation, Float> ITEM_POINTS = new ConcurrentHashMap<>();

    // priority list (item ids)
    private static final List<ResourceLocation> PRIORITY = new ArrayList<>();

    public static void clear() {
        ITEM_POINTS.clear();
        PRIORITY.clear();
    }

    public static void putItem(ResourceLocation id, float points) {
        if (id == null) return;
        ITEM_POINTS.put(id, Math.max(0f, points));
    }

    public static Float getItemPoints(ResourceLocation id) {
        if (id == null) return null;
        return ITEM_POINTS.get(id);
    }

    public static Float getItemPoints(Item item) {
        if (item == null) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return null;
        return ITEM_POINTS.get(id);
    }

    public static void setPriorityIds(List<ResourceLocation> ids) {
        PRIORITY.clear();
        if (ids == null || ids.isEmpty()) return;
        PRIORITY.addAll(ids);
    }

    public static List<ResourceLocation> getPriorityIdsCopy() {
        return new ArrayList<>(PRIORITY);
    }

    public static int getItemEntryCount() {
        return ITEM_POINTS.size();
    }

    public static int getPriorityCount() {
        return PRIORITY.size();
    }

    /** Return all keys currently registered in the datapack mapping. */
    public static Set<ResourceLocation> getMappedItemIdsCopy() {
        return new HashSet<>(ITEM_POINTS.keySet());
    }
}
