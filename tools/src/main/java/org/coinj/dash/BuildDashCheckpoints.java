package org.coinj.dash;

import org.bitcoinj.tools.BuildCheckpoints;
import org.coinj.api.CoinLocator;

/**
 * Date: 6/24/15
 * Time: 2:47 PM
 *
 * @author Mikhail Kulikov
 */
public final class BuildDashCheckpoints {

    public static void main(String[] args) {
        CoinLocator.registerCoin(DashDefinition.INSTANCE);
        try {
            //args = new String[3];
            //args[0] = "useDiscovery";
            //args[1] = "networkId";
            //args[2] = "org.dash.test";
            BuildCheckpoints.main(args);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private BuildDashCheckpoints() {}

}
