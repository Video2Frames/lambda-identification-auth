############################################
# IAM ROLE
############################################

resource "aws_iam_role" "lambda_exec_role" {
  name = "tc-lambda-identification-auth-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Service = "lambda.amazonaws.com" },
      Action    = "sts:AssumeRole"
    }]
  })
}

############################################
# IAM POLICY - COGNITO
############################################

resource "aws_iam_policy" "lambda_cognito_policy" {
  name = "lambda-cognito-policy"

  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "cognito-idp:AdminCreateUser",
          "cognito-idp:AdminSetUserPassword",
          "cognito-idp:AdminInitiateAuth",
          "cognito-idp:AdminGetUser"
        ],
        Resource = aws_cognito_user_pool.pool.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_cognito_attach" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = aws_iam_policy.lambda_cognito_policy.arn
}

resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "lambda_vpc_access" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

############################################
# SECURITY GROUP - LAMBDA
############################################

data "aws_security_group" "id_lambda" {
  # Lookup an existing security group by name in the target VPC.
  # Assumes the security group `tc-id-lambda-sg` is already created elsewhere (e.g. in infra state).
  filter {
    name   = "group-name"
    values = ["tc-id-lambda-sg"]
  }

  # Restrict lookup to the same VPC used by the infra remote state to avoid ambiguous matches.
  vpc_id = data.terraform_remote_state.infra.outputs.vpc_id
}

############################################
# LAMBDA FUNCTION
############################################

resource "aws_lambda_function" "id_lambda" {
  function_name = "lambda-identification-auth"
  depends_on    = [aws_iam_role_policy_attachment.lambda_logs]

  role    = aws_iam_role.lambda_exec_role.arn
  handler = "tech.buildrun.lambda.Handler::handleRequest"
  runtime = "java17"

  timeout = 30

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
    subnet_ids         = data.terraform_remote_state.infra.outputs.private_subnets
    security_group_ids = [data.aws_security_group.id_lambda.id]
  }

  tags = var.tags
}

############################################
# API GATEWAY PERMISSION
############################################

resource "aws_lambda_permission" "apigw_invoke_lambda" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.id_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${data.aws_apigatewayv2_api.hackathon_api.execution_arn}/*/*"
}

############################################
# RULE - LAMBDA -> RDS
############################################

resource "aws_security_group_rule" "id_lambda_to_rds" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.id_lambda.id
  security_group_id        = data.aws_security_group.rds.id
}