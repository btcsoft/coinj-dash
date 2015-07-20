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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Date: 5/16/15
 * Time: 4:46 AM
 *
 * @author Mikhail Kulikov
 */
public class TransactionLockRequest extends Message implements Serializable, Hashable {

    private static final long serialVersionUID = 1L;

    private Transaction tx;

    public TransactionLockRequest(NetworkParameters params, byte[] payloadBytes) throws ProtocolException {
        super(params, payloadBytes, 0, params.protocolVersion, false, false, payloadBytes.length);
    }

    public TransactionLockRequest(Transaction tx) {
        super(tx.params);
        this.tx = tx;
    }

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;
        cursor = offset;
        tx = new Transaction(params, payload, cursor, null, parseLazy, parseRetain, UNKNOWN_LENGTH);
        cursor += tx.getMessageSize();
    }

    @Override
    protected void parseLite() throws ProtocolException {}

    @Override
    void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        tx.bitcoinSerialize(stream);
    }

    public Transaction getTx() {
        return tx;
    }

    @Override
    public Sha256Hash getHash() {
        return tx.getHash();
    }

}
