package org.example.utils;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private final JWTAuth jwtAuth;

    private final JWTOptions jwtOptions;

    public JwtUtil(JsonObject config) {

        var jwtConfig = config.getJsonObject("jwt");

        if (jwtConfig == null) {

            logger.error("JWT configuration is missing");

            throw new IllegalArgumentException("JWT configuration is missing");
        }

        var secretKey = jwtConfig.getString("secret");

        var expirationMillis = jwtConfig.getLong("expirationMillis", 3600000L);

        var algorithm = jwtConfig.getString("algorithm", "HS256");

        if (secretKey == null || secretKey.isBlank()) {
            logger.error("JWT secret key is missing or empty");
            throw new IllegalArgumentException("JWT secret key is missing or empty");
        }

        String base64urlEncodedKey = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(secretKey.getBytes(StandardCharsets.UTF_8));

        var jwk = new JsonObject()
                .put("algorithm", algorithm)
                .put("key", base64urlEncodedKey);

        var jwtAuthOptions = new JWTAuthOptions()
                .addJwk(jwk);

        this.jwtAuth = JWTAuth.create(Vertx.currentContext().owner(), jwtAuthOptions);

        this.jwtOptions = new JWTOptions().setExpiresInSeconds(Math.toIntExact(expirationMillis / 1000));
    }

    public String generateToken(String username)
    {

        logger.info("Generating JWT for username: {}", username);

        var claims = new JsonObject().put("sub", username);

        return jwtAuth.generateToken(claims, jwtOptions);
    }

    public JWTAuth getAuthProvider()
    {
        return jwtAuth;
    }
}
