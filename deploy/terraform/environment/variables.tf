# -----------------------------------------------------------------------------
# Where and what
# -----------------------------------------------------------------------------

variable "kube_context" {
  description = "Context of the kubeconfig to use (k3s names it \"default\")."
  type        = string
  default     = "default"
}

variable "environment" {
  description = "Environment name. Must equal the Terraform workspace (checked)."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.environment))
    error_message = "environment: lowercase letters, digits and dashes, 2 to 21 characters."
  }
}

variable "namespace" {
  description = "Kubernetes namespace of this environment."
  type        = string

  validation {
    condition     = can(regex("^aquaops(-[a-z0-9]+)?$", var.namespace))
    error_message = "namespace must be \"aquaops\" or start with \"aquaops-\"."
  }
}

# -----------------------------------------------------------------------------
# Resources: the VM also runs the Docker Compose stack (8 GB in total)
# -----------------------------------------------------------------------------

variable "quota" {
  description = "Upper bound for the whole namespace (ResourceQuota)."
  type = object({
    requests_cpu    = string
    requests_memory = string
    limits_cpu      = string
    limits_memory   = string
    pods            = number
    pvcs            = number
    storage         = string
  })
}

variable "container_defaults" {
  description = "Defaults and maximum per container (LimitRange)."
  type = object({
    default_request_cpu    = string
    default_request_memory = string
    default_limit_cpu      = string
    default_limit_memory   = string
    max_memory             = string
  })
}

# -----------------------------------------------------------------------------
# Non-sensitive configuration, shared by the services (ConfigMap)
# -----------------------------------------------------------------------------

variable "config" {
  description = "Extra or overriding entries for the aquaops-config ConfigMap. Never secrets."
  type        = map(string)
  default     = {}

  validation {
    condition = alltrue([
      for k in keys(var.config) : !can(regex("(?i)(password|secret|token|credential)", k))
    ])
    error_message = "config must not contain passwords, secrets or tokens: use the ephemeral variables."
  }
}

# -----------------------------------------------------------------------------
# Secrets: ephemeral = never written to the plan or the state. Given at apply
# time only (TF_VAR_... read from a root-only file, see README). Required for
# plan and apply (the plan stops with a clear message without them), not for
# destroy.
# -----------------------------------------------------------------------------

variable "secrets_revision" {
  description = "Increase by 1 to push new secret values (write-only values are not compared)."
  type        = number
  default     = 1
}

variable "db_user" {
  type      = string
  default   = null
  ephemeral = true
}

variable "db_password" {
  type      = string
  default   = null
  sensitive = true
  ephemeral = true
}

variable "keycloak_admin" {
  type      = string
  default   = null
  ephemeral = true
}

variable "keycloak_admin_password" {
  type      = string
  default   = null
  sensitive = true
  ephemeral = true
}

variable "keycloak_audit_client_secret" {
  type      = string
  default   = null
  sensitive = true
  ephemeral = true
}

# -----------------------------------------------------------------------------
# Private images on GHCR
# -----------------------------------------------------------------------------

variable "ghcr_pull_secret" {
  description = "Create the ghcr-pull secret and attach it to the default service account."
  type        = bool
  default     = true
}

variable "ghcr_username" {
  description = "GitHub account that owns the read-only token."
  type        = string
  default     = null
  ephemeral   = true
}

variable "ghcr_token" {
  description = "Classic token with read:packages ONLY."
  type        = string
  default     = null
  sensitive   = true
  ephemeral   = true
}

# -----------------------------------------------------------------------------
# Infrastructure: PostgreSQL, Kafka, Keycloak, Eureka (infrastructure.tf...)
# -----------------------------------------------------------------------------

variable "infrastructure_enabled" {
  description = "Deploy PostgreSQL, Kafka, Keycloak and Eureka in this environment."
  type        = bool
  default     = false
}

variable "infrastructure_images" {
  description = "Infrastructure images, pinned by tag AND digest (same tags as Docker Compose)."
  type = object({
    postgres = string
    kafka    = string
    keycloak = string
  })
  default = {
    postgres = "postgres:18.6@sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42"
    kafka    = "apache/kafka:4.3.1@sha256:77e3df9054047a88b520d0cc46e16696d3b22022e1d580aeccd2632df6532837"
    keycloak = "quay.io/keycloak/keycloak:26.8.0@sha256:27b3230fbabb8c9ff3c66c864cf09dcd8a41aca82e5100679d109ef0eeb05741"
  }

  validation {
    condition     = alltrue([for i in values(var.infrastructure_images) : can(regex("^[^:@]+(:[^:@]+)?@sha256:[0-9a-f]{64}$", i))])
    error_message = "Each infrastructure image must be pinned by digest (name:tag@sha256:...)."
  }
}

variable "eureka_image" {
  description = "Eureka server image, built from services/eureka-server, scanned, then imported into k3s (no registry, no token). Tag = source commit."
  type        = string
  default     = null

  validation {
    condition     = var.eureka_image == null || can(regex("^localhost/aquaops/eureka-server:[0-9a-f]{7,40}$", var.eureka_image))
    error_message = "eureka_image: localhost/aquaops/eureka-server:<commit SHA> (imported image, never \"latest\")."
  }
}

variable "infrastructure_storage" {
  description = "Size of the persistent volumes (local-path, under /var/lib/rancher/k3s/storage)."
  type = object({
    postgres = string
    kafka    = string
  })
  default = {
    postgres = "2Gi"
    kafka    = "2Gi"
  }
}
