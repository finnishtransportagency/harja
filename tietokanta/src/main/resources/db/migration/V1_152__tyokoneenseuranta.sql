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

CREATE TABLE tyokonehavainto (
  tyokoneid integer PRIMARY KEY,
  jarjestelma varchar(128) NOT NULL,
  organisaatio integer REFERENCES organisaatio(id),
  viestitunniste integer NOT NULL,
  lahetysaika timestamp NOT NULL,
  vastaanotettu timestamp default current_timestamp,
  tyokonetyyppi varchar(64) NOT NULL,
  sijainti point NOT NULL,
  urakkaid integer REFERENCES urakka(id),
  sopimusid integer REFERENCES sopimus(id),
  tehtavat suoritettavatehtava[]
);
