package tech.buildrun.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.*;

public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String USER_POOL_ID =  System.getenv("USER_POOL_ID");
    private static final String CLIENT_ID = System.getenv("CLIENT_ID");

    private final ObjectMapper mapper = new ObjectMapper();

    private final CognitoIdentityProviderClient cognito = CognitoIdentityProviderClient.builder()
            .region(Region.US_EAST_1)
            .build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {

        try {

            // Extract method and path in a way that supports both REST (v1) and HTTP (v2) API Gateway events.
            Route route = extractPathAndMethod(request);
            String safePath = route.path == null ? "" : route.path;
            String safeMethod = route.method == null ? "" : route.method;

            if (safePath.endsWith("/clients") && safeMethod.equalsIgnoreCase("POST")) {
                return createClient(request);
            }

            if (safePath.endsWith("/login") && safeMethod.equalsIgnoreCase("POST")) {
                return login(request);
            }

            if (safePath.endsWith("/me") && safeMethod.equalsIgnoreCase("GET")) {
                return me(request);
            }

            return response(404, Map.of("message", "endpoint não encontrado"));

        } catch (Exception e) {

            try {
                return response(500, Map.of("error", e.getMessage()));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private APIGatewayProxyResponseEvent createClient(APIGatewayProxyRequestEvent request) throws Exception {

        Map<String, String> body = mapper.readValue(request.getBody(), new TypeReference<>() {});

        String email = body.get("email");
        String password = body.get("password");

        AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(email)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value("true").build()
                )
                .messageAction(MessageActionType.SUPPRESS)
                .build();

        cognito.adminCreateUser(createUserRequest);

        AdminSetUserPasswordRequest passwordRequest = AdminSetUserPasswordRequest.builder()
                .userPoolId(USER_POOL_ID)
                .username(email)
                .password(password)
                .permanent(true)
                .build();

        cognito.adminSetUserPassword(passwordRequest);

        return response(201, Map.of("message", "cliente criado com sucesso"));
    }

    private APIGatewayProxyResponseEvent login(APIGatewayProxyRequestEvent request) throws Exception {

        Map<String, String> body = mapper.readValue(request.getBody(), new TypeReference<>() {});

        String email = body.get("email");
        String password = body.get("password");

        Map<String, String> authParams = new HashMap<>();
        authParams.put("USERNAME", email);
        authParams.put("PASSWORD", password);

        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(CLIENT_ID)
                .authParameters(authParams)
                .build();

        InitiateAuthResponse authResponse = cognito.initiateAuth(authRequest);

        Map<String, Object> tokens = new HashMap<>();
        tokens.put("id_token", authResponse.authenticationResult().idToken());
        tokens.put("access_token", authResponse.authenticationResult().accessToken());
        tokens.put("refresh_token", authResponse.authenticationResult().refreshToken());

        return response(200, tokens);
    }

    private APIGatewayProxyResponseEvent me(APIGatewayProxyRequestEvent request) throws Exception {

        Map<String, String> headers = request.getHeaders();

        // look up Authorization header case-insensitively to handle API Gateway/clients that lowercase header names
        String authHeader = null;
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if ("authorization".equalsIgnoreCase(e.getKey())) {
                    authHeader = e.getValue();
                    break;
                }
            }
        }

        if (authHeader == null || authHeader.trim().isEmpty()) {
            return response(401, Map.of("message", "Token não informado"));
        }

        authHeader = authHeader.trim();

        // Accept Bearer token case-insensitively and tolerate extra spaces
        if (!authHeader.toLowerCase().startsWith("bearer ")) {
            return response(401, Map.of("message", "Token inválido"));
        }

        String token = authHeader.substring(authHeader.indexOf(' ') + 1).trim();

        // JWT possui 3 partes
        String[] parts = token.split("\\.");

        if (parts.length != 3) {
            return response(401, Map.of("message", "Token JWT inválido"));
        }

        // Decodifica payload
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

        Map<String, Object> claims = mapper.readValue(payload, new TypeReference<>() {});

        Map<String, Object> user = new HashMap<>();

        user.put("id", claims.get("sub"));
        user.put("email", claims.get("email"));

        return response(200, user);
    }

    private APIGatewayProxyResponseEvent response(int status, Object body) throws Exception {

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(mapper.writeValueAsString(body));
    }

    // Helper types and methods for flexible extraction
    private static class Route {
        String path;
        String method;

        Route(String path, String method) {
            this.path = path;
            this.method = method;
        }
    }

    private Route extractPathAndMethod(APIGatewayProxyRequestEvent request) {
        if (request == null) {
            return new Route(null, null);
        }

        String path = request.getPath();
        String method = request.getHttpMethod();

        // If both present, return immediately
        if (path != null && method != null) {
            return new Route(path, method);
        }

        // Try to inspect requestContext for HTTP v2 style: requestContext.http.method & requestContext.http.path
        Object rc = request.getRequestContext();
        if (rc != null) {
            try {
                // try rc.getHttp() -> object with getMethod() and getPath()
                java.lang.reflect.Method getHttp = null;
                try {
                    getHttp = rc.getClass().getMethod("getHttp");
                } catch (NoSuchMethodException ignored) {}

                if (getHttp != null) {
                    Object httpObj = getHttp.invoke(rc);
                    if (httpObj != null) {
                        try {
                            java.lang.reflect.Method getMethod = httpObj.getClass().getMethod("getMethod");
                            Object m = getMethod.invoke(httpObj);
                            if (m instanceof String && method == null) method = (String) m;
                        } catch (NoSuchMethodException ignored) {}

                        try {
                            java.lang.reflect.Method getPath = httpObj.getClass().getMethod("getPath");
                            Object p = getPath.invoke(httpObj);
                            if (p instanceof String && path == null) path = (String) p;
                        } catch (NoSuchMethodException ignored) {}

                        try {
                            java.lang.reflect.Method getRawPath = httpObj.getClass().getMethod("getRawPath");
                            Object p = getRawPath.invoke(httpObj);
                            if (p instanceof String && (path == null || path.isEmpty())) path = (String) p;
                        } catch (NoSuchMethodException ignored) {}
                    }
                }

                // fallback: requestContext may have getHttpMethod() or getResourcePath()
                try {
                    java.lang.reflect.Method mth = rc.getClass().getMethod("getHttpMethod");
                    Object m = mth.invoke(rc);
                    if (m instanceof String && method == null) method = (String) m;
                } catch (Exception ignored) {}

                try {
                    java.lang.reflect.Method pth = rc.getClass().getMethod("getResourcePath");
                    Object p = pth.invoke(rc);
                    if (p instanceof String && path == null) path = (String) p;
                } catch (Exception ignored) {}

                try {
                    java.lang.reflect.Method pth2 = rc.getClass().getMethod("getPath");
                    Object p = pth2.invoke(rc);
                    if (p instanceof String && path == null) path = (String) p;
                } catch (Exception ignored) {}

            } catch (Exception ignored) {
                // ignore reflection issues and continue
            }
        }

        return new Route(path, method);
    }
}