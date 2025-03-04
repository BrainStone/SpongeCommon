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
package org.spongepowered.common.bridge.entity.player;

import com.flowpowered.math.vector.Vector3d;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.api.data.type.SkinPart;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.scoreboard.Scoreboard;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.world.border.PlayerOwnBorderListener;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

public interface ServerPlayerEntityBridge extends PlayerEntityBridge {

    default boolean bridge$usesCustomClient() {
        return false;
    }

    int bridge$getViewDistance();

    Optional<User> getBackingUser();

    User getUserObject();

    void forceRecreateUser();

    void bridge$setVelocityOverride(@Nullable Vector3d velocity);

    void sendBlockChange(BlockPos pos, IBlockState state);

    MessageChannel getDeathMessageChannel();

    void initScoreboard();

    void removeScoreboardOnRespawn();

    void setScoreboardOnRespawn(Scoreboard scoreboard);

    ServerWorldBridge getMixinWorld();

    void refreshXpHealthAndFood();

    void bridge$restorePacketItem(EnumHand hand);

    void bridge$setPacketItem(ItemStack itemstack);

    void refreshExp();

    PlayerOwnBorderListener getWorldBorderListener();

    void setHealthScale(double scale);

    double getHealthScale();

    float getInternalScaledHealth();

    boolean isHealthScaled();

    void setHealthScaled(boolean scaled);

    void refreshScaledHealth();

    void injectScaledHealth(Collection<IAttributeInstance> set, boolean b);

    void updateDataManagerForScaledHealth();

    boolean hasForcedGamemodeOverridePermission();

    void setContainerDisplay(Text displayName);

    void setDelegateAfterRespawn(EntityPlayerMP delegate);

    Scoreboard bridge$getScoreboard();

    void bridge$replaceScoreboard(@Nullable Scoreboard scoreboard);

    Set<SkinPart> bridge$getSkinParts();

    User bridge$getUser();
    boolean bridge$hasDelegate();

    @Nullable
    EntityPlayerMP bridge$getDelegate();

    Vector3d bridge$getVelocityOverride();
}
