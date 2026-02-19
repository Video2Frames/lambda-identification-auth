data "aws_s3_bucket" "video_uploads" {
  bucket = "video2frames-video-uploads"
}

data "terraform_remote_state" "infra" {
  backend = "s3"
  config = {
    bucket = "fiap-soat-hackathon-2026-tfstate"
    key    = "PRD/hackathon-infra"
    region = "us-east-1"
  }
}

data "aws_apigatewayv2_api" "hackathon_api" {
  api_id = data.terraform_remote_state.infra.outputs.api_gateway_id
}

data "aws_vpc" "hackathon-vpc" {
  filter {
    name   = "tag:Name"
    values = ["hackathon-vpc"]
  }
}

data "aws_subnets" "tc_lambda_subnets" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.hackathon-vpc.id]
  }
}

data "aws_db_instance" "db_instance" {
  db_instance_identifier = data.terraform_remote_state.infra.outputs.database_identifier
}
