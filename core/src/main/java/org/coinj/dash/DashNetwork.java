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
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.utils.Threading;
import org.coinj.api.CoinDefinition;
import org.coinj.api.NetworkExtensionsContainer;
import org.coinj.api.NetworkMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Date: 5/23/15
 * Time: 7:48 AM
 *
 * @author Mikhail Kulikov
 */
@ThreadSafe
public final class DashNetwork implements NetworkExtensionsContainer {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(DashNetwork.class);

    public enum Mode implements NetworkMode {

        LITE_MODE {
            @Override
            boolean permitMasternodeStructures() {
                return false;
            }

            @Override
            boolean permitInstantXLockRequestsSending() {
                return false;
            }
        },
        INSTANTX_SEND_ONLY_MODE {
            @Override
            boolean permitMasternodeStructures() {
                return false;
            }
        },
        INSTANTX_RESTRICT_NOT_RELEVANT {
            @Override
            boolean permitRegularMasternodesLogic(@Nonnull AbstractBlockChain blockChain) {
                return true;
            }

            @Override
            boolean permitInstantX(@Nonnull DashNetwork network, @Nonnull Transaction tx) {
                if (!network.isSyncingMasternodeAssets()) {
                    if (tx.isCoinBase()) {
                        return false;
                    }

                    for (final TransactionInput input : tx.getInputs()) {
                        final Sha256Hash originHash = input.getOutpoint().getHash();

                        if (validatedOriginHeight(network, originHash) == null) {
                            return false;
                        }
                    }

                    return true;
                }

                return false;
            }

            @Nullable
            @Override
            Integer checkTransactionAndGetAge(@Nonnull DashNetwork network, @Nonnull Transaction tx) {
                final AbstractBlockChain chain = network.getBlockChain();
                if (chain == null) {
                    return null;
                }

                final Sha256Hash originHash = tx.getInputs().get(0).getOutpoint().getHash();
                return validatedOriginHeight(network, originHash);
            }

            @Nullable
            private Integer validatedOriginHeight(DashNetwork network, Sha256Hash originHash) {
                final AbstractBlockChain chain = network.getBlockChain();
                if (chain == null) {
                    return null;
                }
                final BlockStore store = chain.getBlockStore();
                final int bestChainHeight = chain.getBestChainHeight();

                for (final DashWalletCoinSpecifics walletSpecs : network.getInstantXManager().getRegisteredWallets()) {
                    final Transaction originTx = walletSpecs.getWallet().getTransaction(originHash);

                    if (originTx == null) {
                        continue;
                    }

                    final Map<Sha256Hash, Integer> blockHashes = originTx.getAppearsInHashes();

                    if (blockHashes == null) {
                        return null;
                    }

                    int height = Integer.MAX_VALUE;
                    for (final Sha256Hash blkHash : blockHashes.keySet()) {
                        if (chain.isOrphan(blkHash)) {
                            continue;
                        }

                        try {
                            final StoredBlock storedBlock = store.get(blkHash);

                            final int stHeight = storedBlock.getHeight();
                            if (height > stHeight) {
                                height = stHeight;
                            }
                        } catch (BlockStoreException ex) {
                            logger.warn("Block store exception while trying to get InstantX permission", ex);
                            return null;
                        }
                    }

                    if (height == Integer.MAX_VALUE || (bestChainHeight + 1 - height) < 6) {
                        return null;
                    }

                    return height;
                }

                return null;
            }

            @Override
            boolean isInputAcceptable(@Nonnull DashNetwork network, @Nonnull TransactionInput txIn) {
                return true; // experimental masternodes mode which is OK because no features except InstantX of known transactions is enabled
            }

            @Override
            boolean isInputAcceptable(@Nonnull DashNetwork network, @Nonnull VerificationInputContext context) {
                return true; // experimental masternodes mode which is OK because no features except InstantX of known transactions is enabled
            }

            @Override
            boolean isVinAssociatedWithPubkey(@Nonnull DashNetwork network, @Nonnull VerificationInputContext context, @Nonnull ECKey pubKey) {
                return true; // experimental masternodes mode which is OK because no features except InstantX of known transactions is enabled
            }

            @Override
            MinConfirmationsCheckResult performMinConfirmationsCheck(@Nonnull DashNetwork network, @Nonnull VerificationInputContext inputContext, long sigTime) {
                return MinConfirmationsCheckResult.createSuccess();
            }

            @Override
            boolean performMasternodeInputsValidationAgainstIncomingTransactions() {
                return true; // some safety measure to avoid masternodes registry bloating
            }
        },
        MASTERNODES_LOGIC_MODE {
            @Override
            boolean permitRegularMasternodesLogic(@Nonnull AbstractBlockChain blockChain) {
                return isChainFullPruned(blockChain);
            }

            @Override
            boolean permitInstantX(@Nonnull DashNetwork network, @Nonnull Transaction tx) {
                return !network.isSyncingMasternodeAssets();
            }

            @Override
            boolean isInputAcceptable(@Nonnull DashNetwork network, @Nonnull TransactionInput txIn) {
                return VerificationUtils.isInputAcceptable(txIn, network.getBlockChain(), network.params);
            }

            @Override
            boolean isInputAcceptable(@Nonnull DashNetwork network, @Nonnull VerificationInputContext context) {
                return VerificationUtils.isInputAcceptable(context, network.getBlockChain(), network.params);
            }

            @Override
            VerificationInputContext getVerificationInputContext(@Nonnull DashNetwork network, @Nonnull TransactionInput txIn) {
                return VerificationUtils.getVerificationInputContext(txIn, network.getBlockChain());
            }

            @Override
            boolean isVinAssociatedWithPubkey(@Nonnull DashNetwork network, @Nonnull VerificationInputContext context, @Nonnull ECKey pubKey) {
                return VerificationUtils.isVinAssociatedWithPubkey(context, pubKey, network.params);
            }
        },
        SEND_INVENTORY_MODE {
            @Override
            boolean permitRegularMasternodesLogic(@Nonnull AbstractBlockChain blockChain) {
                return isChainFullPruned(blockChain);
            }

            @Override
            boolean mustSendInventory() {
                return true;
            }

            @Override
            boolean permitInstantX(@Nonnull DashNetwork network, @Nonnull Transaction tx) {
                return !network.isSyncingMasternodeAssets();
            }

            @Override
            boolean isInputAcceptable(@Nonnull DashNetwork network, @Nonnull TransactionInput txIn) {
                return VerificationUtils.isInputAcceptable(txIn, network.getBlockChain(), network.params);
            }

            @Override
            boolean isInputAcceptable(@Nonnull DashNetwork network, @Nonnull VerificationInputContext context) {
                return VerificationUtils.isInputAcceptable(context, network.getBlockChain(), network.params);
            }

            @Override
            VerificationInputContext getVerificationInputContext(@Nonnull DashNetwork network, @Nonnull TransactionInput txIn) {
                return VerificationUtils.getVerificationInputContext(txIn, network.getBlockChain());
            }

            @Override
            boolean isVinAssociatedWithPubkey(@Nonnull DashNetwork network, @Nonnull VerificationInputContext context, @Nonnull ECKey pubKey) {
                return VerificationUtils.isVinAssociatedWithPubkey(context, pubKey, network.params);
            }
        };

