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
package org.spongepowered.common.mixin.core.entity.passive;

import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.manipulator.mutable.entity.ParrotData;
import org.spongepowered.api.data.type.ParrotVariant;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.api.entity.living.animal.Parrot;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeParrotData;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeSittingData;
import org.spongepowered.common.mixin.api.minecraft.entity.passive.MixinEntityTameable_API;
import org.spongepowered.common.mixin.core.entity.MixinEntityAgeable;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.data.value.mutable.SpongeValue;
import org.spongepowered.common.registry.type.entity.ParrotVariantRegistryModule;

import java.util.List;
import java.util.Random;

@Mixin(EntityParrot.class)
public abstract class MixinEntityParrot extends MixinEntityAgeable  {

    @Redirect(method = "processInteract",
        at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", ordinal = 0, remap = false))
    private int impl$TameEntityAndGetRandom(Random rand, int bound, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        int random = rand.nextInt(bound);
        if (random == 0) {
            stack.setCount(stack.getCount() + 1);
            try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                frame.pushCause(player);
                if (!SpongeImpl.postEvent(SpongeEventFactory.createTameEntityEvent(frame.getCurrentCause(), (Parrot) this))) {
                    stack.setCount(stack.getCount() - 1);
                    return random;
                }
            }
        }
        return 1;
    }


}
