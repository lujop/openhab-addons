/**
 * Copyright (c) 2020-2020 Contributors to the openHAB project
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
package org.openhab.binding.dbquery.internal.domain;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.lessThan;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

/**
 *
 * @author Joan Pujol - Initial contribution
 */
class QueryResultJSONEncoderTest {
    public static final double TOLERANCE = 0.001d;
    private DBQueryJSONEncoder instance = new DBQueryJSONEncoder();
    private Gson gson = new Gson();
    private JsonParser jsonParser = new JsonParser();

    @Test
    public void given_query_result_is_serialized_to_json() {
        String json = instance.encode(givenQueryResultWithResults());

        assertThat(jsonParser.parse(json), notNullValue());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void given_query_result_its_content_is_correctly_serialized_to_json() {
        String json = instance.encode(givenQueryResultWithResults());

        Map<String, Object> map = gson.fromJson(json, Map.class);
        assertThat(map, Matchers.hasEntry("correct", Boolean.TRUE));
        assertThat(map, Matchers.hasKey("data"));
        List<Map> data = (List<Map>) map.get("data");
        assertThat(data, Matchers.hasSize(2));
        Map firstRow = data.get(0);

        assertReadGivenValuesDecodedFromJson(firstRow);
    }

    private void assertReadGivenValuesDecodedFromJson(Map firstRow) {
        assertThat(firstRow.get("strValue"), is("an string"));

        Object doubleValue = firstRow.get("doubleValue");
        assertThat(doubleValue, instanceOf(Number.class));
        assertThat(((Number) doubleValue).doubleValue(), closeTo(2.3d, TOLERANCE));

        Object intValue = firstRow.get("intValue");
        assertThat(intValue, instanceOf(Number.class));
        assertThat(((Number) intValue).intValue(), is(3));

        Object longValue = firstRow.get("longValue");
        assertThat(longValue, instanceOf(Number.class));
        assertThat(((Number) longValue).longValue(), is(Long.MAX_VALUE));

        Object date = firstRow.get("date");
        assertThat(date, instanceOf(String.class));
        var parsedDate = Instant.from(DateTimeFormatter.ISO_INSTANT.parse((String) date));
        assertThat(Duration.between(parsedDate, Instant.now()).getSeconds(), lessThan(10L));

        Object instant = firstRow.get("instant");
        assertThat(instant, instanceOf(String.class));
        var parsedInstant = Instant.from(DateTimeFormatter.ISO_INSTANT.parse((String) instant));
        assertThat(Duration.between(parsedInstant, Instant.now()).getSeconds(), lessThan(10L));

        assertThat(firstRow.get("booleanValue"), is(Boolean.TRUE));
        assertThat(firstRow.get("object"), is("an object"));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void given_query_result_with_incorrect_result_its_content_is_correctly_serialized_to_json() {
        String json = instance.encode(QueryResult.ofIncorrectResult("Incorrect"));

        Map<String, Object> map = gson.fromJson(json, Map.class);
        assertThat(map, Matchers.hasEntry("correct", Boolean.FALSE));
        assertThat(map.get("errorMessage"), is("Incorrect"));
    }

    @Test
    public void given_query_parameters_are_correctly_serialized_to_json() {
        QueryParameters queryParameters = new QueryParameters(givenRowValues());

        String json = instance.encode(queryParameters);

        Map<String, Object> map = gson.fromJson(json, Map.class);
        assertReadGivenValuesDecodedFromJson(map);
    }

    private QueryResult givenQueryResultWithResults() {
        return QueryResult.of(new ResultRow(givenRowValues()), new ResultRow(givenRowValues()));
    }

    @NotNull
    private Map<String, @Nullable Object> givenRowValues() {
        Map<String, @Nullable Object> values = new HashMap<>();
        values.put("strValue", "an string");
        values.put("doubleValue", 2.3d);
        values.put("intValue", 3);
        values.put("longValue", Long.MAX_VALUE);
        values.put("date", new Date());
        values.put("instant", Instant.now());
        values.put("booleanValue", Boolean.TRUE);
        values.put("object", new Object() {
            @Override
            public String toString() {
                return "an object";
            }
        });
        return values;
    }
}
