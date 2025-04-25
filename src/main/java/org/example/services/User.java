package org.example.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.DecodeException;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;

import org.example.db.DbQueryHelper;
import org.example.utils.ApiResponse;
import org.example.utils.JwtUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class User
{

    private static final Logger logger = LoggerFactory.getLogger(User.class);

    private final DbQueryHelper dbQueryHelper;

    private final JwtUtil jwtUtil;

    public User(SqlClient sqlClient, JwtUtil jwtUtil)
    {
        this.dbQueryHelper = new DbQueryHelper(sqlClient);
        this.jwtUtil = jwtUtil;
    }

    public void registerUser(RoutingContext ctx)
    {
        logger.info("Handling POST /register");

        var body = parseRequestBody(ctx);

        if (body == null)
        {
            return;
        }

        try
        {
            validateUserData(body);

            var username = body.getString("username");

            var password = body.getString("password");

            var cleanedData = new JsonObject()
                    .put("username", username)
                    .put("password", hashPassword(password));

            logger.info("Registering user with data: {}", cleanedData.encode());

            dbQueryHelper.insert("users", cleanedData)
                    .onSuccess(v -> ApiResponse.success(ctx, null, "User registered successfully", 201))
                    .onFailure(err ->
                    {
                        logger.error("Database insert failed: {}", err.getMessage());
                        ApiResponse.error(ctx, err.getMessage(), 400);
                    });
        }
        catch (IllegalArgumentException exception)
        {
            logger.warn("Validation failed: {}", exception.getMessage());
            ApiResponse.error(ctx, exception.getMessage(), 400);
        }
    }

    public void authenticateUser(RoutingContext ctx)
    {
        logger.info("Handling POST /login");

        var body = parseRequestBody(ctx);

        if (body == null)
        {
            return;
        }

        try
        {
            validateUserData(body);

            var username = body.getString("username");

            var password = body.getString("password");

            dbQueryHelper.fetchOne("users", "username", username)
                    .compose(user ->
                    {
                        if (user == null)
                        {
                            logger.warn("User not found: {}", username);
                            return Future.failedFuture("User not found");
                        }

                        var storedPassword = user.getString("password");

                        var hashedInputPassword = hashPassword(password);

                        if (storedPassword.equals(hashedInputPassword))
                        {
                            user.remove("password");

                            var token = jwtUtil.generateToken(username);

                            user.put("token", token);

                            return Future.succeededFuture(user);
                        }
                        else
                        {
                            logger.warn("Invalid credentials for user: {}", username);
                            return Future.failedFuture("Invalid credentials");
                        }
                    })

                    .onSuccess(user -> ApiResponse.success(ctx, user, "Login successful", 200))

                    .onFailure(err ->
                    {
                        logger.error("Login failed: {}", err.getMessage());
                        ApiResponse.error(ctx, err.getMessage(), 401);
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
            var body = ctx.body().asJsonObject();

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

    private String hashPassword(String password)
    {
        try
        {
            var digest = MessageDigest.getInstance("SHA-256");

            var hash = digest.digest(password.getBytes());

            return Base64.getEncoder().encodeToString(hash);
        }
        catch (NoSuchAlgorithmException e)
        {
            logger.error("Password hashing failed: {}", e.getMessage());
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    private void validateUserData(JsonObject userData)
    {
        if (userData == null)
        {
            throw new IllegalArgumentException("Request body is empty");
        }

        if (!userData.containsKey("username") || !userData.containsKey("password"))
        {
            throw new IllegalArgumentException("Username and password are required");
        }

        var username = userData.getString("username");

        var password = userData.getString("password");

        if (username == null || username.trim().isEmpty())
        {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        if (password == null || password.trim().isEmpty())
        {
            throw new IllegalArgumentException("Password cannot be empty");
        }
    }
}
