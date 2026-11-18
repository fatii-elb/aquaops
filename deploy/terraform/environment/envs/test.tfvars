# Environment "test" on the k3s of the test VM: namespace "aquaops" (the one
# E2's manifests in deploy/k8s/ use). Long-lived: tf.sh refuses to destroy it.
# No secret here: secrets come from TF_VAR_* at apply time (see README).

environment = "test"
namespace   = "aquaops"

# Coexistence phase: the Docker Compose stack (Sprint 4) still runs on the same
# 8 GB VM. Raise these limits only once Compose has been retired (README, "Budget").
quota = {
  requests_cpu    = "1"
  requests_memory = "2Gi"
  limits_cpu      = "3"
  limits_memory   = "3Gi"
  pods            = 20
  pvcs            = 4
  storage         = "10Gi"
}

container_defaults = {
  default_request_cpu    = "50m"
  default_request_memory = "128Mi"
  default_limit_cpu      = "500m"
  default_limit_memory   = "512Mi"
  max_memory             = "1536Mi"
}

# No GHCR token stored in Kubernetes (decision of 2026-11-15): public
# infrastructure images, AquaOps images imported into k3s after their scan.
ghcr_pull_secret = false

# PostgreSQL, Kafka, Keycloak and Eureka stay in Docker Compose on the VM for
# now: not deployed in k3s for "test" (decision of 2026-11-15).
infrastructure_enabled = false

# The services stay in Docker Compose on the VM (Sprint 4 deployment).
services_enabled = false
