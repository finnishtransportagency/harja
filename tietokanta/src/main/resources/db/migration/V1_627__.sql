CREATE TABLE kan_kanava (
  id       SERIAL PRIMARY KEY,
  nimi     TEXT NOT NULL,
  sijainti GEOMETRY
);

INSERT INTO kan_kanava (nimi) VALUES
  ('Ahkiolahden kanava'),
  ('Juankosken kanava'),
  ('Joensuun kanava'),
  ('Kaltimon kanava'),
  ('Karjalankosken kanava'),
  ('Karvion kanava'),
  ('Kerman kanava'),
  ('Konnuksen kanava'),
  ('Kuurnan kanava'),
  ('Lastukosken kanava'),
  ('Nerkoon kanava'),
  ('Pilpan kanava'),
  ('Taipaleen kanava'),
  ('Taivallahden kanava'),
  ('Varistaipaleen kanava'),
  ('Vihovuonteen kanava'),
  ('Saimaan kanava'),
  ('Kalkkisten kanava'),
  ('Keiteleen kanava: Vaajakosken kanava'),
  ('Keiteleen kanava: Kuhankosken kanava'),
  ('Keiteleen kanava: Kuusan kanava'),
  ('Keiteleen kanava: Kapeenkosken kanava'),
  ('Keiteleen kanava: Paatelan kanava'),
  ('Kerkonkosken kanava'),
  ('Kiesimän kanava'),
  ('Kolun kanava'),
  ('Neiturin kanava'),
  ('Vääksyn kanava'),
  ('Herraskosken kanava'),
  ('Lempäälän kanava'),
  ('Muroleen kanava'),
  ('Valkeakosken kanava');

CREATE TYPE KOHTEEN_TYYPPI AS ENUM ('silta', 'sulku', 'sulku-ja-silta');

CREATE TABLE kan_kohde (
  id          SERIAL PRIMARY KEY,
  "kanava-id" INTEGER REFERENCES kan_kanava (id) NOT NULL,
  nimi        TEXT,
  tyyppi      KOHTEEN_TYYPPI                     NOT NULL,
  sijainti    GEOMETRY,

  luotu       TIMESTAMP DEFAULT NOW(),
  luoja       INTEGER REFERENCES kayttaja (id)   NOT NULL,
  muokattu    TIMESTAMP,
  muokkaaja   INTEGER REFERENCES kayttaja (id),
  poistettu   BOOLEAN   DEFAULT FALSE,
  poistaja    INTEGER REFERENCES kayttaja (id)
);

CREATE TABLE kan_kohde_urakka (
  "kohde-id"  INTEGER REFERENCES kan_kohde (id) NOT NULL,
  "urakka-id" INTEGER REFERENCES urakka (id)    NOT NULL,

  luotu       TIMESTAMP DEFAULT NOW(),
  luoja       INTEGER REFERENCES kayttaja (id)  NOT NULL,
  poistettu   BOOLEAN   DEFAULT FALSE,
  poistaja    INTEGER REFERENCES kayttaja (id)
);