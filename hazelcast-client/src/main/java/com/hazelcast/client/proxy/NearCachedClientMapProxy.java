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

package com.hazelcast.client.proxy;

import com.hazelcast.cache.impl.nearcache.NearCache;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.MapAddNearCacheEntryListenerCodec;
import com.hazelcast.client.impl.protocol.codec.MapGetAllCodec;
import com.hazelcast.client.impl.protocol.codec.MapRemoveCodec;
import com.hazelcast.client.impl.protocol.codec.MapRemoveEntryListenerCodec;
import com.hazelcast.client.map.impl.nearcache.ClientHeapNearCache;
import com.hazelcast.client.spi.EventHandler;
import com.hazelcast.client.spi.impl.ListenerMessageCodec;
import com.hazelcast.client.util.ClientDelegatingFuture;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.core.EntryEventType;
import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.logging.Logger;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.monitor.NearCacheStats;
import com.hazelcast.monitor.impl.LocalMapStatsImpl;
import com.hazelcast.monitor.impl.NearCacheStatsImpl;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.util.CollectionUtil;
import com.hazelcast.util.MapUtil;
import com.hazelcast.util.executor.CompletedFuture;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.cache.impl.nearcache.NearCache.NULL_OBJECT;
import static com.hazelcast.map.impl.MapListenerFlagOperator.ALL_LISTENER_FLAGS;
import static java.util.Collections.emptyMap;

/**
 * A Client-side {@code IMap} implementation which is fronted by a near-cache.
 *
 * @param <K> the key type for this {@code IMap} proxy.
 * @param <V> the value type for this {@code IMap} proxy.
 */
public class NearCachedClientMapProxy<K, V> extends ClientMapProxy<K, V> {

    protected NearCache nearCache;
    protected volatile String invalidationListenerId;

    public NearCachedClientMapProxy(String serviceName, String name) {
        super(serviceName, name);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();

        init();
    }

    protected void init() {
        NearCacheConfig nearCacheConfig = getContext().getClientConfig().getNearCacheConfig(name);
        this.nearCache = new ClientHeapNearCache<Data>(name, getContext(), nearCacheConfig);

        if (this.nearCache.isInvalidateOnChange()) {
            addNearCacheInvalidateListener();
        }
    }

    @Override
    protected boolean containsKeyInternal(Data keyData) {
        Object cached = nearCache.get(keyData);
        if (cached != null) {
            return NULL_OBJECT != cached;
        }

        return super.containsKeyInternal(keyData);
    }


    @Override
    protected V getInternal(Data keyData) {
        Object cached = nearCache.get(keyData);
        if (cached != null) {
            if (NULL_OBJECT == cached) {
                return null;
            }
            return (V) cached;
        }

        V response = super.getInternal(keyData);
        nearCache.put(keyData, response);

        return response;
    }

    @Override
    protected MapRemoveCodec.ResponseParameters removeInternal(Data keyData) {
        invalidateNearCache(keyData);
        return super.removeInternal(keyData);
    }

    @Override
    protected boolean removeInternal(Data keyData, Data valueData) {
        invalidateNearCache(keyData);
        return super.removeInternal(keyData, valueData);
    }

    @Override
    protected void deleteInternal(Data keyData) {
        invalidateNearCache(keyData);
        super.deleteInternal(keyData);
    }

    @Override
    public ICompletableFuture<V> getAsyncInternal(final Data keyData) {
        Object cached = nearCache.get(keyData);
        if (cached != null && NULL_OBJECT != cached) {
            return new CompletedFuture<V>(getContext().getSerializationService(),
                    cached, getContext().getExecutionService().getAsyncExecutor());
        }

        ICompletableFuture<V> future = super.getAsyncInternal(keyData);
        ((ClientDelegatingFuture) future).andThenInternal(new ExecutionCallback<Data>() {
            @Override
            public void onResponse(Data response) {
                nearCache.put(keyData, response);
            }

            @Override
            public void onFailure(Throwable t) {

            }
        });
        return future;
    }

