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

import org.coinj.dash.DashDefinition;
import org.coinj.dash.MasternodeData;
import org.coinj.dash.MasternodeManager;
import org.coinj.dash.VerificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Date: 5/23/15
 * Time: 8:12 AM
 *
 * @author Mikhail Kulikov
 */
public class MasternodePing extends Message implements Hashable, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(MasternodePing.class);

    private TransactionInput txIn;
    private byte[] signature;
    long sigTime; //mnb message times

    private Sha256Hash hashCache;

    public MasternodePing(NetworkParameters params, byte[] payloadBytes) {
        super(params, payloadBytes, 0, false, false, payloadBytes.length);
    }

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;
        cursor = offset;
        txIn = new TransactionInput(params, null, payload, cursor, parseLazy, parseRetain);
        long scriptLen = readVarInt(TransactionOutPoint.MESSAGE_LENGTH);
        cursor += scriptLen + 4;
        signature = readByteArray();
        sigTime = readInt64();
    }

    @Override
    protected void parseLite() throws ProtocolException {}

    @Override
    void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        txIn.bitcoinSerialize(stream);
        stream.write(new VarInt(signature.length).encode());
        stream.write(signature);
        Utils.int64ToByteStreamLE(sigTime, stream);
    }

    public boolean check(MasternodeManager manager) {
        final long curSec = Utils.currentTimeSeconds();
        final int hour = 60 * 60;
        if (sigTime > curSec + hour) {
            logger.info("mnping - Signature rejected, too far into the future {}", txIn);
            return false;
        }

        if (sigTime <= curSec - hour) {
            logger.info("mnping - Signature rejected, too far into the past {} - sigTime is {}, current seconds {}", txIn, sigTime, curSec);
            return false;
        }

        // see if we have this Masternode
        final MasternodeData pmn = manager.find(txIn);
        if (pmn != null && pmn.getProtocolVersion() >= DashDefinition.DARKSEND_POOL_MIN_PROTOCOL_VERSION) {
            // take this only if it's newer
            if (pmn.getLastMnPing() < sigTime) {
                if (!VerificationUtils.verifyMessage(pmn.getPubKey2(), signature, Long.toString(sigTime), manager.getParams())) {
                    logger.info("mnping - Got bad Masternode address signature {}", txIn);
                    return false;
                }

                pmn.setLastMnPing(sigTime);

                if (pmn.updatedWithin(MasternodeData.MASTERNODE_MIN_MNP_SECONDS)) {
                    pmn.updateLastSeen();
                    pmn.check(manager);

                    return pmn.isEnabled();
                }
            }
        }

        return false;
    }

    public byte[] getSignature() {
        return signature;
    }

    public long getSigTime() {
        return sigTime;
    }

    public TransactionInput getTxIn() {
        return txIn;
    }

    @Override
    public Sha256Hash getHash() {
        if (hashCache == null) {
            final byte[] serIn = txIn.bitcoinSerialize(); // TODO: make sure it's what (BEGIN(vin), END(vin)), BEGIN:((char*)&(a)) and END:((char*)&((&(a))[1]))), do
            final byte[] toHash = new byte[serIn.length + 8];
            System.arraycopy(serIn, 0, toHash, 0, serIn.length);
            Utils.uint64ToByteArrayLE(sigTime, toHash, serIn.length);
            hashCache = Sha256Hash.createDouble(toHash);
        }
        return hashCache;
    }

}
