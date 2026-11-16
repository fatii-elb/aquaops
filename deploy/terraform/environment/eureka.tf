# Eureka: the registry of services/eureka-server (TECH Eureka, E5).
# The image is NOT in GHCR yet (Images workflow off): it is built from the
# repository, scanned with Trivy, then imported into k3s
# (deploy/terraform/README.md). imagePullPolicy Never: k3s never pulls it
# from a registry, so no token is needed and no public image can replace it.

resource "kubernetes_service_v1" "eureka" {
  count = local.infra

  metadata {
    name      = "eureka"
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.eureka)
  }

  spec {
    selector = local.component_labels.eureka
    port {
      name        = "http"
      port        = 8761
      target_port = "http"
    }
  }
}

resource "kubernetes_deployment_v1" "eureka" {
  count = local.infra

  metadata {
    name      = "eureka"
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.eureka)
  }

  spec {
    replicas = 1

    selector {
      match_labels = local.component_labels.eureka
    }

    template {
      metadata {
        labels = merge(local.labels, local.component_labels.eureka)
      }

      spec {
        enable_service_links            = false
        automount_service_account_token = false

        security_context {
          run_as_non_root = true
          run_as_user     = 999 # user "aquaops" of deploy/docker/spring-service.Dockerfile
          run_as_group    = 999
          seccomp_profile {
            type = "RuntimeDefault"
          }
        }

        container {
          name              = "eureka"
          image             = var.eureka_image
          image_pull_policy = "Never"

          port {
            name           = "http"
            container_port = 8761
          }

          env {
            name  = "EUREKA_HOSTNAME"
            value = "eureka"
          }

          resources {
            requests = { cpu = "50m", memory = "256Mi" }
            limits   = { cpu = "500m", memory = "384Mi" }
          }

          # Read-only root file system, /tmp on an emptyDir (embedded Tomcat).
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

          startup_probe {
            http_get {
              path = "/actuator/health/liveness"
              port = "http"
            }
            period_seconds    = 5
            failure_threshold = 30
          }
          readiness_probe {
            http_get {
              path = "/actuator/health/readiness"
              port = "http"
            }
            period_seconds  = 10
            timeout_seconds = 3
          }
          liveness_probe {
            http_get {
              path = "/actuator/health/liveness"
              port = "http"
            }
            period_seconds    = 20
            timeout_seconds   = 3
            failure_threshold = 3
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
    create = "10m"
    update = "10m"
  }

  depends_on = [terraform_data.infrastructure_inputs]
}
