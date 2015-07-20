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

import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Date: 5/20/15
 * Time: 7:47 PM
 *
 * @author Mikhail Kulikov
 */
@ThreadSafe
public final class MasternodeData implements Serializable, MasternodeStateInfo {

    private static final long serialVersionUID = 1L;

    public static final int MASTERNODE_MIN_CONFIRMATIONS = 15;
    public static final int MASTERNODE_MIN_MNP_SECONDS = 30 * 60;
    public static final int MASTERNODE_MIN_DSEE_SECONDS = 5 * 60;
    public static final int MASTERNODE_REMOVAL_SECONDS = 24 * 60 * 60;
    public static final int MASTERNODE_EXPIRATION_SECONDS = 65 * 60;

    private static final AtomicInteger idSeq = new AtomicInteger(0);
    public static final AtomicBoolean testMode = new AtomicBoolean(false);

    private final TransactionInput txIn;
    private final ECKey pubKey1;

    private final ReentrantReadWriteLock.ReadLock readLock;
    private final ReentrantReadWriteLock.WriteLock writeLock;

    @GuardedBy("readLock/writeLock") private PeerAddress addr;
    @GuardedBy("readLock/writeLock") private ECKey pubKey2;
    @GuardedBy("readLock/writeLock") private byte[] signature;
    @GuardedBy("readLock/writeLock") private State activeState;
    @GuardedBy("readLock/writeLock") private long sigTime; //dsee message times
    @GuardedBy("readLock/writeLock") private long lastMnPing;
    @GuardedBy("readLock/writeLock") private long lastTimeSeen;
    @GuardedBy("readLock/writeLock") private boolean allowFreeTx;
    @GuardedBy("readLock/writeLock") private int protocolVersion;
    @GuardedBy("readLock/writeLock") private byte[] donationAddress;
    @GuardedBy("readLock/writeLock") private int donationPercentage;
    @GuardedBy("readLock/writeLock") private int scanningErrorCount;
    @GuardedBy("readLock/writeLock") private int lastScanningErrorBlockHeight;
    @GuardedBy("readLock/writeLock") private long lastPaid;

    public MasternodeData(MasternodeStateInfo info) {
        final ReentrantReadWriteLock lock = Threading.factory.newReentrantReadWriteLock("masternodeData" + idSeq.incrementAndGet(), false);
        readLock = lock.readLock();
        writeLock = lock.writeLock();

        txIn = info.getTxIn();
        addr = info.getAddr();
        pubKey1 = info.getPubKey1();
        pubKey2 = info.getPubKey2();
        signature = info.getSignature();
        activeState = State.MASTERNODE_ENABLED;
        sigTime = info.getSigTime();
        lastMnPing = 0;
        lastTimeSeen = info.getLastTimeSeen();
        allowFreeTx = true;
        protocolVersion = info.getProtocolVersion();
        donationAddress = info.getDonationAddress();
        donationPercentage = info.getDonationPercentage();
        scanningErrorCount = 0;
        lastScanningErrorBlockHeight = 0;
        lastPaid = info.getLastPaid();
    }

    @Override
    public PeerAddress getAddr() {
        return addr;
    }

