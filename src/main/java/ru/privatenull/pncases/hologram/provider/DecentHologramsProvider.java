package ru.privatenull.pncases.hologram.provider;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.hologram.HologramDefinition;

import java.util.ArrayList;
import java.util.List;

public final class DecentHologramsProvider implements HologramProvider {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
    }

    @Override
    public void create(HologramDefinition spec) {
        if (!isAvailable()) {
            throw new IllegalStateException("DecentHolograms API недоступен");
        }

        remove(spec.name());

        Hologram hologram = switch (spec.type()) {
            case ITEM -> createItemHologram(spec);
            case BLOCK -> createBlockHologram(spec);
            case TEXT -> createTextHologram(spec);
        };

        hologram.setSaveToFile(false);
        hologram.updateAll();
    }

    @Override
    public void remove(String name) {
        if (name == null || name.isBlank()) {
            return;
        }

        DHAPI.removeHologram(name);
    }

    @Override
    public void move(String name, org.bukkit.Location location, int movementTicks) {
        Hologram hologram = DHAPI.getHologram(name);
        if (hologram == null || location == null) return;
        DHAPI.moveHologram(hologram, location);
    }

    @Override
    public void updateText(String name, List<String> lines) {
        Hologram hologram = DHAPI.getHologram(name);
        if (hologram == null) return;
        DHAPI.setHologramLines(hologram, colorLines(lines));
    }

    private static Hologram createTextHologram(HologramDefinition spec) {
        return DHAPI.createHologram(spec.name(), spec.location(), false, colorLines(spec.lines()));
    }

    private static Hologram createItemHologram(HologramDefinition spec) {
        Hologram hologram = DHAPI.createHologram(spec.name(), spec.location(), false);
        ItemStack item = spec.item() == null ? new ItemStack(Material.CHEST) : spec.item().clone();
        DHAPI.addHologramLine(hologram, item);
        return hologram;
    }

    private static Hologram createBlockHologram(HologramDefinition spec) {
        Hologram hologram = DHAPI.createHologram(spec.name(), spec.location(), false);
        DHAPI.addHologramLine(hologram, spec.block() == null ? Material.CHEST : spec.block());
        return hologram;
    }

    private static List<String> colorLines(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(line == null ? "" : line.replace('&', '§'));
        }
        return out;
    }
}
