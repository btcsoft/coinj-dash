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

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.googlecode.concurrentlinkedhashmap.EvictionListener;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

/**
 * Date: 5/21/15
 * Time: 12:36 AM
 *
 * @author Mikhail Kulikov
 */
@ThreadSafe
public final class MasternodeManager implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(MasternodeManager.class);

    public static final int MASTERNODES_DSEG_SECONDS = 3 * 60 * 60;
    private static final String CACHE_FILE_NAME = "mncache.dat";
    private static final String CACHE_FILE_MAGIC_MESSAGE = "MasternodeCache";

    private static final Lock fileLock = Threading.lock(CACHE_FILE_MAGIC_MESSAGE);

    static MasternodeManager construct(NetworkParameters params) {
        final MasternodeManager masternodeManager = readFromFile(params);
        return masternodeManager != null
                ? masternodeManager
                : new MasternodeManager(params);
    }

    private final NetworkParameters params;

    private final ConcurrentHashMap<TransactionInput, MasternodeData> masternodesContainer = new ConcurrentHashMap<TransactionInput, MasternodeData>(1100, 0.95f, 4);

    // who've asked for the Masternode list and the last time
    private final ConcurrentHashMap<PeerAddress, Long> askedUsForMasternodeList = new ConcurrentHashMap<PeerAddress, Long>(100, 0.75f, 4);
    // which Masternodes we've asked for
    private final ConcurrentHashMap<TransactionOutPoint, Long> weAskedForMasternodeListEntry = new ConcurrentHashMap<TransactionOutPoint, Long>(1100, 0.95f, 4);
    // which nodes we've asked for Masternodes list
    private final ConcurrentHashMap<PeerAddress, Long> weAskedForMasternodeList = new ConcurrentHashMap<PeerAddress, Long>(100, 0.95f, 1);

    // Keep track of all broadcasts I've seen
    private final MessagesRegistry<MasternodeBroadcast> masternodeBroadcastRegistry =
            new MessagesRegistry<MasternodeBroadcast>(100, 1000, 4, new EvictionListener<Sha256Hash, MessagesRegistry.Entry<MasternodeBroadcast>>() {
        @Override
        public void onEviction(Sha256Hash key, MessagesRegistry.Entry<MasternodeBroadcast> value) {
            if (logger.isDebugEnabled()) {
                if (value.message != null) {
                    logger.debug("Forgot about seen masternode broadcast - {}:{};{}.", value.message.getAddr(), value.message.getTxIn(), value.message.getProtocolVersion());
                } else {
                    logger.debug("Forgot about seen masternode broadcast as an INV with hash {}", key);
                }
            }
        }
    });
    // Keep track of all pings I've seen
    private final MessagesRegistry<MasternodePing> masternodePingRegistry =
            new MessagesRegistry<MasternodePing>(100, 1000, 4, new EvictionListener<Sha256Hash, MessagesRegistry.Entry<MasternodePing>>() {
                @Override
                public void onEviction(Sha256Hash key, MessagesRegistry.Entry<MasternodePing> value) {
                    if (logger.isDebugEnabled()) {
                        if (value.message != null) {
                            logger.debug("Forgot about seen masternode ping - {}:{};{}.",
                                    value.message.getTxIn(), Utils.HEX.encode(value.message.getSignature()), value.message.getSigTime());
                        } else {
                            logger.debug("Forgot about seen masternode ping as an INV with hash {}", key);
                        }
                    }
                }
            });
    // Keep track of all errors I've seen
    private final MessagesRegistry<MasternodeScanningError> masternodeScanningErrorsRegistry =
            new MessagesRegistry<MasternodeScanningError>(100, 1000, 4, new EvictionListener<Sha256Hash, MessagesRegistry.Entry<MasternodeScanningError>>() {
                @Override
                public void onEviction(Sha256Hash key, MessagesRegistry.Entry<MasternodeScanningError> value) {
                    if (logger.isDebugEnabled()) {
                        if (value.message != null) {
                            logger.debug("Forgot about seen masternode scanning error - A:{}, B:{}; error type {} at {}.",
                                    value.message.getTxInMasternodeA(), value.message.getTxInMasternodeB(), value.message.getErrorType(), value.message.getBlockHeight());
                        } else {
                            logger.debug("Forgot about seen masternode scanning error as an INV with hash {}", key);
                        }
                    }
                }
            });

    private final AtomicInteger aboveProtocolCounter = new AtomicInteger(0);

    private MasternodeManager(NetworkParameters params) {
        this.params = params;
    }

    public NetworkParameters getParams() {
        return params;
    }

    public MasternodeData find(TransactionInput txIn) {
        return masternodesContainer.get(txIn);
    }

    boolean add(MasternodeBroadcast mb) {
        return _add(new MasternodeData(mb));
    }

    private boolean _add(MasternodeData mn) {
        if (!mn.isEnabled())
            return false;

        final boolean added =
                (masternodesContainer.putIfAbsent(mn.getTxIn(), mn) == null);
        if (added) {
            if (mn.getProtocolVersion() >= DashDefinition.DARKSEND_POOL_MIN_PROTOCOL_VERSION) {
                aboveProtocolCounter.incrementAndGet();
            }
            if (logger.isDebugEnabled()) {
                logger.debug("MasternodeMan: Adding new Masternode {} - {} now", mn.getAddr(), size());
            }
        }
        return added;
    }

    void disableCallback() {
        aboveProtocolCounter.decrementAndGet();
    }
    void enableCallback() {
        aboveProtocolCounter.incrementAndGet();
    }

    int size() {
        return masternodesContainer.size();
    }

    boolean didWeAskForListRecently(TransactionInput txIn) {
        return universalListAskCheck(weAskedForMasternodeListEntry, txIn.getOutpoint());
    }

    boolean didPeerAskForListRecently(PeerAddress peerAddress) {
        return universalListAskCheck(askedUsForMasternodeList, peerAddress);
    }

    private static boolean universalListAskCheck(ConcurrentHashMap map, Object key) {
        final Long t = (Long) map.get(key);
        if (t != null) {
            if (Utils.currentTimeSeconds() < t)
                return true;
        }
        return false;
    }

    boolean registerOurAskForList(TransactionInput input) {
        if (didWeAskForListRecently(input)) {
            return false;
        }
        long askAgainTime = Utils.currentTimeSeconds() + MasternodeData.MASTERNODE_MIN_MNP_SECONDS;
        return weAskedForMasternodeListEntry.putIfAbsent(input.getOutpoint(), askAgainTime) == null;
    }

    boolean registerPeerAskForList(PeerAddress peerAddress) {
        if (didPeerAskForListRecently(peerAddress)) {
            return false;
        }
        long askAgainTime = Utils.currentTimeSeconds() + MASTERNODES_DSEG_SECONDS;
        return askedUsForMasternodeList.putIfAbsent(peerAddress, askAgainTime) == null;
    }

    Iterable<MasternodeData> getMasternodesIterableSnapshot() {
        return masternodesContainer.values();
    }

    int countMasternodesAboveProtocol() {
        return aboveProtocolCounter.get();
    }

    @Nullable
    MasternodeData getMasternodeByRank(int rank, int blockHeight, int protoVersion, AbstractBlockChain blockChain) {
        final ArrayList<RankedMasternode> sortedRanking = buildRanking(blockHeight, blockChain, true);

        if (sortedRanking == null)
            return null;

        int i = 0;
        for (final RankedMasternode rankedMasternode : sortedRanking) {
            if (rankedMasternode.data.getProtocolVersion() < protoVersion)
                continue;
            i++;
            if (i == rank)
                return rankedMasternode.data;
        }

        return null;
    }

    // called from a single thread (masternodes daemon) so racing conditions aren't possible
    boolean dsegUpdate(Peer p) {
        final PeerAddress address = p.getAddress();

        final Long when = weAskedForMasternodeList.get(address);
        if (when != null && Utils.currentTimeSeconds() < when) {
            logger.info("dseg - we already asked {} for the list; skipping...", address);
            return false;
        }

        p.sendMessage(new MasternodeListRequest(params, new TransactionInput(params, null, TransactionInput.EMPTY_ARRAY)));
        weAskedForMasternodeList.put(address, Utils.currentTimeMillis() + MASTERNODES_DSEG_SECONDS);
        return true;
    }

    static final class RankedMasternode {
        private final BigInteger score;
        private final MasternodeData data;
        private RankedMasternode(MasternodeData data, BigInteger score) {
            this.data = data;
            this.score = score;
        }
    }

    int getMasternodeRank(TransactionInput txIn, int blockHeight, AbstractBlockChain blockChain, boolean onlyActive) {
        return getMasternodeRank(
                txIn,
                buildRanking(blockHeight, blockChain, onlyActive),
                false
        );
    }

    @Nullable
    ArrayList<RankedMasternode> buildRanking(int blockHeight, AbstractBlockChain blockChain) {
        return buildRanking(blockHeight, blockChain, false);
    }

    int getMasternodeRank(TransactionInput txIn, @Nullable ArrayList<RankedMasternode> sortedRanking, boolean onlyActive) {
        if (sortedRanking != null) {
            int i = 0;
            for (final RankedMasternode pair : sortedRanking) {
                if (skipRankIfOnlyActive(pair.data, onlyActive)) {
                    continue;
                }
                i++;
                if (pair.data.getTxIn().equals(txIn)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Nullable
    private ArrayList<RankedMasternode> buildRanking(int blockHeight, AbstractBlockChain blockChain, boolean onlyActive) {
        final DashChainExtension chainExtension = CommonUtils.extractChainExtension(blockChain, params.getCoinDefinition());
        if (chainExtension.getHashByHeight(blockHeight - 1) == null) {
            return null;
        }

        final ArrayList<RankedMasternode> ranking = Lists.newArrayListWithCapacity(masternodesContainer.size());
        for (final MasternodeData data : masternodesContainer.values()) {
            if (data.getProtocolVersion() < DashDefinition.DARKSEND_POOL_MIN_PROTOCOL_VERSION){
                continue;
            }
            if (skipRankIfOnlyActive(data, onlyActive)) {
                continue;
            }

            ranking.add(new RankedMasternode(data, data.calculateScore(blockHeight - 1, chainExtension)));
        }

        Collections.sort(ranking, new Comparator<RankedMasternode>() {
            @Override
            public int compare(RankedMasternode p1, RankedMasternode p2) {
                return p1.score.compareTo(p2.score);
            }
        });

        return ranking;
    }

    private boolean skipRankIfOnlyActive(MasternodeData data, boolean onlyActive) {
        if (onlyActive) {
            if (!data.check(this).equals(MasternodeStateInfo.State.MASTERNODE_ENABLED)) {
                return true;
            }
        }
        return false;
    }

    void validateMasternodesAgainstTransactionOutpoints(Transaction transaction) {
        final List<TransactionInput> inputs = transaction.getInputs();
        final ArrayList<TransactionOutPoint> txOutpoints = Lists.newArrayListWithCapacity(inputs.size() + 1);
        for (final TransactionInput i : inputs) {
            txOutpoints.add(i.getOutpoint());
        }

        for (final MasternodeData masternodeData : masternodesContainer.values()) {
            final TransactionOutPoint mnOutpoint = masternodeData.getTxIn().getOutpoint();
            for (TransactionOutPoint outpoint : txOutpoints) {
                if (mnOutpoint.getIndex() == outpoint.getIndex() && mnOutpoint.getHash().equals(outpoint.getHash())) {
                    // gotcha! masternode's 1000 DASH got spent
                    masternodeData.reportTxInSpent();
                    break;
                }
            }
        }
    }

    void performDump() {
        writeToFile();
    }

    MessagesRegistry<MasternodePing> getMasternodePingRegistry() {
        return masternodePingRegistry;
    }

    MessagesRegistry<MasternodeBroadcast> getMasternodeBroadcastRegistry() {
        return masternodeBroadcastRegistry;
    }

    MessagesRegistry<MasternodeScanningError> getMasternodeScanningErrorsRegistry() {
        return masternodeScanningErrorsRegistry;
    }

    void checkAndResolve() {
        final Collection<MasternodeData> masternodesSnapshot = masternodesContainer.values();
        final Iterable<Map.Entry<PeerAddress, Long>> askedUsForListSnapshot= askedUsForMasternodeList.entrySet();
        final Iterable<Map.Entry<TransactionOutPoint, Long>> weAskedForListEntrySnapshot= weAskedForMasternodeListEntry.entrySet();
        final Iterable<Map.Entry<PeerAddress, Long>> weAskedForListSnapshot= weAskedForMasternodeList.entrySet();

        //remove inactive
        for (final MasternodeData masternodeData : masternodesSnapshot) {
            masternodeData.check(this);
            final MasternodeStateInfo.State activeState = masternodeData.getActiveState();
            if (activeState == MasternodeStateInfo.State.MASTERNODE_REMOVE || activeState == MasternodeStateInfo.State.MASTERNODE_VIN_SPENT) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Removing inactive Masternode {} - {} now", masternodeData.getAddr(), masternodesSnapshot.size() - 1);
                }
                masternodesContainer.remove(masternodeData.getTxIn());
            }
        }

        // check who's asked for the Masternode list
        for (final Map.Entry<PeerAddress, Long> entry : askedUsForListSnapshot) {
            if (entry.getValue() < Utils.currentTimeSeconds()) {
                askedUsForMasternodeList.remove(entry.getKey());
            }
        }

        // check who we asked for the Masternode list
        for (final Map.Entry<PeerAddress, Long> entry : weAskedForListSnapshot) {
            if (entry.getValue() < Utils.currentTimeSeconds()) {
                weAskedForMasternodeList.remove(entry.getKey());
            }
        }

        for (final Map.Entry<TransactionOutPoint, Long> entry : weAskedForListEntrySnapshot) {
            if (entry.getValue() < Utils.currentTimeSeconds()) {
                weAskedForMasternodeListEntry.remove(entry.getKey());
            }
        }
    }

    private void writeToFile() {
        fileLock.lock();
        try {
            final long start = System.currentTimeMillis();

            final File file = new File(CACHE_FILE_NAME);
            DataOutputStream outputStream = null;
            try {
                if (file.createNewFile()) {
                    logger.info("Created new " + CACHE_FILE_NAME);
                }
                final UnsafeByteArrayOutputStream outBuilder = new UnsafeByteArrayOutputStream();
                final ObjectOutputStream dataStream = new ObjectOutputStream(outBuilder);

                final byte[] magicMessage = CACHE_FILE_MAGIC_MESSAGE.getBytes(Charsets.UTF_8);
                dataStream.write(magicMessage);
                dataStream.writeLong(params.getPacketMagic());
                dataStream.writeObject(this);
                dataStream.close();

                final byte[] bytes = outBuilder.toByteArray();

                outputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file, false)));
                outputStream.writeInt(bytes.length);
                outputStream.write(bytes);
                outputStream.write(Sha256Hash.create(bytes).getBytes());
                outputStream.flush();

                logger.info("Written to " + CACHE_FILE_NAME + "; masternodes size is " + masternodesContainer.size());
            } catch (IOException ex) {
                throw new IllegalStateException("IO fault while writing to masternodes cache file", ex);
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        logger.warn("IO exception while closing outputStream", e);
                    }
                }
                logger.info("writeToFile() took {} millis", System.currentTimeMillis() - start);
            }
        } finally {
            fileLock.unlock();
        }
    }

    @Nullable
    private static MasternodeManager readFromFile(NetworkParameters params) {
        fileLock.lock();
        try {
            final long start = System.currentTimeMillis();

            final File file = new File(CACHE_FILE_NAME);
            if (!file.exists()) {
                return null;
            }

            DataInputStream inputStream = null;
            try {
                inputStream = new DataInputStream(new FileInputStream(file));

                final int arraySize = inputStream.readInt();
                final byte[] bytes = new byte[arraySize];
                int read = inputStream.read(bytes);
                if (read < arraySize) {
                    logger.error("Masternodes cache file format fault: serialized message size less than specified by first 4 bytes (specified {}, read {})", arraySize, read);
                    return null;
                }

                final byte[] hashBytes = new byte[32];
                read = inputStream.read(hashBytes);
                if (read < 32) {
                    logger.error("Masternodes cache file format fault: hash bytes read less than 32 bytes: " + read);
                    return null;
                }
                final byte[] calculatedHash = Sha256Hash.create(bytes).getBytes();
                if (!Arrays.equals(hashBytes, calculatedHash)) {
                    logger.error("Masternodes cache file format fault: hashes don't match (calculated {}, read {})", Utils.HEX.encode(calculatedHash), Utils.HEX.encode(hashBytes));
                    return null;
                }

                final ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes));

                final byte[] magicMessage = CACHE_FILE_MAGIC_MESSAGE.getBytes(Charsets.UTF_8);
                final byte[] mmFromFile = new byte[magicMessage.length];
                read = objectInputStream.read(mmFromFile);
                if (read < magicMessage.length || !Arrays.equals(magicMessage, mmFromFile)) {
                    logger.error("Masternodes cache file format fault: unable to read magic message ({})", new String(mmFromFile, Charsets.UTF_8));
                    return null;
                }

                final long readPacketMagic = objectInputStream.readLong();
                if (readPacketMagic != params.getPacketMagic()) {
                    logger.error("Masternodes cache file format fault: packet magic is wrong (needs to be {}, got {})", params.getPacketMagic(), readPacketMagic);
                    return null;
                }

                final MasternodeManager masternodeManager = (MasternodeManager) objectInputStream.readObject();

                logger.info("Read from " + CACHE_FILE_NAME + "; masternodes size is " + masternodeManager.masternodesContainer.size());

                return masternodeManager;
            } catch (IOException ex) {
                logger.error("IO fault while reading masternodes cache file", ex);
            } catch (ClassNotFoundException e) {
                logger.error("Class not found - impossible", e);
            } catch (Throwable th) {
                logger.error("Runtime exception while reading masternodes cache file", th);
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        logger.warn("IO exception while closing inputStream", e);
                    }
                }
                logger.info("readFromFile(NetworkParameters) took {} millis", System.currentTimeMillis() - start);
            }
        } finally {
            fileLock.unlock();
        }

        return null;
    }

}
