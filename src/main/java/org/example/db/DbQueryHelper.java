package org.example.db;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DbQueryHelper
{

    private static final Logger logger = LoggerFactory.getLogger(DbQueryHelper.class);

    private final SqlClient client;

    public DbQueryHelper(SqlClient client)
    {
        this.client = client;
    }

    public Future<RowSet<Row>> insert(String table, JsonObject data)
    {
        var fieldNames = data.stream()
                .map(Map.Entry::getKey)
                .toList();

        var columns = String.join(", ", fieldNames);

        var placeholders = IntStream.rangeClosed(1, fieldNames.size())
                .mapToObj(i -> "$" + i)
                .collect(Collectors.joining(", "));

        var query = String.format("INSERT INTO %s (%s) VALUES (%s)", table, columns, placeholders);

        var values = Tuple.tuple();

        for (var field : fieldNames)
        {
            Object value = data.getValue(field);

            if (value instanceof JsonArray || value instanceof JsonObject)
            {
                values.addValue(value.toString());
            }
            else
            {
                values.addValue(value);
            }
        }

        return client
                .preparedQuery(query)
                .execute(values)
                .mapEmpty();
    }

    public Future<Void> update(String table, String idColumn, Object idValue, JsonObject data)
    {
        var fieldNames = data.stream().map(Map.Entry::getKey).toList();

        var setClause = IntStream.rangeClosed(1, fieldNames.size())
                .mapToObj(i -> fieldNames.get(i - 1) + " = $" + i)
                .collect(Collectors.joining(", "));

        var query = String.format("UPDATE %s SET %s WHERE %s = $%d", table, setClause, idColumn, fieldNames.size() + 1);

        var values = Tuple.tuple();

        for (var field : fieldNames)
        {
            values.addValue(data.getValue(field));
        }

        values.addValue(idValue);

        return client
                .preparedQuery(query)
                .execute(values)
                .mapEmpty();
    }

    public Future<Void> delete(String table, String idColumn, Object idValue)
    {
        var query = String.format("DELETE FROM %s WHERE %s = $1", table, idColumn);

        logger.info("Executing DELETE query: {}", query);

        return client
                .preparedQuery(query)
                .execute(Tuple.of(idValue))
                .mapEmpty();
    }

    public Future<JsonObject> fetchOne(String table, String idColumn, Object idValue)
    {
        var query = String.format("SELECT * FROM %s WHERE %s = $1", table, idColumn);

        logger.info("Executing SELECT query: {}", query);

        return client
                .preparedQuery(query)
                .execute(Tuple.of(idValue))
                .map(rows ->
                {
                    var row = rows.iterator().next();
                    return row.toJson();
                });
    }

    public Future<List<JsonObject>> fetchAll(String table)
    {
        var query = String.format("SELECT * FROM %s", table);

        logger.info("Executing SELECT ALL query: {}", query);

        return client
                .query(query)
                .execute()
                .map(rows ->
                {
                    var result = new ArrayList<JsonObject>();
                    for (var row : rows)
                    {
                        result.add(row.toJson());
                    }
                    return result;
                });
    }
}
