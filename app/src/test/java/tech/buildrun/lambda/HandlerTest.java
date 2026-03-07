package tech.buildrun.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandlerTest {

    @Mock
    private CognitoIdentityProviderClient cognito;

    @InjectMocks
    private Handler handler;

    @BeforeEach
    void setup() throws Exception {

        cognito = mock(CognitoIdentityProviderClient.class);

        handler = new Handler();

        Field field = Handler.class.getDeclaredField("cognito");
        field.setAccessible(true);
        field.set(handler, cognito);
    }

    @Test
    void deveCriarClienteComSucesso() throws Exception {

        // DADO
        APIGatewayV2HTTPEvent request = new APIGatewayV2HTTPEvent();
        request.setRawPath("/clients");

        APIGatewayV2HTTPEvent.RequestContext context = new APIGatewayV2HTTPEvent.RequestContext();
        APIGatewayV2HTTPEvent.RequestContext.Http http = new APIGatewayV2HTTPEvent.RequestContext.Http();

        http.setMethod("POST");
        context.setHttp(http);
        request.setRequestContext(context);

        request.setBody("""
        {
          "email":"teste@email.com",
          "password":"123456"
        }
        """);

        when(cognito.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenReturn(AdminCreateUserResponse.builder().build());

        when(cognito.adminSetUserPassword(any(AdminSetUserPasswordRequest.class)))
                .thenReturn(AdminSetUserPasswordResponse.builder().build());

        // QUANDO
        APIGatewayV2HTTPResponse response = handler.handleRequest(request, null);

        // ENTÃO
        assertEquals(201, response.getStatusCode());

        verify(cognito, times(1))
                .adminCreateUser(any(AdminCreateUserRequest.class));

        verify(cognito, times(1))
                .adminSetUserPassword(any(AdminSetUserPasswordRequest.class));
    }

    @Test
    void deveRealizarLoginComSucesso() throws Exception {

        // DADO
        APIGatewayV2HTTPEvent request = new APIGatewayV2HTTPEvent();
        request.setRawPath("/login");

        APIGatewayV2HTTPEvent.RequestContext context = new APIGatewayV2HTTPEvent.RequestContext();
        APIGatewayV2HTTPEvent.RequestContext.Http http = new APIGatewayV2HTTPEvent.RequestContext.Http();

        http.setMethod("POST");
        context.setHttp(http);
        request.setRequestContext(context);

        request.setBody("""
        {
          "email":"teste@email.com",
          "password":"123456"
        }
        """);

        AuthenticationResultType authResult =
                AuthenticationResultType.builder()
                        .idToken("idToken")
                        .accessToken("accessToken")
                        .refreshToken("refreshToken")
                        .build();

        InitiateAuthResponse authResponse =
                InitiateAuthResponse.builder()
                        .authenticationResult(authResult)
                        .build();

        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenReturn(authResponse);

        // QUANDO
        APIGatewayV2HTTPResponse response = handler.handleRequest(request, null);

        // ENTÃO
        assertEquals(200, response.getStatusCode());

        verify(cognito, times(1))
                .initiateAuth(any(InitiateAuthRequest.class));
    }

    @Test
    void deveRetornarUsuarioDoToken() throws Exception {

        // DADO
        APIGatewayV2HTTPEvent request = new APIGatewayV2HTTPEvent();
        request.setRawPath("/me");

        APIGatewayV2HTTPEvent.RequestContext context = new APIGatewayV2HTTPEvent.RequestContext();
        APIGatewayV2HTTPEvent.RequestContext.Http http = new APIGatewayV2HTTPEvent.RequestContext.Http();

        http.setMethod("GET");
        context.setHttp(http);
        request.setRequestContext(context);

        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("""
        {"sub":"123","email":"teste@email.com"}
        """.getBytes());

        String token = "aaa." + payload + ".bbb";

        request.setHeaders(Map.of(
                "authorization", "Bearer " + token
        ));

        // QUANDO
        APIGatewayV2HTTPResponse response = handler.handleRequest(request, null);

        // ENTÃO
        assertEquals(200, response.getStatusCode());
    }
}