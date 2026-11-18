# =============================================================================
# The AquaOps services of an environment (SCRUM-56 / NFR-09, E5).
# Off unless services_enabled = true (needs infrastructure_enabled).
#
# gateway, detection, incidents, notifications, simulator are described here;
# assets and telemetry come from E2's manifests (e2_services.tf).
# Same images and variables as deploy/test/compose.yml (Sprint 4); Service
# names and ports as in Compose: telemetry:8081 ... gateway:8080.
#
# Images: <services_image_registry>/<name>:<services_image_tag>[@<digest>],
# one tag (the commit SHA) for all, like IMAGE_TAG in the pipeline.
#   localhost/aquaops          imported into k3s after their Trivy scan
#                              (README), pull policy Never, never pulled.
#   ghcr.io/fatii-elb/aquaops  pulled; a sha256 digest per image is REQUIRED
#                              (services_image_digests), so the running image
#                              is byte-for-byte the one the pipeline scanned.
#
# Memory request / limit, from the real use measured on the Compose stack:
#   gateway 256/512 Mi  detection 384/640  incidents 384/640
#   notifications 384/640  simulator 192/384
#   (E2: assets 384/768 x 2, telemetry 384/768 x 2)
# =============================================================================

locals {
  svc = var.services_enabled ? 1 : 0

  service_ports = {
    gateway       = 8080
    telemetry     = 8081
    assets        = 8082
    detection     = 8083
    incidents     = 8084
    notifications = 8085
    simulator     = 8086
  }

  service_image = {
    for name in keys(local.service_ports) : name => join("", [
      "${var.services_image_registry}/${name}:${coalesce(var.services_image_tag, "unset")}",
      contains(keys(var.services_image_digests), name) ? "@${var.services_image_digests[name]}" : "",
    ])
  }

  # Imported images are never pulled: no token, and no public image can
  # replace them.
  service_pull_policy = var.services_image_registry == "localhost/aquaops" ? "Never" : "IfNotPresent"

  # Gateway (profile "eureka"): a route listed in gateway_eureka_routes goes to
  # lb://<service name>; every other route keeps its fixed *_URL.
  gateway_fixed_routes = {
    for name in ["telemetry", "assets", "detection", "incidents", "notifications", "simulator"] :
    "${upper(name)}_URL" => "http://${name}:${local.service_ports[name]}"
    if !contains(var.gateway_eureka_routes, name)
  }

  services = {
    gateway = {
      memory      = ["256Mi", "512Mi"]
      env         = {} # routing: ConfigMap gateway-routes (below)
      from_config = ["EUREKA_URL", "TELEMETRY_INGEST_RATE_LIMIT"]
      db          = false
      ingress     = true
    }
    # Since PR #116: anomalies stored in PostgreSQL (JPA) and an API secured
    # by Keycloak JWTs (same issuer as assets).
    detection = {
      memory      = ["384Mi", "640Mi"]
      env         = { DETECTION_DB_URL = "jdbc:postgresql://$(DB_HOST):$(DB_PORT)/aquaops_detection" }
      from_config = ["DB_HOST", "DB_PORT", "KAFKA_BOOTSTRAP_SERVERS", "KEYCLOAK_ISSUER_URI"]
      db          = true
      ingress     = false
    }
    incidents = {
      memory      = ["384Mi", "640Mi"]
      env         = { INCIDENTS_DB_URL = "jdbc:postgresql://$(DB_HOST):$(DB_PORT)/aquaops_incidents" }
      from_config = ["DB_HOST", "DB_PORT", "KAFKA_BOOTSTRAP_SERVERS"]
      db          = true
      ingress     = false
    }
    notifications = {
      memory = ["384Mi", "640Mi"]
      env = {
        NOTIFICATIONS_DB_URL = "jdbc:postgresql://$(DB_HOST):$(DB_PORT)/aquaops_notifications"
        SPRING_JPA_SHOW_SQL  = "false"
      }
      from_config = ["DB_HOST", "DB_PORT", "KAFKA_BOOTSTRAP_SERVERS"]
      db          = true
      ingress     = false
    }
    simulator = {
      memory      = ["192Mi", "384Mi"]
      env         = {}
      from_config = ["GATEWAY_URL", "EUREKA_ENABLED", "EUREKA_URL"]
      db          = false
      ingress     = false
    }
  }

  deployed_services = var.services_enabled ? local.services : {}
}

