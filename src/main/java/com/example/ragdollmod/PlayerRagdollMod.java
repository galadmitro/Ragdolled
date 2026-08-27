package com.example.ragdollmod;

import com.example.ragdollmod.network.ToggleRagdollPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(PlayerRagdollMod.MODID)
public class PlayerRagdollMod {
    public static final String MODID = "ragdollmod";

    public PlayerRagdollMod(IEventBus modEventBus) {
        modEventBus.addListener(this::registerNetworking);
    }

    private void registerNetworking(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(
            ToggleRagdollPayload.TYPE,
            ToggleRagdollPayload.STREAM_CODEC,
            ToggleRagdollPayload::handle
        );
    }
}