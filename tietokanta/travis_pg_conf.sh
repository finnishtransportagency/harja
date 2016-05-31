echo "Konfiguroidaan PostgreSQL 9.5"

sed -i "s/#listen_addresses.*/listen_addresses = '*'/g" /etc/postgresql/9.5/main/postgresql.conf

sed -i "s/port = 5433/port = 5432/g" /etc/postgresql/9.5/main/postgresql.conf
