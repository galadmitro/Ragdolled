package com.example.gly091020.sableragdolllib;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SableRagdollLib {
    public static final String MODID = "ragdollmod";

    public SableRagdollLib(IEventBus modEventBus) {
        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.register(new ServerEventHandler());
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            ServerboundDragPacket.TYPE,
            ServerboundDragPacket.STREAM_CODEC,
            ServerEventHandler::handleDragPacket
        );
        registrar.playToClient(
            ClientboundRagdollSyncPacket.TYPE,
            ClientboundRagdollSyncPacket.STREAM_CODEC,
            ClientEventHandler::handleSyncPacket
        );
    }

    public record ServerboundDragPacket(int entityId, int partIndex, Vec3 targetPos, boolean releasing) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ServerboundDragPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "drag_packet"));
        public static final StreamCodec<FriendlyByteBuf, ServerboundDragPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.entityId);
                buf.writeInt(packet.partIndex);
                buf.writeDouble(packet.targetPos.x);
                buf.writeDouble(packet.targetPos.y);
                buf.writeDouble(packet.targetPos.z);
                buf.writeBoolean(packet.releasing);
            },
            buf -> new ServerboundDragPacket(
                buf.readInt(),
                buf.readInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readBoolean()
            )
        );

        @Override
        public CustomPacketPayload.Type<ServerboundDragPacket> type() {
            return TYPE;
        }
    }

    public record ClientboundRagdollSyncPacket(int entityId, List<RagdollPart> parts) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ClientboundRagdollSyncPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "sync_packet"));
        public static final StreamCodec<FriendlyByteBuf, ClientboundRagdollSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.entityId);
                buf.writeInt(packet.parts.size());
                for (RagdollPart part : packet.parts) {
                    buf.writeUtf(part.name);
                    buf.writeDouble(part.position.x);
                    buf.writeDouble(part.position.y);
                    buf.writeDouble(part.position.z);
                    buf.writeDouble(part.velocity.x);
                    buf.writeDouble(part.velocity.y);
                    buf.writeDouble(part.velocity.z);
                }
            },
            buf -> {
                int entityId = buf.readInt();
                int count = buf.readInt();
                List<RagdollPart> parts = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String name = buf.readUtf();
                    Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                    Vec3 vel = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                    parts.add(new RagdollPart(name, pos, vel, new AABB(-0.25, -0.25, -0.25, 0.25, 0.25, 0.25)));
                }
                return new ClientboundRagdollSyncPacket(entityId, parts);
            }
        );

        @Override
        public CustomPacketPayload.Type<ClientboundRagdollSyncPacket> type() {
            return TYPE;
        }
    }

    public static class RagdollPart {
        public String name;
        public Vec3 position;
        public Vec3 velocity;
        public AABB localBounds;

        public RagdollPart(String name, Vec3 position, Vec3 velocity, AABB localBounds) {
            this.name = name;
            this.position = position;
            this.velocity = velocity;
            this.localBounds = localBounds;
        }

        public AABB getBoundingBox() {
            return localBounds.move(position);
        }

        public void stepPhysics(Level level) {
            velocity = velocity.add(0, -0.08, 0).scale(0.98);
            Vec3 nextPos = position.add(velocity);
            AABB futureBox = localBounds.move(nextPos);
            if (!level.getBlockCollisions(null, futureBox).iterator().hasNext()) {
                position = nextPos;
            } else {
                velocity = new Vec3(velocity.x * 0.5, 0, velocity.z * 0.5);
            }
        }
    }

    public static class RagdollJoint {
        public int parentIndex;
        public int childIndex;
        public double maxLength;

        public RagdollJoint(int parentIndex, int childIndex, double maxLength) {
            this.parentIndex = parentIndex;
            this.childIndex = childIndex;
            this.maxLength = maxLength;
        }

        public void solveConstraint(List<RagdollPart> parts) {
            if (parentIndex < 0 || parentIndex >= parts.size() || childIndex < 0 || childIndex >= parts.size()) return;
            RagdollPart parent = parts.get(parentIndex);
            RagdollPart child = parts.get(childIndex);

            Vec3 delta = child.position.subtract(parent.position);
            double currentDist = delta.length();
            if (currentDist > maxLength && currentDist > 0.0001) {
                Vec3 correction = delta.normalize().scale((currentDist - maxLength) * 0.5);
                parent.position = parent.position.add(correction);
                child.position = child.position.subtract(correction);
            }
        }
    }

    public static class RagdollInstance {
        public final int entityId;
        public final List<RagdollPart> parts = new ArrayList<>();
        public final List<RagdollJoint> joints = new ArrayList<>();

        public RagdollInstance(int entityId, Vec3 origin) {
            this.entityId = entityId;
            parts.add(new RagdollPart("head", origin.add(0, 1.5, 0), Vec3.ZERO, new AABB(-0.2, -0.2, -0.2, 0.2, 0.2, 0.2)));
            parts.add(new RagdollPart("torso", origin.add(0, 1.0, 0), Vec3.ZERO, new AABB(-0.25, -0.4, -0.15, 0.25, 0.4, 0.15)));
            parts.add(new RagdollPart("left_arm", origin.add(-0.4, 1.0, 0), Vec3.ZERO, new AABB(-0.1, -0.3, -0.1, 0.1, 0.3, 0.1)));
            parts.add(new RagdollPart("right_arm", origin.add(0.4, 1.0, 0), Vec3.ZERO, new AABB(-0.1, -0.3, -0.1, 0.1, 0.3, 0.1)));
            parts.add(new RagdollPart("left_leg", origin.add(-0.2, 0.3, 0), Vec3.ZERO, new AABB(-0.1, -0.4, -0.1, 0.1, 0.4, 0.1)));
            parts.add(new RagdollPart("right_leg", origin.add(0.2, 0.3, 0), Vec3.ZERO, new AABB(-0.1, -0.4, -0.1, 0.1, 0.4, 0.1)));

            joints.add(new RagdollJoint(1, 0, 0.5));
            joints.add(new RagdollJoint(1, 2, 0.4));
            joints.add(new RagdollJoint(1, 3, 0.4));
            joints.add(new RagdollJoint(1, 4, 0.7));
            joints.add(new RagdollJoint(1, 5, 0.7));
        }

        public void tick(ServerLevel level) {
            for (RagdollPart part : parts) {
                part.stepPhysics(level);
            }
            for (int i = 0; i < 4; i++) {
                for (RagdollJoint joint : joints) {
                    joint.solveConstraint(parts);
                }
            }
        }
    }

    public static class DragSession {
        public final UUID playerId;
        public final int entityId;
        public final int partIndex;
        public Vec3 targetPos;

        public DragSession(UUID playerId, int entityId, int partIndex, Vec3 targetPos) {
            this.playerId = playerId;
            this.entityId = entityId;
            this.partIndex = partIndex;
            this.targetPos = targetPos;
        }

        public void applyForce(RagdollInstance instance) {
            if (partIndex >= 0 && partIndex < instance.parts.size()) {
                RagdollPart part = instance.parts.get(partIndex);
                Vec3 force = targetPos.subtract(part.position).scale(0.3);
                part.velocity = part.velocity.add(force);
            }
        }
    }

    public static class RagdollManager {
        public static final Map<Integer, RagdollInstance> ACTIVE_RAGDOLLS = new ConcurrentHashMap<>();
        public static final Map<UUID, DragSession> ACTIVE_DRAGS = new ConcurrentHashMap<>();

        public static void spawnRagdoll(Entity entity) {
            RagdollInstance ragdoll = new RagdollInstance(entity.getId(), entity.position());
            ACTIVE_RAGDOLLS.put(entity.getId(), ragdoll);
        }

        public static void tick(ServerLevel level) {
            for (DragSession session : ACTIVE_DRAGS.values()) {
                RagdollInstance instance = ACTIVE_RAGDOLLS.get(session.entityId);
                if (instance != null) {
                    session.applyForce(instance);
                }
            }
            for (RagdollInstance ragdoll : ACTIVE_RAGDOLLS.values()) {
                ragdoll.tick(level);
                PacketDistributor.sendToPlayersInDimension(level, new ClientboundRagdollSyncPacket(ragdoll.entityId, ragdoll.parts));
            }
        }
    }

    public static class ServerEventHandler {
        @SubscribeEvent
        public void onLivingDeath(LivingDeathEvent event) {
            if (!event.getEntity().level().isClientSide()) {
                RagdollManager.spawnRagdoll(event.getEntity());
            }
        }

        @SubscribeEvent
        public void onServerTick(ServerTickEvent.Post event) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                RagdollManager.tick(level);
            }
        }

        public static void handleDragPacket(ServerboundDragPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (packet.releasing()) {
                        RagdollManager.ACTIVE_DRAGS.remove(player.getUUID());
                    } else {
                        RagdollManager.ACTIVE_DRAGS.put(
                            player.getUUID(),
                            new DragSession(player.getUUID(), packet.entityId(), packet.partIndex(), packet.targetPos())
                        );
                    }
                }
            });
        }
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientEventHandler {
        public static final Map<Integer, RagdollInstance> CLIENT_RAGDOLLS = new ConcurrentHashMap<>();

        public static void handleSyncPacket(ClientboundRagdollSyncPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
            context.enqueueWork(() -> {
                RagdollInstance ragdoll = CLIENT_RAGDOLLS.computeIfAbsent(packet.entityId(), id -> new RagdollInstance(id, Vec3.ZERO));
                for (int i = 0; i < packet.parts().size() && i < ragdoll.parts.size(); i++) {
                    RagdollPart serverPart = packet.parts().get(i);
                    RagdollPart clientPart = ragdoll.parts.get(i);
                    clientPart.position = serverPart.position;
                    clientPart.velocity = serverPart.velocity;
                }
            });
        }

        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;

                for (DragSession drag : RagdollManager.ACTIVE_DRAGS.values()) {
                    if (drag.playerId.equals(mc.player.getUUID())) {
                        RagdollInstance ragdoll = CLIENT_RAGDOLLS.get(drag.entityId);
                        if (ragdoll != null && drag.partIndex < ragdoll.parts.size()) {
                            Vec3 start = ragdoll.parts.get(drag.partIndex).position;
                            Vec3 end = drag.targetPos;
                            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
                        }
                    }
                }
            }
        }
    }
}