    @Override
    public byte[] getDonationAddress() {
        readLock.lock();
        try {
            return donationAddress;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int getDonationPercentage() {
        readLock.lock();
        try {
            return donationPercentage;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long getLastPaid() {
        readLock.lock();
        try {
            return lastPaid;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long getLastTimeSeen() {
        readLock.lock();
        try {
            return lastTimeSeen;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int getProtocolVersion() {
        readLock.lock();
        try {
            return protocolVersion;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public ECKey getPubKey1() {
        return pubKey1;
    }

    @Override
    public ECKey getPubKey2() {
        readLock.lock();
        try {
            return pubKey2;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    @Nullable
    public byte[] getSignature() {
        readLock.lock();
        try {
            return signature;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long getSigTime() {
        readLock.lock();
        try {
            return sigTime;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public TransactionInput getTxIn() {
        return txIn;
    }

    public void reportTxInSpent() {
        writeLock.lock();
        try {
            activeState = State.MASTERNODE_VIN_SPENT;
        } finally {
            writeLock.unlock();
        }
    }

    public long getLastMnPing() {
        readLock.lock();
        try {
            return lastMnPing;
        } finally {
            readLock.unlock();
        }
    }

    public void setLastMnPing(long lastMnPing) {
        writeLock.lock();
        try {
            this.lastMnPing = lastMnPing;
        } finally {
            writeLock.unlock();
        }
    }

    public boolean getAllowFreeTx() {
        readLock.lock();
        try {
            return allowFreeTx;
        } finally {
            readLock.unlock();
        }
    }

    public void setAllowFreeTx(boolean allowFreeTx) {
        writeLock.lock();
        try {
            this.allowFreeTx = allowFreeTx;
        } finally {
            writeLock.unlock();
        }
    }

    public void updateFromNewBroadcast(MasternodeBroadcast mnb) {
        writeLock.lock();
        try {
            pubKey2 = mnb.getPubKey2();
            sigTime = mnb.getSigTime();
            signature = mnb.getSignature();
            protocolVersion = mnb.getProtocolVersion();
            addr = mnb.getAddr();
            donationAddress = mnb.getDonationAddress();
            donationPercentage = mnb.getDonationPercentage();
            lastPaid = mnb.getLastPaid();
        } finally {
            writeLock.unlock();
        }
    }

    public void updateLastSeen() {
        updateLastSeen(0);
    }
    public void updateLastSeen(long override) {
        writeLock.lock();
        try {
            if(override == 0) {
                lastTimeSeen = Utils.currentTimeSeconds();
            } else {
                lastTimeSeen = override;
            }
        } finally {
            writeLock.unlock();
        }
    }

    public boolean updatedWithin(int seconds) {
        readLock.lock();
        try {
            return _updatedWithin(seconds, lastTimeSeen);
        } finally {
            readLock.unlock();
        }
    }
    private static boolean _updatedWithin(int seconds, long lastTimeSeen) {
        return (Utils.currentTimeSeconds() - lastTimeSeen) < seconds;
    }

    public State check(MasternodeManager manager) {
        writeLock.lock();
        try {
            State nextState = State.MASTERNODE_ENABLED;
            if (scanningErrorCount >= MasternodeScanningError.MASTERNODE_SCANNING_ERROR_THRESHOLD) {
                nextState = State.MASTERNODE_POS_ERROR;
            }
            //once spent, stop doing the checks
            else if (activeState.equals(State.MASTERNODE_VIN_SPENT)) {
                return State.MASTERNODE_VIN_SPENT;
            } else if (!_updatedWithin(MASTERNODE_REMOVAL_SECONDS, lastTimeSeen)) {
                nextState = State.MASTERNODE_REMOVE;
            } else if (!_updatedWithin(MASTERNODE_EXPIRATION_SECONDS, lastTimeSeen)) {
                nextState = State.MASTERNODE_EXPIRED;
            } else if (!testMode.get()) {
                if (!CommonUtils.extractNetworkExtension(manager.getParams()).isInputAcceptable(txIn)) {
                    nextState = State.MASTERNODE_VIN_SPENT;
                }
            }

            if (manager != null && protocolVersion >= DashDefinition.DARKSEND_POOL_MIN_PROTOCOL_VERSION) {
                if (!nextState.equals(State.MASTERNODE_ENABLED) && activeState.equals(State.MASTERNODE_ENABLED)) {
                    manager.disableCallback();
                }
                if (nextState.equals(State.MASTERNODE_ENABLED) && !activeState.equals(State.MASTERNODE_ENABLED)) {
                    manager.enableCallback();
                }
            }

            activeState = nextState;
            return nextState;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MasternodeData that = (MasternodeData) o;

        State thatActiveState;
        PeerAddress thatAddr;
        byte[] thatSignature;
        that.readLock.lock();
        try {
            thatActiveState = that.activeState;
            thatAddr = that.addr;
            thatSignature = that.signature;
        } finally {
            that.readLock.unlock();
        }

        readLock.lock();
        try {
            return activeState.equals(thatActiveState)
                    && addr.equals(thatAddr)
                    && Arrays.equals(signature, thatSignature)
                    && txIn.equals(that.txIn);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int hashCode() {
        readLock.lock();
        try {
            int result = txIn.hashCode();
            result = 31 * result + addr.hashCode();
            result = 31 * result + Arrays.hashCode(signature);
            result = 31 * result + activeState.hashCode();
            return result;
        } finally {
            readLock.unlock();
        }
    }

    public boolean isEnabled() {
        readLock.lock();
        try {
            return activeState.equals(State.MASTERNODE_ENABLED);
        } finally {
            readLock.unlock();
        }
    }

    State getActiveState() {
        readLock.lock();
        try {
            return activeState;
        } finally {
            readLock.unlock();
        }
    }

    void applyScanningError(MasternodeScanningError mnse) {
        if (!mnse.isValid())
            return;

        final int mnseHeight = mnse.getBlockHeight();

        writeLock.lock();
        try {
            if (mnseHeight == lastScanningErrorBlockHeight) {
                return;
            }
            lastScanningErrorBlockHeight = mnseHeight;

            if (mnse.getErrorType() == MasternodeScanningError.SCANNING_SUCCESS) {
                scanningErrorCount--;
                if (scanningErrorCount < 0) {
                    scanningErrorCount = 0;
                }
            } else { //all other codes are equally as bad
                scanningErrorCount++;
                final int threshold = MasternodeScanningError.MASTERNODE_SCANNING_ERROR_THRESHOLD * 2;
                if (scanningErrorCount > threshold) {
                    scanningErrorCount = threshold;
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Deterministically calculate a given "score" for a Masternode depending on how close it's hash is to
     * the proof of work for that block. The further away they are the better, the furthest will win the election
     * and get paid this block
     * @param blockHeight block's height
     * @return masternode's "score" for the block
     */
    BigInteger calculateScore(int blockHeight, DashChainExtension chainExtension) {
        if (blockHeight == 0) {
            final StoredBlock chainHead = chainExtension.getBlockChain().getChainHead();
            if (chainHead == null)
                return BigInteger.ZERO;
            blockHeight = chainHead.getHeight() - 1;
        }

        final Sha256Hash hash = chainExtension.getHashByHeight(blockHeight);
        if (hash == null)
            return BigInteger.ZERO;

        final TransactionOutPoint outpoint = getTxIn().getOutpoint();
        final BigInteger aux = outpoint.getHash().toBigInteger()
                .add(BigInteger.valueOf(outpoint.getIndex()));
        final Sha256Hash auxAsHash = new Sha256Hash(aux);

        final byte[] concatenated = new byte[64];
        System.arraycopy(hash.getBytes(), 0, concatenated, 0, 32);
        System.arraycopy(auxAsHash.getBytes(), 0, concatenated, 32, 32);
        final Sha256Hash hash3 = Sha256Hash.createDouble(concatenated);
        final Sha256Hash hash2 = Sha256Hash.createDouble(hash.getBytes());
        final BigInteger int2 = hash2.toBigInteger();
        final BigInteger int3 = hash3.toBigInteger();

        return (hash3.compareTo(hash2) > 0)
                ? int3.subtract(int2)
                : int2.subtract(int3);
    }

}
