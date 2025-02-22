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
package org.spongepowered.common.bridge.entity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import org.spongepowered.api.event.cause.entity.damage.DamageFunction;
import org.spongepowered.api.event.cause.entity.damage.DamageModifier;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

import java.util.List;
import java.util.Optional;

// in 1.14, this can be named MobEntityBridge.
public interface BaseLivingEntityBridge {

    boolean bridge$damageEntityHook(DamageSource damageSource, float damage);

    int bridge$getMaxAir();

    void bridge$setMaxAir(int max);

    Optional<List<DamageFunction>> bridge$provideArmorModifiers(EntityLivingBase entityLivingBase, DamageSource source, double damage);

    float bridge$applyModDamage(EntityLivingBase entityLivingBase, DamageSource source, float damage);

    void bridge$applyArmorDamage(EntityLivingBase entityLivingBase, DamageSource source, DamageEntityEvent entityEvent, DamageModifier modifier);

    boolean bridge$hookModAttack(EntityLivingBase entityLivingBase, DamageSource source, float amount);

    void bridge$resetDeathEventsPosted();

}
