package ru.privatenull.pncases.cases.animation.effect;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the selected reward readable by suppressing only nearby particle packets for its opener. */
public final class RewardVisibilityGuard implements AutoCloseable {

    private final ProtocolManager protocol;
    private final PacketAdapter listener;
    private final Map<UUID, Zone> zones = new ConcurrentHashMap<>();
    private final double radius;
    private final int durationTicks;

    public RewardVisibilityGuard(Plugin plugin) {
        protocol = ProtocolLibrary.getProtocolManager();
        radius = Math.max(0.25, plugin.getConfig().getDouble(
                "animations.reward-visibility.particle-free-radius", 1.25));
        durationTicks = Math.max(1, plugin.getConfig().getInt(
                "animations.reward-visibility.duration-ticks", 80));
        listener = new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.WORLD_PARTICLES) {
            @Override
            public void onPacketSending(PacketEvent event) {
                suppressNearReward(event);
            }
        };
        protocol.addPacketListener(listener);
    }

    public void protect(Player player, Location center) {
        if (player == null || center == null || center.getWorld() == null) return;
        long expiresAt = System.nanoTime() + durationTicks * 50_000_000L;
        zones.put(player.getUniqueId(), new Zone(
                center.getWorld().getUID(), center.getX(), center.getY(), center.getZ(),
                radius * radius, expiresAt));
    }

    public void clear(UUID playerId) {
        if (playerId != null) zones.remove(playerId);
    }

    private void suppressNearReward(PacketEvent event) {
        Player player = event.getPlayer();
        Zone zone = zones.get(player.getUniqueId());
        if (zone == null) return;
        if (System.nanoTime() >= zone.expiresAt()) {
            zones.remove(player.getUniqueId(), zone);
            return;
        }
        if (!player.getWorld().getUID().equals(zone.worldId())) return;
        var coordinates = event.getPacket().getDoubles();
        if (coordinates.size() < 3) return;
        double dx = coordinates.read(0) - zone.x();
        double dy = coordinates.read(1) - zone.y();
        double dz = coordinates.read(2) - zone.z();
        if (dx * dx + dy * dy + dz * dz <= zone.radiusSquared()) event.setCancelled(true);
    }

    @Override
    public void close() {
        protocol.removePacketListener(listener);
        zones.clear();
    }

    private record Zone(UUID worldId, double x, double y, double z,
                        double radiusSquared, long expiresAt) {
    }
}
