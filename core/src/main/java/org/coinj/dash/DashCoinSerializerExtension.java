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
import org.coinj.api.CoinSerializerExtension;

import javax.annotation.Nullable;
import java.util.Map;

/**
* Date: 5/17/15
* Time: 1:29 PM
*
* @author Mikhail Kulikov
*/
public final class DashCoinSerializerExtension implements CoinSerializerExtension {

    static final DashCoinSerializerExtension INSTANCE = new DashCoinSerializerExtension();

    private DashCoinSerializerExtension() {}

    @Nullable
    @Override
    public Message attemptToMakeMessage(NetworkParameters params, String command, int length, byte[] payloadBytes, byte[] hash, byte[] checksum) throws ProtocolException {
        if (command.equals("dstx")) {
            return new DarksendFreeTransaction(params, payloadBytes);
        } else if (command.equals("mnb")) {
            return new MasternodeBroadcast(params, payloadBytes);
        } else if (command.equals("mnp")) {
            return new MasternodePing(params, payloadBytes);
        } else if (command.equals("dseg")) {
            return new MasternodeListRequest(params, payloadBytes);
        } else if (command.equals("mnse")) {
            return new MasternodeScanningError(params, payloadBytes);
        } else if (command.equals("mvote")) {
            return new MasternodeBudgetVote(params, payloadBytes);
        } else if (command.equals("txlreq")) {
            return new TransactionLockRequest(params, payloadBytes);
        } else if (command.equals("txlvote")) {
            return new ConsensusVote(params, payloadBytes);
        } else if (command.equals("dseep")) {
            return new DarkSendElectionEntryPingMessage(params, payloadBytes);
        } else if (command.equals("spork")) {
            return new SporkMessage(params, payloadBytes);
        }  else if (command.equals("getsporks")) {
            return new GetSporksMessage(params);
        } else {
            return null;
        }
    }

    @Override
    public void retrofitNamesMap(Map<Class<? extends Message>, String> names) {
        names.put(DarksendFreeTransaction.class, "dstx");
        names.put(MasternodeBroadcast.class, "mnb");
        names.put(MasternodePing.class, "mnp");
        names.put(MasternodeListRequest.class, "dseg");
        names.put(MasternodeBudgetVote.class, "mvote");
        names.put(TransactionLockRequest.class, "txlreq");
        names.put(ConsensusVote.class, "txlvote");
        names.put(DarkSendElectionEntryPingMessage.class, "dseep");
        names.put(SporkMessage.class, "spork");
        names.put(GetSporksMessage.class, "getsporks");
    }

}
