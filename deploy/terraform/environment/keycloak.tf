# Keycloak: realm "aquaops" imported from docs/keycloak/aquaops-realm.json
# (E2) at every start. TEST ONLY, same mode as Docker Compose: start-dev,
# HTTP, database inside the container (a restart re-imports the realm; users
# created by hand are lost, the 4 test users of the export are not).

resource "kubernetes_config_map_v1" "keycloak_realm" {
  count = local.infra

  metadata {
    name      = "keycloak-realm"
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.keycloak)
  }

  # No secret in this file: the client secret is the placeholder
  # ${KEYCLOAK_AUDIT_CLIENT_SECRET}, replaced by Keycloak at import time.
  data = {
    "aquaops-realm.json" = file("${path.module}/../../../docs/keycloak/aquaops-realm.json")
  }
}

resource "kubernetes_service_v1" "keycloak" {
  count = local.infra

  metadata {
    name      = "keycloak"
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.keycloak)
  }

  spec {
    selector = local.component_labels.keycloak
    port {
      name        = "http"
      port        = 8180
      target_port = "http"
    }
  }
}

resource "kubernetes_deployment_v1" "keycloak" {
  #checkov:skip=CKV_K8S_22:Accepted for test-only start-dev: Keycloak writes to /opt/keycloak; see deploy/terraform/README.md section 7.
  count = local.infra

  metadata {
    name      = "keycloak"
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.keycloak)
  }

  spec {
    replicas = 1

    # One instance at a time: two start-dev instances would not share state.
    strategy {
      type = "Recreate"
    }

    selector {
      match_labels = local.component_labels.keycloak
    }

    template {
      metadata {
        labels = merge(local.labels, local.component_labels.keycloak)
        annotations = {
          "aquaops.ma/realm-sha256" = sha256(kubernetes_config_map_v1.keycloak_realm[0].data["aquaops-realm.json"])
        }
      }

      spec {
        enable_service_links            = false
        automount_service_account_token = false

        security_context {
          run_as_non_root = true
          run_as_user     = 1000 # user "keycloak" of the image
          run_as_group    = 0
          seccomp_profile {
            type = "RuntimeDefault"
          }
        }

        container {
          name  = "keycloak"
          image = var.infrastructure_images.keycloak
          args  = ["start-dev", "--http-port=8180", "--import-realm"]

          port {
            name           = "http"
            container_port = 8180
          }
          port {
            name           = "management"
            container_port = 9000
          }

          env {
            name  = "KC_HEALTH_ENABLED"
            value = "true"
          }
          env {
            name = "KC_BOOTSTRAP_ADMIN_USERNAME"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.keycloak.metadata[0].name
                key  = "KEYCLOAK_ADMIN"
              }
            }
          }
          env {
            name = "KC_BOOTSTRAP_ADMIN_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.keycloak.metadata[0].name
                key  = "KEYCLOAK_ADMIN_PASSWORD"
              }
            }
          }
          # Replaces ${KEYCLOAK_AUDIT_CLIENT_SECRET} in the realm (client
          # aquaops-audit, read by assets-service: same secret, same key).
          env {
            name = "KEYCLOAK_AUDIT_CLIENT_SECRET"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.keycloak.metadata[0].name
                key  = "KEYCLOAK_AUDIT_CLIENT_SECRET"
              }
            }
          }

          resources {
            requests = { cpu = "100m", memory = "512Mi" }
            limits   = { cpu = "1", memory = "768Mi" }
          }

          security_context {
            allow_privilege_escalation = false
            capabilities {
              drop = ["ALL"]
            }
          }

          volume_mount {
            name       = "realm"
            mount_path = "/opt/keycloak/data/import"
            read_only  = true
          }

          startup_probe {
            http_get {
              path = "/health/started"
              port = "management"
            }
            period_seconds    = 5
            failure_threshold = 60
          }
          readiness_probe {
            http_get {
              path = "/health/ready"
              port = "management"
            }
            period_seconds  = 10
            timeout_seconds = 5
          }
          liveness_probe {
            http_get {
              path = "/health/live"
              port = "management"
            }
            period_seconds    = 20
            timeout_seconds   = 5
            failure_threshold = 6
          }
        }

        volume {
          name = "realm"
          config_map {
            name = kubernetes_config_map_v1.keycloak_realm[0].metadata[0].name
          }
        }
      }
    }
  }

  timeouts {
    create = "10m"
    update = "10m"
  }

  depends_on = [terraform_data.infrastructure_inputs]
}
