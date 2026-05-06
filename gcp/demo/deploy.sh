#!/usr/bin/env bash
# Build api/ and worker/ images, push to Artifact Registry, and roll out Cloud Run.
# Requires: gcloud, docker. Assumes terraform/ has been applied.
set -euo pipefail

PROJECT_ID="${PROJECT_ID:?set PROJECT_ID}"
REGION="${REGION:-asia-east1}"
PREFIX="${NAME_PREFIX:-demo}"
TAG="${TAG:-$(date +%Y%m%d-%H%M%S)}"

REPO="${REGION}-docker.pkg.dev/${PROJECT_ID}/${PREFIX}"
API_IMAGE="${REPO}/api:${TAG}"
WORKER_IMAGE="${REPO}/worker:${TAG}"

echo "==> Configuring docker auth for ${REGION}-docker.pkg.dev"
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet

echo "==> Building api image: ${API_IMAGE}"
docker build -t "${API_IMAGE}" api

echo "==> Building worker image: ${WORKER_IMAGE}"
docker build -t "${WORKER_IMAGE}" worker

echo "==> Pushing images"
docker push "${API_IMAGE}"
docker push "${WORKER_IMAGE}"

echo "==> Rolling out Cloud Run: ${PREFIX}-api"
gcloud run services update "${PREFIX}-api" \
  --image="${API_IMAGE}" \
  --region="${REGION}" \
  --quiet

echo "==> Rolling out Cloud Run: ${PREFIX}-worker"
gcloud run services update "${PREFIX}-worker" \
  --image="${WORKER_IMAGE}" \
  --region="${REGION}" \
  --quiet

API_URL=$(gcloud run services describe "${PREFIX}-api" --region="${REGION}" --format="value(status.url)")
WORKER_URL=$(gcloud run services describe "${PREFIX}-worker" --region="${REGION}" --format="value(status.url)")
echo
echo "Done."
echo "  API URL    : ${API_URL}"
echo "  Worker URL : ${WORKER_URL}"
echo
echo "Try:"
echo "  curl -X POST ${API_URL}/orders -H 'Content-Type: application/json' -d '{\"order_id\":\"A001\",\"amount\":120,\"item\":\"book\"}'"
