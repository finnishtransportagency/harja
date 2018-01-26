CREATE TABLE kan_silta
(
  id              SERIAL PRIMARY KEY,
  siltanro        INTEGER NOT NULL,
  nimi            TEXT,
  tunnus          TEXT,
  kayttotarkoitus TEXT,
  tila            TEXT,
  pituus          NUMERIC(10, 2),
  rakennetiedot   TEXT [],
  tieosoitteet    TR_OSOITE_LAAJENNETTU [],
  sijainti_lev    TEXT,
  sijainti_pit    TEXT,
  geometria       GEOMETRY,
  avattu          DATE,
  trex_muutettu   DATE,
  trex_oid        TEXT,
  trex_sivu       INTEGER,
  luoja           TEXT,
  luotu           TIMESTAMP,
  muokkaaja       TEXT,
  muokattu        TIMESTAMP,
  poistettu       BOOLEAN
);

CREATE UNIQUE INDEX siltanro_unique_index
  ON kan_silta (siltanro);

-- kan_kohteenosa.kohde-id ei voi olla pakollinen, koska avattavia siltoja
-- ei automaattisesti liitetä kohteeseen, vaikka ne tuodaan tauluun.
ALTER TABLE kan_kohteenosa
  ALTER COLUMN "kohde-id" DROP NOT NULL;

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('trex', 'kanavasillat-haku');
INSERT INTO geometriapaivitys (nimi) VALUES ('kanavasillat')
ON CONFLICT DO NOTHING;
;

-- Muutetaan kanavataulun nimi selkeämmäksi.
-- Rakenne muuten sama.
DROP TABLE kanava;

CREATE TABLE kan_sulku
(
  kanavanro                 INTEGER NOT NULL,
  aluenro                   INTEGER,
  nimi                      VARCHAR,
  kanavatyyppi              VARCHAR,
  aluetyyppi                VARCHAR,
  kiinnitys                 VARCHAR, -- Liikkuvat pollarit jne
  porttityyppi              VARCHAR, -- Salpaus + Nosto/Lasku jne
  kayttotapa                VARCHAR, -- Kaukokäyttö jne
  sulku_leveys              NUMERIC(10, 2),
  sulku_pituus              NUMERIC(10, 2),
  alus_leveys               NUMERIC(10, 2),
  alus_pituus               NUMERIC(10, 2),
  alus_syvyys               NUMERIC(10, 2),
  alus_korkeus              NUMERIC(10, 2),
  sulkumaara                INTEGER,
  putouskorkeus_1           NUMERIC(10, 2),
  putouskorkeus_2           NUMERIC(10, 2),
  alakanavan_alavertaustaso VARCHAR,
  alakanavan_ylavertaustaso VARCHAR,
  ylakanavan_ylavertaustaso VARCHAR,
  ylakanavan_alavertaustaso VARCHAR,
  kynnys_1                  VARCHAR,
  kynnys_2                  VARCHAR,
  vesisto                   VARCHAR,
  kanavakokonaisuus         VARCHAR,
  kanava_pituus             NUMERIC(10, 2),
  kanava_leveys             NUMERIC(10, 2),
  lahtopaikka               VARCHAR,
  kohdepaikka               VARCHAR,
  omistaja                  VARCHAR,
  geometria                 GEOMETRY,
  luoja                     VARCHAR,
  luotu                     TIMESTAMP,
  muokkaaja                 VARCHAR,
  muokattu                  TIMESTAMP,
  poistettu                 BOOLEAN
);

CREATE UNIQUE INDEX kanavanro_unique_index
  ON kan_sulku (kanavanro);

INSERT INTO geometriapaivitys (nimi) VALUES ('kanavat')
ON CONFLICT DO NOTHING;

-- U U S I     S A R A K E    K O H D E T A U L U I H I N
-- LAHDETUNNUS = sarake kanavasulun tai kanavasillan numerolle, kun kohteen osa tuodaan integraatiolla tauluun.
-- Arvo viittaa periaatteessa kan_silta tai kan_sulku-tauluun, mutta ei ole syytä luoda näiden välille
-- virallista viiteyhteyttä. kan_silta ja kan_sulku ovat säilyttävät integraation tuomaa dataa, eivät osallistu sovelluksen toimintaan.
ALTER TABLE kan_kohteenosa
  ADD COLUMN lahdetunnus INTEGER;


-- K Ä Y T T Ä J Ä   K O H D E T A U L U J E N    P Ä I V I T T Ä M I S E E N
-- Tieto tallennetaan luoja-kenttään, joka vaatii viitteen kayttaja-tauluun, joka taas vaatii rivin organisaatiotaulussa.
INSERT INTO organisaatio (id, nimi, tyyppi, harjassa_luotu, luotu)
VALUES (999999, 'Integraatio', 'liikennevirasto', TRUE, current_timestamp)
ON CONFLICT (id)
  DO NOTHING;

