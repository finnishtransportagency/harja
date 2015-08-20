CREATE TYPE erilliskustannustyyppi AS ENUM ('tilaajan_maa-aines', 'vahinkojen_korjaukset', 'asiakastyytyvaisyysbonus', 'muu');

CREATE TABLE erilliskustannus (
  id serial PRIMARY KEY,
  tyyppi erilliskustannustyyppi NOT NULL,
  sopimus integer REFERENCES sopimus(id),
  toimenpideinstanssi integer NOT NULL REFERENCES toimenpideinstanssi(id),
  pvm date NOT NULL,
  rahasumma numeric NOT NULL,
  nimi varchar(128), -- emme pysty viittaamaan indeksin nimeen koska ei ole key
  lisatieto varchar(1024),
  luotu timestamp,
  luoja integer REFERENCES kayttaja (id),
  muokattu timestamp,
  muokkaaja integer REFERENCES kayttaja (id),
  poistettu boolean default false
);