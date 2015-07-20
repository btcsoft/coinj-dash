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

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.TransactionInput;

import javax.annotation.Nullable;

/**
 * Date: 5/21/15
 * Time: 12:03 AM
 *
 * @author Mikhail Kulikov
 */
public interface MasternodeStateInfo {

    String STATE_STATUS_ENABLED = "ENABLED";
    String STATE_STATUS_EXPIRED = "EXPIRED";
    String STATE_STATUS_VIN_SPENT = "VIN_SPENT";
    String STATE_STATUS_REMOVE = "REMOVE";
    String STATE_STATUS_POS_ERROR = "POS_ERROR";

    enum State {

        MASTERNODE_ENABLED(STATE_STATUS_ENABLED),
        MASTERNODE_EXPIRED(STATE_STATUS_EXPIRED),
        MASTERNODE_VIN_SPENT(STATE_STATUS_VIN_SPENT),
        MASTERNODE_REMOVE(STATE_STATUS_REMOVE),
        MASTERNODE_POS_ERROR(STATE_STATUS_POS_ERROR);

        public final String status;

        State(String status) {
            this.status = status;
        }

    }

    TransactionInput getTxIn();

    PeerAddress getAddr();

    @Nullable
    byte[] getSignature();

    long getSigTime();

    ECKey getPubKey1();

    ECKey getPubKey2();

    long getLastTimeSeen();

    int getProtocolVersion();

    byte[] getDonationAddress();

    int getDonationPercentage();

    long getLastPaid();

}
