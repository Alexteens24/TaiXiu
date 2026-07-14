package com.cortezromeo.taixiu.file.inventory;

import com.cortezromeo.taixiu.TaiXiu;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class TaiXiuInfoInventoryFile {
    private static File file;
    private static FileConfiguration fileConfiguration;

    public static void setup() {
        file = new File(TaiXiu.plugin.getDataFolder() + "/inventories/sessioninfoinventory.yml");

        fileConfiguration = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get() {
        return fileConfiguration;
    }

    public static void saveDefault() {
        if (file.isFile() && file.length() > 0) return;
        try (InputStream resource = TaiXiu.plugin.getResource("sessioninfoinventory.yml")) {
            if (resource == null) throw new IOException("Bundled sessioninfoinventory.yml is missing");
            Files.copy(resource, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            reload();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create " + file, exception);
        }
    }

    public static void reload() {
        fileConfiguration = YamlConfiguration.loadConfiguration(file);
    }
}
