# Names only, never values: the next layers (infrastructure, services) use them.

output "namespace" {
  value = kubernetes_namespace_v1.this.metadata[0].name
}

output "config_map" {
  value = kubernetes_config_map_v1.aquaops.metadata[0].name
}

output "secret_names" {
  value = {
    db       = kubernetes_secret_v1.db.metadata[0].name
    keycloak = kubernetes_secret_v1.keycloak.metadata[0].name
    ghcr     = var.ghcr_pull_secret ? kubernetes_secret_v1.ghcr[0].metadata[0].name : null
  }
}

output "infrastructure" {
  description = "Service addresses inside the namespace (null when infrastructure_enabled = false)."
  value = var.infrastructure_enabled ? {
    postgres = "${kubernetes_service_v1.postgres[0].metadata[0].name}:5432"
    kafka    = "${kubernetes_service_v1.kafka[0].metadata[0].name}:9092"
    keycloak = "${kubernetes_service_v1.keycloak[0].metadata[0].name}:8180"
    eureka   = "${kubernetes_service_v1.eureka[0].metadata[0].name}:8761"
  } : null
}
