-- ---------------------------------------------------------------------
-- Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset
-- ---------------------------------------------------------------------

CREATE TYPE muutos_yksikkohinta_lahde_enum AS ENUM ('laskettu','aseta','manuaali');


CREATE TABLE mhu_muutos_tehtava_tiedot (

  versio INTEGER NOT NULL DEFAULT 1,  -- Versioinnilla oma taulu, ei liity mhu_muutos tauluun   (nämä eivät ole varsinaisia muutoksia)
  validi_aikana TSTZRANGE NOT NULL DEFAULT TSTZRANGE(CURRENT_TIMESTAMP, NULL), -- Kertoo koska versio ollut aktiivinen 
  urakka    INTEGER NOT NULL REFERENCES urakka(id),                            -- Näillä joinataan data näkymään 
  tehtava   INTEGER NOT NULL REFERENCES tehtava(id),
  hoitokauden_alkuvuosi INTEGER NOT NULL,
  lahde muutos_yksikkohinta_lahde_enum NOT NULL DEFAULT 'laskettu',            -- Yksikköhinnan lähde voi olla edelliseltä hoitokaudelta 
  valitun_yksikkohinnan_hoitokausi INTEGER,
  kasin_syotetty_tavoitehintamuutos NUMERIC(12,2),                             -- Jos yksikköhintaa ei voi laskea, syötetään käsin 
  syy TEXT,
  -----------------------------------------
  luotu    TIMESTAMP NOT NULL DEFAULT NOW(),
  luoja    INTEGER REFERENCES kayttaja(id),
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja(id),

  -- Yksi rivi per / (urakka,tehtava,hoitokauden_alkuvuosi)
  CONSTRAINT mmt_pk PRIMARY KEY (urakka, tehtava, hoitokauden_alkuvuosi),
  CHECK (                                             -- Evaluoidaan kun insertataan tai päivitetään 
    (lahde = 'laskettu'                               -- Kun lähde on laskettu, yksikköhinta voidaan laskea suoraa
    AND valitun_yksikkohinnan_hoitokausi IS NULL 
    AND kasin_syotetty_tavoitehintamuutos IS NULL)
    OR
    (lahde = 'manuaali'                               -- Kun lähde on manuaali, käsin syötetty tavoitehinta hinta on olemassa
    AND kasin_syotetty_tavoitehintamuutos IS NOT NULL 
    AND valitun_yksikkohinnan_hoitokausi IS NULL)
    OR 
    (lahde = 'aseta'                                  -- Kun lähde on aseta, valittu yksikköhinta hinta on olemassa
    AND kasin_syotetty_tavoitehintamuutos IS NULL 
    AND valitun_yksikkohinnan_hoitokausi IS NOT NULL)
  )
);

CREATE INDEX muutos_tehtava_tiedot_idx ON mhu_muutos_tehtava_tiedot (urakka, tehtava, hoitokauden_alkuvuosi, versio);


CREATE TABLE IF NOT EXISTS mhu_muutos_tehtava_tiedot_historia (
  urakka    INTEGER NOT NULL,
  tehtava   INTEGER NOT NULL,
  hoitokauden_alkuvuosi INTEGER NOT NULL,

  lahde muutos_yksikkohinta_lahde_enum NOT NULL,
  valitun_yksikkohinnan_hoitokausi INTEGER,
  kasin_syotetty_tavoitehintamuutos NUMERIC(12,2),
  syy TEXT,

  luotu TIMESTAMP,
  luoja INTEGER,
  muokattu TIMESTAMP,
  muokkaaja INTEGER,

  versio INTEGER NOT NULL,
  validi_aikana TSTZRANGE NOT NULL,

  PRIMARY KEY (urakka, tehtava, hoitokauden_alkuvuosi, versio)
);

CREATE INDEX IF NOT EXISTS muutos_tehtava_historia_idx ON mhu_muutos_tehtava_tiedot_historia (urakka, tehtava, hoitokauden_alkuvuosi);


CREATE OR REPLACE FUNCTION paivita_mhu_muutos_tehtava_tiedot_historia()
RETURNS TRIGGER AS $$
  DECLARE versio_seur int;
BEGIN

  IF TG_OP IN ('UPDATE','DELETE') THEN
    SELECT COALESCE(MAX(versio),0)+1
      INTO versio_seur
      FROM mhu_muutos_tehtava_tiedot_historia v
     WHERE v.urakka = OLD.urakka
       AND v.tehtava = OLD.tehtava
       AND v.hoitokauden_alkuvuosi = OLD.hoitokauden_alkuvuosi;

    INSERT INTO mhu_muutos_tehtava_tiedot_historia
      (urakka, tehtava, hoitokauden_alkuvuosi, lahde,
       valitun_yksikkohinnan_hoitokausi, kasin_syotetty_tavoitehintamuutos, syy,
       luotu, luoja, muokattu, muokkaaja,
       versio, validi_aikana)
    VALUES
      (OLD.urakka, OLD.tehtava, OLD.hoitokauden_alkuvuosi, OLD.lahde,
       OLD.valitun_yksikkohinnan_hoitokausi, OLD.kasin_syotetty_tavoitehintamuutos, OLD.syy,
       OLD.luotu, OLD.luoja, OLD.muokattu, OLD.muokkaaja,
       versio_seur, TSTZRANGE(COALESCE(OLD.muokattu, OLD.luotu, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP));
  END IF;

  IF TG_OP = 'UPDATE' THEN
    NEW.versio := OLD.versio +1;
    NEW.validi_aikana := TSTZRANGE(CURRENT_TIMESTAMP, NULL);
    RETURN NEW;
  ELSE
    RETURN OLD;
  END IF;

END;
$$ LANGUAGE plpgsql;


DROP TRIGGER IF EXISTS muutos_tehtava_update_trigger ON mhu_muutos_tehtava_tiedot;


CREATE TRIGGER muutos_tehtava_update_trigger
  BEFORE UPDATE ON mhu_muutos_tehtava_tiedot
  FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_tehtava_tiedot_historia();
