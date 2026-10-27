package ru.privatenull.pncases.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Called on the main thread after virtual keys were added to a player's balance in storage. */
public final class CaseKeyGiveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String caseId;
    private final int amount;
    private final int balance;

    public CaseKeyGiveEvent(Player player, String caseId, int amount, int balance) {
        this.player = player;
        this.caseId = caseId;
        this.amount = amount;
        this.balance = balance;
    }

    public Player getPlayer() {
        return player;
    }

    /** Key id, which always equals the id of the case it opens. */
    public String getCaseId() {
        return caseId;
    }

    public int getAmount() {
        return amount;
    }

    /** Balance after the keys were added. */
    public int getBalance() {
        return balance;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
