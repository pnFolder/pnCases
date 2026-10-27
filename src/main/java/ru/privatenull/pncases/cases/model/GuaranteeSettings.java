package ru.privatenull.pncases.cases.model;

/** Optional per-player protection against long streaks without a valuable reward. */
public record GuaranteeSettings(
        boolean enabled,
        int openings,
        Reward.Rarity minimumRarity,
        boolean resetAfterWin
) {

    public GuaranteeSettings {
        openings = Math.max(1, openings);
        minimumRarity = minimumRarity == null ? Reward.Rarity.LEGENDARY : minimumRarity;
    }

    public static GuaranteeSettings disabled() {
        return new GuaranteeSettings(false, 50, Reward.Rarity.LEGENDARY, true);
    }

    public boolean qualifies(Reward reward) {
        return reward != null && reward.rarity().ordinal() >= minimumRarity.ordinal();
    }
}
