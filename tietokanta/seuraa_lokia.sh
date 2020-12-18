docker exec harjadb bash -c "tail -n 100 -f /var/lib/pgsql/12/data/log/postgresql-$(LC_ALL=C date +%a).log"
