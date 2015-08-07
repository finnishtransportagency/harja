
# Testitietokannan ja frontin pystytys Vagrantilla

## Tarvitset

1. Uusin [VirtualBox](https://www.virtualbox.org)
2. [Vagrant 1.7.4](https://www.vagrantup.com)
3. Ansible 1.9.2+

  ```brew install ansible```

# Käyttö

Virtuaalikone käynnistyy ja provisioituu komennolla

```vagrant up```

Nginx proxy vastaa portissa 8000 ja PostgreSQL oletusportissa 5432.
