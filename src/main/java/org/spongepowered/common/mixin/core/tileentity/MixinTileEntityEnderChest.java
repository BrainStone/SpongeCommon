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
package org.spongepowered.common.mixin.core.tileentity;

import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.SoundCategory;
import org.spongepowered.api.block.tileentity.EnderChest;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@NonnullByDefault
@Mixin(TileEntityEnderChest.class)
public abstract class MixinTileEntityEnderChest extends MixinTileEntity {

    @Shadow public float lidAngle;
    @Shadow public int numPlayersUsing;

    /**
     * @author bloodmc - July 21st, 2016
     *
     * @reason Overwritten in case ender chests ever attempt to tick
     */
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void impl$IgnoreTicking(CallbackInfo ci) {
        if (this.world == null || !this.world.isRemote) {
            // chests should never tick on server
            ci.cancel();
        }
    }

    @Inject(method = "openChest",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addBlockEvent(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;II)V"
            ),
            cancellable = true)
    private void impl$PlaySoundOnServerOnlyDuringUseOnOpen(CallbackInfo ci) {
        // Moved out of tick loop
        if (this.world == null) {
            ci.cancel();
            return;
        }
        if (this.world.isRemote) {
            return;
        }

        if (this.numPlayersUsing > 0 && this.lidAngle == 0.0F) {
            this.lidAngle = 0.7F;
            double posX = (double)this.pos.getX() + 0.5D;
            double posY = (double)this.pos.getY() + 0.5D;
            double posZ = (double)this.pos.getZ() + 0.5D;

            this.world.playSound(null, posX, posY, posZ, SoundEvents.BLOCK_ENDERCHEST_OPEN, SoundCategory.BLOCKS, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
        }
    }

    @Inject(method = "closeChest",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addBlockEvent(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;II)V"
            ),
            cancellable = true)
    private void impl$PlaySoundOnServerOnlyDuringUseOnClose(CallbackInfo ci) {
        // Moved out of tick loop
        if (this.world == null) {
            ci.cancel();
            return;
        }
        if (this.world.isRemote) {
            return;
        }

        if (this.numPlayersUsing == 0 && this.lidAngle > 0.0F || this.numPlayersUsing > 0 && this.lidAngle < 1.0F) {
            double posX = (double)this.pos.getX() + 0.5D;
            double posY = (double)this.pos.getY() + 0.5D;
            double posZ = (double)this.pos.getZ() + 0.5D;


            this.world.playSound(null, posX, posY, posZ, SoundEvents.BLOCK_ENDERCHEST_CLOSE, SoundCategory.BLOCKS, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
        }
    }
}
