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

output "services" {
  description = "The 7 services and their address inside the namespace (null when services_enabled = false)."
  value = var.services_enabled ? merge(
    { for name, svc in kubernetes_service_v1.service : name => "${svc.metadata[0].name}:${local.service_ports[name]}" },
    { for name in local.e2_services : name => "${name}:${local.service_ports[name]}" },
  ) : null
}

output "services_images" {
  description = "Image reference of each service. The deploy script reads it before an update to know what to roll back to."
  value       = var.services_enabled ? { for name in keys(local.service_ports) : name => local.service_image[name] } : null
}

output "services_image_tag" {
  description = "Commit SHA currently deployed (null when services_enabled = false)."
  value       = var.services_enabled ? var.services_image_tag : null
}

output "gateway_routes" {
  description = "How the gateway reaches each service: lb:// (Eureka) or a fixed address."
  value = var.services_enabled ? merge(
    { for name in var.gateway_eureka_routes : name => "lb://${name == "simulator" ? "simulator" : "${name}-service"}" },
    { for k, v in local.gateway_fixed_routes : lower(trimsuffix(k, "_URL")) => v },
  ) : null
}
