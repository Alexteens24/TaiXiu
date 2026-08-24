package com.cortezromeo.taixiu.util;

import com.cortezromeo.taixiu.TaiXiu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ItemUtil {

    public static ItemStack getItem(String type, String value, short itemData, String name, List<String> lore) {
        return getItem(type, value, itemData, name, lore, Map.of());
    }

    public static ItemStack getItem(String type, String value, short itemData, String name, List<String> lore,
                                    Map<String, String> replacements) {
        AtomicReference<ItemStack> material = new AtomicReference<>(new ItemStack(Material.BEDROCK));

        if (type.equalsIgnoreCase("customhead"))
            material.set(TaiXiu.nms.getHeadItemFromBase64(value));
        if (type.equalsIgnoreCase("playerhead"))
            material.set(TaiXiu.nms.getHeadItemFromPlayerName(value));
        if (type.equalsIgnoreCase("material"))
            material.set(TaiXiu.nms.createItemStack(value, 1, itemData));

        ItemMeta materialMeta = material.get().getItemMeta();

        materialMeta.setDisplayName(TextFormatter.legacy(replace(name, replacements)));

        List<String> newList = new ArrayList<>();
        for (String string : lore)
            newList.add(TextFormatter.legacy(replace(string, replacements)));
        materialMeta.setLore(newList);

        material.get().setItemMeta(materialMeta);
        return material.get();
    }

    private static String replace(String input, Map<String, String> replacements) {
        String resolved = input == null ? "" : input;
        for (Map.Entry<String, String> replacement : replacements.entrySet())
            resolved = resolved.replace(replacement.getKey(), replacement.getValue());
        return resolved;
    }
}
