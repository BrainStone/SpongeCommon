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
package org.spongepowered.common.mixin.realtime.mixin;

import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.mixin.realtime.IMixinRealTimeTicking;

@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayer_RealTime extends MixinEntityLivingBase_RealTime {

    @Shadow public int xpCooldown;
    @Shadow private int sleepTimer;

    @Redirect(method = "onUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;xpCooldown:I", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void adjustForRealTimeXpCooldown(EntityPlayer self, int modifier) {
        if (SpongeImplHooks.isFakePlayer((EntityPlayer) (Object) this) || ((WorldBridge) this.world).isFake()) {
            this.xpCooldown = modifier;
        }
        int ticks = (int) ((IMixinRealTimeTicking) self.getEntityWorld()).getRealTimeTicks();
        this.xpCooldown = Math.max(0, this.xpCooldown - ticks);
    }

    @Redirect(
        method = "onUpdate",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/entity/player/EntityPlayer;sleepTimer:I",
            opcode = Opcodes.PUTFIELD
        ),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/entity/player/EntityPlayer;isPlayerSleeping()Z"
            ),
            to = @At(
                value = "CONSTANT",
                args = "intValue=100",
                ordinal = 0
            )
        )
    )
    private void adjustForRealTimeSleepTimer(EntityPlayer self, int modifier) {
        if (SpongeImplHooks.isFakePlayer((EntityPlayer) (Object) this) || ((WorldBridge) this.world).isFake()) {
            this.sleepTimer = modifier;
        }
        int ticks = (int) ((IMixinRealTimeTicking) self.getEntityWorld()).getRealTimeTicks();
        this.sleepTimer += ticks;
    }

    @Redirect(
        method = "onUpdate",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/entity/player/EntityPlayer;sleepTimer:I",
            opcode = Opcodes.PUTFIELD
        ),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/entity/player/EntityPlayer;wakeUpPlayer(ZZZ)V",
                ordinal = 1
            ),
            to = @At(
                value = "CONSTANT",
                args = "intValue=110"
            )
        )
    )
    private void adjustForRealTimeWakeTimer(EntityPlayer self, int modifier) {
        if (SpongeImplHooks.isFakePlayer((EntityPlayer) (Object) this) || ((WorldBridge) this.world).isFake()) {
            this.sleepTimer = modifier;
        }
        int ticks = (int) ((IMixinRealTimeTicking) self.getEntityWorld()).getRealTimeTicks();
        this.sleepTimer += ticks;
    }

}
