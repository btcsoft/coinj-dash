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

import org.bitcoinj.core.Coin;
import org.coinj.api.TransactionWireStrategy;

import javax.annotation.Nullable;

/**
 * Date: 5/26/15
 * Time: 1:11 AM
 *
 * @author Mikhail Kulikov
 */
public enum DashTransactionWireStrategy implements TransactionWireStrategy {

    STANDARD,
    INSTANT_X {
        @Nullable
        @Override
        public Coin defaultFee() {
            return Coin.valueOf(INSTANT_X_DEFAULT_FEE);
        }
    },
    INSTANT_X_LOCKED;

    public static final long INSTANT_X_DEFAULT_FEE = 1000000;

    @Nullable
    @Override
    public Coin defaultFee() {
        return null;
    }

}
