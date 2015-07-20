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

package org.bitcoinj.core;

import org.coinj.dash.MasternodeData;
import org.coinj.dash.MasternodeManager;
import org.coinj.dash.VerificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Date: 5/21/15
 * Time: 5:35 AM
 *
 * @author Mikhail Kulikov
 */
public class MasternodeScanningError extends Message implements Hashable, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(MasternodeScanningError.class);

    public static final int SCANNING_SUCCESS = 1;
    public static final int SCANNING_ERROR_NO_RESPONSE = 2;
    public static final int SCANNING_ERROR_IX_NO_RESPONSE = 3;
    public static final int SCANNING_ERROR_MAX = 3;

    /**
     * 1% of the network is scanned every 2.5 minutes, making a full
     * round of scanning take about 4.16 hours. We're targeting about
     * a day of proof-of-service errors for complete removal from the
     * masternode system.
     */
    public static final int MASTERNODE_SCANNING_ERROR_THRESHOLD = 6;

    private TransactionInput txInMasternodeA;
    private TransactionInput txInMasternodeB;
    private int errorType;
    private int expiration;
    private int blockHeight;
    private byte[] signature;

    private Sha256Hash hashCache;

    public MasternodeScanningError(NetworkParameters params, byte[] payload) {
        super(params, payload, 0, false, false, payload.length);
    }

    public MasternodeScanningError(TransactionInput txInMasternodeB, int errorType, int blockHeight) {
        super(txInMasternodeB.getParams());
        this.txInMasternodeA = new TransactionInput(params, null, new byte[] {});
        this.txInMasternodeB = txInMasternodeB;
        this.errorType = errorType;
        this.expiration = (int) Utils.currentTimeSeconds() + 60 * 60;
        this.blockHeight = blockHeight;
    }

    @Override
    protected void parseLite() throws ProtocolException {}

    @Override
    void parse() throws ProtocolException {
        cursor = offset;

        txInMasternodeA = new TransactionInput(params, null, payload, cursor, parseLazy, parseRetain);
        long scriptLen = readVarInt(TransactionOutPoint.MESSAGE_LENGTH);
        cursor += scriptLen + 4;
        txInMasternodeB = new TransactionInput(params, null, payload, cursor, parseLazy, parseRetain);
        scriptLen = readVarInt(TransactionOutPoint.MESSAGE_LENGTH);
        cursor += scriptLen + 4;

        errorType = (int) readUint32();
        expiration = (int) readUint32();
        blockHeight = (int) readUint32();
        signature = readByteArray();
    }

    @Override
    void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        txInMasternodeA.bitcoinSerialize(stream);
        txInMasternodeB.bitcoinSerialize(stream);
        Utils.uint32ToByteStreamLE(errorType, stream);
        Utils.uint32ToByteStreamLE(expiration, stream);
        Utils.uint32ToByteStreamLE(blockHeight, stream);
        stream.write(new VarInt(signature.length).encode());
        stream.write(signature);
    }

    @Override
    public Sha256Hash getHash() {
        if (hashCache == null) {
            hashCache = Sha256Hash.createDouble(bitcoinSerialize());
        }
        return hashCache;
    }

    public boolean isValid() {
        return (errorType > 0 && errorType <= SCANNING_ERROR_MAX);
    }

    public int getBlockHeight() {
        return blockHeight;
    }

    public int getErrorType() {
        return errorType;
    }

    public TransactionInput getTxInMasternodeA() {
        return txInMasternodeA;
    }

    public TransactionInput getTxInMasternodeB() {
        return txInMasternodeB;
    }

    public boolean isSignatureValid(MasternodeManager manager) {
        final StringBuilder strMessage = new StringBuilder();
        appendTxIn(strMessage, txInMasternodeA);
        appendTxIn(strMessage, txInMasternodeB)
                .append(blockHeight)
                .append(errorType);

        final MasternodeData pmn = manager.find(txInMasternodeA);

        if (pmn == null) {
            logger.info("MasternodeScanningError.isSignatureValid(MasternodeManager) - Unknown Masternode");
            return false;
        }

        if (!VerificationUtils.verifyMessage(pmn.getPubKey2(), signature, strMessage.toString(), params)) {
            logger.info("MasternodeScanningError.isSignatureValid(MasternodeManager) - Verify message failed");
            return false;
        }

        return true;
    }

    private static StringBuilder appendTxIn(StringBuilder builder, TransactionInput txIn) {
        final TransactionOutPoint outpoint = txIn.getOutpoint();
        builder.append("CTxIn(")
                .append("COutPoint(")
                .append(outpoint.getHash())
                .append(", ")
                .append(outpoint.getIndex())
                .append(')');
        if (txIn.isCoinBase()) {
            builder.append(", coinbase ")
                    .append(Utils.HEX.encode(txIn.getScriptBytes()));
        } else {
            final String str = txIn.getScriptSig().satoshiStyleToString();
            builder.append(", scriptSig=")
                    .append(str.length() > 24 ? str.substring(0, 24) : str);
        }
        if (txIn.hasSequence()) {
            builder.append(", nSequence=")
                    .append(txIn.getSequenceNumber());
        }
        builder.append(')');
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MasternodeScanningError that = (MasternodeScanningError) o;

        return blockHeight == that.blockHeight
                && expiration == that.expiration
                && !(txInMasternodeA != null ? !txInMasternodeA.equals(that.txInMasternodeA) : that.txInMasternodeA != null)
                && !(txInMasternodeB != null ? !txInMasternodeB.equals(that.txInMasternodeB) : that.txInMasternodeB != null);
    }

    @Override
    public int hashCode() {
        int result = txInMasternodeA != null ? txInMasternodeA.hashCode() : 0;
        result = 31 * result + (txInMasternodeB != null ? txInMasternodeB.hashCode() : 0);
        result = 31 * result + expiration;
        result = 31 * result + blockHeight;
        return result;
    }

}
