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
package org.spongepowered.common.data.processor.value.entity;

import net.minecraft.entity.item.EntityItem;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.value.ValueContainer;
import org.spongepowered.api.data.value.immutable.ImmutableValue;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.common.bridge.entity.ItemEntityBridge;
import org.spongepowered.common.data.processor.common.AbstractSpongeValueProcessor;
import org.spongepowered.common.data.value.mutable.SpongeValue;
import org.spongepowered.common.util.Constants;

import java.util.Optional;

public class InfinitePickupDelayValueProcessor extends AbstractSpongeValueProcessor<EntityItem, Boolean, Value<Boolean>> {

    public InfinitePickupDelayValueProcessor() {
        super(EntityItem.class, Keys.INFINITE_PICKUP_DELAY);
    }

    @Override
    public Value<Boolean> constructValue(Boolean defaultValue) {
        return new SpongeValue<>(Keys.INFINITE_PICKUP_DELAY, false, defaultValue);
    }

    @Override
    protected boolean set(EntityItem container, Boolean value) {
        ((ItemEntityBridge) container).bridge$setPickupDelay(value ? Constants.Entity.Item.MAGIC_NO_PICKUP : Constants.Entity.Item.DEFAULT_PICKUP_DELAY, value);
        return true;
    }

    @Override
    protected Optional<Boolean> getVal(EntityItem container) {
        return Optional.of(((ItemEntityBridge) container).bridge$infinitePickupDelay());
    }

    @Override
    protected ImmutableValue<Boolean> constructImmutableValue(Boolean value) {
        return constructValue(value).asImmutable();
    }

    @Override
    protected boolean supports(EntityItem container) {
        return true;
    }

    @Override
    public DataTransactionResult removeFrom(ValueContainer<?> container) {
        return DataTransactionResult.failNoData();
    }

}