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
package org.spongepowered.common.event.tracking.phase.tick;

import net.minecraft.world.WorldServer;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.bridge.world.ServerWorldBridge;

import javax.annotation.Nullable;

public final class DimensionContext extends TickContext<DimensionContext> {

    @Nullable private World world;

    DimensionContext() {
        super(TickPhase.Tick.DIMENSION);
    }

    public DimensionContext world(WorldServer worldServer) {
        this.world = ((World) worldServer);
        return this;
    }

    public DimensionContext world(ServerWorldBridge worldServer) {
        this.world = ((World) worldServer);
        return this;
    }

    public World getWorld() throws IllegalStateException {
        if (this.world == null) {
            throw new IllegalStateException("Expected to be ticking on a world!");
        }
        return this.world;
    }

    @Override
    public PrettyPrinter printCustom(PrettyPrinter printer, int indent) {
        String s = String.format("%1$"+indent+"s", "");
        return super.printCustom(printer, indent)
            .add(s + "- %s: %s", "World", this.world)
            ;
    }
}
