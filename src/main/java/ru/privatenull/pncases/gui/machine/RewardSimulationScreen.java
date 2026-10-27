package ru.privatenull.pncases.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.support.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static ru.privatenull.pncases.gui.machine.RewardLabels.chancePercent;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.CONTENT_SLOTS;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_BACK;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_NEXT;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.SLOT_PREVIOUS;

/** Runs 100 000 virtual openings of a case and shows how often each reward fell. */
final class RewardSimulationScreen {

    /** Opens the reward list of a case; used to return from the simulation. */
    interface ListNavigation {
        void openList(Player player, String caseName, int page);
    }

    private static final int OPENINGS = 100_000;

    private final CaseManager caseManager;
    private final MachineItemFactory items;
    private final RewardLabels labels;
    private final ListNavigation navigation;
    private final Map<UUID, Simulation> simulations = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> simulationRequests = new ConcurrentHashMap<>();

    RewardSimulationScreen(CaseManager caseManager, MachineItemFactory items, RewardLabels labels,
                           ListNavigation navigation) {
        this.caseManager = caseManager;
        this.items = items;
        this.labels = labels;
        this.navigation = navigation;
    }

    void cancel(Player player) {
        simulations.remove(player.getUniqueId());
        simulationRequests.remove(player.getUniqueId());
    }

    void handleClick(GuiSession holder, Player player, CaseDefinition definition, int slot) {
        Simulation simulation = simulations.get(player.getUniqueId());
        if (slot == SLOT_BACK) {
            navigation.openList(player, definition.name(), holder.page());
        } else if (slot == SLOT_PREVIOUS && holder.page() > 0) {
            open(player, definition, simulation, holder.page() - 1);
        } else if (slot == SLOT_NEXT && simulation != null) {
            open(player, definition, simulation, holder.page() + 1);
        }
    }

    /** A distribution needs something to compare: one reward always falls 100% of the time. */
    static final int MINIMUM_REWARDS = 2;

    static boolean canSimulate(int rewardCount) {
        return rewardCount >= MINIMUM_REWARDS;
    }

    void start(Player player, CaseDefinition definition) {
        List<Reward> rewards = List.copyOf(definition.rewards());
        if (!canSimulate(rewards.size())) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.2f, 1.0f);
            player.sendMessage(ColorUtil.colorize(rewards.isEmpty()
                    ? "&#FC7165В кейсе нет наград, проверять нечего."
                    : "&#FC7165В кейсе одна награда: она выпадает всегда. Добавьте хотя бы ещё одну награду, чтобы проверить шансы."));
            return;
        }
        UUID playerId = player.getUniqueId();
        UUID request = UUID.randomUUID();
        if (simulationRequests.putIfAbsent(playerId, request) != null) {
            player.sendMessage(ColorUtil.colorize("&#A6B0BAСимуляция уже выполняется, подождите."));
            return;
        }
        player.closeInventory();
        Inventory previous = InventoryViewCompat.topInventory(player);
        player.sendMessage(ColorUtil.colorize("&#A6B0BAВыполняется 100 000 виртуальных открытий..."));
        caseManager.getPlugin().tasks().async(() -> {
            try {
                Map<Reward, Integer> counts = new IdentityHashMap<>();
                for (Reward reward : rewards) counts.put(reward, 0);
                for (int index = 0; index < OPENINGS; index++) {
                    if ((index & 1023) == 0 && (!caseManager.getPlugin().isEnabled()
                            || !request.equals(simulationRequests.get(playerId)))) {
                        simulationRequests.remove(playerId, request);
                        return;
                    }
                    Reward selected = Reward.select(rewards);
                    counts.put(selected, counts.getOrDefault(selected, 0) + 1);
                }
                List<SimulationResult> results = new ArrayList<>();
                for (int index = 0; index < rewards.size(); index++) {
                    Reward reward = rewards.get(index);
                    results.add(new SimulationResult(index, counts.getOrDefault(reward, 0)));
                }
                Simulation simulation = new Simulation(definition, List.copyOf(results));
                if (!caseManager.getPlugin().isEnabled()) return;
                caseManager.getPlugin().tasks().run(() -> {
                    if (!simulationRequests.remove(playerId, request) || !player.isOnline()
                            || InventoryViewCompat.topInventory(player) != previous) return;
                    simulations.put(player.getUniqueId(), simulation);
                    open(player, definition, simulation, 0);
                });
            } catch (RuntimeException failure) {
                simulationRequests.remove(playerId, request);
                caseManager.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
                        "Не удалось выполнить симуляцию кейса '" + definition.name() + "'.", failure);
                if (caseManager.getPlugin().isEnabled()) {
                    caseManager.getPlugin().tasks().run(() -> {
                        if (player.isOnline()) player.sendMessage(ColorUtil.colorize(
                                "&#FC7165Симуляция не выполнена. Проверьте настройки шансов и консоль."));
                    });
                }
            }
        });
    }

    private void open(Player player, CaseDefinition definition, Simulation simulation, int requestedPage) {
        if (simulation == null || simulation.definition != caseManager.getCaseByName(definition.name())) {
            player.sendMessage(ColorUtil.colorize("&#A6B0BAНастройки кейса изменились. Запустите симуляцию заново."));
            navigation.openList(player, definition.name(), 0);
            return;
        }
        int pages = RewardScreenLayout.pageCount(simulation.results.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineRewardSimulation(definition.name(), page), 54,
                ColorUtil.colorize("&0Симуляция наград"));
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        int start = page * CONTENT_SLOTS.size();
        for (int position = 0; position < CONTENT_SLOTS.size(); position++) {
            int resultIndex = start + position;
            if (resultIndex >= simulation.results.size()) break;
            SimulationResult result = simulation.results.get(resultIndex);
            Reward reward = definition.rewards().get(result.rewardIndex);
            ItemStack button = reward.visualItem() == null
                    ? new ItemStack(Material.CHEST)
                    : reward.visualItem().clone();
            button.setAmount(1);
            ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.colorize(items.name(labels.plainRewardName(reward))));
                meta.setLore(items.lore(
                        List.of("Результат 100 000 виртуальных открытий"),
                        List.of(
                                "Базовый шанс", chancePercent(reward, definition) + "%",
                                "Выпадений", String.valueOf(result.hits),
                                "Доля в симуляции", String.format(Locale.US, "%.3f%%", result.hits / 1000.0D)
                        ), null, null).stream().map(ColorUtil::colorize).toList());
                button.setItemMeta(meta);
            }
            inventory.setItem(CONTENT_SLOTS.get(position), button);
        }
        inventory.setItem(SLOT_BACK, items.backButton(definition));
        if (page > 0) inventory.setItem(SLOT_PREVIOUS,
                items.builtInButton(MachineIcon.REWARDS_PREVIOUS, "&#65D1FCПредыдущая", List.of()));
        if (page + 1 < pages) inventory.setItem(SLOT_NEXT,
                items.builtInButton(MachineIcon.REWARDS_NEXT, "&#65D1FCСледующая", List.of()));
        caseManager.getPlugin().openGui(player, inventory);
    }

    private record Simulation(CaseDefinition definition, List<SimulationResult> results) { }

    private record SimulationResult(int rewardIndex, int hits) { }
}
