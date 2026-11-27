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
