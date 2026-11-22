# -----------------------------------------------------------
# Latest Amazon Linux 2023 AMI in the chosen region
# -----------------------------------------------------------
data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# -----------------------------------------------------------
# Key pair (generated for you - the .pem file is saved locally)
# -----------------------------------------------------------
resource "tls_private_key" "project_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "project_key" {
  key_name   = "${var.project_name}-key"
  public_key = tls_private_key.project_key.public_key_openssh
}

resource "local_sensitive_file" "private_key_pem" {
  content         = tls_private_key.project_key.private_key_pem
  filename        = "${path.module}/${var.project_name}-key.pem"
  file_permission = "0400"
}

# -----------------------------------------------------------
# VPC, public subnet, internet gateway, route table
# -----------------------------------------------------------
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.project_name}-vpc" }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = { Name = "${var.project_name}-public-subnet" }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project_name}-igw" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }

  tags = { Name = "${var.project_name}-public-rt" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# -----------------------------------------------------------
# Security group
#   SSH (22)    -> only your IP
#   Jenkins UI (8080) -> only your IP
#   Jenkins agent port (50000) -> inside the VPC only
#   All outbound traffic allowed
# -----------------------------------------------------------
resource "aws_security_group" "project_sg" {
  name        = "${var.project_name}-sg"
  description = "Project 06: SSH + Jenkins only from your IP"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH from your IP only"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.my_ip]
  }

  ingress {
    description = "Jenkins UI from your IP only"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.my_ip]
  }

  ingress {
    description = "Jenkins master-agent port within VPC"
    from_port   = 50000
    to_port     = 50000
    protocol    = "tcp"
    cidr_blocks = [aws_vpc.main.cidr_block]
  }

  ingress {
    description = "All traffic between our own servers"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-sg" }
}

# -----------------------------------------------------------
# EC2 instances
# -----------------------------------------------------------
resource "aws_instance" "ansible_controller" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.agent_instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.project_sg.id]
  key_name               = aws_key_pair.project_key.key_name

  tags = {
    Name = "${var.project_name}-ansible-controller"
    Role = "ansible-controller"
  }
}

resource "aws_instance" "jenkins_master" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.master_instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.project_sg.id]
  key_name               = aws_key_pair.project_key.key_name

  tags = {
    Name = "${var.project_name}-jenkins-master"
    Role = "jenkins-master"
  }
}

resource "aws_instance" "jenkins_agent" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.agent_instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.project_sg.id]
  key_name               = aws_key_pair.project_key.key_name

  tags = {
    Name = "${var.project_name}-jenkins-agent"
    Role = "jenkins-agent"
  }
}
