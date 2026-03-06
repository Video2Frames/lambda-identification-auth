package tech.buildrun.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.*;

public class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final String USER_POOL_ID = System.getenv("USER_POOL_ID");
    private static final String CLIENT_ID = System.getenv("CLIENT_ID");

    private final ObjectMapper mapper = new ObjectMapper();

    private final CognitoIdentityProviderClient cognito =
            CognitoIdentityProviderClient.builder()
                    .region(Region.US_EAST_1)
                    .build();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {

        try {

            String path = request.getRawPath();
            String method = request.getRequestContext().getHttp().getMethod();

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
            return response(500, Map.of("error", e.getMessage()));
        }
    }

    // ---------------------------------------------------
    // CREATE CLIENT
    // ---------------------------------------------------

    private APIGatewayV2HTTPResponse createClient(APIGatewayV2HTTPEvent request) throws Exception {

        Map<String, String> body =
                mapper.readValue(request.getBody(), new TypeReference<>() {});

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

        AdminSetUserPasswordRequest passwordRequest =
                AdminSetUserPasswordRequest.builder()
                        .userPoolId(USER_POOL_ID)
                        .username(email)
                        .password(password)
                        .permanent(true)
                        .build();

        cognito.adminSetUserPassword(passwordRequest);

        return response(201, Map.of("message", "cliente criado com sucesso"));
    }

    // ---------------------------------------------------
    // LOGIN
    // ---------------------------------------------------

    private APIGatewayV2HTTPResponse login(APIGatewayV2HTTPEvent request) throws Exception {

        Map<String, String> body =
                mapper.readValue(request.getBody(), new TypeReference<>() {});

        String email = body.get("email");
        String password = body.get("password");

        Map<String, String> authParams = new HashMap<>();
        authParams.put("USERNAME", email);
        authParams.put("PASSWORD", password);

        AdminInitiateAuthRequest authRequest =
                AdminInitiateAuthRequest.builder()
                        .userPoolId(USER_POOL_ID)
                        .clientId(CLIENT_ID)
                        .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                        .authParameters(authParams)
                        .build();

        AdminInitiateAuthResponse authResponse =
                cognito.adminInitiateAuth(authRequest);

        return response(200, authResponse.authenticationResult());
    }

    // ---------------------------------------------------
    // ME (TOKEN)
    // ---------------------------------------------------

    private APIGatewayV2HTTPResponse me(APIGatewayV2HTTPEvent request) throws Exception {

        Map<String, String> headers = request.getHeaders();

        if (headers == null) {
            return response(401, "Token não informado");
        }

        String authHeader = headers.get("authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return response(401, "Token inválido");
        }

        String token = authHeader.substring(7);

        String[] parts = token.split("\\.");

        if (parts.length != 3) {
            return response(401, "Token JWT inválido");
        }

        // decodifica payload
        String payloadJson = new String(
                java.util.Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8
        );

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> claims = mapper.readValue(payloadJson, Map.class);

        Map<String, Object> user = new HashMap<>();

        user.put("id", claims.get("sub"));
        user.put("email", claims.get("email"));

        return response(200, mapper.writeValueAsString(user));
    }

    // ---------------------------------------------------
    // RESPONSE
    // ---------------------------------------------------

    private APIGatewayV2HTTPResponse response(int status, Object body) {

        try {

            APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();

            response.setStatusCode(status);
            response.setHeaders(Map.of("Content-Type", "application/json"));
            response.setBody(mapper.writeValueAsString(body));

            return response;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}