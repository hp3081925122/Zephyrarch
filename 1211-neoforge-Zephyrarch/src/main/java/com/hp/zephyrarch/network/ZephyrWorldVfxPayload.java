package com.hp.zephyrarch.network;

import com.hp.zephyrarch.Zephyrarch;
import com.hp.zephyrarch.client.ZephyrWorldVfxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ZephyrWorldVfxPayload(
        int kind,
        double x,
        double y,
        double z,
        float vx,
        float vy,
        float vz,
        float scale,
        int duration
) implements CustomPacketPayload {

    public static final int KIND_STORM_EYE_MARK = 1;
    public static final int KIND_DOWNDRAFT_COLUMN = 2;
    public static final int KIND_CHARGE_BURST = 3;
    public static final int KIND_TWISTER_DISSIPATE = 4;
    public static final int KIND_WIND_DASH_TRAIL = 5;

    public static final Type<ZephyrWorldVfxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zephyrarch.MOD_ID, "world_vfx")
    );

    public static final StreamCodec<FriendlyByteBuf, ZephyrWorldVfxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.vx);
                buf.writeFloat(p.vy);
                buf.writeFloat(p.vz);
                buf.writeFloat(p.scale);
                buf.writeVarInt(p.duration);
            },
            buf -> new ZephyrWorldVfxPayload(
                    buf.readVarInt(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToClient(TYPE, STREAM_CODEC, ZephyrWorldVfxPayload::handleClient);
        GauntletNetwork.register(registrar);
    }

    private static void handleClient(ZephyrWorldVfxPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() -> ClientBridge.accept(payload));
        }
    }

    public static void sendPoint(Entity anchor, int kind, Vec3 pos, float scale, int duration) {
        if (!anchor.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntity(anchor,
                    new ZephyrWorldVfxPayload(kind, pos.x, pos.y, pos.z, 0.0F, 0.0F, 0.0F, scale, duration));
        }
    }

    public static void sendDirected(Entity anchor, int kind, Vec3 pos, Vec3 vec, float scale, int duration) {
        if (!anchor.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntity(anchor,
                    new ZephyrWorldVfxPayload(kind, pos.x, pos.y, pos.z, (float) vec.x, (float) vec.y, (float) vec.z, scale, duration));
        }
    }

    private static final class ClientBridge {
        private static void accept(ZephyrWorldVfxPayload payload) {
            ZephyrWorldVfxClient.spawn(payload);
        }
    }
}
