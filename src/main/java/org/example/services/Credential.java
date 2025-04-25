package org.example.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;
import org.example.db.DbQueryHelper;
import org.example.utils.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Credential
{

    private static final Logger logger = LoggerFactory.getLogger(Credential.class);

    private final DbQueryHelper dbQueryHelper;

    public Credential(SqlClient sqlClient)
    {
        this.dbQueryHelper = new DbQueryHelper(sqlClient);
    }

    public void createCredential(RoutingContext ctx)
    {
        var body = parseAndValidateBody(ctx, true);
        if (body == null) return;

        dbQueryHelper.insert("credentials", body)
                .onSuccess(res -> ApiResponse.success(ctx, null, "Credential created successfully", 201))
                .onFailure(err -> {
                    logger.error("Insert failed: {}", err.getMessage(), err);
                    ApiResponse.error(ctx, "Database error: " + err.getMessage(), 400);
                });
    }

    public void getCredential(RoutingContext ctx)
    {
        var idParam = ctx.pathParam("id");
        if (isValidId(idParam, ctx)) return;

        var id = Integer.parseInt(idParam);

        dbQueryHelper.fetchOne("credentials", "id", id)
                .compose(credential -> credential == null
                        ? Future.failedFuture("Credential not found")
                        : Future.succeededFuture(credential))
                .onSuccess(credential -> ApiResponse.success(ctx, credential, "Credential retrieved", 200))
                .onFailure(err -> ApiResponse.error(ctx, err.getMessage(), 404));
    }

    public void updateCredential(RoutingContext ctx)
    {
        var idParam = ctx.pathParam("id");
        if (isValidId(idParam, ctx)) return;

        var body = parseAndValidateBody(ctx, false);
        if (body == null) return;

        var id = Integer.parseInt(idParam);

        dbQueryHelper.update("credentials", "id", id, body)
                .onSuccess(res -> ApiResponse.success(ctx, null, "Credential updated", 200))
                .onFailure(err -> ApiResponse.error(ctx, "Update failed: " + err.getMessage(), 400));
    }

    public void deleteCredential(RoutingContext ctx)
    {
        var idParam = ctx.pathParam("id");
        if (isValidId(idParam, ctx)) return;

        var id = Integer.parseInt(idParam);

        dbQueryHelper.delete("credentials", "id", id)
                .onSuccess(res -> ApiResponse.success(ctx, null, "Credential deleted", 200))
                .onFailure(err -> ApiResponse.error(ctx, "Delete failed: " + err.getMessage(), 404));
    }

    public void allCredential(RoutingContext ctx)
    {
        dbQueryHelper.fetchAll("credentials")
                .map(JsonArray::new)
                .onSuccess(credentials -> ApiResponse.success(ctx, credentials, "All credentials", 200))
                .onFailure(err -> ApiResponse.error(ctx, "Fetch failed: " + err.getMessage(), 500));
    }

    private boolean isValidId(String idParam, RoutingContext ctx)
    {
        if (idParam == null || idParam.trim().isEmpty()) {
            ApiResponse.error(ctx, "ID cannot be empty", 400);
            return true;
        }

        try {
            Integer.parseInt(idParam);
            return false;
        } catch (NumberFormatException e) {
            ApiResponse.error(ctx, "Invalid ID format", 400);
            return true;
        }
    }

    private JsonObject parseAndValidateBody(RoutingContext ctx, boolean isCreate)
    {

        var body = ctx.body().asJsonObject();

        if (body == null) {
            ApiResponse.error(ctx, "Request body is empty", 400);
            return null;
        }

        if (isCreate && (!body.containsKey("name") || !body.containsKey("attributes") || !body.containsKey("type"))) {
            ApiResponse.error(ctx, "Missing fields: name, attributes, or type", 400);
            return null;
        }

        if (body.containsKey("type") && !"SSH".equals(body.getString("type"))) {
            ApiResponse.error(ctx, "Unsupported credential type: " + body.getString("type"), 400);
            return null;
        }

        return body;

    }
}