        boolean permitMasternodeStructures() {
            return true;
        }

        boolean permitRegularMasternodesLogic(@Nonnull AbstractBlockChain blockChain) {
            return false;
        }

        boolean permitRegularMasternodesLogic(@Nonnull DashNetwork network) {
            final AbstractBlockChain blockChain = network.getBlockChain();
            return blockChain != null
                    && permitRegularMasternodesLogic(blockChain);
        }

        boolean permitInstantXLockRequestsSending() {
            return true;
        }

        boolean mustSendInventory() {
            return false;
        }

        boolean permitInstantX(@Nonnull DashNetwork network, @Nonnull Transaction tx) {
            return false;
        }

        boolean isInputAcceptable(@Nonnull DashNetwork network, @Nonnull TransactionInput txIn) {
            return false;
        }

        boolean isInputAcceptable(@Nonnull DashNetwork network, @Nonnull VerificationInputContext context) {
            return false;
        }

        VerificationInputContext getVerificationInputContext(@Nonnull DashNetwork network, @Nonnull TransactionInput txIn) {
            return new VerificationInputContext(null);
        }

        boolean isVinAssociatedWithPubkey(@Nonnull DashNetwork network, @Nonnull VerificationInputContext context, @Nonnull ECKey pubKey) {
            return false;
        }

