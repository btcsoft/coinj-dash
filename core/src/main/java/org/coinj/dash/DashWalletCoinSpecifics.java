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

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.WalletTransaction;
import org.coinj.api.TransactionWireStrategy;
import org.coinj.api.WalletCoinSpecifics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;

/**
* Date: 5/17/15
* Time: 1:35 PM
*
* @author Mikhail Kulikov
*/
@ThreadSafe
public final class DashWalletCoinSpecifics implements WalletCoinSpecifics {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(DashWalletCoinSpecifics.class);

    private final Wallet wallet;

    public static final WalletTransaction.Pool INSTANTX_PENDING = WalletTransaction.Pool.createExtension();
    public static final WalletTransaction.Pool INSTANTX_LOCKED = WalletTransaction.Pool.createExtension();

    @GuardedBy("wallet.lock") private final Map<Sha256Hash, Transaction> instantXPending;
    @GuardedBy("wallet.lock") private final Map<Sha256Hash, Transaction> instantXLocked;

    private final DashNetwork network;

    DashWalletCoinSpecifics(Wallet wallet, DashNetwork network) {
        this.wallet = wallet;
        instantXPending = new HashMap<Sha256Hash, Transaction>();
        instantXLocked = new HashMap<Sha256Hash, Transaction>();
        this.network = network;
    }

    @Override
    public void addAllTransactionsFromPools(Set<Transaction> set) {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        set.addAll(instantXPending.values());
        set.addAll(instantXLocked.values());
    }

    @Nullable
    @Override
    public Map<Sha256Hash, Transaction> getExtendedTransactionPool(WalletTransaction.Pool pool) {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        if (pool == INSTANTX_PENDING) {
            return instantXPending;
        } else if (pool == INSTANTX_LOCKED) {
            return instantXLocked;
        } else {
            return null;
        }
    }

    @Override
    public ImmutableList<Map<Sha256Hash, Transaction>> getExtendedTransactionPools() {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        return ImmutableList.of(instantXPending, instantXLocked);
    }

    @Override
    public ImmutableList<Map<Sha256Hash, Transaction>> getSpendableExtendedPools() {
        return getExtendedTransactionPools();
    }

    @Override
    public ImmutableList<Map<Sha256Hash, Transaction>> getPendingExtendedPools() {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        return ImmutableList.of(instantXPending);
    }

    @Nullable
    @Override
    public Transaction putTransactionIntoPool(WalletTransaction.Pool pool, Sha256Hash key, Transaction value) throws IllegalArgumentException {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        final DashTransactionExtension txExtension = CommonUtils.extractTransactionExtension(value);
        final DashTransactionWireStrategy wireStrategy = txExtension.getWireStrategy();
        if (wireStrategy == DashTransactionWireStrategy.INSTANT_X_LOCKED || wireStrategy == DashTransactionWireStrategy.INSTANT_X) {
            if (InstantXManager.isTransactionExpired(txExtension)) {
                logger.warn("Wallet coin specifics transaction pool put with wire strategy {}, but wire lock expired;");
                logger.warn("that can happen if we load a wallet from file;");
                logger.warn("we'll put it in a wallet as PENDING for now and will wait for discovery of a block which contains it.");
                wallet.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.PENDING, value));
                return null;
            }

