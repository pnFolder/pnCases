package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.IdleParticleSettings;
import ru.privatenull.pncases.cases.model.CaseGuiLayoutRules;
import ru.privatenull.pnlibrary.compat.ServerVersion;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.*;
import static ru.privatenull.pnlibrary.item.ItemFactory.isRealItem;
import static ru.privatenull.pnlibrary.item.ItemFactory.writeExactItem;

final class MachineActionController {

    private final CaseManager caseManager;
    private final MachineGuiSupport support;
    private final MachineLayoutEditor layoutEditor;
    private final MachineMainScreen mainScreen;
    private final MachineHologramScreen hologramScreen;
    private final MachinePurchaseScreen purchaseScreen;
    private final MachineMenuScreen menuScreen;
    private final MachineAnimationScreen animationScreen;
    private final MachineShowcaseScreen showcaseScreen;
    private final MachineTextEditor textEditor;
    private final MachineFeaturesScreen featuresScreen;
    private final MachineRewardScreen rewardScreen;

    MachineActionController(
            CaseManager caseManager,
            MachineGuiSupport support,
            MachineLayoutEditor layoutEditor,
            MachineMainScreen mainScreen,
            MachineHologramScreen hologramScreen,
            MachinePurchaseScreen purchaseScreen,
            MachineMenuScreen menuScreen,
            MachineAnimationScreen animationScreen,
            MachineShowcaseScreen showcaseScreen,
            MachineTextEditor textEditor,
            MachineFeaturesScreen featuresScreen,
            MachineRewardScreen rewardScreen
    ) {
        this.caseManager = caseManager;
        this.support = support;
        this.layoutEditor = layoutEditor;
        this.mainScreen = mainScreen;
        this.hologramScreen = hologramScreen;
        this.purchaseScreen = purchaseScreen;
        this.menuScreen = menuScreen;
        this.animationScreen = animationScreen;
        this.showcaseScreen = showcaseScreen;
        this.textEditor = textEditor;
        this.featuresScreen = featuresScreen;
        this.rewardScreen = rewardScreen;
    }

    void handle(GuiSession holder, Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        switch (holder.screen()) {
            case MACHINE_MAIN -> handleMain(player, event, definition, slot);
            case MACHINE_ANIMATION -> handleAnimation(holder, player, event, definition, slot);
            case MACHINE_LAYOUT -> layoutEditor.handleClick(player, event, definition, slot);
            case MACHINE_LAYOUT_ROLE -> layoutEditor.handleRoleClick(holder, player, event, definition, slot);
            case MACHINE_HOLOGRAM -> handleHologram(player, event, definition, slot);
            case MACHINE_SHOWCASE -> handleShowcase(player, event, definition, slot);
            case MACHINE_MENU -> handleMenu(player, event, definition, slot);
            case MACHINE_PURCHASE -> handlePurchase(player, event, definition, slot);
            case MACHINE_FEATURES -> featuresScreen.handleClick(player, event, definition, slot);
            case MACHINE_REWARDS -> rewardScreen.handleListClick(holder, player, event, definition, slot);
            case MACHINE_REWARD -> rewardScreen.handleEditorClick(holder, player, event, definition, slot);
            case MACHINE_REWARD_ICON -> rewardScreen.handleIconClick(holder, player, event, definition, slot);
            case MACHINE_REWARD_MOVE -> rewardScreen.handleMoveClick(holder, player, definition, slot);
            case MACHINE_REWARD_DELETE -> rewardScreen.handleDeleteClick(holder, player, definition, slot);
            default -> {
            }
        }
    }

