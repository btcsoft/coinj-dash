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
import org.coinj.api.BlockChainExtension;
import org.coinj.api.CoinDefinition;
import org.coinj.api.NetworkExtensionsContainer;
import org.coinj.api.TransactionExtension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Date: 5/18/15
 * Time: 4:48 PM
 *
 * @author Mikhail Kulikov
 */
public final class CommonUtils {

    static double convertBitsToDouble(long bits) {
        long shift = (bits >> 24) & 0xff;
        double diff = (double) 0x0000ffff / (double) (bits & 0x00ffffff);

        if (shift < 29) {
            do {
                diff *= 256.0;
                shift++;
            } while (shift < 29);
        } else {
            while (shift > 29) {
                diff /= 256.0;
                shift--;
            }
        }

        return diff;
    }

    static void relayMessage(Message message, @Nullable Peer peer, @Nullable PeerGroup peerGroup) {
        if (peerGroup == null) {
            if (peer == null) return;
            peerGroup = peer.getPeerGroup();
            if (peerGroup != null) {
                peerGroupRelay(message, peerGroup);
            } else {
                peer.sendMessage(message);
            }
        } else {
            peerGroupRelay(message, peerGroup);
        }
    }
    private static void peerGroupRelay(Message message, PeerGroup peerGroup) {
        for (final Peer p : peerGroup.getConnectedPeers()) {
            p.sendMessage(message);
        }
    }

    static boolean peerDownloading(@Nonnull Peer p) {
        return p.getDownloadData() && (p.getBestHeight() - p.getBlockChain().getBestChainHeight() > 0);
    }

    static boolean isBlockChainDownloading(@Nullable Peer peer) {
        return peer != null
                && (isBlockChainDownloading(peer.getPeerGroup()) || peerDownloading(peer));
    }

    static boolean isBlockChainDownloading(@Nullable PeerGroup peerGroup) {
        if (peerGroup != null) {
            for (final Peer p : peerGroup.getConnectedPeers()) {
                if (CommonUtils.peerDownloading(p))
                    return true;
            }
        }
        return false;
    }

    static void relayInv(int ordinal, Hashable hashable, DashNetwork network, @Nullable Peer peer, @Nullable PeerGroup peerGroup) {
        if (network.mustSendInventory()) {
            final NetworkParameters params = network.getParams();
            final InventoryMessage inv = new InventoryMessage(params);
            inv.addItem(InventoryItem.createByTypeCode(hashable.getHash(), ordinal, params.getCoinDefinition()));
            relayMessage(inv, peer, peerGroup);
        }
    }

    public static DashDefinition extractDefinition(NetworkParameters params) {
        final CoinDefinition def = params.getCoinDefinition();
        if (def instanceof DashDefinition) {
            return (DashDefinition) def;
        } else {
            throw new IllegalArgumentException(wrongNetworkMessage(def));
        }
    }

    public static DashNetwork extractNetworkExtension(NetworkParameters params) {
        final NetworkExtensionsContainer extensionsContainer = params.getExtensionsContainer();
        if (extensionsContainer instanceof DashNetwork) {
            return (DashNetwork) extensionsContainer;
        } else {
            throw new IllegalArgumentException(wrongNetworkMessage(params.getCoinDefinition()));
        }
    }

    public static DashChainExtension extractChainExtension(AbstractBlockChain blockChain, CoinDefinition def) {
        final BlockChainExtension chainExtension = blockChain.getChainExtension();
        if (chainExtension instanceof DashChainExtension) {
            return (DashChainExtension) chainExtension;
        } else
            throw new IllegalArgumentException(wrongNetworkMessage(def));
    }

    public static DashTransactionExtension extractTransactionExtension(Transaction tx) {
        return extractTransactionExtension(tx.getTransactionExtension());
    }

    public static DashTransactionExtension extractTransactionExtension(TransactionExtension transactionExtension) {
        if (transactionExtension instanceof DashTransactionExtension) {
            return (DashTransactionExtension) transactionExtension;
        } else {
            throw new IllegalArgumentException("Illegal network parameters with wrong TransactionExtension implementation: " + transactionExtension.getClass().getName());
        }
    }

    public static DashNetwork.MinConfirmationsCheckResult performMinConfirmationsCheck(DashNetwork network, VerificationInputContext inputContext, long sigTime) {
        return network.performMinConfirmationsCheck(inputContext, sigTime);
    }

    private static String wrongNetworkMessage(CoinDefinition def) {
        return "Illegal network parameters with wrong CoinDefinition implementation: " + def.getClass().getName();
    }

    private CommonUtils() {}

}
