package org.openhab.binding.dbquery.internal.domain;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class DBQueryJSONEncoder {
    private final Gson gson;

    public DBQueryJSONEncoder() {
        gson = new GsonBuilder().registerTypeAdapter(QueryResult.class, new QueryResultGSONSerializer())
                .registerTypeAdapter(ResultRow.class, new ResultRowGSONSerializer())
                       .registerTypeAdapter(QueryParameters.class, new QueryParametersGSONSerializer())
                       .create();
    }

    public String encode(QueryResult queryResult) {
        return gson.toJson(queryResult);
    }

    public String encode(QueryParameters parameters) {
        return gson.toJson(parameters);
    }

    private JsonElement convertValueToJsonPrimitive(Object value) {
        if (value instanceof Number)
            return new JsonPrimitive((Number) value);
        else if (value instanceof Boolean)
            return new JsonPrimitive((Boolean) value);
        else if (value instanceof Character) {
            return new JsonPrimitive((Character) value);
        } else if (value instanceof Date) {
            return new JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(((Date) value).toInstant()));
        } else if (value instanceof Instant) {
            return new JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format((Instant) value));
        } else if (value != null) {
            return new JsonPrimitive(value.toString());
        } else {
            return JsonNull.INSTANCE;
        }
    }

    private class QueryResultGSONSerializer implements JsonSerializer<QueryResult> {
        @Override
        public JsonElement serialize(QueryResult src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("correct", src.isCorrect());
            if (src.getErrorMessage() != null)
                jsonObject.addProperty("errorMessage", src.getErrorMessage());
            jsonObject.add("data", context.serialize(src.getData()));
            return jsonObject;
        }
    }

    private class ResultRowGSONSerializer implements JsonSerializer<ResultRow> {
        @Override
        public JsonElement serialize(ResultRow src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            for (String columnName : src.getColumnNames()) {
                jsonObject.add(columnName, convertValueToJsonPrimitive(src.getValue(columnName)));
            }
            return jsonObject;
        }
    }

    private class QueryParametersGSONSerializer implements JsonSerializer<QueryParameters> {
        @Override
        public JsonElement serialize(QueryParameters src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            for(Map.Entry<String,@Nullable Object> param : src.getAll().entrySet())  {
                jsonObject.add(param.getKey(), convertValueToJsonPrimitive(param.getValue()));
            }
            return jsonObject;
        }
    }
}
