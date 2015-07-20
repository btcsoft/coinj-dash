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

import com.googlecode.concurrentlinkedhashmap.EvictionListener;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SporkMessage;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Date: 6/4/15
 * Time: 1:49 PM
 *
 * @author Mikhail Kulikov
 */
@ThreadSafe
public final class SporkManager implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SPORK_2_INSTANTX = 10001;
    public static final int SPORK_3_INSTANTX_BLOCK_FILTERING = 10002;
    public static final int SPORK_5_MAX_VALUE = 10004;
    public static final int SPORK_7_MASTERNODE_SCANNING = 10006;
    public static final int SPORK_8_MASTERNODE_PAYMENTS_ENFORCEMENT = 10007;
    public static final int SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT = 10008;

    private static final int SPORK_2_INSTANTX_DEFAULT = 978307200;                          //2001-1-1
    private static final int SPORK_3_INSTANTX_BLOCK_FILTERING_DEFAULT = 1424217600;         //2015-2-18
    private static final int SPORK_5_MAX_VALUE_DEFAULT = 1000;                              //1000 DASH
    private static final int SPORK_7_MASTERNODE_SCANNING_DEFAULT = 978307200;               //2001-1-1
    private static final int SPORK_8_MASTERNODE_PAYMENTS_ENFORCEMENT_DEFAULT = 1434326400;  //2015-6-15
    private static final int SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT_DEFAULT = 1434326400;    //2015-6-15

    private static final long DEFAULT_SPORK_TIME = 4070908800L;

    private static final Logger logger = LoggerFactory.getLogger(SporkManager.class);

    final MessagesRegistry<SporkMessage> sporkMessagesRegistry =
            new MessagesRegistry<SporkMessage>(10, 100, 4, new EvictionListener<Sha256Hash, MessagesRegistry.Entry<SporkMessage>>() {
        @Override
        public void onEviction(Sha256Hash key, MessagesRegistry.Entry<SporkMessage> value) {
            if (logger.isDebugEnabled()) {
                if (value.message != null) {
                    logger.debug("Forgot about seen spork message - {}:{};{}.", key, value.message.getSporkId(), value.message.getValue());
                } else {
                    logger.debug("Forgot about seen spork message as an INV with hash {}", key);
                }
            }
        }
    });

    private final ReentrantReadWriteLock lock = Threading.factory.newReentrantReadWriteLock("spork");
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    @GuardedBy("lock")
    private final HashMap<Integer, SporkMessage> activeSporks = new HashMap<Integer, SporkMessage>(10, 0.9f);

    private final DashNetwork network;

    SporkManager(DashNetwork network) {
        this.network = network;
    }

    boolean registerSporkMessage(final SporkMessage spork, int tipHeight) {
        if (!sporkMessagesRegistry.registerMessage(spork))
            return false;

        final int sporkId = spork.getSporkId();
        final Sha256Hash hash = spork.getHash();

        readLock.lock();
        try {
            final SporkMessage oldSpork = activeSporks.get(sporkId);
            if (oldSpork != null) {
                if (oldSpork.getTimeSigned() >= spork.getTimeSigned()) {
                    logger.debug("spork - seen {} block {}", hash, tipHeight);
                    return false;
                } else {
                    logger.debug("spork - got updated spork {} block {}", hash, tipHeight);
                }
            }
        } finally {
            readLock.unlock();
        }

        logger.info("spork - new {} ID {} Time {} bestHeight {}", hash, sporkId, spork.getValue(), tipHeight);

        if (!spork.checkSignature(network)) {
            logger.info("spork - invalid signature");
            return false;
        }

        writeLock.lock();
        try {
            activeSporks.put(sporkId, spork);
        } finally {
            writeLock.unlock();
        }

        return true;
    }

    boolean isSporkActive(int idSpork) {
        return _getSporkValue(idSpork) < Utils.currentTimeSeconds();
    }

    long getSporkValue(int idSpork) {
        final long r = _getSporkValue(idSpork);
        return r == DEFAULT_SPORK_TIME
                ? 0L
                : r;
    }

    private long _getSporkValue(int idSpork) {
        SporkMessage spork = null;
        readLock.lock();
        try {
            spork = activeSporks.get(idSpork);
        } finally {
            readLock.unlock();
        }

        final long r;

        if (spork != null) {
            r = spork.getValue();
        } else {
            switch (idSpork) {
                case SPORK_2_INSTANTX:
                    r = SPORK_2_INSTANTX_DEFAULT;
                    break;
                case SPORK_3_INSTANTX_BLOCK_FILTERING:
                    r = SPORK_3_INSTANTX_BLOCK_FILTERING_DEFAULT;
                    break;
                case SPORK_5_MAX_VALUE:
                    r = SPORK_5_MAX_VALUE_DEFAULT;
                    break;
                case SPORK_7_MASTERNODE_SCANNING:
                    r = SPORK_7_MASTERNODE_SCANNING_DEFAULT;
                    break;
                case SPORK_8_MASTERNODE_PAYMENTS_ENFORCEMENT:
                    r = SPORK_8_MASTERNODE_PAYMENTS_ENFORCEMENT_DEFAULT;
                    break;
                case SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT:
                    r = SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT_DEFAULT;
                    break;
                default:
                    logger.info("GetSpork::Unknown Spork " + idSpork);
                    r = DEFAULT_SPORK_TIME; //return 2099-1-1 by default
            }
        }

        return r;
    }

}
