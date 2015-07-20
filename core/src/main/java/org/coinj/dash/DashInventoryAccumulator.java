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

import com.google.common.base.Preconditions;
import org.bitcoinj.core.InventoryItem;
import org.coinj.commons.AbstractPeerExtension;

/**
* Date: 5/17/15
* Time: 1:32 PM
*
* @author Mikhail Kulikov
*/
public final class DashInventoryAccumulator extends AbstractPeerExtension.AbstractInventoryAccumulator {

    DashInventoryAccumulator() {}

    @Override
    protected boolean isItemSupported(InventoryItem item) {
        Preconditions.checkNotNull(item);
        Preconditions.checkNotNull(item.type);
        return  item.type.equals(DashDefinition.INV_TYPE_TRANSACTION_LOCK_REQUEST)
                || item.type.equals(DashDefinition.INV_TYPE_TRANSACTION_LOCK_VOTE)
                || item.type.equals(DashDefinition.INV_TYPE_SPORK)
                || item.type.equals(DashDefinition.INV_TYPE_MASTERNODE_WINNER)
                || item.type.equals(DashDefinition.INV_TYPE_MASTERNODE_SCANNING_ERROR)
                || item.type.equals(DashDefinition.INV_TYPE_BUDGET_VOTE)
                || item.type.equals(DashDefinition.INV_TYPE_BUDGET_PROPOSAL)
                || item.type.equals(DashDefinition.INV_TYPE_BUDGET_FINALIZED)
                || item.type.equals(DashDefinition.INV_TYPE_BUDGET_FINALIZED_VOTE)
                || item.type.equals(DashDefinition.INV_TYPE_MASTERNODE_QUORUM)
                || item.type.equals(DashDefinition.INV_TYPE_MASTERNODE_ANNOUNCE)
                || item.type.equals(DashDefinition.INV_TYPE_MASTERNODE_PING);
    }

}