            if (pool == INSTANTX_PENDING) {
                return instantXPending.put(key, value);
            } else if (pool == INSTANTX_LOCKED) {
                if (instantXPending.remove(key) != null) {
                    logger.info("<-instantx locked: {}", value.getHashAsString());
                }
                return instantXLocked.put(key, value);
            } else {
                throw new IllegalArgumentException("Pool " + pool + " unsupported");
            }
        } else {
            logger.warn("Wallet coin specifics transaction pool put, but transactions wire strategy is " + wireStrategy);
            wallet.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.PENDING, value));
            return null;
        }
    }

    @Override
    public void removeTransactionFromAllPools(Sha256Hash key) {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        if (instantXPending.remove(key) == null) {
            instantXLocked.remove(key);
        }
    }

    @Override
    public void addPoolsToSetIfContainKey(Set<WalletTransaction.Pool> set, Sha256Hash key) {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        if (instantXPending.containsKey(key))
            set.add(INSTANTX_PENDING);
        if (instantXLocked.containsKey(key))
            set.add(INSTANTX_LOCKED);
    }

    @Nullable
    @Override
    public Integer getPoolSize(WalletTransaction.Pool pool) {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        if (pool == INSTANTX_PENDING) {
            return instantXPending.size();
        } else if (pool == INSTANTX_LOCKED) {
            return instantXLocked.size();
        } else {
            return null;
        }
    }

    @Override
    public int getAllExtendedPoolSizes() {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        return instantXPending.size() + instantXLocked.size();
    }

    @Override
    public void addTransactionsFromAllPoolsToSet(Set<WalletTransaction> set) {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        for (final Transaction tx : instantXPending.values()) {
            set.add(new WalletTransaction(INSTANTX_PENDING, tx));
        }
        for (final Transaction tx : instantXLocked.values()) {
            set.add(new WalletTransaction(INSTANTX_LOCKED, tx));
        }
    }

    @Override
    public void processBestBlockForExtendedTypes(Transaction tx, Map<Transaction, TransactionConfidence.Listener.ChangeReason> confidenceChanged) {
        final TransactionConfidence confidence = tx.getConfidence();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (confidence) { // because of questionable synchronization system inside TransactionConfidence
            if(confidence.getConfidenceType().equals(DashTransactionConfidenceExtension.INSTANTX_LOCKED)) {
                final int newDepth = confidence.getDepthInBlocks() + 1;
                confidence.incrementDepthInBlocks();
                if (newDepth > 6) {
                    confidence.setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
                    confidenceChanged.put(tx, TransactionConfidence.Listener.ChangeReason.TYPE);
                }
            }
        }
    }

    @Override
    public boolean isExtendedConfidenceRelevant(Transaction tx) {
        final TransactionWireStrategy wireStrategy = tx.getTransactionExtension().getWireStrategy();
        return wireStrategy == DashTransactionWireStrategy.INSTANT_X || wireStrategy == DashTransactionWireStrategy.INSTANT_X_LOCKED;
    }

    @Override
    public TransactionConfidence.ConfidenceType commitTxExtendedConfidenceType(Transaction tx) {
        return tx.getTransactionExtension().getWireStrategy() == DashTransactionWireStrategy.INSTANT_X
                ? DashTransactionConfidenceExtension.INSTANTX_PENDING
                : DashTransactionConfidenceExtension.INSTANTX_LOCKED;
    }

    @Override
    @Nullable
    public WalletTransaction.Pool commitTxExtendedPool(Transaction tx) {
        final TransactionWireStrategy wireStrategy = tx.getTransactionExtension().getWireStrategy();
        return wireStrategy == DashTransactionWireStrategy.INSTANT_X
                ? INSTANTX_PENDING
                : wireStrategy == DashTransactionWireStrategy.INSTANT_X_LOCKED ? INSTANTX_LOCKED : null;
    }

    @Override
    public boolean skipTransactionBroadcast(TransactionConfidence.ConfidenceType confidenceType) {
        return confidenceType.equals(DashTransactionConfidenceExtension.INSTANTX_LOCKED);
    }

    @Override
    public boolean isTransactionBroadcastable(TransactionConfidence.ConfidenceType confidenceType) {
        return confidenceType.equals(DashTransactionConfidenceExtension.INSTANTX_PENDING);
    }

    @Override
    public boolean receivePendingExtension(Transaction tx, @Nullable Peer peer, @Nullable PeerGroup peerGroup) {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());

        final DashTransactionExtension txExtension = CommonUtils.extractTransactionExtension(tx);

        if (txExtension.getWireStrategy() == DashTransactionWireStrategy.INSTANT_X) {
            if (!InstantXManager.isTxValidForLocking(tx, network.getSporkManager()))
                return false;

            for (final TransactionOutput out : tx.getOutputs()) {
                if (!out.getScriptPubKey().isSentToAddress()) {
                    logger.info("txlreq - Invalid Out Script in {}", tx);
                    return false;
                }
            }

            final AbstractBlockChain blockChain = (peer != null)
                    ? peer.getBlockChain()
                    : (peerGroup != null ? peerGroup.getBlockChain() : null);

            if (blockChain == null)
                return false;

            final boolean receivedLock = network.getInstantXManager().createReceivedLock(tx, blockChain);
            if (receivedLock) {
                CommonUtils.relayInv(DashDefinition.INV_ORDINAL_TRANSACTION_LOCK_REQUEST, new TransactionLockRequest(tx), network, peer, peerGroup);
                logger.info("txlreq - Transaction Lock Request: {} : accepted {}", peer != null ? peer : peerGroup, tx.getHash());
            }
            return receivedLock;
        }

        return true;
    }

    private void pushTxBackToPending(@Nullable Transaction tx) {
        if (tx != null) {
            CommonUtils.extractTransactionExtension(tx).setWireStrategy(DashTransactionWireStrategy.STANDARD);
            tx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
            CoreHelper.getWalletPendingPool(wallet).put(tx.getHash(), tx);
            tx.getConfidence().queueListeners(TransactionConfidence.Listener.ChangeReason.TYPE);
            CoreHelper.walletOnTxConfidenceChangedAndSave(wallet, tx);
        }
    }

    @Override
    public boolean removePending(Sha256Hash txHash) {
        final boolean pending = instantXPending.remove(txHash) != null;
        final boolean locked = instantXLocked.remove(txHash) != null;
        return locked || pending;
    }

    @Nullable
    @Override
    public TransactionInput.ConnectionResult maybeConnectResults(TransactionInput input) {
        TransactionInput.ConnectionResult result = input.connect(instantXLocked, TransactionInput.ConnectMode.ABORT_ON_CONFLICT);
        if (result == TransactionInput.ConnectionResult.NO_SUCH_TX) {
            result = input.connect(instantXPending, TransactionInput.ConnectMode.ABORT_ON_CONFLICT);
        }
        return result;
    }

    @Override
    public void clearExtendedPools() {
        checkState(CoreHelper.getWalletLock(wallet).isHeldByCurrentThread());
        instantXPending.clear();
        instantXLocked.clear();
    }

    @Override
    public String getPoolName(WalletTransaction.Pool pool) {
        if (pool == INSTANTX_LOCKED)
            return "instantx locked";
        else if (pool == INSTANTX_PENDING)
            return "instantx pending";

        return null;
    }

    void onLockEviction(Sha256Hash txHash) {
        final ReentrantLock walletLock = CoreHelper.getWalletLock(wallet);
        walletLock.lock();
        try {
            moveTransactionBackToPending(txHash);
        } finally {
            walletLock.unlock();
        }
    }

    Wallet getWallet() {
        return wallet;
    }

    /**
     * wallet.lock must be locked.
     */
    private void moveTransactionBackToPending(Sha256Hash txHash) {
        Transaction tx = instantXPending.remove(txHash);
        pushTxBackToPending(tx);
        tx = instantXLocked.remove(txHash);
        pushTxBackToPending(tx);
    }

}
