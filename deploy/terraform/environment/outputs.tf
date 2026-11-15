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