INSERT INTO kayttaja (kayttajanimi, sahkoposti, organisaatio, luotu, jarjestelma, kuvaus)
VALUES ('Integraatio', 'harja-integraatiotuki@solita.fi', (SELECT id
                                                           FROM organisaatio
                                                           WHERE nimi = 'Integraatio'), current_timestamp, TRUE,
        'Ajastettu integraatio')
ON CONFLICT (kayttajanimi)
  DO NOTHING;

-- P R O S E D U U R I T   K O H D E T A U L U J E N    P Ä I V I T T Ä M I S E E N

-- K A N A V A S U L U T
CREATE OR REPLACE FUNCTION lisaa_tai_paivita_kanavasulku_kohdetietoihin()
  RETURNS TRIGGER AS $$

-- Triggerifunktio päivittää integraation kautta saadut kanavasulkutiedot (kan_sulku) käyttöliittymän käyttämiin kohdetauluihin: kan_kohdekokonaisuus, kan_kohde, kan_kohteenosa.

DECLARE   integraatiokayttaja INTEGER;
  DECLARE kohteen_osa         INTEGER;
  DECLARE kohde               INTEGER;
  DECLARE kohdekokonaisuus    INTEGER;
  DECLARE oletuskaytotapa     TEXT;

BEGIN

  -- Selvitetään integraatiokäyttäjän id luoja-kenttään tallennusta varten.
  -- Integraatiokäyttäjä on luotu migraatiossa, jos sitä ei aiemmin ole ollut.
  integraatiokayttaja := (SELECT id
                          FROM kayttaja
                          WHERE kayttajanimi = 'Integraatio');

  -- Selvitetään löytyykö sulkuun liittyvä kohteen osa, kohde ja kohdekokonaisuus kannasta. Lähtökohtana on kanavasulkuaineistossa saatu kanavanumero (lähdetunnus)
  kohteen_osa := (SELECT id
                  FROM kan_kohteenosa
                  WHERE lahdetunnus = new."kanavanro");
  kohde := (SELECT "kohde-id"
            FROM kan_kohteenosa
            WHERE lahdetunnus = new."kanavanro");
  kohdekokonaisuus := (SELECT id
                       FROM kan_kohdekokonaisuus
                       WHERE id = (SELECT "kohdekokonaisuus-id"
                                   FROM kan_kohde
                                   WHERE id = kohde));

  -- Jos kohdekokonaisuutta ei vielä löytynyt, selvitetään löytyykö se nimen perusteella.
  IF (kohdekokonaisuus ISNULL)
  THEN
    kohdekokonaisuus := (SELECT id
                         FROM kan_kohdekokonaisuus
                         WHERE nimi = new."kanavakokonaisuus");
  END IF;

  -- Jos kohdeetta ei vielä löytynyt, selvitetään löytyykö se nimen perusteella.
  IF (kohde ISNULL)
  THEN
    kohde := (SELECT id
              FROM kan_kohde
              WHERE nimi = new."nimi");
  END IF;

  -- K O H D E K O K O N A I S U U S
  -- Luo tai päivitä kohdekokonaisuus
  IF (kohdekokonaisuus ISNULL)
  THEN
    INSERT INTO kan_kohdekokonaisuus (nimi, luotu, luoja)
    VALUES (new."kanavakokonaisuus", current_timestamp, integraatiokayttaja);
    kohdekokonaisuus := (SELECT id
                         FROM kan_kohdekokonaisuus
                         WHERE nimi = new."kanavakokonaisuus");
  ELSE
    UPDATE kan_kohdekokonaisuus
    SET nimi = new."kanavakokonaisuus", muokattu = current_timestamp, muokkaaja = integraatiokayttaja
    WHERE id = kohdekokonaisuus;
  END IF;

  -- K O H D E
  -- Luo tai päivitä kohde.
  -- Hox. Kohteen sijainti != kohteen osan sijainti. Kohteella ja kohteen osilla on omat geometriansa.
  IF (kohde ISNULL)
  THEN
    INSERT INTO kan_kohde ("kohdekokonaisuus-id", nimi, luotu, luoja, poistettu)
    VALUES (kohdekokonaisuus, new."nimi", current_timestamp, integraatiokayttaja, new."poistettu");
    kohde := (SELECT id
              FROM kan_kohde
              WHERE nimi = new."nimi");
  ELSE
    UPDATE kan_kohde
    SET
      nimi      = new."nimi",
      muokattu  = current_timestamp,
      muokkaaja = integraatiokayttaja
    WHERE id = kohde;
  END IF;

  -- K O H T E E N   O S A
  -- Luo tai päivitä kohteenosa

  -- Selvitä tallennettava oletuskäyttötapa
  IF (new."kayttotapa" = 'Itsepalvelu')
  THEN oletuskaytotapa = 'itse'; END IF;
  IF (new."kayttotapa" = 'Kaukokäyttö')
  THEN oletuskaytotapa = 'kauko'; END IF;
  IF (new."kayttotapa" = 'Paikalliskäyttö')
  THEN oletuskaytotapa = 'paikallis'; END IF;
  IF (oletuskaytotapa ISNULL)
  THEN oletuskaytotapa = 'muu'; END IF;

  IF (kohteen_osa ISNULL)
  THEN
    INSERT INTO kan_kohteenosa (tyyppi, "kohde-id", oletuspalvelumuoto, luoja, luotu, sijainti, lahdetunnus, poistettu)
    VALUES
      ('sulku' :: KOHTEENOSA_TYYPPI, kohde, oletuskaytotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO, integraatiokayttaja,
       current_timestamp, new."geometria", new."kanavanro", new."poistettu");
  ELSE
    UPDATE kan_kohteenosa
    SET
      "kohde-id"         = kohde,
      oletuspalvelumuoto = oletuskaytotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO,
      sijainti           = ST_Centroid(new."geometria") :: GEOMETRY,
      muokattu        = current_timestamp,
      poistettu = new."poistettu",
      muokkaaja = integraatiokayttaja
    WHERE id = kohteen_osa;
  END IF;

  RETURN new;

