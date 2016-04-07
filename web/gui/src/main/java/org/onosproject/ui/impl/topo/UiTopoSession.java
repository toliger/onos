/*
 *  Copyright 2016 Open Networking Laboratory
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.onosproject.ui.impl.topo;

import org.onosproject.ui.UiTopoLayoutService;
import org.onosproject.ui.impl.UiWebSocket;
import org.onosproject.ui.impl.topo.model.UiSharedTopologyModel;
import org.onosproject.ui.model.topo.UiTopoLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates with the {@link UiTopoLayoutService} to access
 * {@link UiTopoLayout}s, and with the {@link UiSharedTopologyModel} which
 * maintains a local model of the network entities,
 * tailored specifically for displaying on the UI.
 * <p>
 * Note that an instance of this class will be created for each
 * {@link UiWebSocket} connection, and will contain
 * the state of how the topology is laid out for the logged-in user.
 */
public class UiTopoSession {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final UiWebSocket webSocket;
    private final String username;

    final UiSharedTopologyModel sharedModel;

    private boolean registered = false;

    private UiTopoLayoutService service;
    private UiTopoLayout currentLayout;

    /**
     * Creates a new topology session for the specified web socket connection.
     *
     * @param webSocket web socket
     */
    public UiTopoSession(UiWebSocket webSocket) {
        this.webSocket = webSocket;
        this.username = webSocket.userName();
        this.sharedModel = UiSharedTopologyModel.instance();
    }

    /**
     * Initializes the session; registering with the shared model.
     */
    public void init() {
        if (!registered) {
            log.debug("{} : Registering with shared model", this);
            sharedModel.register(this);
            registered = true;
        } else {
            log.warn("already registered");
        }
    }

    /**
     * Destroys the session; unregistering from the shared model.
     */
    public void destroy() {
        if (registered) {
            log.debug("{} : Unregistering from shared model", this);
            sharedModel.unregister(this);
            registered = false;
        } else {
            log.warn("already unregistered");
        }
    }

    @Override
    public String toString() {
        return String.format("{UiTopoSession for user <%s>}", username);
    }
}