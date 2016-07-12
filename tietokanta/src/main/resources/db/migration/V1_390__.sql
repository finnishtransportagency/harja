CREATE TYPE suoritettavatehtava AS
ENUM ('auraus ja sohjonpoisto',
  'suolaus',
  'pistehiekoitus',
  'linjahiekoitus',
  'pinnan tasaus',
  'liikennemerkkien puhdistus',
  'lumivallien madaltaminen',
  'sulamisveden haittojen torjunta',
  'tiestotarkastus',
  'kelintarkastus',
  'harjaus',
  'koneellinen niitto',
  'koneellinen vesakonraivaus',
  'sorateiden muokkaushoylays',
  'sorateiden polynsidonta',
  'sorateiden tasaus',
  'sorastus',
  'paallysteiden paikkaus',
  'paallysteiden juotostyot',
  'siltojen puhdistus',
  'l- ja p-alueiden puhdistus',
  'muu'
);

CREATE TABLE liikenneohjausaitahavainto (
  tyokoneid      INTEGER PRIMARY KEY,
  jarjestelma    VARCHAR(128) NOT NULL,
  organisaatio   INTEGER REFERENCES organisaatio (id),
  viestitunniste INTEGER      NOT NULL,
  lahetysaika    TIMESTAMP    NOT NULL,
  vastaanotettu  TIMESTAMP DEFAULT current_timestamp,
  tyokonetyyppi  VARCHAR(64)  NOT NULL,
  sijainti       POINT        NOT NULL,
  urakkaid       INTEGER REFERENCES urakka (id),
  sopimusid      INTEGER REFERENCES sopimus (id),
  tehtavat       suoritettavatehtava []
);
