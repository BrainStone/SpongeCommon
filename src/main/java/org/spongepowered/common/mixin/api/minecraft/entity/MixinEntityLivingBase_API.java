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
package org.spongepowered.common.mixin.api.minecraft.entity;

import static com.google.common.base.Preconditions.checkNotNull;

import com.flowpowered.math.vector.Vector3d;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.CombatTracker;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundEvent;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.mutable.entity.DamageableData;
import org.spongepowered.api.data.manipulator.mutable.entity.HealthData;
import org.spongepowered.api.data.value.mutable.MutableBoundedValue;
import org.spongepowered.api.data.value.mutable.OptionalValue;
import org.spongepowered.api.entity.EntitySnapshot;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.projectile.Projectile;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeDamageableData;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeHealthData;
import org.spongepowered.common.data.value.SpongeValueFactory;
import org.spongepowered.common.data.value.mutable.SpongeOptionalValue;
import org.spongepowered.common.entity.projectile.ProjectileLauncher;
import org.spongepowered.common.mixin.api.minecraft.entity.MixinEntity_API;

import java.util.Optional;
import java.util.Random;

import javax.annotation.Nullable;

@SuppressWarnings("rawtypes")
@NonnullByDefault
@Mixin(value = EntityLivingBase.class, priority = 999)
public abstract class MixinEntityLivingBase_API extends MixinEntity_API implements Living {

    @Shadow @Nullable public EntityLivingBase revengeTarget;
    @Shadow protected float lastDamage;

    @Shadow public abstract void setHealth(float health);
    @Shadow public abstract void setLastAttackedEntity(net.minecraft.entity.Entity entity);
    @Shadow public abstract void setRotationYawHead(float rotation);
    @Shadow public abstract void setRenderYawOffset(float offset);
    @Shadow public abstract void knockBack(net.minecraft.entity.Entity entityIn, float p_70653_2_, double p_70653_3_, double p_70653_5_);
    @Shadow public abstract void shadow$setRevengeTarget(EntityLivingBase livingBase);
    @Shadow public abstract void setAbsorptionAmount(float amount);
    @Shadow public abstract void setHeldItem(EnumHand hand, @Nullable ItemStack stack);
    @Shadow public abstract void setSprinting(boolean sprinting);
    @Shadow public abstract void resetActiveHand();
    @Shadow public abstract int getItemInUseCount();
    @Shadow public abstract float getAbsorptionAmount();
    @Shadow public abstract float getHealth();
    @Shadow public abstract float getMaxHealth();
    @Shadow public abstract float getRotationYawHead();
    @Shadow public abstract boolean isPotionActive(Potion potion);
    @Shadow public abstract boolean isOnLadder();
    @Shadow public abstract boolean canBlockDamageSource(DamageSource p_184583_1_);
    @Shadow public abstract IAttributeInstance getEntityAttribute(IAttribute attribute);
    @Shadow public abstract ItemStack getItemStackFromSlot(EntityEquipmentSlot slotIn);
    @Shadow public abstract ItemStack getHeldItem(EnumHand hand);
    @Shadow public abstract ItemStack getHeldItemMainhand();
    @Shadow public abstract CombatTracker getCombatTracker();
    @Shadow @Nullable public abstract EntityLivingBase getAttackingEntity();
    @Shadow public abstract Random getRNG();
    @Shadow public void onKillCommand() { }
    @Shadow public abstract AbstractAttributeMap getAttributeMap();
    @Shadow public abstract EnumHand getActiveHand();
    @Shadow protected abstract void onDeathUpdate();
    @Shadow protected abstract void markVelocityChanged();
    @Shadow protected abstract void damageShield(float p_184590_1_);
    @Shadow protected abstract void playHurtSound(net.minecraft.util.DamageSource p_184581_1_);
    @Shadow protected abstract void blockUsingShield(EntityLivingBase p_190629_1_);
    @Shadow protected abstract void dropLoot(boolean wasRecentlyHit, int lootingModifier, DamageSource source);
    @Shadow protected abstract int getExperiencePoints(EntityPlayer attackingPlayer);
    @Shadow protected abstract float getSoundVolume();
    @Shadow protected abstract float getSoundPitch();
    @Shadow protected abstract boolean canDropLoot();
    @Shadow protected abstract SoundEvent getDeathSound();
    @Shadow private boolean checkTotemDeathProtection(DamageSource p_190628_1_) {
        return false; // SHADOWED
    }
    @Shadow public abstract boolean attemptTeleport(double x, double y, double z);

    @Override
    public Vector3d getHeadRotation() {
        // pitch, yaw, roll -- Minecraft does not currently support head roll
        return new Vector3d(getRotation().getX(), getRotationYawHead(), 0);
    }

    @Override
    public void setHeadRotation(Vector3d rotation) {
        setRotation(getRotation().mul(0, 1, 1).add(rotation.getX(), 0, 0));
        setRotationYawHead((float) rotation.getY());
        setRenderYawOffset((float) rotation.getY());
    }

    @Override
    public Text getTeamRepresentation() {
        return Text.of(this.getUniqueID().toString());
    }

    @Override
    public HealthData getHealthData() {
        return new SpongeHealthData(this.getHealth(), this.getMaxHealth());
    }

    @Override
    public MutableBoundedValue<Double> health() {
        return SpongeValueFactory.boundedBuilder(Keys.HEALTH)
                .minimum(0D)
                .maximum((double) this.getMaxHealth())
                .defaultValue((double) this.getMaxHealth())
                .actualValue((double) this.getHealth())
                .build();
    }

    @Override
    public MutableBoundedValue<Double> maxHealth() {
        return SpongeValueFactory.boundedBuilder(Keys.MAX_HEALTH)
                .minimum(1D)
                .maximum((double) Float.MAX_VALUE)
                .defaultValue(20D)
                .actualValue((double) this.getMaxHealth())
                .build();
    }

    @Override
    public DamageableData getDamageableData() {
        return new SpongeDamageableData((Living) this.revengeTarget, (double) this.lastDamage);
    }

    @Override
    public OptionalValue<EntitySnapshot> lastAttacker() {
        return new SpongeOptionalValue<>(Keys.LAST_ATTACKER, Optional.empty(), Optional.ofNullable(this.revengeTarget == null ?
                null : ((Living) this.revengeTarget).createSnapshot()));
    }

    @Override
    public OptionalValue<Double> lastDamage() {
        return new SpongeOptionalValue<>(Keys.LAST_DAMAGE, Optional.empty(), Optional.ofNullable(this.revengeTarget == null ?
                null : (double) (this.lastDamage)));
    }

    @Override
    public <T extends Projectile> Optional<T> launchProjectile(Class<T> projectileClass) {
        return ProjectileLauncher.launch(checkNotNull(projectileClass, "projectile class"), this, null);
    }

    @Override
    public <T extends Projectile> Optional<T> launchProjectile(Class<T> projectileClass, Vector3d velocity) {
        return ProjectileLauncher.launch(checkNotNull(projectileClass, "projectile class"), this, checkNotNull(velocity, "velocity"));
    }

    // Start implementation of UseItemstackEvent


}
