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
package org.spongepowered.common.event.tracking;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.world.BlockChangeFlag;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.World;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.block.SpongeBlockSnapshot;
import org.spongepowered.common.entity.PlayerTracker;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.tracking.phase.TrackingPhase;
import org.spongepowered.common.event.tracking.phase.entity.EntityPhase;
import org.spongepowered.common.event.tracking.phase.general.ExplosionContext;
import org.spongepowered.common.event.tracking.phase.packet.PacketPhase;
import org.spongepowered.common.event.tracking.phase.tick.BlockTickContext;
import org.spongepowered.common.event.tracking.phase.tick.TickPhase;
import org.spongepowered.common.interfaces.IMixinChunk;
import org.spongepowered.common.interfaces.block.IMixinBlockEventData;
import org.spongepowered.common.interfaces.world.IMixinWorldServer;
import org.spongepowered.common.world.BlockChange;
import org.spongepowered.common.world.SpongeBlockChangeFlag;
import org.spongepowered.common.world.WorldUtil;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A literal phase state of which the {@link World} is currently running
 * in. The state itself is owned by {@link TrackingPhase}s as the phase
 * defines what to do upon
 * {@link IPhaseState#unwind(PhaseContext)}.
 * As these should be enums, there's no data that should be stored on
 * this state. It can have control flow with {@link #canSwitchTo(IPhaseState)}
 * where preventing switching to another state is possible (likely points out
 * either errors or runaway states not being unwound).
 */
public interface IPhaseState<C extends PhaseContext<C>> {

    TrackingPhase getPhase();

    C createPhaseContext();

    default boolean canSwitchTo(IPhaseState<?> state) {
        return false;
    }

    /**
     * The exit point of any phase. Every phase should have an unwinding
     * process where if anything is captured, events should be thrown and
     * processed accordingly. The outcome of each phase is dependent on
     * the {@link IPhaseState} provded, as different states require different
     * handling.
     *
     * <p>Examples of this include: {@link PacketPhase}, {@link TickPhase}, etc.
     * </p>
     *
     * <p>Note that the {@link PhaseTracker} is only provided for easy access
     * to the {@link WorldServer}, {@link IMixinWorldServer}, and
     * {@link World} instances.</p>
     *
     * @param phaseContext The context of the current state being unwound
     */
    void unwind(C phaseContext);

    /**
     * This is the post dispatch method that is automatically handled for
     * states that deem it necessary to have some post processing for
     * advanced game mechanics. This is always performed when capturing
     * has been turned on during a phases's
     * {@link IPhaseState#unwind(PhaseContext)} is
     * dispatched. The rules of post dispatch are as follows:
     * - Entering extra phases is not allowed: This is to avoid
     *  potential recursion in various corner cases.
     * - The unwinding phase context is provided solely as a root
     *  cause tracking for any nested notifications that require
     *  association of causes
     * - The unwinding phase is used with the unwinding state to
     *  further exemplify during what state that was unwinding
     *  caused notifications. This narrows down to the exact cause
     *  of the notifications.
     * - post dispatch may loop several times until no more notifications
     *  are required to be dispatched. This may include block physics for
     *  neighbor notification events.
     *
     * @param unwindingState
     * @param unwindingContext The context of the state that was unwinding,
     *     contains the root cause for the state
     * @param postContext The post dispatch context captures containing any
     * */
    default void postDispatch(IPhaseState<?> unwindingState, PhaseContext<?> unwindingContext, C postContext) {

    }

    /**
     * This is Step 3 of entity spawning. It is used for the sole purpose of capturing an entity spawn
     * and doesn't actually spawn an entity into the world until the current phase is unwound.
     * The method itself should technically capture entity spawns, however, in the event it
     * is required that the entity cannot be captured, returning {@code false} will mark it
     * to spawn into the world, bypassing any of the bulk spawn events or capturing.
     *
     * <p>NOTE: This method should only be called and handled if and only if {@link IPhaseState#allowEntitySpawns()}
     * returns {@code true}. Violation of this will have unforseen consequences.</p>
     *
     *
     * @param context The current context
     * @param entity The entity being captured
     * @param chunkX The chunk x position
     * @param chunkZ The chunk z position
     * @return True if the entity was successfully captured
     */
    default boolean spawnEntityOrCapture(C context, org.spongepowered.api.entity.Entity entity, int chunkX, int chunkZ) {
        final ArrayList<org.spongepowered.api.entity.Entity> entities = new ArrayList<>(1);
        entities.add(entity);
        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.PASSIVE);
            return SpongeCommonEventFactory.callSpawnEntity(entities, context);
        }
    }

    default boolean ignoresBlockTracking() {
        return false;
    }

    default void handleBlockChangeWithUser(@Nullable BlockChange blockChange, Transaction<BlockSnapshot> snapshotTransaction, C context) {

    }

    default boolean tracksBlockSpecificDrops() {
        return false;
    }

    /**
     * A simple boolean switch to whether an {@link net.minecraft.entity.EntityLivingBase#onDeath(DamageSource)}
     * should enter a specific phase to handle the destructed drops until either after this current phase
     * has completed (if returning {@code true}) or whether the entity is going to enter a specific
     * phase directly to handle entity drops (if returning {@code false}). Most all phases should
     * return true, except certain few that require it. The reasoning for a phase to return
     * {@code false} would be if it's own phase can handle entity drops with appropriate causes
     * on it's own.
     *
     * @return True if this phase is aware enough to handle entity death drops per entity, or will
     *     cause {@link EntityPhase.State#DEATH} to be entered and handle it's own drops
     */
    default boolean tracksEntitySpecificDrops() {
        return false;
    }

    default boolean ignoresEntityCollisions() {
        return false;
    }

    default boolean isExpectedForReEntrance() {
        return false;
    }

    default boolean tracksEntityDeaths() {
        return false;
    }

    default boolean shouldCaptureBlockChangeOrSkip(C phaseContext, BlockPos pos) {
        return true;
    }

    default boolean isInteraction() {
        return false;
    }

    default void postTrackBlock(BlockSnapshot snapshot, C context) {

    }


    default boolean requiresBlockPosTracking() {
        return false;
    }

    default boolean isTicking() {
        return false;
    }

    default boolean handlesOwnStateCompletion() {
        return false;
    }

    default void associateAdditionalCauses(PhaseContext<?> context, CauseStackManager.StackFrame frame) {
        context.getOwner().ifPresent(owner -> frame.addContext(EventContextKeys.OWNER, owner));
        context.getNotifier().ifPresent(notifier -> frame.addContext(EventContextKeys.NOTIFIER, notifier));

    }

    default boolean doesCaptureEntityDrops(C context) {
        return false;
    }

    default boolean requiresPost() {
        return true;
    }

    default boolean ignoresBlockUpdateTick(PhaseData phaseData) {
        return false;
    }

    default boolean allowEntitySpawns() {
        return true;
    }

    default boolean ignoresBlockEvent() {
        return false;
    }

    default boolean ignoresScheduledUpdates() {
        return false;
    }

    default boolean alreadyCapturingBlockTicks(C context) {
        return false;
    }
    default boolean doesBulkBlockCapture(C context) {
        return true;
    }

    default void postProcessSpawns(C unwindingContext, ArrayList<org.spongepowered.api.entity.Entity> entities) {
        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.BLOCK_SPAWNING);
            SpongeCommonEventFactory.callSpawnEntity(entities, unwindingContext);
        }
    }

    default boolean alreadyCapturingEntitySpawns() {
        return false;
    }

    default boolean alreadyCapturingEntityTicks() {
        return false;
    }
    default boolean alreadyCapturingTileTicks() {
        return false;
    }

    default boolean isWorldGeneration() {
        return false;
    }
    default boolean alreadyCapturingItemSpawns() {
        return false;
    }
    default boolean ignoresItemPreMerging() {
        return false;
    }

    default void appendNotifierPreBlockTick(IMixinWorldServer mixinWorld, BlockPos pos, C context, BlockTickContext phaseContext) {
        final Chunk chunk = WorldUtil.asNative(mixinWorld).getChunkFromBlockCoords(pos);
        final IMixinChunk mixinChunk = (IMixinChunk) chunk;
        if (chunk != null && !chunk.isEmpty()) {
            mixinChunk.getBlockOwner(pos).ifPresent(phaseContext::owner);
            mixinChunk.getBlockNotifier(pos).ifPresent(phaseContext::notifier);
        }
    }
    default void capturePlayerUsingStackToBreakBlock(ItemStack itemStack, EntityPlayerMP playerIn, C context) {

    }
    default void addNotifierToBlockEvent(C context, IMixinWorldServer mixinWorldServer, BlockPos pos, IMixinBlockEventData blockEvent) {

    }
    default void associateNeighborStateNotifier(C unwindingContext, @Nullable BlockPos sourcePos, Block block, BlockPos notifyPos,
        WorldServer minecraftWorld, PlayerTracker.Type notifier) {

    }
    default void appendContextPreExplosion(ExplosionContext explosionContext, C currentPhaseData) {

    }
    default boolean doesDenyChunkRequests() {
        return false;
    }

    /**
     * A phase specific method that determines whether it is needed to capture the entity based onto the
     * entity-specific lists of drops, or a generalized list of drops.
     *
     * Cases for entity specific drops:
     * - Explosions
     * - Entity deaths
     * - Commands killing mass entities and those entities dropping items
     *
     * Cases for generalized drops:
     * - Phase states for specific entity deaths
     * - Phase states for generalization, like packet handling
     * - Using items
     *
     * @param phaseContext The current context
     * @param entity The entity performing the drop or "source" of the drop
     * @param entityitem The item to be dropped
     * @return True if we are capturing, false if we are to let the item spawn
     */
    default boolean performOrCaptureItemDrop(C phaseContext, Entity entity, EntityItem entityitem) {
        if (this.doesCaptureEntityDrops(phaseContext)) {
            if (this.tracksEntitySpecificDrops()) {
                // We are capturing per entity drop
                // This has to be handled specially for the entity in forge environments to
                // specifically syncronize the list used for sponge's tracking and forge's partial tracking
                SpongeImplHooks.capturePerEntityItemDrop(phaseContext, entity, entityitem);
            } else {
                // We are adding to a general list - usually for EntityPhase.State.DEATH
                phaseContext.getCapturedItemsSupplier().get().add(entityitem);
            }
            // Return the item, even if it wasn't spawned in the world.
            return true;
        }
        return false;
    }
    default boolean doesCaptureEntitySpawns() {
        return false;
    }

    /**
     * Specifically designed to allow certain registries use the event listener hooks to prevent unnecessary off-threaded
     * checks and allows for registries to restrict additional registrations ouside of events.
     *
     * @return True if this is an event listener state
     */
    default boolean isEvent() {
        return false;
    }

    /**
     * An alternative to {@link #doesBulkBlockCapture(PhaseContext)} to where if capturing is expressly
     * disabled, we can still track the block change through normal methods, and throw events,
     * but we won't be capturing directly or delaying any block related physics.
     *
     * <p>If this and {@link #doesBulkBlockCapture(PhaseContext)} both return {@code false}, vanilla
     * mechanics will take place, and no tracking or capturing is taking place unless otherwise
     * noted by
     * {@link #associateNeighborStateNotifier(PhaseContext, BlockPos, Block, BlockPos, WorldServer, PlayerTracker.Type)}</p>
     *
     * @return True by default, false for things like world gen
     * @param context
     */
    default boolean doesBlockEventTracking(C context) {
        return true;
    }

    default boolean acceptBlockChangeAndThrowEvent(IMixinWorldServer mixinWorld, Chunk chunk, IBlockState currentState, IBlockState newState, BlockPos pos,
        BlockChangeFlag flag, C context) {
        final WorldServer minecraftWorld = (WorldServer) mixinWorld;
        final SpongeBlockChangeFlag spongeFlag = (SpongeBlockChangeFlag) flag;
        final Block block = newState.getBlock();

        if (!ShouldFire.CHANGE_BLOCK_EVENT) { // If we don't have to worry about any block events, don't bother
            // Sponge End - continue with vanilla mechanics
            final IBlockState iblockstate = chunk.setBlockState(pos, newState);

            if (iblockstate == null) {
                return false;
            }
            // else { // Sponge - unnecessary formatting
            if (newState.getLightOpacity() != iblockstate.getLightOpacity() || newState.getLightValue() != iblockstate.getLightValue()) {
                minecraftWorld.profiler.startSection("checkLight"); // Sponge - we don't need to us the profiler
                minecraftWorld.checkLight(pos);
                minecraftWorld.profiler.endSection(); // Sponge - We don't need to use the profiler
            }

            if (spongeFlag.isNotifyClients() && chunk.isPopulated()) {
                minecraftWorld.notifyBlockUpdate(pos, iblockstate, newState, spongeFlag.getRawFlag());
            }

            TrackingUtil.notifyNeighbors(pos, newState, minecraftWorld, block, iblockstate, flag.updateNeighbors(), flag.notifyObservers());

            return true;
        }
        // Sponge Start - Fall back to performing a singular block capture and throwing an event with all the
        // reprocussions, such as neighbor notifications and whatnot. Entity spawns should also be
        // properly handled since bulk captures technically should be disabled if reaching
        // this point.
        final SpongeBlockSnapshot originalBlockSnapshot= mixinWorld.createSpongeBlockSnapshot(currentState, currentState, pos, flag);
        final List<BlockSnapshot> capturedSnapshots = new ArrayList<>(1); // only need tone
        final Block newBlock = newState.getBlock();

        TrackingUtil.associateBlockChangeWithSnapshot(this, newBlock, currentState, originalBlockSnapshot, capturedSnapshots);
        final IMixinChunk mixinChunk = (IMixinChunk) chunk;
        final IBlockState originalBlockState = mixinChunk.setBlockState(pos, newState, currentState, originalBlockSnapshot);
        if (originalBlockState == null) {
            return false; // Return fast
        }
        final Transaction<BlockSnapshot> transaction = TrackingUtil.TRANSACTION_CREATION.apply(originalBlockSnapshot);
        final ImmutableList<Transaction<BlockSnapshot>> transactions = ImmutableList.of(transaction);
        // Create and throw normal event
        final ChangeBlockEvent normalEvent =
            originalBlockSnapshot.blockChange.createEvent(Sponge.getCauseStackManager().getCurrentCause(), transactions);
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            this.associateAdditionalCauses(context, frame);
            SpongeImpl.postEvent(normalEvent);
            frame.pushCause(normalEvent); // Because of our contract for post events
            final ChangeBlockEvent.Post post = this.createChangeBlockPostEvent(context, transactions);
            SpongeImpl.postEvent(post);
            if (post == null) {
                return false;
            }
            if (!transaction.isValid()) {
                transaction.getOriginal().restore(true, BlockChangeFlags.NONE);
                if (this.tracksBlockSpecificDrops()) {
                    context.getBlockDropSupplier().removeAllIfNotEmpty(pos);
                }
                return false; // Short circuit
            }
            // And now, proceed as normal.
            return TrackingUtil.performTransactionProcess(transaction, this, context, false);
        }
    }

    /**
     * Specifically used when block changes have taken place in place (after block events are thrown),
     * some captures may take place, and those captures may need to be "depth first" processed. Imagining
     * that every block change that is bulk captured would be iterated and the changes from those block changes
     * iterated in a fashion of a "Depth First" iteration of a tree. This is to propogate Minecraft block
     * physics correctly and allow mechanics to function that otherwise would not function correctly.
     *
     * Case in point: Once we had done the "breadth first" strategy of processing, which broke redstone
     * contraptions, but allowed some "interesting" new contraptions, including but not excluded to a new
     * easy machine that could create quantum redstone clocks where redstone would be flipped twice in a
     * "single" tick. It was pretty cool, but did not work out as it broke vanilla mechanics.
     *
     * @param context The context to re-check for captures
     */
    default void performOnBlockAddedSpawns(C context) {

    }

    /**
     * Specifically used when block changes have taken place in place (after block events are thrown),
     * some captures may take place, and those captures may need to be "depth first" processed. Imagining
     * that every block change that is bulk captured would be iterated and the changes from those block changes
     * iterated in a fashion of a "Depth First" iteration of a tree. This is to propogate Minecraft block
     * physics correctly and allow mechanics to function that otherwise would not function correctly.
     *
     * Case in point: Once we had done the "breadth first" strategy of processing, which broke redstone
     * contraptions, but allowed some "interesting" new contraptions, including but not excluded to a new
     * easy machine that could create quantum redstone clocks where redstone would be flipped twice in a
     * "single" tick. It was pretty cool, but did not work out as it broke vanilla mechanics.
     *
     * @param context The context to re-check for captures
     */
    default void performPostBlockNotificationsAndNeighborUpdates(C context) {

    }

    /**
     * Used to create any extra specialized events for {@link ChangeBlockEvent.Post} as necessary.
     * An example of this being used specially is for explosions.
     *
     * @param context
     * @param transactions
     * @return
     */
    default ChangeBlockEvent.Post createChangeBlockPostEvent(C context, ImmutableList<Transaction<BlockSnapshot>> transactions) {
        return SpongeEventFactory.createChangeBlockEventPost(Sponge.getCauseStackManager().getCurrentCause(), transactions);
    }
}
