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
package org.spongepowered.common.advancement;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import net.minecraft.advancements.ICriterionInstance;
import org.spongepowered.api.advancement.criteria.ScoreAdvancementCriterion;
import org.spongepowered.api.advancement.criteria.trigger.FilteredTrigger;

import javax.annotation.Nullable;

public class SpongeScoreCriterionBuilder implements ScoreAdvancementCriterion.Builder {

    @Nullable private FilteredTrigger trigger;
    private int goal;

    public SpongeScoreCriterionBuilder() {
        reset();
    }

    @Override
    public ScoreAdvancementCriterion.Builder trigger(FilteredTrigger<?> trigger) {
        this.trigger = trigger;
        return this;
    }

    @Override
    public ScoreAdvancementCriterion build(String name) {
        checkNotNull(name, "name");
        return new SpongeScoreCriterion(name, this.goal, (ICriterionInstance) this.trigger);
    }

    @Override
    public ScoreAdvancementCriterion.Builder from(ScoreAdvancementCriterion value) {
        this.goal = value.getGoal();
        return this;
    }

    @Override
    public ScoreAdvancementCriterion.Builder reset() {
        this.goal = 1;
        return this;
    }

    @Override
    public ScoreAdvancementCriterion.Builder goal(int goal) {
        checkState(goal > 0, "The goal must be greater than zero.");
        this.goal = goal;
        return this;
    }
}
