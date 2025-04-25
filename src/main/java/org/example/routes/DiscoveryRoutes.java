package org.example.routes;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
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
        Router router = Router.router(vertx);

        Discovery discoveryService = new Discovery(sqlClient);

        // Create a new discovery
        router.post("/")
                .handler(discoveryService::createDiscovery)
                .failureHandler(ctx -> logger.error("Failed to create discovery: {}", ctx.failure().getMessage()));

        // Get a discovery by ID
        router.get("/:id")
                .handler(discoveryService::getDiscovery)
                .failureHandler(ctx -> logger.error("Failed to get discovery: {}", ctx.failure().getMessage()));

        // Update a discovery by ID
        router.put("/:id")
                .handler(discoveryService::updateDiscovery)
                .failureHandler(ctx -> logger.error("Failed to update discovery: {}", ctx.failure().getMessage()));

        // Delete a discovery by ID
        router.delete("/:id")
                .handler(discoveryService::deleteDiscovery)
                .failureHandler(ctx -> logger.error("Failed to delete discovery: {}", ctx.failure().getMessage()));

        // Get all discoveries
        router.get("/")
                .handler(discoveryService::allDiscovery);

        return router;
    }
}
