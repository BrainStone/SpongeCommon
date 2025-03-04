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
package org.spongepowered.common.event.tracking.phase.entity;

import net.minecraft.world.WorldServer;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.world.PortalAgent;
import org.spongepowered.api.world.World;
import org.spongepowered.common.event.tracking.IPhaseState;

public final class InvokingTeleporterContext extends EntityContext<InvokingTeleporterContext> {

    private WorldServer world;
    private PortalAgent agent;
    private Transform<World> transform;
    private boolean didPort = false;

    InvokingTeleporterContext(IPhaseState<? extends InvokingTeleporterContext> state) {
        super(state);
    }

    public InvokingTeleporterContext setTargetWorld(WorldServer world) {
        this.world = world;
        return this;
    }

    public WorldServer getTargetWorld() {
        return this.world;
    }

    public InvokingTeleporterContext setTeleporter(PortalAgent agent) {
        this.agent = agent;
        return this;
    }

    public PortalAgent getTeleporter() {
        return this.agent;
    }

    public InvokingTeleporterContext setExitTransform(Transform<World> transform) {
        this.transform = transform;
        return this;
    }

    public Transform<World> getExitTransform() {
        return this.transform;
    }

    public InvokingTeleporterContext setDidPort(boolean didPort) {
        this.didPort = didPort;
        return this;
    }

    public boolean getDidPort() {
        return this.didPort;
    }
}
