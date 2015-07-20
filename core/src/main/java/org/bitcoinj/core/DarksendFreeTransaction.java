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
import org.coinj.dash.VerificationUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Date: 5/30/15
 * Time: 1:13 AM
 *
 * @author Mikhail Kulikov
 */
public class DarksendFreeTransaction extends Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private Transaction tx;
    private TransactionInput in;
    private byte[] signature;
    private long sigTime;

    public DarksendFreeTransaction(NetworkParameters params, byte[] payloadBytes) {
        super(params, payloadBytes, 0, false, false, payloadBytes.length);
    }

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;
        cursor = offset;
        tx = new Transaction(params, payload, cursor, null, parseLazy, parseRetain, UNKNOWN_LENGTH);
        cursor += tx.getMessageSize();
        in = new TransactionInput(params, null, payload, cursor, parseLazy, parseRetain);
        long scriptLen = readVarInt(TransactionOutPoint.MESSAGE_LENGTH);
        cursor += scriptLen + 4;
        signature = readByteArray();
        sigTime = readInt64();
    }

    @Override
    protected void parseLite() throws ProtocolException {}

    @Override
    void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        tx.bitcoinSerialize(stream);
        in.bitcoinSerialize(stream);
        stream.write(new VarInt(signature.length).encode());
        stream.write(signature);
        Utils.int64ToByteStreamLE(sigTime, stream);
    }

    public Transaction getTx() {
        return tx;
    }

    public TransactionInput getIn() {
        return in;
    }

    public boolean checkSignature(MasternodeData mn) {
        final String strMessage = tx.getHashAsString() + sigTime;
        return VerificationUtils.verifyMessage(mn.getPubKey2(), signature, strMessage, params);
    }

}
