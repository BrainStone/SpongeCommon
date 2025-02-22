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
package org.spongepowered.common.bridge.world;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.world.EnumDifficulty;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.PortalAgentType;
import org.spongepowered.common.config.SpongeConfig;
import org.spongepowered.common.config.type.WorldConfig;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

public interface WorldInfoBridge {

    NBTTagCompound getSpongeRootLevelNbt();

    void setSpongeRootLevelNBT(NBTTagCompound nbt);

    NBTTagCompound getSpongeNbt();

    void readSpongeNbt(NBTTagCompound spongeNbt);

    int getIndexForUniqueId(UUID uuid);

    Optional<UUID> getUniqueIdForIndex(int index);

    @Nullable
    Integer getDimensionId();

    void setDimensionId(int id);

    boolean getIsMod();

    void setIsMod(boolean isMod);

    SpongeConfig<WorldConfig> getConfigAdapter();

    /**
     * Creates the world config.
     *
     * @return True if the config was wrote to disk, false otherwise
     */
    boolean createWorldConfig();

    void setUniqueId(UUID uniqueId);

    void setDimensionType(DimensionType type);

    void setPortalAgentType(PortalAgentType type);

    void setScoreboard(ServerScoreboard scoreboard);

    boolean isValid();

    boolean hasCustomDifficulty();

    /**
     * Sets the difficulty without marking it as custom
     */
    void forceSetDifficulty(EnumDifficulty difficulty);
}
