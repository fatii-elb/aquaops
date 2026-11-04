# AquaOps integration stack: one database per service, owned by POSTGRES_USER.
# Run by the postgres image ONCE, on the first start of an EMPTY volume.
#
# A shell script (not .sql) so the owner follows POSTGRES_USER instead of a
# hardcoded name. The image sources this file: it must not change shell options.
# Independent from deploy/postgres-init/01-bases.sql (local dev stack).

for aquaops_db in aquaops_assets aquaops_incidents aquaops_notifications aquaops_telemetry; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        -c "CREATE DATABASE \"$aquaops_db\" OWNER \"$POSTGRES_USER\";"
done
