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
package org.spongepowered.common.mixin.optimization.tileentity;

import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.bridge.optimization.HopperOptimizationBridge;

@Mixin(value = TileEntity.class, priority = 1300)
public class MixinTileEntity_HopperOptimization implements HopperOptimizationBridge {

    private boolean hopper$shouldCancelDirtyUpdate = false;

    @Override
    public boolean hopper$getCancelDirtyUpdate() {
        return this.hopper$shouldCancelDirtyUpdate;
    }

    @Override
    public void hopper$setCancelDirtyUpdate(final boolean canMarkDirty) {
        this.hopper$shouldCancelDirtyUpdate = canMarkDirty;
    }

    @Inject(method = "markDirty", at = @At("HEAD"), cancellable = true)
    private void hopper$DoNotUpdateIfMarked(final CallbackInfo ci) {
        if (this.hopper$shouldCancelDirtyUpdate) {
            ci.cancel();
        }
    }
}
