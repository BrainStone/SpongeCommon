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
package org.spongepowered.common.mixin.api.minecraft.entity.projectile;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityFireball;
import org.spongepowered.api.entity.projectile.explosive.fireball.Fireball;
import org.spongepowered.api.entity.projectile.source.ProjectileSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.mixin.api.minecraft.entity.MixinEntity_API;

import javax.annotation.Nullable;

@Mixin(EntityFireball.class)
public abstract class MixinEntityFireball_API extends MixinEntity_API implements Fireball {

    @Shadow @Nullable public EntityLivingBase shootingEntity;

    @Nullable private ProjectileSource projectileSource = null;

    @Override
    public ProjectileSource getShooter() {
        if (this.shootingEntity instanceof ProjectileSource) {
            return (ProjectileSource) this.shootingEntity;
        }

        if (this.projectileSource != null) {
            return this.projectileSource;
        }

        return ProjectileSource.UNKNOWN;
    }

    @Override
    public void setShooter(ProjectileSource shooter) {
        this.projectileSource = shooter;
        if (shooter instanceof EntityLivingBase) {
            this.shootingEntity = (EntityLivingBase) shooter;
        } else {
            this.shootingEntity = null;
        }
    }

}
