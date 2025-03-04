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
package org.spongepowered.common.mixin.core.scoreboard;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.text.TextFormatting;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.format.TextColor;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.bridge.scoreboard.TeamBridge;
import org.spongepowered.common.registry.type.text.TextColorRegistryModule;
import org.spongepowered.common.text.SpongeTexts;
import org.spongepowered.common.text.format.SpongeTextColor;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(ScorePlayerTeam.class)
public abstract class MixinScorePlayerTeam extends net.minecraft.scoreboard.Team implements TeamBridge {

    @Shadow @Nullable public Scoreboard scoreboard;
    @Shadow public String name;
    @Shadow public Set<String> membershipSet;
    @Shadow public String displayName;
    @Shadow public TextFormatting color;
    @Shadow public String prefix;
    @Shadow public String suffix;
    @Shadow public boolean allowFriendlyFire;
    @Shadow public boolean canSeeFriendlyInvisibles;
    @Shadow public net.minecraft.scoreboard.Team.EnumVisible nameTagVisibility;
    @Shadow public net.minecraft.scoreboard.Team.EnumVisible deathMessageVisibility;
    @Shadow public net.minecraft.scoreboard.Team.CollisionRule collisionRule;

    @Shadow public abstract void setAllowFriendlyFire(boolean friendlyFire);

    @SuppressWarnings("NullableProblems") @MonotonicNonNull private Text bridge$displayName;
    @SuppressWarnings("NullableProblems") @MonotonicNonNull private Text bridge$Prefix;
    @SuppressWarnings("NullableProblems") @MonotonicNonNull private Text bridge$Suffix;
    @SuppressWarnings("NullableProblems") @MonotonicNonNull private TextColor bridge$Color;

