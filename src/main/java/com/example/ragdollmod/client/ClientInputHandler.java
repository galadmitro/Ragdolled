package com.example.ragdollmod.client;

import com.example.ragdollmod.PlayerRagdollMod;
import com.example.ragdollmod.network.ToggleRagdollPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PlayerRagdollMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientInputHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (ModKeybindings.RAGDOLL_KEY.consumeClick()) {
            PacketDistributor.sendToServer(new ToggleRagdollPayload());
        }
    }
}