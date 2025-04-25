package org.example.utils;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ApiResponse
{

    public static void success(RoutingContext ctx, Object data, String message, int statusCode)
    {
        var response = new JsonObject()
                .put("status", "success")
                .put("message", message != null ? message : "Operation successful");

        if (data != null)
        {
            response.put("data", data);
        }

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }

    public static void error(RoutingContext ctx, String message, Object errorDetails, int statusCode)
    {
        var response = new JsonObject()
                .put("status", "error")
                .put("message", message != null ? message : "Operation failed");

        if (errorDetails != null)
        {
            response.put("error", errorDetails);
        }

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }

    public static void error(RoutingContext ctx, String message, int statusCode)
    {
        error(ctx, message, null, statusCode);
    }
}
