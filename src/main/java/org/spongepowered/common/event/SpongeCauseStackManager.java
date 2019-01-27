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
package org.spongepowered.common.event;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.logging.log4j.Level;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.event.cause.EventContextKey;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.util.ThreadUtil;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

@Singleton
public final class SpongeCauseStackManager implements CauseStackManager {

    public static final boolean DEBUG_CAUSE_FRAMES = Boolean.valueOf(System.getProperty("sponge.debugcauseframes", "false"));
    public static final int MAX_POOL_SIZE;

    static {
        int maxPoolSize = 50;
        try {
            maxPoolSize = Integer.valueOf(System.getProperty("sponge.cause.framePoolSize", "50"));
        } catch (NumberFormatException ex) {
            SpongeImpl.getLogger().warn("sponge.cause.framePoolSize must be an integer, was set to {}. Defaulting to 50.",
                    System.getProperty("sponge.cause.framePoolSize"));
        }
        MAX_POOL_SIZE = Math.max(0, maxPoolSize);
    }

    private final Deque<Object> cause = Queues.newArrayDeque();

    // Frames in use
    private final Deque<CauseStackFrameImpl> frames = Queues.newArrayDeque();

    // Frames not currently in use
    private final Deque<CauseStackFrameImpl> framePool = new ArrayDeque<>(MAX_POOL_SIZE);

    private Map<EventContextKey<?>, Object> ctx = Maps.newHashMap();
    private int min_depth = 0;
    private int[] duplicateCauses = new int[100];
    @Nullable private Cause cached_cause;
    @Nullable private EventContext cached_ctx;
    private boolean pendingProviders = false;
    /**
     * Specifically a Deque because we need to replicate
     * the stack iteration from the bottom of the stack
     * to the top when pushing frames.
     */
    private Deque<Tuple<PhaseContext<?>, BiConsumer<StackFrame, PhaseContext<?>>>> phaseContextProviders = new ArrayDeque<>();

    @Inject
    private SpongeCauseStackManager() {
        for (int i = 0; i < MAX_POOL_SIZE; i++) {
            this.framePool.push(new CauseStackFrameImpl());
        }
    }

    private void enforceMainThread() {
        // On clients, this may not be available immediately, we can't bomb out that early.
        if (Sponge.isServerAvailable() && !isPermittedThread()) {
            throw new IllegalStateException(String.format(
                    "CauseStackManager called from off main thread (current='%s', expected='%s')!",
                    ThreadUtil.getDescription(Thread.currentThread()),
                    ThreadUtil.getDescription(SpongeImpl.getServer().serverThread)
            ));
        }
        if (this.pendingProviders) {
            checkProviders();
        }
    }

    private static boolean isPermittedThread() {
        return SpongeImplHooks.isMainThread() || Thread.currentThread().getName().equals("Server Shutdown Thread");
    }

    @Override
    public Cause getCurrentCause() {
        enforceMainThread();
        if (this.cached_cause == null || this.cached_ctx == null) {
            if (this.cause.isEmpty()) {
                this.cached_cause = Cause.of(getCurrentContext(), SpongeImpl.getGame());
            } else {
                this.cached_cause = Cause.of(getCurrentContext(), this.cause);
            }
        }
        return this.cached_cause;
    }

    private void checkProviders() {
        if (!this.pendingProviders) {
            return; // we've done our work already
        }
        // This is the first time we're calling it, we want to reset it before
        // we have the chance to re-enter and cause frame corruption.
        this.pendingProviders = false;
        // Then, we want to inversely iterate the stack (from bottom to top)
        // to properly mimic as though the frames were created at the time of the
        // phase switches. It does not help the debugging of cause frames
        // except for this method call-point.
        for (Iterator<Tuple<PhaseContext<?>, BiConsumer<StackFrame, PhaseContext<?>>>> iterator = this.phaseContextProviders.descendingIterator(); iterator.hasNext(); ) {
            final Tuple<PhaseContext<?>, BiConsumer<StackFrame, PhaseContext<?>>> tuple = iterator.next();
            final StackFrame frame = pushCauseFrame(); // these should auto close
            tuple.getSecond().accept(frame, tuple.getFirst()); // The frame will be auto closed by the phase context
        }
        // Clear the list since everything is now loaded.
        // PhaseStates will handle automatically closing their frames
        // and then any new phase states that get entered can still be lazily loaded afterwards, while
        // we take advantage of the already made modifications are being tracked by the stack manager
        this.phaseContextProviders.clear();
    }

