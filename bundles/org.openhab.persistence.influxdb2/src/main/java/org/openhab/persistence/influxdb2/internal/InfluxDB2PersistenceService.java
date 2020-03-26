/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.persistence.influxdb2.internal;

import static org.openhab.persistence.influxdb2.internal.InfluxDBConstants.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.openhab.core.config.core.ConfigurableService;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.persistence.*;
import org.openhab.core.persistence.strategy.PersistenceStrategy;
import org.openhab.core.types.State;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.influxdb.client.write.Point;
import com.influxdb.query.FluxTable;

/**
 * This is the implementation of the InfluxDB 2 {@link PersistenceService}. It persists item values
 * using the <a href="http://influxdb.org">InfluxDB 2.0 or superior</a> time series database. The states (
 * {@link State}) of an {@link Item} are persisted by default in a time series with names equal to the name of
 * the item.
 *
 * @author Joan Pujol Espinar - Initial contribution, rewrite of InfluxDB 1.X addon based on
 *         previous work from Theo Weiss and Dominik Vorreiter
 */
@NonNullByDefault
@Component(service = { PersistenceService.class,
        QueryablePersistenceService.class }, configurationPid = "org.openhab.influxdb2", property = {
                Constants.SERVICE_PID + "=org.openhab.influxdb2",
                ConfigurableService.SERVICE_PROPERTY_DESCRIPTION_URI + "=persistence:influxdb2",
                ConfigurableService.SERVICE_PROPERTY_LABEL + "=InfluxDB 2 persistence layer",
                ConfigurableService.SERVICE_PROPERTY_CATEGORY + "=persistence" })
public class InfluxDB2PersistenceService implements QueryablePersistenceService {
    static final String SERVICE_NAME = "influxdb2";

    private final Logger logger = LoggerFactory.getLogger(InfluxDB2PersistenceService.class);

    // External dependencies
    @Nullable
    private ItemRegistry itemRegistry;
    @Nullable
    private MetadataRegistry metadataRegistry;

    // Internal dependencies/state
    private InfluxDBConfiguration configuration = InfluxDBConfiguration.NO_CONFIGURATION;
    @NonNullByDefault({}) // Relax rules because can only be null if component is not active
    private ItemToStorePointCreator itemToStorePointCreator;
    @NonNullByDefault({}) // Relax rules because can only be null if component is not active
    private InfluxDBRepository influxDBRepository;

    @Activate
    public void activate(final @Nullable Map<String, @Nullable Object> config) {
        if (logger.isDebugEnabled())
            logger.debug("InfluxDB persistence service is being activated");

        if (loadConfiguration(config)) {
            itemToStorePointCreator = new ItemToStorePointCreator(configuration, metadataRegistry);
            influxDBRepository = createInfluxDBRepository();
            influxDBRepository.connect();
        } else {
            logger.error("Cannot load configuration, persistence service won't work");
        }

        logger.debug("InfluxDB persistence service is now activated");
    }

    @NotNull
    // Visible for testing
    protected InfluxDBRepository createInfluxDBRepository() {
        return new InfluxDBRepository(configuration);
    }

    @Deactivate
    public void deactivate() {
        logger.debug("InfluxDB persistence service deactivated");
        if (influxDBRepository != null) {
            influxDBRepository.disconnect();
            influxDBRepository = null;
        }
        if (itemToStorePointCreator != null) {
            itemToStorePointCreator = null;
        }
    }

    @Modified
    protected void modified(@Nullable Map<String, @Nullable Object> config) {
        if (config != null) {
            logger.debug("Config has been modified will deactivate/activate with new config");

            deactivate();
            activate(config);
        } else {
            logger.warn("Null configuration, ignoring");
        }
    }

