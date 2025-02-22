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
package org.spongepowered.common.mixin.core.entity;

import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.passive.EntityAnimal;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.animal.Animal;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.bridge.world.WorldBridge;

import java.util.Optional;

@Mixin(EntityAgeable.class)
public abstract class MixinEntityAgeable extends MixinEntity {

    @Inject(method = "setGrowingAge", at = @At("RETURN"))
    private void callReadyToMateOnAgeUp(final int age, final CallbackInfo ci) {
        if (age == 0) {
            this.callReadyToMateEvent();
        }
    }

    @SuppressWarnings("deprecation")
    private void callReadyToMateEvent() {
        if (!((WorldBridge) this.world).isFake() && ShouldFire.BREED_ENTITY_EVENT_READY_TO_MATE && ((EntityAgeable) (Object) this) instanceof EntityAnimal) {
            final org.spongepowered.api.event.entity.BreedEntityEvent.ReadyToMate event =
                SpongeEventFactory.createBreedEntityEventReadyToMate(Sponge.getCauseStackManager().getCurrentCause(), Optional.empty(), (Animal)
                    this);
            SpongeImpl.postEvent(event);
        }
    }
}
