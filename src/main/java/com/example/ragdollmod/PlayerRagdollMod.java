package com.example.ragdollmod;

import com.example.gly091020.sableragdolllib.SableRagdollLib;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(PlayerRagdollMod.MODID)
public class PlayerRagdollMod {
    public static final String MODID = "ragdollmod";

    public PlayerRagdollMod(IEventBus modEventBus) {
        new SableRagdollLib(modEventBus);
    }
}