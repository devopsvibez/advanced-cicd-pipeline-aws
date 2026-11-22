output "ansible_controller_public_ip" {
  description = "Public IP of the Ansible controller"
  value       = aws_instance.ansible_controller.public_ip
}

output "jenkins_master_public_ip" {
  description = "Public IP of the Jenkins master"
  value       = aws_instance.jenkins_master.public_ip
}

output "jenkins_agent_public_ip" {
  description = "Public IP of the Jenkins agent"
  value       = aws_instance.jenkins_agent.public_ip
}

output "jenkins_url" {
  description = "Open this in your browser after Jenkins is installed (Step 2)"
  value       = "http://${aws_instance.jenkins_master.public_ip}:8080"
}

output "ssh_commands" {
  description = "How to SSH into each instance"
  value = {
    ansible_controller = "ssh -i ${var.project_name}-key.pem ec2-user@${aws_instance.ansible_controller.public_ip}"
    jenkins_master     = "ssh -i ${var.project_name}-key.pem ec2-user@${aws_instance.jenkins_master.public_ip}"
    jenkins_agent      = "ssh -i ${var.project_name}-key.pem ec2-user@${aws_instance.jenkins_agent.public_ip}"
  }
}
