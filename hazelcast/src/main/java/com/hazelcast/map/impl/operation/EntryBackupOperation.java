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

package com.hazelcast.map.impl.operation;

import com.hazelcast.core.EntryEventType;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.impl.LazyMapEntry;
import com.hazelcast.map.impl.MapContainer;
import com.hazelcast.map.impl.event.MapEventPublisher;
import com.hazelcast.map.impl.record.Record;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.BackupOperation;
import com.hazelcast.spi.impl.MutatingOperation;
import com.hazelcast.util.Clock;

import java.io.IOException;
import java.util.Map;

import static com.hazelcast.map.impl.EntryViews.createSimpleEntryView;

public class EntryBackupOperation extends KeyBasedMapOperation implements BackupOperation, MutatingOperation {

    protected transient Object oldValue;
    private EntryBackupProcessor entryProcessor;

    public EntryBackupOperation() {
    }

    public EntryBackupOperation(String name, Data dataKey, EntryBackupProcessor entryProcessor) {
        super(name, dataKey);
        this.entryProcessor = entryProcessor;
    }

    @Override
    public void innerBeforeRun() throws Exception {
        super.innerBeforeRun();

        if (entryProcessor instanceof HazelcastInstanceAware) {
            HazelcastInstance hazelcastInstance = getNodeEngine().getHazelcastInstance();
            ((HazelcastInstanceAware) entryProcessor).setHazelcastInstance(hazelcastInstance);
        }
    }

    @Override
    public void run() {
        final long now = getNow();
        oldValue = getValueFor(dataKey, now);

        Map.Entry entry = createMapEntry(dataKey, oldValue);

        processBackup(entry);

        if (noOpBackup(entry)) {
            return;
        }

        if (entryRemovedBackup(entry)) {
            return;
        }

        entryAddedOrUpdatedBackup(entry);
    }

    private void publishWanReplicationEvent(EntryEventType eventType) {
        final MapContainer mapContainer = this.mapContainer;
        if (!mapContainer.isWanReplicationEnabled()) {
            return;
        }
        final MapEventPublisher mapEventPublisher = mapContainer.getMapServiceContext().getMapEventPublisher();
        final Data key = dataKey;

        if (EntryEventType.REMOVED == eventType) {
            mapEventPublisher.publishWanReplicationRemoveBackup(name, key, getNow());
        } else {
            final Record record = recordStore.getRecord(key);
            if (record != null) {
                dataValue = mapContainer.getMapServiceContext().toData(dataValue);
                final EntryView entryView = createSimpleEntryView(key, dataValue, record);
                mapEventPublisher.publishWanReplicationUpdateBackup(name, entryView);
            }
        }
    }

    @Override
    public void afterRun() throws Exception {
        evict();
    }

    @Override
    public Object getResponse() {
        return true;
    }

    private void processBackup(Map.Entry entry) {
        entryProcessor.processBackup(entry);
    }

    private boolean entryRemovedBackup(Map.Entry entry) {
        final Object value = entry.getValue();
        if (value == null) {
            recordStore.removeBackup(dataKey);
            publishWanReplicationEvent(EntryEventType.REMOVED);
            return true;
        }
        return false;
    }

    private boolean entryAddedOrUpdatedBackup(Map.Entry entry) {
        final Object value = entry.getValue();
        if (value != null) {
            recordStore.putBackup(dataKey, value);
            publishWanReplicationEvent(EntryEventType.UPDATED);
            return true;
        }
        return false;
    }

    /**
     * noOpBackup in two cases:
     * - setValue not called on entry
     * - or entry does not exist and no add operation is done.
     */
    private boolean noOpBackup(Map.Entry entry) {
        final LazyMapEntry mapEntrySimple = (LazyMapEntry) entry;
        return !mapEntrySimple.isModified() || (oldValue == null && entry.getValue() == null);
    }

    private Map.Entry createMapEntry(Data key, Object value) {
        return new LazyMapEntry(key, value, getNodeEngine().getSerializationService());
    }

    private Object getValueFor(Data dataKey, long now) {
        Map.Entry<Data, Object> mapEntry = recordStore.getMapEntry(dataKey, now);
        return mapEntry.getValue();
    }

    private long getNow() {
        return Clock.currentTimeMillis();
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        entryProcessor = in.readObject();
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeObject(entryProcessor);
    }
}
