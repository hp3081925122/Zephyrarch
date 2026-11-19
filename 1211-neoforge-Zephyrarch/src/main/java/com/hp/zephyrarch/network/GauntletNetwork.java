package com.hp.zephyrarch.network;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.client.GauntletClientController;
import com.hp.zephyrarch.item.SovereignGauntletItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class GauntletNetwork {
    public static final byte ACTION_CHARGE_START = 1;
    public static final byte ACTION_PUNCH_FIRE = 2;
    public static final byte ACTION_CHARGE_STOP = 3;
    public static final byte ACTION_WIND_DASH = 4;

    private GauntletNetwork() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(HoldPayload.TYPE, HoldPayload.STREAM_CODEC, GauntletNetwork::handleHoldServer);
        registrar.playToClient(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, GauntletNetwork::handleActionClient);
    }

    private static void handleHoldServer(HoldPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                SovereignGauntletItem.setHoldingAttack(sp, payload.holding());
            }
        });
    }

    private static void handleActionClient(ActionPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() -> ClientBridge.accept(payload));
        }
    }

    public static void broadcastAction(Entity player, byte action, InteractionHand hand, int maxChargeTicks, Vec3 pos, Vec3 dir) {
        if (!player.level().isClientSide) {
            byte handOrd = (byte) (hand == InteractionHand.OFF_HAND ? 1 : 0);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ActionPayload(
                    player.getId(),
                    action,
                    handOrd,
                    maxChargeTicks,
                    pos.x,
                    pos.y,
                    pos.z,
                    (float) dir.x,
                    (float) dir.y,
                    (float) dir.z
            ));
        }
    }

    public record HoldPayload(boolean holding) implements CustomPacketPayload {
        public static final Type<HoldPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(Zephyrarch.MOD_ID, "gauntlet_hold")
        );

        public static final StreamCodec<FriendlyByteBuf, HoldPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.holding),
                buf -> new HoldPayload(buf.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActionPayload(
            int entityId,
            byte action,
            byte handOrd,
            int maxChargeTicks,
            double x,
            double y,
            double z,
            float dx,
            float dy,
            float dz
    ) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(Zephyrarch.MOD_ID, "gauntlet_action")
        );

        public static final StreamCodec<FriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeByte(p.action);
                    buf.writeByte(p.handOrd);
                    buf.writeVarInt(p.maxChargeTicks);
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeFloat(p.dx);
                    buf.writeFloat(p.dy);
                    buf.writeFloat(p.dz);
                },
                buf -> new ActionPayload(
                        buf.readVarInt(),
                        buf.readByte(),
                        buf.readByte(),
                        buf.readVarInt(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readFloat()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final class ClientBridge {
        private static void accept(ActionPayload payload) {
            GauntletClientController.handleAction(payload);
        }
    }
}
