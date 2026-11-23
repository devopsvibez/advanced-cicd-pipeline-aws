terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
provider "aws" {
  region = "us-east-1"
  default_tags {
    tags = { Project = "project", Component = "eks-lab" }
  }
}


variable "lab_subnet_id" {
  description = "Existing public lab subnet, verified from AWS. Read only."
  type        = string
}
variable "second_subnet_cidr" {
  description = "Unused /24 in the existing VPC, verified before plan."
  type        = string
}
variable "admin_principal_arn" {
  description = "Permanent IAM user or role ARN for the laptop admin, NOT an STS session ARN."
  type        = string
  validation {
    condition     = can(regex("^arn:aws:iam::[0-9]{12}:(user|role)/.+$", var.admin_principal_arn))
    error_message = "Use an IAM user/role ARN, not a root or assumed-role session ARN."
  }
}
variable "admin_cidr" {
  description = "Laptop public IPv4 /32 allowed to reach the Kubernetes API."
  type        = string
  validation {
    condition     = can(cidrnetmask(var.admin_cidr)) && endswith(var.admin_cidr, "/32")
    error_message = "Use the current laptop IPv4 address with /32."
  }
}
variable "kubernetes_version" {
  description = "Verify STANDARD_SUPPORT using the regional AWS CLI before applying."
  type        = string
  default     = "1.36"
}


locals { name = "project-eks" }
data "aws_partition" "current" {}
data "aws_subnet" "lab" { id = var.lab_subnet_id }
data "aws_vpc" "lab" { id = data.aws_subnet.lab.vpc_id }
data "aws_route_table" "lab" { subnet_id = var.lab_subnet_id }
data "aws_availability_zones" "available" { state = "available" }
locals {
  other_az = [for az in data.aws_availability_zones.available.names : az if az != data.aws_subnet.lab.availability_zone][0]
}
resource "aws_subnet" "second" {
  vpc_id                  = data.aws_vpc.lab.id
  cidr_block              = var.second_subnet_cidr
  availability_zone       = local.other_az
  map_public_ip_on_launch = true
  tags                    = { Name = "${local.name}-second-public" }
}
resource "aws_route_table_association" "second" {
  subnet_id      = aws_subnet.second.id
  route_table_id = data.aws_route_table.lab.id
}
resource "aws_iam_role" "cluster" {
  name = "${local.name}-cluster"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "eks.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}
resource "aws_iam_role_policy_attachment" "cluster" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKSClusterPolicy"
}
resource "aws_eks_cluster" "this" {
  name     = local.name
  role_arn = aws_iam_role.cluster.arn
  version  = var.kubernetes_version
  access_config {
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = false
  }
  upgrade_policy { support_type = "STANDARD" }
  vpc_config {
    subnet_ids              = [var.lab_subnet_id, aws_subnet.second.id]
    endpoint_private_access = true
    endpoint_public_access  = true
    public_access_cidrs     = [var.admin_cidr]
  }
  depends_on = [aws_iam_role_policy_attachment.cluster, aws_route_table_association.second]
}
resource "aws_eks_access_entry" "admin" {
  cluster_name  = aws_eks_cluster.this.name
  principal_arn = var.admin_principal_arn
  type          = "STANDARD"
}
resource "aws_eks_access_policy_association" "admin" {
  cluster_name  = aws_eks_cluster.this.name
  principal_arn = aws_eks_access_entry.admin.principal_arn
  policy_arn    = "arn:${data.aws_partition.current.partition}:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
  access_scope { type = "cluster" }
}
resource "aws_iam_role" "node" {
  name = "${local.name}-node"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}
