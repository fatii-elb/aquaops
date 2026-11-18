# =============================================================================
# assets and telemetry from E2's manifests, REUSED AS THEY ARE (NFR-05:
# probes, PodDisruptionBudget, graceful stop; assets also registers in
# Eureka). Telemetry replica count is managed by its HPA (NFR-04).
# SCRUM-56 / NFR-09, E5.
#
# deploy/k8s/<service>/deployment.yaml and service.yaml are read at plan time,
# never copied: a change by E2 is picked up by the next plan. Only what an
# environment must decide is changed here, on the decoded objects:
#   - namespace, common labels, image (registry, SHA, digest, pull policy);
#   - replicas for assets if e2_replicas says so; telemetry replicas are
#     omitted so the HPA exclusively manages them;
#   - no injected <SERVICE>_PORT variables, no Kubernetes API token;
#   - E5 hardening, checked on the cluster: uid/gid 999 declared (so
#     runAsNonRoot can be verified), read-only root file system, /tmp on an
#     emptyDir, as for the 5 services of services.tf;
#   - assets only: login audit (FR-24), as deploy/test/compose.infra.yml.
# =============================================================================

locals {
  e2_service_names = ["assets", "telemetry"]
  e2_services      = var.services_enabled ? toset(local.e2_service_names) : toset([])

  e2_deployment = {
    for s in local.e2_service_names :
    s => provider::kubernetes::manifest_decode(file("${path.module}/../../k8s/${s}/deployment.yaml"))
  }
  e2_others = {
    for s in local.e2_service_names :
    s => provider::kubernetes::manifest_decode_multi(file("${path.module}/../../k8s/${s}/service.yaml"))
  }

  # Variables added to E2's list (never replacing one of E2's).
  e2_extra_env = {
    assets = [
      { name = "AUDIT_KEYCLOAK_ENABLED", value = "true" },
      { name = "KEYCLOAK_BASE_URL", value = local.config.KEYCLOAK_BASE_URL },
      {
        name = "KEYCLOAK_AUDIT_CLIENT_SECRET"
        valueFrom = { secretKeyRef = {
          name = kubernetes_secret_v1.keycloak.metadata[0].name
          key  = "KEYCLOAK_AUDIT_CLIENT_SECRET"
        } }
      },
    ]
    telemetry = []
  }

  e2_container = {
    for s, d in local.e2_deployment : s => merge(d.spec.template.spec.containers[0], {
      image           = local.service_image[s]
      imagePullPolicy = local.service_pull_policy
      env             = concat(d.spec.template.spec.containers[0].env, local.e2_extra_env[s])
      securityContext = merge(try(d.spec.template.spec.containers[0].securityContext, {}), {
        readOnlyRootFilesystem = true
      })
      volumeMounts = concat(try(d.spec.template.spec.containers[0].volumeMounts, []), [
        { name = "tmp", mountPath = "/tmp" },
      ])
    })
  }

  e2_manifest = {
    for s, d in local.e2_deployment : s => merge(d, {
      metadata = merge(d.metadata, {
        namespace = local.ns
        labels    = merge(local.labels, d.metadata.labels, { "app.kubernetes.io/name" = s })
      })
      spec = merge(
        { for key, value in d.spec : key => value if !(s == "telemetry" && key == "replicas") },
        { for key, value in { replicas = lookup(var.e2_replicas, s, try(d.spec.replicas, 2)) } : key => value if s == "assets" },
        {
          template = merge(d.spec.template, {
            metadata = merge(d.spec.template.metadata, {
              labels = merge(local.labels, d.spec.template.metadata.labels, { "app.kubernetes.io/name" = s })
            })
            spec = merge(d.spec.template.spec, {
              enableServiceLinks           = false
              automountServiceAccountToken = false
              securityContext = merge(try(d.spec.template.spec.securityContext, {}), {
                runAsNonRoot = true
                runAsUser    = 999 # user "aquaops" of deploy/docker/spring-service.Dockerfile
                runAsGroup   = 999
              })
              containers = [local.e2_container[s]]
              volumes = concat(try(d.spec.template.spec.volumes, []), [
                { name = "tmp", emptyDir = { sizeLimit = "64Mi" } },
              ])
            })
          })
        }
      )
    })
  }

  # Service, PodDisruptionBudget, and HPA documents, keyed "<svc>/<Kind>/<name>".
  e2_other_docs = merge([
    for s in local.e2_services : {
      for doc in local.e2_others[s] : "${s}/${doc.kind}/${doc.metadata.name}" => doc
    }
  ]...)
}

resource "kubernetes_manifest" "e2_others" {
  for_each = local.e2_other_docs

  manifest = merge(each.value, {
    metadata = merge(each.value.metadata, {
      namespace = local.ns
      labels    = merge(local.labels, try(each.value.metadata.labels, {}))
    })
  })

  # Terraform owns these objects: a field changed by hand (kubectl patch,
  # kubectl scale) is taken back at the next apply instead of blocking it
  # (server-side apply conflict), so a deployment or a rollback never stops on it.
  field_manager {
    force_conflicts = true
  }

  depends_on = [terraform_data.services_inputs]
}

resource "kubernetes_manifest" "e2_service" {
  for_each = local.e2_services

  manifest = local.e2_manifest[each.key]

  # NO "wait { rollout = true }" here: with it, every image update ends in
  # "Provider produced inconsistent result after apply" (the Deployment
  # controller bumps deployment.kubernetes.io/revision while the provider
  # waits; computed_fields does not cover it: both reproduced in the sandbox,
  # provider 3.5). The rollout is awaited and verified by
  # deploy/k3s/deploy-services.sh (kubectl rollout status, images, health).

  # Terraform owns these objects: a field changed by hand (kubectl patch,
  # kubectl scale) is taken back at the next apply instead of blocking it
  # (server-side apply conflict), so a deployment or a rollback never stops on it.
  field_manager {
    force_conflicts = true
  }

  timeouts {
    create = var.services_rollout_timeout
    update = var.services_rollout_timeout
  }

  depends_on = [
    terraform_data.services_inputs,
    kubernetes_stateful_set_v1.postgres,
    kubernetes_stateful_set_v1.kafka,
    kubernetes_deployment_v1.keycloak,
    kubernetes_deployment_v1.eureka,
    kubernetes_job_v1.postgres_databases,
  ]
}
