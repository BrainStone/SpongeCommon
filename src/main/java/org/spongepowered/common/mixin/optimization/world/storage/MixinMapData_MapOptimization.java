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
package org.spongepowered.common.mixin.optimization.world.storage;

import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.Packet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapData;
import net.minecraft.world.storage.MapDecoration;
import net.minecraft.world.storage.WorldSavedData;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.bridge.optimization.OptimizedMapDataBridge;
import org.spongepowered.common.bridge.optimization.OptimizedMapInfoBridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mixin(MapData.class)
public abstract class MixinMapData_MapOptimization extends WorldSavedData implements OptimizedMapDataBridge {


    public MixinMapData_MapOptimization(final String name) {
        super(name);
    }

    @Shadow @Final @Mutable private Map<EntityPlayer, MapData.MapInfo> playersHashMap;
    @Shadow public Map<String, MapDecoration> mapDecorations;
    @Shadow public List<MapData.MapInfo> playersArrayList;

    @Shadow public boolean trackingPosition;


    @Shadow protected abstract void updateDecorations(MapDecoration.Type type, World worldIn, String decorationName, double worldX, double worldZ,
            double rotationIn);

    @Shadow public byte scale;
    @Shadow public int xCenter;
    @Shadow public int zCenter;
    @Shadow public boolean unlimitedTracking;

    private Set<UUID> activeWorlds = new HashSet<>();
    // Used
    private ItemStack dummyItemStack = new ItemStack(Items.FILLED_MAP, 1, this.getMapId());

    private static Constructor<MapData.MapInfo> mapInfoConstructor;
    // Forge changes the type of this field from 'byte' to 'integer'
    // To support both SpongeVanilla and SpongeForge, we use reflection to access it
    private static Field dimensionField;



