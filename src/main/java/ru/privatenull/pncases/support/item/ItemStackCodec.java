package ru.privatenull.pncases.support.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Canonical lossless ItemStack codec for YAML and database payloads.
 *
 * <p>Servers without {@code ItemStack#serializeAsBytes} (old Paper) store the item through Bukkit's object stream
 * instead, marked with a prefix, so an item is never dropped silently. Both forms are read on every server.</p>
 */
public final class ItemStackCodec {

    private static final String OBJECT_PREFIX = "obj:";

    private ItemStackCodec() {
    }

    public static String encode(ItemStack item) {
        if (item == null) return null;
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (RuntimeException | LinkageError unavailable) {
            return encodeObject(item);
        }
    }

    public static ItemStack decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.startsWith(OBJECT_PREFIX)) return decodeObject(value.substring(OBJECT_PREFIX.length()));
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
        } catch (RuntimeException | LinkageError | java.io.IOException | ClassNotFoundException ignored) {
            return null;
        }
    }

    private static String encodeObject(ItemStack item) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(item);
            out.flush();
            return OBJECT_PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (java.io.IOException | RuntimeException failure) {
            return null;
        }
    }

    private static ItemStack decodeObject(String payload) throws java.io.IOException, ClassNotFoundException {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(payload)))) {
            return in.readObject() instanceof ItemStack stack ? stack : null;
        }
    }
}
