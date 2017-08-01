echo "Konfiguroidaan PostgreSQL 9.5"

sed -i "s/#listen_addresses.*/listen_addresses = '*'/g" /etc/postgresql/9.5/main/postgresql.conf

sed -i "s/port = 5433/port = 5432/g" /etc/postgresql/9.5/main/postgresql.conf

echo "\nlocal all all trust\nhost    all             all             0.0.0.0/0            trust" > /etc/postgresql/9.5/main/pg_hba.conf

sed -i "s/#fsync.*/fsync = off/g" /etc/postgresql/9.5/main/postgresql.conf
sed -i "s/#full_page_writes.*/full_page_writes = off/g" /etc/postgresql/9.5/main/postgresql.conf
