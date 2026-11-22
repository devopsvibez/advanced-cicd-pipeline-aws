#!/bin/sh
set -eu
# Only accepts a Docker Hub image with a full Git SHA tag.
image=${1:?Usage: sh deploy.sh namespace/repository:40-character-git-sha}
printf '%s\n' "$image" | grep -Eq '^[a-z0-9][a-z0-9_-]*/[a-z0-9][a-z0-9_.-]*:[a-f0-9]{40}$' || {
  echo 'Invalid image reference' >&2; exit 1;
}
kubectl config current-context
rendered=$(mktemp)
trap 'rm -f "$rendered"' EXIT HUP INT TERM
sed "s|IMAGE_PLACEHOLDER|$image|g" k8s/app.yaml > "$rendered"
kubectl apply --dry-run=client -f "$rendered"
kubectl apply -f "$rendered"
kubectl -n pipeline-lab rollout status deployment/pipeline-lab --timeout=120s
kubectl -n pipeline-lab get pods,svc
