/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.openhab.persistence.influxdb2.internal.LineProtocolExtractUtils.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openhab.core.items.Metadata;
import org.openhab.core.items.MetadataKey;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.library.items.NumberItem;

import com.influxdb.client.write.Point;

/**
 * @author Joan Pujol Espinar - Initial contribution
 */
@SuppressWarnings("null") // In case of any NPE it will cause test fail that it's the expected result
public class ItemToStorePointCreatorTest {
    @Mock
    private InfluxDBConfiguration influxDBConfiguration;
    @Mock
    private MetadataRegistry metadataRegistry;
    private ItemToStorePointCreator instance;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        when(influxDBConfiguration.isAddCategoryTag()).thenReturn(false);
        when(influxDBConfiguration.isAddLabelTag()).thenReturn(false);
        when(influxDBConfiguration.isAddTypeTag()).thenReturn(false);
        when(influxDBConfiguration.isReplaceUnderscore()).thenReturn(false);

        instance = new ItemToStorePointCreator(influxDBConfiguration, metadataRegistry);
    }

    @After
    public void after() {
        instance = null;
        influxDBConfiguration = null;
        metadataRegistry = null;
    }

    @Test
    public void convertBasicItem() {
        NumberItem item = ItemTestHelper.createNumberItem("myitem", 5);
        Point point = instance.convert(item, null);
        String line = point.toLineProtocol();

        assertThat(itemName(line), equalTo(item.getName()));
        assertThat("Must Store item name", tags(line), hasEntry("item", item.getName()));
        assertThat(value(line), is("5i"));
    }

    @Test
    public void shouldUseAliasAsMeasurementNameIfProvided() {
        NumberItem item = ItemTestHelper.createNumberItem("myitem", 5);
        Point point = instance.convert(item, "aliasName");
        String line = point.toLineProtocol();

        assertThat(itemName(line), is("aliasName"));
    }

    @Test
    public void shouldStoreCategoryTagIfProvidedAndConfigured() {
        NumberItem item = ItemTestHelper.createNumberItem("myitem", 5);
        item.setCategory("categoryValue");

        when(influxDBConfiguration.isAddCategoryTag()).thenReturn(true);
        Point point = instance.convert(item, null);
        String line = point.toLineProtocol();
        assertThat(tags(line), hasEntry(InfluxDBConstants.TAG_CATEGORY_NAME, "categoryValue"));

        when(influxDBConfiguration.isAddCategoryTag()).thenReturn(false);
        point = instance.convert(item, null);
        line = point.toLineProtocol();
        assertThat(tags(line), not(hasKey(InfluxDBConstants.TAG_CATEGORY_NAME)));
    }

    @Test
    public void shouldStoreTypeTagIfProvidedAndConfigured() {
        NumberItem item = ItemTestHelper.createNumberItem("myitem", 5);

        when(influxDBConfiguration.isAddTypeTag()).thenReturn(true);
        Point point = instance.convert(item, null);
        String line = point.toLineProtocol();
        assertThat(tags(line), hasEntry(InfluxDBConstants.TAG_TYPE_NAME, "Number"));

        when(influxDBConfiguration.isAddTypeTag()).thenReturn(false);
        point = instance.convert(item, null);
        line = point.toLineProtocol();
        assertThat(tags(line), not(hasKey(InfluxDBConstants.TAG_TYPE_NAME)));
    }

    @Test
    public void shouldStoreTypeLabelIfProvidedAndConfigured() {
        NumberItem item = ItemTestHelper.createNumberItem("myitem", 5);
        item.setLabel("ItemLabel");

        when(influxDBConfiguration.isAddLabelTag()).thenReturn(true);
        Point point = instance.convert(item, null);
        String line = point.toLineProtocol();
        assertThat(tags(line), hasEntry(InfluxDBConstants.TAG_LABEL_NAME, "ItemLabel"));

        when(influxDBConfiguration.isAddLabelTag()).thenReturn(false);
        point = instance.convert(item, null);
        line = point.toLineProtocol();
        assertThat(tags(line), not(hasKey(InfluxDBConstants.TAG_LABEL_NAME)));
    }

    @Test
    public void shouldStoreMetadataAsTagsIfProvided() {
        NumberItem item = ItemTestHelper.createNumberItem("myitem", 5);
        MetadataKey metadataKey = new MetadataKey(InfluxDB2PersistenceService.SERVICE_NAME, item.getName());

        when(metadataRegistry.get(metadataKey))
                .thenReturn(new Metadata(metadataKey, "", Map.of("key1", "val1", "key2", "val2")));

        Point point = instance.convert(item, null);
        String line = point.toLineProtocol();
        assertThat(tags(line), hasEntry("key1", "val1"));
        assertThat(tags(line), hasEntry("key2", "val2"));
    }

}

/**
 * Simple InfluxDB line protocol parser (not complete, only for test purposes)
 * https://v2.docs.influxdata.com/v2.0/reference/syntax/line-protocol/
 *
 * @author Joan Pujol Espinar - Initial contribution
 */
class LineProtocolExtractUtils {

    public static String itemName(String line) {
        String nameTagsPart = line.split(" ", -1)[0];
        return nameTagsPart.split(",", -1)[0];
    }

    public static Map<String, String> tags(String line) {
        String nameTagsPart = line.split(" ", -1)[0];
        int idxFirstComma = nameTagsPart.indexOf(",");
        if (idxFirstComma > -1) {
            return parseFieldValueList(nameTagsPart.substring(idxFirstComma + 1));
        } else {
            return Collections.emptyMap();
        }
    }

    private static Map<String, String> parseFieldValueList(String fieldValueList) {
        String[] fields = fieldValueList.split(",", -1);
        Map<String, String> result = new HashMap<String, String>();

        for (String field : fields) {
            String[] fieldParts = field.split("=");
            result.put(fieldParts[0], fieldParts[1].replaceAll("\"", ""));
        }
        return result;
    }

    public static Map<String, String> fields(String line) {
        String fieldsPart = line.split(" ", -1)[1];
        return parseFieldValueList(fieldsPart);
    }

    public static String value(String line) {
        return fields(line).get("value");
    }
}