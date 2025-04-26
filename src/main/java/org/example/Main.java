package org.example;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.example.ApiServer.HttpServer;
import org.example.db.DatabaseConfig;
import org.example.utils.ConfigLoader;
import org.example.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{

    private static final Logger logger = LoggerFactory.getLogger(Main.class);


    public static void main(String[] args)
    {
        var vertx = Vertx.vertx();

        startServer(vertx)
                .onSuccess(v -> logger.info("HTTP server started successfully"))

                .onFailure(err -> {
                    logger.error("Failed to start server: {}", err.getMessage());
                    vertx.close();
                });
    }

    private static Future<Object> startServer(Vertx vertx) {

        return ConfigLoader.load(vertx)
                .compose(config -> {

                    var pgPool = DatabaseConfig.createPgPool(vertx, config);

                    var jwtUtil = new JwtUtil(config);

                    var server = new HttpServer(pgPool, jwtUtil, config.getInteger("http.port"));

                    // Deploy the HttpServer verticle
                    return vertx.deployVerticle(server)
                            .mapEmpty()

                            .onComplete(ar ->
                            {
                                if (ar.succeeded()) {
                                      Runtime.getRuntime().addShutdownHook(new Thread(() ->
                                      {
                                        logger.info("Shutting down server and closing resources");
                                        pgPool.close();
                                        vertx.close();
                                    }));
                                }
                            });
                });
    }
}