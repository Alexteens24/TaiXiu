package com.cortezromeo.taixiu.language;

import com.cortezromeo.taixiu.TaiXiu;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class English {
    private static File file;
    private static FileConfiguration messageFile;

    public static void setup() {
        file = new File(TaiXiu.plugin.getDataFolder() + "/languages/messages_en.yml");

        messageFile = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get() {
        return messageFile;
    }

    public static void saveDefault() {
        if (file.isFile() && file.length() > 0) return;
        try (InputStream resource = TaiXiu.plugin.getResource("messages_en.yml")) {
            if (resource == null) throw new IOException("Bundled messages_en.yml is missing");
            Files.copy(resource, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            reload();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create " + file, exception);
        }
    }

    public static void reload() {
        messageFile = YamlConfiguration.loadConfiguration(file);
    }
}
