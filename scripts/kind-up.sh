#!/usr/bin/env bash
# Creates a kind cluster, loads the image, applies k8s/, waits, curls health and records the result
# in results/kind.txt. Remove the cluster with: kind delete cluster --name loanledger
set -euo pipefail
cd "$(dirname "$0")/.."

CLUSTER=loanledger
# A private kubeconfig, so this never changes your current kubectl context.
export KUBECONFIG="${TMPDIR:-/tmp}/loanledger-kind.kubeconfig"
LOCAL_PORT=28081
METRICS_SERVER=https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.7.2/components.yaml

kind get clusters | grep -qx "$CLUSTER" || kind create cluster --name "$CLUSTER" --wait 120s
kind export kubeconfig --name "$CLUSTER"

# kind imports with --all-platforms, which fails on Docker's attestation manifests.
docker build --provenance=false -t loanledger:local .
kind load docker-image --name "$CLUSTER" loanledger:local

# The HPA needs metrics-server; kind doesn't ship it, and its kubelets use self-signed certificates.
kubectl apply -f "$METRICS_SERVER"
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' || true

# Create the secret only once: Postgres keeps the password it first started with.
kubectl get secret loanledger-db >/dev/null 2>&1 ||
  kubectl create secret generic loanledger-db --from-literal=password="$(openssl rand -hex 16)"

# Postgres first: an app pod that starts before the database is ready exits and costs a restart.
kubectl apply -f k8s/postgres.yaml
kubectl rollout status statefulset/postgres --timeout=180s
kubectl apply -f k8s/configmap.yaml -f k8s/deployment.yaml -f k8s/service.yaml -f k8s/hpa.yaml
kubectl rollout status deployment/loanledger --timeout=300s

kubectl port-forward service/loanledger "$LOCAL_PORT:8080" >/dev/null &
FORWARD=$!
trap 'kill $FORWARD 2>/dev/null' EXIT
for _ in $(seq 1 30); do curl -sf "http://localhost:$LOCAL_PORT/actuator/health" >/dev/null && break; sleep 1; done
HEALTH="$(curl -sf "http://localhost:$LOCAL_PORT/actuator/health" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])')"
LOANS="$(curl -sf "http://localhost:$LOCAL_PORT/api/v1/loans?size=1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["totalElements"])')"
READY="$(kubectl get deployment loanledger -o jsonpath='{.status.readyReplicas}')"
DESIRED="$(kubectl get deployment loanledger -o jsonpath='{.spec.replicas}')"

mkdir -p results
cat > results/kind.txt <<EOF
health: $HEALTH
loans: $LOANS
ready_replicas: $READY/$DESIRED
restarts: $(kubectl get pods -l app=loanledger -o jsonpath='{range .items[*]}{.status.containerStatuses[0].restartCount}{"\n"}{end}' | paste -sd+ - | bc)
kubernetes: $(kubectl version -o json | python3 -c 'import json,sys; print(json.load(sys.stdin)["serverVersion"]["gitVersion"])')
EOF
cat results/kind.txt
[ "$HEALTH" = UP ]
