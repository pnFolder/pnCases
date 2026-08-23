package ru.privatenull.pncases.hologram.provider;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.*;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;
import ru.privatenull.pncases.hologram.HologramDefinition;
import ru.privatenull.pncases.hologram.HologramVector;

import java.lang.reflect.Method;
import java.util.Locale;

public final class FancyHologramsProvider implements HologramProvider {

    private final HologramManager manager;
    private final Method displayAccessor;

    public FancyHologramsProvider() {
        this.manager = FancyHologramsPlugin.get().getHologramManager();
        this.displayAccessor = findDisplayAccessor();
    }

    @Override
    public boolean isAvailable() {
        return FancyHologramsPlugin.isEnabled() && manager != null;
    }

    @Override
    public void create(HologramDefinition spec) {
        if (!isAvailable()) {
            throw new IllegalStateException("FancyHolograms API недоступен");
        }

        remove(spec.name());

        HologramData data = buildData(spec);
        data.setPersistent(false);
        if (spec.visibilityDistance() != null) {
            data.setVisibilityDistance(Math.max(1, spec.visibilityDistance()));
        }

        applyDisplayStyles(data, spec);

        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);
    }

    @Override
    public void remove(String name) {
        if (manager == null || name == null || name.isBlank()) {
            return;
        }

        manager.getHologram(name).ifPresent(manager::removeHologram);
    }

    @Override
    public void move(String name, org.bukkit.Location location, int movementTicks) {
        if (manager == null || name == null || location == null) return;
        manager.getHologram(name).ifPresent(hologram -> {
            hologram.getData().setLocation(location);
            if (!moveDisplay(hologram, location, movementTicks)) {
                hologram.forceUpdate();
            }
        });
    }

    @Override
    public void updateText(String name, java.util.List<String> lines) {
        if (manager == null || name == null) return;
        manager.getHologram(name).ifPresent(hologram -> {
            if (hologram.getData() instanceof TextHologramData text) {
                text.setText(lines);
                hologram.forceUpdate();
            }
        });
    }

    private static HologramData buildData(HologramDefinition spec) {
        return switch (spec.type()) {
            case ITEM -> buildItemData(spec);
            case BLOCK -> buildBlockData(spec);
            case TEXT -> buildTextData(spec);
        };
    }

    private static TextHologramData buildTextData(HologramDefinition spec) {
        TextHologramData data = new TextHologramData(spec.name(), spec.location());
        data.setText(spec.lines());

        if (spec.textShadow() != null) {
            data.setTextShadow(spec.textShadow());
        }
        if (spec.seeThrough() != null) {
            data.setSeeThrough(spec.seeThrough());
        }
        TextDisplay.TextAlignment alignment = readTextAlignment(spec.textAlignment());
        if (alignment != null) {
            data.setTextAlignment(alignment);
        }
        if (spec.background() != null) {
            data.setBackground(spec.background());
        }
        if (spec.textUpdateInterval() != null) {
            data.setTextUpdateInterval(Math.max(0, spec.textUpdateInterval()));
        }

        return data;
    }

    private static ItemHologramData buildItemData(HologramDefinition spec) {
        ItemHologramData data = new ItemHologramData(spec.name(), spec.location());
        ItemStack item = spec.item() == null ? new ItemStack(Material.CHEST) : spec.item().clone();
        data.setItemStack(item);
        return data;
    }

    private static BlockHologramData buildBlockData(HologramDefinition spec) {
        BlockHologramData data = new BlockHologramData(spec.name(), spec.location());
        data.setBlock(spec.block() == null ? Material.CHEST : spec.block());
        return data;
    }

    private static void applyDisplayStyles(HologramData data, HologramDefinition spec) {
        if (!(data instanceof DisplayHologramData displayData)) {
            return;
        }

        Display.Billboard billboard = readBillboard(spec.billboard());
        if (billboard != null) {
            displayData.setBillboard(billboard);
        }
        if (spec.shadowRadius() != null) {
            displayData.setShadowRadius(spec.shadowRadius());
        }
        if (spec.shadowStrength() != null) {
            displayData.setShadowStrength(spec.shadowStrength());
        }
        if (spec.interpolationDuration() != null) {
            displayData.setInterpolationDuration(Math.max(0, spec.interpolationDuration()));
        }
        if (spec.scale() != null) {
            displayData.setScale(toVector(spec.scale()));
        }
        if (spec.translation() != null) {
            displayData.setTranslation(toVector(spec.translation()));
        }
        if (spec.brightness() != null) {
            displayData.setBrightness(new Display.Brightness(spec.brightness().block(), spec.brightness().sky()));
        }
    }

    private static Display.Billboard readBillboard(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Display.Billboard.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Display.Billboard.CENTER;
        }
    }

    private static TextDisplay.TextAlignment readTextAlignment(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TextDisplay.TextAlignment.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TextDisplay.TextAlignment.CENTER;
        }
    }

    private static Vector3f toVector(HologramVector vector) {
        return new Vector3f(vector.x(), vector.y(), vector.z());
    }

    /** Uses the transitional FancyHolograms display accessor when available. */
    private boolean moveDisplay(Hologram hologram, Location location, int movementTicks) {
        if (hologram == null || location == null || displayAccessor == null) return false;
        try {
            Object value = displayAccessor.invoke(hologram);
            if (!(value instanceof Display display) || !display.isValid()) return false;
            display.setTeleportDuration(Math.max(0, Math.min(59, movementTicks)));
            display.teleport(location);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static Method findDisplayAccessor() {
        try {
            return Hologram.class.getMethod("getDisplayEntity");
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }
}
