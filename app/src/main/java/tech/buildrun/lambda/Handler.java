package tech.buildrun.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

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

            String path = request.getPath();
            String method = request.getHttpMethod();

            if (path.endsWith("/clients") && method.equalsIgnoreCase("POST")) {
                return createClient(request);
            }

            if (path.endsWith("/login") && method.equalsIgnoreCase("POST")) {
                return login(request);
            }

            if (path.endsWith("/me") && method.equalsIgnoreCase("GET")) {
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

        Map<String, String> body = mapper.readValue(request.getBody(), Map.class);

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

        Map<String, String> body = mapper.readValue(request.getBody(), Map.class);

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

        if (headers == null || !headers.containsKey("Authorization")) {
            return response(401, Map.of("message", "Token não informado"));
        }

        String authHeader = headers.get("Authorization");

        if (!authHeader.startsWith("Bearer ")) {
            return response(401, Map.of("message", "Token inválido"));
        }

        String token = authHeader.substring(7);

        // JWT possui 3 partes
        String[] parts = token.split("\\.");

        if (parts.length != 3) {
            return response(401, Map.of("message", "Token JWT inválido"));
        }

        // Decodifica payload
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> claims = mapper.readValue(payload, Map.class);

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
}