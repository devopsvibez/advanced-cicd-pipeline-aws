# Chapter & Co. CI/CD lab

A Java 21 bookstore demo and an AWS delivery pipeline. The storefront has a home page, a searchable book collection, individual book pages and an about page. Prices are sample values. There is no checkout, payment processing, sign-in or customer-data storage.

## Architecture

```text
Laptop Terraform -> Ansible controller -> Jenkins master + Docker/Maven agent
GitHub multibranch -> Maven HTTP checks + coverage -> SonarCloud gate
Trusted branch -> Docker image + smoke test -> Docker Hub -> Amazon EKS
Public HTTP -> Terraform-managed NLB -> NodePort 30080 -> one application replica
```

The initial EC2 lab and the EKS lab have separate Terraform state. SonarCloud Free replaces a separate Sonar server. Docker Hub is the temporary image registry. No monitoring stack is deployed. The application still exposes a small `/metrics` endpoint for learning.

## Run locally

Requires Java 21 and Maven.

```sh
mvn -B clean verify
java -jar target/pipeline-lab-1.0.0-SNAPSHOT.jar
```

Open `http://localhost:8080`. Fourteen HTTP checks cover health, metrics and bookstore routes. JaCoCo reports are written to `target/site/jacoco/`.

## Jenkins setup

Use a multibranch Pipeline with an agent labelled `docker-agent`. The agent needs Java 21, Maven, Docker, curl, Git, AWS CLI v2 and kubectl compatible with the selected EKS version. Keep Jenkins 8080 restricted to the operator's IP. Do not expose it worldwide for webhooks.

Install Pipeline, Git, GitHub Branch Source, Credentials Binding, SonarQube Scanner and Timestamper support. Configure SonarQube server name `SonarCloud`, URL `https://sonarcloud.io`, secret-text credential ID `sonarcloud-token`. Set Docker Hub username/password credential ID `dockerhub`, username `devopsvibezlab`, with a short-lived Read & Write access token as password. Never put tokens, AWS keys, state or kubeconfig in Git.

During setup, suppress automatic SCM builds for indexing and events. Discover only origin PRs, not fork PRs, and filter `maven-pipeline PR-*`. Do not start the first build until the credentials, tools and deployment plan are ready.

### Safe first-run order

1. Before attaching the EKS instance role, run PR 1 against main. It runs Maven checks, SonarCloud quality gate, container build and smoke test. PRs do not publish or deploy.
2. Verify the PR quality gate passes. Then disable PR discovery/building on this agent. Only now attach the namespace-scoped EKS role using the reviewed Terraform plan.
3. Run the trusted `maven-pipeline` branch explicitly with PUBLISH and DEPLOY enabled. It uses the same commit that passed the PR gate, publishes a full-Git-SHA image tag and deploys it.
4. Verify rollout, NLB target health, `/healthz` and the rendered bookstore. Merge into main only after this full pipeline succeeds. Switch the branch filter to main and keep PR code away from the role-bearing agent.

Stage guards are not a security boundary: code running on an EC2 agent can access its instance-role credentials. Future PR analysis needs a separate credential-free agent. Do not re-enable PR jobs on the deployment agent.

SonarCloud Free analyzes main and PRs targeting main. The trusted pre-merge `maven-pipeline` run intentionally skips Sonar analysis, relying on the preceding PR gate for the exact commit. Main builds analyze main after merge. Quality-gate waiting uses scanner polling rather than a Jenkins webhook.

## Registry and deployment

The public demo-image repository is `devopsvibezlab/pipeline-lab`. Only the built demo artifact belongs there, never secrets or private data. The Docker build context excludes local Terraform state and credentials. Images use immutable full commit SHA tags, not `latest`.

`deploy.sh` creates a mode-restricted temporary kubeconfig using the agent's short-lived instance-role credentials and deletes it on exit. The laptop admin must create `pipeline-lab` beforehand. Jenkins has edit rights only inside that namespace, not namespace creation or cluster-admin rights.

See [EKS setup, plan review and cleanup](terraform/eks/README.md) for the four required inputs, one-node configuration and public NLB. The Kubernetes manifest deliberately uses NodePort, not a LoadBalancer Service, to avoid creating a second billable load balancer.

## Cost and cleanup

AWS resources cost money while idle and during failures. EKS adds a standard-support control plane, one t3.small worker with 20 GiB gp3, public IPv4 and a two-AZ NLB. The planning estimate is about $0.16/hour additional baseline, plus usage, transfer and tax, not a hard cap. The original three EC2 instances cost extra.

Review every saved plan before apply. No replacement of the existing lab should be accepted merely to attach a role. Do not run root Terraform destroy while EKS still depends on that VPC. Follow EKS cleanup first, verify resources are gone, then handle the original lab separately. Do not claim billing stopped merely because a command returned.

## Validation status

The application and coverage-enabled Maven build have passed local checks. The initial end-to-end Jenkins, image push and EKS rollout still require live validation. This repository is a learning lab, not a production-ready or highly available deployment.
