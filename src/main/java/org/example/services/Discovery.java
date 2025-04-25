package org.example.services;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;
import org.example.db.DbQueryHelper;
import org.example.utils.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Discovery
{

    private static final Logger logger = LoggerFactory.getLogger(Discovery.class);

    private final DbQueryHelper dbQueryHelper;

    public Discovery(SqlClient sqlClient)
    {
        this.dbQueryHelper = new DbQueryHelper(sqlClient);
    }

    public void createDiscovery(RoutingContext ctx)
    {
        logger.info("Handling POST /discoveries");

        var body = parseAndValidateBody(ctx);

        if (body == null)
        {
            return;
        }

        var credentialIDs = body.getJsonArray("credential_ids", new JsonArray());

        validateCredentialIDs(credentialIDs)
                .onSuccess(v -> {


                    logger.info("Creating discovery with data: {}", body.encode());

                    dbQueryHelper.insert("discoveries", body)
                            .onSuccess(results ->
                            {
                                ApiResponse.success(ctx, null, "Discovery created successfully", 201);
                            })
                            .onFailure(err ->
                            {
                                logger.error("Database insert failed: {}", err.getMessage());
                                ApiResponse.error(ctx, err.getMessage(), 400);
                            });
                })
                .onFailure(err -> {
                    // If any credential ID is invalid, return a failed response
                    logger.error("Validation failed: {}", err.getMessage());
                    ApiResponse.error(ctx, err.getMessage(), 400);
                });
    }

    public void getDiscovery(RoutingContext ctx)
    {
        logger.info("Handling GET /discoveries/:id");

        var idParam = ctx.pathParam("id");

        if (idParam == null || idParam.trim().isEmpty())
        {
            logger.warn("Discovery ID is empty");
            ApiResponse.error(ctx, "Discovery ID cannot be empty", 400);
            return;
        }

        var id = Integer.parseInt(idParam);

        dbQueryHelper.fetchOne("discoveries", "id", id)
                .onSuccess(credential ->
                {
                    if (credential == null) {
                        logger.warn("Discovery not found: {}", id);
                        ApiResponse.error(ctx, "Discovery not found", 404);
                        return;
                    }
                    ApiResponse.success(ctx, credential, "Discovery retrieved successfully", 200);
                })
                .onFailure(err ->
                {
                    logger.error("Failed to fetch discovery: {}", err.getMessage());
                    ApiResponse.error(ctx, err.getMessage(), 500);
                });
    }

    public void updateDiscovery(RoutingContext ctx)
    {
        logger.info("Handling PUT /discoveries/:id");

        var body = parseAndValidateBody(ctx);

        if (body == null) {
            return;
        }


        var idParam = ctx.pathParam("id");

        if (idParam == null || idParam.trim().isEmpty()) {
            logger.warn("Discovery ID is empty");
            ApiResponse.error(ctx, "Discovery ID cannot be empty", 400);
            return;
        }

        int id;
        try
        {
            id = Integer.parseInt(idParam);
        }
        catch (NumberFormatException e)
        {
            logger.warn("Invalid discovery ID format: {}", idParam);
            ApiResponse.error(ctx, "Invalid discovery ID format", 400);
            return;
        }

        JsonObject data = new JsonObject();
        if (body.containsKey("name"))
        {
            data.put("name", body.getString("name"));
        }
        if (body.containsKey("ip"))
        {
            data.put("ip", body.getString("ip"));
        }

        if (body.containsKey("credentialIDs"))
        {
            data.put("credentialIDs", body.getJsonArray("credentialIDs"));
        }

        if (body.containsKey("result"))
        {
            data.put("result", body.getJsonArray("result"));
        }

        logger.info("Updating discovery with data: {}", data.encode());

        dbQueryHelper.update("discoveries", "id", id, data)
                .onSuccess(v -> ApiResponse.success(ctx, null, "Discovery updated successfully", 200))
                .onFailure(err ->
                {
                    logger.error("Failed to update discovery: {}", err.getMessage());
                    ApiResponse.error(ctx, err.getMessage(), 400);
                });

    }

    public void deleteDiscovery(RoutingContext ctx)
    {
        logger.info("Handling DELETE /discoveries/:id");


        var idParam = ctx.pathParam("id");

        if (idParam == null || idParam.trim().isEmpty())
        {
            logger.warn("Discovery ID is empty");
            ApiResponse.error(ctx, "Discovery ID cannot be empty", 400);
            return;
        }

        int id;
        try {
            id = Integer.parseInt(idParam);
        } catch (NumberFormatException e) {
            logger.warn("Invalid discovery ID format: {}", idParam);
            ApiResponse.error(ctx, "Invalid discovery ID format", 400);
            return;
        }

        dbQueryHelper.delete("discoveries", "id", id)
                .onSuccess(v -> ApiResponse.success(ctx, null, "Discovery deleted successfully", 200))
                .onFailure(err ->
                {
                    logger.error("Failed to delete discovery: {}", err.getMessage());
                    ApiResponse.error(ctx, err.getMessage(), 404);
                });
    }

    public void allDiscovery(RoutingContext ctx)
    {
        logger.info("Handling GET /discoveries/");


        logger.info("Fetching all discoveries");

        dbQueryHelper.fetchAll("discoveries")
                .compose(discoveries ->
                {
                    if (discoveries == null) {
                        return Future.succeededFuture(new JsonArray());
                    }
                    return Future.succeededFuture(discoveries);
                })
                .onSuccess(discoveries -> ApiResponse.success(ctx, discoveries, "discoveries retrieved successfully", 200))
                .onFailure(err ->
                {
                    logger.error("Failed to fetch discoveries: {}", err.getMessage());
                    ApiResponse.error(ctx, err.getMessage(), 404);
                });
    }


    private Future<Void> validateCredentialIDs(JsonArray credentialIDs) {
        if (credentialIDs.isEmpty()) {
            return Future.succeededFuture();
        }

        var checks = new ArrayList<Future>();
        for (int i = 0; i < credentialIDs.size(); i++) {
            int id = credentialIDs.getInteger(i);
            checks.add(dbQueryHelper.fetchOne("credentials", "id", id));
        }

        return CompositeFuture.all(checks)
                .compose(cf -> {
                    // Iterate through the result of each check
                    for (int i = 0; i < cf.size(); i++) {
                        JsonObject result = (JsonObject) cf.resultAt(i);
                        if (result == null) {
                            return Future.failedFuture("Credential ID " + credentialIDs.getInteger(i) + " does not exist.");
                        }
                    }
                    return Future.succeededFuture();
                });
    }

    private JsonObject parseAndValidateBody(RoutingContext ctx)
    {
        var body = ctx.body().asJsonObject();

        logger.debug("Request body: {}", body != null ? body.encode() : "null");

        if (body == null) {
            ApiResponse.error(ctx, "Request body is empty", 400);
            return null;
        }


        if (body.containsKey("credentialIDs")) {
            var credentialIDs = body.getJsonArray("credentialIDs");

            if (credentialIDs == null) {
                ApiResponse.error(ctx, "credentialIDs must be a valid JSON array", 400);
                return null;
            }

        }

        return body;
    }
}

