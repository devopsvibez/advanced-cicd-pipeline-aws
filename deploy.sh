#!/bin/sh
set -eu
umask 077
image=${1:?Usage: sh deploy.sh namespace/repository:40-character-git-sha}
printf '%s\n' "$image" | grep -Eq '^devopsvibezlab/pipeline-lab:[a-f0-9]{40}$' || {
  echo 'Invalid image reference' >&2; exit 1;
}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT HUP INT TERM
export KUBECONFIG="$work/kubeconfig"
# Uses only short-lived credentials from the Jenkins agent instance role.
aws eks update-kubeconfig --region us-east-1 --name project-eks --kubeconfig "$KUBECONFIG"
kubectl auth can-i patch deployments -n pipeline-lab | grep -qx yes
sed "s|IMAGE_PLACEHOLDER|$image|g" k8s/app.yaml > "$work/app.yaml"
kubectl apply --dry-run=server -f "$work/app.yaml"
kubectl apply -f "$work/app.yaml"
kubectl -n pipeline-lab rollout status deployment/pipeline-lab --timeout=180s
kubectl -n pipeline-lab get pods,svc
