terraform {
  # 1.11+: ephemeral variables and write-only arguments (secrets never in the
  # plan or the state).
  required_version = ">= 1.11.0"

  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 3.5"
    }
  }

  # Local state, one file per workspace (= per environment). The path is given
  # at init time by deploy/terraform/tf.sh, outside the Git checkout:
  #   -backend-config=path=...  -backend-config=workspace_dir=...
  backend "local" {}
}
