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
package org.spongepowered.common.mixin.core.block;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.world.LocatableBlock;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.world.SpongeLocatableBlockBuilder;

import java.util.Random;
import java.util.function.BiConsumer;

@Mixin(BlockDynamicLiquid.class)
public abstract class MixinBlockDynamicLiquid extends MixinBlockLiquid {

    @Override
    public BiConsumer<CauseStackManager.StackFrame, ServerWorldBridge> getTickFrameModifier() {
        return (frame, world) -> frame.addContext(EventContextKeys.LIQUID_FLOW, (World) world);
    }

    @Inject(method = "canFlowInto", at = @At("HEAD"), cancellable = true)
    private void onCanFlowInto(net.minecraft.world.World worldIn, BlockPos pos, IBlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!((WorldBridge) worldIn).isFake() && ShouldFire.CHANGE_BLOCK_EVENT_PRE &&
            SpongeCommonEventFactory.callChangeBlockEventPre((ServerWorldBridge) worldIn, pos).isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "updateTick", at = @At("HEAD"), cancellable = true)
    private void onUpdateTickHead(net.minecraft.world.World worldIn, BlockPos pos, IBlockState state, Random rand, CallbackInfo ci) {
        if (!((WorldBridge) worldIn).isFake() && ShouldFire.CHANGE_BLOCK_EVENT_PRE) {
            if (SpongeCommonEventFactory.callChangeBlockEventPre((ServerWorldBridge) worldIn, pos).isCancelled()) {
                ci.cancel();
            }
        }
    }


    // Capture Lava falling on Water forming Stone
    @Inject(
        method = "updateTick",
        cancellable = true,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z"
        )
    )
    private void settingStoneThrowSpongeEvent(net.minecraft.world.World worldIn, BlockPos sourcePos, IBlockState state, Random rand, CallbackInfo ci) {
        if (!ShouldFire.CHANGE_BLOCK_EVENT_MODIFY) {
            return;
        }
        final BlockPos targetPos = sourcePos.down();
        LocatableBlock source = new SpongeLocatableBlockBuilder().world((World) worldIn).position(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ()).state((BlockState) state).build();
        IBlockState newState = Blocks.STONE.getDefaultState();
        ChangeBlockEvent.Modify event = SpongeCommonEventFactory.callChangeBlockEventModifyLiquidMix(worldIn, targetPos, newState, source);
        Transaction<BlockSnapshot> transaction = event.getTransactions().get(0);
        if (event.isCancelled() || !transaction.isValid()) {
            ci.cancel();
            return;
        }
        if (!worldIn.setBlockState(targetPos, (IBlockState) transaction.getFinal().getState())) {
            ci.cancel();
        }
    }

    // Capture Fluids flowing into other blocks
    @Inject(
        method = "tryFlowInto",
        cancellable = true,
        at = @At(
            value= "INVOKE",
            target = "Lnet/minecraft/block/state/IBlockState;getMaterial()Lnet/minecraft/block/material/Material;"
        ),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/block/BlockDynamicLiquid;canFlowInto(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z"
            ),
            to = @At(
                value = "FIELD",
                target = "Lnet/minecraft/block/BlockDynamicLiquid;material:Lnet/minecraft/block/material/Material;"
            )
        )
    )
    private void afterCanFlowInto(net.minecraft.world.World worldIn, BlockPos pos, IBlockState state, int level, CallbackInfo ci) {
        if (!ShouldFire.CHANGE_BLOCK_EVENT_BREAK) {
            return;
        }
        // Do not call events when just flowing into air or same liquid
        if (state.getMaterial() != Material.AIR && state.getMaterial() != this.shadow$getDefaultState().getMaterial()) {
            IBlockState newState = this.shadow$getDefaultState().withProperty(BlockLiquid.LEVEL, level);
            ChangeBlockEvent.Break event = SpongeCommonEventFactory.callChangeBlockEventModifyLiquidBreak(worldIn, pos, newState);

            Transaction<BlockSnapshot> transaction = event.getTransactions().get(0);
            if (event.isCancelled() || !transaction.isValid()) {
                ci.cancel();
                return;
            }

            // Transaction modified?
            if (transaction.getCustom().isPresent()) {
                worldIn.setBlockState(pos, (IBlockState) transaction.getFinal().getState());
                ci.cancel();
            }
            // else do vanilla logic
        }
    }

}
