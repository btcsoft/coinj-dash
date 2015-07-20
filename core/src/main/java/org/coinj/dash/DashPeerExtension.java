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

import com.google.common.base.Preconditions;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;
import org.coinj.api.CoinDefinition;
import org.coinj.commons.AbstractPeerExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

/**
* Date: 5/17/15
* Time: 1:31 PM
*
* @author Mikhail Kulikov
*/
public final class DashPeerExtension extends AbstractPeerExtension {

    private static final Logger logger = LoggerFactory.getLogger(DashPeerExtension.class);

    private final Peer peer;
    private final NetworkParameters params;
    private final DashNetwork network;
    private final MasternodeManager masternodeManager;
    private final InstantXManager instantXManager;
    private final SporkManager sporkManager;

    DashPeerExtension(Peer peer, DashNetwork network) {
        this.peer = peer;
        this.params = peer.getParams();
        this.network = CommonUtils.extractNetworkExtension(params);
        this.masternodeManager = network.getMasternodeManager();
        this.instantXManager = network.getInstantXManager();
        this.sporkManager = network.getSporkManager();
    }

    @Override
    public DashInventoryAccumulator createInventoryAccumulator() {
        return new DashInventoryAccumulator();
    }

    @Override
    public void processInv(InventoryAccumulator accumulator, GetDataMessage getDataMessage) {
        Preconditions.checkArgument(accumulator instanceof DashInventoryAccumulator,
                "accumulator not an instance of DashInventoryAccumulator: %s", accumulator.getClass().getName());

        final boolean mnLogicPermitted = isMasternodesLogicPermitted();
        final boolean chainDownloading = isBlockChainDownloading();

        final LinkedList<InventoryItem> instantXLockRequests = new LinkedList<InventoryItem>();
        final LinkedList<InventoryItem> instantXLockVotes = new LinkedList<InventoryItem>();
        final LinkedList<InventoryItem> sporks = new LinkedList<InventoryItem>();
        final LinkedList<InventoryItem> scanningErrors = new LinkedList<InventoryItem>();
        final LinkedList<InventoryItem> masternodeBroadcasts = new LinkedList<InventoryItem>();
        final LinkedList<InventoryItem> masternodePings = new LinkedList<InventoryItem>();
        @SuppressWarnings("ConstantConditions")
        final DashInventoryAccumulator dac = (DashInventoryAccumulator) accumulator;

        for (final InventoryItem item : dac.getItemsList()) {
            if (item.type.equals(DashDefinition.INV_TYPE_TRANSACTION_LOCK_REQUEST)) {
                instantXLockRequests.add(item);
            } else if (item.type.equals(DashDefinition.INV_TYPE_TRANSACTION_LOCK_VOTE)) {
                if (mnLogicPermitted && !chainDownloading && sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTX))
                    instantXLockVotes.add(item);
            } else if (item.type.equals(DashDefinition.INV_TYPE_SPORK)) {
                if (mnLogicPermitted)
                    sporks.add(item);
            } else if (item.type.equals(DashDefinition.INV_TYPE_MASTERNODE_WINNER)) {
                // ignore it
                logger.debug("INV_TYPE_MASTERNODE_WINNER inv");
            } else if (item.type.equals(DashDefinition.INV_TYPE_MASTERNODE_SCANNING_ERROR)) {
                if (mnLogicPermitted && sporkManager.isSporkActive(SporkManager.SPORK_7_MASTERNODE_SCANNING))
                    scanningErrors.add(item);
            } else if (item.type.equals(DashDefinition.INV_TYPE_BUDGET_VOTE)) {
                // ignore it
                logger.debug("INV_TYPE_BUDGET_VOTE inv");
            } else if (item.type.equals(DashDefinition.INV_TYPE_BUDGET_PROPOSAL)) {
                // ignore it
                logger.debug("INV_TYPE_BUDGET_PROPOSAL inv");
            } else if (item.type.equals(DashDefinition.INV_TYPE_BUDGET_FINALIZED_VOTE)) {
                // ignore it
                logger.debug("INV_TYPE_BUDGET_FINALIZED_VOTE inv");
            } else if (item.type.equals(DashDefinition.INV_TYPE_BUDGET_FINALIZED)) {
                // ignore it
                logger.debug("INV_TYPE_BUDGET_FINALIZED inv");
            } else if (item.type.equals(DashDefinition.INV_TYPE_MASTERNODE_ANNOUNCE)) {
                if (mnLogicPermitted)
                    masternodeBroadcasts.add(item);
            } else if (item.type.equals(DashDefinition.INV_TYPE_MASTERNODE_PING)) {
                if (mnLogicPermitted)
                    masternodePings.add(item);
            }
        }

