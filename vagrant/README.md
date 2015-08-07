
# Testitietokannan ja frontin pystytys Vagrantilla

## Tarvitset

1. Uusin [VirtualBox](https://www.virtualbox.org)
2. [Vagrant 1.7.4](https://www.vagrantup.com)
3. Ansible 1.9.2+

  ```brew install ansible```

# Käyttö

Virtuaalikone käynnistyy ja provisioituu komennolla. Schemaversiot ovat automaattisesti viimeisimmät.

```vagrant up```

Nginx proxy vastaa portissa 8000 ja PostgreSQL oletusportissa 5432.

# Tietokannan päivittäminen

`vagrant provision` muuntaa ja tyhjentää molemmat kannat (harja, harjatest) viimeisimpään schemaversioon
suoraan versionhallinnasta.

`migrate.sh` muuntaa harja-kannan ilman putsausta (omasta harja-tietokanta -workspacesta).

`migrate_test.sh` muuntaa ja tyhjentää testikannan (omasta harja-tietokanta -workspacesta).
