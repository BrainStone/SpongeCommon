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
package org.spongepowered.common.bridge.entity;

import com.flowpowered.math.vector.Vector3d;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.entity.dismount.DismountType;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.common.bridge.OwnershipTrackedBridge;
import org.spongepowered.common.bridge.TrackableBridge;
import org.spongepowered.common.event.tracking.phase.tick.EntityTickContext;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

public interface EntityBridge {

    boolean bridge$isConstructing();

    void bridge$fireConstructors();

    /**
     * Gets whether this entity has been added to a World's tracked entity lists
     * @return True if this entity is being tracked in a world's chunk lists.
     */
    boolean bridge$isWorldTracked();

    /**
     * Sets an entity to be tracked or untracked. Specifically used in
     * {@link net.minecraft.world.World#onEntityAdded(Entity)} and
     * {@link net.minecraft.world.World#onEntityRemoved(Entity)}.
     *
     * @param tracked Tracked
     */
    void bridge$setWorldTracked(boolean tracked);

    boolean removePassengers(DismountType type);

    void bridge$setImplVelocity(Vector3d velocity);

    @Nullable Text getDisplayNameText();

    void setDisplayName(@Nullable Text displayName);

    void setCurrentCollidingBlock(BlockState state);

    @Nullable BlockState getCurrentCollidingBlock();

    @Nullable BlockPos getLastCollidedBlockPos();

    boolean isVanilla();

    void setDestructCause(Cause cause);

    void bridge$setLocationAndAngles(Location<World> location);

    void bridge$setLocationAndAngles(Transform<World> transform);

    void createForgeCapabilities();

    default void onJoinWorld() {

    }

    boolean shouldTick();

    default void clearWrappedCaptureList() {

    }

    /**
     * @author gabizou - July 26th, 2018
     * @reason Due to vanilla logic, a block is removed *after* the held item is set,
     * so, when the block event gets cancelled, we don't have a chance to cancel the
     * enderman pickup. Specifically applies to Enderman so far, may have other uses
     * in the future.
     *
     * @param phaseContext The context, for whatever reason in the future
     */
    default void onCancelledBlockChange(EntityTickContext phaseContext) {

    }
}