    @Override
    protected Future<V> putAsyncInternal(long ttl, TimeUnit timeunit, Data keyData, Data valueData) {
        invalidateNearCache(keyData);
        return super.putAsyncInternal(ttl, timeunit, keyData, valueData);
    }

    @Override
    protected Future<V> removeAsyncInternal(Data keyData) {
        invalidateNearCache(keyData);
        return super.removeAsyncInternal(keyData);
    }

    @Override
    protected boolean tryRemoveInternal(long timeout, TimeUnit timeunit, Data keyData) {
        invalidateNearCache(keyData);
        return super.tryRemoveInternal(timeout, timeunit, keyData);
    }

    @Override
    protected boolean tryPutInternal(long timeout, TimeUnit timeunit, Data keyData, Data valueData) {
        invalidateNearCache(keyData);
        return super.tryPutInternal(timeout, timeunit, keyData, valueData);
    }

    @Override
    protected V putInternal(long ttl, TimeUnit timeunit, Data keyData, Data valueData) {
        invalidateNearCache(keyData);
        return super.putInternal(ttl, timeunit, keyData, valueData);
    }

    @Override
    protected void putTransientInternal(long ttl, TimeUnit timeunit, Data keyData, Data valueData) {
        invalidateNearCache(keyData);
        super.putTransientInternal(ttl, timeunit, keyData, valueData);
    }

    @Override
    protected V putIfAbsentInternal(long ttl, TimeUnit timeunit, Data keyData, Data valueData) {
        invalidateNearCache(keyData);
        return super.putIfAbsentInternal(ttl, timeunit, keyData, valueData);
    }

    @Override
    protected boolean replaceIfSameInternal(Data keyData, Data oldValueData, Data newValueData) {
        invalidateNearCache(keyData);
        return super.replaceIfSameInternal(keyData, oldValueData, newValueData);
    }

    @Override
    protected V replaceInternal(Data keyData, Data valueData) {
        invalidateNearCache(keyData);
        return super.replaceInternal(keyData, valueData);
    }

    @Override
    protected void setInternal(long ttl, TimeUnit timeunit, Data keyData, Data valueData) {
        invalidateNearCache(keyData);
        super.setInternal(ttl, timeunit, keyData, valueData);
    }

    @Override
    protected boolean evictInternal(Data keyData) {
        invalidateNearCache(keyData);
        return super.evictInternal(keyData);
    }

    @Override
    public void evictAll() {
        nearCache.clear();
        super.evictAll();
    }

    @Override
    public void loadAll(boolean replaceExistingValues) {
        if (replaceExistingValues) {
            nearCache.clear();
        }
        super.loadAll(replaceExistingValues);
    }

    @Override
    protected void loadAllInternal(boolean replaceExistingValues, Set<Data> dataKeys) {
        invalidateNearCache(dataKeys);
        super.loadAllInternal(replaceExistingValues, dataKeys);
    }

    @Override
    protected MapGetAllCodec.ResponseParameters getAllInternal(List<Data> keyList, Map<K, V> result) {
        Iterator<Data> iterator = keyList.iterator();
        while (iterator.hasNext()) {
            Data key = iterator.next();
            Object cached = nearCache.get(key);
            if (cached != null && NULL_OBJECT != cached) {
                result.put((K) toObject(key), (V) cached);
                iterator.remove();
            }
        }

        MapGetAllCodec.ResponseParameters resultParameters = super.getAllInternal(keyList, result);
        for (Entry<Data, Data> entry : resultParameters.entrySet) {
            nearCache.put(entry.getKey(), entry.getValue());
        }
        return resultParameters;
    }

    @Override
    public LocalMapStats getLocalMapStats() {
        LocalMapStats localMapStats = super.getLocalMapStats();
        NearCacheStats nearCacheStats = nearCache.getNearCacheStats();
        ((LocalMapStatsImpl) localMapStats).setNearCacheStats(((NearCacheStatsImpl) nearCacheStats));
        return localMapStats;
    }

