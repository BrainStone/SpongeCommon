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

import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.inventory.ContainerHorseChest;
import org.spongepowered.api.entity.living.animal.Horse;
import org.spongepowered.api.item.inventory.Carrier;
import org.spongepowered.api.item.inventory.type.CarriedInventory;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.interfaces.inventory.IMixinCarriedInventory;
import org.spongepowered.common.mixin.api.minecraft.entity.passive.MixinEntityAnimal_API;
import org.spongepowered.common.mixin.core.entity.MixinEntityAgeable;
import org.spongepowered.common.mixin.core.entity.MixinEntityLiving;

@Mixin(AbstractHorse.class)
public abstract class MixinAbstractHorse extends MixinEntityAgeable implements Horse {

    @Shadow protected ContainerHorseChest horseChest;

    @Inject(method = "initHorseChest", at = @At("RETURN"))
    private void onInitHorseChest(final CallbackInfo ci) {
        if (this.horseChest instanceof IMixinCarriedInventory) {
            ((IMixinCarriedInventory) this.horseChest).setCarrier(this);
        }
    }
}
