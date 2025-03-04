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
package org.spongepowered.common.data.processor.data.entity;

import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.immutable.entity.ImmutableHealthScalingData;
import org.spongepowered.api.data.manipulator.mutable.entity.HealthScalingData;
import org.spongepowered.api.data.value.ValueContainer;
import org.spongepowered.api.data.value.immutable.ImmutableValue;
import org.spongepowered.api.data.value.mutable.MutableBoundedValue;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeHealthScaleData;
import org.spongepowered.common.data.processor.common.AbstractEntitySingleDataProcessor;
import org.spongepowered.common.data.value.SpongeValueFactory;
import org.spongepowered.common.bridge.entity.player.ServerPlayerEntityBridge;

import java.util.Optional;

public class HealthScalingProcessor extends AbstractEntitySingleDataProcessor<EntityPlayerMP, Double, MutableBoundedValue<Double>, HealthScalingData, ImmutableHealthScalingData> {

    public HealthScalingProcessor() {
        super(EntityPlayerMP.class, Keys.HEALTH_SCALE);
    }

    @Override
    protected HealthScalingData createManipulator() {
        return new SpongeHealthScaleData();
    }

    @Override
    protected boolean set(EntityPlayerMP dataHolder, Double value) {
        if (value < 1D) {
            return false;
        }
        if (value > Float.MAX_VALUE) {
            return false;
        }
        final ServerPlayerEntityBridge mixinPlayer = (ServerPlayerEntityBridge) dataHolder;
        if (value == 20D) {
            mixinPlayer.setHealthScale(20);
            mixinPlayer.setHealthScaled(true);
            return true;
        }
        mixinPlayer.setHealthScale(value);
        return true;
    }

    @Override
    protected Optional<Double> getVal(EntityPlayerMP dataHolder) {
        final ServerPlayerEntityBridge mixinPlayer = (ServerPlayerEntityBridge) dataHolder;
        return Optional.ofNullable(mixinPlayer.isHealthScaled() ? mixinPlayer.getHealthScale() : null);
    }

    @Override
    protected ImmutableValue<Double> constructImmutableValue(Double value) {
        return SpongeValueFactory.boundedBuilder(Keys.HEALTH_SCALE)
                .minimum(1D)
                .maximum((double) Float.MAX_VALUE)
                .defaultValue(20D)
                .actualValue(value)
                .build()
                .asImmutable();
    }

    @Override
    protected MutableBoundedValue<Double> constructValue(Double actualValue) {
        return SpongeValueFactory.boundedBuilder(Keys.HEALTH_SCALE)
                .minimum(1D)
                .maximum((double) Float.MAX_VALUE)
                .defaultValue(20D)
                .actualValue(actualValue)
                .build();
    }

    @Override
    public DataTransactionResult removeFrom(ValueContainer<?> container) {
        if (!(container instanceof ServerPlayerEntityBridge)) {
            return DataTransactionResult.failNoData();
        }
        final ImmutableValue<Double> current = constructImmutableValue(((ServerPlayerEntityBridge) container).getHealthScale());
        ((ServerPlayerEntityBridge) container).setHealthScale(20D);
        ((ServerPlayerEntityBridge) container).setHealthScaled(false);
        return DataTransactionResult.successRemove(current);
    }
}