# Gateway routing, visible with "kubectl get cm gateway-routes -o yaml".
# Profile "eureka": a route listed in gateway_eureka_routes goes to
# lb://<spring.application.name> (application-eureka.yml defaults); every other
# route keeps its fixed address, exactly as deploy/test/compose.yml.
resource "kubernetes_config_map_v1" "gateway_routes" {
  count = local.svc

  metadata {
    name      = "gateway-routes"
    namespace = local.ns
    labels    = merge(local.labels, { app = "gateway", "app.kubernetes.io/name" = "gateway" })
  }

  data = merge({
    SPRING_PROFILES_ACTIVE = "eureka"
    # Published service: health only (same as deploy/test/compose.yml).
    MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = "health"
  }, local.gateway_fixed_routes)
}

resource "terraform_data" "services_inputs" {
  count = local.svc

  lifecycle {
    precondition {
      condition     = var.infrastructure_enabled
      error_message = "services_enabled = true needs infrastructure_enabled = true (PostgreSQL, Kafka, Keycloak, Eureka)."
    }
    precondition {
      condition     = var.services_image_tag != null
      error_message = "services_enabled = true needs services_image_tag (commit SHA of the imported images)."
    }
    precondition {
      condition = (var.services_image_registry == "localhost/aquaops"
      || alltrue([for name in keys(local.service_ports) : contains(keys(var.services_image_digests), name)]))
      error_message = "A registry image is pulled: services_image_digests must give the sha256 digest of all 7 images (immutable reference)."
    }
  }
}

resource "kubernetes_service_v1" "service" {
  for_each = local.deployed_services

  metadata {
    name      = each.key
    namespace = local.ns
    labels    = merge(local.labels, { app = each.key, "app.kubernetes.io/name" = each.key })
  }

  spec {
    selector = { app = each.key }
    port {
      name        = "http"
      port        = local.service_ports[each.key]
      target_port = "http"
    }
  }
}

