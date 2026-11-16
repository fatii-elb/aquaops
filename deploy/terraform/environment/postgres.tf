# PostgreSQL: one instance, the 4 databases of the services, data on a
# persistent volume. Same image and init script as deploy/integration and
# deploy/test (Compose).

resource "kubernetes_config_map_v1" "postgres_init" {
  count = local.infra

  metadata {
    name      = "postgres-init"
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.postgres)
  }

  # Run once, on the first start of an EMPTY volume: creates aquaops_assets,
  # aquaops_incidents, aquaops_notifications, aquaops_telemetry.
  data = {
    "01-databases.sh" = file("${path.module}/../../integration/postgres-init/01-databases.sh")
  }
}

resource "kubernetes_service_v1" "postgres" {
  count = local.infra

  metadata {
    name      = "postgres"
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.postgres)
  }

  spec {
    selector = local.component_labels.postgres
    port {
      name        = "postgres"
      port        = 5432
      target_port = "postgres"
    }
  }
}

resource "kubernetes_stateful_set_v1" "postgres" {
  count = local.infra

  metadata {
    name      = "postgres"
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.postgres)
  }

  spec {
    service_name = kubernetes_service_v1.postgres[0].metadata[0].name
    replicas     = 1

    selector {
      match_labels = local.component_labels.postgres
    }

    template {
      metadata {
        labels = merge(local.labels, local.component_labels.postgres)
        annotations = {
          "aquaops.ma/init-sha256" = sha256(kubernetes_config_map_v1.postgres_init[0].data["01-databases.sh"])
        }
      }

      spec {
        # No <SERVICE>_PORT variables injected by Kubernetes.
        enable_service_links            = false
        automount_service_account_token = false

        # Runs directly as user postgres (999): the entrypoint then needs no
        # root step and no extra capability.
        security_context {
          run_as_non_root = true
          run_as_user     = 999
          run_as_group    = 999
          fs_group        = 999
          seccomp_profile {
            type = "RuntimeDefault"
          }
        }

        container {
          name  = "postgres"
          image = var.infrastructure_images.postgres

          port {
            name           = "postgres"
            container_port = 5432
          }

          env {
            name = "POSTGRES_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.db.metadata[0].name
                key  = "DB_USER"
              }
            }
          }
          env {
            name = "POSTGRES_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.db.metadata[0].name
                key  = "DB_PASSWORD"
              }
            }
          }

          resources {
            requests = { cpu = "100m", memory = "256Mi" }
            limits   = { cpu = "500m", memory = "512Mi" }
          }

          # Read-only image; writable: the data volume, the socket folder, /tmp.
          security_context {
            allow_privilege_escalation = false
            read_only_root_filesystem  = true
            capabilities {
              drop = ["ALL"]
            }
          }

          # PostgreSQL 18: the volume is mounted on /var/lib/postgresql.
          volume_mount {
            name       = "data"
            mount_path = "/var/lib/postgresql"
          }
          volume_mount {
            name       = "init"
            mount_path = "/docker-entrypoint-initdb.d"
            read_only  = true
          }
          volume_mount {
            name       = "run"
            mount_path = "/var/run/postgresql"
          }
          volume_mount {
            name       = "tmp"
            mount_path = "/tmp"
          }

          startup_probe {
            exec {
              command = ["sh", "-c", "pg_isready -U \"$POSTGRES_USER\" -d postgres"]
            }
            period_seconds    = 5
            failure_threshold = 30
          }
          readiness_probe {
            exec {
              command = ["sh", "-c", "pg_isready -U \"$POSTGRES_USER\" -d postgres"]
            }
            period_seconds  = 10
            timeout_seconds = 5
          }
          liveness_probe {
            exec {
              command = ["sh", "-c", "pg_isready -U \"$POSTGRES_USER\" -d postgres"]
            }
            period_seconds    = 20
            timeout_seconds   = 5
            failure_threshold = 6
          }
        }

        volume {
          name = "init"
          config_map {
            name = kubernetes_config_map_v1.postgres_init[0].metadata[0].name
          }
        }
        volume {
          name = "run"
          empty_dir {
            size_limit = "16Mi"
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

    volume_claim_template {
      metadata {
        name   = "data"
        labels = merge(local.labels, local.component_labels.postgres)
      }
      spec {
        access_modes       = ["ReadWriteOnce"]
        storage_class_name = "local-path"
        resources {
          requests = { storage = var.infrastructure_storage.postgres }
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
