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
package org.spongepowered.common.mixin.core.world.chunk;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.IChunkGenerator;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.entity.CollideEntityEvent;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.api.world.BlockChangeFlag;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.block.SpongeBlockSnapshot;
import org.spongepowered.common.block.SpongeBlockSnapshotBuilder;
import org.spongepowered.common.bridge.tileentity.TileEntityBridge;
import org.spongepowered.common.bridge.util.CacheKeyed;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.bridge.world.chunk.ActiveChunkReferantBridge;
import org.spongepowered.common.bridge.world.chunk.ChunkBridge;
import org.spongepowered.common.bridge.world.chunk.ChunkProviderBridge;
import org.spongepowered.common.entity.PlayerTracker;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.event.tracking.context.BlockTransaction;
import org.spongepowered.common.event.tracking.phase.generation.GenerationPhase;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.util.SpongeHooks;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.gen.WorldGenConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

@NonnullByDefault
@Mixin(net.minecraft.world.chunk.Chunk.class)
public abstract class MixinChunk implements ChunkBridge, CacheKeyed {


    @Shadow @Final private World world;
    @Shadow @Final public int x;
    @Shadow @Final public int z;
    @Shadow @Final private ExtendedBlockStorage[] storageArrays;
    @Shadow @Final private int[] precipitationHeightMap;
    @Shadow @Final private int[] heightMap;
    @Shadow @Final private ClassInheritanceMultiMap<Entity>[] entityLists;
    @Shadow @Final private Map<BlockPos, TileEntity> tileEntities;
    @Shadow private boolean loaded;
    @Shadow private boolean dirty;
    @Shadow public boolean unloadQueued;

    @Shadow @Nullable public abstract TileEntity getTileEntity(BlockPos pos, net.minecraft.world.chunk.Chunk.EnumCreateEntityType p_177424_2_);
    @Shadow public abstract void generateSkylightMap();
    @Shadow public abstract int getLightFor(EnumSkyBlock p_177413_1_, BlockPos pos);
    @Shadow public abstract IBlockState getBlockState(BlockPos pos);
    @Shadow public abstract IBlockState getBlockState(int x, int y, int z);
    @Shadow public abstract Biome getBiome(BlockPos pos, BiomeProvider chunkManager);
    @Shadow private void propagateSkylightOcclusion(final int x, final int z) { }
    @Shadow private void relightBlock(final int x, final int y, final int z) { }
    // @formatter:on

    private long scheduledForUnload = -1; // delay chunk unloads
    private boolean persistedChunk = false;
    private boolean isSpawning = false;
    private net.minecraft.world.chunk.Chunk[] neighbors = new net.minecraft.world.chunk.Chunk[4];
    private long cacheKey;

    @Inject(method = "<init>(Lnet/minecraft/world/World;II)V", at = @At("RETURN"))
    private void impl$onConstruct(World worldIn, int x, int z, CallbackInfo ci) {
        this.cacheKey = ChunkPos.asLong(x, z);
    }

    @Override
    public net.minecraft.world.chunk.Chunk[] getNeighborArray() {
        return this.neighbors;
    }

    @Override
    public long cache$getCacheKey() {
        return this.cacheKey;
    }

    @Override
    public void markChunkDirty() {
        this.dirty = true;
    }

    @Override
    public boolean isChunkLoaded() {
        return this.loaded;
    }

    @Override
    public boolean isQueuedForUnload() {
        return this.unloadQueued;
    }

    @Override
    public boolean isPersistedChunk() {
        return this.persistedChunk;
    }

    @Override
    public void setPersistedChunk(final boolean flag) {
        this.persistedChunk = flag;
        // update persisted status for entities and TE's
        for (final TileEntity tileEntity : this.tileEntities.values()) {
            ((ActiveChunkReferantBridge) tileEntity).bridge$setActiveChunk(this);
        }
        for (final ClassInheritanceMultiMap<Entity> entityList : this.entityLists) {
            for (final Entity entity : entityList) {
                ((ActiveChunkReferantBridge) entity).bridge$setActiveChunk(this);
            }
        }
    }

