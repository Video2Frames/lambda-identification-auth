resource "aws_apigatewayv2_integration" "lambda_backend" {
  depends_on             = [aws_lambda_function.id_lambda]
  api_id                 = data.aws_apigatewayv2_api.tc_api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.id_lambda.arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "route" {
  api_id    = data.aws_apigatewayv2_api.tc_api.id
  route_key = "POST /clients"
  target    = "integrations/${aws_apigatewayv2_integration.lambda_backend.id}"
}