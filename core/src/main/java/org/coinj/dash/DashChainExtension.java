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
import com.google.common.collect.Lists;
import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.coinj.api.BlockChainExtension;
import org.coinj.api.CoinDefinition;
import org.coinj.commons.LinearBlockChainExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Date: 5/17/15
 * Time: 2:17 PM
 *
 * @author Mikhail Kulikov
 */
public final class DashChainExtension implements BlockChainExtension {

    private static final Logger log = LoggerFactory.getLogger(DashChainExtension.class);

    private final InnerLinearExtension linearExtension;
    private final DashNetwork network;

    DashChainExtension(AbstractBlockChain blockChain, DashNetwork network, long testnetDiffDate) {
        linearExtension = new InnerLinearExtension(blockChain, testnetDiffDate);
        this.network = network;
    }

    @Override
    public void verifyBlockAddition(Block added, @Nullable List<Sha256Hash> filteredTxHashList, @Nullable Map<Sha256Hash, Transaction> filteredTxn) {
        if (network.permitsMasternodesLogic() && network.getSporkManager().isSporkActive(SporkManager.SPORK_3_INSTANTX_BLOCK_FILTERING)) {
            if (filteredTxHashList != null && filteredTxn != null) {
                List<Transaction> toCheck = Lists.newArrayListWithExpectedSize(filteredTxn.size());
                for (final Sha256Hash txHash : filteredTxHashList) {
                    final Transaction tx = filteredTxn.get(txHash);
                    if (tx != null)
                        toCheck.add(tx);
                }
                if (toCheck.size() > 0) {
                    checkTxs(toCheck, added.getHashAsString());
                }
            } else {
                final List<Transaction> transactions = added.getTransactions();
                if (transactions != null) {
                    checkTxs(transactions, added.getHashAsString());
                }
            }
        }
    }

    private void checkTxs(List<Transaction> transactions, String block) {
        final InstantXManager.TxConflictResult cnfResult = network.getInstantXManager().checkTransactionsConflict(transactions);
        if (cnfResult.isConflicted()) {
            throw new VerificationException("Transaction " + cnfResult.conflictedTx + " from block " + block + " is conflicting with already locked tx " + cnfResult.lockedTx);
        }
    }

    @Override
    public void verifyDifficultyTransitions(StoredBlock prevBlock, Block added, NetworkParameters params) {
        int diffMode = 1;
        final int heightInc = prevBlock.getHeight() + 1;
        if (CoinDefinition.TEST_NETWORK_STANDARD.equals(params.getStandardNetworkId())) {
            if (heightInc >= 2000) {
                diffMode = 4;
            }
        } else {
            if (heightInc >= 68589) {
                diffMode = 4;
            } else if (heightInc >= 34140) {
                diffMode = 3;
            } else if (heightInc >= 15200) {
                diffMode = 2;
            }
        }

        try {
            switch (diffMode) {
                case 4:
                    darkGravityWave3Check(prevBlock, added, linearExtension.getBlockChain().getBlockStore(), params);
                    return;
                case 1:
                    linearExtension.verifyDifficultyTransitions(prevBlock, added, params);
                    return;
                case 2:
                    kimotoGravityWellCheck(prevBlock, added, linearExtension.getBlockChain().getBlockStore(), params);
                    return;
                case 3:
                    darkGravityWaveCheck(prevBlock, added, linearExtension.getBlockChain().getBlockStore(), params);
                    return;
                default:
                    throw new RuntimeException("Unreachable");
            }
        } catch (BlockStoreException ex) {
            throw new VerificationException("Block store exception during difficulty transitions check", ex);
        }
    }

    @Override
    public void onBlockAddition(StoredBlock block) {}

    public AbstractBlockChain getBlockChain() {
        return linearExtension.getBlockChain();
    }

    @Nullable
    Sha256Hash getHashByHeight(int height) {
        Preconditions.checkArgument(height >= 0, "Height mustn't be negative");

        final AbstractBlockChain blockChain = linearExtension.getBlockChain();
        StoredBlock currentBlock = blockChain.getChainHead();
        if (currentBlock == null || currentBlock.getHeight() < height) {
            return null;
        }

        final BlockStore blockStore = blockChain.getBlockStore();
        try {
            while (currentBlock.getHeight() > height) {
                currentBlock = currentBlock.getPrev(blockStore);
                if (currentBlock == null)
                    return null;
            }
            return currentBlock.getHeader().getHash();
        } catch (BlockStoreException ex) {
            log.error("Error while descending the chain", ex);
            return null;
        }
    }

