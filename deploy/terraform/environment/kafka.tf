# Kafka: one broker in KRaft mode (broker + controller), data on a persistent
# volume. Same image and settings as deploy/test/compose.infra.yml, including
# the SCRUM-55 health check fix (small heap for the probe JVM).

locals {
  kafka_env = {
    KAFKA_NODE_ID                                  = "1"
    KAFKA_PROCESS_ROLES                            = "broker,controller"
    KAFKA_LISTENERS                                = "PLAINTEXT://:9092,CONTROLLER://:9093"
    KAFKA_ADVERTISED_LISTENERS                     = "PLAINTEXT://kafka:9092"
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP           = "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT"
    KAFKA_CONTROLLER_LISTENER_NAMES                = "CONTROLLER"
    KAFKA_CONTROLLER_QUORUM_VOTERS                 = "1@localhost:9093"
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR         = "1"
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR = "1"
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR            = "1"
    KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS         = "0"
    KAFKA_NUM_PARTITIONS                           = "1"
    KAFKA_AUTO_CREATE_TOPICS_ENABLE                = "true"
    KAFKA_LOG_DIRS                                 = "/var/lib/kafka/data"
    KAFKA_HEAP_OPTS                                = "-Xms512m -Xmx512m"
  }
}

resource "kubernetes_service_v1" "kafka" {
  count = local.infra

  metadata {
    name      = "kafka"
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.kafka)
  }

  spec {
    selector = local.component_labels.kafka
    port {
      name        = "kafka"
      port        = 9092
      target_port = "kafka"
    }
  }
}

resource "kubernetes_stateful_set_v1" "kafka" {
  count = local.infra

  metadata {
    name      = "kafka"
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.kafka)
  }

  spec {
    service_name = kubernetes_service_v1.kafka[0].metadata[0].name
    replicas     = 1

    selector {
      match_labels = local.component_labels.kafka
    }

    template {
      metadata {
        labels = merge(local.labels, local.component_labels.kafka)
      }

      spec {
        # REQUIRED for Kafka: the image turns every KAFKA_* variable into a
        # broker setting, and the Service "kafka" would inject KAFKA_PORT=tcp://...
        enable_service_links            = false
        automount_service_account_token = false

        security_context {
          run_as_non_root = true
          run_as_user     = 1000 # appuser in apache/kafka
          run_as_group    = 1000
          fs_group        = 1000
          seccomp_profile {
            type = "RuntimeDefault"
          }
        }

        container {
          name  = "kafka"
          image = var.infrastructure_images.kafka

          port {
            name           = "kafka"
            container_port = 9092
          }

          dynamic "env" {
            for_each = local.kafka_env
            content {
              name  = env.key
              value = env.value
            }
          }

          resources {
            requests = { cpu = "200m", memory = "640Mi" }
            limits   = { cpu = "1", memory = "1Gi" }
          }

          security_context {
            allow_privilege_escalation = false
            capabilities {
              drop = ["ALL"]
            }
          }

          volume_mount {
            name       = "data"
            mount_path = "/var/lib/kafka/data"
          }

          startup_probe {
            tcp_socket {
              port = "kafka"
            }
            period_seconds    = 5
            failure_threshold = 36
          }
          # TCP, not a client JVM. The former exec probe
          # (kafka-broker-api-versions.sh every 15 s) ran inside the broker's
          # own 1-CPU limit: one run took 18-21 s and as much CPU time, runs
          # overlapped, the idle broker sat at ~970m and was throttled 30 % of
          # the time, and producers timed out under load (max.block.ms) in
          # RC-04 / RC-05 (sandbox, 23/11/2026, docs/preuves/RC-04).
          readiness_probe {
            tcp_socket {
              port = "kafka"
            }
            period_seconds    = 10
            failure_threshold = 3
          }
          liveness_probe {
            tcp_socket {
              port = "kafka"
            }
            period_seconds    = 20
            failure_threshold = 3
          }
        }
      }
    }

    volume_claim_template {
      metadata {
        name   = "data"
        labels = merge(local.labels, local.component_labels.kafka)
      }
      spec {
        access_modes       = ["ReadWriteOnce"]
        storage_class_name = "local-path"
        resources {
          requests = { storage = var.infrastructure_storage.kafka }
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
