package com.example.ragdollmod.network;

import com.example.ragdollmod.PlayerRagdollMod;
import com.gly091020.sableragdolllib.api.RagdollApi;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleRagdollPayload() implements CustomPacketPayload {
    public static final Type<ToggleRagdollPayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(PlayerRagdollMod.MODID, "toggle_ragdoll"));

    public static final StreamCodec<FriendlyByteBuf, ToggleRagdollPayload> STREAM_CODEC = 
        StreamCodec.unit(new ToggleRagdollPayload());

    @Override
    public Type<ToggleRagdollPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleRagdollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (RagdollApi.isRagdolled(player)) {
                    RagdollApi.stopRagdoll(player);
                } else {
                    RagdollApi.startRagdoll(player);
                }
            }
        });
    }
}