    private static void kimotoGravityWellCheck(StoredBlock prevBlock, Block added, BlockStore store, NetworkParameters params) throws BlockStoreException {
        final long blocksTargetSpacing = (long) (2.5 * 60); // 2.5 minutes
        int timeDaySeconds = 60 * 60 * 24;
        long pastSecondsMin = timeDaySeconds / 40;
        long pastSecondsMax = timeDaySeconds * 7;
        long pastBlocksMin = pastSecondsMin / blocksTargetSpacing;
        long pastBlocksMax = pastSecondsMax / blocksTargetSpacing;

        StoredBlock blockReading = prevBlock;

        long pastBlocksMass = 0;
        long pastRateActualSeconds = 0;
        long pastRateTargetSeconds = 0;
        double pastRateAdjustmentRatio = 1.0f;
        BigInteger pastDifficultyAverage = BigInteger.valueOf(0);
        BigInteger pastDifficultyAveragePrev = BigInteger.valueOf(0);
        double eventHorizonDeviation;
        double eventHorizonDeviationFast;
        double eventHorizonDeviationSlow;

        if (prevBlock == null || prevBlock.getHeight() == 0 || (long) prevBlock.getHeight() < pastBlocksMin) {
            verifyDifficulty(prevBlock, added, params.getMaxTarget(), params);
            return;
        }

        final Block prevHeader = prevBlock.getHeader();
        long latestBlockTime = prevHeader.getTimeSeconds();

        for (int i = 1; blockReading.getHeight() > 0; i++) {
            if (pastBlocksMax > 0 && i > pastBlocksMax) {
                break;
            }
            pastBlocksMass++;

            if (i == 1)	{
                pastDifficultyAverage = blockReading.getHeader().getDifficultyTargetAsInteger();
            } else {
                pastDifficultyAverage = (blockReading.getHeader().getDifficultyTargetAsInteger().subtract(pastDifficultyAveragePrev))
                        .divide(BigInteger.valueOf(i))
                        .add(pastDifficultyAveragePrev);
            }
            pastDifficultyAveragePrev = pastDifficultyAverage;

            if (blockReading.getHeight() > 646120 && latestBlockTime < blockReading.getHeader().getTimeSeconds()) {
                // eliminates the ability to go back in time
                latestBlockTime = blockReading.getHeader().getTimeSeconds();
            }

            pastRateActualSeconds = prevHeader.getTimeSeconds() - blockReading.getHeader().getTimeSeconds();
            pastRateTargetSeconds = blocksTargetSpacing * pastBlocksMass;
            if (blockReading.getHeight() > 646120) {
                //this should slow down the upward difficulty change
                if (pastRateActualSeconds < 5) {
                    pastRateActualSeconds = 5;
                }
            } else {
                if (pastRateActualSeconds < 0) {
                    pastRateActualSeconds = 0;
                }
            }
            if (pastRateActualSeconds != 0 && pastRateTargetSeconds != 0) {
                pastRateAdjustmentRatio	= (double)pastRateTargetSeconds / pastRateActualSeconds;
            }
            eventHorizonDeviation = 1 + 0.7084 * Math.pow((double) pastBlocksMass / 28.2d, -1.228);
            eventHorizonDeviationFast = eventHorizonDeviation;
            eventHorizonDeviationSlow = 1 / eventHorizonDeviation;

            if (pastBlocksMass >= pastBlocksMin) {
                if (pastRateAdjustmentRatio <= eventHorizonDeviationSlow || pastRateAdjustmentRatio >= eventHorizonDeviationFast) {
                    break;
                }
            }
            blockReading = store.get(blockReading.getHeader().getPrevBlockHash());
            if (blockReading == null) {
                return;
            }
        }

        BigInteger newDifficulty = pastDifficultyAverage;
        if (pastRateActualSeconds != 0 && pastRateTargetSeconds != 0) {
            newDifficulty = newDifficulty.multiply(BigInteger.valueOf(pastRateActualSeconds));
            newDifficulty = newDifficulty.divide(BigInteger.valueOf(pastRateTargetSeconds));
        }

        if (newDifficulty.compareTo(params.getMaxTarget()) > 0) {
            log.info("Difficulty hit proof of work limit: {}", newDifficulty.toString(16));
            newDifficulty = params.getMaxTarget();
        }

        verifyDifficulty(prevBlock, added, newDifficulty, params);
    }

