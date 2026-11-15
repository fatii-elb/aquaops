# =============================================================================
# AquaOps on k3s: CLUSTER-WIDE add-ons, installed once (SCRUM-56, E5).
# Shared by every environment: never destroyed by RC-11.
# Run through deploy/terraform/tf.sh cluster cluster <command>.
# =============================================================================

resource "kubernetes_namespace_v1" "cert_manager" {
  count = var.cert_manager_enabled ? 1 : 0

  metadata {
    name = "cert-manager"
    labels = {
      "app.kubernetes.io/managed-by"       = "terraform"
      "pod-security.kubernetes.io/enforce" = "restricted"
    }
  }

  lifecycle {
    precondition {
      condition     = terraform.workspace == "cluster"
      error_message = "Use the \"cluster\" workspace (deploy/terraform/tf.sh cluster cluster ...)."
    }
  }
}

resource "helm_release" "cert_manager" {
  count = var.cert_manager_enabled ? 1 : 0

  name       = "cert-manager"
  namespace  = kubernetes_namespace_v1.cert_manager[0].metadata[0].name
  repository = "https://charts.jetstack.io"
  chart      = "cert-manager"
  version    = var.cert_manager_version

  atomic          = true # failed install = rolled back, nothing half-installed
  cleanup_on_fail = true
  wait            = true
  timeout         = 600

  values = [yamlencode({
    crds = { enabled = true }
    # Small footprint: the VM also runs the Docker Compose stack.
    resources = {
      requests = { cpu = "10m", memory = "48Mi" }
      limits   = { memory = "128Mi" }
    }
    webhook = {
      resources = {
        requests = { cpu = "10m", memory = "24Mi" }
        limits   = { memory = "64Mi" }
      }
    }
    cainjector = {
      resources = {
        requests = { cpu = "10m", memory = "48Mi" }
        limits   = { memory = "128Mi" }
      }
    }
  })]
}
