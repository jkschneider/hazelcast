/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.spi.impl.listener;

import com.hazelcast.client.impl.client.ClientRequest;
import com.hazelcast.client.spi.EventHandler;

public class ClientRegistrationKey {

    private final String userRegistrationId;
    private final ClientRequest request;
    private final EventHandler handler;


    public ClientRegistrationKey(String userRegistrationId, ClientRequest request, EventHandler handler) {
        this.userRegistrationId = userRegistrationId;
        this.request = request;
        this.handler = handler;
    }

    public ClientRegistrationKey(String userRegistrationId) {
        this.userRegistrationId = userRegistrationId;
        this.request = null;
        this.handler = null;
    }

    /**
     * Add listener request
     *
     * @return request
     */
    public ClientRequest getRequest() {
        return request;
    }

    /**
     * @return related event handler
     */
    public EventHandler getHandler() {
        return handler;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {

            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClientRegistrationKey that = (ClientRegistrationKey) o;

        return !(userRegistrationId != null
                ? !userRegistrationId.equals(that.userRegistrationId) : that.userRegistrationId != null);

    }

    @Override
    public int hashCode() {
        return userRegistrationId != null ? userRegistrationId.hashCode() : 0;
    }
}
