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

            String path = request.getPath();
            String method = request.getHttpMethod();

            // POST /clients
            if ("/clients".equals(path) && "POST".equals(method)) {
                ClientDTO dto = mapper.readValue(request.getBody(), ClientDTO.class);

                createUserCognito(dto);
                saveClientAurora(dto);

                return response(201, "Cliente cadastrado com sucesso");
            }

            // GET /me
            if ("/me".equals(path) && "GET".equals(method)) {

                String email = extractEmailFromToken(request);

                String result = findClientByEmail(email);

                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withBody("{\"email\":\"" + result + "\"}");
            }

            // GET /clients/{id}
            if (path.startsWith("/clients/") && "GET".equals(method)) {

                String id = request.getPathParameters().get("id");

                String email = findClientById(id);

                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withBody("{\"email\":\"" + email + "\"}");
            }

            return response(404, "Endpoint não encontrado");

        } catch (Exception e) {
            return response(500, e.getMessage());
        }
    }

    private String findClientById(String id) throws Exception {

        String sql = "SELECT email FROM tb_identification WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setObject(1, java.util.UUID.fromString(id));

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("email");
            }

            throw new RuntimeException("Cliente não encontrado");
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

    private String findClientByEmail(String email) throws Exception {

        String sql = "SELECT email FROM tb_identification WHERE email = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, email);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("email");
            }

            throw new RuntimeException("Cliente não encontrado");
        }
    }

    private String extractEmailFromToken(APIGatewayProxyRequestEvent request) {

        String authHeader = request.getHeaders().get("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Token não enviado");
        }

        String token = authHeader.substring(7);

        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

        try {
            return mapper.readTree(payload).get("email").asText();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao extrair email do token");
        }
    }


    private APIGatewayProxyResponseEvent response(int status, String msg) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withBody("{\"message\":\"" + msg + "\"}");
    }
}
