# Environment "rc11": DISPOSABLE copy for RC-11 / NFR-09 (terraform destroy,
# then apply, environment rebuilt without manual action). Namespace
# "aquaops-rc11": own secrets (test values only), own volumes, own state.
# It never touches the "test" namespace nor the Docker Compose volumes.

environment = "rc11"
namespace   = "aquaops-rc11"

# Sized from the sums (infrastructure + 7 services, assets x 2, telemetry x 2:
# requests 1.55 CPU / 4800 Mi, limits 12 CPU / 8576 Mi) plus room for TWO
# extra pods: one rolling update at a time (deploy/k3s/deploy-services.sh
# applies with -parallelism=1) AND one pod stuck from a failed release, so a
# rollback is never blocked by the quota (seen in the sandbox with room for
# one pod only: "exceeded quota" during the rollback).
# NOT schedulable on the VM while Docker Compose keeps 5 GiB (deploy/k3s/README.md).
quota = {
  requests_cpu    = "2500m"
  requests_memory = "5632Mi"
  limits_cpu      = "16"
  limits_memory   = "10Gi"
  pods            = 20
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
