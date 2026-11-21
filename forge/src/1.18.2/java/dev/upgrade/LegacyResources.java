package dev.upgrade;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Opens the highest-priority 1.18.2 pack resource only when its reader is requested. */
public record LegacyResources(ResourceManager manager) {
    public record Entry(ResourceManager manager, ResourceLocation id) {
        public BufferedReader openAsReader() throws IOException {
            var resource = manager.getResource(id);
            var stream = new FilterInputStream(resource.getInputStream()) {
                @Override public void close() throws IOException { resource.close(); }
            };
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    public Map<ResourceLocation, Entry> listResources(String directory, Predicate<ResourceLocation> filter) {
        Map<ResourceLocation, Entry> entries = new TreeMap<>();
        for (var id : manager.listResources(directory, filename -> true)) {
            if (filter.test(id)) entries.put(id, new Entry(manager, id));
        }
        return entries;
    }

    public Optional<Entry> getResource(ResourceLocation id) {
        return manager.hasResource(id) ? Optional.of(new Entry(manager, id)) : Optional.empty();
    }

    public Entry getResourceOrThrow(ResourceLocation id) throws IOException {
        return getResource(id).orElseThrow(() -> new java.io.FileNotFoundException(id.toString()));
    }
}
