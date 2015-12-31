
# Testipalvelimien konfiguraation muuttaminen

Playbookien ajaminen uudelleen on turvallista, joten aja test_env_setup.yml -playbook konfiguraation muokkaamisen jälkeen:

```bash
   bash$ ansible-playbook -i inventory/harjadev test_env_setup.yml
```

# Konfiguraation muutosten kohdentaminen tiettyyn palvelimeen

HUOM! Käytä todella harkiten. Testiympäristöjen konfiguraation olisi oltava kaikissa palvelimissa samanlainen. Jos kuitenkin
haluat muuttaa yhden palvelimen konfiguraatiota (toivottavasti väliaikaisesti), voit rajoittaa playbookin ajon tiettyyn palvelimeen:

```bash
   bash$ ansible-playbook -i inventory/harjadev --limit harja-devX test_env_setup.yml
```

# Testipalvelimien uudelleenkäynnistys

