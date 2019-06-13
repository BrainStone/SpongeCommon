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
package org.spongepowered.common.mixin.core.entity;

import static com.google.common.base.Preconditions.checkNotNull;

import co.aikar.timings.Timing;
import com.flowpowered.math.vector.Vector3d;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.play.server.SPacketDestroyEntities;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.dismount.DismountType;
import org.spongepowered.api.event.cause.entity.dismount.DismountTypes;
import org.spongepowered.api.event.entity.IgniteEntityEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.bridge.TimingHolder;
import org.spongepowered.common.bridge.entity.GrieferBridge;
import org.spongepowered.common.bridge.world.ChunkBridge;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.data.nbt.CustomDataNbtUtil;
import org.spongepowered.common.data.util.NbtDataUtil;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.entity.SpongeEntityType;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.damage.DamageEventHandler;
import org.spongepowered.common.event.damage.MinecraftBlockDamageSource;
import org.spongepowered.common.bridge.TrackableBridge;
import org.spongepowered.common.bridge.block.BlockBridge;
import org.spongepowered.common.bridge.entity.EntityBridge;
import org.spongepowered.common.interfaces.network.IMixinNetHandlerPlayServer;
import org.spongepowered.common.interfaces.world.ServerWorldBridge;
import org.spongepowered.common.registry.type.entity.EntityTypeRegistryModule;
import org.spongepowered.common.text.SpongeTexts;
import org.spongepowered.common.util.SpongeHooks;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

@Mixin(Entity.class)
@Implements(
    {@Interface(iface = org.spongepowered.api.entity.Entity.class, prefix = "entityApi$"),
     @Interface(iface = TimingHolder.class, prefix = "timing$")}
)
public abstract class MixinEntity implements EntityBridge, TrackableBridge {

    // @formatter:off
    protected final SpongeEntityType entityType = EntityTypeRegistryModule.getInstance().getForClass(((Entity) (Object) this).getClass());
    private boolean teleporting;
    private WeakReference<ChunkBridge> activeChunk = new WeakReference<>(null);
    private float origWidth;
    private float origHeight;
    private boolean isConstructing = true;
    @Nullable private Text displayName;
    @Nullable private BlockState currentCollidingBlock;
    @Nullable private BlockPos lastCollidedBlockPos;
    @Nullable private Entity teleportVehicle;
    private boolean trackedInWorld = false;
    private boolean collision = false;
    private boolean untargetable = false;
    private boolean isVanished = false;
    private boolean pendingVisibilityUpdate = false;
    private int visibilityTicks = 0;

    @Shadow @Nullable public Entity ridingEntity;
    @Shadow @Final private List<Entity> riddenByEntities;
    @Shadow public net.minecraft.world.World world;
    @Shadow public double posX;
    @Shadow public double posY;
    @Shadow public double posZ;
    @Shadow public double motionX;
    @Shadow public double motionY;
    @Shadow public double motionZ;
    @Shadow public float rotationYaw;
    @Shadow public float rotationPitch;
    @Shadow public boolean velocityChanged;
    @Shadow public boolean onGround;
    @Shadow public boolean isDead;
    @Shadow public float width;
    @Shadow public float height;
    @Shadow public float prevDistanceWalkedModified;
    @Shadow public float distanceWalkedModified;
    @Shadow public float fallDistance;
    @Shadow protected Random rand;
    @Shadow public int ticksExisted;
    @Shadow public int fire;
    @Shadow public int hurtResistantTime;
    @Shadow protected EntityDataManager dataManager;
    @Shadow public int dimension;
    @Shadow private boolean invulnerable;

