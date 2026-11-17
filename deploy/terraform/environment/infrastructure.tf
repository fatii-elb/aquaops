# =============================================================================
# Infrastructure of an environment: PostgreSQL, Kafka, Keycloak, Eureka
# (SCRUM-56 / NFR-09, E5). Off unless infrastructure_enabled = true.
#
# Native Kubernetes resources, official images (the tags Docker Compose uses,
# pinned by digest). Service names = the addresses E2's manifests and the
# aquaops-config ConfigMap already use: postgres:5432, kafka:9092,
# keycloak:8180, eureka:8761.
#
# Memory (request / limit), sized for the 8 GB VM shared with Docker Compose:
#   PostgreSQL 256 / 512 Mi   Kafka 640 Mi / 1 Gi   Keycloak 512 / 768 Mi
#   Eureka 256 / 384 Mi       total 1.6 / 2.6 Gi (namespace quota: 2 / 3 Gi)
# =============================================================================

locals {
  infra = var.infrastructure_enabled ? 1 : 0
  ns    = kubernetes_namespace_v1.this.metadata[0].name

  component_labels = {
    for c in ["postgres", "kafka", "keycloak", "eureka"] : c => {
      "app.kubernetes.io/name"      = c
      "app.kubernetes.io/component" = "infrastructure"
    }
  }
}

resource "terraform_data" "infrastructure_inputs" {
  count = local.infra

  lifecycle {
    precondition {
      condition     = var.eureka_image != null
      error_message = "infrastructure_enabled = true needs eureka_image (built, scanned and imported into k3s: deploy/terraform/README.md)."
    }
  }
}
