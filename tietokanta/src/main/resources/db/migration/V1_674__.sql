CREATE TABLE kan_silta
(
  siltanro        INTEGER NOT NULL,
  nimi            VARCHAR,
  tunnus          VARCHAR,
  kayttotarkoitus VARCHAR,
  tila            VARCHAR,
  pituus          NUMERIC(10, 2),
  rakennetiedot   VARCHAR [],
  tieosoitteet    VARCHAR [],
  sijainti_lev    VARCHAR,
  sijainti_pit    VARCHAR,
  geometria       GEOMETRY,
  avattu          DATE,
  trex_muutettu   DATE,
  trex_oid        VARCHAR,
  trex_sivu       INTEGER,
  luoja           VARCHAR,
  luotu           TIMESTAMP,
  muokkaaja       VARCHAR,
  muokattu        TIMESTAMP
);

-- MITä NÄÄ TIEDOt ON?
-- "aloitus":0,
-- "ajankohta":1514890565190

CREATE UNIQUE INDEX siltanro_unique_index
  ON kan_silta (siltanro);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('trex', 'kanavasillat-haku');
INSERT INTO geometriapaivitys (nimi) VALUES ('kanavat') ON CONFLICT DO NOTHING;
INSERT INTO geometriapaivitys (nimi) VALUES ('kanavasillat') ON CONFLICT DO NOTHING;;

-- Muutetaan kanavataulun nimi vastaamaan kokonaisuutta
-- Rakenne muuten sama

-- Taulun nimi muutettu ennen käyttöönottoa. Sisältö tuodaan integraatiolla.
DROP TABLE kanava;

CREATE TABLE kan_sulku
(
  kanavanro                 INTEGER NOT NULL,
  aluenro                   INTEGER,
  nimi                      VARCHAR,
  kanavatyyppi              VARCHAR,
  aluetyyppi                VARCHAR,
  kiinnitys                 VARCHAR, -- (Liikkuvat pollarit TODO: listaa mahdolliset arvot)
  porttityyppi              VARCHAR, -- (Salpaus + Nosto/Lasku TODO: listaa mahdolliset arvot)
  kayttotapa                VARCHAR, -- (Kaukokäyttö TODO: listaa mahdolliset arvot)
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
  muokattu                  TIMESTAMP
);

CREATE UNIQUE INDEX kanavanro_unique_index
  ON kan_sulku (kanavanro);


-- lahdetunnus = sarake kanavasulun tai kanavasillan numerolle integraatioiden kautta saatavassa materiaalissa
-- viittaa periaatteessa kan_silta tai kan_sulku-tauluun, mutta ei ole syytä luoda näiden välille
-- virallista viiteyhteyttä. kan_silta ja kan_sulku ovat integraatiodatansäilytystauluja.
-- integraatio = viimeisin ajankohta, kun integraatio on päivittänyt riviä. Halutaan säilyttää tieto, jos käyttäjä on koskenut tauluun, joten
-- integraatioon liittyvä triggeri, joka päivittää taulua, ei käytä modifier/modified-kenttiä.
ALTER TABLE kan_kohteenosa
  ADD COLUMN lahdetunnus INTEGER,
  ADD COLUMN integraatio TIMESTAMP;

ALTER TABLE kan_kohde
  ADD COLUMN integraatio TIMESTAMP;

ALTER TABLE kan_kohdekokonaisuus
  ADD COLUMN integraatio TIMESTAMP;


-- K Ä Y T T Ä J Ä   K O H D E T A U L U J E N    P Ä I V I T T Ä M I S E E N
-- Tieto tallennetaan luoja-kenttään, joka vaatii viitteen kayttaja-tauluun.
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

CREATE OR REPLACE FUNCTION lisaa_tai_paivita_kanavasulku_kohdetietoihin ()
RETURNS trigger AS $$

-- Triggerifunktio päivittää integraation kautta saadut kanavasulkutiedot (kan_sulku) käyttöliittymän käyttämiin
-- kohdetauluihin: kan_kohdekokonaisuus, kan_kohde, kan_kohteenosa.
-- Käyttäjän tekemiä muutoksia kohdetauluihin ei ylikirjoiteta (paitsi oletuskäyttötapa ja geometria).

