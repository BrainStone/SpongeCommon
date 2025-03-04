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
package org.spongepowered.common.mixin.core.entity.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAIMate;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.world.World;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.Ageable;
import org.spongepowered.api.entity.living.animal.Animal;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.TristateResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.event.ShouldFire;

import java.util.Optional;

@Mixin(EntityAIMate.class)
public abstract class MixinEntityAIMate {

    @Shadow @Final private EntityAnimal animal;
    @Shadow private EntityAnimal targetMate;
    @Shadow private EntityAnimal getNearbyMate() {
        // Shadow implements
        return null;
    }

    private boolean spawnEntityResult;

    @SuppressWarnings("deprecation")
    @Redirect(method = "shouldExecute", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ai/EntityAIMate;getNearbyMate()Lnet/minecraft/entity/passive/EntityAnimal;"))
    private EntityAnimal callFindMateEvent(final EntityAIMate entityAIMate) {
        EntityAnimal nearbyMate = this.getNearbyMate();
        if (nearbyMate == null) {
            return null;
        }

        if (ShouldFire.BREED_ENTITY_EVENT_FIND_MATE) {
            try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                frame.pushCause(this.animal);
                final org.spongepowered.api.event.entity.BreedEntityEvent.FindMate event =
                    SpongeEventFactory.createBreedEntityEventFindMate(Sponge.getCauseStackManager().getCurrentCause(), TristateResult.Result.DEFAULT,
                        TristateResult.Result.DEFAULT, Optional.empty(), (Animal) nearbyMate, (Ageable) this.animal, true);
                if (SpongeImpl.postEvent(event) || event.getResult() == TristateResult.Result.DENY) {
                    nearbyMate = null;
                }
            }
        }

        return nearbyMate;
    }

    @Inject(method = "spawnBaby", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z", shift = At.Shift.AFTER, ordinal = 0), cancellable = true)
    private void onAfterSpawnEntity(CallbackInfo ci) {
        if (!this.spawnEntityResult) {
            ci.cancel();
        }
    }

    @SuppressWarnings("deprecation")
    @Redirect(method = "spawnBaby()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z", ordinal = 0))
    private boolean onSpawnBabyInWorld(World world, Entity baby) {
        if (ShouldFire.BREED_ENTITY_EVENT_BREED) {
            try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                // TODO API 8 is removing this TargetXXXX nonsense so that is why I put the parents into the Cause
                frame.pushCause(this.animal);
                frame.pushCause(this.targetMate);
                final org.spongepowered.api.event.entity.BreedEntityEvent.Breed event =
                    SpongeEventFactory.createBreedEntityEventBreed(Sponge.getCauseStackManager().getCurrentCause(),
                    Optional.empty(), (Ageable) baby, (Ageable) this.targetMate);
                this.spawnEntityResult = !SpongeImpl.postEvent(event) && world.spawnEntity(baby);
            }
        } else {
            this.spawnEntityResult = world.spawnEntity(baby);
        }
        return this.spawnEntityResult;
    }



}
