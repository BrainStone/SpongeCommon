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
package org.spongepowered.common.mixin.core.api.text;

import com.google.common.collect.ImmutableList;
import net.minecraft.util.text.TextComponentBase;
import net.minecraft.util.text.TextComponentTranslation;
import org.spongepowered.api.text.TranslatableText;
import org.spongepowered.api.text.translation.Translation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.interfaces.text.IMixinText;

@Mixin(value = TranslatableText.class, remap = false)
public abstract class MixinTextTranslatable_Impl extends MixinText_Impl {

    @Shadow @Final protected Translation translation;
    @Shadow @Final protected ImmutableList<Object> arguments;

    @Override
    protected TextComponentBase createComponent() {
        return new TextComponentTranslation(this.translation.getId(), unwrapArguments(this.arguments));
    }

    private Object[] unwrapArguments(ImmutableList<Object> args) {
        Object[] result = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            final Object arg = args.get(i);
            if (arg instanceof IMixinText) {
                result[i] = ((IMixinText) arg).toComponent();
            } else {
                result[i] = arg;
            }
        }
        return result;
    }

}