    private static void darkGravityWaveCheck(StoredBlock prevBlock, Block added, BlockStore store, NetworkParameters params) {
        StoredBlock blockReading = prevBlock;

        long blockTimeAverage = 0;
        long blockTimeAveragePrev = 0;
        long blockTimeCount = 0;
        long blockTimeSum2 = 0;
        long blockTimeCount2 = 0;
        long lastBlockTime = 0;
        long pastBlocksMin = 14;
        long pastBlocksMax = 140;
        long countBlocks = 0;

        BigInteger pastDifficultyAverage = BigInteger.valueOf(0);
        BigInteger pastDifficultyAveragePrev = BigInteger.valueOf(0);

        if (prevBlock == null || prevBlock.getHeight() == 0 || prevBlock.getHeight() < pastBlocksMin) {
            verifyDifficulty(prevBlock, added, params.getMaxTarget(), params);
            return;
        }

        for (int i = 1; blockReading.getHeight() > 0; i++) {
            if (i > pastBlocksMax) {
                break;
            }
            countBlocks++;

            if (countBlocks <= pastBlocksMin) {
                if (countBlocks == 1) {
                    pastDifficultyAverage = blockReading.getHeader().getDifficultyTargetAsInteger();
                } else {
                    pastDifficultyAverage = blockReading.getHeader().getDifficultyTargetAsInteger()
                            .subtract(pastDifficultyAveragePrev)
                            .divide(BigInteger.valueOf(countBlocks))
                            .add(pastDifficultyAveragePrev);
                }
                pastDifficultyAveragePrev = pastDifficultyAverage;
            }

            if (lastBlockTime > 0) {
                long diff = lastBlockTime - blockReading.getHeader().getTimeSeconds();
                if (blockTimeCount <= pastBlocksMin) {
                    blockTimeCount++;

                    if (blockTimeCount == 1) {
                        blockTimeAverage = diff;
                    } else {
                        blockTimeAverage = (diff - blockTimeAveragePrev) / blockTimeCount + blockTimeAveragePrev;
                    }
                    blockTimeAveragePrev = blockTimeAverage;
                }
                blockTimeCount2++;
                blockTimeSum2 += diff;
            }
            lastBlockTime = blockReading.getHeader().getTimeSeconds();

            try {
                blockReading = store.get(blockReading.getHeader().getPrevBlockHash());
                if (blockReading == null) {
                    return;
                }
            }
            catch (BlockStoreException ex) {
                log.warn("Dark gravity wave 3 descended to start of the chain");
                return;
            }
        }

        BigInteger bnNew = pastDifficultyAverage;

        if (blockTimeCount != 0 && blockTimeCount2 != 0) {
            double smartAverage = ((double) blockTimeAverage) * 0.7 + ((double) blockTimeSum2 / (double) blockTimeCount2) * 0.3;
            if (smartAverage < 1)
                smartAverage = 1;

            final int targetSpacing = params.getTargetSpacing(prevBlock.getHeader(), prevBlock.getHeight());
            final double shift = targetSpacing / smartAverage;

            final double dCountBlocks = (double) countBlocks;
            double actualTimespan = dCountBlocks * ((double) targetSpacing) / shift;
            double targetTimespan = dCountBlocks * targetSpacing;

            if (actualTimespan < targetTimespan / 3)
                actualTimespan = targetTimespan / 3;
            if (actualTimespan > targetTimespan * 3)
                actualTimespan = targetTimespan * 3;

            // Retarget
            bnNew = bnNew.multiply(BigInteger.valueOf((long) actualTimespan));
            bnNew = bnNew.divide(BigInteger.valueOf((long) targetTimespan));
        }

        verifyDifficulty(prevBlock, added, bnNew, params);
    }

