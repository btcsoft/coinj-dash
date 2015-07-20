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
import java.math.BigInteger;

/**
 * Date: 5/16/15
 * Time: 4:47 AM
 *
 * @author Mikhail Kulikov
 */
public class ConsensusVote extends Message implements Serializable, Hashable {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(ConsensusVote.class);

    private TransactionInput inMasternode;
    private Sha256Hash txHash;
    private int blockHeight;
    private byte[] signature;

    private Sha256Hash hashCache;

    public ConsensusVote(NetworkParameters params, byte[] payload) throws ProtocolException {
        super(params, payload, 0, false, false, payload.length);
    }

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;
        cursor = offset;
        txHash = readHash();
        inMasternode = new TransactionInput(params, null, payload, cursor, parseLazy, parseRetain);
        long scriptLen = readVarInt(TransactionOutPoint.MESSAGE_LENGTH);
        cursor += scriptLen + 4;
        signature = readByteArray();
        blockHeight = (int) readUint32();
    }

    @Override
    protected void parseLite() throws ProtocolException {}

    @Override
    void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(Utils.reverseBytes(txHash.getBytes()));
        inMasternode.bitcoinSerialize(stream);
        stream.write(new VarInt(signature.length).encode());
        stream.write(signature);
        Utils.uint32ToByteStreamLE((long) blockHeight, stream);
    }

    @Override
    public Sha256Hash getHash() {
        if (hashCache == null) {
            final TransactionOutPoint outpoint = inMasternode.getOutpoint();
            hashCache = new Sha256Hash(
                    outpoint.getHash().toBigInteger()
                            .add(BigInteger.valueOf(outpoint.getIndex()))
                            .add(txHash.toBigInteger())
            );
        }
        return hashCache;
    }

    public TransactionInput getInMasternode() {
        return inMasternode;
    }

    public int getBlockHeight() {
        return blockHeight;
    }

    public Sha256Hash getTxHash() {
        return txHash;
    }

    public boolean checkSignature(MasternodeManager manager) {
        final String strMessage = txHash.toString() + blockHeight;

        final MasternodeData mn = manager.find(inMasternode);
        if (mn == null) {
            logger.info("ConsensusVote.checkSignature(MasternodeManager) - Unknown Masternode");
            return false;
        }

        if (!VerificationUtils.verifyMessage(mn.getPubKey2(), signature, strMessage, manager.getParams())) {
            logger.info("ConsensusVote.checkSignature(MasternodeManager) - Verify message failed");
            return false;
        }

        return true;
    }

}
