# Environment "rc11": DISPOSABLE copy for RC-11 / NFR-09 (terraform destroy,
# then apply, environment rebuilt without manual action). Namespace
# "aquaops-rc11": own secrets (test values only), own volumes, own state.
# It never touches the "test" namespace nor the Docker Compose volumes.

environment = "rc11"
namespace   = "aquaops-rc11"

quota = {
  requests_cpu    = "1"
  requests_memory = "2Gi"
  limits_cpu      = "4"
  limits_memory   = "3Gi"
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