END;
$$ LANGUAGE plpgsql;


-- A V A T T A V A T   S I L L A T
CREATE OR REPLACE FUNCTION lisaa_tai_paivita_kanavasilta_kohdetietoihin()
  RETURNS TRIGGER AS $$

-- Triggerifunktio päivittää integraation kautta saadut siltatiedot (kan_silta) käyttöliittymän käyttämään kan_kohteenosa-tauluun.

DECLARE   integraatiokayttaja INTEGER;
  DECLARE kohteen_osa         INTEGER;
  DECLARE oletuskaytotapa     TEXT;
BEGIN

  -- Selvitetään integraatiokäyttäjän id luoja-kenttään tallennusta varten.
  -- Integraatiokäyttäjä on luotu migraatiossa, jos sitä ei aiemmin ole ollut.
  integraatiokayttaja := (SELECT id
                          FROM kayttaja
                          WHERE kayttajanimi = 'Integraatio');

  -- Selvitetään löytyykö siltaan liittyvä kohteen osa kannasta. Lähtökohtana on kanavasilta-aineistossa saatu siltanumero (= kan_kohteenosa.lähdetunnus).
  kohteen_osa := (SELECT id
                  FROM kan_kohteenosa
                  WHERE lahdetunnus = new."siltanro");

  -- K O H T E E N   O S A
  -- Jos kohteen osaa ei ole sovelluksen taulussa, se luodaan.
  -- Oletuskäyttötapa on silloilla aina 'muu'
  oletuskaytotapa = 'muu';

  IF (kohteen_osa ISNULL)
  THEN
    INSERT INTO kan_kohteenosa (tyyppi, nimi, oletuspalvelumuoto, luoja, luotu, sijainti, lahdetunnus, poistettu)
    VALUES
      ('silta' :: KOHTEENOSA_TYYPPI, new."nimi", oletuskaytotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO, integraatiokayttaja,
       current_timestamp, new."geometria", new."siltanro", new."poistettu");
  ELSE
    UPDATE kan_kohteenosa
    SET
      nimi               = new."nimi",
      oletuspalvelumuoto = oletuskaytotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO,
      sijainti           = new."geometria" :: GEOMETRY,
      muokattu        = current_timestamp,
      muokkaaja = integraatiokayttaja,
      poistettu = new."poistettu"
    WHERE id = kohteen_osa;
  END IF;

  RETURN new;

END;
$$ LANGUAGE plpgsql;

-- T R I G G E R I T   K O H D E T A U L U J E N    P Ä I V I T T Ä M I S E E N

-- Lisää tai päivitä kanavasulkuihin liittyvät tiedot sovelluksen käyttämiin
-- tauluihin: kanavakokonaisuus, kan_kohde, kan_kohteenosa
CREATE TRIGGER tg_lisaa_kanavasulku_kohdetietoihin
AFTER INSERT OR UPDATE ON kan_sulku
FOR EACH ROW EXECUTE PROCEDURE lisaa_tai_paivita_kanavasulku_kohdetietoihin();

-- Lisää tai päivitä kanavasiltoihin liittyvät tiedot sovelluksen käyttämiin
-- tauluihin: kan_kohteenosa
CREATE TRIGGER tg_lisaa_kanavasilta_kohdetietoihin
AFTER INSERT OR UPDATE ON kan_silta
FOR EACH ROW EXECUTE PROCEDURE lisaa_tai_paivita_kanavasilta_kohdetietoihin();
