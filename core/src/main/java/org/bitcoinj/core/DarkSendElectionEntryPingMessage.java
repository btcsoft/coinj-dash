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

/**
 * Date: 5/16/15
 * Time: 4:45 AM
 *
 * @author Mikhail Kulikov
 */
public class DarkSendElectionEntryPingMessage extends Message {

    private static final long serialVersionUID = 1L;

    public DarkSendElectionEntryPingMessage(NetworkParameters params, byte[] payloadBytes) {
        super(params, payloadBytes, 0, false, false, payloadBytes.length);
    }

    @Override
    void parse() throws ProtocolException {

    }

    @Override
    protected void parseLite() throws ProtocolException {

    }

}
