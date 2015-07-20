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

import org.bitcoinj.core.Message;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionLockRequest;
import org.bitcoinj.core.Utils;
import org.coinj.api.TransactionExtension;
import org.coinj.api.TransactionWireStrategy;

import javax.annotation.Nullable;

/**
 * Date: 5/26/15
 * Time: 1:12 AM
 *
 * @author Mikhail Kulikov
 */
public class DashTransactionExtension implements TransactionExtension {

    private static final long serialVersionUID = 1L;

    private final Transaction tx;
    private DashTransactionWireStrategy wireStrategy;
    private long lockingTime = 0; // in seconds
    private boolean isRestored = false;

    DashTransactionExtension(Transaction tx) {
        this.tx = tx;
    }

    @Override
    public Message getBroadcastMessage() {
        final DashNetwork dashNetwork = CommonUtils.extractNetworkExtension(tx.getParams());
        if (wireStrategy == null || wireStrategy == DashTransactionWireStrategy.STANDARD || !dashNetwork.permitsLockRequestSending() || dashNetwork.isSyncingMasternodeAssets()) {
            return tx;
        }
        // we are about to broadcast lock request
        if (dashNetwork.permitsInstantX(tx)) {
            dashNetwork.getInstantXManager().internLockRequest(tx.getHash());
        }
        return new TransactionLockRequest(tx);
    }

    @Nullable
    @Override
    public DashTransactionWireStrategy getWireStrategy() {
        return wireStrategy;
    }

    @Override
    public void setWireStrategy(@Nullable TransactionWireStrategy strategy) {
        if (strategy instanceof DashTransactionWireStrategy) {
            wireStrategy = (DashTransactionWireStrategy) strategy;
            lockingTime = (wireStrategy == DashTransactionWireStrategy.STANDARD)
                    ? 0
                    : Utils.currentTimeSeconds();
        } else {
            lockingTime = 0;
        }
    }

    public long getLockingTime() {
        return lockingTime;
    }

    public boolean isRestored() {
        return isRestored;
    }

    void setLockingTime(long lockingTime) {
        this.lockingTime = lockingTime;
        isRestored = true;
    }

}
