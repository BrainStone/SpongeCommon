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
package org.spongepowered.common.bridge.data;

import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.merge.MergeFunction;
import org.spongepowered.api.data.value.BaseValue;
import org.spongepowered.common.data.util.DataUtil;
import org.spongepowered.common.util.Constants;

import java.util.List;
import java.util.Optional;

public interface CustomDataHolderBridge {

    DataTransactionResult offerCustom(DataManipulator<?, ?> manipulator, MergeFunction function);

    <T extends DataManipulator<?, ?>> Optional<T> getCustom(Class<T> customClass);

    DataTransactionResult removeCustom(Class<? extends DataManipulator<?, ?>> customClass);

    boolean hasManipulators();

    boolean supportsCustom(Key<?> key);

    <E> Optional<E> getCustom(Key<? extends BaseValue<E>> key);

    <E, V extends BaseValue<E>> Optional<V> getCustomValue(Key<V> key);

    List<DataManipulator<?, ?>> getCustomManipulators();

    <E> DataTransactionResult offerCustom(Key<? extends BaseValue<E>> key, E value);

    DataTransactionResult removeCustom(Key<?> key);

    default void removeCustomFromNbt(DataManipulator<?, ?> manipulator) {
        if (this instanceof DataCompoundHolder) {
            final NBTTagCompound spongeData = ((DataCompoundHolder) this).data$getSpongeCompound();
            if (spongeData.hasKey(Constants.Sponge.CUSTOM_MANIPULATOR_TAG_LIST, Constants.NBT.TAG_LIST)) {
                final NBTTagList tagList = spongeData.getTagList(Constants.Sponge.CUSTOM_MANIPULATOR_TAG_LIST, Constants.NBT.TAG_COMPOUND);
                if (!tagList.isEmpty()) {
                    String id = DataUtil.getRegistrationFor(manipulator).getId();
                    for (int i = 0; i < tagList.tagCount(); i++) {
                        final NBTTagCompound tag = tagList.getCompoundTagAt(i);
                        if (id.equals(tag.getString(Constants.Sponge.MANIPULATOR_ID))) {
                            tagList.removeTag(i);
                            break;
                        }
                        final String dataClass = tag.getString(Constants.Sponge.CUSTOM_DATA_CLASS);
                        if (dataClass.equalsIgnoreCase(manipulator.getClass().getName())) {
                            tagList.removeTag(i);
                            break;
                        }
                    }
                }
            }
        }
    }

    void addFailedData(ImmutableList<DataView> failedData);
    List<DataView> getFailedData();
}