        boolean performMasternodeInputsValidationAgainstIncomingTransactions() {
            return false;
        }

        @Nullable
        Integer checkTransactionAndGetAge(@Nonnull DashNetwork network, @Nonnull Transaction tx) {
            int txAge = 0;

            final AbstractBlockChain chain = network.getBlockChain();
            if (chain == null) {
                return null;
            }

            final int bestChainHeight = chain.getBestChainHeight();
            for (final ListIterator<TransactionInput> iterator = tx.getInputs().listIterator(); iterator.hasPrevious(); ) {
                final TransactionInput input = iterator.previous();

                txAge = VerificationUtils.getInputAge(input, chain, bestChainHeight);
                if (txAge < 6) {
                    logger.info("CreateNewLock - Transaction not found / too new: {} / {}", txAge, tx.getHash());
                    return null;
                }
            }

            return txAge;
        }

        MinConfirmationsCheckResult performMinConfirmationsCheck(@Nonnull DashNetwork network, @Nonnull VerificationInputContext inputContext, long sigTime) {
            final AbstractBlockChain blockChain = network.getBlockChain();
            if (blockChain == null) {
                return MinConfirmationsCheckResult.createFail();
            }

            final StoredBlock chainHead = blockChain.getChainHead();
            final int chainHeadHeight = chainHead.getHeight();

            final int inputAge = VerificationUtils.getInputAge(inputContext, chainHeadHeight);
            if (inputAge < MasternodeData.MASTERNODE_MIN_CONFIRMATIONS) {
                return MinConfirmationsCheckResult.createAgeFail(inputAge);
            }

            // verify that sig time is legit in past
            // should be at least not earlier than block when 1000 DASH tx got MASTERNODE_MIN_CONFIRMATIONS
            final int height = chainHeadHeight + 1 - inputAge; // avoid double lookup
            StoredBlock current = chainHead;
            int counter = 0;
            try {
                while (current != null && current.getHeight() > height + MasternodeData.MASTERNODE_MIN_CONFIRMATIONS) {
                    current = current.getPrev(blockChain.getBlockStore());
                    counter++;
                }
            } catch (BlockStoreException ex) {
                return MinConfirmationsCheckResult.createHeightFail(chainHeadHeight - counter, ex);
            }
            if (current == null) {
                return MinConfirmationsCheckResult.createHeightFail(chainHeadHeight - counter, null);
            }

            if (current.getHeader().getTimeSeconds() > sigTime) { // now current must be a block where tx got MASTERNODE_MIN_CONFIRMATIONS
                return MinConfirmationsCheckResult.createBlockTimeFail(current.getHeader().getTimeSeconds());
            }

            return MinConfirmationsCheckResult.createSuccess();
        }

    }

    private static final ECKey SPORK_KEY_MAIN = ECKey.fromPublicOnly(Utils.HEX.decode(DashDefinition.SPORK_KEY_MAIN));
    private static final ECKey SPORK_KEY_TEST = ECKey.fromPublicOnly(Utils.HEX.decode(DashDefinition.SPORK_KEY_TEST));
    private static final int MASTERNODE_LIST_SYNCED = 999;
    private static final int MASTERNODES_DUMP_PERIOD = 15 * 60;

    private static final AtomicInteger threadCounter = new AtomicInteger(0);


    private final MasternodeManager masternodeManager;
    private final InstantXManager instantXManager;
    private final SporkManager sporkManager;
    private final NetworkParameters params;

