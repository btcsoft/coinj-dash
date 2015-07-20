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

import org.coinj.dash.*;
import com.google.common.base.Charsets;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Date: 5/21/15
 * Time: 3:18 AM
 *
 * @author Mikhail Kulikov
 */
public class MasternodeBroadcast extends Message implements MasternodeStateInfo, Hashable, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(MasternodeBroadcast.class);

    private TransactionInput txIn;
    private PeerAddress addr;
    private ECKey pubKey1;
    private ECKey pubKey2;
    private byte[] signature;
    private long sigTime; //dsee message times
    private long lastTimeSeen;
    private int protocolVersion;
    private byte[] donationAddress;
    private int donationPercentage;
    private long lastPaid;
    public boolean requested;

    private Sha256Hash hashCache;

    private transient Script donationAddressScript;

    public MasternodeBroadcast(NetworkParameters params, byte[] payloadBytes) {
        super(params, payloadBytes, 0, false, false, payloadBytes.length);
    }

    public MasternodeBroadcast(NetworkParameters params, MasternodeStateInfo info) {
        super(params);
        txIn = info.getTxIn();
        addr = info.getAddr();
        pubKey1 = info.getPubKey1();
        pubKey2 = info.getPubKey2();
        signature = info.getSignature();
        sigTime = info.getSigTime();
        lastTimeSeen = info.getLastTimeSeen();
        protocolVersion = info.getProtocolVersion();
        donationAddress = info.getDonationAddress();
        donationPercentage = info.getDonationPercentage();
        lastPaid = info.getLastPaid();
        requested = false;
    }

    @Override
    protected void parseLite() throws ProtocolException {}

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;
        cursor = offset;

        txIn = new TransactionInput(params, null, payload, cursor, parseLazy, parseRetain);
        long scriptLen = readVarInt(TransactionOutPoint.MESSAGE_LENGTH);
        cursor += scriptLen + 4;

        byte[] addrBytes = readBytes(16);
        InetAddress address;
        try {
            address = InetAddress.getByAddress(addrBytes);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
        int port = ((0xFF & payload[cursor++]) << 8) | (0xFF & payload[cursor++]);
        addr = new PeerAddress(address, port, params.protocolVersion);

        pubKey1 = ECKey.fromPublicOnly(readByteArray());
        pubKey2 = ECKey.fromPublicOnly(readByteArray());
        signature = readByteArray();
        sigTime = readInt64();
        lastTimeSeen = readInt64();
        protocolVersion = (int) readUint32();
        donationAddress = readByteArray();
        donationPercentage = (int) readUint32();
        lastPaid = readInt64();
        requested = (payload[cursor++] != 0);
    }

    @Override
    void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        txIn.bitcoinSerialize(stream);
        // Java does not provide any utility to map an IPv4 address into IPv6 space, so we have to do it by hand.
        byte[] ipBytes = addr.getAddr().getAddress();
        if (ipBytes.length == 4) {
            byte[] v6addr = new byte[16];
            System.arraycopy(ipBytes, 0, v6addr, 12, 4);
            v6addr[10] = (byte) 0xFF;
            v6addr[11] = (byte) 0xFF;
            ipBytes = v6addr;
        }
        stream.write(ipBytes);
        // And write out the port. Unlike the rest of the protocol, address and port is in big endian byte order.
        final int port = addr.getPort();
        stream.write((byte) (0xFF & port >> 8));
        stream.write((byte) (0xFF & port));
        final byte[] pk1 = pubKey1.getPubKey();
        stream.write(new VarInt(pk1.length).encode());
        stream.write(pk1);
        final byte[] pk2 = pubKey2.getPubKey();
        stream.write(new VarInt(pk2.length).encode());
        stream.write(pk2);
        stream.write(new VarInt(signature.length).encode());
        stream.write(signature);
        Utils.int64ToByteStreamLE(sigTime, stream);
        Utils.int64ToByteStreamLE(lastTimeSeen, stream);
        Utils.uint32ToByteStreamLE(protocolVersion, stream);
        stream.write(new VarInt(donationAddress.length).encode());
        stream.write(donationAddress);
        Utils.uint32ToByteStreamLE(donationPercentage, stream);
        Utils.int64ToByteStreamLE(lastPaid, stream);
        stream.write(requested ? 1 : 0);
    }

    @Override
    public PeerAddress getAddr() {
        return addr;
    }

    @Override
    public byte[] getDonationAddress() {
        return donationAddress;
    }

    @Override
    public int getDonationPercentage() {
        return donationPercentage;
    }

    @Override
    public long getLastPaid() {
        return lastPaid;
    }

    @Override
    public long getLastTimeSeen() {
        return lastTimeSeen;
    }

    @Override
    public int getProtocolVersion() {
        return protocolVersion;
    }

    @Override
    public ECKey getPubKey1() {
        return pubKey1;
    }

    @Override
    public ECKey getPubKey2() {
        return pubKey2;
    }

    @Override
    public byte[] getSignature() {
        return signature;
    }

    @Override
    public long getSigTime() {
        return sigTime;
    }

    public boolean isRequested() {
        return requested;
    }

    @Override
    public TransactionInput getTxIn() {
        return txIn;
    }

    public boolean checkSignature() {
        // make sure signature isn't in the future (past is OK)
        if (sigTime > Utils.currentTimeSeconds() + 60 * 60) {
            logger.info("mnb - Signature rejected, too far into the future {}", txIn.toString());
            return false;
        }

        final String strMessage = (new StringBuilder())
                .append(addr.satoshiStyleToString())
                .append(Long.toString(sigTime))
                .append(new String(pubKey1.getPubKey(), Charsets.US_ASCII))
                .append(new String(pubKey2.getPubKey(), Charsets.US_ASCII))
                .append(Integer.toString(protocolVersion))
                .append(getDonationAddressScript().satoshiStyleToString())
                .append(Integer.toString(donationPercentage))
                .toString();

        if (donationPercentage < 0 || donationPercentage > 100){
            logger.info("mnb - donation percentage out of range {}", donationPercentage);
            return false;
        }

        if (protocolVersion < DashDefinition.DARKSEND_POOL_MIN_PROTOCOL_VERSION) {
            logger.info("mnb - ignoring outdated Masternode {} protocol version {}", txIn, protocolVersion);
            return false;
        }

        final Script pubkeyScript = ScriptBuilder.createOutputScript(new Address(params, pubKey1.getPubKeyHash()));
        final int pk1Len = pubkeyScript.getProgram().length;
        if (pk1Len != 25) {
            logger.error("mnb - pubkey1 the wrong size: {}", pk1Len);
            throw new RuntimeException("Impossible situation: pubkey1 the wrong size: " + pk1Len);
        }

        final Script pubkeyScript2 = ScriptBuilder.createOutputScript(new Address(params, pubKey2.getPubKeyHash()));
        final int pk2Len = pubkeyScript2.getProgram().length;
        if (pk2Len != 25) {
            logger.error("mnb - pubkey2 the wrong size: {}", pk2Len);
            throw new RuntimeException("Impossible situation: pubkey2 the wrong size: " + pk2Len);
        }

        if (txIn.getScriptSig() != null) {
            logger.info("mnb - Ignore Not Empty ScriptSig {}", txIn);
            return false;
        }

        if (!VerificationUtils.verifyMessage(pubKey1, signature, strMessage, params)) {
            logger.error("mnb - Got bad Masternode address signature: {}", addr);
            logger.debug("Bad message? " + strMessage);
            throw new VerificationException("mnb - Got bad Masternode address signature " + addr);
        }

        return addr.getPort() == params.getPort();
    }

    public boolean checkInputs(DashNetwork network, VerificationInputContext inputContext) {
        if (network.isInputAcceptable(inputContext)) {
            if (logger.isDebugEnabled()) {
                logger.debug("mnb - Accepted Masternode entry");
            }

            final DashNetwork.MinConfirmationsCheckResult checkResult = CommonUtils.performMinConfirmationsCheck(network, inputContext, sigTime);

            if (!checkResult.success) {
                if (checkResult.isFailedAgeKnown()) {
                    logger.info("mnb - Input must have least {} confirmations, we have {}", MasternodeData.MASTERNODE_MIN_CONFIRMATIONS, checkResult.failedAge);
                } else if (checkResult.isFailHeightKnown()) {
                    if (checkResult.exception != null) {
                        logger.error("mnb - Descending the chain exception on {} height", checkResult.failHeight);
                    } else {
                        logger.error("mnb - Descending the chain for block with masternode's output failed - NULL on {} height", checkResult.failHeight);
                    }
                } else if (checkResult.isFailBlockTimeKnown()) {
                    logger.info("mnb - Bad sigTime {} for Masternode {} {} ({} conf block is at {})",
                            sigTime, addr, txIn, MasternodeData.MASTERNODE_MIN_CONFIRMATIONS, checkResult.failBlockTime);
                } else {
                    logger.warn("mnb - block chain not set yet");
                }

                return false;
            }

            //doesn't support multisig addresses
            if (getDonationAddressScript().isPayToScriptHash()){
                donationAddress = null;
                donationPercentage = 0;
            }

            return true;
        }

        return false;
    }

    @Override
    public Sha256Hash getHash() {
        if (hashCache == null) {
            final byte[] pk = pubKey1.getPubKey();
            final byte[] toHash = new byte[8 + pk.length];
            Utils.uint64ToByteArrayLE(sigTime, toHash, 0);
            System.arraycopy(pk, 0, toHash, 8, pk.length);
            hashCache = Sha256Hash.createDouble(toHash);
        }
        return hashCache;
    }

    private Script getDonationAddressScript() {
        if (donationAddressScript == null) {
            donationAddressScript = new Script(donationAddress);
        }
        return donationAddressScript;
    }

}
