package org.example.db;

import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.SqlClient;

public class DatabaseConfig
{

    public static SqlClient createPgPool(Vertx vertx, JsonObject config)
    {
        var dbConfig = config.getJsonObject("db");

        if (dbConfig == null)
        {
            throw new IllegalArgumentException("Database configuration is missing");
        }

        var host = dbConfig.getString("host");

        var port = dbConfig.getInteger("port");

        var database = dbConfig.getString("database");

        var user = dbConfig.getString("user");

        var password = dbConfig.getString("password");

        if (host == null || port == null || database == null || user == null || password == null)
        {
            throw new IllegalArgumentException("Missing required database configuration: host, port, database, user, or password");
        }

        var connectOptions = new PgConnectOptions()
                .setPort(port)
                .setHost(host)
                .setDatabase(database)
                .setUser(user)
                .setPassword(password);

        var poolOptions = new PoolOptions()
                .setMaxSize(dbConfig.getInteger("poolSize", 5));

        return PgBuilder
                .client()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .using(vertx)
                .build();
    }
}