    private boolean loadConfiguration(@Nullable Map<String, @Nullable Object> config) {
        boolean configurationIsValid;
        if (config != null) {
            configuration = new InfluxDBConfiguration(config);
            configurationIsValid = configuration.isValid();
            if (configurationIsValid)
                logger.debug("Loaded configuration {}", config);
            else
                logger.warn("Some configuration properties are not valid {}", config);
        } else {
            configuration = InfluxDBConfiguration.NO_CONFIGURATION;
            configurationIsValid = false;
            logger.warn("Ignoring configuration because it's null");
        }
        return configurationIsValid;
    }

    @Override
    public String getId() {
        return SERVICE_NAME;
    }

    @Override
    public String getLabel(@Nullable Locale locale) {
        return "InfluxDB2 persistence layer";
    }

    @Override
    public Set<PersistenceItemInfo> getItemInfo() {
        if (influxDBRepository.isConnected()) {
            return influxDBRepository.getStoredItemsCount().entrySet().stream()
                    .map(entry -> new InfluxDBPersistentItemInfo(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toSet());
        } else {
            logger.warn("InfluxDB is not yet connected");
            return Collections.emptySet();
        }
    }

    @Override
    public void store(Item item) {
        store(item, item.getName());
    }

    @Override
    public void store(Item item, @Nullable String alias) {
        if (influxDBRepository.isConnected()) {
            Point point = itemToStorePointCreator.convert(item, alias);
            if (point != null) {
                logger.trace("Storing item {} in InfluxDB point {}", item, point);
                influxDBRepository.write(point);
            } else {
                logger.trace("Ignoring item {} as is cannot be converted to a InfluxDB point", item);
            }
        } else {
            logger.warn("InfluxDB is not yet connected");
        }
    }

    @Override
    public Iterable<HistoricItem> query(FilterCriteria filter) {
        logger.debug("Got a query for historic points!");

        if (influxDBRepository.isConnected()) {
            logger.trace(
                    "Filter: itemname: {}, ordering: {}, state: {},  operator: {}, getBeginDate: {}, getEndDate: {}, getPageSize: {}, getPageNumber: {}",
                    filter.getItemName(), filter.getOrdering().toString(), filter.getState(), filter.getOperator(),
                    filter.getBeginDateZoned(), filter.getEndDateZoned(), filter.getPageSize(), filter.getPageNumber());

            String query = new FilterCriteriaQueryCreator().createQuery(filter, configuration.getBucket());
            return influxDBRepository.query(query).stream().flatMap(this::mapRawResultToHistoric)
                    .collect(Collectors.toList());
        } else {
            logger.warn("InfluxDB is not yet connected");
            return Collections.emptyList();
        }
    }

    private Stream<InfluxDBHistoricItem> mapRawResultToHistoric(FluxTable rawRow) {
        return rawRow.getRecords().stream().map(r -> {
            String itemName = (String) r.getValueByKey(TAG_ITEM_NAME);
            State state = InfluxDBStateConvertUtils.objectToState(r.getValueByKey(COLUMN_VALUE_NAME), itemName,
                    itemRegistry);
            Number time = (Number) r.getValueByKey(COLUMN_TIME_NAME);
            InfluxDBHistoricItem item = new InfluxDBHistoricItem(itemName, state, new Date(time.longValue()));
            return item;
        });
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
        if (logger.isTraceEnabled())
            logger.trace("ItemRegistry has been set");
    }

    protected void unsetItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = null;
        if (logger.isTraceEnabled())
            logger.trace("ItemRegistry has been unset");
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setMetadataRegistry(MetadataRegistry metadataRegistry) {
        this.metadataRegistry = metadataRegistry;
        if (logger.isTraceEnabled())
            logger.trace("MetadataRegistry has been set");
    }

    protected void unsetMetadataRegistry(MetadataRegistry metadataRegistry) {
        this.metadataRegistry = null;
        if (logger.isTraceEnabled())
            logger.trace("MetadataRegistry has been unset");
    }

    @Override
    public List<PersistenceStrategy> getDefaultStrategies() {
        return List.of(PersistenceStrategy.Globals.RESTORE, PersistenceStrategy.Globals.CHANGE);
    }

}