    @Shadow public abstract void setPosition(double x, double y, double z);
    @Shadow public abstract void setDead();
    @Shadow public abstract int getAir();
    @Shadow public abstract void setAir(int air);
    @Shadow public abstract float getEyeHeight();
    @Shadow public abstract void setCustomNameTag(String name);
    @Shadow public abstract UUID getUniqueID();
    @Shadow public abstract AxisAlignedBB getEntityBoundingBox();
    @Shadow public abstract void setFire(int seconds);
    @Shadow public abstract NBTTagCompound writeToNBT(NBTTagCompound compound);
    @Shadow public abstract boolean attackEntityFrom(DamageSource source, float amount);
    @Shadow public abstract int getEntityId();
    @Shadow public abstract boolean isBeingRidden();
    @Shadow public abstract SoundCategory getSoundCategory();
    @Shadow public abstract List<Entity> shadow$getPassengers();
    @Shadow public abstract Entity getRidingEntity();
    @Shadow public abstract void setItemStackToSlot(EntityEquipmentSlot slotIn, ItemStack stack);
    @Shadow public abstract void playSound(SoundEvent soundIn, float volume, float pitch);
    @Shadow public abstract boolean isEntityInvulnerable(DamageSource source);
    @Shadow public abstract boolean isSprinting();
    @Shadow public abstract boolean shadow$isInWater();
    @Shadow public abstract boolean isRiding();
    @Shadow public abstract boolean isOnSameTeam(Entity entityIn);
    @Shadow public abstract double getDistanceSq(Entity entityIn);
    @Shadow public abstract void setLocationAndAngles(double x, double y, double z, float yaw, float pitch);
    @Shadow public abstract void setPositionAndUpdate(double x, double y, double z);
    @Shadow protected abstract void removePassenger(Entity passenger);
    @Shadow protected abstract void shadow$setRotation(float yaw, float pitch);
    @Shadow protected abstract void setSize(float width, float height);
    @Shadow protected abstract void applyEnchantments(EntityLivingBase entityLivingBaseIn, Entity entityIn);
    @Shadow public abstract void extinguish();
    @Shadow protected abstract void setFlag(int flag, boolean set);

