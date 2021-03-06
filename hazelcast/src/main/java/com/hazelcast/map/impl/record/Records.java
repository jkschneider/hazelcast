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

package com.hazelcast.map.impl.record;

import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.nio.serialization.Data;

import static com.hazelcast.map.impl.record.Record.NOT_CACHED;

/**
 * Contains various factory & helper methods for a {@link com.hazelcast.map.impl.record.Record} object.
 */
public final class Records {

    private Records() {
    }

    public static void applyRecordInfo(Record record, RecordInfo replicationInfo) {
        record.setStatistics(replicationInfo.getStatistics());
        record.setVersion(replicationInfo.getVersion());
        record.setEvictionCriteriaNumber(replicationInfo.getEvictionCriteriaNumber());
        record.setTtl(replicationInfo.getTtl());
        record.setLastAccessTime(replicationInfo.getLastAccessTime());
        record.setLastUpdateTime(replicationInfo.getLastUpdateTime());
    }

    public static RecordInfo buildRecordInfo(Record record) {
        final RecordInfo info = new RecordInfo();
        info.setStatistics(record.getStatistics());
        info.setVersion(record.getVersion());
        info.setEvictionCriteriaNumber(record.getEvictionCriteriaNumber());
        info.setLastAccessTime(record.getLastAccessTime());
        info.setLastUpdateTime(record.getLastUpdateTime());
        info.setTtl(record.getTtl());
        return info;
    }

    public static Object getValueOrCachedValue(Record record, SerializationService serializationService) {
        Object value = record.getCachedValue();
        if (value == NOT_CACHED) {
            value = record.getValue();
        } else if (value == null) {
            value = record.getValue();
            if (shouldCache(record, value)) {
                value = serializationService.toObject(value);
                record.setCachedValue(value);
            }
        }
        return value;
    }

    private static boolean shouldCache(Record record, Object value) {
        boolean isCachableRecordType = record instanceof CachedDataRecordWithStats || record instanceof CachedDataRecord;
        if (!isCachableRecordType) {
            return false;
        }
        return value instanceof Data && !((Data) value).isPortable();
    }

}
