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
import org.bitcoinj.store.WalletProtobufSerializer;
import org.coinj.api.*;
import org.coinj.commons.Util;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.math.BigInteger;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static org.coinj.commons.Util.impossibleNullCheck;

/**
 * Date: 5/16/15
 * Time: 2:36 AM
 *
 * @author Mikhail Kulikov
 */
@ThreadSafe
public class DashDefinition implements CoinDefinition {

    private static final long serialVersionUID = 1L;

    public static final DashDefinition INSTANCE = new DashDefinition();

    public static final String NAME = "Dash";
    public static final String SIGNING_NAME = "DarkCoin";
    public static final String TICKER = "DASH";
    public static final String URI_SCHEME = "dash";
    public static final int PROTOCOL_VERSION = 70076;
    public static final boolean CHECKPOINTING_SUPPORT = true;
    public static final int CHECKPOINT_DAYS_BACK = 0;
    public static final int TARGET_TIMESPAN = 24 * 60 * 60;  // 24 hours per difficulty cycle, on average.
    public static final int TARGET_SPACING = (int) (2.5 * 60);  // 2.5 minutes seconds per block.
    public static final int INTERVAL = TARGET_TIMESPAN / TARGET_SPACING;
    public static final int INTERVAL_REGTEST = 10000;
    public static final int SUBSIDY_DECREASE_BLOCK_COUNT = 4730400;
    public static final int REGTEST_SUBSIDY_DECREASE_BLOCK_COUNT = 150;
    public static final long MAX_COINS = 22000000L;
    public static final BigInteger MAIN_MAX_TARGET = Utils.decodeCompactBits(0x1e0fffffL);
    public static final BigInteger REGTEST_MAX_TARGET = new BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);
    public static final BigInteger UNITTEST_MAX_TARGET = new BigInteger("00ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);
    public static final long REFERENCE_DEFAULT_MIN_TX_FEE = 100000;
    public static final int MIN_NONDUST_OUTPUT = 1000;
    public static final int MAX_BLOCK_SIZE = 1000 * 1000;
    public static final Integer PORT = 9999;
    public static final Integer TEST_PORT = 19999;
    public static final Integer REGTEST_PORT = 18444;
    public static final Integer PUBKEY_ADDRESS_HEADER = 76;
    public static final Integer DUMPED_PRIVATE_KEY_HEADER = 204;
    public static final Integer TEST_PUBKEY_ADDRESS_HEADER = 139;
    public static final Integer TEST_DUMPED_PRIVATE_KEY_HEADER = 267;
    public static final Integer P2SH_ADDRESS_HEADER = 16;
    public static final Integer TEST_P2SH_ADDRESS_HEADER = 19;
    public static final Integer SPENDABLE_COINBASE_DEPTH = 100;
    public static final Long MAIN_PACKET_MAGIC = 0xbf0c6bbdL;
    public static final Long TEST_PACKET_MAGIC = 0xcee2caffL;
    public static final Long REGTEST_PACKET_MAGIC = 0xfabfb5daL;

    /** A services flag that denotes whether the peer has a copy of the block chain or not. */
    public static final int NODE_NETWORK = 1;
    /** The smallest protocol version that supports the pong response (BIP 31). Anything beyond version 60000. */
    public static final int PONG_MIN_PROTOCOL_VERSION = 60000;

    public static final int DARKSEND_POOL_MIN_PROTOCOL_VERSION = 70077;

    private static final String[] MAIN_DNS_SEEDS = new String[] {
            "dnsseed.darkcoin.io",
            "dnsseed.darkcoin.qa",
            "dnsseed.masternode.io",
            "dnsseed.dashpay.io",
            "seed.bitnodes.io"
    };
    private static final String[] TEST_DNS_SEEDS = new String[] {
            "testnet-seed.darkcoin.io",
            "testnet-seed.darkcoin.qa",
            "test.dnsseed.masternode.io"
    };

    public static final String ID_MAINNET = "org.dash.production";
    public static final String ID_TESTNET = "org.dash.test";
    public static final String ID_REGTEST = "org.dash.regtest";
    public static final String ID_UNITTESTNET = "org.dashj.unittest";
    public static final String PAYMENT_PROTOCOL_ID_MAINNET = "main";
    public static final String PAYMENT_PROTOCOL_ID_TESTNET = "test";
    private static final int BLOOM_FILTERING_MIN_PROTOCOL_VERSION = 70000;
    private static final int MIN_BROADCAST_CONNECTIONS = 0;

    private static final String GENESIS_TX_IN_BYTES =
            "04ffff001d01044c5957697265642030392f4a616e2f3230313420546865204772616e64204578706572696d656e7420476f6573204c6976653a204f76657273746f636b2e636f6d204973204e6f7720416363657074696e6720426974636f696e73";
    private static final String GENESIS_TX_OUT_BYTES =
            "040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9";
    private static final int GENESIS_BLOCK_VALUE = 50;
    private static final long GENESIS_BLOCK_DIFFICULTY_TARGET = 0x1e0ffff0L;
    private static final long MAIN_GENESIS_BLOCK_TIME = 1390095618L;
    private static final long MAIN_GENESIS_BLOCK_NONCE = 28917698L;
    private static final String MAIN_GENESIS_HASH = "00000ffd590b1485b3caadc19b22e6379c733355108f107a430458cdf3407ab6";
    private static final long TEST_GENESIS_BLOCK_TIME = 1390666206L;
    private static final long TEST_GENESIS_BLOCK_NONCE = 3861367235L;
    private static final String TEST_GENESIS_HASH = "00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c";
    private static final long REGTEST_GENESIS_BLOCK_DIFFICULTY_TARGET = 0x207fFFFFL;
    private static final long REGTEST_GENESIS_BLOCK_TIME = 1296688602L;
    private static final long REGTEST_GENESIS_BLOCK_NONCE = 2L;
    private static final String REGTEST_GENESIS_HASH = "0f9188f13cb7b2c71f2a335e3a4fc328bf5beb436012afca590b1a11466e2206"; //TODO: calculate

    private static final String MAIN_ALERT_KEY = "048240a8748a80a286b270ba126705ced4f2ce5a7847b3610ea3c06513150dade2a8512ed5ea86320824683fc0818f0ac019214973e677acd1244f6d0571fc5103";
    private static final String TEST_ALERT_KEY = "04517d8a699cb43d3938d7b24faaff7cda448ca4ea267723ba614784de661949bf632d6304316b244646dea079735b9a6fc4af804efb4752075b9fe2245e14e412";

    static final int ALLOWED_TIME_DRIFT = 2 * 60 * 60; // Same value as official client.

    public static final long INSTANTX_FEE = 1000000;

    @Override
    public void checkpointsSanityCheck(CheckpointManager checkpointStore, Map checkpoints, StandardNetworkId networkId) {
        checkState(checkpointStore.numCheckpoints() == checkpoints.size());
        // TODO: check some block
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getSignedMessageName() {
        return SIGNING_NAME;
    }

    @Override
    public String getTicker() {
        return TICKER;
    }

    @Override
    public String getUriScheme() {
        return URI_SCHEME;
    }

    @Override
    public int getProtocolVersion() {
        return PROTOCOL_VERSION;
    }

    @Override
    public boolean isCheckpointingSupported() {
        return CHECKPOINTING_SUPPORT;
    }

    @Override
    public int getCheckpointDaysBack() {
        return CHECKPOINT_DAYS_BACK;
    }

    @Override
    public long getEasiestDifficultyTarget() {
        return GENESIS_BLOCK_DIFFICULTY_TARGET;
    }

    @Override
    public int getTargetTimespan(Block block, int height, StandardNetworkId standardNetworkId) {
        return TARGET_TIMESPAN;
    }

    @Override
    public int getTargetSpacing(Block block, int height, StandardNetworkId standardNetworkId) {
        return TARGET_SPACING;
    }

    @Override
    public int getInterval(Block block, int height, StandardNetworkId standardNetworkId) {
        return (Integer) impossibleNullCheck(networkCheck(INTERVAL, INTERVAL, INTERVAL_REGTEST, standardNetworkId));
    }

    @Override
    public int getAllowedBlockTimeDrift(StandardNetworkId networkId) {
        return ALLOWED_TIME_DRIFT;
    }

    @Override
    public int getIntervalCheckpoints(Block block, int height, StandardNetworkId standardNetworkId) {
        return (Integer) impossibleNullCheck(networkCheck(INTERVAL, INTERVAL, INTERVAL_REGTEST, standardNetworkId));
    }

    @Override
    public long getBlockReward(Block block, Block prevBlock, int prevHeight, StandardNetworkId networkId) {
        long prevDiffTarget = prevBlock.getDifficultyTarget();
        double diff = (double) 0x0000ffff / (double) (prevDiffTarget & 0x00ffffff);

        if (prevHeight > 4500 || isTestNet(networkId)) {
            diff = CommonUtils.convertBitsToDouble(prevDiffTarget);
        }

        int subsidy;
        if (prevHeight >= 5465) {
            if ( (prevHeight >= 17000 && diff > 75) || prevHeight >= 24000) { // GPU/ASIC difficulty calc
                subsidy = (int) (2222222.0 / (Math.pow((diff + 2600.0) / 9.0, 2.0)));
                if (subsidy > 25) subsidy = 25;
                if (subsidy < 5) subsidy = 5;
            } else { // CPU mining calc
                subsidy = (int) (11111.0 / (Math.pow((diff + 51.0) / 6.0, 2.0)));
                if (subsidy > 500) subsidy = 500;
                if (subsidy < 25) subsidy = 25;
            }
        } else {
            subsidy = (int) (1111.0 / (Math.pow((diff + 1.0), 2.0)));
            if (subsidy > 500) subsidy = 500;
            if (subsidy < 1) subsidy = 1;
        }

        subsidy *= Coin.COIN_VALUE;

        if (isTestNet(networkId)) {
            for (int i = 46200; i <= prevHeight; i += 210240) {
                subsidy -= subsidy / 14;
            }
        } else {
            // yearly decline of production by 7.1% per year, projected 21.3M coins max by year 2050.
            for (int i = 210240; i <= prevHeight; i += 210240) {
                subsidy -= subsidy / 14;
            }
        }

        return subsidy;
    }

    @Override
    public int getSubsidyDecreaseBlockCount(StandardNetworkId networkId) {
        return (Integer) impossibleNullCheck(networkCheck(SUBSIDY_DECREASE_BLOCK_COUNT, SUBSIDY_DECREASE_BLOCK_COUNT, REGTEST_SUBSIDY_DECREASE_BLOCK_COUNT, networkId));
    }

    @Override
    public int getSpendableDepth(StandardNetworkId networkId) {
        return SPENDABLE_COINBASE_DEPTH;
    }

    @Override
    public long getMaxCoins() {
        return MAX_COINS;
    }

    @Override
    public BigInteger getProofOfWorkLimit(StandardNetworkId networkId) {
        if (networkId.str().equals("unitTest")) {
            return UNITTEST_MAX_TARGET;
        }
        return (BigInteger) networkCheck(MAIN_MAX_TARGET, MAIN_MAX_TARGET, REGTEST_MAX_TARGET, networkId);
    }

    @Override
    public long getDefaultMinTransactionFee() {
        return REFERENCE_DEFAULT_MIN_TX_FEE;
    }

    @Override
    public long getDustLimit() {
        return MIN_NONDUST_OUTPUT;
    }

    @Override
    public int getMaxBlockSize() {
        return MAX_BLOCK_SIZE;
    }

    @Override
    public int getPort(StandardNetworkId networkId) {
        return (Integer) impossibleNullCheck(networkCheck(PORT, TEST_PORT, REGTEST_PORT, networkId));
    }

    @Override
    public int getPubkeyAddressHeader(StandardNetworkId networkId) {
        return (Integer) impossibleNullCheck(networkCheck(PUBKEY_ADDRESS_HEADER, TEST_PUBKEY_ADDRESS_HEADER, TEST_PUBKEY_ADDRESS_HEADER, networkId));
    }

    @Override
    public int getDumpedPrivateKeyHeader(StandardNetworkId networkId) {
        return (Integer) impossibleNullCheck(networkCheck(DUMPED_PRIVATE_KEY_HEADER,
                TEST_DUMPED_PRIVATE_KEY_HEADER, TEST_DUMPED_PRIVATE_KEY_HEADER, networkId));
    }

    @Override
    public int getP2shAddressHeader(StandardNetworkId networkId) {
        return (Integer) impossibleNullCheck(networkCheck(P2SH_ADDRESS_HEADER, TEST_P2SH_ADDRESS_HEADER, TEST_P2SH_ADDRESS_HEADER, networkId));
    }

    @Override
    public void initCheckpoints(CheckpointsContainer container) {
        container.put(1500, "000000aaf0300f59f49bc3e970bad15c11f961fe2347accffff19d96ec9778e3");
        container.put(4991, "000000003b01809551952460744d5dbb8fcbd6cbae3c220267bf7fa43f837367");
        container.put(9918, "00000000213e229f332c0ffbe34defdaa9e74de87f2d8d1f01af8d121c3c170b");
        container.put(16912, "00000000075c0d10371d55a60634da70f197548dbbfa4123e12abfcbc5738af9");
        container.put(23912, "0000000000335eac6703f3b1732ec8b2f89c3ba3a7889e5767b090556bb9a276");
        container.put(35457, "0000000000b0ae211be59b048df14820475ad0dd53b9ff83b010f71a77342d9f");
        container.put(45479, "000000000063d411655d590590e16960f15ceea4257122ac430c6fbe39fbf02d");
        container.put(55895, "0000000000ae4c53a43639a4ca027282f69da9c67ba951768a20415b6439a2d7");
        container.put(68899, "0000000000194ab4d3d9eeb1f2f792f21bb39ff767cb547fe977640f969d77b7");
        container.put(74619, "000000000011d28f38f05d01650a502cc3f4d0e793fbc26e2a2ca71f07dc3842");
        container.put(75095, "0000000000193d12f6ad352a9996ee58ef8bdc4946818a5fec5ce99c11b87f0d");
        container.put(88805, "00000000001392f1652e9bf45cd8bc79dc60fe935277cd11538565b4a94fa85f");
        container.put(107996, "00000000000a23840ac16115407488267aa3da2b9bc843e301185b7d17e4dc40");
        container.put(137993, "00000000000cf69ce152b1bffdeddc59188d7a80879210d6e5c9503011929c3c");
        container.put(167996, "000000000009486020a80f7f2cc065342b0c2fb59af5e090cd813dba68ab0fed");
        container.put(207992, "00000000000d85c22be098f74576ef00b7aa00c05777e966aff68a270f1e01a5");
        container.put(217752, "00000000000a7baeb2148272a7e14edf5af99a64af456c0afc23d15a0918b704");
    }

    @Override
    public long getPacketMagic(StandardNetworkId networkId) {
        return (Long) impossibleNullCheck(networkCheck(MAIN_PACKET_MAGIC, TEST_PACKET_MAGIC, REGTEST_PACKET_MAGIC, networkId));
    }

    @Override
    public GenesisBlockInfo getGenesisBlockInfo(StandardNetworkId networkId) {
        final GenesisBlockInfo.GenesisBlockInfoBuilder builder = new GenesisBlockInfo.GenesisBlockInfoBuilder();
        builder.setGenesisTxInBytes(GENESIS_TX_IN_BYTES);
        builder.setGenesisTxOutBytes(GENESIS_TX_OUT_BYTES);
        builder.setGenesisBlockValue(GENESIS_BLOCK_VALUE);

        if (MAIN_NETWORK_STANDARD.equals(networkId)) {
            builder.setGenesisBlockDifficultyTarget(GENESIS_BLOCK_DIFFICULTY_TARGET);
            builder.setGenesisBlockTime(MAIN_GENESIS_BLOCK_TIME);
            builder.setGenesisBlockNonce(MAIN_GENESIS_BLOCK_NONCE);
            builder.setGenesisHash(MAIN_GENESIS_HASH);
        } else if (TEST_NETWORK_STANDARD.equals(networkId)) {
            builder.setGenesisBlockDifficultyTarget(GENESIS_BLOCK_DIFFICULTY_TARGET);
            builder.setGenesisBlockTime(TEST_GENESIS_BLOCK_TIME);
            builder.setGenesisBlockNonce(TEST_GENESIS_BLOCK_NONCE);
            builder.setGenesisHash(TEST_GENESIS_HASH);
        } else if (REG_TEST_STANDARD.equals(networkId)) {
            builder.setGenesisBlockDifficultyTarget(REGTEST_GENESIS_BLOCK_DIFFICULTY_TARGET);
            builder.setGenesisBlockTime(REGTEST_GENESIS_BLOCK_TIME);
            builder.setGenesisBlockNonce(REGTEST_GENESIS_BLOCK_NONCE);
            builder.setGenesisHash(REGTEST_GENESIS_HASH);
        } else {
            throw new NonStandardNetworkException(networkId.str(), NAME);
        }

        return builder.build();
    }

    @Override
    @Nullable
    public String[] getDnsSeeds(StandardNetworkId networkId) {
        return (String[]) networkCheck(MAIN_DNS_SEEDS, TEST_DNS_SEEDS, null, networkId);
    }

    @Override
    public String getAlertKey(StandardNetworkId networkId) {
        return (String) networkCheck(MAIN_ALERT_KEY, TEST_ALERT_KEY, MAIN_ALERT_KEY, networkId);
    }

    @Override
    public String getIdMainNet() {
        return ID_MAINNET;
    }

    @Override
    public String getIdTestNet() {
        return ID_TESTNET;
    }

    @Override
    public String getIdRegTest() {
        return ID_REGTEST;
    }

    @Override
    public String getIdUnitTestNet() {
        return ID_UNITTESTNET;
    }

    @Override
    public String getPaymentProtocolId(StandardNetworkId networkId) {
        return (String) networkCheck(PAYMENT_PROTOCOL_ID_MAINNET, PAYMENT_PROTOCOL_ID_TESTNET, null, networkId);
    }

    @Override
    public int getMinBroadcastConnections() {
        return MIN_BROADCAST_CONNECTIONS;
    }

    @Override
    public boolean isBitcoinPrivateKeyAllowed() {
        return false;
    }

    @Override
    public int getAllowedPrivateKey() {
        return 0;
    }

    @Override
    public boolean isBloomFilteringSupported(VersionMessage versionInfo) {
        return versionInfo.getClientVersion() >= BLOOM_FILTERING_MIN_PROTOCOL_VERSION;
    }

    @Override
    public boolean hasBlockChain(VersionMessage versionInfo) {
        return (versionInfo.getLocalServices() & NODE_NETWORK) == NODE_NETWORK;
    }

    @Override
    public boolean isGetUTXOsSupported(VersionMessage versionInfo) {
        return false;
    }

    @Override
    public boolean isPingPongSupported(VersionMessage versionInfo) {
        return versionInfo.getClientVersion() >= PONG_MIN_PROTOCOL_VERSION;
    }

    @Nullable
    @Override
    public Integer getNodeBloomConstant() {
        return null;
    }

    @Nullable
    @Override
    public Integer getNodeNetworkConstant() {
        return NODE_NETWORK;
    }

    @Nullable
    @Override
    public Integer getNodeGetUtxosConstant() {
        return null;
    }

    @Nullable
    @Override
    public Integer getNodePongConstant() {
        return null;
    }

    @Override
    public int getMinBloomProtocolVersion() {
        return BLOOM_FILTERING_MIN_PROTOCOL_VERSION;
    }

    @Override
    public int getMinPongProtocolVersion() {
        return PONG_MIN_PROTOCOL_VERSION;
    }

    @Override
    public BlockHasher createBlockHasher() {
        return new DashBlockHasher();
    }

    @Override
    public BlockExtension createBlockExtension(Block block) {
        return EmptyBlockExtension.INSTANCE;
    }

    @Override
    public DashTransactionExtension createTransactionExtension(Transaction transaction) {
        return new DashTransactionExtension(transaction);
    }

    @Override
    public BlockChainExtension createBlockChainExtension(AbstractBlockChain blockChain) {
        return new DashChainExtension(blockChain, CommonUtils.extractNetworkExtension(blockChain.getParams()), testnetDiffDate);
    }

    @Override
    public CoinSerializerExtension createCoinSerializerExtension() {
        return DashCoinSerializerExtension.INSTANCE;
    }

    @Override
    public PeerExtension createPeerExtension(Peer peer) {
        final DashNetwork dashNetwork = CommonUtils.extractNetworkExtension(peer.getParams());
        dashNetwork.setBlockChain(peer.getBlockChain());
        dashNetwork.showPeer(peer);
        return new DashPeerExtension(peer, dashNetwork);
    }

    @Override
    public PeerGroupExtension createPeerGroupExtension(PeerGroup peerGroup) {
        final DashNetwork dashNetwork = CommonUtils.extractNetworkExtension(peerGroup.getParams());
        dashNetwork.setBlockChain(peerGroup.getBlockChain());
        dashNetwork.setPeerGroup(peerGroup);
        return new DashPeerGroupExtension(dashNetwork);
    }

    @Override
    public TransactionConfidenceExtension createTransactionConfidenceExtension(TransactionConfidence transactionConfidence) {
        return new DashTransactionConfidenceExtension(transactionConfidence);
    }

    @Override
    public WalletCoinSpecifics createWalletCoinSpecifics(Wallet wallet) {
        final DashNetwork dashNetwork = CommonUtils.extractNetworkExtension(wallet.getParams());
        final DashWalletCoinSpecifics walletSpec = new DashWalletCoinSpecifics(wallet, dashNetwork);
        dashNetwork.getInstantXManager().registerWallet(walletSpec);
        return walletSpec;
    }

    @Override
    public WalletProtobufSerializerExtension createWalletProtobufSerializerExtension(WalletProtobufSerializer walletProtobufSerializer) {
        return DashWalletProtobufSerializerExtension.INSTANCE;
    }

    @Override
    public NetworkExtensionsContainer createNetworkExtensionsContainer(NetworkParameters params) {
        return createNetworkExtensionsContainer(params, null);
    }

    @Override
    public NetworkExtensionsContainer createNetworkExtensionsContainer(NetworkParameters params, @Nullable NetworkMode networkMode) {
        final DashNetwork dashNetwork = new DashNetwork(params, networkMode == null ? DashNetwork.Mode.LITE_MODE : (DashNetwork.Mode) networkMode);
        dashNetwork.start();
        return dashNetwork;
    }

    @Nullable
    @Override
    public String getInventoryTypeByCode(int typeCode) {
        switch (typeCode) {
            case INV_ORDINAL_TRANSACTION_LOCK_REQUEST:
                return INV_TYPE_TRANSACTION_LOCK_REQUEST;
            case INV_ORDINAL_TRANSACTION_LOCK_VOTE:
                return INV_TYPE_TRANSACTION_LOCK_VOTE;
            case INV_ORDINAL_SPORK:
                return INV_TYPE_SPORK;
            case INV_ORDINAL_MASTERNODE_WINNER:
                return INV_TYPE_MASTERNODE_WINNER;
            case INV_ORDINAL_MASTERNODE_SCANNING_ERROR:
                return INV_TYPE_MASTERNODE_SCANNING_ERROR;
            case INV_ORDINAL_BUDGET_VOTE:
                return INV_TYPE_BUDGET_VOTE;
            case INV_ORDINAL_BUDGET_PROPOSAL:
                return INV_TYPE_BUDGET_PROPOSAL;
            case INV_ORDINAL_BUDGET_FINALIZED:
                return INV_TYPE_BUDGET_FINALIZED;
            case INV_ORDINAL_BUDGET_FINALIZED_VOTE:
                return INV_TYPE_BUDGET_FINALIZED_VOTE;
            case INV_ORDINAL_MASTERNODE_QUORUM:
                return INV_TYPE_MASTERNODE_QUORUM;
            case INV_ORDINAL_MASTERNODE_ANNOUNCE:
                return INV_TYPE_MASTERNODE_ANNOUNCE;
            case INV_ORDINAL_MASTERNODE_PING:
                return INV_TYPE_MASTERNODE_PING;
            default:
                return null;
        }
    }

    @Nullable
    @Override
    public Integer getInventoryTypeOrdinal(String type) {
        if (type.equals(INV_TYPE_TRANSACTION_LOCK_REQUEST)) {
            return INV_ORDINAL_TRANSACTION_LOCK_REQUEST;
        } else if (type.equals(INV_TYPE_TRANSACTION_LOCK_VOTE)) {
            return INV_ORDINAL_TRANSACTION_LOCK_VOTE;
        } else if (type.equals(INV_TYPE_SPORK)) {
            return INV_ORDINAL_SPORK;
        } else if (type.equals(INV_TYPE_MASTERNODE_WINNER)) {
            return INV_ORDINAL_MASTERNODE_WINNER;
        } else if (type.equals(INV_TYPE_MASTERNODE_SCANNING_ERROR)) {
            return INV_ORDINAL_MASTERNODE_SCANNING_ERROR;
        } else if (type.equals(INV_TYPE_BUDGET_VOTE)) {
            return INV_ORDINAL_BUDGET_VOTE;
        } else if (type.equals(INV_TYPE_BUDGET_PROPOSAL)) {
            return INV_ORDINAL_BUDGET_PROPOSAL;
        } else if (type.equals(INV_TYPE_BUDGET_FINALIZED)) {
            return INV_ORDINAL_BUDGET_FINALIZED;
        } else if (type.equals(INV_TYPE_BUDGET_FINALIZED_VOTE)) {
            return INV_ORDINAL_BUDGET_FINALIZED_VOTE;
        } else if (type.equals(INV_TYPE_MASTERNODE_QUORUM)) {
            return INV_ORDINAL_MASTERNODE_QUORUM;
        } else if (type.equals(INV_TYPE_MASTERNODE_ANNOUNCE)) {
            return INV_ORDINAL_MASTERNODE_ANNOUNCE;
        } else if (type.equals(INV_TYPE_MASTERNODE_PING)) {
            return INV_ORDINAL_MASTERNODE_PING;
        } else {
            return null;
        }
    }

    private static final long testnetDiffDate = 1329264000000L;

    static final String INV_TYPE_TRANSACTION_LOCK_REQUEST = "TransactionLockRequest";
    static final String INV_TYPE_TRANSACTION_LOCK_VOTE = "TransactionLockVote";
    static final String INV_TYPE_SPORK = "Spork";
    static final String INV_TYPE_MASTERNODE_WINNER = "MasternodeWinner";
    static final String INV_TYPE_MASTERNODE_SCANNING_ERROR = "MasternodeScanningError";
    static final String INV_TYPE_BUDGET_VOTE = "BudgetVote";
    static final String INV_TYPE_BUDGET_PROPOSAL = "BudgetProposal";
    static final String INV_TYPE_BUDGET_FINALIZED = "BudgetFinalized";
    static final String INV_TYPE_BUDGET_FINALIZED_VOTE = "BudgetFinalizedVote";
    static final String INV_TYPE_MASTERNODE_QUORUM = "MasternodeQuorum";
    static final String INV_TYPE_MASTERNODE_ANNOUNCE = "MasternodeAnnounce";
    static final String INV_TYPE_MASTERNODE_PING = "MasternodePing";
    static final int INV_ORDINAL_TRANSACTION_LOCK_REQUEST = 4;
    static final int INV_ORDINAL_TRANSACTION_LOCK_VOTE = 5;
    static final int INV_ORDINAL_SPORK = 6;
    static final int INV_ORDINAL_MASTERNODE_WINNER = 7;
    static final int INV_ORDINAL_MASTERNODE_SCANNING_ERROR = 8;
    static final int INV_ORDINAL_BUDGET_VOTE = 9;
    static final int INV_ORDINAL_BUDGET_PROPOSAL = 10;
    static final int INV_ORDINAL_BUDGET_FINALIZED = 11;
    static final int INV_ORDINAL_BUDGET_FINALIZED_VOTE = 12;
    static final int INV_ORDINAL_MASTERNODE_QUORUM = 13;
    static final int INV_ORDINAL_MASTERNODE_ANNOUNCE = 14;
    static final int INV_ORDINAL_MASTERNODE_PING = 15;

    @Nullable
    private static Object networkCheck(@Nullable Object first, @Nullable Object second, @Nullable Object third, StandardNetworkId networkId) {
        return Util.networkCheck(first, second, third, networkId, NAME);
    }

    private boolean isTestNet(StandardNetworkId networkId) {
        return TEST_NETWORK_STANDARD.equals(networkId);
    }

    public static final String SPORK_KEY_MAIN = "04549ac134f694c0243f503e8c8a9a986f5de6610049c40b07816809b0d1d06a21b07be27b9bb555931773f62ba6cf35a25fd52f694d4e1106ccd237a7bb899fdd";
    public static final String SPORK_KEY_TEST = "046f78dcf911fbd61910136f7f0f8d90578f68d0b3ac973b5040fb7afb501b5939f39b108b0569dca71488f5bbf498d92e4d1194f6f941307ffd95f75e76869f0e";


}
