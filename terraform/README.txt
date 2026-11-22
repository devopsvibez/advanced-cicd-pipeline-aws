# Project 06 - Step 1: Infrastructure with Terraform

This creates the Step 1 infrastructure for the Advanced CI/CD Pipeline project
(VPC + public subnet + internet gateway + security group + 3 EC2 instances):

- **Ansible controller** - runs Ansible playbooks (t3.micro)
- **Jenkins master** - runs Jenkins UI on port 8080 (t3.small)
- **Jenkins agent** - runs build jobs for the master (t3.micro)

Region: **us-east-1**. Everything else is created automatically, including a
key pair (saved as `project06-key.pem` in this folder after apply).

## Before you start (2 minutes)

1. Install Terraform: https://developer.hashicorp.com/terraform/install
2. Make sure your AWS CLI is logged in: `aws configure` (needs an IAM user
   with permission to create EC2/VPC resources, e.g. AdministratorAccess).
3. Copy the example vars file and put in YOUR public IP:

       cp terraform.tfvars.example terraform.tfvars

   Open https://whatismyip.com, copy your IP, and put it in terraform.tfvars
   like: `my_ip = "203.0.113.10/32"` (keep the `/32` at the end).

## Create the infrastructure

    terraform init
    terraform plan
    terraform apply    # type: yes

When it finishes it prints the 3 public IPs and ready-made SSH commands.
Jenkins is not installed yet - that is Step 2 (Ansible).

## When you are done (IMPORTANT - stop the billing)

    terraform destroy   # type: yes

## Cost note

Approximate on-demand prices in us-east-1 (Nov 2026):

- t3.small (master)  : ~$0.021/hour  (~$15/month if left running)
- t3.micro x 2       : ~$0.010/hour each (~$7.50/month each)
- All 3 together     : ~$0.041/hour  (~$1.00/day, ~$30/month)
- VPC, subnet, IGW, security group, key pair: free

If your AWS account is still in the free tier, t3.micro hours may be free.
Even so: run `terraform destroy` when you finish each session. Billing only
stops when the instances are destroyed.
