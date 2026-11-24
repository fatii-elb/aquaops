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
# CPU raised for telemetry at 500m / 2 CPU (proposal of E2, branch
# feature/SCRUM-52-telemetry-cpu): at 200m / 1 a JVM start (about 36 CPU
# seconds) was throttled 96 % of the time and RC-05 failed; at 500m / 2 RC-05
# passed in the sandbox (24/11/2026, 31 s, 0 loss) with a quota of 4500m / 26.
# Sizing: telemetry at 6 replicas, usage measured at 200m / 1 = 2350m / 18.5
# CPU; at 500m / 2: 4150m / 24.5; plus two extra telemetry pods (rolling
# update + one stuck pod, as above): + 1000m / + 4 -> 5200m / 29 (rounded).
# 4500m / 26 left no room for a rollout at 6 replicas. 5200m / 29 itself has
# NOT been run in the sandbox yet.
# ORDER: merge and deploy this quota BEFORE the telemetry CPU change. To lower
# it later: shrink the pods first, lower the quota in a second deployment,
# otherwise new pods are refused (24/11/2026: kafka-0 "exceeded quota").
quota = {
  requests_cpu    = "5200m"
  requests_memory = "7680Mi"
  limits_cpu      = "29"
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
