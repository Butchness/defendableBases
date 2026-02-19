package com.defendablebases;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public final class ExplosivesDamageReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    // Local logger so we don't depend on DefendableBases.LOGGER visibility
    private static final Logger LOGGER = LogManager.getLogger("DefendableBases/Explosives");

    public ExplosivesDamageReloadListener() {
        // Loads JSON files under:
        // data/*/defendablebases/*.json
        // So explosives_damage.json should live at:
        // data/<namespace>/defendablebases/explosives_damage.json
        super(GSON, "defendablebases");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager rm, ProfilerFiller profiler) {
        ExplosivesData.clear();

        int loadedBlockEntries = 0;
        int loadedEntityEntries = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonElement rootEl = entry.getValue();

            // We only care about files named "explosives_damage.json"
            // In this listener, fileId.getPath() is the json filename without ".json"
            if (!"explosives_damage".equals(fileId.getPath())) continue;

            if (!rootEl.isJsonObject()) continue;
            JsonObject root = rootEl.getAsJsonObject();

            // blocks: { "mod:block": 3.0, ... }
            if (root.has("blocks") && root.get("blocks").isJsonObject()) {
                JsonObject blocks = root.getAsJsonObject("blocks");
                for (Map.Entry<String, JsonElement> e : blocks.entrySet()) {
                    String idStr = e.getKey();
                    float dpb = safeFloat(e.getValue(), 1f);

                    ResourceLocation id = ResourceLocation.tryParse(idStr);
                    if (id == null) {
                        LOGGER.warn("[Explosives] Bad block id '{}' (file {}) - ignored", idStr, fileId);
                        continue;
                    }
                    if (!ForgeRegistries.BLOCKS.containsKey(id)) {
                        LOGGER.warn("[Explosives] Missing block '{}' (file {}) - ignored", id, fileId);
                        continue;
                    }

                    ExplosivesData.putBlock(id, dpb);
                    loadedBlockEntries++;
                }
            }

            // entities: { "minecraft:tnt": 1.0, "some_mod:bomb_entity": 5.0, ... }
            if (root.has("entities") && root.get("entities").isJsonObject()) {
                JsonObject ents = root.getAsJsonObject("entities");
                for (Map.Entry<String, JsonElement> e : ents.entrySet()) {
                    String idStr = e.getKey();
                    float dpb = safeFloat(e.getValue(), 1f);

                    ResourceLocation id = ResourceLocation.tryParse(idStr);
                    if (id == null) {
                        LOGGER.warn("[Explosives] Bad entity id '{}' (file {}) - ignored", idStr, fileId);
                        continue;
                    }
                    if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                        LOGGER.warn("[Explosives] Missing entity '{}' (file {}) - ignored", id, fileId);
                        continue;
                    }

                    ExplosivesData.putEntity(id, dpb);
                    loadedEntityEntries++;
                }
            }
        }

        LOGGER.info("[Explosives] Loaded explosive DPB mappings: blocks={}, entities={}",
                loadedBlockEntries, loadedEntityEntries);
    }

    private static float safeFloat(JsonElement el, float fallback) {
        try {
            if (el == null) return fallback;
            if (el.isJsonPrimitive()) {
                JsonPrimitive p = el.getAsJsonPrimitive();
                if (p.isNumber()) return p.getAsFloat();
                if (p.isString()) return Float.parseFloat(p.getAsString());
            }
        } catch (Exception ignored) {}
        return fallback;
    }
}
