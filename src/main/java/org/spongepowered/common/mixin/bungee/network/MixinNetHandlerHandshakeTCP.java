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
package org.spongepowered.common.mixin.bungee.network;

import com.google.gson.Gson;
import com.mojang.authlib.properties.Property;
import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.server.SPacketDisconnect;
import net.minecraft.server.network.NetHandlerHandshakeTCP;
import net.minecraft.util.text.TextComponentString;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.interfaces.IMixinNetworkManager;

import java.net.InetSocketAddress;

@Mixin(NetHandlerHandshakeTCP.class)
public abstract class MixinNetHandlerHandshakeTCP {

    private static final Gson gson = new Gson();

    @Shadow @Final private NetworkManager networkManager;

    @Inject(method = "processHandshake", at = @At(value = "HEAD"), cancellable = true)
    public void onProcessHandshakeStart(C00Handshake packetIn, CallbackInfo ci) {
        if (SpongeImpl.getGlobalConfigAdapter().getConfig().getBungeeCord().getIpForwarding() && packetIn.getRequestedState().equals(EnumConnectionState.LOGIN)) {
            String[] split = packetIn.ip.split("\00\\|", 2)[0].split("\00"); // ignore any extra data

            if (split.length == 3 || split.length == 4) {
                packetIn.ip = split[0];
                ((IMixinNetworkManager) this.networkManager).setRemoteAddress(new InetSocketAddress(split[1],
                        ((InetSocketAddress) this.networkManager.getRemoteAddress()).getPort()));
                ((IMixinNetworkManager) this.networkManager).setSpoofedUUID(UUIDTypeAdapter.fromString(split[2]));

                if (split.length == 4) {
                    ((IMixinNetworkManager) this.networkManager).setSpoofedProfile(gson.fromJson(split[3], Property[].class));
                }
            } else {
                TextComponentString chatcomponenttext =
                        new TextComponentString("If you wish to use IP forwarding, please enable it in your BungeeCord config as well!");
                this.networkManager.sendPacket(new SPacketDisconnect(chatcomponenttext));
                this.networkManager.closeChannel(chatcomponenttext);
            }
        }
    }
}