    // @formatter:on

    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;dimension:I", opcode = Opcodes.PUTFIELD))
    private void impl$UpdateDimension(final Entity self, final int dimensionId, final net.minecraft.world.World worldIn) {
        if (worldIn instanceof ServerWorldBridge) {
            self.dimension = ((ServerWorldBridge) worldIn).getDimensionId();
        } else {
            self.dimension = dimensionId;
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onSpongeConstruction(final net.minecraft.world.World worldIn, final CallbackInfo ci) {
        if (this.entityType.isKnown()) {
            this.refreshTrackerStates();
            if (this.entityType.getEnumCreatureType() == null) {
                for (final EnumCreatureType type : EnumCreatureType.values()) {
                    if (SpongeImplHooks.isCreatureOfType((Entity) (Object) this, type)) {
                        this.entityType.setEnumCreatureType(type);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public boolean isInConstructPhase() {
        return this.isConstructing;
    }

    @Override
    public void firePostConstructEvents() {
        this.isConstructing = false;
    }

    @Override
    public boolean isTrackedInWorld() {
        return this.trackedInWorld;
    }

    @Override
    public void setTrackedInWorld(final boolean tracked) {
        this.trackedInWorld = tracked;
    }

    @Inject(method = "startRiding(Lnet/minecraft/entity/Entity;Z)Z", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;ridingEntity:Lnet/minecraft/entity/Entity;", ordinal = 0),
            cancellable = true)
    private void onStartRiding(final Entity vehicle, final boolean force, final CallbackInfoReturnable<Boolean> ci) {
        if (!this.world.isRemote && (ShouldFire.RIDE_ENTITY_EVENT_MOUNT || ShouldFire.RIDE_ENTITY_EVENT)) {
            Sponge.getCauseStackManager().pushCause(this);
            if (SpongeImpl.postEvent(SpongeEventFactory.createRideEntityEventMount(Sponge.getCauseStackManager().getCurrentCause(), (org.spongepowered.api.entity.Entity) vehicle))) {
                ci.cancel();
            }
            Sponge.getCauseStackManager().popCause();
        }
    }

    /**
     * @author rexbut - December 16th, 2016
     *
     * @reason - adjusted to support {@link DismountTypes}
     */
    @Overwrite
    public void dismountRidingEntity() {
        if (this.ridingEntity != null) {
            if (this.getRidingEntity().isDead) {
                this.spongeImpl$dismountRidingEntity(DismountTypes.DEATH);
            } else {
                this.spongeImpl$dismountRidingEntity(DismountTypes.PLAYER);
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    private boolean spongeImpl$dismountRidingEntity(final DismountType type) {
        if (!this.world.isRemote && (ShouldFire.RIDE_ENTITY_EVENT_DISMOUNT || ShouldFire.RIDE_ENTITY_EVENT)) {
            try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                frame.pushCause(this);
                frame.addContext(EventContextKeys.DISMOUNT_TYPE, type);
                if (SpongeImpl.postEvent(SpongeEventFactory.
                    createRideEntityEventDismount(frame.getCurrentCause(), type, (org.spongepowered.api.entity.Entity) this.getRidingEntity()))) {
                    return false;
                }
            }
        }

        if (this.ridingEntity != null) {
            final MixinEntity entity = (MixinEntity) (Object) this.ridingEntity;
            this.ridingEntity = null;
            entity.removePassenger((Entity) (Object) this);
        }
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public boolean removePassengers(final DismountType type) {
        boolean dismount = false;
        for (int i = this.riddenByEntities.size() - 1; i >= 0; --i) {
            dismount = ((MixinEntity) (Object) this.riddenByEntities.get(i)).spongeImpl$dismountRidingEntity(type) || dismount;
        }
        return dismount;
    }

    @Inject(method = "setSize", at = @At("RETURN"))
    private void spongeImpl$onSpongeSetSize(final float width, final float height, final CallbackInfo ci) {
        if (this.origWidth == 0 || this.origHeight == 0) {
            this.origWidth = this.width;
            this.origHeight = this.height;
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void spongeImpl$onSpongeMoveEntity(final MoverType type, final double x, final double y, final double z, final CallbackInfo ci) {
        if (!this.world.isRemote && !SpongeHooks.checkEntitySpeed(((Entity) (Object) this), x, y, z)) {
            ci.cancel();
        }
    }

    @Redirect(method = "setOnFireFromLava",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z"
        )
    )
    private boolean onSpongeRedirectForBlockDamageSource(final Entity entity, final DamageSource source, final float damage) {
        if (this.world.isRemote) { // Short circuit
            return entity.attackEntityFrom(source, damage);
        }
        try {
            final AxisAlignedBB bb = this.getEntityBoundingBox().grow(-0.10000000149011612D, -0.4000000059604645D, -0.10000000149011612D);
            final Location<World> location = DamageEventHandler.findFirstMatchingBlock((Entity) (Object) this, bb, block ->
                block.getMaterial() == Material.LAVA);
            DamageSource.LAVA = new MinecraftBlockDamageSource("lava", location).setFireDamage();
            return entity.attackEntityFrom(DamageSource.LAVA, damage);
        } finally {
            // Since "source" is already the DamageSource.LAVA object, we can simply re-use it here.
            DamageSource.LAVA = source;
        }

    }

    @Redirect(method = "dealFireDamage",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z"
        )
    )
    private boolean onSpongeRedirectForFireDamage(final Entity entity, final DamageSource source, final float damage) {
        if (this.world.isRemote) { // Short Circuit
            return entity.attackEntityFrom(source, damage);
        }
        try {
            final AxisAlignedBB bb = this.getEntityBoundingBox().grow(-0.001D, -0.001D, -0.001D);
            final Location<World> location = DamageEventHandler.findFirstMatchingBlock((Entity) (Object) this, bb, block ->
                block.getBlock() == Blocks.FIRE || block.getBlock() == Blocks.FLOWING_LAVA || block.getBlock() == Blocks.LAVA);
            DamageSource.IN_FIRE = new MinecraftBlockDamageSource("inFire", location).setFireDamage();
            return entity.attackEntityFrom(DamageSource.IN_FIRE, damage);
        } finally {
            // Since "source" is already the DamageSource.IN_FIRE object, we can re-use it to re-assign.
            DamageSource.IN_FIRE = source;
        }

    }

    public void supplyVanillaManipulators(final List<DataManipulator<?, ?>> manipulators) {

    }

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "setPosition", at = @At("HEAD"))
    private void onSetPosition(final double x, final double y, final double z, final CallbackInfo ci) {
        if ((Entity) (Object) this instanceof EntityPlayerMP) {
            final EntityPlayerMP player = (EntityPlayerMP) (Object) this;
            if (player.connection != null) {
                ((IMixinNetHandlerPlayServer) player.connection).captureCurrentPlayerPosition();
            }
        }
    }

    public Vector3d getPosition() {
        return new Vector3d(this.posX, this.posY, this.posZ);
    }



    // always use these methods internally when setting locations from a transform or location
    // to avoid firing a DisplaceEntityEvent.Teleport
    @SuppressWarnings("ConstantConditions")
    @Override
    public void setLocationAndAngles(final Location<World> location) {
        if ((Entity) (Object) this instanceof EntityPlayerMP) {
            ((EntityPlayerMP) (Object) this).connection.setPlayerLocation(location.getX(), location.getY(), location.getZ(), this.rotationYaw, this.rotationPitch);
        } else {
            this.setPosition(location.getX(), location.getY(), location.getZ());
        }
        if (this.world != location.getExtent()) {
            this.world = (net.minecraft.world.World) location.getExtent();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void setLocationAndAngles(final Transform<World> transform) {
        final Vector3d position = transform.getPosition();
        EntityPlayerMP player = null;
        if ((Entity) (Object) this instanceof EntityPlayerMP) {
            player = (EntityPlayerMP) (Object) this;
        }
        if (player != null && player.connection != null) {
            player.connection.setPlayerLocation(position.getX(), position.getY(), position.getZ(), (float) transform.getYaw(), (float) transform.getPitch());
        } else {
            this.setLocationAndAngles(position.getX(), position.getY(), position.getZ(), (float) transform.getYaw(), (float) transform.getPitch());
        }
        if (this.world != transform.getExtent()) {
            this.world = (net.minecraft.world.World) transform.getExtent();
        }
    }


    @SuppressWarnings("ConstantConditions")
    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void spongeOnUpdate(final CallbackInfo callbackInfo) {
        if (this.pendingVisibilityUpdate && !this.world.isRemote) {
            final EntityTracker entityTracker = ((WorldServer) this.world).getEntityTracker();
            final EntityTrackerEntry lookup = entityTracker.trackedEntityHashTable.lookup(this.getEntityId());
            if (this.visibilityTicks % 4 == 0) {
                if (this.isVanished) {
                    for (final EntityPlayerMP entityPlayerMP : lookup.trackingPlayers) {
                        entityPlayerMP.connection.sendPacket(new SPacketDestroyEntities(this.getEntityId()));
                        if ((Entity) (Object) this instanceof EntityPlayerMP) {
                            entityPlayerMP.connection.sendPacket(
                                    new SPacketPlayerListItem(SPacketPlayerListItem.Action.REMOVE_PLAYER, (EntityPlayerMP) (Object) this));
                        }
                    }
                } else {
                    this.visibilityTicks = 1;
                    this.pendingVisibilityUpdate = false;
                    for (final EntityPlayerMP entityPlayerMP : SpongeImpl.getServer().getPlayerList().getPlayers()) {
                        if ((Entity) (Object) this == entityPlayerMP) {
                            continue;
                        }
                        if ((Entity) (Object) this instanceof EntityPlayerMP) {
                            final Packet<?> packet = new SPacketPlayerListItem(SPacketPlayerListItem.Action.ADD_PLAYER, (EntityPlayerMP) (Object) this);
                            entityPlayerMP.connection.sendPacket(packet);
                        }
                        final Packet<?> newPacket = lookup.createSpawnPacket(); // creates the spawn packet for us
                        entityPlayerMP.connection.sendPacket(newPacket);
                    }
                }
            }
            if (this.visibilityTicks > 0) {
                this.visibilityTicks--;
            } else {
                this.pendingVisibilityUpdate = false;
            }
        }
    }


    @Override
    public boolean isTeleporting() {
        return this.teleporting;
    }

    @Override
    public Entity getTeleportVehicle() {
        return this.teleportVehicle;
    }

    @Override
    public void setIsTeleporting(final boolean teleporting) {
        this.teleporting = teleporting;
    }

    @Override
    public void setTeleportVehicle(final Entity vehicle) {
        this.teleportVehicle = vehicle;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Intrinsic
    public List<org.spongepowered.api.entity.Entity> entity$getPassengers() {
        return (List) shadow$getPassengers();
    }

    @Override
    public boolean getIsInvulnerable() {
        return this.invulnerable;
    }

    /**
     * Hooks into vanilla's writeToNBT to call {@link #spongeImpl$writeToSpongeCompound}.
     *
     * <p> This makes it easier for other entity mixins to override writeToNBT
     * without having to specify the <code>@Inject</code> annotation. </p>
     *
     * @param compound The compound vanilla writes to (unused because we write
     *        to SpongeData)
     * @param ci (Unused) callback info
     */
    @Inject(method = "writeToNBT(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;", at = @At("HEAD"))
    private void onSpongeWriteToNBT(final NBTTagCompound compound, final CallbackInfoReturnable<NBTTagCompound> ci) {
        this.spongeImpl$writeToSpongeCompound(this.getSpongeData());
    }

    /**
     * Hooks into vanilla's readFromNBT to call {@link #spongeImpl$readFromSpongeCompound}.
     *
     * <p> This makes it easier for other entity mixins to override readSpongeNBT
     * without having to specify the <code>@Inject</code> annotation. </p>
     *
     * @param compound The compound vanilla reads from (unused because we read
     *        from SpongeData)
     * @param ci (Unused) callback info
     */
    @Inject(method = "readFromNBT(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"))
    private void onSpongeReadFromNBT(final NBTTagCompound compound, final CallbackInfo ci) {
        if (this.isConstructing) {
            firePostConstructEvents(); // Do this early as possible
        }
        this.spongeImpl$readFromSpongeCompound(this.getSpongeData());
    }

    /**
     * Read extra data (SpongeData) from the entity's NBT tag. This is
     * meant to be overridden for each impl based mixin that has to store
     * custom fields based on it's implementation. Examples can include:
     * vanishing booleans, maximum air, maximum boat speeds and modifiers,
     * etc.
     *
     * @param compound The SpongeData compound to read from
     */
    protected void spongeImpl$readFromSpongeCompound(final NBTTagCompound compound) {
        CustomDataNbtUtil.readCustomData(compound, ((org.spongepowered.api.entity.Entity) this));
        if (this instanceof GrieferBridge && ((GrieferBridge) this).bridge$isGriefer() && compound.hasKey(NbtDataUtil.CAN_GRIEF)) {
            ((GrieferBridge) this).bridge$SetCanGrief(compound.getBoolean(NbtDataUtil.CAN_GRIEF));
        }
        if (compound.hasKey(NbtDataUtil.IS_VANISHED, NbtDataUtil.TAG_BYTE)) {
            this.setVanished(compound.getBoolean(NbtDataUtil.IS_VANISHED));
        }
    }

    /**
     * Write extra data (SpongeData) to the entity's NBT tag. This is
     * meant to be overridden for each impl based mixin that has to store
     * custom fields based on it's implementation. Examples can include:
     * vanishing booleans, maximum air, maximum boat speeds and modifiers,
     * etc.
     *
     * @param compound The SpongeData compound to write to
     */
    protected void spongeImpl$writeToSpongeCompound(final NBTTagCompound compound) {
        CustomDataNbtUtil.writeCustomData(compound, (org.spongepowered.api.entity.Entity) this);
        if (this instanceof GrieferBridge && ((GrieferBridge) this).bridge$isGriefer()) {
            compound.setBoolean(NbtDataUtil.CAN_GRIEF, ((GrieferBridge) this).bridge$CanGrief());
        }
        if (this.isVanished) {
            compound.setBoolean(NbtDataUtil.IS_VANISHED, true);
        }
    }

    @Override
    public Optional<User> getTrackedPlayer(final String nbtKey) {
        return Optional.empty();
    }

    @Override
    public Optional<User> getCreatorUser() {
        return Optional.empty();
    }

    @Override
    public Optional<User> getNotifierUser() {
        return Optional.empty();
    }

    @Override
    public void trackEntityUniqueId(final String nbtKey, @Nullable final UUID uuid) {
    }

    @Override
    public void setImplVelocity(final Vector3d velocity) {
        this.motionX = checkNotNull(velocity).getX();
        this.motionY = velocity.getY();
        this.motionZ = velocity.getZ();
        this.velocityChanged = true;
    }

    @Redirect(method = "move",at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;"
                                                                        + "onEntityWalk(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;)V"))
    private void spongeImpl$onEntityCollideWithBlockThrowEventSponge(final Block block, final net.minecraft.world.World world, final BlockPos pos, final Entity entity) {
        // if block can't collide, return
        if (!((BlockBridge) block).hasCollideLogic()) {
            return;
        }

        if (world.isRemote) {
            block.onEntityWalk(world, pos, entity);
            return;
        }

        final IBlockState state = world.getBlockState(pos);
        this.setCurrentCollidingBlock((BlockState) state);
        if (!SpongeCommonEventFactory.handleCollideBlockEvent(block, world, pos, state, entity, Direction.NONE)) {
            block.onEntityWalk(world, pos, entity);
            this.lastCollidedBlockPos = pos;
        }

        this.setCurrentCollidingBlock(null);
    }

    @Redirect(method = "doBlockCollisions", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;onEntityCollision(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/Entity;)V")) // doBlockCollisions
    private void spongeImpl$onEntityCollideWithBlockState(
        final Block block, final net.minecraft.world.World world, final BlockPos pos, final IBlockState state, final Entity entity) {
        // if block can't collide, return
        if (!((BlockBridge) block).hasCollideWithStateLogic()) {
            return;
        }

        if (world.isRemote) {
            block.onEntityCollision(world, pos, state, entity);
            return;
        }

        this.setCurrentCollidingBlock((BlockState) state);
        if (!SpongeCommonEventFactory.handleCollideBlockEvent(block, world, pos, state, entity, Direction.NONE)) {
            block.onEntityCollision(world, pos, state, entity);
            this.lastCollidedBlockPos = pos;
        }

        this.setCurrentCollidingBlock(null);
    }

    @Redirect(method = "updateFallState", at = @At(value = "INVOKE", target="Lnet/minecraft/block/Block;onFallenUpon(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;F)V"))
    private void spongeImpl$onBlockFallenUpon(
        final Block block, final net.minecraft.world.World world, final BlockPos pos, final Entity entity, final float fallDistance) {
        if (world.isRemote) {
            block.onFallenUpon(world, pos, entity, fallDistance);
            return;
        }

        final IBlockState state = world.getBlockState(pos);
        this.setCurrentCollidingBlock((BlockState) state);
        if (!SpongeCommonEventFactory.handleCollideBlockEvent(block, world, pos, state, entity, Direction.UP)) {
            block.onFallenUpon(world, pos, entity, fallDistance);
            this.lastCollidedBlockPos = pos;
        }

        this.setCurrentCollidingBlock(null);
    }



    @Override
    public boolean isVanished() {
        return this.isVanished;
    }

    @Override
    public void setVanished(final boolean vanished) {
        this.isVanished = vanished;
        this.pendingVisibilityUpdate = true;
        this.visibilityTicks = 20;
    }

    @Override
    public boolean ignoresCollision() {
        return this.collision;
    }

    @Override
    public void setIgnoresCollision(final boolean prevents) {
        this.collision = prevents;
    }

    @Override
    public boolean isUntargetable() {
        return this.untargetable;
    }

    @Override
    public void setUntargetable(final boolean untargetable) {
        this.untargetable = untargetable;
    }

    /**
     * @author gabizou - January 4th, 2016
     * @reason  gabizou - January 27th, 2016 - Rewrite to a redirect
     *
     * This prevents sounds from being sent to the server by entities that are vanished
     */
    @Redirect(method = "playSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSilent()Z"))
    private boolean spongeImpl$checkIsSilentOrInvis(final Entity entity) {
        return entity.isSilent() || this.isVanished;
    }

    @Redirect(method = "applyEntityCollision", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;noClip:Z", opcode = Opcodes.GETFIELD))
    private boolean spongeApplyEntityCollisionCheckVanish(final Entity entity) {
        return entity.noClip || ((EntityBridge) entity).isVanished();
    }

    @Redirect(method = "doWaterSplashEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;DDDDDD[I)V"))
    private void spawnParticle(final net.minecraft.world.World world, final EnumParticleTypes particleTypes, final double xCoord, final double yCoord, final double zCoord,
        final double xOffset, final double yOffset, final double zOffset, final int... p_175688_14_) {
        if (!this.isVanished) {
            this.world.spawnParticle(particleTypes, xCoord, yCoord, zCoord, xOffset, yOffset, zOffset, p_175688_14_);
        }
    }

    @Redirect(method = "createRunningParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;DDDDDD[I)V"))
    private void runningSpawnParticle(final net.minecraft.world.World world, final EnumParticleTypes particleTypes, final double xCoord, final double yCoord, final double zCoord,
        final double xOffset, final double yOffset, final double zOffset, final int... p_175688_14_) {
        if (!this.isVanished) {
            this.world.spawnParticle(particleTypes, xCoord, yCoord, zCoord, xOffset, yOffset, zOffset, p_175688_14_);
        }
    }

    @Nullable
    @Override
    public Text getDisplayNameText() {
        return this.displayName;
    }

    private boolean skipSettingCustomNameTag = false;

    @Override
    public void setDisplayName(@Nullable final Text displayName) {
        this.displayName = displayName;

        this.skipSettingCustomNameTag = true;
        if (this.displayName == null) {
            this.setCustomNameTag("");
        } else {
            this.setCustomNameTag(SpongeTexts.toLegacy(this.displayName));
        }

        this.skipSettingCustomNameTag = false;
    }

    @Inject(method = "setCustomNameTag", at = @At("RETURN"))
    private void impl$UpdatedisplayNameText(final String name, final CallbackInfo ci) {
        if (!this.skipSettingCustomNameTag) {
            this.displayName = SpongeTexts.fromLegacy(name);
        }
    }

    /**
     * @author gabizou - January 30th, 2016
     * @author blood - May 12th, 2016
     * @author gabizou - June 2nd, 2016
     *
     * @reason Rewrites the method entirely for several reasons:
     * 1) If we are in a forge environment, we do NOT want forge to be capturing the item entities, because we handle them ourselves
     * 2) If we are in a client environment, we should not perform any sort of processing whatsoever.
     * 3) This method is entirely managed from the standpoint where our events have final say, as per usual.
     *
     * @param stack
     * @param offsetY
     * @return
     */
    @Overwrite
    @Nullable
    public EntityItem entityDropItem(final ItemStack stack, final float offsetY) {
        // Sponge Start
        // Gotta stick with the client side handling things
        if (this.world.isRemote) {
            // Sponge End - resume normal client code. Server side we will handle it elsewhere
            if (stack.isEmpty()) {
                return null;
            } else {
                final EntityItem entityitem = new EntityItem(this.world, this.posX, this.posY + (double) offsetY, this.posZ, stack);
                entityitem.setDefaultPickupDelay();
                this.world.spawnEntity(entityitem);
                return entityitem;
            }
        }
        // Sponge - Redirect server sided code to handle through the PhaseTracker
        return EntityUtil.entityOnDropItem((Entity) (Object) this, stack, offsetY);
    }

    @Override
    public void setCurrentCollidingBlock(@Nullable final BlockState state) {
        this.currentCollidingBlock = state;
    }

    @Override
    public BlockState getCurrentCollidingBlock() {
        if (this.currentCollidingBlock == null) {
            return (BlockState) Blocks.AIR.getDefaultState();
        }
        return this.currentCollidingBlock;
    }

    @Nullable
    @Override
    public BlockPos getLastCollidedBlockPos() {
        return this.lastCollidedBlockPos;
    }

    @Override
    public boolean isVanilla() {
        return this.entityType.isVanilla();
    }

    @Override
    public Timing bridge$getTimingsHandler() {
        return this.entityType.getTimingsHandler();
    }

    @Override
    @Nullable
    public ChunkBridge getActiveChunk() {
        return this.activeChunk.get();
    }

    @Override
    public void setActiveChunk(@Nullable final ChunkBridge chunk) {
        this.activeChunk = new WeakReference<>(chunk);
    }

    @Override
    public boolean shouldTick() {
        final ChunkBridge chunk = this.getActiveChunk();
        // Don't tick if chunk is queued for unload or is in progress of being scheduled for unload
        // See https://github.com/SpongePowered/SpongeVanilla/issues/344
        return chunk == null || chunk.isActive();
    }

    @Override
    public void setInvulnerable(final boolean value) {
        this.invulnerable = value;
    }

    @Override
    public boolean allowsBlockBulkCapture() {
        return this.entityType.allowsBlockBulkCapture;
    }

    @Override
    public boolean allowsEntityBulkCapture() {
        return this.entityType.allowsEntityBulkCapture;
    }

    @Override
    public boolean allowsBlockEventCreation() {
        return this.entityType.allowsBlockEventCreation;
    }

    @Override
    public boolean allowsEntityEventCreation() {
        return this.entityType.allowsEntityEventCreation;
    }


    @Redirect(method = "setFire",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;fire:I", opcode = Opcodes.PUTFIELD)
    )
    private void spongeImpl$ThrowIgniteEventForFire(final Entity entity, final int ticks) {
        if (((WorldBridge) this.world).isFake() || !ShouldFire.IGNITE_ENTITY_EVENT) {
            this.fire = ticks; // Vanilla functionality
            return;
        }
        if (this.fire < 1 && !this.spongeImpl$isImmuneToFireForIgniteEvent()) {
            try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {

                frame.pushCause(((org.spongepowered.api.entity.Entity) this).getLocation().getExtent());
                final IgniteEntityEvent event = SpongeEventFactory.
                        createIgniteEntityEvent(frame.getCurrentCause(), ticks, ticks, (org.spongepowered.api.entity.Entity) this);

                if (SpongeImpl.postEvent(event)) {
                    this.fire = 0;
                    return; // set fire ticks to 0
                }
                this.fire = event.getFireTicks();
            }
        }
    }

    /**
     * Overridden method for Players to determine whether this entity is immune to fire
     * such that {@link IgniteEntityEvent}s are not needed to be thrown as they cannot
     * take fire damage, nor do they light on fire.
     *
     * @return True if this entity is immune to fire.
     */
    protected boolean spongeImpl$isImmuneToFireForIgniteEvent() { // Since normal entities don't have the concept of having game modes...
        return false;
    }

    @Redirect(method = "onStruckByLightning", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z"))
    private boolean spongeImpl$ThrowDamageEventWithLightingSource(
        final Entity entity, final DamageSource source, final float damage, final EntityLightningBolt lightningBolt) {
        if (!this.world.isRemote) {
            return entity.attackEntityFrom(source, damage);
        }
        try {
            DamageSource.LIGHTNING_BOLT = new EntityDamageSource("lightningBolt", lightningBolt);
            return entity.attackEntityFrom(DamageSource.LIGHTNING_BOLT, damage);
        } finally {
            DamageSource.LIGHTNING_BOLT = source;
        }
    }

}