    @Override
    public EventContext getCurrentContext() {
        enforceMainThread();
        if (this.cached_ctx == null) {
            this.cached_ctx = EventContext.of(this.ctx);
        }
        return this.cached_ctx;
    }

    @Override
    public CauseStackManager pushCause(Object obj) {
        enforceMainThread();
        checkNotNull(obj, "obj");
        this.cached_cause = null;
        if (this.cause.peek() == obj) {
            // We don't want to be pushing duplicate objects
            // to the root and secondary entry of the cause.
            // This avoids some odd corner cases of the phase tracking system pushing
            // objects without being able to definitively say if the object is already pushed
            // without generating cause frames forcibly.
            // BUT, we do want to at least mark the index of the duplicated object for later popping (if some consumer is doing manual push and pops)
            final int dupedIndex = this.cause.size();
            if (this.duplicateCauses.length <= dupedIndex) {
                // Make sure that we have enough space. If not, increase by 50%
                this.duplicateCauses = Arrays.copyOf(this.duplicateCauses, (int) (dupedIndex * 1.5));
            }
            // Increase the value by 1 since we've obviously reached a new duplicate. This is to allow for
            // additional duplicates to be "popped" with proper indexing.
            this.duplicateCauses[dupedIndex] = this.duplicateCauses[dupedIndex] + 1;
            return this;
        }
        this.cause.push(obj);
        return this;
    }

    @Override
    public Object popCause() {
        enforceMainThread();
        final int size = this.cause.size();
        // First, check for duplicate causes. If there are duplicates,
        // we can artificially "pop" by just peeking.
        final int dupeCause = this.duplicateCauses[size];
        if (dupeCause > 0) {
            // Make sure to just decrement the duplicate causes.
            this.duplicateCauses[size] = dupeCause - 1;
            return checkNotNull(this.cause.peek());
        }
        if (size <= this.min_depth) {
            throw new IllegalStateException("Cause stack corruption, tried to pop more objects off than were pushed since last frame (Size was "
                                            + size + " but mid depth is " + this.min_depth + ")");
        }
        this.cached_cause = null;
        return this.cause.pop();
    }

    @Override
    public void popCauses(int n) {
        enforceMainThread();
        for (int i = 0; i < n; i++) {
            popCause();
        }
    }

    @Override
    public Object peekCause() {
        enforceMainThread();
        return this.cause.peek();
    }

    @Override
    public StackFrame pushCauseFrame() {
        enforceMainThread();
        // Ensure duplicate causes will be correctly sized.
        int size = this.cause.size();
        if (this.duplicateCauses.length <= size) {
            this.duplicateCauses = Arrays.copyOf(this.duplicateCauses, (int) (size * 1.5));
        }
        CauseStackFrameImpl frame;
        if (this.framePool.isEmpty()) {
            frame = new CauseStackFrameImpl().set(this.min_depth, this.duplicateCauses[size]);
        } else {
            frame = this.framePool.pop();
            frame.old_min_depth = min_depth;
            frame.lastCauseSize = this.duplicateCauses[size];
        }

        this.frames.push(frame);
        this.min_depth = size;
        if (DEBUG_CAUSE_FRAMES) {
            // Attach an exception to the frame so that if there is any frame
            // corruption we can print out the stack trace of when the frames
            // were created.
            frame.stack_debug = new Exception();
        }
        return frame;
    }

