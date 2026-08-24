package net.wesjd.anvilgui.version;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.lang.reflect.Method;

public final class Wrapper1_21_R7 implements VersionWrapper {

    private ServerPlayer toNms(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    @Override
    public int getNextContainerId(Player player, AnvilContainerWrapper container) {
        return ((AnvilContainer) container).getContainerId();
    }

    @Override
    public void handleInventoryCloseEvent(Player player) {
        try {
            Class<?> reasonClass = Class.forName("org.bukkit.event.inventory.InventoryCloseEvent$Reason");
            Method method;
            Object[] arguments;
            try {
                method = CraftEventFactory.class.getMethod(
                        "handleInventoryCloseEvent",
                        net.minecraft.world.entity.player.Player.class,
                        reasonClass
                );
                arguments = new Object[]{toNms(player), reasonClass.getField("UNKNOWN").get(null)};
            } catch (NoSuchMethodException ignored) {
                method = CraftEventFactory.class.getMethod(
                        "handleInventoryCloseEvent",
                        net.minecraft.world.entity.player.Player.class
                );
                arguments = new Object[]{toNms(player)};
            }
            method.invoke(null, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
        toNms(player).doCloseContainer();
    }

    @Override
    public void sendPacketOpenWindow(Player player, int containerId, Object inventoryTitle) {
        toNms(player).connection.send(new ClientboundOpenScreenPacket(
                containerId,
                MenuType.ANVIL,
                (Component) inventoryTitle
        ));
    }

    @Override
    public void sendPacketCloseWindow(Player player, int containerId) {
        toNms(player).connection.send(new ClientboundContainerClosePacket(containerId));
    }

    @Override
    public void sendPacketExperienceChange(Player player, int experienceLevel) {
        toNms(player).connection.send(new ClientboundSetExperiencePacket(0.0F, 0, experienceLevel));
    }

    @Override
    public void setActiveContainerDefault(Player player) {
        toNms(player).containerMenu = toNms(player).inventoryMenu;
    }

    @Override
    public void setActiveContainer(Player player, AnvilContainerWrapper container) {
        toNms(player).containerMenu = (AbstractContainerMenu) container;
    }

    @Override
    public void setActiveContainerId(AnvilContainerWrapper container, int containerId) {
    }

    @Override
    public void addActiveContainerSlotListener(AnvilContainerWrapper container, Player player) {
        toNms(player).initMenu((AbstractContainerMenu) container);
    }

    @Override
    public AnvilContainerWrapper newContainerAnvil(Player player, Object title) {
        return new AnvilContainer(player, toNms(player).nextContainerCounter(), (Component) title);
    }

    @Override
    public Object literalChatComponent(String content) {
        return Component.literal(content);
    }

    @Override
    public Object jsonChatComponent(String json) {
        return CraftChatMessage.fromJSON(json);
    }

    private static final class AnvilContainer extends AnvilMenu implements AnvilContainerWrapper {

        private AnvilContainer(Player player, int containerId, Component title) {
            super(
                    containerId,
                    ((CraftPlayer) player).getHandle().getInventory(),
                    ContainerLevelAccess.create(
                            ((CraftWorld) player.getWorld()).getHandle(),
                            new BlockPos(0, 0, 0)
                    )
            );
            checkReachable = false;
            setTitle(title);
        }

        @Override
        public void createResult() {
            Slot output = getSlot(2);
            if (!output.hasItem()) output.set(getSlot(0).getItem().copy());
            cost.set(0);
            sendAllDataToRemote();
            broadcastChanges();
        }

        @Override
        public void removed(net.minecraft.world.entity.player.Player player) {
        }

        @Override
        protected void clearContainer(net.minecraft.world.entity.player.Player player, Container container) {
        }

        private int getContainerId() {
            return containerId;
        }

        @Override
        public String getRenameText() {
            return itemName;
        }

        @Override
        public void setRenameText(String text) {
            Slot input = getSlot(0);
            if (input.hasItem()) input.getItem().set(DataComponents.CUSTOM_NAME, Component.literal(text));
        }

        @Override
        public Inventory getBukkitInventory() {
            return getBukkitView().getTopInventory();
        }
    }
}