    private void handleMain(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (slot == SLOT_MAIN_CLOSE) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.2f, 0.95f);
        } else if (slot == SLOT_MAIN_ANIMATION) {
            animationScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_MENU) {
            menuScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_HOLOGRAM) {
            hologramScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_PARTICLES) {
            showcaseScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_PURCHASE) {
            purchaseScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_FEATURES) {
            featuresScreen.open(player, definition.name());
        } else if (slot == SLOT_MAIN_REWARDS) {
            rewardScreen.open(player, definition.name());
        }
    }

    private void handleAnimation(GuiSession holder, Player player, InventoryClickEvent event,
                                 CaseDefinition definition, int slot) {
        if (slot == SLOT_BACK) {
            mainScreen.open(player, definition.name());
            return;
        }

        if (slot == SLOT_ANIMATION_MODE) {
            if (ServerVersion.current().isReleaseLine(1, 16)) {
                player.sendMessage(caseManager.getPlugin().getUi().get("animation-legacy-only"));
                return;
            }
            support.updateAnimation(player, definition.name(), root -> section(root, "animation").set(
                    "fixed", null
            ));
            animationScreen.open(player, definition.name());
            return;
        }
        AnimationType selected = animationScreen.typeAtSlot(slot);
        if (selected == null) return;
        support.updateAnimation(player, definition.name(), root -> section(root, "animation").set(
                "fixed", selected.name()
        ));
        caseManager.warnAboutMobAnimation(player, selected);
        animationScreen.open(player, definition.name());
    }

    private void handleHologram(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (backToMain(player, definition, slot)) return;

        if (slot == SLOT_HOLOGRAM_TOGGLE) {
            updateHologram(player, definition, root -> section(root, "hologram").set("enabled", !support.hologramEnabled(definition)));
            hologramScreen.open(player, definition.name());
        } else if (slot == SLOT_HOLOGRAM_HEIGHT) {
            double next = boundedStep(support.hologramHeight(definition), event, 0.1, 1.0, -5.0, 10.0, 1);
            updateHologram(player, definition, root -> {
                ConfigurationSection hologram = section(root, "hologram");
                hologram.set("enabled", true);
                hologram.set("y", next);
            });
            hologramScreen.open(player, definition.name());
        } else if (slot == SLOT_HOLOGRAM_LINES) {
            textEditor.startHologramLines(player, definition);
        }
    }

    private void handleShowcase(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (backToMain(player, definition, slot)) return;

        IdleParticleSettings settings = definition.idleParticles();
        if (slot == SLOT_PARTICLES_TOGGLE) {
            updateShowcase(player, definition, root -> section(root, "idle-particles").set("enabled", !settings.enabled()));
        } else if (slot == SLOT_PARTICLES_THEME) {
            IdleParticleSettings.Theme next = nextEnum(settings.theme(), IdleParticleSettings.Theme.values(), event.isRightClick());
            updateShowcase(player, definition, root -> section(root, "idle-particles").set("theme", next.name()));
        } else {
            return;
        }
        showcaseScreen.open(player, definition.name());
    }

    private void handleMenu(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (backToMain(player, definition, slot)) return;

        if (slot == SLOT_GUI_SIZE) {
            int step = event.isShiftClick() ? 18 : 9;
            int candidate = definition.guiLayout().size() + (event.isRightClick() ? -step : step);
            int next = CaseGuiLayoutRules.normalizeSlotCount(candidate);
            update(player, definition, root -> section(root, "gui").set("size", next));
            menuScreen.open(player, definition.name());
        } else if (slot == SLOT_GUI_TITLE) {
            textEditor.startGuiTitle(player, definition);
        } else if (slot == SLOT_LAYOUT) {
            layoutEditor.open(player, definition.name());
        } else if (slot == SLOT_CONTENT_TOGGLE) {
            if (!caseManager.getPlugin().previewEnabled()) {
                player.sendMessage(ru.privatenull.pnlibrary.text.ColorUtil.colorize(
                        "&#FC7165Сначала включите функцию preview в разделе «Функции»."));
                return;
            }
            update(player, definition, root -> section(root, "gui").set(
                    "preview_enabled", !definition.guiLayout().previewEnabled()
            ));
            menuScreen.open(player, definition.name());
        } else if (slot == SLOT_PREVIEW_CASE) {
            openCasePreview(player, definition);
        } else {
            replaceMenuItem(player, event, definition, slot);
        }
    }

    private void replaceMenuItem(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        ItemStack cursor = event.getCursor();
        if (!isRealItem(cursor)) return;

        if (slot == SLOT_OPEN_ITEM) {
            update(player, definition, root -> writeExactItem(section(root, "gui"), "button", cursor));
        } else if (slot == SLOT_DECOR_ITEM) {
            update(player, definition, root -> writeExactItem(section(section(root, "gui"), "decor"), "item", cursor));
        } else if (slot == SLOT_EMPTY_HISTORY_ITEM) {
            update(player, definition, root -> writeExactItem(
                    section(section(root, "gui"), "history"), "empty", cursor));
        } else {
            return;
        }
        menuScreen.open(player, definition.name());
    }

    private void handlePurchase(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (backToMain(player, definition, slot)) return;

        if (slot == SLOT_XP_BUY) {
            update(player, definition, root -> section(section(root, "cost"), "xp-purchase")
                    .set("enabled", !support.xpBuyEnabled(definition)));
            purchaseScreen.open(player, definition.name());
        } else if (slot == SLOT_XP_LEVELS) {
            int next = Math.max(0, definition.buyKeyWithXpLevels()
                    + signedStep(event, 1, 5));
            update(player, definition, root -> section(section(root, "cost"), "xp-purchase")
                    .set("levels", next));
            purchaseScreen.open(player, definition.name());
        }
    }

    private boolean backToMain(Player player, CaseDefinition definition, int slot) {
        if (slot != SLOT_BACK) return false;
        mainScreen.open(player, definition.name());
        return true;
    }

    private void openCasePreview(Player player, CaseDefinition definition) {
        caseManager.openCaseGui(player, definition);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void update(Player player, CaseDefinition definition, java.util.function.Consumer<ConfigurationSection> updater) {
        support.update(player, definition.name(), updater);
    }

    private void updateHologram(Player player, CaseDefinition definition,
                                 java.util.function.Consumer<ConfigurationSection> updater) {
        support.updateHologram(player, definition.name(), updater);
    }

    private void updateShowcase(Player player, CaseDefinition definition,
                                java.util.function.Consumer<ConfigurationSection> updater) {
        support.updateShowcase(player, definition.name(), updater);
    }

    private static int boundedStep(int current, InventoryClickEvent event, int normal, int shifted, int min, int max) {
        return Math.max(min, Math.min(max, current + signedStep(event, normal, shifted)));
    }

    private static int signedStep(InventoryClickEvent event, int normal, int shifted) {
        int step = event.isShiftClick() ? shifted : normal;
        return event.isRightClick() ? -step : step;
    }

    private static double boundedStep(
            double current,
            InventoryClickEvent event,
            double normal,
            double shifted,
            double min,
            double max,
            int scale
    ) {
        double step = event.isShiftClick() ? shifted : normal;
        if (event.isRightClick()) step = -step;
        double multiplier = Math.pow(10.0, scale);
        double rounded = Math.round((current + step) * multiplier) / multiplier;
        return Math.max(min, Math.min(max, rounded));
    }

    private static <T extends Enum<T>> T nextEnum(T current, T[] values, boolean backwards) {
        if (values.length == 0) return current;
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                index = i;
                break;
            }
        }
        int next = backwards
                ? (index - 1 + values.length) % values.length
                : (index + 1) % values.length;
        return values[next];
    }
}
