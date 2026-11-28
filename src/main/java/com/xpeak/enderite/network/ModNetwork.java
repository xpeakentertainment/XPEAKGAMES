package com.xpeak.enderite.network;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.cling.WallCling;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EnderiteMod.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(ClingJumpPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClingJumpPacket::encode)
                .decoder(ClingJumpPacket::decode)
                .consumerMainThread(ClingJumpPacket::handle)
                .add();
        CHANNEL.messageBuilder(LimboStatePacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LimboStatePacket::encode)
                .decoder(LimboStatePacket::decode)
                .consumerMainThread(LimboStatePacket::handle)
                .add();
        registerRobot();
    }

    public static void registerRobot() {
        CHANNEL.messageBuilder(ArmInputPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ArmInputPacket::encode).decoder(ArmInputPacket::decode).consumerMainThread(ArmInputPacket::handle).add();
        CHANNEL.messageBuilder(ArmSlotPacket.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ArmSlotPacket::encode).decoder(ArmSlotPacket::decode).consumerMainThread(ArmSlotPacket::handle).add();
        CHANNEL.messageBuilder(ArmAnimPacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ArmAnimPacket::encode).decoder(ArmAnimPacket::decode).consumerMainThread(ArmAnimPacket::handle).add();
    }

    public static void sendArmInput(boolean left, boolean right) {
        CHANNEL.sendToServer(new ArmInputPacket(left, right));
    }

    public static void sendArmSlot(boolean left, boolean creative, net.minecraft.world.item.ItemStack stack) {
        CHANNEL.sendToServer(new ArmSlotPacket(left, creative, stack));
    }

    public static void sendArmAnim(ServerPlayer player, boolean left, boolean right) {
        if (player.connection == null) return;
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new ArmAnimPacket(player.getId(), left, right));
    }

    /** Cliente -> servidor: que botones (izq/der) estas pulsando con los brazos robot. */
    public record ArmInputPacket(boolean left, boolean right) {
        static void encode(ArmInputPacket p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.left);
            buf.writeBoolean(p.right);
        }

        static ArmInputPacket decode(FriendlyByteBuf buf) {
            return new ArmInputPacket(buf.readBoolean(), buf.readBoolean());
        }

        static void handle(ArmInputPacket p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) com.xpeak.enderite.robot.RobotServer.setInput(sender, p.left, p.right);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: click en uno de los huecos de brazo del inventario. */
    public record ArmSlotPacket(boolean left, boolean creative, net.minecraft.world.item.ItemStack stack) {
        static void encode(ArmSlotPacket p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.left);
            buf.writeBoolean(p.creative);
            buf.writeItem(p.stack);
        }

        static ArmSlotPacket decode(FriendlyByteBuf buf) {
            return new ArmSlotPacket(buf.readBoolean(), buf.readBoolean(), buf.readItem());
        }

        static void handle(ArmSlotPacket p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) com.xpeak.enderite.robot.RobotArmSlots.handleClick(sender, p.left, p.creative, p.stack);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> clientes: animacion de los brazos (motosierra girando, disparo). */
    public record ArmAnimPacket(int entityId, boolean left, boolean right) {
        static void encode(ArmAnimPacket p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.entityId);
            buf.writeBoolean(p.left);
            buf.writeBoolean(p.right);
        }

        static ArmAnimPacket decode(FriendlyByteBuf buf) {
            return new ArmAnimPacket(buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }

        static void handle(ArmAnimPacket p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xpeak.enderite.client.RobotClient.setAnim(p.entityId, p.left, p.right));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void sendLimboState(ServerPlayer player, int phase, int ticksToDawn) {
        if (player.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new LimboStatePacket(phase, ticksToDawn));
    }

    public static void sendClingJump() {
        CHANNEL.sendToServer(new ClingJumpPacket());
    }

    /** Cliente -> servidor: salto extra desde la pared. */
    public record ClingJumpPacket() {
        static void encode(ClingJumpPacket p, FriendlyByteBuf buf) {
        }

        static ClingJumpPacket decode(FriendlyByteBuf buf) {
            return new ClingJumpPacket();
        }

        static void handle(ClingJumpPacket p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) WallCling.wallJump(sender);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> cliente: fase del Limbo y tiempo hasta el amanecer. */
    public record LimboStatePacket(int phase, int ticksToDawn) {
        static void encode(LimboStatePacket p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.phase);
            buf.writeVarInt(p.ticksToDawn);
        }

        static LimboStatePacket decode(FriendlyByteBuf buf) {
            return new LimboStatePacket(buf.readVarInt(), buf.readVarInt());
        }

        static void handle(LimboStatePacket p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xpeak.enderite.client.ClientLimboState.set(p.phase, p.ticksToDawn));
            ctx.get().setPacketHandled(true);
        }
    }

    private ModNetwork() {}
}
