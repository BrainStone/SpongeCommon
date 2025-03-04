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
package org.spongepowered.common.data.processor.multi.entity;

import com.google.common.collect.ImmutableMap;
import net.minecraft.entity.Entity;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.immutable.entity.ImmutableInvisibilityData;
import org.spongepowered.api.data.manipulator.mutable.entity.InvisibilityData;
import org.spongepowered.common.bridge.data.VanishingBridge;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeInvisibilityData;
import org.spongepowered.common.data.processor.common.AbstractEntityDataProcessor;
import org.spongepowered.common.bridge.entity.EntityBridge;
import org.spongepowered.common.data.processor.common.AbstractMultiDataSingleTargetProcessor;

import java.util.Map;
import java.util.Optional;

public class InvisibilityDataProcessor
        extends AbstractMultiDataSingleTargetProcessor<VanishingBridge, InvisibilityData, ImmutableInvisibilityData> {

    public InvisibilityDataProcessor() {
        super(VanishingBridge.class);
    }

    @Override
    protected InvisibilityData createManipulator() {
        return new SpongeInvisibilityData();
    }

    @Override
    protected boolean doesDataExist(VanishingBridge dataHolder) {
        return true;
    }

    @Override
    protected boolean set(VanishingBridge dataHolder, Map<Key<?>, Object> keyValues) {
        if (dataHolder instanceof Entity && ((Entity) dataHolder).world.isRemote) {
            return false;
        }
        final boolean invis = (Boolean) keyValues.get(Keys.INVISIBLE);
        final boolean collision = (Boolean) keyValues.get(Keys.VANISH_IGNORES_COLLISION);
        final boolean untargetable = (Boolean) keyValues.get(Keys.VANISH_PREVENTS_TARGETING);
        final boolean vanish = (Boolean) keyValues.get(Keys.VANISH);
        dataHolder.vanish$setInvisible(invis);
        if (vanish) {
            dataHolder.vanish$setVanished(true);
            dataHolder.vanish$setUncollideable(collision);
            dataHolder.vanish$setUntargetable(untargetable);
        } else {
            dataHolder.vanish$setVanished(false);
        }
        return true;
    }

    @Override
    protected Map<Key<?>, ?> getValues(VanishingBridge dataHolder) {
        return ImmutableMap.of(Keys.INVISIBLE, dataHolder.vanish$isInvisible(),
                Keys.VANISH, dataHolder.vanish$isVanished(),
                Keys.VANISH_IGNORES_COLLISION, dataHolder.vanish$isUncollideable(),
                Keys.VANISH_PREVENTS_TARGETING, dataHolder.vanish$isUntargetable());
    }

    @Override
    public Optional<InvisibilityData> fill(DataContainer container, InvisibilityData invisibilityData) {
        final boolean vanished = container.getBoolean(Keys.VANISH.getQuery()).orElse(false);
        final boolean invisible = container.getBoolean(Keys.INVISIBLE.getQuery()).orElse(false);
        final boolean collision = container.getBoolean(Keys.VANISH_IGNORES_COLLISION.getQuery()).orElse(false);
        final boolean targeting = container.getBoolean(Keys.VANISH_PREVENTS_TARGETING.getQuery()).orElse(false);
        return Optional.of(invisibilityData
                .set(Keys.VANISH, vanished)
                .set(Keys.INVISIBLE, invisible)
                .set(Keys.VANISH_IGNORES_COLLISION, collision)
                .set(Keys.VANISH_PREVENTS_TARGETING, targeting));
    }

    @Override
    public DataTransactionResult remove(DataHolder dataHolder) {
        return DataTransactionResult.failNoData();
    }
}
