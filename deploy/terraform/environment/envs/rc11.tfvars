# Environment "rc11": DISPOSABLE copy for RC-11 / NFR-09 (terraform destroy,
# then apply, environment rebuilt without manual action). Namespace
# "aquaops-rc11": own secrets (test values only), own volumes, own state.
# It never touches the "test" namespace nor the Docker Compose volumes.

environment = "rc11"
namespace   = "aquaops-rc11"

# Sized from the sums (infrastructure + 7 services, assets x 2, telemetry x 2,
# PostgreSQL 512 Mi / 1 Gi and simulator 256 Mi / 640 Mi as in postgres.tf and
# services.tf: requests 1.55 CPU / 5120 Mi, limits 12.5 CPU / 9344 Mi) plus:
#   - the telemetry HPA (deploy/k8s/telemetry/service.yaml, 2 to 6 replicas):
#     4 more pods of 200m / 384 Mi (limits 1 CPU / 768 Mi);
#   - room for TWO extra pods: one rolling update at a time
#     (deploy/k3s/deploy-services.sh applies with -parallelism=1) AND one pod
#     stuck from a failed release, so a rollback is never blocked by the quota;
#   - the database Job pod.
# Measured in the sandbox on 19/11/2026 with the former quota (5632 Mi): the
# HPA stopped at 4 replicas, then a deployment of 4572bca AND its automatic
# rollback were refused ("exceeded quota"), exit 3 (docs/preuves/NFR-09).
# NOT schedulable on the VM while Docker Compose keeps 5 GiB: dedicated k3s
# host of 8 vCPU / 16 GB (deploy/vm/README-k3s-runner.md, section 1).
quota = {
  requests_cpu    = "3000m"
  requests_memory = "7680Mi"
  limits_cpu      = "20"
  limits_memory   = "14Gi"
  pods            = 24
  pvcs            = 4
  storage         = "5Gi"
}

container_defaults = {
  default_request_cpu    = "50m"
  default_request_memory = "128Mi"
  default_limit_cpu      = "500m"
  default_limit_memory   = "512Mi"
  max_memory             = "1536Mi"
}

config = {
  AQUAOPS_DISPOSABLE = "true"
}

# No GHCR token stored in Kubernetes (decision of 2026-11-15): public
# infrastructure images, AquaOps images imported into k3s after their scan.
ghcr_pull_secret = false

# Full infrastructure in the disposable environment (RC-11).
infrastructure_enabled = true

# Built from services/eureka-server at commit b496543, scanned (Trivy, no
# CRITICAL) and imported into k3s: see README, "Image Eureka".
eureka_image = "localhost/aquaops/eureka-server:b496543"

# The 7 services, images built from this commit of main (01434e2: includes
# the detection database and JWT security of PR #116), scanned and imported
# into k3s (README, "Images des services"). The gateway reaches assets through
# Eureka, the others at their fixed address until they get the Eureka client.
services_enabled      = true
services_image_tag    = "01434e2"
gateway_eureka_routes = ["assets"]
