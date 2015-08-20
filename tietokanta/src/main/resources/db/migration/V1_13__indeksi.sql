CREATE TABLE indeksi (
  id serial primary key,
  nimi varchar(128) not null,
  vuosi smallint not null,
  kuukausi smallint not null,
  arvo numeric not null,
  unique (nimi, vuosi, kuukausi)
);
