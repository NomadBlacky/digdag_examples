FROM myui/digdag-server:latest

CMD exec digdag server --bind 0.0.0.0 \
                       --port 65432 \
                       --config /etc/digdag.properties \
                       --log /var/lib/digdag/logs/server \
                       --task-log /var/lib/digdag/logs/tasks \
                       -X database.type=$DB_TYPE \
                       -X database.user=$DB_USER \
                       -X database.password=$DB_PASSWORD \
                       -X database.host=$DB_HOST \
                       -X database.port=$DB_PORT \
                       -X database.database=$DB_NAME
