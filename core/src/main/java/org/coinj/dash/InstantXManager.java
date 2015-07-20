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

import com.google.common.collect.Lists;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;
import org.coinj.api.CoinDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Date: 5/26/15
 * Time: 1:35 AM
 *
 * @author Mikhail Kulikov
 */
@ThreadSafe
public final class InstantXManager implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(InstantXManager.class);

    /**
     * At 15 signatures, 1/2 of the masternode network can be owned by
     * one party without compromising the security of InstantX
     * (1000/2150.0)**15 = 1.031e-05
     */
    private static final int INSTANTX_SIGNATURES_REQUIRED = 15;
    private static final int INSTANTX_SIGNATURES_TOTAL = 20;

    private static final int MIN_INSTANTX_PROTO_VERSION = 70066;
    private static final int LOCK_LIFETIME = 60 * 60;

    private final ReentrantReadWriteLock lock = Threading.factory.newReentrantReadWriteLock("instantx");
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    private final MasternodeManager masternodeManager;
    private final DashNetwork network;

    @GuardedBy("lock") private long unknownVotesTotal = 0;

    @GuardedBy("lock") private final HashMap<Sha256Hash, Transaction> mapTxLockReq = new HashMap<Sha256Hash, Transaction>(1000);
    @GuardedBy("lock") private final HashMap<Sha256Hash, Long> mapTxLockReqFailed = new HashMap<Sha256Hash, Long>();
    @GuardedBy("lock") private final HashSet<Sha256Hash> internedLocks = new HashSet<Sha256Hash>();
    @GuardedBy("lock") private final HashMap<Sha256Hash, TxLock> mapTxLocks = new HashMap<Sha256Hash, TxLock>(1000);
    @GuardedBy("lock") private final HashMap<TransactionOutPoint, Sha256Hash> mapLockedInputs = new HashMap<TransactionOutPoint, Sha256Hash>(1000);
    @GuardedBy("lock") private final HashMap<Sha256Hash, ConsensusVote> mapTxLockVote = new HashMap<Sha256Hash, ConsensusVote>(1000);
    @GuardedBy("lock") private final LinkedHashMap<Sha256Hash, Long> mapUnknownVotes = new LinkedHashMap<Sha256Hash, Long>(300, 1.0f) {
        private static final long serialVersionUID = 1L;
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, Long> eldest) {
            if (eldest == null)
                return false;
            if (size() >= 299) {
                unknownVotesTotal -= eldest.getValue();
                return true;
            }
            return false;
        }
    };
    @GuardedBy("lock") private final HashMap<Sha256Hash, VotesListener> votesListeners = new HashMap<Sha256Hash, VotesListener>();

    private final CopyOnWriteArrayList<DashWalletCoinSpecifics> registeredWallets = new CopyOnWriteArrayList<DashWalletCoinSpecifics>();

    public interface VotesListener {

        void newPercentage(double percentage);

        void finished();

    }

    InstantXManager(MasternodeManager masternodeManager, DashNetwork network) {
        this.masternodeManager = masternodeManager;
        this.network = network;
    }

    static boolean isTxValidForLocking(Transaction tx, SporkManager sporkManager) {
        final List<TransactionOutput> outputs = tx.getOutputs();

        if (outputs.size() < 1 || tx.getLockTime() != 0)
            return false;

        final CoinDefinition def = tx.getParams().getCoinDefinition();
        Coin totalOut = Coin.zero(def);
        for (final TransactionOutput out : tx.getOutputs()) {
            totalOut = totalOut.add(out.getValue());
        }

        if (totalOut.compareTo(Coin.valueOf((int) sporkManager.getSporkValue(SporkManager.SPORK_5_MAX_VALUE), 0, def)) > 0) {
            logger.debug("isTxValidForLocking - Transaction value too high - {}", tx);
            return false;
        }

        final Coin fee = tx.getFee();
        if (fee == null || fee.compareTo(Coin.valueOf(0, 1, def)) < 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("isTxValidForLocking - did not include enough fees in transaction {}, {}", fee, tx);
            }
            return false;
        }

        return true;
    }

    static boolean isTransactionExpired(DashTransactionExtension transactionExtension) {
        return Utils.currentTimeSeconds() - transactionExtension.getLockingTime() > LOCK_LIFETIME;
    }

    public void registerVotesListener(Sha256Hash txHash, VotesListener listener) {
        writeLock.lock();
        try {
            if (mapTxLocks.containsKey(txHash)) {
                votesListeners.put(txHash, listener);
            }
        } finally {
            writeLock.unlock();
        }
    }

    void registerWallet(DashWalletCoinSpecifics walletCoinSpecifics) {
        registeredWallets.add(walletCoinSpecifics);
    }

    /**
     * Requires "walletSpecs.wallet.lock" to be on.
     * @return true if creation was successful.
     */
    boolean createReceivedLock(Transaction tx, AbstractBlockChain blockChain) {
        final Sha256Hash hashTx = tx.getHash();

        writeLock.lock();
        try {
            final Transaction oldTxReq = mapTxLockReq.get(hashTx);
            if (oldTxReq != null) { // already know
                // check for consensus gathered
                // that will be executed when we received ConsensusVote and already knew about lock request
                final TxLock txLock = mapTxLocks.get(hashTx);
                if (txLock != null && txLock.countSignatures() >= INSTANTX_SIGNATURES_REQUIRED) {
                    tx.getTransactionExtension().setWireStrategy(DashTransactionWireStrategy.INSTANT_X_LOCKED);
                    return true;
                }

                return false;
            }

            final Integer txAge = network.checkTransactionAndGetAge(tx);
            if (txAge == null) {
                return false;
            }

            mapTxLockReq.put(hashTx, tx); // remember after all basic sanity checks

            final long curSec = Utils.currentTimeSeconds();
            if (findAndResolveConflicts(tx)) {
                mapTxLockReqFailed.put(hashTx, curSec + LOCK_LIFETIME);
                return false;
            }

            // Use a block height newer than the input.
            // This prevents attackers from using transaction malleability to predict which masternodes
            // they'll use.
            final int blockHeight = blockChain.getBestChainHeight() - txAge + 4;

            TxLock txLock = mapTxLocks.get(hashTx);
            if (txLock == null) {
                txLock = new TxLock(blockHeight, curSec + LOCK_LIFETIME, hashTx);
                mapTxLocks.put(hashTx, txLock);
                for (final TransactionInput input : tx.getInputs()) {
                    mapLockedInputs.put(input.getOutpoint(), hashTx);
                }
                logger.info("CreateNewLock - New Transaction Lock {} !", hashTx);
            } else {
                txLock.blockHeight = blockHeight;
                if (logger.isDebugEnabled()) {
                    logger.debug("CreateNewLock - Transaction Lock Exists {} !", hashTx);
                }
            }

            // this will be executed when we received ConsensusVotes before actual lock request was known to us,
            // and then when it came to us and after all the checks we are ready to propagate it to the INSTANT_X_LOCKED pool
            if (txLock.countSignatures() >= INSTANTX_SIGNATURES_REQUIRED) {
                tx.getTransactionExtension().setWireStrategy(DashTransactionWireStrategy.INSTANT_X_LOCKED);
            }
        } finally {
            writeLock.unlock();
        }

        return true;
    }

    VotingResult processReceivedVote(ConsensusVote vote, AbstractBlockChain blockChain) {
        final Sha256Hash hashVote = vote.getHash();

        writeLock.lock();
        try {
            final ConsensusVote oldVote = mapTxLockVote.get(hashVote);
            if (oldVote != null) {
                return new VotingResult();
            }
            mapTxLockVote.put(hashVote, vote);

            final TransactionInput inMasternode = vote.getInMasternode();
            final int blockHeight = vote.getBlockHeight();

            final int rank = masternodeManager.getMasternodeRank(inMasternode, blockHeight, blockChain, true);

            if (logger.isDebugEnabled()) {
                final MasternodeData mn = masternodeManager.find(inMasternode);
                logger.debug("processReceivedVote - Masternode ADDR {} {}", mn.getAddr(), rank);
            }

            if (rank < 0) {
                // can be caused by past versions trying to vote with an invalid protocol
                logger.debug("processReceivedVote - Unknown Masternode");
                return new VotingResult();
            }

            if (rank > INSTANTX_SIGNATURES_TOTAL) {
                if (logger.isDebugEnabled()) {
                    logger.debug("processReceivedVote - Masternode not in the top {} ({}) - {}", INSTANTX_SIGNATURES_TOTAL, rank, hashVote);
                }
                return new VotingResult();
            }

            if (!vote.checkSignature(masternodeManager)) {
                logger.info("processReceivedVote - Signature invalid");
                return new VotingResult();
            }

            final Sha256Hash txHash = vote.getTxHash();
            TxLock txLock = mapTxLocks.get(txHash);
            if (txLock == null) {
                logger.info("processReceivedVote - New Transaction Lock {} !", txHash);
                final int curSec = (int) Utils.currentTimeSeconds();
                txLock = new TxLock(0, curSec + 60 * 60, txHash);
                mapTxLocks.put(txHash, txLock);
            } else {
                logger.debug("processReceivedVote - Transaction Lock Exists {} !", txHash);
            }

            txLock.addSignatureAndNotify(vote, votesListeners);
            final int sigCount = txLock.countSignatures();

            logger.debug("processReceivedVote - Transaction Lock Votes {} - {} !", sigCount, hashVote);

            if (sigCount >= INSTANTX_SIGNATURES_REQUIRED) {
                logger.debug("processReceivedVote - Transaction Lock Is Complete {} !", txLock.getHash());

                final Transaction tx = mapTxLockReq.get(txHash);

                final Sha256Hash mnInHash = inMasternode.getOutpoint().getHash();
                Long time = mapUnknownVotes.get(mnInHash);
                if (time == null) {
                    time = initUnknown(mnInHash);
                }

                if (time > Utils.currentTimeSeconds() && time - (unknownVotesTotal / mapUnknownVotes.size()) > 60 * 10) {
                    logger.info("processReceivedVote - masternode is spamming transaction votes: {} {}", inMasternode, txHash);
                    return new VotingResult(tx, false);
                } else {
                    initUnknown(mnInHash);
                }

                return new VotingResult(tx, true);
            }
        } finally {
            writeLock.unlock();
        }

        return new VotingResult();
    }

    @Immutable
    static final class VotingResult {
        final Transaction tx;
        final boolean relayInv;
        final boolean isSuccessful;

        private VotingResult(Transaction tx, boolean relayInv) {
            this.tx = tx;
            this.relayInv = relayInv;
            isSuccessful = true;
        }

        private VotingResult() {
            tx = null;
            relayInv = false;
            isSuccessful = false;
        }

        boolean isVotingCompleted() {
            return tx != null;
        }
    }

    private long initUnknown(Sha256Hash mnInHash) {
        long time = Utils.currentTimeSeconds() + 60 * 10;
        mapUnknownVotes.put(mnInHash, time);
        unknownVotesTotal += time;
        return time;
    }

    /**
     * Requires "walletSpecs.wallet.lock" to be on.
     * @return true if conflicts were found.
     */
    private boolean findAndResolveConflicts(Transaction tx) {
        final Sha256Hash hashTx = tx.getHash();
        boolean conflict = false;
        for (TransactionInput input : tx.getInputs()) {
            final Sha256Hash lockedHash = mapLockedInputs.get(input.getOutpoint());
            if (lockedHash != null && !lockedHash.equals(hashTx)) {
                // we've found lock conflict
                conflict = true;
                for (final DashWalletCoinSpecifics walletSpec : registeredWallets) {
                    network.executeTask(new Runnable() {
                        @Override
                        public void run() {
                            walletSpec.onLockEviction(lockedHash); // this will switch "wallet.lock" on so we'll execute this in background thread to avoid deadlocks
                        }
                    });
                }
            }
        }

        if (conflict) {
            // treat it like regular transaction
            tx.getTransactionExtension().setWireStrategy(DashTransactionWireStrategy.STANDARD);
        }

        return conflict;
    }

    boolean registerLockRequestInv(Sha256Hash hash) {
        writeLock.lock();
        try {
            if (internedLocks.contains(hash))
                return false;
            if (mapTxLockReq.containsKey(hash))
                return false;
            mapTxLockReq.put(hash, null);
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    void internLockRequest(Sha256Hash hash) {
        writeLock.lock();
        try {
            internedLocks.add(hash);
        } finally {
            writeLock.unlock();
        }
    }

    @Nullable
    TransactionLockRequest getKnownLockRequest(Sha256Hash hash) {
        readLock.lock();
        try {
            final Transaction tx = mapTxLockReq.get(hash);
            return tx == null ? null : new TransactionLockRequest(tx);
        } finally {
            readLock.unlock();
        }
    }

    boolean registerConsensusVoteInv(Sha256Hash hash) {
        writeLock.lock();
        try {
            if (mapTxLockVote.containsKey(hash))
                return false;
            mapTxLockVote.put(hash, null);
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    ConsensusVote getKnownConsensusVote(Sha256Hash hash) {
        readLock.lock();
        try {
            return mapTxLockVote.get(hash);
        } finally {
            readLock.unlock();
        }
    }

    void cleanTransactionLocksList(AbstractBlockChain blockChain) {
        final ArrayList<Sha256Hash> hashesToEvict = Lists.newArrayList();
        writeLock.lock();
        try {
            final Iterator<Map.Entry<Sha256Hash, TxLock>> txLocksIter = mapTxLocks.entrySet().iterator();
            while (txLocksIter.hasNext()) {
                final Map.Entry<Sha256Hash, TxLock> next = txLocksIter.next();
                final TxLock txLock = next.getValue();

                final Sha256Hash txHash = txLock.getHash();
                if (Utils.currentTimeSeconds() > txLock.expiration) { // keep 'em for an hour
                    logger.info("Removing old transaction lock {}", txHash);

                    for (int rank = 0; rank <= INSTANTX_SIGNATURES_TOTAL; rank++) {
                        MasternodeData mn = masternodeManager.getMasternodeByRank(rank, txLock.blockHeight, MIN_INSTANTX_PROTO_VERSION, blockChain);
                        if (mn == null)
                            continue;

                        boolean found = false;
                        for (final ConsensusVote vote : txLock.consensusVotes) {
                            if (vote.getInMasternode().equals(mn.getTxIn())) {
                                // masternode responded
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            final MasternodeScanningError scanningError =
                                    new MasternodeScanningError(mn.getTxIn(), MasternodeScanningError.SCANNING_ERROR_IX_NO_RESPONSE, txLock.blockHeight);
                            mn.applyScanningError(scanningError);
                        }
                    }

                    final Transaction tx = mapTxLockReq.remove(txHash);
                    if (tx != null) {
                        for (final TransactionInput input : tx.getInputs()) {
                            mapLockedInputs.remove(input.getOutpoint());
                        }
                    }

                    internedLocks.remove(txHash);

                    for (final ConsensusVote vote : txLock.consensusVotes) {
                        mapTxLockVote.remove(vote.getHash());
                    }

                    hashesToEvict.add(txHash);
                    txLocksIter.remove();
                }
            }

            final Iterator<Map.Entry<Sha256Hash, Long>> failuresIter = mapTxLockReqFailed.entrySet().iterator();
            while (failuresIter.hasNext()) {
                final Map.Entry<Sha256Hash, Long> next = failuresIter.next();
                if (Utils.currentTimeSeconds() > next.getValue()) {
                    final Sha256Hash hash = next.getKey();
                    mapTxLockReq.remove(hash);
                    failuresIter.remove();
                }
            }
        } finally {
            writeLock.unlock();
        }

        // tell wallets that txs are no longer locked
        for (final Sha256Hash txHash : hashesToEvict) {
            for (final DashWalletCoinSpecifics walletSpec : registeredWallets) {
                walletSpec.onLockEviction(txHash);
            }
        }
    }

    TxConflictResult checkTransactionsConflict(List<Transaction> transactions) {
        readLock.lock();
        try {
            for (final Transaction tx : transactions) {
                if (!tx.isCoinBase()) {
                    for (final TransactionInput input : tx.getInputs()) {
                        final Sha256Hash lockedTxHash = mapLockedInputs.get(input.getOutpoint());
                        if (lockedTxHash != null && !lockedTxHash.equals(tx.getHash())) {
                            return new TxConflictResult(tx.getHashAsString(), lockedTxHash.toString());
                        }
                    }
                }
            }
        } finally {
            readLock.unlock();
        }

        return new TxConflictResult();
    }

    CopyOnWriteArrayList<DashWalletCoinSpecifics> getRegisteredWallets() {
        return registeredWallets;
    }

    @Immutable
    static final class TxConflictResult {
        final String conflictedTx;
        final String lockedTx;

        private TxConflictResult(String conflictedTx, String lockedTx) {
            this.conflictedTx = conflictedTx;
            this.lockedTx = lockedTx;
        }
        private TxConflictResult() {
            conflictedTx = null; lockedTx = null;
        }

        boolean isConflicted() {
            return conflictedTx != null;
        }
    }

    /**
     * Date: 5/28/15
     * Time: 5:59 PM
     *
     * @author Mikhail Kulikov
     */
    @NotThreadSafe
    private static final class TxLock implements Hashable {

        private int blockHeight;
        private final Sha256Hash txHash;
        private final List<ConsensusVote> consensusVotes;
        private final long expiration;

        private TxLock(int blockHeight, long expiration, Sha256Hash txHash) {
            this.blockHeight = blockHeight;
            this.expiration = expiration;
            this.txHash = txHash;
            consensusVotes = Lists.newArrayListWithCapacity(INSTANTX_SIGNATURES_TOTAL + 1);
        }

        @Override
        public Sha256Hash getHash() {
            return txHash;
        }

        private void addSignatureAndNotify(ConsensusVote vote, HashMap<Sha256Hash, VotesListener> listeners) {
            consensusVotes.add(vote);

            final VotesListener l = listeners.get(txHash);
            if (l != null) {
                final int size = consensusVotes.size();
                if (size < INSTANTX_SIGNATURES_REQUIRED) {
                    l.newPercentage(((double) size) / INSTANTX_SIGNATURES_REQUIRED * 100.0);
                } else if (size == INSTANTX_SIGNATURES_REQUIRED) {
                    l.finished();
                    listeners.remove(txHash);
                }
            }
        }

        private int countSignatures() {
            // Only count signatures where the BlockHeight matches the transaction's blockheight.
            // The votes have no proof it's the correct blockheight
            if (blockHeight == 0)
                return -1;

            int n = 0;
            for (final ConsensusVote vote : consensusVotes) {
                if (vote.getBlockHeight() == blockHeight) {
                    n++;
                }
            }

            return n;
        }

    }

}
