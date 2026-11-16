output "cert_manager" {
  value = var.cert_manager_enabled ? "${helm_release.cert_manager[0].name} ${helm_release.cert_manager[0].version} (${helm_release.cert_manager[0].status})" : "disabled"
}
