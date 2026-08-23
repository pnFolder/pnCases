package ru.privatenull.pncases.cases.animation.effect;

import org.bukkit.Particle;
import ru.privatenull.pnlibrary.compat.BukkitCompat.ParticleCompat;

/** Version-neutral visual primitives used only by pnCases animations. */
public final class AnimationEffects {

    public enum Kind {
        BLOCK("BLOCK", "BLOCK_CRACK", "BLOCK_DUST"),
        DUST("DUST", "REDSTONE"),
        ENCHANT("ENCHANT", "ENCHANTMENT_TABLE"),
        EXPLOSION("EXPLOSION", "EXPLOSION_NORMAL"),
        FIREWORK("FIREWORK", "FIREWORKS_SPARK"),
        SLIME("ITEM_SLIME", "SLIME"),
        LARGE_SMOKE("LARGE_SMOKE", "SMOKE_LARGE"),
        POOF("POOF", "EXPLOSION_NORMAL"),
        SMOKE("SMOKE", "SMOKE_NORMAL"),
        TOTEM("TOTEM_OF_UNDYING", "TOTEM"),
        WITCH("WITCH", "SPELL_WITCH");

        private final String[] names;

        Kind(String... names) {
            this.names = names;
        }
    }

    private AnimationEffects() {
    }

    public static Particle particle(Kind kind) {
        return ParticleCompat.first(kind.names);
    }

}
