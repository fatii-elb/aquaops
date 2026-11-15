variable "kube_context" {
  description = "Context of the kubeconfig to use (k3s names it \"default\")."
  type        = string
  default     = "default"
}

variable "cert_manager_enabled" {
  description = "Install cert-manager (needed by E2's deploy/k8s/tls, NFR-14)."
  type        = bool
  default     = true
}

variable "cert_manager_version" {
  description = "cert-manager chart version, pinned (https://charts.jetstack.io)."
  type        = string
  default     = "v1.21.3"
}