    private final transient AtomicReference<AbstractBlockChain> blockChainRef = new AtomicReference<AbstractBlockChain>(null);
    private final transient AtomicReference<PeerGroup> peerGroupRef = new AtomicReference<PeerGroup>(null);
    private final transient AtomicReference<Peer> peerRef = new AtomicReference<Peer>(null);

    private final AtomicInteger requestedMasternodeAssets;
    private final LinkedHashMap<PeerAddress, PeerAddress> peersFulfilledSync;

    private final transient AtomicBoolean darksendThreadShutdownMarker = new AtomicBoolean(false);
    private final transient Thread darksendThread;

    private final Mode mode;

    private DashNetwork(DashNetwork deSer) {
        masternodeManager = deSer.masternodeManager;
        instantXManager = deSer.instantXManager;
        sporkManager = deSer.sporkManager;
        params = deSer.params;
        mode = deSer.mode;
        requestedMasternodeAssets = deSer.requestedMasternodeAssets;
        peersFulfilledSync = deSer.peersFulfilledSync;
        darksendThread = null; // if java serialization becomes an issue, use reflection here
    }

    DashNetwork(final NetworkParameters params, Mode mode) {
        this.params = params;
        this.mode = mode;
        requestedMasternodeAssets = new AtomicInteger(0);
        peersFulfilledSync = new LinkedHashMap<PeerAddress, PeerAddress>(101, 1.0f) {
            private static final long serialVersionUID = 1L;
            @Override
            protected boolean removeEldestEntry(Map.Entry<PeerAddress, PeerAddress> eldest) {
                return size() >= 100;
            }
        };
        if (mode.permitMasternodeStructures()) {
            masternodeManager = MasternodeManager.construct(params);
            sporkManager = new SporkManager(this);
            instantXManager = new InstantXManager(masternodeManager, this);
            final int threadNum = threadCounter.incrementAndGet();
            darksendThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    long c = 0;
                    while (true) {
                        if (darksendThreadShutdownMarker.get())
                            return;

                        c++;

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            logger.warn(Thread.currentThread().getName() + " interrupted and exiting now");
                            return;
                        }

                        if (darksendThreadShutdownMarker.get())
                            return;

                        final AbstractBlockChain blockChain = blockChainRef.get();
                        if (blockChain != null && DashNetwork.this.mode.permitRegularMasternodesLogic(blockChain)) {
                            if (c % 60 == 0) {
                                masternodeManager.checkAndResolve();
                                instantXManager.cleanTransactionLocksList(blockChain);
                            }

                            if (darksendThreadShutdownMarker.get())
                                return;

                            if (c % 5 == 0 && requestedMasternodeAssets.get() <= 2) {
                                final PeerGroup peerGroup = peerGroupRef.get();
                                final Peer peer = peerRef.get();
                                if (!CommonUtils.isBlockChainDownloading(peerGroup) && !CommonUtils.isBlockChainDownloading(peer)) {
                                    final ArrayList<Peer> peers = Lists.newArrayList();
                                    if (peerGroup != null) {
                                        peers.addAll(peerGroup.getConnectedPeers());
                                    }
                                    if (peer != null) {
                                        peers.add(peer);
                                    }

                                    for (final Peer p : peers) {
                                        if (p.getPeerVersionMessage().getClientVersion() >= DashDefinition.DARKSEND_POOL_MIN_PROTOCOL_VERSION) {
                                            final PeerAddress address = p.getAddress();
                                            // keep track of who we've asked for the list
                                            if (peersFulfilledSync.containsKey(address)) {
                                                continue;
                                            }

                                            logger.info("Successfully synced, asking {} for Masternode list and payment list", p);

                                            // request full mn list only if Masternodes.dat was updated quite a long time ago
                                            if (!masternodeManager.dsegUpdate(p)) {
                                                continue;
                                            }
                                            peersFulfilledSync.put(address, address);

                                            p.sendMessage(new GetSporksMessage(params)); //get current network sporks
                                            requestedMasternodeAssets.incrementAndGet();
                                            break;
                                        }
                                    }
                                }
                            } else if (c % 60 == 0 && requestedMasternodeAssets.get() == 3) {
                                requestedMasternodeAssets.set(MASTERNODE_LIST_SYNCED); // done syncing
                            }

                            if (c % MASTERNODES_DUMP_PERIOD == 0) {
                                masternodeManager.performDump();
                            }
                        }

                        if (c == Long.MAX_VALUE)
                            c = 0;
                    }
                }
            }, "dash-daemon-thread-" + threadNum);
        } else {
            masternodeManager = null;
            sporkManager = null;
            instantXManager = null;
            darksendThread = null;
        }
    }

    MasternodeManager getMasternodeManager() {
        return masternodeManager;
    }

    InstantXManager getInstantXManager() {
        return instantXManager;
    }

    SporkManager getSporkManager() {
        return sporkManager;
    }

    public NetworkParameters getParams() {
        return params;
    }

    public ECKey getSporkKey() {
        return CoinDefinition.MAIN_NETWORK_STANDARD.equals(params.getStandardNetworkId())
                ? SPORK_KEY_MAIN
                : SPORK_KEY_TEST;
    }

    /**
     * Permission to use masternode logic.
     * For inner package use.
     * @param blockChain block chain manager
     * @return true if permitted
     */
    boolean permitsMasternodesLogic(@Nonnull AbstractBlockChain blockChain) {
        return mode.permitRegularMasternodesLogic(blockChain);
    }

    /**
     * Clarifies if network is working with masternodes.
     * @return true if masternodes are being tracked
     */
    public boolean permitsMasternodesLogic() {
        return mode.permitRegularMasternodesLogic(this);
    }

    /**
     * Clarifies if network is sending InstantX lock requests for this app's wallets' outgoing txs.
     * @return true if user's spends are permitted to be broadcasted as InstantX txs
     */
    public boolean permitsLockRequestSending() {
        return mode.permitInstantXLockRequestsSending();
    }

    /**
     * Clarifies if tx is valid to be processed as InstantX tx.
     * @param tx transaction in question
     * @return true if tx will be processed by InstantX code
     */
    public boolean permitsInstantX(@Nonnull Transaction tx) {
        return mode.permitInstantX(this, tx);
    }

    /**
     * Checks transaction if it's age is less than 6 blocks in a chain.
     * If check is passed returns transaction's age.
     * For inner package use.
     * @param tx transaction in question
     * @return null if check failed, integer that is more or equal to 6 if check passed
     */
    @Nullable
    Integer checkTransactionAndGetAge(Transaction tx) {
        return mode.checkTransactionAndGetAge(this, tx);
    }

    /**
     * Checks (or blindly permits/restricts) if a given input is acceptable as masternode input.
     * For inner package use.
     * @param txIn input in question
     * @return true if input was accepted
     */
    boolean isInputAcceptable(TransactionInput txIn) {
        return mode.isInputAcceptable(this, txIn);
    }

    /**
     * Checks (or blindly permits/restricts) if already retrieved input context is acceptable.
     * @param context input context
     * @return true if input was accepted
     */
    public boolean isInputAcceptable(VerificationInputContext context) {
        return mode.isInputAcceptable(this, context);
    }

    /**
     * For masternode inputs related code.
     * Perform early lookup and return result in a context.
     * For inner package use.
     * @param txIn input in question
     * @return context with either found or not found utxo
     */
    VerificationInputContext getVerificationInputContext(TransactionInput txIn) {
        return mode.getVerificationInputContext(this, txIn);
    }

    /**
     * If masternode input is associated with public key.
     * For inner package use.
     * @param context input context from early lookup
     * @param pubKey public key in question (first mn pubkey)
     * @return true if association present
     */
    boolean isVinAssociatedWithPubkey(VerificationInputContext context, ECKey pubKey) {
        return mode.isVinAssociatedWithPubkey(this, context, pubKey);
    }

    /**
     * Perform succession of checks against masternode's input context related to compliance with MASTERNODE_MIN_CONFIRMATIONS constant.
     * For inner package use.
     * @param inputContext input context from early lookup
     * @param sigTime sign time of a broadcast in question
     * @return validation result as an object
     */
    MinConfirmationsCheckResult performMinConfirmationsCheck(VerificationInputContext inputContext, long sigTime) {
        return mode.performMinConfirmationsCheck(this, inputContext, sigTime);
    }

    /**
     * Clarifies if masternode inputs validation against incoming txs is really needed.
     * @return true if needed
     */
    boolean performMasternodeInputsValidationAgainstIncomingTransactions() {
        return mode.performMasternodeInputsValidationAgainstIncomingTransactions();
    }

    /**
     * Clarifies if peer code must send inventory messages to relay data.
     * To send them is redundant for mobile wallets.
     * For inner package use.
     * @return true if sending inventory messages required.
     */
    boolean mustSendInventory() {
        return mode.mustSendInventory();
    }

    void setBlockChain(AbstractBlockChain blockChain) {
        blockChainRef.compareAndSet(null, blockChain);
    }

    @Nullable
    public AbstractBlockChain getBlockChain() {
        return blockChainRef.get();
    }

    void setPeerGroup(PeerGroup peerGroup) {
        peerGroupRef.compareAndSet(null, peerGroup);
    }

    void showPeer(Peer peer) {
        peerRef.compareAndSet(null, peer);
    }

    void executeTask(Runnable runnable) {
        Threading.USER_THREAD.execute(runnable);
    }

    void start() {
        if (darksendThread != null)
            darksendThread.start();
    }

    void stop() {
        if (darksendThread != null)
            darksendThread.interrupt();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        stop();
    }

    boolean isSyncingMasternodeAssets() {
        return requestedMasternodeAssets.get() != MASTERNODE_LIST_SYNCED;
    }

    private static boolean isChainFullPruned(@Nonnull AbstractBlockChain blockChain) {
        return blockChain.getBlockStore() instanceof FullPrunedBlockStore;
    }

    private Object readResolve() {
        return new DashNetwork(this);
    }

    public static final class MinConfirmationsCheckResult {

        static MinConfirmationsCheckResult createFail() {
            return new MinConfirmationsCheckResult(false);
        }
        static MinConfirmationsCheckResult createHeightFail(int failHeight, @Nullable Throwable exception) {
            checkArgument(failHeight >= 0);
            return new MinConfirmationsCheckResult(failHeight, exception);
        }
        static MinConfirmationsCheckResult createAgeFail(int failedAge) {
            return new MinConfirmationsCheckResult(failedAge);
        }
        static MinConfirmationsCheckResult createBlockTimeFail(long failBlockTime) {
            return new MinConfirmationsCheckResult(failBlockTime);
        }
        static MinConfirmationsCheckResult createSuccess() {
            return new MinConfirmationsCheckResult(true);
        }

        public final boolean success;
        public final int failHeight;
        public final int failedAge;
        public final long failBlockTime;
        @Nullable
        public final Throwable exception;

        private MinConfirmationsCheckResult(int failHeight, @Nullable Throwable exception) {
            success = false;
            this.failHeight = failHeight;
            failedAge = Integer.MIN_VALUE;
            this.exception = exception;
            failBlockTime = -1;
        }
        private MinConfirmationsCheckResult(int failedAge) {
            success = false;
            failHeight = -1;
            this.failedAge = failedAge;
            exception = null;
            failBlockTime = -1;
        }
        private MinConfirmationsCheckResult(long failBlockTime) {
            success = false;
            failHeight = -1;
            failedAge = Integer.MIN_VALUE;
            exception = null;
            this.failBlockTime = failBlockTime;
        }
        private MinConfirmationsCheckResult(boolean success) {
            this.success = success;
            failHeight = -1;
            failedAge = Integer.MIN_VALUE;
            exception = null;
            failBlockTime = -1;
        }

        public boolean isFailHeightKnown() {
            return failHeight >= 0;
        }

        public boolean isFailedAgeKnown() {
            return failedAge > Integer.MIN_VALUE;
        }

        public boolean isFailBlockTimeKnown() {
            return failBlockTime >= 0;
        }

    }

}
