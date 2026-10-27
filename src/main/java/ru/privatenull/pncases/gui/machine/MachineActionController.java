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
import ru.privatenull.pncases.support.compat.ServerVersion;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.*;
import static ru.privatenull.pncases.support.item.ItemFactory.isRealItem;
import static ru.privatenull.pncases.support.item.ItemFactory.writeExactItem;

final class MachineActionController {

    private final CaseManager caseManager;
    private final MachineGuiSupport support;
    private final MachineLayoutEditor layoutEditor;
    private final MachineMainScreen mainScreen;
    private final MachineHologramScreen hologramScreen;
    private final MachineProximityScreen proximityScreen;
    private final MachineBundleScreen bundleScreen;
    private final MachinePurchaseScreen purchaseScreen;
    private final MachineMenuScreen menuScreen;
    private final MachineAnimationScreen animationScreen;
    private final MachineTextEditor textEditor;
    private final MachineExtrasScreen extrasScreen;
    private final MachineRewardScreen rewardScreen;
    private final MachineCasesScreen casesScreen;
    private final MachineWorkbenchScreen workbenchScreen;

    MachineActionController(
            CaseManager caseManager,
            MachineGuiSupport support,
            MachineLayoutEditor layoutEditor,
            MachineMainScreen mainScreen,
            MachineHologramScreen hologramScreen,
            MachineProximityScreen proximityScreen,
            MachineBundleScreen bundleScreen,
            MachinePurchaseScreen purchaseScreen,
            MachineMenuScreen menuScreen,
            MachineAnimationScreen animationScreen,
            MachineTextEditor textEditor,
            MachineExtrasScreen extrasScreen,
            MachineRewardScreen rewardScreen,
            MachineCasesScreen casesScreen,
            MachineWorkbenchScreen workbenchScreen
    ) {
        this.workbenchScreen = workbenchScreen;
        this.caseManager = caseManager;
        this.support = support;
        this.layoutEditor = layoutEditor;
        this.mainScreen = mainScreen;
        this.hologramScreen = hologramScreen;
        this.proximityScreen = proximityScreen;
        this.bundleScreen = bundleScreen;
        this.purchaseScreen = purchaseScreen;
        this.menuScreen = menuScreen;
        this.animationScreen = animationScreen;
        this.textEditor = textEditor;
        this.extrasScreen = extrasScreen;
        this.rewardScreen = rewardScreen;
        this.casesScreen = casesScreen;
    }

    void handle(GuiSession holder, Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        switch (holder.screen()) {
            case MACHINE_MAIN -> handleMain(player, event, definition, slot);
            case MACHINE_ANIMATION -> handleAnimation(holder, player, event, definition, slot);
            case MACHINE_LAYOUT -> layoutEditor.handleClick(player, event, definition, slot);
            case MACHINE_LAYOUT_ROLE -> layoutEditor.handleRoleClick(holder, player, event, definition, slot);
            case MACHINE_HOLOGRAM -> handleHologram(player, event, definition, slot);
            case MACHINE_MENU -> handleMenu(player, event, definition, slot);
            case MACHINE_PURCHASE -> handlePurchase(player, event, definition, slot);
            case MACHINE_WORKBENCH -> workbenchScreen.handleListClick(holder, player, event, definition, slot, extrasScreen);
            case MACHINE_WORKBENCH_RECIPE -> workbenchScreen.handleRecipeClick(holder, player, event, definition, slot);
            case MACHINE_PROXIMITY -> handleProximity(holder, player, event, definition, slot);
            case MACHINE_BUNDLE -> bundleScreen.handleClick(holder, player, definition, slot, extrasScreen);
            case MACHINE_FEATURES -> handleExtras(player, event, definition, slot);
            case MACHINE_REWARDS -> rewardScreen.handleListClick(holder, player, event, definition, slot);
            case MACHINE_REWARD -> rewardScreen.handleEditorClick(holder, player, event, definition, slot);
            case MACHINE_REWARD_ICON -> rewardScreen.handleIconClick(holder, player, event, definition, slot);
            case MACHINE_REWARD_MOVE -> rewardScreen.handleMoveClick(holder, player, definition, slot);
            case MACHINE_REWARD_DELETE -> rewardScreen.handleDeleteClick(holder, player, definition, slot);
            case MACHINE_REWARD_SIMULATION -> rewardScreen.handleSimulationClick(holder, player, definition, slot);
            case MACHINE_REWARD_CHANCE -> rewardScreen.handleChanceClick(holder, player, definition, slot);
            case MACHINE_CASES -> casesScreen.handleClick(holder, player, event, slot);
            default -> {
            }
        }
    }

