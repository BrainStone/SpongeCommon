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
package org.spongepowered.common.event.tracking.phase.generation;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.world.BlockChangeFlag;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.event.tracking.phase.tick.BlockTickContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A generalized generation phase state. Used for entering populator world generation,
 * new chunk generation, and world spawner entity spawning (since it is used as a populator).
 * Generally does not capture or throw events unless necessary.
 */
@SuppressWarnings("rawtypes")
abstract class GeneralGenerationPhaseState<G extends GenerationContext<G>> implements IPhaseState<G> {

    private Set<IPhaseState<?>> compatibleStates = new HashSet<>();
    private boolean isBaked = false;
    private final String id;
    private final String desc;

    GeneralGenerationPhaseState(String id) {
        this.id = id;
        this.desc = TrackingUtil.phaseStateToString("Generation", id, this);
    }

    final GeneralGenerationPhaseState addCompatibleState(IPhaseState<?> state) {
        if (this.isBaked) {
            throw new IllegalStateException("This state is already baked! " + this.id);
        }
        this.compatibleStates.add(state);
        return this;
    }

    final GeneralGenerationPhaseState bake() {
        if (this.isBaked) {
            throw new IllegalStateException("This state is already baked! " + this.id);
        }
        this.compatibleStates = ImmutableSet.copyOf(this.compatibleStates);
        this.isBaked = true;
        return this;
    }


    @Override
    public boolean requiresPost() {
        return false;
    }

    @Override
    public final boolean isNotReEntrant() {
        return false;
    }

    @Override
    public boolean ignoresBlockEvent() {
        return true;
    }

    @Override
    public boolean ignoresBlockUpdateTick(G context) {
        return true;
    }

    @Override
    public boolean ignoresEntityCollisions() {
        return true;
    }

    @Override
    public boolean doesBulkBlockCapture(G context) {
        return false;
    }

    @Override
    public boolean alreadyProcessingBlockItemDrops() {
        return true;
    }

    @Override
    public boolean isWorldGeneration() {
        return true;
    }

    @Override
    public boolean includesDecays() {
        return true;
    }

    @Override
    public boolean allowsEventListener() {
        return false;
    }

    @Override
    public boolean shouldCaptureBlockChangeOrSkip(G phaseContext, BlockPos pos, IBlockState currentState, IBlockState newState,
        BlockChangeFlag flags) {
        return false;
    }

    @Override
    public void appendNotifierPreBlockTick(ServerWorldBridge mixinWorld, BlockPos pos, G context, BlockTickContext phaseContext) {

    }

    @Override
    public boolean doesBlockEventTracking(G context) {
        return false;
    }

    @Override
    public final void unwind(G context) {
        final List<Entity> spawnedEntities = context.getCapturedEntitySupplier().orEmptyList();
        if (spawnedEntities.isEmpty()) {
            return;
        }
        SpongeCommonEventFactory.callSpawnEntitySpawner(spawnedEntities, context);
    }

    @Override
    public boolean spawnEntityOrCapture(G context, Entity entity, int chunkX, int chunkZ) {
        final ArrayList<Entity> entities = new ArrayList<>(1);
        entities.add(entity);
        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(entity.getLocation().getExtent());
            return SpongeCommonEventFactory.callSpawnEntitySpawner(entities, context);
        }
    }

    @Override
    public boolean tracksOwnersAndNotifiers() {
        return false;
    }

    @Override
    public boolean doesCaptureEntitySpawns() {
        return false;
    }

    @Override
    public boolean shouldProvideModifiers(G phaseContext) {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GeneralGenerationPhaseState that = (GeneralGenerationPhaseState) o;
        return Objects.equal(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.id);
    }

    @Override
    public String toString() {
        return this.desc;
    }
}
