variable "aws_region" {
  description = "AWS region to create resources in"
  type        = string
  default     = "us-east-1"
}

variable "my_ip" {
  description = "Your public IP in CIDR format, e.g. \"203.0.113.10/32\". SSH and Jenkins UI are only open to this IP. Find yours at https://whatismyip.com"
  type        = string
}

variable "project_name" {
  description = "Name prefix used for all resources and tags"
  type        = string
  default     = "project06"
}

variable "master_instance_type" {
  description = "Instance type for the Jenkins master"
  type        = string
  default     = "t3.small"
}

variable "agent_instance_type" {
  description = "Instance type for the Jenkins agent and Ansible controller"
  type        = string
  default     = "t3.micro"
}
