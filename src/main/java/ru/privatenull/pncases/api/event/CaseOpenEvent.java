package ru.privatenull.pncases.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called on the main thread before a player starts opening a case. Cancelling it stops the opening
 * before a key is spent or a reward is reserved.
 */
public final class CaseOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String caseId;
    private final int amount;
    private boolean cancelled;

    public CaseOpenEvent(Player player, String caseId, int amount) {
        this.player = player;
        this.caseId = caseId;
        this.amount = amount;
    }

    public Player getPlayer() {
        return player;
    }

    public String getCaseId() {
        return caseId;
    }

    /** Number of keys requested: 1 for a normal opening, more for a bulk opening. */
    public int getAmount() {
        return amount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
