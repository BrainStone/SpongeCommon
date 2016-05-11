/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.entity;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketDestroyEntities;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.network.play.server.SPacketSpawnPainting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.type.Profession;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.interfaces.IMixinEntityPlayerMP;
import org.spongepowered.common.interfaces.entity.IMixinEntity;
import org.spongepowered.common.interfaces.world.IMixinWorldServer;
import org.spongepowered.common.registry.type.entity.ProfessionRegistryModule;
import org.spongepowered.common.world.WorldManager;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public final class EntityUtil {

    /**
     * This is a dummy entity that can be used for various mixins where a null
     * check on an entity is required. Note that this entity SHOULD NEVER BE
     * USED FOR OTHER PURPOSES AT ALL.
     */
    public static final Entity USELESS_ENTITY_FOR_MIXINS = new EntityDummy(null);

    public static final BlockPos HANGING_OFFSET_EAST = new BlockPos(1, 1, 0);
    public static final BlockPos HANGING_OFFSET_WEST = new BlockPos(-1, 1, 0);
    public static final BlockPos HANGING_OFFSET_NORTH = new BlockPos(0, 1, -1);
    public static final BlockPos HANGING_OFFSET_SOUTH = new BlockPos(0, 1, 1);

    private EntityUtil() {
    }

    static final Predicate<Entity> TRACEABLE = Predicates.and(EntitySelectors.NOT_SPECTATING, new Predicate<Entity>() {
        @Override
        public boolean apply(Entity entity) {
            return entity != null && entity.canBeCollidedWith();
        }
    });

    static final class EntityTrace {

        Entity entity;
        Vec3d location;
        double distance;

        EntityTrace(double entityDistance) {
            this.distance = entityDistance;
        }

        RayTraceResult asRayTraceResult() {
            return new RayTraceResult(this.entity, this.location);
        }
    }

    public static RayTraceResult rayTraceFromEntity(Entity source, double traceDistance, float partialTicks, boolean includeEntities) {
        RayTraceResult blockRay = EntityUtil.rayTraceFromEntity(source, traceDistance, partialTicks);

        if (!includeEntities) {
            return blockRay;
        }

        Vec3d traceStart = EntityUtil.getPositionEyes(source, partialTicks);
        double blockDistance = (blockRay != null) ? blockRay.hitVec.distanceTo(traceStart) : traceDistance;
        EntityTrace entityRay = EntityUtil.rayTraceEntities(source, traceDistance, partialTicks, blockDistance, traceStart);

        if (entityRay.entity != null && (entityRay.distance < blockDistance || blockRay == null)) {
            return entityRay.asRayTraceResult();
        }

        return blockRay;
    }

    private static EntityTrace rayTraceEntities(Entity source, double traceDistance, float partialTicks, double blockDistance, Vec3d traceStart) {
        EntityTrace trace = new EntityTrace(blockDistance);

        Vec3d lookDir = source.getLook(partialTicks).scale(traceDistance);
        Vec3d traceEnd = traceStart.add(lookDir);

        for (final Entity entity : EntityUtil.getTraceEntities(source, traceDistance, lookDir, EntityUtil.TRACEABLE)) {
            AxisAlignedBB entityBB = entity.getEntityBoundingBox().expandXyz(entity.getCollisionBorderSize());
            RayTraceResult entityRay = entityBB.calculateIntercept(traceStart, traceEnd);

            if (entityBB.isVecInside(traceStart)) {
                if (trace.distance >= 0.0D) {
                    trace.entity = entity;
                    trace.location = entityRay == null ? traceStart : entityRay.hitVec;
                    trace.distance = 0.0D;
                }
                continue;
            }

            if (entityRay == null) {
                continue;
            }

            double distanceToEntity = traceStart.distanceTo(entityRay.hitVec);

            if (distanceToEntity < trace.distance || trace.distance == 0.0D) {
                if (entity.getLowestRidingEntity() == source.getLowestRidingEntity()) {
                    if (trace.distance == 0.0D) {
                        trace.entity = entity;
                        trace.location = entityRay.hitVec;
                    }
                } else {
                    trace.entity = entity;
                    trace.location = entityRay.hitVec;
                    trace.distance = distanceToEntity;
                }
            }
        }

        return trace;
    }

    private static List<Entity> getTraceEntities(Entity source, double traceDistance, Vec3d dir, Predicate<Entity> filter) {
        AxisAlignedBB boundingBox = source.getEntityBoundingBox();
        AxisAlignedBB traceBox = boundingBox.addCoord(dir.xCoord, dir.yCoord, dir.zCoord);
        List<Entity> entities = source.worldObj.getEntitiesInAABBexcluding(source, traceBox.expand(1.0F, 1.0F, 1.0F), filter);
        return entities;
    }

    public static RayTraceResult rayTraceFromEntity(Entity source, double traceDistance, float partialTicks) {
        Vec3d traceStart = EntityUtil.getPositionEyes(source, partialTicks);
        Vec3d lookDir = source.getLook(partialTicks).scale(traceDistance);
        Vec3d traceEnd = traceStart.add(lookDir);
        return source.worldObj.rayTraceBlocks(traceStart, traceEnd, false, false, true);
    }

    public static Vec3d getPositionEyes(Entity entity, float partialTicks)
    {
        if (partialTicks == 1.0F)
        {
            return new Vec3d(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
        }

        double interpX = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
        double interpY = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks + entity.getEyeHeight();
        double interpZ = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;
        return new Vec3d(interpX, interpY, interpZ);
    }
    @SuppressWarnings("unchecked")
    public static boolean refreshPainting(EntityPainting painting, EntityPainting.EnumArt art) {
        final EntityTracker paintingTracker = ((WorldServer) painting.worldObj).getEntityTracker();
        EntityTrackerEntry paintingEntry = paintingTracker.trackedEntityHashTable.lookup(painting.getEntityId());
        List<EntityPlayerMP> playerMPs = new ArrayList<>();
        for (EntityPlayerMP player : paintingEntry.trackingPlayers) {
            SPacketDestroyEntities packet = new SPacketDestroyEntities(painting.getEntityId());
            player.playerNetServerHandler.sendPacket(packet);
            playerMPs.add(player);
        }
        painting.art = art;
        painting.updateFacingWithBoundingBox(painting.facingDirection);
        for (EntityPlayerMP playerMP : playerMPs) {
            SpongeImpl.getGame().getScheduler().createTaskBuilder()
                    .delayTicks(SpongeImpl.getGlobalConfig().getConfig().getEntity().getPaintingRespawnDelaly())
                    .execute(() -> {
                        final SPacketSpawnPainting packet = new SPacketSpawnPainting(painting);
                        playerMP.playerNetServerHandler.sendPacket(packet);
                    })
                    .submit(SpongeImpl.getPlugin());
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean toggleInvisibility(Entity entity, boolean vanish) {
        entity.setInvisible(vanish);
        ((IMixinEntity) entity).setVanished(vanish);
        return true;
    }

    public static Profession validateProfession(int professionId) {
        List<Profession> professions = (List<Profession>) ProfessionRegistryModule.getInstance().getAll();
        for (Profession profession : professions) {
            if (profession instanceof SpongeProfession) {
                if (professionId == ((SpongeProfession) profession).type) {
                    return profession;
                }
            }
        }
        throw new IllegalStateException("Invalid Villager profession id is present! Found: " + professionId
                                        + " when the expected contain: " + professions);
    }

    public static List<EntityHanging> findHangingEntities(World worldIn, BlockPos pos) {
        return worldIn.getEntitiesWithinAABB(EntityHanging.class, new AxisAlignedBB(pos, pos).expand(1.1D, 1.1D, 1.1D),
                entityIn -> {
                    if (entityIn == null) {
                        return false;
                    }

                    BlockPos entityPos = entityIn.getPosition();
                    // Hanging Neighbor Entity
                    if (entityPos.equals(pos.add(0, 1, 0))) {
                        return true;
                    }

                    // Check around source block
                    EnumFacing entityFacing = entityIn.getHorizontalFacing();

                    if (entityFacing == EnumFacing.NORTH) {
                        return entityPos.equals(pos.add(HANGING_OFFSET_NORTH));
                    } else if (entityFacing == EnumFacing.SOUTH) {
                        return entityIn.getPosition().equals(pos.add(HANGING_OFFSET_SOUTH));
                    } else if (entityFacing == EnumFacing.WEST) {
                        return entityIn.getPosition().equals(pos.add(HANGING_OFFSET_WEST));
                    } else if (entityFacing == EnumFacing.EAST) {
                        return entityIn.getPosition().equals(pos.add(HANGING_OFFSET_EAST));
                    }
                    return false;
                });
    }

    public static Entity toNative(org.spongepowered.api.entity.Entity tickingEntity) {
        if (!(tickingEntity instanceof Entity)) {
            throw new IllegalArgumentException("Not a native Entity for this implementation!");
        }
        return (Entity) tickingEntity;
    }

    @Nullable
    public static Entity toNullableNative(@Nullable org.spongepowered.api.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        if (!(entity instanceof Entity)) {
            throw new IllegalArgumentException("Not a native Entity for this implementation!");
        }
        return (Entity) entity;
    }

    public static org.spongepowered.api.entity.Entity fromNative(Entity entity) {
        return (org.spongepowered.api.entity.Entity) entity;
    }

    public static Living fromNativeToLiving(Entity entity) {
        if (!(entity instanceof Living)) {
            throw new IllegalArgumentException("Entity is incompatible with SpongeAPI Living interface: " + entity);
        }
        return (Living) entity;
    }

    public static EntityLivingBase toNative(Living entity) {
        if (!(entity instanceof EntityLivingBase)) {
            throw new IllegalArgumentException("Living entity is not compatible with this implementation: " + entity);
        }
        return (EntityLivingBase) entity;
    }

    public static EntityPlayerMP toNative(Player player) {
        if (!(player instanceof EntityPlayerMP)) {
            throw new IllegalArgumentException("Player entity is not compatible with this implementation: " + player);
        }
        return (EntityPlayerMP) player;
    }

    public static IMixinEntity toMixin(Entity entity) {
        if (!(entity instanceof IMixinEntity)) {
            throw new IllegalArgumentException("Not a mixin Entity for this implementation!");
        }
        return (IMixinEntity) entity;
    }

    public static IMixinEntity toMixin(org.spongepowered.api.entity.Entity entity) {
        if (!(entity instanceof IMixinEntity)) {
            throw new IllegalArgumentException("Not a mixin Entity for this implementation!");
        }
        return (IMixinEntity) entity;
    }

    public static org.spongepowered.api.entity.Entity fromMixin(IMixinEntity mixinEntity) {
        if (!(mixinEntity instanceof org.spongepowered.api.entity.Entity)) {
            throw new IllegalArgumentException("Not a native SpongeAPI entity!");
        }
        return (org.spongepowered.api.entity.Entity) mixinEntity;
    }

    public static boolean changeWorld(Entity entity, Transform<org.spongepowered.api.world.World> toTransform) {
        final MinecraftServer server = (MinecraftServer) Sponge.getServer();
        final WorldServer fromWorld = (WorldServer) entity.getEntityWorld();
        final WorldServer toWorld = (WorldServer) toTransform.getExtent();

        fromWorld.getEntityTracker().untrackEntity(entity);

        // First remove us from where we come from
        if (entity instanceof EntityPlayer) {
            fromWorld.getEntityTracker().removePlayerFromTrackers((EntityPlayerMP) entity);
            fromWorld.getPlayerChunkMap().removePlayer((EntityPlayerMP) entity);
            fromWorld.playerEntities.remove(entity);
            fromWorld.updateAllPlayersSleepingFlag();
        }

        if (entity.isBeingRidden())
        {
            entity.removePassengers();
        }

        if (entity.isRiding())
        {
            entity.dismountRidingEntity();
        }

        final IMixinWorldServer fromMixinWorld = (IMixinWorldServer) fromWorld;
        fromMixinWorld.onSpongeEntityRemoved(entity);

        int i = entity.chunkCoordX;
        int j = entity.chunkCoordZ;

        if (entity.addedToChunk && fromMixinWorld.isMinecraftChunkLoaded(i, j, true))
        {
            fromWorld.getChunkFromChunkCoords(i, j).removeEntity(entity);
        }

        if (!(entity instanceof EntityPlayerMP)) {
            fromWorld.loadedEntityList.remove(entity);
        }

        // At this point, we've removed the entity from the previous world. Lets now put them in the new world.
        final IMixinWorldServer toMixinWorld = (IMixinWorldServer) toWorld;
        entity.dimension = toMixinWorld.getDimensionId();
        entity.setWorld(toWorld);

        if (entity instanceof EntityPlayer) {
            final EntityPlayerMP entityPlayerMP = (EntityPlayerMP) entity;

            // Support vanilla clients going into custom dimensions
            final net.minecraft.world.DimensionType fromClientDimensionType = WorldManager.getClientDimensionType(fromWorld.provider
                    .getDimensionType());
            final net.minecraft.world.DimensionType toClientDimensionType = WorldManager.getClientDimensionType(toWorld.provider.getDimensionType
                    ());
            if (((IMixinEntityPlayerMP) entityPlayerMP).usesCustomClient()) {
                WorldManager.sendDimensionRegistration(entityPlayerMP, toClientDimensionType);
            } else {
                final int currentDim =  fromMixinWorld.getDimensionId();
                final int targetDim = toMixinWorld.getDimensionId();
                final int fromClientDimensionTypeId = fromClientDimensionType.getId();
                final int toClientDimensionTypeId = toClientDimensionType.getId();
                // Force vanilla client to refresh their chunk cache if same dimension
                if (currentDim != targetDim && fromClientDimensionTypeId == toClientDimensionTypeId) {
                    entityPlayerMP.playerNetServerHandler.sendPacket(
                            new SPacketRespawn(toClientDimensionTypeId >= 0 ? -1 : 0, toWorld.getDifficulty(), toWorld.getWorldInfo().
                                    getTerrainType(), entityPlayerMP.interactionManager.getGameType()));
                }
            }

            entityPlayerMP.playerNetServerHandler.sendPacket(new SPacketRespawn(toClientDimensionType.getId(), toWorld.getDifficulty(), toWorld.
                    getWorldInfo().getTerrainType(), entityPlayerMP.interactionManager.getGameType()));
            entityPlayerMP.setLocationAndAngles(toTransform.getLocation().getX(), toTransform.getLocation().getY(), toTransform.getLocation().getZ(),
                    (int) toTransform.getRotation().getY(), (int) toTransform.getRotation().getX());
            toWorld.getChunkProvider().provideChunk((int) toTransform.getLocation().getX() >> 4, (int) toTransform.getLocation().getZ() >> 4);
            while (!toWorld.getCollisionBoxes(entityPlayerMP, entityPlayerMP.getEntityBoundingBox()).isEmpty() && entityPlayerMP.posY < 256.0D)
            {
                entityPlayerMP.setPosition(entityPlayerMP.posX, entityPlayerMP.posY + 1.0D, entityPlayerMP.posZ);
            }
            entityPlayerMP.playerNetServerHandler.setPlayerLocation(entityPlayerMP.posX, entityPlayerMP.posY, entityPlayerMP.posZ,
                    entityPlayerMP.rotationYaw, entityPlayerMP.rotationPitch);
            entityPlayerMP.setSneaking(false);
            server.getPlayerList().updateTimeAndWeatherForPlayer(entityPlayerMP, toWorld);
            entityPlayerMP.interactionManager.setWorld(toWorld);
            entityPlayerMP.addSelfToInternalCraftingInventory();
            toWorld.getPlayerChunkMap().addPlayer(entityPlayerMP);
        } else {
            entity.setPositionAndRotation(toTransform.getLocation().getX(), toTransform.getLocation().getY(), toTransform.getLocation().getZ(), 0, 0);
        }

        toWorld.spawnEntityInWorld(entity);

        fromWorld.resetUpdateEntityTick();
        toWorld.resetUpdateEntityTick();
        return true;
    }
}