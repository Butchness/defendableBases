package com.defendablebases;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RepairResourcesReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final Logger LOGGER = LogUtils.getLogger();

    public RepairResourcesReloadListener() {
        // Loads all JSON files under:
        // data/*/defendablebases/repair_resources.json
        super(GSON, "defendablebases");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager rm, ProfilerFiller profiler) {
        RepairResourcesData.clear();

        int loadedFiles = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonElement rootEl = entry.getValue();

            // only files named "repair_resources.json"
            if (!"repair_resources".equals(fileId.getPath())) continue;

            loadedFiles++;

            if (!rootEl.isJsonObject()) continue;
            JsonObject root = rootEl.getAsJsonObject();

            // priority: ["minecraft:oak_planks", ...]
            if (root.has("priority") && root.get("priority").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("priority");
                List<ResourceLocation> ids = new ArrayList<>(arr.size());

                for (JsonElement el : arr) {
                    String idStr = safeString(el, null);
                    if (idStr == null) continue;

                    ResourceLocation id = ResourceLocation.tryParse(idStr);
                    if (id == null) {
                        LOGGER.warn("[RepairResources] Bad item id '{}' (file {}) - ignored", idStr, fileId);
                        continue;
                    }
                    if (!ForgeRegistries.ITEMS.containsKey(id)) {
                        LOGGER.warn("[RepairResources] Missing item '{}' (file {}) - ignored", id, fileId);
                        continue;
                    }
                    ids.add(id);
                }

                RepairResourcesData.setPriorityIds(ids);
            }

            // repair_items: { "minecraft:oak_planks": 1.0, ... }
            // also supports object: { "minecraft:oak_planks": { "healing_per_block": 1.0 } }
            if (root.has("repair_items") && root.get("repair_items").isJsonObject()) {
                JsonObject items = root.getAsJsonObject("repair_items");

                for (Map.Entry<String, JsonElement> e : items.entrySet()) {
                    String idStr = e.getKey();
                    JsonElement val = e.getValue();

                    ResourceLocation id = ResourceLocation.tryParse(idStr);
                    if (id == null) {
                        LOGGER.warn("[RepairResources] Bad item id '{}' (file {}) - ignored", idStr, fileId);
                        continue;
                    }
                    if (!ForgeRegistries.ITEMS.containsKey(id)) {
                        LOGGER.warn("[RepairResources] Missing item '{}' (file {}) - ignored", id, fileId);
                        continue;
                    }

                    float points = readPoints(val, 0f);
                    RepairResourcesData.putItem(id, points);
                }
            }
        }

        LOGGER.info("[RepairResources] Loaded files={}, repair_items={}, priority={}",
                loadedFiles,
                RepairResourcesData.getItemEntryCount(),
                RepairResourcesData.getPriorityCount()
        );
    }

    private static float readPoints(JsonElement el, float fallback) {
        try {
            if (el == null) return fallback;

            // primitive number or string
            if (el.isJsonPrimitive()) {
                JsonPrimitive p = el.getAsJsonPrimitive();
                if (p.isNumber()) return p.getAsFloat();
                if (p.isString()) return Float.parseFloat(p.getAsString());
            }

            // object with "repair_points" or "healing_per_block" (compat with your wording)
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("repair_points")) return readPoints(obj.get("repair_points"), fallback);
                if (obj.has("healing_per_block")) return readPoints(obj.get("healing_per_block"), fallback);
                if (obj.has("healing_per_item")) return readPoints(obj.get("healing_per_item"), fallback);
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private static String safeString(JsonElement el, String fallback) {
        try {
            if (el == null) return fallback;
            if (!el.isJsonPrimitive()) return fallback;
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (!p.isString()) return fallback;
            return p.getAsString();
        } catch (Exception ignored) {}
        return fallback;
    }
}