    @Override
    public boolean isSpawning() {
        return this.isSpawning;
    }

    @Override
    public void setIsSpawning(final boolean spawning) {
        this.isSpawning = spawning;
    }

    @Inject(method = "addEntity", at = @At("RETURN"))
    private void impl$SetActiveChunkOnEntityAdd(final Entity entityIn, final CallbackInfo ci) {
        ((ActiveChunkReferantBridge) entityIn).bridge$setActiveChunk(this);
    }

    @Inject(
        method = "addTileEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;validate()V"))
    private void impl$SetActiveChunkOnTileEntityAdd(final BlockPos pos, final TileEntity tileEntityIn, final CallbackInfo ci) {
        ((ActiveChunkReferantBridge) tileEntityIn).bridge$setActiveChunk(this);
    }

    @Inject(method = "removeEntityAtIndex", at = @At("RETURN"))
    private void impl$ResetEntityActiveChunk(final Entity entityIn, final int index, final CallbackInfo ci) {
        ((ActiveChunkReferantBridge) entityIn).bridge$setActiveChunk(null);
    }

    @Redirect(method = "removeTileEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;invalidate()V"))
    private void impl$resetTileEntityActiveChunk(final TileEntity tileEntityIn) {
        ((ActiveChunkReferantBridge) tileEntityIn).bridge$setActiveChunk(null);
        tileEntityIn.invalidate();
    }

    @Inject(method = "onLoad", at = @At("HEAD"), cancellable = true)
    private void impl$IgnoreOnLoadDuringRegeneration(final CallbackInfo ci) {
        if (!this.world.isRemote) {
            if (PhaseTracker.getInstance().getCurrentState() == GenerationPhase.State.CHUNK_REGENERATING_LOAD_EXISTING) {
                // If we are loading an existing chunk for the sole purpose of
                // regenerating, we can skip loading TE's and Entities into the world
                ci.cancel();
            }
        }
    }

    @Inject(method = "onLoad", at = @At("RETURN"))
    private void impl$UpdateNeighborsOnLoad(final CallbackInfo ci) {
        for (final Direction direction : Constants.Chunk.CARDINAL_DIRECTIONS) {
            final Vector3i neighborPosition = ((Chunk) this).getPosition().add(direction.asBlockOffset());
            final ChunkProviderBridge spongeChunkProvider = (ChunkProviderBridge) this.world.getChunkProvider();
            final net.minecraft.world.chunk.Chunk neighbor = spongeChunkProvider.bridge$getLoadedChunkWithoutMarkingActive
                    (neighborPosition.getX(), neighborPosition.getZ());
            if (neighbor != null) {
                final int neighborIndex = SpongeImpl.directionToIndex(direction);
                final int oppositeNeighborIndex = SpongeImpl.directionToIndex(direction.getOpposite());
                this.setNeighborChunk(neighborIndex, neighbor);
                ((ChunkBridge) neighbor).setNeighborChunk(oppositeNeighborIndex, (net.minecraft.world.chunk.Chunk) (Object) this);
            }
        }

        if (ShouldFire.LOAD_CHUNK_EVENT) {
            SpongeImpl.postEvent(SpongeEventFactory.createLoadChunkEvent(Sponge.getCauseStackManager().getCurrentCause(), (Chunk) this));
        }
        if (!this.world.isRemote) {
            SpongeHooks.logChunkLoad(this.world, ((Chunk) this).getPosition());
        }
    }

    @Inject(method = "onUnload", at = @At("RETURN"))
    private void impl$UpdateNeighborsOnUnload(final CallbackInfo ci) {
        for (final Direction direction : Constants.Chunk.CARDINAL_DIRECTIONS) {
            final Vector3i neighborPosition = ((Chunk) this).getPosition().add(direction.asBlockOffset());
            final ChunkProviderBridge spongeChunkProvider = (ChunkProviderBridge) this.world.getChunkProvider();
            final net.minecraft.world.chunk.Chunk neighbor = spongeChunkProvider.bridge$getLoadedChunkWithoutMarkingActive
                    (neighborPosition.getX(), neighborPosition.getZ());
            if (neighbor != null) {
                final int neighborIndex = SpongeImpl.directionToIndex(direction);
                final int oppositeNeighborIndex = SpongeImpl.directionToIndex(direction.getOpposite());
                this.setNeighborChunk(neighborIndex, null);
                ((ChunkBridge) neighbor).setNeighborChunk(oppositeNeighborIndex, null);
            }
        }

        if (!this.world.isRemote) {
            SpongeImpl.postEvent(SpongeEventFactory.createUnloadChunkEvent(Sponge.getCauseStackManager().getCurrentCause(), (Chunk) this));
            SpongeHooks.logChunkUnload(this.world, ((Chunk) this).getPosition());
        }
    }


    @Inject(method = "getEntitiesWithinAABBForEntity", at = @At(value = "RETURN"))
    private void impl$ThrowCollisionEvent(final Entity entityIn, final AxisAlignedBB aabb, final List<Entity> listToFill,
        @SuppressWarnings("Guava") final Predicate<Entity> p_177414_4_, final CallbackInfo ci) {
        if (((WorldBridge) this.world).isFake() || PhaseTracker.getInstance().getCurrentState().ignoresEntityCollisions()) {
            return;
        }

        if (listToFill.size() == 0) {
            return;
        }

        if (!ShouldFire.COLLIDE_ENTITY_EVENT) {
            return;
        }

        final CollideEntityEvent event = SpongeCommonEventFactory.callCollideEntityEvent(this.world, entityIn, listToFill);

        if (event == null || event.isCancelled()) {
            if (event == null && !PhaseTracker.getInstance().getCurrentState().isTicking()) {
                return;
            }
            listToFill.clear();
        }
    }

    @Inject(method = "getEntitiesOfTypeWithinAABB", at = @At(value = "RETURN"))
    private void impl$throwCollsionEvent(final Class<? extends Entity> entityClass, final AxisAlignedBB aabb, final List<Entity> listToFill,
        @SuppressWarnings("Guava") final Predicate<Entity> p_177430_4_, final CallbackInfo ci) {
        if (((WorldBridge) this.world).isFake() || PhaseTracker.getInstance().getCurrentState().ignoresEntityCollisions()) {
            return;
        }

        if (listToFill.size() == 0) {
            return;
        }

        final CollideEntityEvent event = SpongeCommonEventFactory.callCollideEntityEvent(this.world, null, listToFill);

        if (event == null || event.isCancelled()) {
            if (event == null && !PhaseTracker.getInstance().getCurrentState().isTicking()) {
                return;
            }
            listToFill.clear();
        }
    }


    /**
     * @author blood
     * @reason cause tracking
     *
     * @param pos The position to set
     * @param state The state to set
     * @return The changed state
     */
    @Nullable
    @Overwrite
    public IBlockState setBlockState(final BlockPos pos, final IBlockState state) {
        final IBlockState iblockstate1 = this.getBlockState(pos);

        // Sponge - reroute to new method that accepts snapshot to prevent a second snapshot from being created.
        return setBlockState(pos, state, iblockstate1, BlockChangeFlags.ALL);
    }

    /**
     * @author blood - November 2015
     * @author gabizou - updated April 10th, 2016 - Add cause tracking refactor changes
     * @author gabizou - Updated June 26th, 2018 - Bulk capturing changes
     * @author gabizou - March 4th, 2019 - Refactoring and cleanup with multipos captures
     *
     *
     * @param pos The position changing
     * @param newState The new state
     * @param currentState The current state - passed in from either chunk or world
     * @return The changed block state if not null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    @Nullable
    public IBlockState setBlockState(final BlockPos pos, final IBlockState newState, final IBlockState currentState, final BlockChangeFlag flag) {
        final int xPos = pos.getX() & 15;
        final int yPos = pos.getY();
        final int zPos = pos.getZ() & 15;
        final int combinedPos = zPos << 4 | xPos;

        if (yPos >= this.precipitationHeightMap[combinedPos] - 1) {
            this.precipitationHeightMap[combinedPos] = -999;
        }

        final int currentHeight = this.heightMap[combinedPos];

        // Sponge Start - remove blockstate check as we handle it in world.setBlockState
        // IBlockState iblockstate = this.getBlockState(pos);
        //
        // if (iblockstate == state) {
        //    return null;
        // } else {
        final Block newBlock = newState.getBlock();
        final Block currentBlock = currentState.getBlock();
        // Sponge End

        ExtendedBlockStorage extendedblockstorage = this.storageArrays[yPos >> 4];
        // Sponge - make this final so we don't change it later
        final boolean requiresNewLightCalculations;

        // Sponge - Forge moves this from
        final int newBlockLightOpacity = SpongeImplHooks.getBlockLightOpacity(newState, this.world, pos);

        if (extendedblockstorage == net.minecraft.world.chunk.Chunk.NULL_BLOCK_STORAGE) {
            if (newBlock == Blocks.AIR) {
                return null;
            }

            extendedblockstorage = this.storageArrays[yPos >> 4] = new ExtendedBlockStorage(yPos >> 4 << 4, this.world.provider.hasSkyLight());
            requiresNewLightCalculations = yPos >= currentHeight;
            // Sponge Start - properly initialize variable
        } else {
            requiresNewLightCalculations = false;
        }
        // Sponge end

        // Sponge Start
        // Set up some default information variables for later processing
        final boolean isFake = ((WorldBridge) this.world).isFake();
        final TileEntity existing = this.getTileEntity(pos, net.minecraft.world.chunk.Chunk.EnumCreateEntityType.CHECK);
        final PhaseContext<?> peek = isFake ? null : PhaseTracker.getInstance().getCurrentContext();
        final IPhaseState state = isFake ? null : peek.state;
        final SpongeBlockSnapshot snapshot = (isFake || (!ShouldFire.CHANGE_BLOCK_EVENT || !state.shouldCaptureBlockChangeOrSkip(peek, pos, currentState, newState, flag))) ? null : createSpongeBlockSnapshot(currentState, currentState, pos, flag, existing);
        final BlockTransaction.ChangeBlock transaction;
        final ServerWorldBridge mixinWorld = isFake ? null : (ServerWorldBridge) this.world;

        final int modifiedY = yPos & 15;

        extendedblockstorage.set(xPos, modifiedY, zPos, newState);


        // if (block1 != block) // Sponge - Forge removes this change.
        // { // Sponge - remove unnecessary braces
        if (!this.world.isRemote) {
            // Sponge - Redirect phase checks to use isFake in the event we have mods worlds doing silly things....
            // i.e. fake worlds. Likewise, avoid creating unnecessary snapshots/transactions
            // or triggering unprocessed captures when there are no events being thrown.
            if (!isFake && ShouldFire.CHANGE_BLOCK_EVENT && snapshot != null) {

                // Mark the tile entity as captured so when it is being removed during the chunk setting, it won't be
                // re-captured again.
                snapshot.blockChange = ((IPhaseState) peek.state).associateBlockChangeWithSnapshot(peek, newState, newBlock, currentState, snapshot, currentBlock);
                transaction = state.captureBlockChange(peek, pos, snapshot, newState, flag, existing);

                if (currentBlock != newBlock) {
                    // We want to queue the break logic later, while the transaction is processed
                    if (transaction != null) {
                        transaction.queueBreak = true;
                        transaction.enqueueChanges(mixinWorld.bridge$getProxyAccess(), peek.getCapturedBlockSupplier());
                    }
                    currentBlock.breakBlock(this.world, pos, currentState);
                }
                if (existing != null && SpongeImplHooks.shouldRefresh(existing, this.world, pos, currentState, newState)) {
                    // And likewise, we want to queue the tile entity being removed, while
                    // the transaction is processed.
                    if (transaction != null) {
                        // Set tile to be captured, if it's showing up in removals later, it will
                        // be ignored since the transaction process will actually process
                        // the removal.
                        ((TileEntityBridge) existing).setCaptured(true);
                        transaction.queuedRemoval = existing;
                        transaction.enqueueChanges(mixinWorld.bridge$getProxyAccess(), peek.getCapturedBlockSupplier());
                    } else {
                        this.world.removeTileEntity(pos);
                    }
                }

            } else {
                transaction = null;
                // Sponge - Forge adds this change for block changes to only fire events when necessary
                if (currentBlock != newBlock && (state == null || !state.isRestoring())) { // cache the block break in the event we're capturing tiles
                    currentBlock.breakBlock(this.world, pos, currentState);
                }
                // Sponge - Add several tile entity hook checks. Mainly for forge added hooks, but these
                // still work by themselves in vanilla. In all intents and purposes, the remove tile entity could
                // occur before we have a chance to
                if (existing != null && SpongeImplHooks.shouldRefresh(existing, this.world, pos, currentState, newState)) {
                    this.world.removeTileEntity(pos);
                }
            }
            // } else if (currentBlock instanceof ITileEntityProvider) { // Sponge - remove since forge has a special hook we need to add here
        } else { // Sponge - Add transaction initializer before checking for tile entities
            transaction = null; // Set the value to null

            // Forge's hook is currentBlock.hasTileEntity(iblockstate) we add it on to SpongeImplHooks via mixins.
            // We don't have to check for transactions or phases or capturing because the world is obviously not being managed
            if (SpongeImplHooks.hasBlockTileEntity(currentBlock, currentState)) {
                final TileEntity tileEntity = this.getTileEntity(pos, net.minecraft.world.chunk.Chunk.EnumCreateEntityType.CHECK);
                // Sponge - Add hook for refreshing, because again, forge hooks.
                if (tileEntity != null && SpongeImplHooks.shouldRefresh(tileEntity, this.world, pos, currentState, newState)) {
                    this.world.removeTileEntity(pos);
                }
            }
        }
        // } // Sponge - Remove unnecessary braces

        final IBlockState blockAfterSet = extendedblockstorage.get(xPos, modifiedY, zPos);
        if (blockAfterSet.getBlock() != newBlock) {
            // Sponge Start - prune tracked change
            if (!isFake && snapshot != null) {
                if (state.doesBulkBlockCapture(peek)) {
                    peek.getCapturedBlockSupplier().prune(snapshot);
                } else {
                    peek.setSingleSnapshot(null);
                }
            }
            return null;
        }

        // } else { // Sponge - remove unnecessary else
        if (requiresNewLightCalculations) {
            this.generateSkylightMap();
        } else {

            // int newBlockLightOpacity = state.getLightOpacity(); - Sponge Forge moves this all the way up before tile entities are removed.
            // int postNewBlockLightOpacity = newState.getLightOpacity(this.worldObj, pos); - Sponge use the SpongeImplHooks for forge compatibility
            final int postNewBlockLightOpacity = SpongeImplHooks.getBlockLightOpacity(newState, this.world, pos);
            // Sponge End

            if (newBlockLightOpacity > 0) {
                if (yPos >= currentHeight) {
                    this.relightBlock(xPos, yPos + 1, zPos);
                }
            } else if (yPos == currentHeight - 1) {
                this.relightBlock(xPos, yPos, zPos);
            }

            if (newBlockLightOpacity != postNewBlockLightOpacity && (newBlockLightOpacity < postNewBlockLightOpacity || this.getLightFor(EnumSkyBlock.SKY, pos) > 0 || this.getLightFor(EnumSkyBlock.BLOCK, pos) > 0)) {
                this.propagateSkylightOcclusion(xPos, zPos);
            }
        }

        // Sponge Start - Handle block physics only if we're actually the server world
        if (!isFake && currentState != newState) {
            // Reset the proxy access or add to the proxy state during processing.
            ((ServerWorldBridge) this.world).bridge$getProxyAccess().onChunkChanged(pos, newState);
        }
        if (!isFake && currentBlock != newBlock) {
            final boolean isBulkCapturing = ShouldFire.CHANGE_BLOCK_EVENT && state.doesBulkBlockCapture(peek);

            // Sponge start - Ignore block activations during block placement captures unless it's
            // a BlockContainer. Prevents blocks such as TNT from activating when cancelled.
            // Forge changes this check from
            // if (!this.world.isRemote && block1 != block)
            // to
            //  if (!this.world.isRemote && block1 != block && (!this.world.captureBlockSnapshots || block.hasTileEntity(state)))
            // which would normally translate to
            // if (!isFake && currentBlock != newBlock && (!isBulkCapturing || SpongeImplHooks.hasBlockTileEntity(newBlock, newState))
            // but, because we have transactions to deal with, we have to still set the transaction to
            // queue on add as long as the flag deems it so.
            if (flag.performBlockPhysics()) {
                // Occasionally, certain phase states will need to prevent onBlockAdded to be called until after the tile entity tracking
                // has been done, in the event of restores needing to re-override the block changes.
                if (transaction != null) {
                    transaction.queueOnAdd = true;
                } else if (!isBulkCapturing || SpongeImplHooks.hasBlockTileEntity(newBlock, newState)) {
                    newBlock.onBlockAdded(this.world, pos, newState);
                }
            }
            // Sponge end
        }

        // Sponge Start - Use SpongeImplHooks for forge compatibility
        // if (block instanceof ITileEntityProvider) { // Sponge
        // We also don't want to create/attempt to create tile entities while they are being tracked, especially if they end up needing to be removed.
        if (SpongeImplHooks.hasBlockTileEntity(newBlock, newState)) {
            // Sponge End
            TileEntity tileentity = this.getTileEntity(pos, net.minecraft.world.chunk.Chunk.EnumCreateEntityType.CHECK);

            if (tileentity == null) {
                // Sponge Start - use SpongeImplHooks for forge compatibility
                // tileentity = ((ITileEntityProvider)block).createNewTileEntity(this.worldObj, block.getMetaFromState(state)); // Sponge
                tileentity = SpongeImplHooks.createTileEntity(newBlock, this.world, newState);

                if (!isFake) { // Surround with a server check
                    final User owner = peek.getOwner().orElse(null);
                    // If current owner exists, transfer it to newly created TE pos
                    // This is required for TE's that get created during move such as pistons and ComputerCraft turtles.
                    if (owner != null) {
                        this.addTrackedBlockPosition(newBlock, pos, owner, PlayerTracker.Type.OWNER);
                    }
                }
                if (transaction != null) {
                    // Go ahead and log the tile being replaced, the tile being removed will be at least already notified of removal
                    transaction.queueTileSet = tileentity;
                    if (tileentity != null) {
                        ((TileEntityBridge) tileentity).setCaptured(true);
                        tileentity.setWorld(this.world);
                        tileentity.setPos(pos);// Set the position
                    }
                    transaction.enqueueChanges(mixinWorld.bridge$getProxyAccess(), peek.getCapturedBlockSupplier());
                } else {
                    // Some mods are relying on the world being set prior to setting the tile
                    // world prior to the position. It's weird, but during block restores, this can
                    // cause an exception.
                    // See https://github.com/SpongePowered/SpongeForge/issues/2677 for reference.
                    // Note that vanilla will set the world later, and forge sets the world
                    // after setting the position, but a mod has expectations that defy both of these...
                    if (tileentity != null && tileentity.getWorld() != this.world) {
                        tileentity.setWorld(this.world);
                    }
                    this.world.setTileEntity(pos, tileentity);
                }
            }

            if (tileentity != null) {
                tileentity.updateContainingBlockInfo();
            }
        } else if (transaction != null) {
            // We still want to enqueue any changes to the transaction, including any tiles removed
            // if there was a tile entity added, it will be logged above
            transaction.enqueueChanges(mixinWorld.bridge$getProxyAccess(), peek.getCapturedBlockSupplier());
        }

        this.dirty = true;
        return currentState;
    }

    @Override
    public void removeTileEntity(final TileEntity removed) {
        final TileEntity tileentity = this.tileEntities.remove(removed.getPos());
        if (tileentity != removed && tileentity != null) {
            // Because multiple requests to remove a tile entity could cause for checks
            // without actually knowing if the chunk doesn't have the tile entity, this
            // avoids storing nulls.
            // Replace the pre-existing tile entity in case we remove a tile entity
            // we don't want to be removing.
            this.tileEntities.put(removed.getPos(), tileentity);
        }
        ((ActiveChunkReferantBridge) removed).bridge$setActiveChunk(null);
        removed.invalidate();
    }

    @Override
    public void setTileEntity(final BlockPos pos, final TileEntity added) {
        if (added.getWorld() != this.world) {
            // Forge adds this because some mods do stupid things....
            added.setWorld(this.world);
        }
        added.setPos(pos);
        if (this.tileEntities.containsKey(pos)) {
            this.tileEntities.get(pos).invalidate();
        }
        added.validate();
        ((ActiveChunkReferantBridge) added).bridge$setActiveChunk(this);
        this.tileEntities.put(pos, added);
    }

    private SpongeBlockSnapshot createSpongeBlockSnapshot(
        final IBlockState state, final IBlockState extended, final BlockPos pos, final BlockChangeFlag updateFlag, @Nullable final TileEntity existing) {
        final SpongeBlockSnapshotBuilder builder = new SpongeBlockSnapshotBuilder();
        builder.reset();
        builder.blockState(state)
            .extendedState(extended)
            .worldId(((org.spongepowered.api.world.World) this.world).getUniqueId())
            .position(VecHelper.toVector3i(pos));
        final Optional<UUID> creator = getBlockOwnerUUID(pos);
        final Optional<UUID> notifier = getBlockNotifierUUID(pos);
        creator.ifPresent(builder::creator);
        notifier.ifPresent(builder::notifier);
        if (existing != null) {
            TrackingUtil.addTileEntityToBuilder(existing, builder);
        }
        builder.flag(updateFlag);
        return builder.build();
    }

    // These methods are enabled in MixinChunk_Tracker as a Mixin plugin

    @Override
    public void addTrackedBlockPosition(final Block block, final BlockPos pos, final User user, final PlayerTracker.Type trackerType) { }

    @Override
    public Map<Integer, PlayerTracker> getTrackedIntPlayerPositions() { return Collections.emptyMap(); }

    @Override
    public Map<Short, PlayerTracker> getTrackedShortPlayerPositions() { return Collections.emptyMap(); }

    @Override
    public Optional<User> getBlockOwner(final BlockPos pos) { return Optional.empty(); }

    @Override
    public Optional<UUID> getBlockOwnerUUID(final BlockPos pos) { return Optional.empty(); }

    @Override
    public Optional<User> getBlockNotifier(final BlockPos pos) { return Optional.empty(); }

    @Override
    public Optional<UUID> getBlockNotifierUUID(final BlockPos pos) { return Optional.empty(); }

    @Override
    public void setBlockNotifier(final BlockPos pos, @Nullable final UUID uuid) { }

    @Override
    public void setBlockCreator(final BlockPos pos, @Nullable final UUID uuid) { }

    @Override
    public void setTrackedIntPlayerPositions(final Map<Integer, PlayerTracker> trackedPositions) { }

    @Override
    public void setTrackedShortPlayerPositions(final Map<Short, PlayerTracker> trackedPositions) { }

    // Continuing the rest of the implementation

    @SuppressWarnings("ConstantConditions")
    @Redirect(
        method = "populate(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/gen/IChunkGenerator;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/IChunkProvider;getLoadedChunk(II)Lnet/minecraft/world/chunk/Chunk;"))
    private net.minecraft.world.chunk.Chunk impl$GetChunkWithoutMarkingAsActive(final IChunkProvider chunkProvider, final int x, final int z) {
        // Don't mark chunks as active
        return ((ChunkProviderBridge) chunkProvider).bridge$getLoadedChunkWithoutMarkingActive(x, z);
    }

    @Inject(
        method = "populate(Lnet/minecraft/world/gen/IChunkGenerator;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/gen/IChunkGenerator;populate(II)V"))
    private void impl$StartTerrainGenerationState(final IChunkGenerator generator, final CallbackInfo callbackInfo) {
        if (!this.world.isRemote) {
            if (!PhaseTracker.getInstance().getCurrentState().isRegeneration()) {
                GenerationPhase.State.TERRAIN_GENERATION.createPhaseContext()
                    .world(this.world)
                    .buildAndSwitch();
            }
        }
    }


    @Inject(method = "populate(Lnet/minecraft/world/gen/IChunkGenerator;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;markDirty()V"),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/IChunkGenerator;populate(II)V"))
    )
    private void impl$CloseTerrainGenerationState(final IChunkGenerator generator, final CallbackInfo info) {
        if (!this.world.isRemote) {
            if (!PhaseTracker.getInstance().getCurrentState().isRegeneration()) {
                PhaseTracker.getInstance().getCurrentContext().close();
            }
        }
    }

    // Fast neighbor methods for internal use
    @Override
    public void setNeighborChunk(final int index, @Nullable final net.minecraft.world.chunk.Chunk chunk) {
        this.neighbors[index] = chunk;
    }

    @Nullable
    @Override
    public net.minecraft.world.chunk.Chunk getNeighborChunk(final int index) {
        return this.neighbors[index];
    }

    @Override
    public List<net.minecraft.world.chunk.Chunk> getNeighbors() {
        final List<net.minecraft.world.chunk.Chunk> neighborList = new ArrayList<>();
        for (final net.minecraft.world.chunk.Chunk neighbor : this.neighbors) {
            if (neighbor != null) {
                neighborList.add(neighbor);
            }
        }
        return neighborList;
    }

    @Override
    public boolean areNeighborsLoaded() {
        for (int i = 0; i < 4; i++) {
            if (this.neighbors[i] == null) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void setNeighbor(final Direction direction, @Nullable final net.minecraft.world.chunk.Chunk neighbor) {
        this.neighbors[SpongeImpl.directionToIndex(direction)] = neighbor;
    }

    @Override
    public long getScheduledForUnload() {
        return this.scheduledForUnload;
    }

    @Override
    public void setScheduledForUnload(final long scheduled) {
        this.scheduledForUnload = scheduled;
    }

    @Inject(method = "generateSkylightMap", at = @At("HEAD"), cancellable = true)
    private void impl$IfLightingEnabledCancel(final CallbackInfo ci) {
        if (!WorldGenConstants.lightingEnabled) {
            ci.cancel();
        }
    }

    @Override
    public void fill(final ChunkPrimer primer) {
        final boolean flag = this.world.provider.hasSkyLight();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                for (int y = 0; y < 256; ++y) {
                    final IBlockState block = primer.getBlockState(x, y, z);
                    if (block.getMaterial() != Material.AIR) {
                        final int section = y >> 4;
                        if (this.storageArrays[section] == net.minecraft.world.chunk.Chunk.NULL_BLOCK_STORAGE) {
                            this.storageArrays[section] = new ExtendedBlockStorage(section << 4, flag);
                        }
                        this.storageArrays[section].set(x, y & 15, z, block);
                    }
                }
            }
        }
    }

    @Redirect(
        method = "addTileEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;invalidate()V"))
    private void redirectInvalidate(final TileEntity te) {
        SpongeImplHooks.onTileEntityInvalidate(te);
    }

    @Override
    public boolean isActive() {
        if (this.isPersistedChunk()) {
            return true;
        }
        return this.loaded && !this.isQueuedForUnload() && this.getScheduledForUnload() == -1;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("World", this.world)
                .add("Position", this.x + this.z)
                .toString();
    }
}
