package org.example.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.DecodeException;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;
import org.example.db.DbQueryHelper;
import org.example.utils.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        JsonObject body = parseRequestBody(ctx);
        if (body == null)
        {
            return;
        }

        try
        {
            validateDiscoveryData(body, true);
            String name = body.getString("name");
            String ip = body.getString("ip");
            JsonArray result = body.getJsonArray("result", new JsonArray());

            JsonObject data = new JsonObject()
                    .put("name", name)
                    .put("ip", ip)
                    .put("result", result);

            logger.info("Creating discovery with data: {}", data.encode());

            dbQueryHelper.insert("discoveries", data)
                    .onSuccess(results ->
                    {
                        ApiResponse.success(ctx, null, "Discovery created successfully", 201);
                    })
                    .onFailure(err ->
                    {
                        logger.error("Database insert failed: {}", err.getMessage());
                        ApiResponse.error(ctx, err.getMessage(), 400);
                    });
        }
        catch (IllegalArgumentException e)
        {
            logger.warn("Validation failed: {}", e.getMessage());
            ApiResponse.error(ctx, e.getMessage(), 400);
        }
    }

    public void getDiscovery(RoutingContext ctx)
    {
        logger.info("Handling GET /discoveries/:id");

        try
        {
            String idParam = ctx.pathParam("id");
            if (idParam == null || idParam.trim().isEmpty())
            {
                logger.warn("Discovery ID is empty");
                ApiResponse.error(ctx, "Discovery ID cannot be empty", 400);
                return;
            }

            try
            {
                Integer id = Integer.parseInt(idParam);
                dbQueryHelper.fetchOne("discoveries", "id", id)
                        .onSuccess(credential ->
                        {
                            if (credential == null)
                            {
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
            catch (NumberFormatException e)
            {
                logger.warn("Invalid discovery ID format: {}", idParam);
                ApiResponse.error(ctx, "Invalid discovery ID format", 400);
            }
        }
        catch (IllegalArgumentException e)
        {
            logger.warn("Validation failed: {}", e.getMessage());
            ApiResponse.error(ctx, e.getMessage(), 400);
        }
    }

    public void updateDiscovery(RoutingContext ctx)
    {
        logger.info("Handling PUT /discoveries/:id");

        JsonObject body = parseRequestBody(ctx);
        if (body == null)
        {
            return;
        }

        try
        {
            validateDiscoveryData(body, false);
            String idParam = ctx.pathParam("id");

            if (idParam == null || idParam.trim().isEmpty())
            {
                logger.warn("Discovery ID is empty");
                ApiResponse.error(ctx, "Discovery ID cannot be empty", 400);
                return;
            }

            Integer id;
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
        catch (IllegalArgumentException e)
        {
            logger.warn("Validation failed: {}", e.getMessage());
            ApiResponse.error(ctx, e.getMessage(), 400);
        }
    }

    public void deleteDiscovery(RoutingContext ctx)
    {
        logger.info("Handling DELETE /discoveries/:id");

        try
        {
            String idParam = ctx.pathParam("id");

            if (idParam == null || idParam.trim().isEmpty())
            {
                logger.warn("Discovery ID is empty");
                ApiResponse.error(ctx, "Discovery ID cannot be empty", 400);
                return;
            }

            Integer id;
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

            dbQueryHelper.delete("discoveries", "id", id)
                    .onSuccess(v -> ApiResponse.success(ctx, null, "Discovery deleted successfully", 200))
                    .onFailure(err ->
                    {
                        logger.error("Failed to delete discovery: {}", err.getMessage());
                        ApiResponse.error(ctx, err.getMessage(), 404);
                    });
        }
        catch (IllegalArgumentException e)
        {
            logger.warn("Validation failed: {}", e.getMessage());
            ApiResponse.error(ctx, e.getMessage(), 400);
        }
    }

    public void allDiscovery(RoutingContext ctx)
    {
        logger.info("Handling GET /discoveries/");

        try
        {
            logger.info("Fetching all discoveries");

            dbQueryHelper.fetchAll("discoveries")
                    .compose(discoveries ->
                    {
                        if (discoveries == null)
                        {
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
        catch (IllegalArgumentException e)
        {
            logger.warn("Validation failed: {}", e.getMessage());
            ApiResponse.error(ctx, e.getMessage(), 400);
        }
    }

    private JsonObject parseRequestBody(RoutingContext ctx)
    {
        try
        {
            JsonObject body = ctx.body().asJsonObject();
            logger.debug("Request body: {}", body != null ? body.encode() : "null");

            if (body == null)
            {
                ApiResponse.error(ctx, "Request body is empty", 400);
                return null;
            }

            return body;
        }
        catch (DecodeException e)
        {
            logger.error("JSON parsing error: {}", e.getMessage());
            ApiResponse.error(ctx, "Malformed JSON request", 400);
            return null;
        }
    }

    private void validateDiscoveryData(JsonObject data, boolean isCreate)
    {
        if (data == null)
        {
            throw new IllegalArgumentException("Request body is empty");
        }
        if (isCreate)
        {
            if (!data.containsKey("name") || !data.containsKey("ip"))
            {
                throw new IllegalArgumentException("Missing required fields: name or ip");
            }
            String name = data.getString("name");
            if (name == null || name.trim().isEmpty())
            {
                throw new IllegalArgumentException("Discovery name cannot be empty");
            }
        }
        if (data.containsKey("ip"))
        {
            String ip = data.getString("ip");
            if (ip == null || ip.trim().isEmpty())
            {
                throw new IllegalArgumentException("IP cannot be empty");
            }
        }
        if (data.containsKey("result"))
        {
            JsonArray result = data.getJsonArray("result");
            if (result == null)
            {
                throw new IllegalArgumentException("Result must be a valid JSON array");
            }
        }
        if (!isCreate && !data.containsKey("name") && !data.containsKey("ip") && !data.containsKey("result"))
        {
            throw new IllegalArgumentException("No valid updatable fields provided");
        }
    }
}
