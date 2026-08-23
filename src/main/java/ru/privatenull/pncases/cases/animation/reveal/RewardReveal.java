package ru.privatenull.pncases.cases.animation.reveal;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;

/** Version-neutral visual used to present the selected reward. */
public interface RewardReveal {
    List<Entity> entities();

    void tick(Location center, int tick);

    void remove();
}
