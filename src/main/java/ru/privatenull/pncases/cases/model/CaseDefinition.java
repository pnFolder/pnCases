package ru.privatenull.pncases.cases.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CaseDefinition {

    public static final List<String> DEFAULT_BROADCAST_LINES = List.of(
            "",
            "&#EFF7B9К§x§6§8§A§F§9§4Е§x§8§D§B§F§9§7Й§x§B§3§C§F§9§AС&#EFF7B9Ы",
            "",
            "&#EFF7B9▸ &fИгрок &#EFF7B9{player} &fоткрыл кейс &#EFF7B9{case}",
            "&#EFF7B9▸ &fНаграда: &f{reward}",
            ""
    );

    private final String name;
    private final String displayName;
    private final Location blockLocation;
    private final List<Location> blockLocations;

    private final String guiTitle;
    private final ItemStack openButton;
    private final CaseGuiLayout guiLayout;
    private final IdleParticleSettings idleParticles;

    private final int buyKeyWithXpLevels;
    private final int durationTicks;
    private final int cycleEveryTicks;
    private final double riseBlocks;
    private final float spinDegreesPerTick;
    private final AnimationType fixedAnimation;
    private final List<ItemStack> animationItems;
    private final boolean broadcastEnabled;
    private final List<String> broadcastLines;

    private final List<Reward> rewards;

    public CaseDefinition(
            String name,
            String displayName,
            List<Location> blockLocations,
            String guiTitle,
            ItemStack openButton,
            CaseGuiLayout guiLayout,
            IdleParticleSettings idleParticles,
            int buyKeyWithXpLevels,
            int durationTicks,
            int cycleEveryTicks,
            double riseBlocks,
            float spinDegreesPerTick,
            AnimationType fixedAnimation,
            List<ItemStack> animationItems,
            boolean broadcastEnabled,
            List<String> broadcastLines,
            List<Reward> rewards
    ) {
        this.name = name;
        this.displayName = displayName == null || displayName.isBlank() ? name : displayName;
        this.blockLocations = blockLocations == null
                ? List.of()
                : List.copyOf(blockLocations.stream().filter(location -> location != null).toList());
        this.blockLocation = this.blockLocations.isEmpty() ? null : this.blockLocations.get(0);
        this.guiTitle = guiTitle;
        this.openButton = openButton;
        this.guiLayout = guiLayout == null ? CaseGuiLayout.defaults() : guiLayout;
        this.idleParticles = idleParticles == null ? IdleParticleSettings.defaults() : idleParticles;
        this.buyKeyWithXpLevels = buyKeyWithXpLevels;
        this.durationTicks = durationTicks;
        this.cycleEveryTicks = cycleEveryTicks;
        this.riseBlocks = riseBlocks;
        this.spinDegreesPerTick = spinDegreesPerTick;
        this.fixedAnimation = fixedAnimation;
        this.animationItems = animationItems;
        this.broadcastEnabled = broadcastEnabled;
        this.broadcastLines = broadcastLines == null
                ? DEFAULT_BROADCAST_LINES
                : List.copyOf(broadcastLines);
        this.rewards = rewards;
    }

    public String name() { return name; }
    public String displayName() { return displayName; }
    public Location blockLocation() { return blockLocation; }
    public List<Location> blockLocations() { return blockLocations; }
    public String guiTitle() { return guiTitle; }
    public ItemStack openButton() { return openButton; }
    public CaseGuiLayout guiLayout() { return guiLayout; }
    public IdleParticleSettings idleParticles() { return idleParticles; }

    public int buyKeyWithXpLevels() { return buyKeyWithXpLevels; }

    public int durationTicks() { return durationTicks; }
    public int cycleEveryTicks() { return cycleEveryTicks; }
    public double riseBlocks() { return riseBlocks; }
    public float spinDegreesPerTick() { return spinDegreesPerTick; }
    public AnimationType fixedAnimation() { return fixedAnimation; }
    public List<ItemStack> animationItems() { return animationItems; }
    public boolean broadcastEnabled() { return broadcastEnabled; }
    public List<String> broadcastLines() { return broadcastLines; }

    public List<Reward> rewards() { return rewards; }
}
