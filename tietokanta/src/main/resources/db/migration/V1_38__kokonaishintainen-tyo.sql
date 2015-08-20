CREATE TABLE kokonaishintainen_tyo (
  id serial primary key,       -- sisäinen ID
  vuosi smallint not null,
  kuukausi smallint not null,
  summa numeric,
  maksupvm date,

  toimenpideinstanssi integer not null REFERENCES toimenpideinstanssi (id),
  sopimus integer not null REFERENCES sopimus(id),
  unique (toimenpideinstanssi, sopimus, vuosi, kuukausi)
);