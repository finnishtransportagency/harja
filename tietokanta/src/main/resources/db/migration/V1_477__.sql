-- Lisää jyra suoritettaviin tehtäviin

ALTER TYPE suoritettavatehtava RENAME TO _st;

-- Luo uudet tehtävät
CREATE TYPE suoritettavatehtava AS
ENUM (
  'auraus ja sohjonpoisto',
  'aurausviitoitus ja kinostimet',
  'harjaus',
  'kelintarkastus',
  'koneellinen niitto',
  'koneellinen vesakonraivaus',
  'l- ja p-alueiden puhdistus',
  'liikennemerkkien puhdistus',
  'liik. opast. ja ohjausl. hoito seka reunapaalujen kun.pito',
  'linjahiekoitus',
  'lumensiirto',
  'lumivallien madaltaminen',
  'ojitus',
  'paallysteiden juotostyot',
  'paallysteiden paikkaus',
  'paannejaan poisto',
  'palteen poisto',
  'pinnan tasaus',
  'pistehiekoitus',
  'paallystetyn tien sorapientareen taytto',
  'siltojen puhdistus',
  'sorastus',
  'sorapientareen taytto',
  'sorateiden muokkaushoylays',
  'sorateiden polynsidonta',
  'sorateiden tasaus',
  'sulamisveden haittojen torjunta',
  'suolaus',
  'tiestotarkastus',
  'asfaltointi',
  'tiemerkinta',
  'kuumennus',
  'sekoitus tai stabilointi',
  'turvalaite',
  'jyrays',
  'muu');


-- Tee päivitys työkonehavaintotauluun
ALTER TABLE tyokonehavainto RENAME COLUMN tehtavat TO _tehtavat;
ALTER TABLE tyokonehavainto ADD tehtavat suoritettavatehtava [];
UPDATE tyokonehavainto SET tehtavat = _tehtavat :: TEXT :: suoritettavatehtava [];
ALTER TABLE tyokonehavainto DROP COLUMN _tehtavat;

-- Tee päivitys toimenpidekooditauluun
ALTER TABLE toimenpidekoodi RENAME COLUMN suoritettavatehtava TO _suoritettavatehtava;
ALTER TABLE toimenpidekoodi ADD suoritettavatehtava suoritettavatehtava;
UPDATE toimenpidekoodi SET suoritettavatehtava = _suoritettavatehtava :: TEXT :: suoritettavatehtava;
ALTER TABLE toimenpidekoodi DROP COLUMN _suoritettavatehtava;

-- Luo uudella tyypillä työkonehavaintojen käsittely funktio
CREATE OR REPLACE FUNCTION tallenna_tai_paivita_tyokonehavainto(
  jarjestelma_ character varying,
  organisaationimi_ character varying,
  ytunnus_ character varying,
  viestitunniste_ integer,
  lahetysaika_ timestamp,
  tyokoneid_ integer,
  tyokonetyyppi_ character varying,
  sijainti_ point,
  suunta_ real,
  urakkaid_ integer,
  tehtavat_ suoritettavatehtava[]
) RETURNS VOID AS
$$
DECLARE
  organisaatioid integer;
BEGIN
  SELECT id INTO organisaatioid FROM organisaatio WHERE nimi=organisaationimi_ AND ytunnus=ytunnus_;

  LOOP
    UPDATE tyokonehavainto
    SET jarjestelma=jarjestelma_,
      organisaatio=organisaatioid,
      viestitunniste=viestitunniste_,
      lahetysaika=lahetysaika_,
      vastaanotettu=DEFAULT,
      tyokonetyyppi=tyokonetyyppi_,
      sijainti=sijainti_,
      suunta=suunta_,
      urakkaid=urakkaid_,
      tehtavat=tehtavat_,
      edellinensijainti=sijainti
    WHERE tyokoneid=tyokoneid_;
    IF FOUND THEN
      RETURN;
    END IF;

    BEGIN
      INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika,
                                   tyokoneid, tyokonetyyppi, sijainti, urakkaid, tehtavat, suunta)
      VALUES (jarjestelma_, organisaatioid, viestitunniste_, lahetysaika_, tyokoneid_, tyokonetyyppi_, sijainti_,
              urakkaid_, tehtavat_, suunta_);
      RETURN;
      EXCEPTION WHEN unique_violation THEN
      -- retry
    END;
  END LOOP;
END;
$$
LANGUAGE plpgsql;

-- Hävitä vanha tyyppi
DROP TYPE _st;
