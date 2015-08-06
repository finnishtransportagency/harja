
# Testitietokannan ja frontin pystytys Vagrantilla

## Tarvitset

1. [VirtualBox](https://www.virtualbox.org)
2. [Vagrant](https://www.vagrantup.com)
3. Ansible
   ```brew install ansible```

## Valmistelut

Editoi omat Deus-käyttäjätunnuksesi ja salasanasi db_provision.yml:n vars-osioon (deus_user ja deus_password).

Virtuaalikone käynnistyy ja provisioituu komennolla

```vagrant up```

Nginx proxy vastaa portissa 8000 ja PostgreSQL oletusportissa 5432.
