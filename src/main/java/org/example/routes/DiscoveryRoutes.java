package org.example.routes;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.sqlclient.SqlClient;
import org.example.services.Discovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscoveryRoutes
{

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryRoutes.class);

    private final SqlClient sqlClient;

    public DiscoveryRoutes(SqlClient sqlClient)
    {
        this.sqlClient = sqlClient;
    }

    public Router configureRoutes(Vertx vertx)
    {
        var router = Router.router(vertx);

        var discoveryService = new Discovery(sqlClient);

        // Create a new discovery
        router.post("/")
                .handler(discoveryService::createDiscovery);

        // Get a discovery by ID
        router.get("/:id")
                .handler(discoveryService::getDiscovery);

        // Update a discovery by ID
        router.put("/:id")
                .handler(discoveryService::updateDiscovery);

        // Delete a discovery by ID
        router.delete("/:id")
                .handler(discoveryService::deleteDiscovery);

        // Get all discoveries
        router.get("/")
                .handler(discoveryService::allDiscovery);

        return router;
    }
}
