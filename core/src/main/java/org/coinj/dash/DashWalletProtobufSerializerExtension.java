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

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.WalletTransaction;
import org.coinj.api.TransactionExtension;
import org.coinj.api.WalletProtobufSerializerExtension;

import javax.annotation.Nullable;

/**
* Date: 5/17/15
* Time: 1:36 PM
*
* @author Mikhail Kulikov
*/
public final class DashWalletProtobufSerializerExtension implements WalletProtobufSerializerExtension {

    static final DashWalletProtobufSerializerExtension INSTANCE = new DashWalletProtobufSerializerExtension();

    private DashWalletProtobufSerializerExtension() {}

    @Nullable
    @Override
    public Protos.Transaction.Pool getProtoExtendedPool(WalletTransaction.Pool pool) {
        if (pool == DashWalletCoinSpecifics.INSTANTX_PENDING) {
            return Protos.Transaction.Pool.INSTANTX_PENDING;
        } else if (pool == DashWalletCoinSpecifics.INSTANTX_LOCKED) {
            return Protos.Transaction.Pool.INSTANTX_LOCKED;
        } else {
            return null;
        }
    }

    @Nullable
    @Override
    public WalletTransaction.Pool getTxsExtendedPool(Protos.Transaction.Pool pool) {
        switch (pool) {
            case INSTANTX_LOCKED:
                return DashWalletCoinSpecifics.INSTANTX_LOCKED;
            case INSTANTX_PENDING:
                return DashWalletCoinSpecifics.INSTANTX_PENDING;
            default:
                return null;
        }
    }

    @Nullable
    @Override
    public TransactionConfidence.ConfidenceType getTxsExtendedConfidenceType(Protos.TransactionConfidence.Type type) {
        switch (type) {
            case INSTANTX_PENDING:
                return DashTransactionConfidenceExtension.INSTANTX_PENDING;
            case INSTANTX_LOCKED:
                return DashTransactionConfidenceExtension.INSTANTX_LOCKED;
            default:
                return null;
        }
    }

    @Override
    public Protos.TransactionExtension protoFromTransactionExtension(TransactionExtension txExtension) {
        final DashTransactionExtension dashTxExt = CommonUtils.extractTransactionExtension(txExtension);
        final Protos.TransactionExtension.Builder builder = Protos.TransactionExtension.newBuilder();
        final DashTransactionWireStrategy wireStrategy = dashTxExt.getWireStrategy();

        final Protos.TransactionExtension.WireStrategy protoWireStrategy;
        if (wireStrategy != null) {
            switch (wireStrategy) {
                case INSTANT_X:
                    protoWireStrategy = Protos.TransactionExtension.WireStrategy.INSTANT_X; break;
                case INSTANT_X_LOCKED:
                    protoWireStrategy = Protos.TransactionExtension.WireStrategy.INSTANT_X_LOCKED; break;
                case STANDARD:
                    // fall down
                default:
                    protoWireStrategy = Protos.TransactionExtension.WireStrategy.STANDARD;
            }
        } else {
            protoWireStrategy = Protos.TransactionExtension.WireStrategy.STANDARD;
        }

        builder.setWireStrategy(protoWireStrategy);
        builder.setLockTime(dashTxExt.getLockingTime());
        return builder.build();
    }

    @Override
    public DashTransactionExtension transactionExtensionFromProto(Protos.TransactionExtension txExtension, Transaction tx) {
        final DashTransactionExtension dashTxExt = CommonUtils.extractDefinition(tx.getParams()).createTransactionExtension(tx);
        final Protos.TransactionExtension.WireStrategy wireStrategy = txExtension.getWireStrategy();
        final DashTransactionWireStrategy dashWireStrategy;
        switch (wireStrategy) {
            case INSTANT_X:
                dashWireStrategy = DashTransactionWireStrategy.INSTANT_X;
                break;
            case INSTANT_X_LOCKED:
                dashWireStrategy = DashTransactionWireStrategy.INSTANT_X_LOCKED;
                break;
            case STANDARD:
                // fall down
            default:
                dashWireStrategy = DashTransactionWireStrategy.STANDARD;
        }
        dashTxExt.setWireStrategy(dashWireStrategy);
        if (dashWireStrategy != DashTransactionWireStrategy.STANDARD)
            dashTxExt.setLockingTime(txExtension.getLockTime());
        return dashTxExt;
    }

}
