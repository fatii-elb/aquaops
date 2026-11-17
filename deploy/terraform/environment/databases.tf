# =============================================================================
# The databases of the services, created if missing (SCRUM-56, E5).
#
# The init script of postgres.tf (deploy/integration/postgres-init, shared with
# the Compose stacks) runs ONLY on an empty volume and creates 4 databases.
# detection-service needs a 5th one since PR #116 (aquaops_detection). This
# Job makes the list true on EVERY volume, new or existing: for each name it
# creates the database only if it does not exist yet (idempotent; never drops
# or alters anything). The Job name carries a hash of the list, so adding a
# database runs it again; an unchanged list leaves it as it is.
#
# Credentials: the aquaops-db Secret (PGUSER/PGPASSWORD), never in the
# command, the plan or the state.
# =============================================================================

locals {
  service_databases = [
    "aquaops_assets",
    "aquaops_detection",
    "aquaops_incidents",
    "aquaops_notifications",
    "aquaops_telemetry",
  ]
  databases_job_name = "postgres-databases-${substr(sha256(join(",", local.service_databases)), 0, 8)}"
}

resource "kubernetes_job_v1" "postgres_databases" {
  count = local.infra

  metadata {
    name      = local.databases_job_name
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.postgres, { "app.kubernetes.io/component" = "database-setup" })
  }

  spec {
    backoff_limit = 10 # PostgreSQL may still be starting: each retry waits a little longer

    template {
      metadata {
        labels = merge(local.labels, { "app.kubernetes.io/name" = "postgres-databases" })
      }

      spec {
        restart_policy                  = "OnFailure"
        enable_service_links            = false
        automount_service_account_token = false

        security_context {
          run_as_non_root = true
          run_as_user     = 999 # user "postgres" of the image
          run_as_group    = 999
          seccomp_profile {
            type = "RuntimeDefault"
          }
        }

        container {
          name  = "create-missing-databases"
          image = var.infrastructure_images.postgres

          # Database names are fixed identifiers from local.service_databases
          # (no user input); the owner comes from the Secret.
          command = ["sh", "-ec", <<-EOT
            for db in ${join(" ", local.service_databases)}; do
              if [ "$(psql -h postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$db'")" = 1 ]; then
                echo "$db: exists"
              else
                psql -h postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$db\" OWNER \"$PGUSER\""
                echo "$db: created"
              fi
            done
          EOT
          ]

          env {
            name = "PGUSER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.db.metadata[0].name
                key  = "DB_USER"
              }
            }
          }
          env {
            name = "PGPASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.db.metadata[0].name
                key  = "DB_PASSWORD"
              }
            }
          }

          resources {
            requests = { cpu = "20m", memory = "32Mi" }
            limits   = { cpu = "200m", memory = "64Mi" }
          }

          security_context {
            allow_privilege_escalation = false
            read_only_root_filesystem  = true
            capabilities {
              drop = ["ALL"]
            }
          }
        }
      }
    }
  }

  wait_for_completion = true

  timeouts {
    create = "5m"
    update = "5m"
  }

  depends_on = [kubernetes_stateful_set_v1.postgres]
}
