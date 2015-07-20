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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Date: 5/26/15
 * Time: 4:38 PM
 *
 * @author Mikhail Kulikov
 */
public final class CoreHelper {

    public static void callProcessTransaction(Peer peer, Transaction tx) {
        peer.processTransaction(tx);
    }

    public static void callProcessTxInvItem(Peer peer, InventoryItem item, GetDataMessage getdata, Iterator<InventoryItem> it) {
        peer.processTxInvItem(item, getdata, it);
    }

    public static void callSeenTxInv(Peer peer, InventoryItem item) {
        peer.seenTxInv(item);
    }

    public static ReentrantLock getWalletLock(Wallet wallet) {
        return wallet.lock;
    }

    public static Map<Sha256Hash, Transaction> getWalletPendingPool(Wallet wallet) {
        return wallet.pending;
    }
    public static Map<Sha256Hash, Transaction> getWalletUnspentPool(Wallet wallet) {
        return wallet.unspent;
    }
    public static Map<Sha256Hash, Transaction> getWalletSpentPool(Wallet wallet) {
        return wallet.spent;
    }
    public static Map<Sha256Hash, Transaction> getWalletDeadPool(Wallet wallet) {
        return wallet.dead;
    }

    public static void walletOnTxConfidenceChangedAndSave(Wallet wallet, Transaction tx) {
        wallet.queueOnTransactionConfidenceChanged(tx);
        wallet.saveNow();
    }

    private CoreHelper() {}

}
