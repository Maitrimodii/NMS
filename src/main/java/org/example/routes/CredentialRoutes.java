package org.example.routes;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.sqlclient.SqlClient;
import org.example.services.Credential;

public class CredentialRoutes {

    private final Credential credentialsService;

    public CredentialRoutes(SqlClient sqlClient)
    {
        this.credentialsService = new Credential(sqlClient);
    }

    public Router configureRoutes(Vertx vertx)
    {
        var router = Router.router(vertx);

        router.post("/").handler(credentialsService::createCredential);

        router.get("/").handler(credentialsService::allCredential);

        router.get("/:id").handler(credentialsService::getCredential);

        router.put("/:id").handler(credentialsService::updateCredential);

        router.delete("/:id").handler(credentialsService::deleteCredential);


        return router;
    }
}