    private void handleMain(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        String name = definition.name();
        switch (slot) {
            case SLOT_MAIN_REWARDS -> rewardScreen.open(player, name);
            case SLOT_MAIN_ANIMATION -> animationScreen.open(player, name);
            case SLOT_MAIN_MENU -> menuScreen.open(player, name);
            case SLOT_MAIN_FEATURES -> extrasScreen.open(player, name);
            case SLOT_MAIN_CASES -> casesScreen.open(player, 0);
            case SLOT_MAIN_CHECK -> {
                if (event.isRightClick()) rewardScreen.startSimulation(player, definition);
                else mainScreen.showCheck(player, definition);
            }
            case SLOT_MAIN_PREVIEW -> openCasePreview(player, definition);
            case SLOT_MAIN_UNDO -> mainScreen.undo(player, definition);            case SLOT_MAIN_BIND -> {
                if (event.isShiftClick() && event.isRightClick()) mainScreen.bind(player, definition, true);
                else if (event.isLeftClick() && !event.isShiftClick()) mainScreen.bind(player, definition, false);
            }
            case SLOT_MAIN_KEY -> {
                if (event.isLeftClick()) mainScreen.giveTestKeys(player, definition, event.isShiftClick() ? 10 : 1);
            }
            case SLOT_MAIN_CLOSE -> {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.2f, 0.95f);
            }
            default -> { }
        }
    }

    /**
     * "Additional": left click switches a case card, right click opens its settings. The two plugin-wide
     * switches (rarities, content catalog) are toggled by a left click.
     */
    private void handleExtras(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        String name = definition.name();
        if (slot == MachineExtrasScreen.backSlot()) {
            mainScreen.open(player, name);
            return;
        }
        switch (slot) {
            case SLOT_EXTRA_HOLOGRAM -> {
                if (event.isRightClick()) {
                    hologramScreen.open(player, name);
                    return;
                }
                updateHologram(player, definition, root -> section(root, "hologram")
                        .set("enabled", !support.hologramEnabled(definition)));
            }
            case SLOT_EXTRA_SHOWCASE -> {
                if (event.isRightClick()) {
                    IdleParticleSettings.Theme next = nextEnum(definition.idleParticles().theme(),
                            IdleParticleSettings.Theme.values(), event.isShiftClick());
                    updateShowcase(player, definition, root -> {
                        ConfigurationSection particles = section(root, "idle-particles");
                        particles.set("enabled", true);
                        particles.set("theme", next.name());
                    });
                } else {
                    updateShowcase(player, definition, root -> section(root, "idle-particles")
                            .set("enabled", !definition.idleParticles().enabled()));
                }
            }
            case SLOT_EXTRA_BUNDLE -> {
                bundleScreen.open(player, name, 0);
                return;
            }
            case SLOT_EXTRA_PROXIMITY -> {
                if (event.isRightClick()) {
                    proximityScreen.open(player, name, MachineProximityScreen.Section.HUB);
                    return;
                }
                boolean next = !definition.proximity().enabled();
                updateShowcase(player, definition, root -> section(root, "proximity-animation").set("enabled", next));
            }
            case SLOT_EXTRA_PURCHASE -> {
                if (event.isRightClick()) {
                    purchaseScreen.open(player, name);
                    return;
                }
                update(player, definition, root -> section(section(root, "cost"), "xp-purchase")
                        .set("enabled", !support.xpBuyEnabled(definition)));
            }
            case SLOT_EXTRA_WORKBENCH -> {
                workbenchScreen.openList(player, name);
                return;
            }
            case SLOT_EXTRA_GIFT -> update(player, definition, root -> section(root, "gift")
                    .set("enabled", !caseManager.giftable(name)));
            default -> {
                String feature = MachineExtrasScreen.featureAt(slot);
                if (feature == null || !extrasScreen.toggleFeature(player, feature)) return;
            }
        }
        extrasScreen.open(player, name);
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
        if (slot == SLOT_ANIMATION_CATEGORY) {
            animationScreen.cycleCategory(player, definition.name(), !event.isRightClick());
            return;
        }
        AnimationType selected = animationScreen.typeAtSlot(holder.page(), slot);
        if (selected == null) return;
        var availability = caseManager.animationAvailability(definition, selected);
        if (!availability.available()) {
            player.sendMessage(ru.privatenull.pncases.support.text.ColorUtil.colorize(
                    "&#FC7165Проблема с регионом кейса: " + availability.reason() + ". Исправьте её, чтобы сущности анимации могли появиться."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.18f, 1.0f);
            return;
        }
        support.updateAnimation(player, definition.name(), root -> section(root, "animation").set(
                "fixed", selected.name()
        ));
        animationScreen.open(player, definition.name());
    }

    private void handleHologram(Player player, InventoryClickEvent event, CaseDefinition definition, int slot) {
        if (backToExtras(player, definition, slot)) return;

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

    private void handleProximity(GuiSession holder, Player player, InventoryClickEvent event,
                                 CaseDefinition definition, int slot) {
        MachineProximityScreen.Section section = MachineProximityScreen.Section.at(holder.selection());
        String name = definition.name();
        if (slot == MachineProximityScreen.BACK) {
            if (section == MachineProximityScreen.Section.HUB) extrasScreen.open(player, name);
            else proximityScreen.open(player, name, MachineProximityScreen.Section.HUB);
            return;
        }

        ru.privatenull.pncases.cases.model.ProximitySettings p = definition.proximity();
        boolean up = !event.isRightClick();
        boolean big = event.isShiftClick();
        java.util.function.Consumer<ConfigurationSection> change = null;

        switch (section) {
            case HUB -> {
                MachineProximityScreen.Section target = switch (slot) {
                    case MachineProximityScreen.HUB_GENERAL -> MachineProximityScreen.Section.GENERAL;
                    case MachineProximityScreen.HUB_AUDIO -> MachineProximityScreen.Section.AUDIO;
                    case MachineProximityScreen.HUB_PARTICLES -> MachineProximityScreen.Section.PARTICLES;
                    case MachineProximityScreen.HUB_DECOR -> MachineProximityScreen.Section.DECOR;
                    case MachineProximityScreen.HUB_SANTA -> MachineProximityScreen.Section.SANTA;
                    case MachineProximityScreen.HUB_WORLD -> MachineProximityScreen.Section.WORLD;
                    default -> null;
                };
                if (target != null) {
                    proximityScreen.open(player, name, target);
                    return;
                }
                if (slot == MachineProximityScreen.HUB_ENABLED) change = setting("enabled", !p.enabled());
                if (slot == MachineProximityScreen.HUB_SCENE) {
                    if (p.scene() == ru.privatenull.pncases.cases.model.ProximitySettings.Scene.AUTUMN
                            && !ru.privatenull.pncases.cases.animation.core.AnimationSeason.newYearOpen()) {
                        player.sendMessage("§cЗимняя сцена " + ru.privatenull.pncases.cases.animation.core.AnimationSeason.NEW_YEAR_TEXT + ".");
                        return;
                    }
                    change = switchScene(p);
                }
            }
            case GENERAL -> change = switch (slot) {
                case MachineProximityScreen.GENERAL_RADIUS -> setting("radius", step(p.radius(), up, big ? 5 : 1, 3, 32));
                case MachineProximityScreen.GENERAL_SPREAD -> setting("spread", step(p.spread(), up, big ? 0.3 : 0.1, 0.6, 2));
                case MachineProximityScreen.GENERAL_ROTATION -> setting("rotation",
                        Math.floorMod(p.rotation() + (event.isRightClick() ? -90 : 90), 360));
                case MachineProximityScreen.GENERAL_FADE -> setting("fade_seconds", step(p.fadeSeconds(), up, big ? 2 : 0.5, 0.5, 15));
                default -> null;
            };
            case AUDIO -> change = switch (slot) {
                case MachineProximityScreen.AUDIO_MUSIC -> setting("music.enabled", !p.music().enabled());
                case MachineProximityScreen.AUDIO_MUSIC_VOLUME -> setting("music.volume", step(p.music().volume(), up, big ? 0.2 : 0.05, 0, 1));
                case MachineProximityScreen.AUDIO_MUSIC_PAUSE -> setting("music.pause_seconds", step(p.music().pauseSeconds(), up, big ? 5 : 1, 0, 120));
                case MachineProximityScreen.AUDIO_SOUNDS -> setting("sounds.enabled", !p.sounds().enabled());
                case MachineProximityScreen.AUDIO_SOUNDS_VOLUME -> setting("sounds.volume", step(p.sounds().volume(), up, big ? 0.5 : 0.1, 0, 2));
                default -> null;
            };
            case PARTICLES -> change = switch (slot) {
                case MachineProximityScreen.PARTICLES_DENSITY -> setting("particles.density", step(p.particles().density(), up, big ? 1 : 0.25, 0, 3));
                case MachineProximityScreen.PARTICLES_SNOWFALL -> setting("particles.snowfall", !p.particles().snowfall());
                case MachineProximityScreen.PARTICLES_SWIRL -> setting("particles.swirl", !p.particles().swirl());
                case MachineProximityScreen.PARTICLES_RING -> setting("particles.frost_ring", !p.particles().frostRing());
                case MachineProximityScreen.PARTICLES_GLINTS -> setting("particles.glints", !p.particles().glints());
                default -> null;
            };
            case DECOR -> change = switch (slot) {
                case MachineProximityScreen.DECOR_MOUNDS -> setting("decor.mounds", count(p.decor().mounds(), up, big, 30));
                case MachineProximityScreen.DECOR_TREES -> setting("decor.trees", count(p.decor().trees(), up, big, 12));
                case MachineProximityScreen.DECOR_SNOWMEN -> setting("decor.snowmen", count(p.decor().snowmen(), up, big, 6));
                case MachineProximityScreen.DECOR_CRYSTALS -> setting("decor.crystals", count(p.decor().crystals(), up, big, 10));
                case MachineProximityScreen.DECOR_GARLAND -> setting("decor.garland", count(p.decor().garland(), up, big, 40));
                case MachineProximityScreen.DECOR_SNOWFLAKES -> setting("decor.snowflakes", count(p.decor().snowflakes(), up, big, 30));
                case MachineProximityScreen.DECOR_GIFTS -> setting("decor.gifts", count(p.decor().gifts(), up, big, 16));
                default -> null;
            };
            case SANTA -> change = switch (slot) {
                case MachineProximityScreen.SANTA_ENABLED -> setting("santa.enabled", !p.santa().enabled());
                case MachineProximityScreen.SANTA_COAT -> setting("santa.coat",
                        nextEnum(p.santa().coat(), MachineProximityScreen.autumn(p)
                                ? MachineProximityScreen.SHIRTS : MachineProximityScreen.COATS,
                                event.isRightClick()).name());
                default -> null;
            };
            case WORLD -> change = switch (slot) {
                case MachineProximityScreen.WORLD_ENABLED -> setting("terrain.enabled", !p.terrain().enabled());
                case MachineProximityScreen.WORLD_RADIUS -> setting("terrain.radius",
                        Math.max(0, Math.min(12, p.terrain().radius() + (up ? 1 : -1) * (big ? 3 : 1))));
                case MachineProximityScreen.WORLD_STYLE -> setting("terrain.case_style", nextEnum(p.terrain().caseStyle(),
                        ru.privatenull.pncases.cases.model.ProximitySettings.CaseStyle.forScene(p.scene()),
                        event.isRightClick()).name());
                case MachineProximityScreen.WORLD_COLOR -> setting("terrain.case_color",
                        nextEnum(p.terrain().caseColor(), MachineProximityScreen.GIFT_COLORS, event.isRightClick()).name());
                case MachineProximityScreen.WORLD_BLOCK -> {
                    var next = nextEnum(p.terrain().caseBlock(), MachineProximityScreen.autumn(p)
                            ? MachineProximityScreen.AUTUMN_CASE_BLOCKS : MachineProximityScreen.CASE_BLOCKS,
                            event.isRightClick());
                    yield setting("terrain.case_block", next == null ? "NONE" : next.name());
                }
                case MachineProximityScreen.WORLD_SNOW_LAYER -> setting("terrain.snow_layer", !p.terrain().snowLayer());
                case MachineProximityScreen.WORLD_ALL_BLOCKS -> setting("terrain.replace_all_blocks", !p.terrain().allBlocks());
                case MachineProximityScreen.WORLD_MORPH -> setting("terrain.morph_shapes", !p.terrain().morphShapes());
                case MachineProximityScreen.WORLD_SCALE -> setting("terrain.case_scale", step(p.terrain().caseScale(), up, big ? 0.25 : 0.1, 0.5, 2));
                case MachineProximityScreen.WORLD_OPENING -> setting("terrain.keep_during_opening", !p.terrain().keepDuringOpening());
                case MachineProximityScreen.WORLD_KEEP -> setting("terrain.keep_case", !p.terrain().keepCase());
                case MachineProximityScreen.WORLD_SIDES -> setting("terrain.side_coverage", step(p.terrain().sideCoverage(), up, big ? 0.2 : 0.05, 0, 1));
                default -> null;
            };
        }
        if (change == null) return;
        updateShowcase(player, definition, change);
        proximityScreen.open(player, name, section);
    }

    /** Switches between the winter and the autumn scene and puts the new scene's own decor, look and music in place. */
    private static java.util.function.Consumer<ConfigurationSection> switchScene(
            ru.privatenull.pncases.cases.model.ProximitySettings current) {
        var next = current.scene() == ru.privatenull.pncases.cases.model.ProximitySettings.Scene.AUTUMN
                ? ru.privatenull.pncases.cases.model.ProximitySettings.Scene.WINTER
                : ru.privatenull.pncases.cases.model.ProximitySettings.Scene.AUTUMN;
        var d = ru.privatenull.pncases.cases.model.ProximitySettings.defaults(next);
        return root -> {
            ConfigurationSection s = section(root, "proximity-animation");
            s.set("scene", next.name());
            s.set("decor.mounds", d.decor().mounds());
            s.set("decor.trees", d.decor().trees());
            s.set("decor.snowmen", d.decor().snowmen());
            s.set("decor.crystals", d.decor().crystals());
            s.set("decor.garland", d.decor().garland());
            s.set("decor.snowflakes", d.decor().snowflakes());
            s.set("decor.gifts", d.decor().gifts());
            s.set("santa.coat", d.santa().coat().name());
            s.set("santa.messages", d.santa().messages());
            s.set("terrain.case_style", d.terrain().caseStyle().name());
            s.set("terrain.case_color", d.terrain().caseColor().name());
            s.set("terrain.case_block", d.terrain().caseBlock() == null ? "NONE" : d.terrain().caseBlock().name());
            s.set("terrain.ground", d.terrain().ground().stream()
                    .map(entry -> entry.material().name() + ":" + entry.weight()).toList());
            s.set("terrain.snow_layer", d.terrain().snowLayer());
            // The scene keys of the other spelling would only confuse the reader of the file.
            s.set("scarecrow", null);
            s.set("decor.leaf_piles", null);
            s.set("decor.maples", null);
            s.set("decor.pumpkins", null);
            s.set("decor.mushrooms", null);
            s.set("decor.lanterns", null);
            s.set("decor.leaves", null);
            s.set("decor.hay", null);
            s.set("terrain.leaf_layer", null);
        };
    }

    /** Writes one option of the {@code proximity-animation} section. */
    private static java.util.function.Consumer<ConfigurationSection> setting(String path, Object value) {
        return root -> section(root, "proximity-animation").set(path, value);
    }

    private static double step(double current, boolean up, double amount, double min, double max) {
        double next = Math.round((current + (up ? amount : -amount)) * 100.0) / 100.0;
        return Math.max(min, Math.min(max, next));
    }

    private static int count(int current, boolean up, boolean big, int max) {
        int amount = big ? 3 : 1;
        return Math.max(0, Math.min(max, current + (up ? amount : -amount)));
    }

    private static <T> T nextEnum(T current, java.util.List<T> values, boolean backwards) {
        int index = Math.max(0, values.indexOf(current));
        return values.get(Math.floorMod(index + (backwards ? -1 : 1), values.size()));
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
                player.sendMessage(ru.privatenull.pncases.support.text.ColorUtil.colorize(
                        "&#FC7165Сначала включите «Каталог содержимого» в разделе «Дополнительно»."));
                return;
            }
            update(player, definition, root -> section(root, "gui").set(
                    "preview_enabled", !definition.guiLayout().previewEnabled()
            ));
            menuScreen.open(player, definition.name());
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
        if (backToExtras(player, definition, slot)) return;

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

    private boolean backToExtras(Player player, CaseDefinition definition, int slot) {
        if (slot != SLOT_BACK) return false;
        extrasScreen.open(player, definition.name());
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
