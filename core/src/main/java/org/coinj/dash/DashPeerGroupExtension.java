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

import org.bitcoinj.core.InventoryItem;
import org.bitcoinj.core.Message;
import org.coinj.api.PeerGroupExtension;

import javax.annotation.Nullable;

/**
 * Date: 5/30/15
 * Time: 12:31 AM
 *
 * @author Mikhail Kulikov
 */
public class DashPeerGroupExtension implements PeerGroupExtension {

    private final DashNetwork network;

    DashPeerGroupExtension(DashNetwork network) {
        this.network = network;
    }

    @Nullable
    @Override
    public Message handleGetDataExtension(InventoryItem item) {
        if (network.permitsMasternodesLogic()) {
            final String type = item.type;
            if (type.equals(DashDefinition.INV_TYPE_TRANSACTION_LOCK_REQUEST)) {
                if (!network.getSporkManager().isSporkActive(SporkManager.SPORK_2_INSTANTX))
                    return null;
                return network.getInstantXManager().getKnownLockRequest(item.hash);
            } else if (type.equals(DashDefinition.INV_TYPE_TRANSACTION_LOCK_VOTE)) {
                if (!network.getSporkManager().isSporkActive(SporkManager.SPORK_2_INSTANTX))
                    return null;
                return network.getInstantXManager().getKnownConsensusVote(item.hash);
            } else if (type.equals(DashDefinition.INV_TYPE_SPORK)) {
                return network.getSporkManager().sporkMessagesRegistry.get(item.hash);
            } else if (type.equals(DashDefinition.INV_TYPE_MASTERNODE_SCANNING_ERROR)) {
                return network.getMasternodeManager().getMasternodeScanningErrorsRegistry().get(item.hash);
            } else if (type.equals(DashDefinition.INV_TYPE_MASTERNODE_ANNOUNCE)) {
                return network.getMasternodeManager().getMasternodeBroadcastRegistry().get(item.hash);
            } else if (type.equals(DashDefinition.INV_TYPE_MASTERNODE_PING)) {
                return network.getMasternodeManager().getMasternodePingRegistry().get(item.hash);
            }
        }

        return null;
    }

}