        Iterator<InventoryItem> it = instantXLockRequests.iterator();
        while (it.hasNext()) {
            InventoryItem item = it.next();
            if (mnLogicPermitted && !chainDownloading && sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTX)) {
                if (instantXManager.registerLockRequestInv(item.hash)) {
                    logger.debug("{}: getdata on txlreq {}", peer.getAddress(), item.hash);
                    getDataMessage.addItem(item);
                }
                CoreHelper.callSeenTxInv(peer, item);
            } else {
                CoreHelper.callProcessTxInvItem(peer, item, getDataMessage, it);
            }
        }

        it = instantXLockVotes.iterator();
        while (it.hasNext()) {
            InventoryItem item = it.next();
            if (instantXManager.registerConsensusVoteInv(item.hash)) {
                logger.debug("{}: getdata on txlvote {}", peer.getAddress(), item.hash);
                getDataMessage.addItem(item);
            }
        }

        it = sporks.iterator();
        while (it.hasNext()) {
            InventoryItem item = it.next();
            if (sporkManager.sporkMessagesRegistry.registerInv(item.hash)) {
                logger.debug("{}: getdata on spork {}", peer.getAddress(), item.hash);
                getDataMessage.addItem(item);
            }
        }

        it = scanningErrors.iterator();
        while (it.hasNext()) {
            InventoryItem item = it.next();
            if (masternodeManager.getMasternodeScanningErrorsRegistry().registerInv(item.hash)) {
                logger.debug("{}: getdata on mnse {}", peer.getAddress(), item.hash);
                getDataMessage.addItem(item);
            }
        }

        it = masternodeBroadcasts.iterator();
        while (it.hasNext()) {
            InventoryItem item = it.next();
            if (masternodeManager.getMasternodeBroadcastRegistry().registerInv(item.hash)) {
                logger.debug("{}: getdata on mnb {}", peer.getAddress(), item.hash);
                getDataMessage.addItem(item);
            }
        }

        it = masternodePings.iterator();
        while (it.hasNext()) {
            InventoryItem item = it.next();
            if (masternodeManager.getMasternodePingRegistry().registerInv(item.hash)) {
                logger.debug("{}: getdata on mnp {}", peer.getAddress(), item.hash);
                getDataMessage.addItem(item);
            }
        }
    }

    @Override
    public boolean processMessage(final Message m) {
        if (m instanceof Transaction) {
            // catch masternodes vin spends and invalidate if that's the case
            if (network.performMasternodeInputsValidationAgainstIncomingTransactions()) {
                // operation is relatively heavy on cpu usage so execute it in a user thread to avoid peer lags
                Threading.USER_THREAD.execute(new Runnable() {
                    @Override
                    public void run() {
                        network.getMasternodeManager().validateMasternodesAgainstTransactionOutpoints((Transaction) m);
                    }
                });
            }
        }

        if (m instanceof DarksendFreeTransaction) {
            processDarksendFreeTransaction((DarksendFreeTransaction) m);
        } else if (m instanceof MasternodeBroadcast) {
            processMasternodeBroadcast((MasternodeBroadcast) m);
        } else if (m instanceof MasternodePing) {
            processMasternodePing((MasternodePing) m);
        } else if (m instanceof MasternodeScanningError) {
            processMasternodeScanningError((MasternodeScanningError) m);
        } else if (m instanceof MasternodeBudgetVote) {
            processMasternodeVote((MasternodeBudgetVote) m);
        } else if (m instanceof MasternodeListRequest) {
            processMasternodeListRequest((MasternodeListRequest) m);
        } else if (m instanceof TransactionLockRequest) {
            processTransactionLockRequest((TransactionLockRequest) m);
        } else if (m instanceof DarkSendElectionEntryPingMessage) {
            processDarkSendPingMessage((DarkSendElectionEntryPingMessage) m);
        } else if (m instanceof ConsensusVote) {
            processTransactionLockRequestVote((ConsensusVote) m);
        } else if (m instanceof SporkMessage) {
            processSporkMessage((SporkMessage) m);
        } else if (m instanceof GetSporksMessage) {
            processGetSporksMessage((GetSporksMessage) m);
        } else {
            return false;
        }

        return true;
    }

    public boolean isBlockChainDownloading() {
        return CommonUtils.isBlockChainDownloading(peer);
    }

    private void processDarksendFreeTransaction(DarksendFreeTransaction tx) {
        final MasternodeData mn = masternodeManager.find(tx.getIn());

        if (mn != null) {
            if (!mn.getAllowFreeTx()) {
                //multiple peers can send us a valid masternode transaction
                logger.debug("dstx: Masternode sending too many transactions {}", tx.getTx().getHash());
                return;
            }

            if (tx.checkSignature(mn)) {
                logger.info("dstx: Got Masternode transaction {}", tx.getTx().getHash());

                mn.setAllowFreeTx(false);
                // TODO: some darksend functionality
            } else {
                logger.info("dstx: Got bad masternode address signature {}", tx.getIn());
                return;
            }
        }

        CoreHelper.callProcessTransaction(peer, tx.getTx());
    }

    private void processMasternodeBroadcast(MasternodeBroadcast broadcast) {
        if (!isMasternodesLogicPermitted() || isBlockChainDownloading()) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("mnb - Received: addr: {} vin: {}", broadcast.getAddr(), broadcast.getTxIn());
        }

        if (!masternodeManager.getMasternodeBroadcastRegistry().registerMessage(broadcast)) {
            //seen
            return;
        }

        if (!broadcast.checkSignature() || updateExistingMasternodes(broadcast) || broadcast.getTxIn().isCoinBase()) {
            //failed
            return;
        }

        final VerificationInputContext inputContext = network.getVerificationInputContext(broadcast.getTxIn());
        // make sure the v-out that was signed is related to the transaction that spawned the Masternode
        // this is expensive, so it's only done once per Masternode
        if (!network.isVinAssociatedWithPubkey(inputContext, broadcast.getPubKey1())) {
            logger.info("mnb - Got mismatched pubkey and vin: {}, {}", broadcast.getPubKey1(), broadcast.getTxIn());
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("mnb - Got NEW Masternode entry {}", broadcast.getAddr());
        }
        // make sure it's still unspent
        // this is checked later by .check() in many places (incl. by DashNetwork's darksend thread)
        if (broadcast.checkInputs(network, inputContext)) {
            // add our Masternode based on broadcast
            final boolean added = masternodeManager.add(broadcast);
            if (!added) {
                updateExistingMasternodes(broadcast);
                return;
            }

            final InetAddress addr = broadcast.getAddr().getAddr();
            boolean isLocal = addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isLoopbackAddress() || addr.isAnyLocalAddress();
            if (CoinDefinition.REG_TEST_STANDARD.equals(params.getStandardNetworkId())) {
                isLocal = false;
            }

            if(!broadcast.isRequested() && !isLocal) {
                relayMasternodeBroadcast(broadcast);
            }

            // use this as a peer
            final PeerGroup peerGroup = peer.getPeerGroup();
            if (peerGroup != null) {
                peerGroup.addAddress(broadcast.getAddr());
            }
        } else {
            logger.info("mnb - Rejected Masternode entry {}", broadcast.getAddr());
        }
    }

    /**
     * Search existing Masternode list, this is where we update existing Masternodes with new dsee broadcasts.
     * @param broadcast broadcast
     * @return true if update happened
     */
    private boolean updateExistingMasternodes(MasternodeBroadcast broadcast) {
        final MasternodeData mn = masternodeManager.find(broadcast.getTxIn());
        if (mn != null) {
            // mn.pubkey = pubkey, IsVinAssociatedWithPubkey is validated once below,
            //   after that they just need to match
            if (!broadcast.isRequested()
                    && mn.getPubKey1().getPubKeyPoint().equals(broadcast.getPubKey1().getPubKeyPoint())
                    && !mn.updatedWithin(MasternodeData.MASTERNODE_MIN_DSEE_SECONDS))
            {
                mn.updateLastSeen();

                if (mn.getSigTime() < broadcast.getSigTime()) { //take the newest entry
                    logger.info("mnb - Got updated entry for {}", broadcast.getAddr());

                    mn.updateFromNewBroadcast(broadcast);

                    mn.check(masternodeManager);
                    if (mn.isEnabled()) {
                        relayMasternodeBroadcast(broadcast);
                    }
                }
            }
            return true;
        }

        return false;
    }

    private void processMasternodePing(MasternodePing ping) {
        if (!isMasternodesLogicPermitted() || isBlockChainDownloading())
            return;

        if (logger.isDebugEnabled()) {
            logger.debug("mnping - Received: vin: {} sigTime: {}", ping.getTxIn(), ping.getSigTime());
        }

        if (!masternodeManager.getMasternodePingRegistry().registerMessage(ping)) {
            //seen
            return;
        }

        final TransactionInput txIn = ping.getTxIn();

        final boolean checked = ping.check(masternodeManager);
        if (!checked) {
            return;
        }
        relayMasternodePing(ping);

        if (masternodeManager.registerOurAskForList(txIn)) {
            // ask for the dsee info once from the node that sent mnping
            logger.info("mnping - Asking source node for missing entry {}", txIn);
            peer.sendMessage(new MasternodeListRequest(params, txIn));
        }
    }

    private void processMasternodeScanningError(MasternodeScanningError error) {
        final AbstractBlockChain blockChain = peer.getBlockChain();
        if (!network.permitsMasternodesLogic(blockChain) || isBlockChainDownloading() || !sporkManager.isSporkActive(SporkManager.SPORK_7_MASTERNODE_SCANNING))
            return;

        if (!masternodeManager.getMasternodeScanningErrorsRegistry().registerMessage(error)) {
            // seen
            return;
        }

        if (!error.isValid()) {
            logger.info("mnse - Invalid object");
        }

        final MasternodeData mpnA = masternodeManager.find(error.getTxInMasternodeA());
        if (mpnA == null || mpnA.getProtocolVersion() < DashDefinition.DARKSEND_POOL_MIN_PROTOCOL_VERSION) {
            return;
        }

        int blockHeight = blockChain.getBestChainHeight();
        if (blockHeight - error.getBlockHeight() > 10) {
            logger.info("mnse - Too old");
            return;
        }

        final ArrayList<MasternodeManager.RankedMasternode> sortedRanking = masternodeManager.buildRanking(error.getBlockHeight(), blockChain);

        // Lowest masternodes in rank check the highest each block
        int rankA = masternodeManager.getMasternodeRank(error.getTxInMasternodeA(), sortedRanking, true);
        final int masternodesAboveProtocol = masternodeManager.countMasternodesAboveProtocol();
        int countScanningPerBlock = Math.max(1, masternodesAboveProtocol / 100);
        if (rankA < 0 || rankA > countScanningPerBlock) {
            if (rankA >= 0)
                logger.info("mnse - MasternodeA ranking is too high: {}", rankA);
            return;
        }

        int rankB = masternodeManager.getMasternodeRank(error.getTxInMasternodeB(), sortedRanking, false);
        if (rankB < 0 || rankB < masternodesAboveProtocol - countScanningPerBlock) {
            if (rankB >= 0)
                logger.info("mnse - MasternodeB ranking is too low: {}", rankB);
            return;
        }

        if (!error.isSignatureValid(masternodeManager)) {
            logger.info("mnse - Bad masternode message, signature check failed");
            return;
        }

        final MasternodeData mpnB = masternodeManager.find(error.getTxInMasternodeB());
        if (mpnB == null)
            return;

        if (logger.isDebugEnabled()) {
            logger.debug("mnse - height {} MasternodeA {} MasternodeB {}", error.getBlockHeight(), mpnA.getAddr(), mpnB.getAddr());
        }

        mpnB.applyScanningError(error);
        relayMasternodeScanningError(error);
    }

    private void processMasternodeVote(@SuppressWarnings("UnusedParameters") MasternodeBudgetVote vote) {
        // no need to support masternode budget functionality in a mobile wallet
        if (isMasternodesLogicPermitted())
            logger.debug("Got mvote from peer {}", peer);
    }

    private void relayMasternodeBroadcast(MasternodeBroadcast broadcast) {
        CommonUtils.relayInv(DashDefinition.INV_ORDINAL_MASTERNODE_ANNOUNCE, broadcast, network, peer, null);
    }

    private void relayMasternodePing(MasternodePing ping) {
        CommonUtils.relayInv(DashDefinition.INV_ORDINAL_MASTERNODE_PING, ping, network, peer, null);
    }

    private void relayMasternodeScanningError(MasternodeScanningError error) {
        CommonUtils.relayInv(DashDefinition.INV_ORDINAL_MASTERNODE_SCANNING_ERROR, error, network, peer, null);
    }

    private void processMasternodeListRequest(MasternodeListRequest request) {
        if (!isMasternodesLogicPermitted())
            return;

        final TransactionInput txIn = request.getTxIn();
        final TransactionOutPoint outpoint = txIn.getOutpoint();
        final long outIx = outpoint.getIndex();
        final boolean emptyInput =
                outpoint.getHash().equals(Sha256Hash.ZERO_HASH) && (outIx == -1 || outIx == TransactionInput.NO_SEQUENCE) && txIn.getScriptBytes().length == 0 && !txIn.hasSequence();
        final PeerAddress peerAddress = peer.getAddress();
        if (emptyInput) { //only should ask for this once
            final InetAddress addr = peerAddress.getAddr();
            boolean isLocal = addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isLoopbackAddress() || addr.isAnyLocalAddress();
            if (!isLocal && params.isMainNet()) {
                if (!masternodeManager.registerPeerAskForList(peerAddress)) {
                    logger.info("dseg - peer already asked me for the list");
                    return;
                }
            }
        } //else, asking for a specific node which is ok

        int i = 0;
        for (final MasternodeData mn : masternodeManager.getMasternodesIterableSnapshot()) {
            final PeerAddress mnPeerAddr = mn.getAddr();
            final InetAddress addrMn = mnPeerAddr.getAddr();
            if (addrMn.isSiteLocalAddress() || addrMn.isLinkLocalAddress() || addrMn.isLoopbackAddress() || addrMn.isAnyLocalAddress()) {
                continue; //local network
            }

            if (mn.isEnabled()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("dseg - Sending Masternode entry - {}", mnPeerAddr);
                }
                if (emptyInput) {
                    final MasternodeBroadcast broadcast = new MasternodeBroadcast(params, mn);
                    broadcast.requested = true;
                    peer.sendMessage(broadcast);
                } else if (txIn.equals(mn.getTxIn())) {
                    final MasternodeBroadcast broadcast = new MasternodeBroadcast(params, mn);
                    broadcast.requested = true;
                    peer.sendMessage(broadcast);
                    logger.info("dseg - Sent 1 Masternode entries to {}", mnPeerAddr);
                    return;
                }
            }
            i++;
        }

        logger.info("dseg - Sent {} Masternode entries to {}", i, peerAddress);
    }

    /**
     * InstanceX lock handling will occur inside {@link DashWalletCoinSpecifics#receivePendingExtension(Transaction, Peer, PeerGroup)}.
     */
    private void processTransactionLockRequest(TransactionLockRequest request) {
        final Transaction tx = request.getTx();

        if (network.permitsInstantX(request.getTx()) && !isBlockChainDownloading() && sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTX)) {
            final Coin valueOut = tx.getTotalOutputs();
            if(valueOut.compareTo(Coin.valueOf(1000, 0)) > 0)
                throw new VerificationException("InstantX transaction of more than 1000");
            final Coin fee = tx.getFeeFromOutputs(valueOut);
            if (fee == null || fee.compareTo(Coin.valueOf(0, 1)) < 0) {
                throw new VerificationException("InstantX transaction with fee less than 0.01");
            }

            CommonUtils.extractTransactionExtension(tx).setWireStrategy(DashTransactionWireStrategy.INSTANT_X);
        }

        CoreHelper.callProcessTransaction(peer, tx);
    }

    private void processTransactionLockRequestVote(ConsensusVote vote) {
        if (isMasternodesLogicPermitted() && !isBlockChainDownloading() && sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTX)) {
            final InstantXManager.VotingResult votingResult =
                    instantXManager.processReceivedVote(vote, peer.getBlockChain());

            if (votingResult.isSuccessful) {
                if (votingResult.isVotingCompleted()) {
                    for (final Wallet wallet : peer.getRegisteredWallets()) {
                        wallet.receivePendingWithoutExtendedCheck(votingResult.tx, null, peer);
                    }
                }

                if (votingResult.relayInv) {
                    relayTransactionLockRequestVote(vote);
                }
            }
        }
    }

    private boolean isMasternodesLogicPermitted() {
        return network.permitsMasternodesLogic(peer.getBlockChain());
    }

    private void relayTransactionLockRequestVote(ConsensusVote vote) {
        CommonUtils.relayInv(DashDefinition.INV_ORDINAL_TRANSACTION_LOCK_VOTE, vote, network, peer, null);
    }

    private void processDarkSendPingMessage(@SuppressWarnings("UnusedParameters") DarkSendElectionEntryPingMessage message) {
        // TODO: darksend features not yet implemented
        logger.info("Got dseep from peer {}, doing nothing", peer);
    }

    private void processSporkMessage(SporkMessage spork) {
        if (isMasternodesLogicPermitted()) {
            if (logger.isDebugEnabled()) {
                logger.debug("spork - received message " + spork.getHash());
            }

            if (sporkManager.registerSporkMessage(spork, peer.getBlockChain().getBestChainHeight())) {
                relaySporkMessage(spork);
                spork.execute(network); // does a task if needed
            }
        }
    }

    private void relaySporkMessage(SporkMessage spork) {
        CommonUtils.relayInv(DashDefinition.INV_ORDINAL_SPORK, spork, network, peer, null);
    }

    private void processGetSporksMessage(@SuppressWarnings("UnusedParameters") GetSporksMessage getSporks) {
        // no need to reply 'cause we are wallet app
        logger.debug("Got getsporks message");
    }

}
