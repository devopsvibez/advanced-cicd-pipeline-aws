# Lab-only SonarQube host. Existing Jenkins instances are not modified.
# UI access: SSH tunnel from the same /32 used by the existing lab.
# Port 9000: Jenkins agent private address only; never public.
resource "aws_security_group" "sonar" {
  name        = "${var.project_name}-sonar-sg"
  description = "Sonar lab: SSH from owner, analysis from Jenkins agent"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "Owner SSH and UI tunnel"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.my_ip]
  }

  ingress {
    description = "Sonar analysis from Jenkins agent only"
    from_port   = 9000
    to_port     = 9000
    protocol    = "tcp"
    cidr_blocks = ["${aws_instance.jenkins_agent.private_ip}/32"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    precondition {
      condition     = can(cidrnetmask(var.my_ip)) && endswith(var.my_ip, "/32")
      error_message = "my_ip must be your single public IPv4 address with /32."
    }
  }

  tags = { Name = "${var.project_name}-sonar-sg" }
}

resource "aws_instance" "sonar" {
  ami                         = aws_instance.jenkins_agent.ami
  instance_type               = "t3.medium"
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.sonar.id]
  key_name                    = aws_key_pair.project_key.key_name
  associate_public_ip_address = true

  credit_specification {
    cpu_credits = "standard"
  }

  root_block_device {
    volume_size           = 30
    volume_type           = "gp3"
    iops                  = 3000
    throughput            = 125
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = {
    Name = "${var.project_name}-sonar"
    Role = "sonarqube-lab"
  }
}

output "sonar_public_ip" {
  value = aws_instance.sonar.public_ip
}

output "sonar_private_ip" {
  value = aws_instance.sonar.private_ip
}
