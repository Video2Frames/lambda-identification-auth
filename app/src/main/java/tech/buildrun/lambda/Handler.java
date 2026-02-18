package tech.buildrun.lambda;

import com.amazonaws.services.lambda.runtime.*;
import com.amazonaws.services.lambda.runtime.events.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.cognitoidentityprovider.*;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.sql.*;

public class Handler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CognitoIdentityProviderClient cognito = CognitoIdentityProviderClient.create();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            ClientDTO dto = mapper.readValue(request.getBody(), ClientDTO.class);

            createUserCognito(dto);
            saveClientAurora(dto);

            return response(201, "Cliente cadastrado com sucesso");

        } catch (Exception e) {
            return response(500, e.getMessage());
        }
    }

    private void createUserCognito(ClientDTO dto) {
        cognito.adminCreateUser(AdminCreateUserRequest.builder()
                .userPoolId(System.getenv("USER_POOL_ID"))
                .username(dto.getEmail())
                .userAttributes(
                        AttributeType.builder().name("email").value(dto.getEmail()).build()
                )
                .temporaryPassword(dto.getPassword())
                .build());

        cognito.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                .userPoolId(System.getenv("USER_POOL_ID"))
                .username(dto.getEmail())
                .password(dto.getPassword())
                .permanent(true)
                .build());
    }

    private void saveClientAurora(ClientDTO dto) throws Exception {

        String sql = "INSERT INTO tb_identification (email, senha) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, dto.getEmail());
            ps.setString(2, PasswordService.hash(dto.getPassword()));

            ps.executeUpdate();
        }
    }


    private APIGatewayProxyResponseEvent response(int status, String msg) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withBody("{\"message\":\"" + msg + "\"}");
    }
}
