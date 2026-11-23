# EKS lab, separate state

One `t3.small` managed node, 20 GiB encrypted gp3, standard-support EKS,
one two-AZ internet-facing NLB, no NAT, no monitoring, no Auto Mode.
T3 CPU credits use Standard mode to prevent surplus-credit charges.
This directory reads the existing lab network without importing or changing its
resources. It creates one additional public subnet and route association.
Run every EKS command with `-chdir=terraform/eks` from the repository root.
Do not run root Terraform destroy while this lab still uses its VPC.

## 1. Verify inputs before planning

Read-only checks from the laptop, authenticated to the intended account:

```sh
aws sts get-caller-identity
terraform -chdir=terraform state show aws_subnet.public
terraform -chdir=terraform state show aws_instance.jenkins_agent
aws eks describe-cluster-versions --region us-east-1 --cluster-versions 1.36
```

Then inspect every current subnet in the VPC, the public subnet's route table,
and the Jenkins agent's current IAM instance profile with AWS CLI. Confirm:

- VPC and subnet belong to the currently running three-EC2 lab in us-east-1.
- Existing subnet auto-assigns public IPv4, with active `0.0.0.0/0` IGW route.
- The proposed `second_subnet_cidr` is unused and within the VPC CIDR.
- Version 1.36 is available in STANDARD_SUPPORT. If not, stop for a supported version.
- Laptop admin ARN is an IAM user or permanent role, not an STS session ARN.
- Agent has no existing role that would be overwritten. If it has one, stop and
  adapt permissions without discarding existing functionality.
- The current laptop public IPv4 /32 is used for `admin_cidr`.

Create `terraform/eks/terraform.tfvars` with these four verified values:

```hcl
lab_subnet_id       = "subnet-REPLACE"
second_subnet_cidr  = "REPLACE_UNUSED_CIDR"
admin_principal_arn = "arn:aws:iam::ACCOUNT:user/REPLACE"
admin_cidr          = "YOUR_CURRENT_IPV4/32"
```

The region defaults to `us-east-1` and Kubernetes version to `1.36`.
Do not commit tfvars, state, plans or kubeconfig. Do not share full root state:
it contains the SSH private key.

## 2. Plan, inspect, then apply the reviewed saved plan

```sh
terraform -chdir=terraform/eks init
terraform -chdir=terraform/eks fmt -check
terraform -chdir=terraform/eks validate
terraform -chdir=terraform/eks plan -out=eks.plan
terraform -chdir=terraform/eks show -no-color eks.plan
```

Review the entire plan BEFORE applying. No destroys or replacements; only EKS
lab additions. Exactly one cluster with STANDARD upgrade policy, one node group
with min/desired/max all 1 and only `t3.small`, one launch-template 20-GiB gp3
root disk, one internet-facing network load balancer spanning two AZs, no NAT,
no other load balancer, no Auto Mode, no logging/monitoring stack.

Terraform cannot quote an exact AWS bill. Estimated incremental baseline is approximately
$0.16/hour, not a hard limit, plus tax and usage/transfer.
Costs continue during setup failures and idle time. Obtain explicit approval for the resource scope and costs before any apply.
If the plan differs from that approved scope, do not apply it.

Only after the reviewed plan matches:

```sh
terraform -chdir=terraform/eks apply eks.plan
aws eks update-kubeconfig --region us-east-1 --name project-eks
kubectl get nodes -o wide
kubectl create namespace pipeline-lab --dry-run=client -o yaml | kubectl apply -f -
```

Standard EKS bootstraps VPC CNI, kube-proxy and CoreDNS. Confirm system Pods are
healthy before deploying; do not add another node to fix capacity without
permission. This is a one-node learning lab, not highly available.

## 3. Jenkins instance role

The EKS state creates `project-eks-jenkins` role and instance profile. The role
has only `eks:DescribeCluster` for this cluster at IAM level; its EKS access
policy grants edit rights only inside `pipeline-lab`, not cluster admin.