    private static void darkGravityWave3Check(StoredBlock prevBlock, Block added, BlockStore store, NetworkParameters params) {
        StoredBlock blockReading = prevBlock;

        long actualTimespan = 0;
        long lastBlockTime = 0;
        long pastBlocksMin = 24;
        long pastBlocksMax = 24;
        long countBlocks = 0;

        BigInteger pastDifficultyAverage = BigInteger.ZERO;
        BigInteger pastDifficultyAveragePrev = BigInteger.ZERO;

        if (prevBlock == null || prevBlock.getHeight() == 0 || prevBlock.getHeight() < pastBlocksMin) {
            verifyDifficulty(prevBlock, added, params.getMaxTarget(), params);
            return;
        }

        for (int i = 1; blockReading.getHeight() > 0; i++) {
            if (i > pastBlocksMax) {
                break;
            }
            countBlocks++;

            if (countBlocks <= pastBlocksMin) {
                if (countBlocks == 1) {
                    pastDifficultyAverage = blockReading.getHeader().getDifficultyTargetAsInteger();
                } else {
                    pastDifficultyAverage = pastDifficultyAveragePrev
                            .multiply(BigInteger.valueOf(countBlocks))
                            .add(blockReading.getHeader().getDifficultyTargetAsInteger())
                            .divide(BigInteger.valueOf(countBlocks + 1));
                }
                pastDifficultyAveragePrev = pastDifficultyAverage;
            }

            if (lastBlockTime > 0) {
                actualTimespan += (lastBlockTime - blockReading.getHeader().getTimeSeconds());
            }
            lastBlockTime = blockReading.getHeader().getTimeSeconds();

            try {
                blockReading = store.get(blockReading.getHeader().getPrevBlockHash());
                if (blockReading == null) {
                    return;
                }
            } catch (BlockStoreException ex) {
                log.warn("Dark gravity wave 3 descended to start of the chain");
                return;
            }
        }

        BigInteger bnNew = pastDifficultyAverage;

        long targetTimespan = countBlocks * params.getTargetSpacing(prevBlock.getHeader(), prevBlock.getHeight());

        if (actualTimespan < targetTimespan / 3) {
            actualTimespan = targetTimespan / 3;
        }
        if (actualTimespan > targetTimespan * 3) {
            actualTimespan = targetTimespan * 3;
        }

        // Retarget
        bnNew = bnNew.multiply(BigInteger.valueOf(actualTimespan));
        bnNew = bnNew.divide(BigInteger.valueOf(targetTimespan));

        verifyDifficulty(prevBlock, added, bnNew, params);
    }

    private static void verifyDifficulty(StoredBlock prevBlock, Block added, BigInteger calcDiff, NetworkParameters params) {
        if (calcDiff.compareTo(params.getMaxTarget()) > 0) {
            log.info("Difficulty hit proof of work limit: {}", calcDiff.toString(16));
            calcDiff = params.getMaxTarget();
        }

        int accuracyBytes = (int) (added.getDifficultyTarget() >>> 24) - 3;
        final BigInteger receivedDifficulty = added.getDifficultyTargetAsInteger();

        // The calculated difficulty is to a higher precision than received, so reduce here.
        final BigInteger mask = BigInteger.valueOf(0xFFFFFFL).shiftLeft(accuracyBytes * 8);
        calcDiff = calcDiff.and(mask);

        if(CoinDefinition.TEST_NETWORK_STANDARD.equals(params.getStandardNetworkId())) {
            if (calcDiff.compareTo(receivedDifficulty) != 0) {
                throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                        receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));
            }
        } else {
            final int height = prevBlock.getHeight() + 1;
            if (height <= 68589) {
                long nBitsNext = added.getDifficultyTarget();

                long calcDiffBits = (accuracyBytes+3) << 24;
                calcDiffBits |= calcDiff.shiftRight(accuracyBytes*8).longValue();

                final double n1 = CommonUtils.convertBitsToDouble(calcDiffBits);
                final double n2 = CommonUtils.convertBitsToDouble(nBitsNext);

                if (Math.abs(n1 - n2) > n1 * 0.2) {
                    throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                            receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));
                }
            } else {
                if (calcDiff.compareTo(receivedDifficulty) != 0) {
                    throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                            receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));
                }
            }
        }
    }

    private static final class InnerLinearExtension extends LinearBlockChainExtension {

        private InnerLinearExtension(AbstractBlockChain bc, long testnetDiffDate) {
            super(bc, testnetDiffDate);
        }

        @Override
        protected int backTill(NetworkParameters params, StoredBlock prevBlock, Block added) {
            final int height = prevBlock.getHeight();
            final int interval = params.getInterval(prevBlock.getHeader(), height);
            // Dash: This fixes an issue where a 51% attack can change difficulty at will.
            // Go back the full period unless it's the first retarget after genesis.
            // Code courtesy of Art Forz.
            return (height + 1 != interval)
                    ? interval
                    : height;
        }

        private AbstractBlockChain getBlockChain() {
            return blockChain;
        }

    }

}