    @Override
    public void popCauseFrame(StackFrame oldFrame) {
        enforceMainThread();
        checkNotNull(oldFrame, "oldFrame");
        CauseStackFrameImpl frame = this.frames.peek();
        if (frame != oldFrame) {
            // If the given frame is not the top frame then some form of
            // corruption of the stack has occurred and we do our best to correct
            // it.

            // If the target frame is still in the stack then we can pop frames
            // off the stack until we reach it, otherwise we have no choice but
            // to simply throw an error.
            int offset = -1;
            int i = 0;
            for (CauseStackFrameImpl f : this.frames) {
                if (f == oldFrame) {
                    offset = i;
                    break;
                }
                i++;
            }
            if (!DEBUG_CAUSE_FRAMES && offset == -1) {
                // if we're not debugging the cause frames then throw an error
                // immediately otherwise let the pretty printer output the frame
                // that was erroneously popped.
                throw new IllegalStateException("Cause Stack Frame Corruption! Attempted to pop a frame that was not on the stack.");
            }
            final PrettyPrinter printer = new PrettyPrinter(100).add("Cause Stack Frame Corruption!").centre().hr()
                .add("Found %n frames left on the stack. Clearing them all.", new Object[]{offset + 1});
            if (!DEBUG_CAUSE_FRAMES) {
                printer.add()
                    .add("Please add -Dsponge.debugcauseframes=true to your startup flags to enable further debugging output.");
                SpongeImpl.getLogger().warn("  Add -Dsponge.debugcauseframes to your startup flags to enable further debugging output.");
            } else {
                printer.add()
                    .add("Attempting to pop frame:")
                    .add(frame.stack_debug)
                    .add()
                    .add("Frames being popped are:")
                    .add(((CauseStackFrameImpl) oldFrame).stack_debug);
            }

            while (offset >= 0) {
                CauseStackFrameImpl f = this.frames.peek();
                if (DEBUG_CAUSE_FRAMES && offset > 0) {
                    printer.add("   Stack frame in position %n :", offset);
                    printer.add(f.stack_debug);
                }
                popCauseFrame(f);
                offset--;
            }
            printer.trace(System.err, SpongeImpl.getLogger(), Level.ERROR);
            if (offset == -1) {
                // Popping a frame that was not on the stack is not recoverable
                // so we throw an exception.
                throw new IllegalStateException("Cause Stack Frame Corruption! Attempted to pop a frame that was not on the stack.");
            }
            return;
        }
        this.frames.pop();

        // Remove new values
        for (Map.Entry<EventContextKey<?>, Object> entry : frame.getOriginalContextDelta().entrySet()) {
            this.cached_ctx = null;
            if (entry.getValue() == null) { // wasn't present before, remove
                this.ctx.remove(entry.getKey());
            } else { // was there, replace
                this.ctx.put(entry.getKey(), entry.getValue());
            }
        }

        // If there were any objects left on the stack then we pop them off
        while (this.cause.size() > this.min_depth) {
            int index = this.cause.size();

            // Then, only pop the potential duplicate causes (if any) if and only if
            // there was a duplicate cause pushed prior to the frame being popped.
            if (this.duplicateCauses.length > index) {
                // At this point, we now need to "clean" the duplicate causes array of duplicates
                // to avoid potentially pruning earlier frame's potentially duplicate causes.
                // And of course, reset the number of duplicates in the entry.
                this.duplicateCauses[index] = 0;
            }
            this.cause.pop();

            // and clear the cached causes
            this.cached_cause = null;
        }
        this.min_depth = frame.old_min_depth;
        int size = this.cause.size();
        if (this.duplicateCauses.length > size) {
            // Then set the last cause index to whatever the size of the entry was at the time.
            this.duplicateCauses[size] = frame.lastCauseSize;
        }

        // finally, return the frame to the pool
        if (this.framePool.size() < MAX_POOL_SIZE) {
            // cache it, but also call clear so we remove references to
            // other objects that may go out of scope
            frame.clear();
            this.framePool.push(frame);
        }
    }

