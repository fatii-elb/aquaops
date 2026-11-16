# Kubeconfig path from KUBE_CONFIG_PATH (never in the repository).
provider "kubernetes" {
  config_context = var.kube_context
}

provider "helm" {
  kubernetes = {
    config_context = var.kube_context
  }
}
