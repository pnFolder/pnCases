package ru.privatenull.pncases.listener;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.animation.core.AnimationSeason;
import ru.privatenull.pncases.cases.model.ProximitySettings;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Tells admins once the winter scene is open. The notice stops for good as soon as a case uses the winter scene;
 * that is remembered in notices.yml, so turning the scene off later does not bring the notice back.
 */
public final class NewYearNotice {

    private static final String KEY = "new-year-scene-used";

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final File file;
    private boolean used;

    public NewYearNotice(PnCasesPlugin plugin, CaseManager caseManager) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.file = new File(plugin.getDataFolder(), "notices.yml");
        this.used = YamlConfiguration.loadConfiguration(file).getBoolean(KEY, false);
    }

    /** Call when an admin joins. */
    public void notifyIfNeeded(Player player) {
        if (used || !AnimationSeason.newYearOpen() || !player.hasPermission("pncases.admin")) return;
        if (anyCaseUsesWinter()) {
            remember();
            return;
        }
        player.sendMessage("§b[pnCases] §fОткрылась новогодняя зимняя сцена вокруг кейса: снег, ёлки и Дед Мороз.");
        player.sendMessage("§7Включи её в Machine: кейс → Ближняя анимация → Сцена. Это сообщение пропадёт, когда сцена будет включена.");
    }

    private boolean anyCaseUsesWinter() {
        return caseManager.getCases().stream().anyMatch(definition -> {
            ProximitySettings settings = definition.proximity();
            return settings != null && settings.enabled() && settings.scene() == ProximitySettings.Scene.WINTER;
        });
    }

    private void remember() {
        used = true;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(KEY, true);
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Не удалось сохранить notices.yml", exception);
        }
    }
}