    // Minecraft doesn't do a null check on scoreboard, so we redirect
    // the call and do it ourselves.
    private void impl$doTeamUpdate() {
        if (this.scoreboard != null) {
            this.scoreboard.broadcastTeamInfoUpdate((ScorePlayerTeam) (Object) this);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void impl$setUpDisplayNames(final Scoreboard scoreboardIn, final String name, final CallbackInfo ci) {
        this.bridge$displayName = SpongeTexts.fromLegacy(name);
        this.bridge$Prefix = SpongeTexts.fromLegacy(this.prefix);
        this.bridge$Suffix = SpongeTexts.fromLegacy(this.suffix);
        this.bridge$Color = TextColorRegistryModule.enumChatColor.get(this.color);
    }

    @Override
    public Text bridge$getDisplayName() {
        return this.bridge$displayName;
    }

    @Override
    public void bridge$setDisplayName(final Text text) {
        final String newText = SpongeTexts.toLegacy(text);
        if (newText.length() > 32) {
            throw new IllegalArgumentException(String.format("Display name is %s characters long! It must be at most 32.", newText.length()));
        }
        this.bridge$displayName = text;
        this.displayName = newText;
        this.impl$doTeamUpdate();
    }

    @Override
    public Text bridge$getPrefix() {
        return this.bridge$Prefix;
    }

    @Override
    public void bridge$setPrefix(final Text text) {
        final String newPrefix = SpongeTexts.toLegacy(text);
        if (newPrefix.length() > 16) {
            throw new IllegalArgumentException(String.format("Prefix is %s characters long! It must be at most 16.", newPrefix.length()));
        }
        this.bridge$Prefix = text;
        this.prefix = newPrefix;
        this.impl$doTeamUpdate();
    }

    @Override
    public Text bridge$getSuffix() {
        return this.bridge$Suffix;
    }

    @Override
    public void bridge$setSuffix(final Text suffix) {
        final String newSuffix = SpongeTexts.toLegacy(suffix);
        if (newSuffix.length() > 16) {
            throw new IllegalArgumentException(String.format("Suffix is %s characters long! It must be at most 16.", newSuffix.length()));
        }
        this.bridge$Suffix = suffix;
        this.suffix = newSuffix;
        this.impl$doTeamUpdate();
    }

    @Override
    public TextColor bridge$getColor() {
        return this.bridge$Color;
    }

    @Override
    public void bridge$setColor(TextColor color) {
        if (color.equals(TextColors.NONE)) {
            color = TextColors.RESET;
        }
        this.bridge$Color = color;
        this.color = ((SpongeTextColor) color).getHandle();
        this.impl$doTeamUpdate();
    }


    @Redirect(method = "setDisplayName",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/scoreboard/Scoreboard;broadcastTeamInfoUpdate(Lnet/minecraft/scoreboard/ScorePlayerTeam;)V"))
    private void onSetTeamName(final Scoreboard scoreboard, final ScorePlayerTeam team, final String name) {
        this.bridge$displayName = SpongeTexts.fromLegacy(name);
        this.impl$doTeamUpdate();
    }

    @Redirect(method = "setPrefix",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/scoreboard/Scoreboard;broadcastTeamInfoUpdate(Lnet/minecraft/scoreboard/ScorePlayerTeam;)V"))
    private void onSetNamePrefix(final Scoreboard scoreboard, final ScorePlayerTeam team, final String prefix) {
        this.bridge$Prefix = SpongeTexts.fromLegacy(prefix);
        this.impl$doTeamUpdate();
    }


    @Redirect(method = "setSuffix",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/scoreboard/Scoreboard;broadcastTeamInfoUpdate(Lnet/minecraft/scoreboard/ScorePlayerTeam;)V"))
    private void onSetNameSuffix(final Scoreboard scoreboard, final ScorePlayerTeam team, final String suffix) {
        this.bridge$Suffix = SpongeTexts.fromLegacy(suffix);
        this.impl$doTeamUpdate();
    }

    @Redirect(method = "setAllowFriendlyFire",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/scoreboard/Scoreboard;broadcastTeamInfoUpdate(Lnet/minecraft/scoreboard/ScorePlayerTeam;)V"))
    private void onSetAllowFriendlyFire(final Scoreboard scoreboard, final ScorePlayerTeam team) {
        this.impl$doTeamUpdate();
    }

    @Redirect(method = "setSeeFriendlyInvisiblesEnabled",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/scoreboard/Scoreboard;broadcastTeamInfoUpdate(Lnet/minecraft/scoreboard/ScorePlayerTeam;)V"))
    private void onSetSeeFriendlyInvisiblesEnabled(final Scoreboard scoreboard, final ScorePlayerTeam team) {
        this.impl$doTeamUpdate();
    }

    @Redirect(method = "setNameTagVisibility",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/scoreboard/Scoreboard;broadcastTeamInfoUpdate(Lnet/minecraft/scoreboard/ScorePlayerTeam;)V"))
    private void onSetNameTagVisibility(final Scoreboard scoreboard, final ScorePlayerTeam team) {
        this.impl$doTeamUpdate();
    }

    @Redirect(method = "setDeathMessageVisibility",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/scoreboard/Scoreboard;broadcastTeamInfoUpdate(Lnet/minecraft/scoreboard/ScorePlayerTeam;)V"))
    private void onSetDeathMessageVisibility(final Scoreboard scoreboard, final ScorePlayerTeam team) {
        this.impl$doTeamUpdate();
    }

    @Inject(method = "setColor", at = @At("RETURN"))
    private void onSetChatFormat(final TextFormatting format, final CallbackInfo ci) {
        this.bridge$Color = TextColorRegistryModule.enumChatColor.get(format);
        // This isn't called by Vanilla, so we inject the call ourselves.
        this.impl$doTeamUpdate();
    }

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    @Override
    public MessageChannel bridge$getTeamChannel(final EntityPlayerMP player) {
        return MessageChannel.fixed(this.getMembershipCollection().stream()
                .map(name -> Sponge.getGame().getServer().getPlayer(name))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(member -> member != player)
                .collect(Collectors.toSet()));
    }

    @Override
    public MessageChannel bridge$getNonTeamChannel() {
        return MessageChannel.fixed(Sponge.getGame().getServer().getOnlinePlayers().stream()
                .filter(player -> ((EntityPlayerMP) player).getTeam() != this)
                .collect(Collectors.toSet()));
    }
}