    @Override
    public <T> CauseStackManager addContext(EventContextKey<T> key, T value) {
        enforceMainThread();
        checkNotNull(key, "key");
        checkNotNull(value, "value");
        this.cached_ctx = null;
        Object existing = this.ctx.put(key, value);
        if (!this.frames.isEmpty()) {
            this.frames.peek().storeOriginalContext(key, existing);
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getContext(EventContextKey<T> key) {
        enforceMainThread();
        checkNotNull(key, "key");
        return Optional.ofNullable((T) this.ctx.get(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> removeContext(EventContextKey<T> key) {
        enforceMainThread();
        checkNotNull(key, "key");
        this.cached_ctx = null;
        Object existing = this.ctx.remove(key);
        if (!this.frames.isEmpty()) {
            this.frames.peek().storeOriginalContext(key, existing);
        }
        return Optional.ofNullable((T) existing);
    }

    public int registerPhaseContextProvider(PhaseContext<?> context, BiConsumer<StackFrame, PhaseContext<?>> consumer) {
        checkNotNull(consumer, "Consumer");
        // Reset our cached objects
        this.pendingProviders = true; //I Reset the cache
        this.cached_cause = null; // Reset the cache
        this.cached_ctx = null; // Reset the cache
        // Since we cannot rely on the PhaseStack being tied to this stack of providers,
        // we have to make the tuple to tie the phase context to provide the consumer.
        this.phaseContextProviders.push(Tuple.of(context, consumer));
        return this.phaseContextProviders.size();
    }

    public void popFrameMutator(PhaseContext<?> context) {
        final Tuple<PhaseContext<?>, BiConsumer<StackFrame, PhaseContext<?>>> peek = this.phaseContextProviders.peek();
        if (peek == null) {
            return;
        }
        if (peek.getFirst() != context) {
            // there's an exception to be thrown or printed out at least, basically a copy of popFrame.
            System.err.println("oops. corrupted phase context providers!");
        }
        this.phaseContextProviders.pop();
        if (this.phaseContextProviders.isEmpty()) {
            // if we're empty, we don't need to bother with the context providers
            // because there's nothing to push.
            this.pendingProviders = false;
        }

    }

    public static class CauseStackFrameImpl implements StackFrame {

        private final Map<EventContextKey<?>, Object> stored_ctx_values = new HashMap<>();
        int old_min_depth;
        int lastCauseSize;
        private final Map<EventContextKey<?>, Object> storedContext = new HashMap<>();

        @Nullable Exception stack_debug = null;

        // for pooling
        CauseStackFrameImpl() {}

        public void clear() {
            this.stored_ctx_values.clear();
            this.storedContext.clear();
            this.lastCauseSize = -1;
            this.old_min_depth = -1;
            this.stack_debug = null;
        }

        // used in chaining.
        public CauseStackFrameImpl set(int old_depth, int size) {
            this.old_min_depth = old_depth;
            this.lastCauseSize = size;
            return this;
        }

        public boolean isStored(EventContextKey<?> key) {
            return this.stored_ctx_values != null && this.stored_ctx_values.containsKey(key);
        }

        public Set<Map.Entry<EventContextKey<?>, Object>> getStoredValues() {
            return this.stored_ctx_values.entrySet();
        }

        public boolean hasStoredValues() {
            return !this.stored_ctx_values.isEmpty();
        }

        public void store(EventContextKey<?> key, Object existing) {
            this.stored_ctx_values.put(key, existing);
        }

        // Note that a null object indicates that the context should be removed
        void storeOriginalContext(EventContextKey<?> key, @Nullable Object object) {
            if (!this.storedContext.containsKey(key)) {
                this.storedContext.put(key, object);
            }
        }

        Map<EventContextKey<?>, Object> getOriginalContextDelta() {
            return this.storedContext;
        }

        @Override
        public Cause getCurrentCause() {
            return Sponge.getCauseStackManager().getCurrentCause();
        }

        @Override
        public EventContext getCurrentContext() {
            return Sponge.getCauseStackManager().getCurrentContext();
        }

        @Override
        public StackFrame pushCause(Object obj) {
            Sponge.getCauseStackManager().pushCause(obj);
            return this;
        }

        @Override
        public Object popCause() {
            return Sponge.getCauseStackManager().popCause();
        }

        @Override
        public <T> StackFrame addContext(EventContextKey<T> key, T value) {
            Sponge.getCauseStackManager().addContext(key, value);
            return this;
        }

        @Override
        public <T> Optional<T> removeContext(EventContextKey<T> key) {
            return Sponge.getCauseStackManager().removeContext(key);
        }

        @Override
        public void close() {
            Sponge.getCauseStackManager().popCauseFrame(this);
        }

    }

}
