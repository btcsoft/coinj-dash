/**
 * Copyright 2015 BitTechCenter Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coinj.dash;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.EvictionListener;
import com.googlecode.concurrentlinkedhashmap.Weighers;
import org.bitcoinj.core.Hashable;
import org.bitcoinj.core.Sha256Hash;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.Serializable;

/**
 * Date: 5/29/15
 * Time: 11:14 PM
 *
 * @author Mikhail Kulikov
 */
@ThreadSafe
public final class MessagesRegistry<T extends Hashable> implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final class Entry<T extends Hashable> implements Serializable {
        private static final long serialVersionUID = 1L;

        public final T message;

        private Entry(T message) {
            this.message = message;
        }
    }

    private final ConcurrentLinkedHashMap<Sha256Hash, Entry<T>> cache;

    public MessagesRegistry(int initialCapacity, int maxCapacity, int concurrencyLevel, @Nullable EvictionListener<Sha256Hash, Entry<T>> evictionListener) {
        final ConcurrentLinkedHashMap.Builder<Sha256Hash, Entry<T>> builder = new ConcurrentLinkedHashMap.Builder<Sha256Hash, Entry<T>>()
                .concurrencyLevel(concurrencyLevel)
                .initialCapacity(initialCapacity)
                .maximumWeightedCapacity(maxCapacity)
                .weigher(Weighers.entrySingleton());
        if (evictionListener != null) {
            builder.listener(evictionListener);
        }
        cache = builder.build();
    }

    /**
     * Crafted for actual messages.
     * @return true if we've seen this message for the first time.
     */
    public boolean registerMessage(T message) {
        final Entry<T> oldEntry = cache.put(message.getHash(), new Entry<T>(message));
        return oldEntry == null || oldEntry.message == null;
    }

    /**
     * Crafted for INV messages.
     * @return true if we've seen this INV for the first time.
     */
    public boolean registerInv(Sha256Hash hash) {
        return cache.putIfAbsent(hash, new Entry<T>(null)) == null;
    }

    @Nullable
    public T get(Sha256Hash hash) {
        final Entry<T> entry = cache.get(hash);
        return (entry == null)
                ? null
                : entry.message;
    }

}
