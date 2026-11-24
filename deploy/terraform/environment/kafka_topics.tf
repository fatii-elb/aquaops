# =============================================================================
# sensor-readings with 6 partitions (RC-04 / NFR-04, E5).
#
# With 1 partition only ONE telemetry pod consumes, whatever the HPA does:
# in the sandbox (23/11/2026, 500 readings/s for 5 min) storage stayed at
# 220-300 rows/s while ingestion reached 338/s and the Kafka lag grew to
# 26 000. 6 partitions = the HPA maximum: up to 6 pods consume in parallel.
#
# Only this topic: the broker default stays 1 partition (KAFKA_NUM_PARTITIONS
# in kafka.tf), so the ordering of incident-events and anomaly-detected is not
# changed. Per-sensor ordering is kept: telemetry sends with the sensor id as
# the key, so one sensor always goes to the same partition. No duplicate:
# uk_readings_source includes the partition, uk_readings_reading_id does not
# depend on it.
#
# Idempotent: creates the topic with 6 partitions if missing, raises it to 6
# if it has fewer (Kafka never reduces partitions), otherwise changes nothing.
# The Job name carries a hash of its settings: changing them runs it again.
# =============================================================================

locals {
  kafka_topic_partitions = {
    "sensor-readings" = 6
  }
  kafka_topics_job_name = "kafka-topics-${substr(sha256(jsonencode(local.kafka_topic_partitions)), 0, 8)}"
}

resource "kubernetes_job_v1" "kafka_topics" {
  count = local.infra

  metadata {
    name      = local.kafka_topics_job_name
    namespace = local.ns
    labels    = merge(local.labels, local.component_labels.kafka, { "app.kubernetes.io/component" = "topic-setup" })
  }

  spec {
    backoff_limit = 10 # the broker may still be starting: each retry waits a little longer

    template {
      metadata {
        labels = merge(local.labels, { "app.kubernetes.io/name" = "kafka-topics" })
      }

      spec {
        restart_policy                  = "OnFailure"
        enable_service_links            = false
        automount_service_account_token = false

        security_context {
          run_as_non_root = true
          run_as_user     = 1000 # appuser in apache/kafka
          run_as_group    = 1000
          seccomp_profile {
            type = "RuntimeDefault"
          }
        }

        container {
          name  = "ensure-partitions"
          image = var.infrastructure_images.kafka

          # Topic names and counts are fixed values from local.kafka_topic_partitions.
          command = ["sh", "-ec", <<-EOT
            T=/opt/kafka/bin/kafka-topics.sh; B=kafka:9092
            %{for topic, partitions in local.kafka_topic_partitions~}
            n="$($T --bootstrap-server $B --describe --topic ${topic} 2> /dev/null | sed -n 's/.*PartitionCount: *\([0-9]*\).*/\1/p' | head -n 1)"
            if [ -z "$n" ]; then
              $T --bootstrap-server $B --create --if-not-exists --topic ${topic} --partitions ${partitions} --replication-factor 1
              echo "${topic}: created with ${partitions} partitions"
            elif [ "$n" -lt ${partitions} ]; then
              $T --bootstrap-server $B --alter --topic ${topic} --partitions ${partitions}
              echo "${topic}: $n -> ${partitions} partitions"
            else
              echo "${topic}: $n partitions, nothing to do"
            fi
            %{endfor~}
          EOT
          ]

          env {
            name  = "KAFKA_HEAP_OPTS"
            value = "-Xms64m -Xmx128m"
          }
          env {
            name  = "LOG_DIR" # kafka-run-class.sh writes its log files here
            value = "/tmp"
          }

          resources {
            requests = { cpu = "50m", memory = "192Mi" }
            limits   = { cpu = "500m", memory = "256Mi" }
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

  wait_for_completion = true

  timeouts {
    create = "5m"
    update = "5m"
  }

  depends_on = [kubernetes_stateful_set_v1.kafka]
}
