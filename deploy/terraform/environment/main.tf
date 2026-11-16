# =============================================================================
# AquaOps on k3s: one ENVIRONMENT = one namespace (SCRUM-56 / NFR-09, E5).
#
# This layer prepares the namespace the workloads will run in: limits,
# network isolation, shared configuration, secrets and GHCR access. The
# workloads (PostgreSQL, Kafka, Keycloak, Eureka, the 7 services) come in the
# next PRs and reuse the names defined here (see outputs.tf).
#
# Run it through deploy/terraform/tf.sh, never directly: the wrapper checks
# the kubeconfig, selects the workspace and refuses to destroy "test".
# =============================================================================

locals {
  labels = {
    "app.kubernetes.io/part-of"    = "aquaops"
    "app.kubernetes.io/managed-by" = "terraform"
    "aquaops.ma/environment"       = var.environment
  }

  # Addresses as seen from inside the namespace (Kubernetes Service names),
  # the same names as deploy/test/compose.yml and E2's manifests.
  config = merge({
    AQUAOPS_ENVIRONMENT     = var.environment
    DB_HOST                 = "postgres"
    DB_PORT                 = "5432"
    KAFKA_BOOTSTRAP_SERVERS = "kafka:9092"
    EUREKA_ENABLED          = "true"
    EUREKA_URL              = "http://eureka:8761/eureka"
    KEYCLOAK_BASE_URL       = "http://keycloak:8180"
    # Must equal the "iss" of the tokens: to agree with E2 (single issuer URL).
    KEYCLOAK_ISSUER_URI = "http://keycloak:8180/realms/aquaops"
  }, var.config)
}

# -----------------------------------------------------------------------------
# Namespace
# -----------------------------------------------------------------------------

resource "kubernetes_namespace_v1" "this" {
  metadata {
    name = var.namespace
    labels = merge(local.labels, {
      # Pod Security Admission: block privileged pods, report what is not
      # "restricted" yet (E2's assets Deployment has no runAsNonRoot).
      "pod-security.kubernetes.io/enforce" = "baseline"
      "pod-security.kubernetes.io/warn"    = "restricted"
      "pod-security.kubernetes.io/audit"   = "restricted"
    })
  }

  timeouts {
    delete = "10m"
  }

  lifecycle {
    # One workspace per environment: a "rc11" plan can never touch "test".
    precondition {
      condition     = terraform.workspace == var.environment
      error_message = "Workspace \"${terraform.workspace}\" does not match environment \"${var.environment}\". Use deploy/terraform/tf.sh."
    }
  }
}

# -----------------------------------------------------------------------------
# Resources: the namespace can never take more than its share of the VM
# -----------------------------------------------------------------------------

