package ru.privatenull.pncases.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called on the main thread after a reward has been delivered to a player. The grant has already
 * happened, so this event cannot be cancelled.
 */
public final class CaseRewardGrantEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String caseId;
    private final String rewardType;
    private final String rewardLabel;

    public CaseRewardGrantEvent(Player player, String caseId, String rewardType, String rewardLabel) {
        this.player = player;
        this.caseId = caseId;
        this.rewardType = rewardType;
        this.rewardLabel = rewardLabel;
    }

    public Player getPlayer() {
        return player;
    }

    /** Case id, or null for a reward delivered outside a case (for example a recovered pending reward). */
    public String getCaseId() {
        return caseId;
    }

    /** Reward type name: ITEM, VAULT, PLAYERPOINTS, LUCKPERMS or PERSONAL. */
    public String getRewardType() {
        return rewardType;
    }

    /** Colored display name of the reward. */
    public String getRewardLabel() {
        return rewardLabel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
