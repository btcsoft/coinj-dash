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
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.security.SignatureException;
import java.util.Arrays;

/**
 * Date: 5/22/15
 * Time: 7:40 AM
 *
 * @author Mikhail Kulikov
 */
public final class VerificationUtils {

    private static final Logger logger = LoggerFactory.getLogger(VerificationUtils.class);

    public static boolean verifyMessage(ECKey pubkey, byte[] signature, String strMessage, NetworkParameters params) {
        try {
            final ECKey fromSig = ECKey.signedMessageToKey(strMessage, signature, params);
            final boolean equals = fromSig.getPubKeyPoint().equals(pubkey.getPubKeyPoint());
            if (logger.isDebugEnabled() && !equals) {
                logger.debug("DarkSendSigner.verifyMessage -- keys don't match: \"{}\" \"{}\"", fromSig, pubkey);
            }
            return equals;
        } catch (SignatureException e) {
            throw new VerificationException("Darksend signature verification fail", e);
        }
    }

    static boolean isInputAcceptable(TransactionInput txIn, AbstractBlockChain blockChain, NetworkParameters params) {
        return isInputAcceptable(getVerificationInputContext(txIn, blockChain), blockChain, params);
    }

    static boolean isInputAcceptable(VerificationInputContext context, AbstractBlockChain blockChain, NetworkParameters params) {
        // if utxo was found we assume input is valid
        // because scripts were checked inside isVinAssociatedWithPubkey(VerificationInputContext,ECKey,NetworkParameters) already
        // we just have to check additional requirement for coinbase outputs (that coinbase is spendable after all)
        return context.storedTxOut != null
                && (blockChain.getBestChainHeight() - context.storedTxOut.getCoinbaseHeight() >= params.getSpendableCoinbaseDepth());
    }

    static int getInputAge(TransactionInput txIn, AbstractBlockChain blockChain, int chainHeadHeight) {
        final StoredTransactionOutput txOut = retrieveFromUtxo(blockChain, txIn.getOutpoint());
        return _getInputAge(txOut, chainHeadHeight);
    }

    static int getInputAge(VerificationInputContext context, int chainHeadHeight) {
        return _getInputAge(context.storedTxOut, chainHeadHeight);
    }

    private static int _getInputAge(StoredTransactionOutput txOut, int chainHeadHeight) {
        if (txOut != null) {
            return chainHeadHeight + 1 - txOut.getHeight();
        } else {
            return -1;
        }
    }

    static boolean isVinAssociatedWithPubkey(VerificationInputContext context, ECKey pubKey, NetworkParameters params) {
        final StoredTransactionOutput txOut = context.storedTxOut;
        if (txOut != null) {
            final Script payee2 = ScriptBuilder.createOutputScript(new Address(params, pubKey.getPubKeyHash()));
            if (txOut.getValue().equals(Coin.valueOf(1000, params.getCoinDefinition())) && Arrays.equals(payee2.getProgram(), txOut.getScriptBytes())) {
                return true;
            }
        }

        return false;
    }

    static VerificationInputContext getVerificationInputContext(TransactionInput txIn, AbstractBlockChain blockChain) {
        return new VerificationInputContext(retrieveFromUtxo(blockChain, txIn.getOutpoint()));
    }

    @Nullable
    private static StoredTransactionOutput retrieveFromUtxo(AbstractBlockChain blockChain, TransactionOutPoint txInOutpoint) {
        final BlockStore bs = blockChain.getBlockStore();
        if (bs instanceof FullPrunedBlockStore) {
            FullPrunedBlockStore blockStore = (FullPrunedBlockStore) bs;
            try {
                return blockStore.getTransactionOutput(txInOutpoint.getHash(), txInOutpoint.getIndex());
            } catch (BlockStoreException e) {
                logger.error("blockStore.getTransactionOutput(Sha256Hash, long) lookup exception", e);
            }
        } else {
            logger.warn("Masternode code active despite that BlockStore is not an instance of FullPrunedBlockStore");
        }

        return null;
    }

}
