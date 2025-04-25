package org.example.routes;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.sqlclient.SqlClient;
import org.example.services.User;
import org.example.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserRoutes
{
    private final User userService;

    public UserRoutes(SqlClient sqlClient, JwtUtil jwtUtil)
    {
        this.userService = new User(sqlClient, jwtUtil);
    }

    public Router configureRoutes(Vertx vertx)
    {
        var router = Router.router(vertx);

        router.post("/register").handler(userService::registerUser);

        router.post("/login").handler(userService::authenticateUser);

        return router;
    }
}