resource "kubernetes_resource_quota_v1" "this" {
  metadata {
    name      = "aquaops-quota"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  spec {
    hard = {
      "requests.cpu"           = var.quota.requests_cpu
      "requests.memory"        = var.quota.requests_memory
      "limits.cpu"             = var.quota.limits_cpu
      "limits.memory"          = var.quota.limits_memory
      "pods"                   = var.quota.pods
      "persistentvolumeclaims" = var.quota.pvcs
      "requests.storage"       = var.quota.storage
      # No NodePort or LoadBalancer: entry only through the Ingress (Traefik).
      "services.nodeports"     = 0
      "services.loadbalancers" = 0
    }
  }
}

resource "kubernetes_limit_range_v1" "this" {
  metadata {
    name      = "aquaops-limits"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  spec {
    limit {
      type = "Container"
      default_request = {
        cpu    = var.container_defaults.default_request_cpu
        memory = var.container_defaults.default_request_memory
      }
      default = {
        cpu    = var.container_defaults.default_limit_cpu
        memory = var.container_defaults.default_limit_memory
      }
      max = {
        memory = var.container_defaults.max_memory
      }
    }
  }
}

# -----------------------------------------------------------------------------
# Network: nothing enters the namespace unless allowed below
# -----------------------------------------------------------------------------

resource "kubernetes_network_policy_v1" "default_deny_ingress" {
  metadata {
    name      = "default-deny-ingress"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  spec {
    pod_selector {}
    policy_types = ["Ingress"]
  }
}

# The services of one environment talk to each other (gateway -> services,
# services -> PostgreSQL / Kafka / Keycloak / Eureka). Another environment's
# pods cannot reach them: "rc11" is isolated from "test".
resource "kubernetes_network_policy_v1" "allow_same_namespace" {
  metadata {
    name      = "allow-same-namespace"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  spec {
    pod_selector {}
    policy_types = ["Ingress"]

    ingress {
      from {
        pod_selector {}
      }
    }
  }
}

# The Ingress controller (Traefik, namespace kube-system in k3s) may reach
# only the pods labelled aquaops.ma/ingress=true (frontend, gateway).
resource "kubernetes_network_policy_v1" "allow_ingress_controller" {
  metadata {
    name      = "allow-ingress-controller"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  spec {
    pod_selector {
      match_labels = { "aquaops.ma/ingress" = "true" }
    }
    policy_types = ["Ingress"]

    ingress {
      from {
        namespace_selector {
          match_labels = { "kubernetes.io/metadata.name" = "kube-system" }
        }
      }
    }
  }
}

# -----------------------------------------------------------------------------
# Shared, non-sensitive configuration
# -----------------------------------------------------------------------------

resource "kubernetes_config_map_v1" "aquaops" {
  metadata {
    name      = "aquaops-config"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  data = local.config
}

# -----------------------------------------------------------------------------
# Secrets. data_wo = write-only: the values go to Kubernetes but are never
# stored in the Terraform plan or state. They are read again only when
# secrets_revision changes.
# -----------------------------------------------------------------------------

resource "kubernetes_secret_v1" "db" {
  metadata {
    name      = "aquaops-db" # name expected by E2's deploy/k8s/assets
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  data_wo = {
    DB_USER     = var.db_user
    DB_PASSWORD = var.db_password
  }
  data_wo_revision = var.secrets_revision

  lifecycle {
    precondition {
      condition     = var.db_user != null && var.db_password != null
      error_message = "TF_VAR_db_user and TF_VAR_db_password are required for apply (see deploy/terraform/README.md)."
    }
  }
}

resource "kubernetes_secret_v1" "keycloak" {
  metadata {
    name      = "aquaops-keycloak"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  data_wo = {
    KEYCLOAK_ADMIN               = var.keycloak_admin
    KEYCLOAK_ADMIN_PASSWORD      = var.keycloak_admin_password
    KEYCLOAK_AUDIT_CLIENT_SECRET = var.keycloak_audit_client_secret
  }
  data_wo_revision = var.secrets_revision

  lifecycle {
    precondition {
      condition = alltrue([
        var.keycloak_admin != null,
        var.keycloak_admin_password != null,
        var.keycloak_audit_client_secret != null,
      ])
      error_message = "TF_VAR_keycloak_admin, TF_VAR_keycloak_admin_password and TF_VAR_keycloak_audit_client_secret are required for apply."
    }
  }
}

# Pull access to the private images ghcr.io/fatii-elb/aquaops/*.
resource "kubernetes_secret_v1" "ghcr" {
  count = var.ghcr_pull_secret ? 1 : 0

  metadata {
    name      = "ghcr-pull"
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  type = "kubernetes.io/dockerconfigjson"

  data_wo = {
    ".dockerconfigjson" = jsonencode({
      auths = {
        "ghcr.io" = {
          # Evaluated even by destroy, where no secret is loaded: no
          # interpolation of null (the precondition below guards apply).
          auth = (var.ghcr_username != null && var.ghcr_token != null
            ? base64encode("${var.ghcr_username}:${var.ghcr_token}")
          : null)
        }
      }
    })
  }
  data_wo_revision = var.secrets_revision

  lifecycle {
    precondition {
      condition     = var.ghcr_username != null && var.ghcr_token != null
      error_message = "TF_VAR_ghcr_username and TF_VAR_ghcr_token are required when ghcr_pull_secret = true."
    }
  }
}

# Every pod of the namespace pulls with ghcr-pull, without each Deployment
# naming it. No Kubernetes API token is mounted unless a workload asks for it.
resource "kubernetes_default_service_account_v1" "this" {
  metadata {
    namespace = kubernetes_namespace_v1.this.metadata[0].name
    labels    = local.labels
  }

  automount_service_account_token = false

  dynamic "image_pull_secret" {
    for_each = var.ghcr_pull_secret ? [1] : []
    content {
      name = kubernetes_secret_v1.ghcr[0].metadata[0].name
    }
  }
}
