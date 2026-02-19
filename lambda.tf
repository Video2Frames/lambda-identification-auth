resource "aws_iam_role" "lambda_exec_role" {
  name = "${var.tags.project}-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Service = "lambda.amazonaws.com" },
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_security_group" "id_lambda" {
  name        = "tc-id-lambda-sg"
  description = "Security group for Lambda ID function"
  vpc_id      = data.aws_vpc.hackathon-vpc.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = var.tags
}
resource "aws_lambda_function" "id_lambda" {
  function_name = "lambda-identification-auth"
  depends_on    = []
  role          = aws_iam_role.lambda_exec_role.arn
  handler       = "tech.buildrun.lambda.Handler::handleRequest"
  runtime       = "java17"

  timeout       = 30

  # Usa o caminho passado via variável
  filename         = var.lambda_jar_path
  source_code_hash = filebase64sha256(var.lambda_jar_path)

  environment {
    variables = {
      DB_URL      = local.jdbc_url
      DB_USER     = var.db_user
      DB_PASSWORD = var.db_password
    }
  }
  vpc_config {
    subnet_ids         = data.aws_subnets.tc_lambda_subnets.ids
    security_group_ids = [aws_security_group.id_lambda.id]
  }

  tags = var.tags
}

resource "aws_lambda_permission" "apigw_invoke_lambda" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.id_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${data.aws_apigatewayv2_api.hackathon_api.execution_arn}/*/*"
}
