package ru.privatenull.pncases.hologram.provider;

import org.bukkit.Bukkit;
import ru.privatenull.pncases.PnCasesPlugin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selects the configured hologram implementation for the running server. */
public final class HologramProviderResolver {
    private static final Pattern SERVER_VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final int FANCY_MIN_MINOR_VERSION = 20;

    private final PnCasesPlugin plugin;

    public HologramProviderResolver(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    public HologramProvider select() {
        ProviderMode mode = readMode();
        boolean fancyAvailable = Bukkit.getPluginManager().isPluginEnabled("FancyHolograms");
        boolean decentAvailable = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        if (mode == ProviderMode.FANCY) return fancyAvailable ? fancy() : null;
        if (mode == ProviderMode.DECENT) return decentAvailable ? decent() : null;

        if (preferDecent()) {
            HologramProvider decent = decentAvailable ? decent() : null;
            return decent != null ? decent : fancyAvailable ? fancy() : null;
        }
        HologramProvider fancy = fancyAvailable ? fancy() : null;
        return fancy != null ? fancy : decentAvailable ? decent() : null;
    }

    public HologramProvider fancy() {
        try {
            return new FancyHologramsProvider();
        } catch (LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    public HologramProvider decent() {
        try {
            return new DecentHologramsProvider();
        } catch (LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private ProviderMode readMode() {
        String raw = plugin.getConfig().getString("holograms.provider", "AUTO");
        try {
            return ProviderMode.valueOf(raw == null ? "AUTO" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ProviderMode.AUTO;
        }
    }

    private boolean preferDecent() {
        Matcher matcher = SERVER_VERSION.matcher(Bukkit.getBukkitVersion());
        if (!matcher.find()) return false;
        int major = integer(matcher.group(1), 1);
        int minor = integer(matcher.group(2), 21);
        return major == 1 && minor < FANCY_MIN_MINOR_VERSION;
    }

    private int integer(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private enum ProviderMode { AUTO, FANCY, DECENT }
}
