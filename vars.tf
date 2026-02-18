# Região da AWS
variable "region" {
  default = "us-east-1"
}

# Tags para os recursos
variable "tags" {
  default = {
    Environment = "PRD"
    Project     = "tc-lambda-identification-auth"
  }
}

# Banco de dados
variable "db_user" {
  description = "Usuário do banco de dados"
  sensitive   = true
}

variable "db_password" {
  description = "Senha do banco de dados"
  sensitive   = true
}

variable "lambda_jar_path" {
  description = "Caminho do fat JAR da Lambda"
  type        = string
  default     = "app/target/lambda-identification-auth.jar"
}
