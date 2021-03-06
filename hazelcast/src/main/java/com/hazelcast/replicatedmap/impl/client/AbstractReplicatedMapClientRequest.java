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

package com.hazelcast.replicatedmap.impl.client;

import com.hazelcast.client.impl.client.PartitionClientRequest;
import com.hazelcast.client.impl.client.RetryableRequest;
import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableReader;
import com.hazelcast.nio.serialization.PortableWriter;
import com.hazelcast.replicatedmap.impl.ReplicatedMapService;
import java.io.IOException;

/**
 * Base class for all ReplicatedMap client request.
 */
public abstract class AbstractReplicatedMapClientRequest extends PartitionClientRequest
        implements RetryableRequest, Portable {

    private String mapName;
    private int partitionId;

    protected AbstractReplicatedMapClientRequest() {
    }

    public AbstractReplicatedMapClientRequest(String mapName) {
        this.mapName = mapName;
    }

    public AbstractReplicatedMapClientRequest(String mapName, int partitionId) {
        this.mapName = mapName;
        this.partitionId = partitionId;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    @Override
    public String getServiceName() {
        return ReplicatedMapService.SERVICE_NAME;
    }

    @Override
    public void write(PortableWriter writer) throws IOException {
        writer.writeUTF("mapName", mapName);
        writer.writeInt("pid", partitionId);
    }

    @Override
    public void read(PortableReader reader) throws IOException {
        mapName = reader.readUTF("mapName");
        partitionId = reader.readInt("pid");
    }

    @Override
    public int getFactoryId() {
        return ReplicatedMapPortableHook.F_ID;
    }

    @Override
    protected int getPartition() {
        return partitionId;
    }

    @Override
    public String getDistributedObjectName() {
        return mapName;
    }
}
