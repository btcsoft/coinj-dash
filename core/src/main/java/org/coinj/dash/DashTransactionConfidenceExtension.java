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
import org.coinj.api.CoinDefinition;
import org.coinj.api.TransactionConfidenceExtension;
import org.coinj.api.TransactionWireStrategy;

import javax.annotation.Nullable;

/**
* Date: 5/17/15
* Time: 1:34 PM
*
* @author Mikhail Kulikov
*/
public final class DashTransactionConfidenceExtension implements TransactionConfidenceExtension {

    private static final long serialVersionUID = 1L;

    public static final int VALUE_CONFIDENCE_INSTANTX_PENDING = 5;
    public static final int VALUE_CONFIDENCE_INSTANTX_LOCKED = 6;

    public static final TransactionConfidence.ConfidenceType INSTANTX_PENDING = TransactionConfidence.ConfidenceType.createExtension(VALUE_CONFIDENCE_INSTANTX_PENDING);
    public static final TransactionConfidence.ConfidenceType INSTANTX_LOCKED = TransactionConfidence.ConfidenceType.createExtension(VALUE_CONFIDENCE_INSTANTX_LOCKED);

    private final TransactionConfidence transactionConfidence;

    DashTransactionConfidenceExtension(TransactionConfidence transactionConfidence) {
        this.transactionConfidence = transactionConfidence;
    }

    @Override
    public boolean acknowledgeExtendedConfidenceType(TransactionConfidence.ConfidenceType confidenceType) {
        return confidenceType.equals(INSTANTX_LOCKED) || confidenceType.equals(INSTANTX_PENDING);
    }

    @Override
    public void appendToStringBuilder(StringBuilder builder, final TransactionConfidence confidence) {
        final TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
        if (confidenceType.equals(INSTANTX_PENDING)) {
            builder.append("InstantX Lock Request");
        } else if (confidenceType.equals(INSTANTX_LOCKED)) {
            builder.append("InstantX Locked");
            final int chainHeight = confidence.getAppearedAtChainHeight();
            if (chainHeight > 0) {
                builder.append(String.format("Appeared in best chain at height %d, depth %d.",
                        chainHeight, confidence.getDepthInBlocks()));
            }
        }
    }

    @Override
    public boolean isAllowedAppearanceAtChainHeight(TransactionConfidence.ConfidenceType confidenceType) {
        return confidenceType.equals(INSTANTX_LOCKED);
    }

    @Override
    public boolean allowBuildingTypeIfChainAppearanceSet(TransactionConfidence.ConfidenceType confidenceType) {
        return !confidenceType.equals(INSTANTX_LOCKED);
    }

    @Override
    public boolean mustDoDepthNullSettingAtSetTypeIfNotPending(TransactionConfidence.ConfidenceType confidenceType) {
        return confidenceType.equals(INSTANTX_PENDING);
    }

    @Override
    public boolean isMatureConfidenceType(TransactionConfidence.ConfidenceType confidenceType) {
        return confidenceType.equals(INSTANTX_LOCKED);
    }

    @Override
    public boolean haveSpecialMaturityConditions(TransactionConfidence.ConfidenceType confidenceType) {
        return false;
    }

    @Override
    public boolean specialMaturityConditions(TransactionConfidence.ConfidenceType confidenceType) {
        return false;
    }

    @Override
    public boolean isCoinsSelectableByDefault(Transaction tx) {
        final TransactionConfidence cnf = tx.getConfidence();
        final TransactionConfidence.ConfidenceType confidenceType = cnf.getConfidenceType();
        return confidenceType.equals(INSTANTX_LOCKED)
                || (confidenceType.equals(INSTANTX_PENDING) && (cnf.numBroadcastPeers() > 1 || CoinDefinition.REG_TEST_STANDARD.equals(tx.getParams().getStandardNetworkId())));
    }

    @Nullable
    @Override
    public String getConfidenceTypeName(TransactionConfidence.ConfidenceType confidenceType) {
        if (confidenceType.equals(INSTANTX_PENDING)) {
            return "INSTANTX_PENDING";
        } else if (confidenceType.equals(INSTANTX_LOCKED)) {
            return "INSTANTX_LOCKED";
        } else {
            return null;
        }
    }

    @Override
    public boolean isExtendedConfidenceRelevant(Transaction tx) {
        final TransactionWireStrategy wireStrategy = tx.getTransactionExtension().getWireStrategy();
        return wireStrategy == DashTransactionWireStrategy.INSTANT_X || wireStrategy == DashTransactionWireStrategy.INSTANT_X_LOCKED;
    }

    @Override
    public TransactionConfidence.ConfidenceType markBroadcastByExtendedConfidenceType() {
        return INSTANTX_PENDING;
    }

    @Override
    public TransactionConfidenceExtension copy() {
        return new DashTransactionConfidenceExtension(transactionConfidence);
    }

}
