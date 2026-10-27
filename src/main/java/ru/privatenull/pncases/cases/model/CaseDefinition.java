package ru.privatenull.pncases.cases.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CaseDefinition {

    public static final List<String> DEFAULT_BROADCAST_LINES = List.of(
            "",
            "&#EFF7B9&lК&#68AF94&lЕ&#8DBF97&lЙ&#B3CF9A&lС&#EFF7B9&lЫ",
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
    private final ProximitySettings proximity;
    private final List<String> children;

    private final int buyKeyWithXpLevels;
    private final AnimationType fixedAnimation;
    private final List<ItemStack> animationItems;
    private final boolean broadcastEnabled;
    private final List<String> broadcastLines;
    private final GuaranteeSettings guarantee;

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
            AnimationType fixedAnimation,
            List<ItemStack> animationItems,
            boolean broadcastEnabled,
            List<String> broadcastLines,
            GuaranteeSettings guarantee,
            List<Reward> rewards
    ) {
        this(name, displayName, blockLocations, guiTitle, openButton, guiLayout, idleParticles, null,
                buyKeyWithXpLevels, fixedAnimation, animationItems, broadcastEnabled, broadcastLines,
                guarantee, rewards);
    }

    public CaseDefinition(
            String name,
            String displayName,
            List<Location> blockLocations,
            String guiTitle,
            ItemStack openButton,
            CaseGuiLayout guiLayout,
            IdleParticleSettings idleParticles,
            ProximitySettings proximity,
            int buyKeyWithXpLevels,
            AnimationType fixedAnimation,
            List<ItemStack> animationItems,
            boolean broadcastEnabled,
            List<String> broadcastLines,
            GuaranteeSettings guarantee,
            List<Reward> rewards
    ) {
        this(name, displayName, blockLocations, guiTitle, openButton, guiLayout, idleParticles, proximity,
                buyKeyWithXpLevels, fixedAnimation, animationItems, broadcastEnabled, broadcastLines,
                guarantee, rewards, List.of());
    }

    public CaseDefinition(
            String name,
            String displayName,
            List<Location> blockLocations,
            String guiTitle,
            ItemStack openButton,
            CaseGuiLayout guiLayout,
            IdleParticleSettings idleParticles,
            ProximitySettings proximity,
            int buyKeyWithXpLevels,
            AnimationType fixedAnimation,
            List<ItemStack> animationItems,
            boolean broadcastEnabled,
            List<String> broadcastLines,
            GuaranteeSettings guarantee,
            List<Reward> rewards,
            List<String> children
    ) {
        this.name = name;
        this.children = children == null ? List.of() : List.copyOf(children);
        this.displayName = displayName == null || displayName.isBlank() ? name : displayName;
        this.blockLocations = blockLocations == null
                ? List.of()
                : List.copyOf(blockLocations.stream().filter(location -> location != null).toList());
        this.blockLocation = this.blockLocations.isEmpty() ? null : this.blockLocations.get(0);
        this.guiTitle = guiTitle;
        this.openButton = openButton;
        this.guiLayout = guiLayout == null ? CaseGuiLayout.defaults() : guiLayout;
        this.idleParticles = idleParticles == null ? IdleParticleSettings.defaults() : idleParticles;
        this.proximity = proximity == null ? ProximitySettings.defaults() : proximity;
        this.buyKeyWithXpLevels = buyKeyWithXpLevels;
        this.fixedAnimation = fixedAnimation;
        this.animationItems = animationItems;
        this.broadcastEnabled = broadcastEnabled;
        this.broadcastLines = broadcastLines == null
                ? DEFAULT_BROADCAST_LINES
                : List.copyOf(broadcastLines);
        this.guarantee = guarantee == null ? GuaranteeSettings.disabled() : guarantee;
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
    public ProximitySettings proximity() { return proximity; }

    /** Names of the cases listed inside this case; when it has any, opening it shows their menu instead. */
    public List<String> children() { return children; }

    public boolean isBundle() { return !children.isEmpty(); }

    public int buyKeyWithXpLevels() { return buyKeyWithXpLevels; }

    public AnimationType fixedAnimation() { return fixedAnimation; }
    public List<ItemStack> animationItems() { return animationItems; }
    public boolean broadcastEnabled() { return broadcastEnabled; }
    public List<String> broadcastLines() { return broadcastLines; }
    public GuaranteeSettings guarantee() { return guarantee; }

    public List<Reward> rewards() { return rewards; }
}
