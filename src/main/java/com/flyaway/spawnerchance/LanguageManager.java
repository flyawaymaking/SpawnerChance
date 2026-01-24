package com.flyaway.spawnerchance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class LanguageManager {

    private final SpawnerChance plugin;
    private final Gson gson = new GsonBuilder().create();
    private final YamlConfiguration translations = new YamlConfiguration();

    public LanguageManager(SpawnerChance plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        String lang = plugin.getConfigManager().getLanguage().toLowerCase();

        File file = new File(plugin.getDataFolder(), "translations/" + lang + ".yml");
        file.getParentFile().mkdirs();

        if (file.exists()) {
            try {
                translations.load(file);
                if (!translations.getKeys(false).isEmpty()) return;
            } catch (Exception ignored) {
            }
        }

        plugin.getLogger().info("Loading the language: " + lang);

        String version = plugin.getServer().getMinecraftVersion();
        String url = "https://api.github.com/repos/InventivetalentDev/minecraft-assets"
                + "/contents/assets/minecraft/lang/" + lang + ".json?ref=" + version;

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            JsonObject root = gson.fromJson(resp.body(), JsonObject.class);
            String base64 = root.get("content").getAsString();

            JsonObject json = gson.fromJson(
                    new String(Base64Coder.decodeLines(base64)),
                    JsonObject.class
            );

            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                String key = e.getKey();
                String value = e.getValue().getAsString();

                if (key.startsWith("entity.minecraft.")) {
                    String entityKey = key.replace("entity.minecraft.", "");
                    translations.set("entity." + entityKey, value);
                }

                if (key.equals("block.minecraft.spawner")) {
                    translations.set("block.spawner", value);
                }
            }

            translations.save(file);
            plugin.getLogger().info("The language was uploaded successfully.");

        } catch (Exception ex) {
            plugin.getLogger().warning(ex.getMessage());
            plugin.getLogger().severe("Language loading error!");
        }
    }

    public String translate(Material mat) {
        if (mat == Material.SPAWNER) {
            return translations.getString("block.spawner", "Spawner");
        }
        return mat.name().toLowerCase().replace("_", " ");
    }

    public String translate(EntityType type) {
        String key = type.getKey().getKey();
        String def = key.replace("_", " ");

        return translations.getString("entity." + key, def);
    }
}