    static {
        try {
            mapInfoConstructor = MapData.MapInfo.class.getDeclaredConstructor(MapData.class, EntityPlayer.class);
            if (SpongeImplHooks.isDeobfuscatedEnvironment()) {
                dimensionField = MapData.class.getDeclaredField("dimension");
            } else {
                dimensionField = MapData.class.getDeclaredField("field_76200_c");
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private Integer getMapId() {
        return Integer.valueOf(this.mapName.split("map_")[1]);
    }

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void mapOptimization$initPlayerHashmap(final CallbackInfo ci) {
        this.playersHashMap = new LinkedHashMap<>();
    }

    /**
     * @reason This method is absurdly inefficient. We completely replace
     * its funcitonality with bridge$tickMap, which produces identical results with
     * thousands fewer calls to InventoryPlayer#hasItemStack
     * @author Aaron1011 - August 8th, 2018
     */
    @Overwrite
    public void updateVisiblePlayers(final EntityPlayer player, final ItemStack mapStack) {
    }

    /**
     * This method performs the important logic from updateVisiblePlayers, while
     * skipping all of the unecessary InventoryPlayer#hasItemStack checks
     *
     * Before this method is called, all players have their inventories
     * (and therefore map items) ticked by the server. When a map item is ticked,
     * our mixin makes it call MixinMapData#bridge$updatePlayer. This method
     * marks it as valid within MapData, and updates the decorations
     * from the 'Decorations' tag.
     *
     * Inside bridge$tickMap(), we use the flag set by bridge$updatePlayer to
     * determine whether or not to update or remove a player
     * from our map decorations. This eliminates the need
     * to do any InventoryPlayer#hasItemStack calls. If a player's
     * inventory contains a map corresponding to this MapData,
     * the flag will have been updated when it ticked. If the item
     * is removed from the player's inventory, the flag will
     * no longer be set before bridge$tickMap() runs.
     *
     * MixinMapData#bridge$updateItemFrameDecoration is run from
     * MixinEntityTrackerEntry - once per Itemframe, not once
     * per player per itemframe. bridge$updateItemFrameDecoration just
     * updates the frame's position (in case it teleported) in our
     * decorations. We skip running all of the unecessary logic that
     * Vanilla does (it calls updateVisiblePlayers), since that will
     * be handled in bridge$tickMap(), which runs after all entities and items
     * have ticked.
     *
     **/
    @Override
    public void bridge$tickMap() {
        final List<OptimizedMapInfoBridge> mapInfosToUpdate = new ArrayList<>(this.playersHashMap.size());
        try {
            final Iterator<Map.Entry<EntityPlayer, MapData.MapInfo>> it = this.playersHashMap.entrySet().iterator();
            while (it.hasNext()) {
                final Map.Entry<EntityPlayer, MapData.MapInfo> entry = it.next();
                final EntityPlayer player = entry.getKey();
                final MapData.MapInfo mapInfo = entry.getValue();
                final OptimizedMapInfoBridge mixinMapInfo = (OptimizedMapInfoBridge) mapInfo;
                if (player.isDead) {
                    it.remove();
                    continue;
                }

                if (!mixinMapInfo.mapOptimizationBridge$isValid()) {
                    this.mapDecorations.remove(player.getName());
                } else {
                    if (this.trackingPosition && dimensionField.get(this).equals(player.dimension)) {
                        this.updateDecorations(MapDecoration.Type.PLAYER, player.world, player.getName(), player.posX, player.posZ,
                                (double) player.rotationYaw);
                    }
                    // We invalidate the player's map info every tick.
                    // If the map item is still in the player's hand, the MapInfo
                    // will have mapOptimizationBridge$setValid(true) called when the item ticks.
                    // Otherwise, it will remain invalid
                    mapInfosToUpdate.add(mixinMapInfo);
                }
            }

            this.updatePlayersInWorld();

            // We only invalidate the MapInfos after calling updatePlayersInWorld
            // This allows updatePlayersInWorld to skip sending a duplicate packet
            // to players with a valid entry
            for (final OptimizedMapInfoBridge mapInfo: mapInfosToUpdate) {
                mapInfo.mapOptimizationBridge$setValid(false);
            }

        } catch (final Exception e) {
            SpongeImpl.getLogger().error("Exception ticking map data!", e);
        }
    }

    // In EntityPlayerMP#onUpdateEntity, players have map data packets
    // sent to them for each map in their inventory.
    // In this method, we send map data packets to players in the same world
    // as an ItemFrame containing a map. In Vanilla, this is done in EntityTrackerEntry,
    // for every single ItemFrame in the game. This is completely unecessary - we only
    // need to send update packets once per player per unique MapData.

    // To further improve performance, we skip sending map update packets
    // to players who already have the same map in their inventory
    private void updatePlayersInWorld() {
        // Copied from EntityTrackerEntry#updatePlayerList
        if (Sponge.getServer().getRunningTimeTicks() % 10 == 0) {
            for (final org.spongepowered.api.world.World world: Sponge.getServer().getWorlds()) {
                if (!this.activeWorlds.contains(world.getUniqueId())) {
                    continue;
                }
                for (final Player player: world.getPlayers()) {
                    // Copied from EntityTrackerEntry#updatePlayerList

                    final EntityPlayerMP entityplayermp = (EntityPlayerMP) player;
                    OptimizedMapInfoBridge mapInfo = (OptimizedMapInfoBridge) this.playersHashMap.get(player);
                    if (mapInfo != null && mapInfo.mapOptimizationBridge$isValid()) {
                        continue; // We've already sent the player a map data packet for this map
                    }

                    // Create a MapInfo for use by createMapDataPacket
                    if (mapInfo == null) {
                        mapInfo = (OptimizedMapInfoBridge) this.constructMapInfo(entityplayermp);
                        this.playersHashMap.put(entityplayermp, (MapData.MapInfo) mapInfo);
                    }

                    //mapdata.updateVisiblePlayers(entityplayermp, itemstack); - Sponge - this is handled above in bridge$tickMap
                    final Packet<?> packet = Items.FILLED_MAP.createMapDataPacket(this.dummyItemStack, (World) world, entityplayermp);

                    if (packet != null)
                    {
                        entityplayermp.connection.sendPacket(packet);
                    }
                }
            }
        }
    }

    // Use playersHashMap instead of playersArrayList, since we skip updating playersArrayList
    @Redirect(method = "updateMapData", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", remap = false))
    private Iterator<?> mapOptimization$GetIteratorFromPlayerHashMap(final List<?> this$0) {
        return this.playersHashMap.values().iterator();
    }



    // MapInfo is a non-static inner class, so we need to use reflection to call
    // the constructor
    private MapData.MapInfo constructMapInfo(final EntityPlayer player) {
        try {
            return mapInfoConstructor.newInstance(this, player);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to construct MapInfo for player " + player, e);
        }
    }

    @Override
    public void bridge$updatePlayer(final EntityPlayer player, final ItemStack mapStack) {
        MapData.MapInfo info = this.playersHashMap.get(player);
        if (info == null) {
            info = this.constructMapInfo(player);
            this.playersHashMap.put(player, info);
        }
        ((OptimizedMapInfoBridge) info).mapOptimizationBridge$setValid(true);

        if (mapStack.hasTagCompound() && mapStack.getTagCompound().hasKey("Decorations", 9))
        {
            final NBTTagList nbttaglist = mapStack.getTagCompound().getTagList("Decorations", 10);

            for (int j = 0; j < nbttaglist.tagCount(); ++j)
            {
                final NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(j);

                if (!this.mapDecorations.containsKey(nbttagcompound.getString("id")))
                {
                    this.updateDecorations(MapDecoration.Type.byIcon(nbttagcompound.getByte("type")), player.world, nbttagcompound.getString("id"), nbttagcompound.getDouble("x"), nbttagcompound.getDouble("z"), nbttagcompound.getDouble("rot"));
                }
            }
        }
    }

    @Override
    public void bridge$updateItemFrameDecoration(final EntityItemFrame frame) {
        this.activeWorlds.add(((Entity) frame).getWorld().getUniqueId());
        if (this.trackingPosition) {
            final BlockPos blockpos = frame.getHangingPosition();
            if (blockpos == null || frame.facingDirection == null) {
                return;
            }
            this.updateDecorations(MapDecoration.Type.FRAME, frame.world, "frame-" + frame.getEntityId(), (double)blockpos.getX(), (double)blockpos.getZ(), (double)(frame.facingDirection.getHorizontalIndex() * 90));
        }
    }

    @Override
    public void bridge$removeItemFrame(final EntityItemFrame frame) {
        this.activeWorlds.remove(((Entity) frame).getWorld().getUniqueId());
        this.mapDecorations.remove("frame-" + frame.getEntityId());
    }

}