Root Terraform supports optional `jenkins_agent_instance_profile` with a
null default, mapped to the existing `aws_instance.jenkins_agent` field
`iam_instance_profile`. Set its local tfvars value to `project-eks-jenkins`.
Review the root saved plan separately: only an in-place profile attachment on
the Jenkins agent, no replacements or other changes. Apply only that reviewed
plan. Never use `-target` to hide unexpected drift.

On the Jenkins EC2 agent, using its short-lived instance-role credentials:

```sh
aws sts get-caller-identity
aws eks update-kubeconfig --region us-east-1 --name project-eks
kubectl auth can-i patch deployments -n pipeline-lab
kubectl auth can-i create namespaces
```

Expected: agent assumed-role ARN, namespace edit allowed, namespace creation
NOT allowed. Do not paste AWS keys into Jenkins and do not store a kubeconfig
credential. `update-kubeconfig` configures an exec credential helper; AWS CLI
retrieves fresh short-lived tokens when kubectl runs.

## 4. Deploy contract

The deployment script must use a per-build temporary kubeconfig, `umask 077`,
and an EXIT trap to remove it. Run only on a trusted publishing branch, never
on a PR. PRs must have no image push/deploy stage and no deployment credentials.
Remove the Namespace object from the apply manifest because the laptop admin
already created it. Set one app replica. Keep resource limits and probes.

Service contract:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: pipeline-lab
  namespace: pipeline-lab
spec:
  type: NodePort
  externalTrafficPolicy: Cluster
  selector:
    app: pipeline-lab
  ports:
    - name: http
      port: 80
      targetPort: http
      nodePort: 30080
```

Do NOT use `type: LoadBalancer` or install a controller. Terraform's NLB forwards
TCP 80 to NodePort 30080 and its target group tracks the managed node ASG.
Cross-zone forwarding is enabled so both public NLB AZ addresses can reach the
single worker. Additional cross-AZ/data charges may apply.

```sh
kubectl -n pipeline-lab rollout status deployment/pipeline-lab --timeout=180s
terraform -chdir=terraform/eks output -raw app_url
```

Verify the returned URL's `/healthz` and actual app page, and verify target
health in ELB before saying deployment is complete. The HTTP link is for a
public demo only, without personal data or sign-in.

## 5. Destroy when finished

1. Remove `jenkins_agent_instance_profile` from root local tfvars, review the
   root plan for only detaching the agent profile, and apply that saved plan.
2. Optionally delete the namespace with laptop admin permissions. Do not create
   a LoadBalancer Service during cleanup.
3. Destroy EKS state, then verify absence of the cluster, managed node EC2,
   root volume, NLB and its target group before claiming billing stopped.

```sh
terraform -chdir=terraform/eks plan -destroy -out=destroy.plan
terraform -chdir=terraform/eks show -no-color destroy.plan
terraform -chdir=terraform/eks apply destroy.plan
aws eks list-clusters --region us-east-1
aws elbv2 describe-load-balancers --region us-east-1 --query 'LoadBalancers[].{Name:LoadBalancerName,ARN:LoadBalancerArn}'
```

Do not delete existing lab resources manually to unblock EKS teardown. Keep the
local EKS state and lock file safe until destroy has completed. The original
three-EC2 lab has its own costs and separate cleanup.

## Vendor references checked 2026-11-23

- https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-versions.html
- https://docs.aws.amazon.com/eks/latest/userguide/view-upgrade-policy.html
- https://docs.aws.amazon.com/eks/latest/userguide/sec-group-reqs.html
- https://docs.aws.amazon.com/eks/latest/userguide/access-policy-permissions.html
- https://docs.aws.amazon.com/elasticloadbalancing/latest/network/load-balancer-target-groups.html
- https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/autoscaling_attachment
- https://aws.amazon.com/eks/pricing/
- https://aws.amazon.com/elasticloadbalancing/pricing/
- https://aws.amazon.com/vpc/pricing/