    @Override
    protected Map<K, Object> prepareResult(Set<Entry<Data, Data>> entrySet) {
        if (CollectionUtil.isEmpty(entrySet)) {
            return emptyMap();
        }

        Map<K, Object> result = MapUtil.createHashMap(entrySet.size());
        for (Entry<Data, Data> entry : entrySet) {
            Data dataKey = entry.getKey();
            invalidateNearCache(dataKey);

            K key = toObject(dataKey);
            Object value = toObject(entry.getValue());
            result.put(key, value);
        }
        return result;
    }

    @Override
    protected void putAllInternal(Map<Integer, Map<Data, Data>> entryMap) {
        for (Map<Data, Data> map : entryMap.values()) {
            Set<Data> keySet = map.keySet();
            invalidateNearCache(keySet);
        }
        super.putAllInternal(entryMap);
    }

    @Override
    public void clear() {
        nearCache.clear();
        super.clear();
    }

    @Override
    protected void onDestroy() {
        removeNearCacheInvalidationListener();
        nearCache.destroy();

        super.onDestroy();
    }

    @Override
    protected void onShutdown() {
        removeNearCacheInvalidationListener();
        nearCache.destroy();

        super.onShutdown();
    }

    protected void invalidateNearCache(Data key) {
        nearCache.remove(key);
    }


    protected void invalidateNearCache(Collection<Data> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (Data key : keys) {
            nearCache.remove(key);
        }
    }

    protected void addNearCacheInvalidateListener() {
        try {
            EventHandler handler = new ClientMapAddNearCacheEventHandler(this.nearCache);
            invalidationListenerId = registerListener(createNearCacheEntryListenerCodec(), handler);

        } catch (Exception e) {
            Logger.getLogger(ClientHeapNearCache.class).severe(
                    "-----------------\n Near Cache is not initialized!!! \n-----------------", e);
        }
    }

    private ListenerMessageCodec createNearCacheEntryListenerCodec() {
        return new ListenerMessageCodec() {
            @Override
            public ClientMessage encodeAddRequest(boolean localOnly) {
                return MapAddNearCacheEntryListenerCodec.encodeRequest(name, false,
                        ALL_LISTENER_FLAGS, localOnly);
            }

            @Override
            public String decodeAddResponse(ClientMessage clientMessage) {
                return MapAddNearCacheEntryListenerCodec.decodeResponse(clientMessage).response;
            }

            @Override
            public ClientMessage encodeRemoveRequest(String realRegistrationId) {
                return MapRemoveEntryListenerCodec.encodeRequest(name, realRegistrationId);
            }

            @Override
            public boolean decodeRemoveResponse(ClientMessage clientMessage) {
                return MapRemoveEntryListenerCodec.decodeResponse(clientMessage).response;
            }
        };
    }

    protected void removeNearCacheInvalidationListener() {
        String invalidationListenerId = this.invalidationListenerId;
        if (invalidationListenerId == null) {
            return;
        }
        deregisterListener(invalidationListenerId);
    }

    protected static final class ClientMapAddNearCacheEventHandler extends MapAddNearCacheEntryListenerCodec.AbstractEventHandler
            implements EventHandler<ClientMessage> {

        protected final NearCache nearCache;

        protected ClientMapAddNearCacheEventHandler(NearCache nearCache) {
            this.nearCache = nearCache;
        }

        @Override
        public void beforeListenerRegister() {
            nearCache.clear();
        }

        @Override
        public void onListenerRegister() {
            nearCache.clear();
        }

        @Override
        public void handle(Data key, Data value, Data oldValue, Data mergingValue,
                           int eventType, String uuid, int numberOfAffectedEntries) {
            EntryEventType entryEventType = EntryEventType.getByType(eventType);
            switch (entryEventType) {
                case ADDED:
                case REMOVED:
                case UPDATED:
                case MERGED:
                case EVICTED:
                case EXPIRED:
                    nearCache.remove(key);
                    break;
                case CLEAR_ALL:
                case EVICT_ALL:
                    nearCache.clear();
                    break;
                default:
                    throw new IllegalArgumentException("Not a known event type " + entryEventType);
            }
        }
    }
}