DECLARE integraatiokayttaja                  INTEGER;
  DECLARE kohteen_osa                          INTEGER;
  DECLARE kohde                                INTEGER;
  DECLARE kohdekokonaisuus                     INTEGER;
  DECLARE kohteen_osa_kayttajan_muokkaama      BOOLEAN;
  DECLARE kohde_kayttajan_muokkaama            BOOLEAN;
  DECLARE kohdekokonaisuus_kayttajan_muokkaama BOOLEAN;
  DECLARE kohteen_osa_kayttajan_luoma          BOOLEAN;
  DECLARE kohde_kayttajan_luoma                BOOLEAN;
  DECLARE kohdekokonaisuus_kayttajan_luoma     BOOLEAN;
  DECLARE oletuskaytotapa                      TEXT;

BEGIN

  -- Selvitetään integraatiokäyttäjän id luoja-kenttään tallennusta varten.
  -- Integraatiokäyttäjä on luotu migraatiossa, jos sitä ei aiemmin ole ollut.
  integraatiokayttaja := (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio');

  -- Selvitetään löytyykö sulkuun liittyvä kohteen osa, kohde ja kohdekokonaisuus kannasta. Lähtökohtana on kanavasulkuaineistossa saatu kanavanumero (lähdetunnus)
  kohteen_osa := (SELECT id from kan_kohteenosa where lahdetunnus = new."kanavanro");
  kohde := (SELECT "kohde-id" from kan_kohteenosa where lahdetunnus = new."kanavanro");
  kohdekokonaisuus := (SELECT id from kan_kohdekokonaisuus WHERE id = (select "kohdekokonaisuus-id" from kan_kohde where id = kohde));

  -- Jos kohdekokonaisuutta ei vielä löytynyt, selvitetään löytyykö se nimen perusteella.
  IF (kohdekokonaisuus ISNULL)
  THEN
    kohdekokonaisuus := (SELECT id from kan_kohdekokonaisuus WHERE nimi = new."kanavakokonaisuus");
  END IF;

  -- Selvitetään onko käyttäjä muokannut kohteen osaa, kohdetta tai kohdekokonaisuutta.
  -- Käyttäjän muutosten päälle ei päivitetä integraation kautta tulleita nimitietoja tai kohdelinkityksiä.
  kohteen_osa_kayttajan_muokkaama :=  (SELECT EXISTS(SELECT muokkaaja FROM kan_kohteenosa WHERE id = kohteen_osa AND muokkaaja != integraatiokayttaja));
  kohde_kayttajan_muokkaama := (SELECT EXISTS(SELECT muokkaaja FROM kan_kohde WHERE id = kohde AND muokkaaja != integraatiokayttaja));
  kohdekokonaisuus_kayttajan_muokkaama := (SELECT EXISTS(SELECT muokkaaja FROM kan_kohdekokonaisuus WHERE id = kohdekokonaisuus AND muokkaaja != integraatiokayttaja));

  kohteen_osa_kayttajan_luoma := (SELECT EXISTS(SELECT luoja FROM kan_kohteenosa WHERE id = kohteen_osa AND luoja != integraatiokayttaja));
  kohde_kayttajan_luoma := (SELECT EXISTS(SELECT luoja FROM kan_kohde WHERE id = kohde AND luoja != integraatiokayttaja));
  kohdekokonaisuus_kayttajan_luoma := (SELECT EXISTS(SELECT luoja FROM kan_kohdekokonaisuus WHERE id = kohdekokonaisuus AND luoja != integraatiokayttaja));

  -- K O H D E K O K O N A I S U U S
  -- Jos kohdekokonaisuutta ei ole sovelluksen taulussa, se luodaan.
  -- Olemassa olevan kohdekokonaisuuden nimi päivitetään, jos se ei ole käyttäjän luoma tai muokkaama.
  -- Integraatio eli tämä proseduuri ei saa päivittää muokkaaja-tietoa. Muokkausaika tallennetaan integraatio-kenttään.
  IF (kohdekokonaisuus ISNULL)
  THEN
    INSERT INTO kan_kohdekokonaisuus (nimi, luotu, luoja) VALUES (new."kanavakokonaisuus", current_timestamp, integraatiokayttaja);
    kohdekokonaisuus := (SELECT id FROM kan_kohdekokonaisuus WHERE nimi = new."kanavakokonaisuus");
  ELSE
    IF (kohdekokonaisuus_kayttajan_luoma = false AND kohdekokonaisuus_kayttajan_muokkaama = false)
    THEN
      UPDATE kan_kohdekokonaisuus SET nimi = new."kanavakokonaisuus", integraatio = current_timestamp WHERE id = kohdekokonaisuus;
    END IF;
  END IF;

  -- K O H D E
  -- Jos kohdetta ei ole sovelluksen taulussa, se luodaan.
  -- Olemassa olevan, käyttäjän luomaa tai muokkaamaa kohdetta ei päivitetä - muutoin päivitetään nimi. Kohteen sijainti != kohteen osan sijainti.
  IF (kohde ISNULL)
  THEN
    INSERT INTO kan_kohde ("kohdekokonaisuus-id", nimi, luotu, luoja)
    VALUES (kohdekokonaisuus, new."nimi", current_timestamp, integraatiokayttaja);
    kohde := (SELECT id
              FROM kan_kohde
              WHERE nimi = new."nimi"
              LIMIT 1); -- Aineistossa voi tulla sama kanavasulku monta kertaa. Se saattaa aiheuttaa tässä kohtaa virheen.
  ELSE
    IF (kohde_kayttajan_luoma = FALSE AND kohde_kayttajan_muokkaama = FALSE)
    THEN
      UPDATE kan_kohde
      SET
        nimi        = new."nimi",
        integraatio = current_timestamp
      WHERE id = kohde;
    END IF;
  END IF;

  -- K O H T E E N   O S A
  -- Jos kohteen osaa ei ole sovelluksen taulussa, se luodaan.
  -- Olemassa olevan, käyttäjän luoman tai muokkaaman kohteen osan nimeä tai kohdelinkkausta ei päivitetä. Oletuspalvelumuoto ja sijainti päivitetään.
  -- Integraatio eli tämä proseduuri ei saa päivittää muokkaaja-tietoa. Muokkausaika tallennetaan integraatio-kenttään.

  -- Selvitä tallennettava oletuskäyttötapa
  IF (new."kayttotapa"= 'Itsepalvelu') THEN oletuskaytotapa = 'itse'; END IF;
  IF (new."kayttotapa"= 'Kaukokäyttö') THEN oletuskaytotapa = 'kauko'; END IF;
  IF (new."kayttotapa"= 'Paikalliskäyttö') THEN oletuskaytotapa = 'paikallis'; END IF;
  IF (oletuskaytotapa ISNULL) THEN oletuskaytotapa = 'muu'; END IF;

  IF (kohteen_osa ISNULL) THEN
    INSERT INTO kan_kohteenosa (tyyppi, "kohde-id", oletuspalvelumuoto, luoja, luotu, sijainti, lahdetunnus)
    VALUES
      ('sulku' ::KOHTEENOSA_TYYPPI, kohde, oletuskaytotapa ::LIIKENNETAPAHTUMA_PALVELUMUOTO, integraatiokayttaja, current_timestamp, new."geometria", new."kanavanro");
  ELSE
    IF (kohdekokonaisuus_kayttajan_luoma = FALSE AND kohdekokonaisuus_kayttajan_muokkaama = FALSE)
    THEN
      UPDATE kan_kohteenosa
      SET
        "kohde-id"         = kohde,
        oletuspalvelumuoto = oletuskaytotapa ::LIIKENNETAPAHTUMA_PALVELUMUOTO,
        sijainti           = new."geometria" :: GEOMETRY,
        integraatio        = current_timestamp
      WHERE id = kohteen_osa;
    ELSE
      UPDATE kan_kohteenosa
      SET
        oletuspalvelumuoto = oletuskaytotapa ::LIIKENNETAPAHTUMA_PALVELUMUOTO, -- type LIIKENNETAPAHTUMA_PALVELUMUOTO - ENUM ('kauko', 'itse', 'paikallis', 'muu');
        sijainti           = new."geometria" :: GEOMETRY,
        integraatio        = current_timestamp
      WHERE id = kohteen_osa;
    END IF;
  END IF;

  RETURN new;

END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION lisaa_tai_paivita_kanavasilta_kohdetietoihin()
  RETURNS TRIGGER AS $$
DECLARE kayttaja INTEGER;

BEGIN

  kayttaja := (SELECT muokkaaja
  FROM kan_kohteenosa
  WHERE lahdetunnus = new."siltanro");

  IF (COUNT(kayttaja) > 0)
  THEN
    RAISE NOTICE 'LÖYTYY RIVI %', new."siltanro";
    IF (NOTNUL(kayttaja))
    THEN
      RAISE NOTICE 'Käyttäjä on muokannut %', new."modifier";
    END IF;
  ELSE
    RAISE NOTICE 'EI LÖYDY RIVIÄ %', new."siltanro";
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

