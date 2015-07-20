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

import org.coinj.dash.DashNetwork;
import org.coinj.dash.VerificationUtils;
import com.google.common.base.Preconditions;
import org.coinj.x11.X11Alg;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Date: 6/4/15
 * Time: 1:21 PM
 *
 * @author Mikhail Kulikov
 */
public class SporkMessage extends Message implements Serializable, Hashable {

    private static final long serialVersionUID = 1L;

    private int sporkId;
    private long value;
    private long timeSigned;
    private byte[] signature;

    private byte[] bytesToHash;
    private Sha256Hash hashCache;

    public SporkMessage(NetworkParameters params, byte[] payload) throws ProtocolException {
        super(params, payload, 0, false, false, payload.length);
    }

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;
        cursor = offset;

        bytesToHash = new byte[20];
        System.arraycopy(payload, 0, bytesToHash, 0, 20);
        sporkId = (int) readUint32();
        value = readInt64();
        timeSigned = readInt64();
        signature = readByteArray();
    }

    @Override
    protected void parseLite() throws ProtocolException {}

    @Override
    void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Preconditions.checkState(parsed, "Message not parsed yet");
        Utils.uint32ToByteStreamLE(sporkId, stream);
        Utils.int64ToByteStreamLE(value, stream);
        Utils.int64ToByteStreamLE(timeSigned, stream);
        stream.write(new VarInt(signature.length).encode());
        stream.write(signature);
    }

    @Override
    public Sha256Hash getHash() {
        Preconditions.checkState(parsed, "Message not parsed yet");
        if (hashCache == null) {
            hashCache = new Sha256Hash(Utils.reverseBytes(X11Alg.x11Digest(bytesToHash)));
        }
        return hashCache;
    }

    public int getSporkId() {
        return sporkId;
    }

    public long getValue() {
        return value;
    }

    public long getTimeSigned() {
        return timeSigned;
    }

    public boolean checkSignature(DashNetwork network) {
        final String strMessage = String.valueOf(sporkId) + String.valueOf(value) + String.valueOf(timeSigned);
        return VerificationUtils.verifyMessage(network.getSporkKey(), signature, strMessage, network.getParams());
    }

    public void execute(DashNetwork network) {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SporkMessage that = (SporkMessage) o;

        return sporkId == that.sporkId
                && value == that.value
                && Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        int result = sporkId;
        result = 31 * result + (int) (value ^ (value >>> 32));
        result = 31 * result + (signature != null ? Arrays.hashCode(signature) : 0);
        return result;
    }

}
