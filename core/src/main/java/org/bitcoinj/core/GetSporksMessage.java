package org.bitcoinj.core;

import java.io.Serializable;

/**
 * Date: 6/4/15
 * Time: 3:55 PM
 *
 * @author Mikhail Kulikov
 */
public final class GetSporksMessage extends EmptyMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public GetSporksMessage(NetworkParameters params) {
        super(params);
    }

}
