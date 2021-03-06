/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.incubator.store.routing.impl;

import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.util.KryoNamespace;
import org.onosproject.incubator.net.routing.InternalRouteEvent;
import org.onosproject.incubator.net.routing.Route;
import org.onosproject.incubator.net.routing.RouteSet;
import org.onosproject.incubator.net.routing.RouteStoreDelegate;
import org.onosproject.incubator.net.routing.RouteTableId;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.MapEvent;
import org.onosproject.store.service.MapEventListener;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.Versioned;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default implementation of a route table based on a consistent map.
 */
public class DefaultRouteTable implements RouteTable {

    private final RouteTableId id;
    private final ConsistentMap<IpPrefix, Set<Route>> routes;
    private final RouteStoreDelegate delegate;
    private final RouteTableListener listener = new RouteTableListener();

    /**
     * Creates a new route table.
     *
     * @param id route table ID
     * @param delegate route store delegate to notify of events
     * @param storageService storage service
     */
    public DefaultRouteTable(RouteTableId id, RouteStoreDelegate delegate,
                             StorageService storageService) {
        this.delegate = checkNotNull(delegate);
        this.id = checkNotNull(id);
        this.routes = buildRouteMap(checkNotNull(storageService));

        routes.entrySet().stream()
                .map(e -> new InternalRouteEvent(InternalRouteEvent.Type.ROUTE_ADDED,
                        new RouteSet(id, e.getKey(), e.getValue().value())))
                .forEach(delegate::notify);

        routes.addListener(listener);
    }

    private ConsistentMap<IpPrefix, Set<Route>> buildRouteMap(StorageService storageService) {
        KryoNamespace routeTableSerializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(Route.class)
                .register(Route.Source.class)
                .build();
        return storageService.<IpPrefix, Set<Route>>consistentMapBuilder()
                .withName("onos-routes-" + id.name())
                .withRelaxedReadConsistency()
                .withSerializer(Serializer.using(routeTableSerializer))
                .build();
    }

    @Override
    public RouteTableId id() {
        return id;
    }

    @Override
    public void shutdown() {
        routes.removeListener(listener);
    }

    @Override
    public void destroy() {
        shutdown();
        routes.destroy();
    }

    @Override
    public void update(Route route) {
        routes.compute(route.prefix(), (prefix, set) -> {
            if (set == null) {
                set = new HashSet<>();
            }
            set.add(route);
            return set;
        });
    }

    @Override
    public void remove(Route route) {
        routes.compute(route.prefix(), (prefix, set) -> {
            if (set != null) {
                set.remove(route);
                if (set.isEmpty()) {
                    return null;
                }
                return set;
            }
            return null;
        });
    }

    @Override
    public Collection<RouteSet> getRoutes() {
        return routes.entrySet().stream()
                .map(e -> new RouteSet(id, e.getKey(), e.getValue().value()))
                .collect(Collectors.toSet());
    }

    @Override
    public RouteSet getRoutes(IpPrefix prefix) {
        Versioned<Set<Route>> routeSet = routes.get(prefix);

        if (routeSet != null) {
            return new RouteSet(id, prefix, routeSet.value());
        }
        return null;
    }

    @Override
    public Collection<Route> getRoutesForNextHop(IpAddress nextHop) {
        // TODO index
        return routes.values().stream()
                .flatMap(v -> v.value().stream())
                .filter(r -> r.nextHop().equals(nextHop))
                .collect(Collectors.toSet());
    }

    private class RouteTableListener
            implements MapEventListener<IpPrefix, Set<Route>> {

        private InternalRouteEvent createRouteEvent(
                InternalRouteEvent.Type type, MapEvent<IpPrefix, Set<Route>> event) {
            Set<Route> currentRoutes =
                    (event.newValue() == null) ? Collections.emptySet() : event.newValue().value();
            return new InternalRouteEvent(type, new RouteSet(id, event.key(), currentRoutes));
        }

        @Override
        public void event(MapEvent<IpPrefix, Set<Route>> event) {
            InternalRouteEvent ire = null;
            switch (event.type()) {
            case INSERT:
                ire = createRouteEvent(InternalRouteEvent.Type.ROUTE_ADDED, event);
                break;
            case UPDATE:
                if (event.newValue().value().size() > event.oldValue().value().size()) {
                    ire = createRouteEvent(InternalRouteEvent.Type.ROUTE_ADDED, event);
                } else {
                    ire = createRouteEvent(InternalRouteEvent.Type.ROUTE_REMOVED, event);
                }
                break;
            case REMOVE:
                ire = createRouteEvent(InternalRouteEvent.Type.ROUTE_REMOVED, event);
                break;
            default:
                break;
            }
            if (ire != null) {
                delegate.notify(ire);
            }
        }
    }
}