resource "aws_iam_role_policy_attachment" "node" {
  for_each   = toset(["AmazonEKSWorkerNodePolicy", "AmazonEC2ContainerRegistryPullOnly", "AmazonEKS_CNI_Policy"])
  role       = aws_iam_role.node.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/${each.value}"
}
resource "aws_launch_template" "node" {
  name_prefix = "${local.name}-"
  credit_specification { cpu_credits = "standard" }
  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size           = 20
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }
}
resource "aws_eks_node_group" "one" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "one-small"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = [var.lab_subnet_id]
  ami_type        = "AL2023_x86_64_STANDARD"
  capacity_type   = "ON_DEMAND"
  instance_types  = ["t3.small"]
  scaling_config {
    desired_size = 1
    min_size     = 1
    max_size     = 1
  }
  update_config {
    max_unavailable = 1
    update_strategy = "MINIMAL"
  }
  launch_template {
    id      = aws_launch_template.node.id
    version = aws_launch_template.node.latest_version
  }
  lifecycle {
    precondition {
      condition     = data.aws_subnet.lab.map_public_ip_on_launch
      error_message = "Existing public subnet must auto-assign public IPv4 for node internet egress without NAT."
    }
  }
  depends_on = [aws_iam_role_policy_attachment.node]
}
# Uses the existing EC2 agent via an optional root-state instance-profile input.
# Never add access keys or a reusable kubeconfig secret to Jenkins.
resource "aws_iam_role" "jenkins" {
  name = "${local.name}-jenkins"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}
resource "aws_iam_role_policy" "jenkins" {
  name = "describe-one-cluster"
  role = aws_iam_role.jenkins.id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "eks:DescribeCluster", Resource = aws_eks_cluster.this.arn }]
  })
}
resource "aws_iam_instance_profile" "jenkins" {
  name = "${local.name}-jenkins"
  role = aws_iam_role.jenkins.name
}
resource "aws_eks_access_entry" "jenkins" {
  cluster_name  = aws_eks_cluster.this.name
  principal_arn = aws_iam_role.jenkins.arn
  type          = "STANDARD"
}
resource "aws_eks_access_policy_association" "jenkins" {
  cluster_name  = aws_eks_cluster.this.name
  principal_arn = aws_eks_access_entry.jenkins.principal_arn
  policy_arn    = "arn:${data.aws_partition.current.partition}:eks::aws:cluster-access-policy/AmazonEKSEditPolicy"
  access_scope {
    type       = "namespace"
    namespaces = ["pipeline-lab"]
  }
}
# Jenkins reaches the private Kubernetes endpoint from the existing lab subnet.
resource "aws_vpc_security_group_ingress_rule" "agent_api" {
  security_group_id = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
  cidr_ipv4         = data.aws_subnet.lab.cidr_block
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "Kubernetes API from lab subnet; IAM and EKS RBAC still required"
}


# One Terraform-managed NLB. Kubernetes must use NodePort, never LoadBalancer.
# No in-cluster AWS load-balancer controller or broad controller IAM required.
resource "aws_security_group" "nlb" {
  name_prefix = "${local.name}-nlb-"
  vpc_id      = data.aws_vpc.lab.id
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
resource "aws_lb" "app" {
  name                             = "${local.name}-app"
  internal                         = false
  load_balancer_type               = "network"
  security_groups                  = [aws_security_group.nlb.id]
  subnets                          = [var.lab_subnet_id, aws_subnet.second.id]
  enable_cross_zone_load_balancing = true
  depends_on                       = [aws_route_table_association.second]
}
resource "aws_lb_target_group" "app" {
  name_prefix          = "eks-"
  port                 = 30080
  protocol             = "TCP"
  target_type          = "instance"
  vpc_id               = data.aws_vpc.lab.id
  preserve_client_ip   = false
  deregistration_delay = 10
  health_check {
    protocol = "HTTP"
    port     = "traffic-port"
    path     = "/healthz"
    matcher  = "200"
  }
}
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "TCP"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}
resource "aws_autoscaling_attachment" "app" {
  autoscaling_group_name = aws_eks_node_group.one.resources[0].autoscaling_groups[0].name
  lb_target_group_arn    = aws_lb_target_group.app.arn
}
resource "aws_vpc_security_group_ingress_rule" "nlb_to_app" {
  security_group_id            = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
  referenced_security_group_id = aws_security_group.nlb.id
  ip_protocol                  = "tcp"
  from_port                    = 30080
  to_port                      = 30080
  description                  = "App NodePort and health check from sole NLB"
}


output "cluster_name" { value = aws_eks_cluster.this.name }
output "aws_region" { value = "us-east-1" }
output "app_url" { value = "http://${aws_lb.app.dns_name}" }
output "jenkins_instance_profile" { value = aws_iam_instance_profile.jenkins.name }
output "jenkins_role_arn" { value = aws_iam_role.jenkins.arn }
output "configure_kubectl" {
  value = "aws eks update-kubeconfig --region us-east-1 --name ${aws_eks_cluster.this.name}"
}
