package org.example.Engine;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClientOptions;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscoveryEngine extends AbstractVerticle
{
    private static final String DISCOVERY_ADDRESS = "discovery";

    private static final Pattern IP_RANGE_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.)(\\d+)-(\\d+)");

    private static final int FPING_TIMEOUT_SECONDS = 30;

    private static final int PORT_SCAN_TIMEOUT_MS = 2000;

    private static final int PROCESS_TIMEOUT_SECONDS = 60;

    @Override
    public void start(Promise<Void> startPromise)
    {
        vertx.eventBus().consumer(DISCOVERY_ADDRESS, message ->
        {
            var request = (JsonObject) message.body();

            handleDiscoveryRequest(request)
                    .onSuccess(message::reply)
                    .onFailure(err -> message.reply(errorResponse(err.getMessage())));
        });

        startPromise.complete();
    }

    private Future<JsonObject> handleDiscoveryRequest(JsonObject request)
    {
        var promise = Promise.<JsonObject>promise();

        vertx.executeBlocking(blockingPromise ->
        {
            try
            {
                if (!"Discovery".equals(request.getString("requestType")))
                {
                    blockingPromise.fail("Invalid request type");
                    return;
                }

                var contexts = request.getJsonArray("contexts");

                if (contexts == null || contexts.isEmpty())
                {
                    blockingPromise.fail("No discovery contexts provided");
                    return;
                }

                var context = contexts.getJsonObject(0);

                var ipInput = context.getString("ip");

                var port = context.getInteger("port");

                var credentials = context.getJsonArray("credentials");

                if (ipInput == null || port == null || credentials == null)
                {
                    blockingPromise.fail("Missing required fields");
                    return;
                }

                var ips = expandIpRange(ipInput);

                if (ips.isEmpty())
                {
                    blockingPromise.fail("Invalid IP or range");
                    return;
                }

                var activeIps = performFping(ips);

                if (activeIps.isEmpty())
                {
                    blockingPromise.fail("No active IPs found");
                    return;
                }

                var activeIp = activeIps.get(0);

                if (!scanPortBlocking(activeIp, port))
                {
                    blockingPromise.fail("Port " + port + " is not open on IP: " + activeIp);
                    return;
                }

                var result = spawnDiscoveryProcess(activeIp, port, credentials);

                blockingPromise.complete(result);
            }
            catch (Exception e)
            {
                blockingPromise.fail(e);
            }
        }, res ->
        {
            if (res.succeeded())
            {
                promise.complete((JsonObject) res.result());
            }
            else
            {
                promise.fail(res.cause());
            }
        });

        return promise.future();
    }

    private List<String> expandIpRange(String ipInput)
    {
        var ips = new ArrayList<String>();

        var matcher = IP_RANGE_PATTERN.matcher(ipInput);

        if (matcher.matches())
        {
            var baseIp = matcher.group(1);

            var start = Integer.parseInt(matcher.group(2));

            var end = Integer.parseInt(matcher.group(3));

            if (start <= end && start >= 0 && end <= 255)
            {
                for (var i = start; i <= end; i++)
                {
                    ips.add(baseIp + i);
                }
            }
        }
        else if (isValidIp(ipInput))
        {
            ips.add(ipInput);
        }

        return ips;
    }

    private boolean isValidIp(String ip)
    {
        var ipPattern = "^(\\d{1,3}\\.){3}\\d{1,3}$";

        if (!ip.matches(ipPattern))
        {
            return false;
        }

        return Arrays.stream(ip.split("\\."))
                .allMatch(part ->
                {
                    try
                    {
                        var num = Integer.parseInt(part);

                        return num >= 0 && num <= 255;
                    }
                    catch (NumberFormatException e)
                    {
                        return false;
                    }
                });
    }

    private List<String> performFping(List<String> ips) throws Exception
    {
        var activeIps = new ArrayList<String>();

        if (ips.isEmpty())
        {
            return activeIps;
        }

        var command = new ArrayList<String>();

        command.add("fping");

        command.add("-q");

        command.add("-a");

        command.add("-r");

        command.add("1");

        command.addAll(ips);

        var pb = new ProcessBuilder(command);

        pb.redirectErrorStream(true);

        var process = pb.start();

        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
        {
            String line;

            while ((line = reader.readLine()) != null)
            {
                var ip = line.trim();

                if (!ip.isEmpty())
                {
                    activeIps.add(ip);
                }
            }
        }

        if (!process.waitFor(FPING_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        {
            process.destroy();

            throw new RuntimeException("fping timed out");
        }

        return activeIps;
    }

    private boolean scanPortBlocking(String ip, int port) throws Exception
    {
        var promise = Promise.<Boolean>promise();

        vertx.createNetClient(new NetClientOptions().setConnectTimeout(PORT_SCAN_TIMEOUT_MS))
                .connect(port, ip, ar -> promise.complete(ar.succeeded()));

        return promise.future().toCompletionStage().toCompletableFuture().get();
    }

    private JsonObject spawnDiscoveryProcess(String ip, int port, JsonArray credentials) throws Exception
    {
        var processInput = new JsonObject()
                .put("requestType", "Discovery")
                .put("contexts", new JsonArray()
                        .add(new JsonObject()
                                .put("ip", ip)
                                .put("port", port)
                                .put("credentials", credentials)));

        var command = List.of("go", "run", "main.go", processInput.encode());

        var pb = new ProcessBuilder(command);

        pb.redirectErrorStream(true);

        var process = pb.start();

        var output = new StringBuilder();

        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
        {
            String line;

            while ((line = reader.readLine()) != null)
            {
                output.append(line).append("\n");
            }
        }

        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        {
            process.destroy();

            throw new RuntimeException("Go process timed out");
        }

        if (process.exitValue() != 0)
        {
            throw new RuntimeException("Go process failed with exit code: " + process.exitValue());
        }

        return new JsonObject()
                .put("status", "success")
                .put("result", output.toString());
    }

    private JsonObject errorResponse(String message)
    {
        return new JsonObject()
                .put("status", "error")
                .put("message", message);
    }
}
