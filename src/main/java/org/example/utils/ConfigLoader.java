package org.example.utils;

import io.vertx.config.ConfigRetriever;

import io.vertx.config.ConfigRetrieverOptions;

import io.vertx.config.ConfigStoreOptions;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;

import io.vertx.core.Future;

public class ConfigLoader {

    public static Future<JsonObject> load(Vertx vertx) {

        var fileStore = new ConfigStoreOptions()

                .setType("file")

                .setFormat("json")

                .setConfig(new JsonObject().put("path", "config.json"));

        var options = new ConfigRetrieverOptions().addStore(fileStore);

        var retriever = ConfigRetriever.create(vertx, options);

        return retriever.getConfig();

    }

}