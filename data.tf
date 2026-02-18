data "terraform_remote_state" "infra" {
  backend = "s3"
  config = {
    bucket = "tc-2025-tf-states-bucket"
    key    = "PRD/tc-infra"
    region = "us-east-1"
  }
}

data "terraform_remote_state" "database" {
  backend = "s3"
  config = {
    bucket = "tc-2025-tf-states-bucket"
    key    = "PRD/tc-database"
    region = "us-east-1"
  }
}

data "aws_apigatewayv2_api" "tc_api" {
  api_id = data.terraform_remote_state.infra.outputs.api_gateway_id
}

data "aws_vpc" "tc_lambda_vpc" {
  filter {
    name   = "tag:Name"
    values = ["tc-infra-vpc"]
  }
}

data "aws_subnets" "tc_lambda_subnets" {
  depends_on = [data.aws_vpc.tc_lambda_vpc]
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.tc_lambda_vpc.id]
  }
}

data "aws_db_instance" "db_instance" {
  db_instance_identifier = data.terraform_remote_state.database.outputs.database_identifier
}

data "aws_security_group" "rds" {
  id = data.terraform_remote_state.database.outputs.rds_sg_id
}
