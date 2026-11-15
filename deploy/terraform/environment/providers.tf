# The kubeconfig is never in the repository. Its path comes from the
# KUBE_CONFIG_PATH environment variable (checked by deploy/terraform/tf.sh).
provider "kubernetes" {
  config_context = var.kube_context
}