resource "kubernetes_deployment_v1" "service" {
  #checkov:skip=CKV_K8S_12:False positive: limits come from local.services (for_each); verified on the deployed pods.
  #checkov:skip=CKV_K8S_13:False positive: requests come from local.services (for_each); verified on the deployed pods.
  #checkov:skip=CKV_K8S_14:False positive: the tag is services_image_tag, a validated commit SHA, never latest.
  #checkov:skip=CKV_K8S_15:Accepted: imported images use pull policy Never (no registry token in the cluster); see README 9.
  #checkov:skip=CKV_K8S_43:Accepted for imported images (no registry digest); a registry pull requires services_image_digests.
  for_each = local.deployed_services

  metadata {
    name      = each.key
    namespace = local.ns
    labels    = merge(local.labels, { app = each.key, "app.kubernetes.io/name" = each.key })
  }

  spec {
    replicas               = 1
    revision_history_limit = 5

    # A new pod must be Ready before the old one goes: no gap during an update.
    strategy {
      type = "RollingUpdate"
      rolling_update {
        max_unavailable = 0
        max_surge       = 1
      }
    }

    selector {
      match_labels = { app = each.key }
    }

    template {
      metadata {
        labels = merge(
          local.labels,
          { app = each.key, "app.kubernetes.io/name" = each.key },
          each.value.ingress ? { "aquaops.ma/ingress" = "true" } : {},
        )
        # A routing change restarts the gateway (env is read at start only).
        annotations = each.key == "gateway" ? {
          "aquaops.ma/routes-sha256" = sha256(jsonencode(kubernetes_config_map_v1.gateway_routes[0].data))
        } : {}
      }

      spec {
        enable_service_links             = false
        automount_service_account_token  = false
        termination_grace_period_seconds = 40

        security_context {
          run_as_non_root = true
          run_as_user     = 999 # user "aquaops" of deploy/docker/spring-service.Dockerfile
          run_as_group    = 999
          seccomp_profile {
            type = "RuntimeDefault"
          }
        }

        container {
          name              = each.key
          image             = local.service_image[each.key]
          image_pull_policy = local.service_pull_policy

          port {
            name           = "http"
            container_port = local.service_ports[each.key]
          }

          dynamic "env" {
            # Before the literal values: $(DB_HOST) below is expanded by Kubernetes
            # only from variables defined earlier in the list.
            for_each = each.value.from_config
            content {
              name = env.value
              value_from {
                config_map_key_ref {
                  name = kubernetes_config_map_v1.aquaops.metadata[0].name
                  key  = env.value
                }
              }
            }
          }

          dynamic "env" {
            for_each = each.value.env
            content {
              name  = env.key
              value = env.value
            }
          }

          dynamic "env_from" {
            for_each = each.key == "gateway" ? [1] : []
            content {
              config_map_ref {
                name = kubernetes_config_map_v1.gateway_routes[0].metadata[0].name
              }
            }
          }

          dynamic "env" {
            for_each = each.value.db ? toset(["DB_USER", "DB_PASSWORD"]) : toset([])
            content {
              name = env.value
              value_from {
                secret_key_ref {
                  name = kubernetes_secret_v1.db.metadata[0].name
                  key  = env.value
                }
              }
            }
          }

          dynamic "env" {
            for_each = contains(["gateway", "simulator"], each.key) ? [1] : []
            content {
              name = "SIMULATOR_API_KEY"
              value_from {
                secret_key_ref {
                  name = kubernetes_secret_v1.simulator[0].metadata[0].name
                  key  = "SIMULATOR_API_KEY"
                }
              }
            }
          }

          resources {
            requests = { cpu = "100m", memory = each.value.memory[0] }
            limits   = { cpu = "1", memory = each.value.memory[1] }
          }

          security_context {
            allow_privilege_escalation = false
            read_only_root_filesystem  = true
            capabilities {
              drop = ["ALL"]
            }
          }

          volume_mount {
            name       = "tmp"
            mount_path = "/tmp"
          }

          # Spring Boot exposes the Kubernetes probe groups when it runs in a pod.
          startup_probe {
            http_get {
              path = "/actuator/health/liveness"
              port = "http"
            }
            period_seconds    = 5
            failure_threshold = 36
          }
          readiness_probe {
            http_get {
              path = "/actuator/health/readiness"
              port = "http"
            }
            period_seconds  = 5
            timeout_seconds = 3
          }
          liveness_probe {
            http_get {
              path = "/actuator/health/liveness"
              port = "http"
            }
            period_seconds    = 10
            timeout_seconds   = 3
            failure_threshold = 3
          }

          # Lets the Service (and Eureka) drop the pod before it stops.
          lifecycle {
            pre_stop {
              exec {
                command = ["sleep", "5"]
              }
            }
          }
        }

        volume {
          name = "tmp"
          empty_dir {
            size_limit = "64Mi"
          }
        }
      }
    }
  }

  timeouts {
    create = var.services_rollout_timeout
    update = var.services_rollout_timeout
  }

  depends_on = [
    terraform_data.services_inputs,
    kubernetes_stateful_set_v1.postgres,
    kubernetes_stateful_set_v1.kafka,
    kubernetes_deployment_v1.keycloak,
    kubernetes_deployment_v1.eureka,
    kubernetes_job_v1.postgres_databases,
  ]
}
