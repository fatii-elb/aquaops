# Reprise après suppression d'un pod (NFR-05, SCRUM-52)

Objectif : un pod de télémétrie supprimé sous charge ne fait perdre aucune mesure, et le service est rétabli en 60 s au plus (RC-05).

## Ce qui garantit « 0 perte »

| Risque | Protection | Où |
|---|---|---|
| L'offset est validé avant que la mesure soit en base | `enable-auto-commit: false` et `ack-mode: batch` : l'offset n'est validé qu'après le traitement du lot | `telemetry-service/application.yml` |
| La base est indisponible quelques secondes | `KafkaRecoveryConfig` : nouvel essai toutes les 2 s sans limite, l'offset n'avance pas (par défaut Spring abandonne le message après 9 essais) | `KafkaRecoveryConfig.java` |
| Le pod est arrêté en plein lot | Arrêt gracieux : le lot en cours est enregistré, l'offset validé, le consommateur quitte le groupe | `shutdown: graceful`, `preStop` du manifeste |
| Le pod est tué sans prévenir | Les messages non validés sont relus par un autre pod après 15 s au plus (`session.timeout.ms`) | `application.yml` |
| Un message relu serait stocké deux fois | Colonnes `source_partition`, `source_offset`, `source_timestamp` et contrainte unique `uk_readings_source` : le doublon est refusé par la base puis ignoré | `Reading.java`, `ReadingMeasureSink.java` |
| Une mesure mal formée bloquerait la partition | Les erreurs de lecture ne sont pas réessayées : journal, puis on passe | `KafkaRecoveryConfig.java` |

L'horodatage du message fait partie de la contrainte : si le topic est recréé, les offsets repartent de 0, mais l'horodatage change, donc aucune mesure n'est prise pour un doublon.

## Sondes pour tous les services

`shutdown: graceful`, `timeout-per-shutdown-phase: 20s` et les sondes `/actuator/health/liveness` et `/readiness` sont maintenant dans l'`application.yml` des 7 services (assets l'avait déjà, telemetry dans le lot précédent, les 5 autres dans ce lot). Les manifestes Kubernetes des autres services sont ceux de E5 : ils doivent pointer vers ces deux chemins.

## À vérifier après déploiement

```bash
# la contrainte existe-t-elle ?
kubectl -n aquaops exec deploy/postgres -- psql -U aquaops -d aquaops_telemetry -c '\d readings'
```
Tu dois voir `uk_readings_source` UNIQUE sur les trois colonnes `source_*`. Elle est créée au démarrage par Hibernate (`ddl-auto: update`) ; les anciennes lignes ont ces colonnes vides, ce qui est accepté.

Puis lancer `bash deploy/demo/rc05.sh`.

## Point d'attention pour E1 et E5 : le topic a une seule partition

`deploy/docker-compose.yml` crée `sensor-readings` avec `--partitions 1`. Une partition ne peut être lue que par un seul consommateur d'un groupe : avec 2 à 6 pods de télémétrie (HPA, NFR-04), **un seul lit, les autres attendent**. Conséquences :
- l'autoscaling ne raccourcit pas le retard de lecture ; il ne sert que pour la charge CPU de l'API ;
- supprimer un pod qui n'est pas le consommateur ne teste rien : `rc05.sh` identifie donc le pod qui lit et supprime celui-là.

Pour que l'autoscaling serve à quelque chose, il faudrait plusieurs partitions et envoyer les mesures avec l'identifiant du capteur comme clé (l'ordre par capteur est conservé). C'est une décision d'équipe, non faite ici : elle touche le simulateur (E1) et le Docker Compose et les manifestes (E5).
