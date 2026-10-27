package ru.privatenull.pncases.hologram;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Provider-neutral description of one persistent or animation hologram. */
public record HologramDefinition(
        String name,
        String caseName,
        Type type,
        Location location,
        List<String> lines,
        ItemStack item,
        Material block,
        String billboard,
        String textAlignment,
        Boolean textShadow,
        Boolean seeThrough,
        Color background,
        Integer textUpdateInterval,
        Float shadowRadius,
        Float shadowStrength,
        Integer visibilityDistance,
        Integer interpolationDuration,
        HologramVector scale,
        HologramVector translation,
        HologramBrightness brightness
) {
    public static HologramDefinition animationText(
            String name,
            Location location,
            List<String> lines,
            HologramTextStyle style
    ) {
        return new HologramDefinition(name, "animation", Type.TEXT, location.clone(),
                HologramText.normalize(lines), null, null,
                "CENTER", "CENTER", true, true, Color.fromARGB(0, 0, 0, 0),
                0, null, null, style.visibilityDistance(), style.interpolationTicks(),
                new HologramVector(style.scale(), style.scale(), style.scale()), null,
                new HologramBrightness(style.brightness(), style.brightness()));
    }

    public enum Type { TEXT, ITEM, BLOCK }

}
