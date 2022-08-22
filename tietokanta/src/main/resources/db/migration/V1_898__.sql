CREATE TYPE paatoksen_tyyppi AS ENUM (
    'tavoitehinnan-ylitys',
    'kattohinnan-ylitys',
    'tavoitehinnan-alitus',
    'lupaus-bonus',
    'lupaus-sanktio'
    );

CREATE TABLE urakka_paatos
(
    id                      SERIAL PRIMARY KEY,
    "hoitokauden-alkuvuosi" INT NOT NULL,
    "urakka-id"             INTEGER NOT NULL REFERENCES urakka (id),
    -- Tavoite- tai kattohinnan ylityksen tai alituksen määrä. Alitus negatiivisena.
    "hinnan-erotus"         NUMERIC,
    -- Paljonko maksetaan rahana, urakoitsijan ja tilaajan osuudet erikseen.
    "urakoitsijan-maksu"    NUMERIC,
    "tilaajan-maksu"        NUMERIC,
    -- Paljonko siirretään ensi hoitokaudelle. Miinusmerkkinen, jos vähennetään.
    siirto                  NUMERIC,
    tyyppi                  paatoksen_tyyppi,
    -- Millä perusteella on laskettu lupauksien bonus/sanktio (kun tyyppi on lupaus-bonus tai lupaus-sanktio)
    "lupaus-luvatut-pisteet"     INTEGER,
    "lupaus-toteutuneet-pisteet" INTEGER,
    "lupaus-tavoitehinta"        NUMERIC,
    muokattu                TIMESTAMP,
    "muokkaaja-id"          INTEGER REFERENCES kayttaja (id),
    "luoja-id"              INTEGER REFERENCES kayttaja (id) NOT NULL,
    luotu                   TIMESTAMP DEFAULT NOW(),
    poistettu               BOOLEAN DEFAULT false
);

COMMENT ON COLUMN urakka_paatos."hinnan-erotus" IS E'Tavoite- tai kattohinnan ylityksen tai alituksen määrä. Alitus negatiivisena.';
COMMENT ON COLUMN urakka_paatos."urakoitsijan-maksu" IS E'Paljonko maksetaan rahana, urakoitsijan ja tilaajan osuudet erikseen.';
COMMENT ON COLUMN urakka_paatos."siirto" IS E'Paljonko siirretään ensi hoitokaudelle. Miinusmerkkinen, jos vähennetään.';
COMMENT ON COLUMN urakka_paatos."lupaus-luvatut-pisteet" IS E'Millä perusteella on laskettu lupauksien bonus/sanktio (kun tyyppi on lupaus-bonus tai lupaus-sanktio).';
COMMENT ON COLUMN urakka_paatos."lupaus-toteutuneet-pisteet" IS E'Millä perusteella on laskettu lupauksien bonus/sanktio (kun tyyppi on lupaus-bonus tai lupaus-sanktio).';
COMMENT ON COLUMN urakka_paatos."lupaus-tavoitehinta" IS E'Millä perusteella on laskettu lupauksien bonus/sanktio (kun tyyppi on lupaus-bonus tai lupaus-sanktio).';

COMMENT ON TABLE urakka_paatos IS
    E'Kuvaa vuoden päättämisessä tehtäviä päätöksiä tavoite- ja kattohintaan liittyen.

    Kattohinnan ylittyessä urakoitsija voi maksaa yli menevästä osasta tilaajalle, tai summa voidaan
    siirtää seuraavalle hoitovuodelle kustannuksiksi kokonaan tai osittain.

    Tavoitehinnan ylittyessä urakoitsija maksaa tilaajalle.

    Lähtökohtaisesti urakoitsija maksaa summasta 30% tilaajalle tavoitehinnan ylityksissä ja 100% kattohinnan ylityksessä.

    Mikäli tavoitehinta alittuu, tilaaja maksaa puolestaan urakoitsijalle 30%, kuitenkin maksimissaan 3% urakan tavoitehinnasta.
    Tavoitehinnan alittuessa voidaan myös siirtää seuraavan vuoden alennukseksi, tässä myös mahdollisuus tehdä osittain siirto ja maksu.';

ALTER TABLE tavoitehinnan_oikaisu RENAME COLUMN hoitokausi TO "hoitokauden-alkuvuosi";
ALTER TABLE tavoitehinnan_oikaisu ALTER COLUMN poistettu SET DEFAULT false;

-- luodaan lupausten tietomalli

-- pistemäärä, johon urakoitsija sitoutuu (pisteet per hoitokausi)
CREATE TABLE lupaus_sitoutuminen
(
    id          SERIAL PRIMARY KEY,
    "urakka-id" INTEGER   NOT NULL REFERENCES urakka (id),
    pisteet     INTEGER,

    -- muokkausmetatiedot
    poistettu   BOOLEAN            DEFAULT FALSE,
    muokkaaja   INTEGER REFERENCES kayttaja (id),
    muokattu    TIMESTAMP,
    luoja       INTEGER   NOT NULL REFERENCES kayttaja (id),
    luotu       TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE lupaus_sitoutuminen IS E'Pistemäärä, johon urakoitsija sitoutuu (pisteet per hoitokausi).';

CREATE TABLE lupausryhma
(
    id                 SERIAL PRIMARY KEY,
    otsikko            TEXT      NOT NULL,
    jarjestys          INTEGER   NOT NULL, -- 1 = A, 2 = B, ...
    "urakan-alkuvuosi" INTEGER   NOT NULL CHECK ("urakan-alkuvuosi" BETWEEN 2010 AND 2040),
    luotu              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TYPE lupaustyyppi AS ENUM ('yksittainen', 'monivalinta', 'kysely');

CREATE TABLE lupaus
(
    id                 SERIAL PRIMARY KEY,
    jarjestys          INTEGER      NOT NULL,                                                -- lupauksen järjestysnumero, jonka mukaan käli järjestää. Ryhmittelyyn käytettävä lupausryhmän id:tä
    "lupausryhma-id"   INTEGER      NOT NULL REFERENCES lupausryhma (id),
    "urakka-id"        INTEGER REFERENCES urakka (id),                                       -- tämä on tuki tulevaisuuden urakkakohtaisille lupauksille. Oletus nil = voimassa kaikissa urakoissa. Ensisijaisesti urakassa käytetään ko. urakalle erityisesti tehtyä lupausta, muutoin sitä jolla urakka-id on NIL eli ns. yleistä lupausta
    lupaustyyppi       lupaustyyppi NOT NULL DEFAULT 'yksittainen',
    pisteet            INTEGER,
    "kirjaus-kkt"      INTEGER[],                                                            -- kuukaudet milloin lupausta kysytään (ja urakoitsija kirjaa mielipiteensä)
    "paatos-kk"        INTEGER      NOT NULL DEFAULT 9 CHECK ("paatos-kk" BETWEEN 0 AND 12), --  kuukausi milloin lupauksen onnistumisesta päätetään (aluevastaava tekee lopullisen päätöksen). 0 = kaikki
    "joustovara-kkta"  INTEGER CHECK ("joustovara-kkta" BETWEEN 0 AND 12),                   -- kuinka monta kuukautta lupaus saa epäonnistua, 0 = kerrasta poikki
    kuvaus             TEXT,                                                                 -- lyhyt kuvaus
    sisalto            TEXT,
    "urakan-alkuvuosi" INTEGER      NOT NULL CHECK ("urakan-alkuvuosi" BETWEEN 2010 AND 2040),
    luotu              TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN lupaus.jarjestys IS E'Lupauksen järjestysnumero, jonka mukaan käyttöliittymä järjestää. Ryhmittelyyn käytettävä lupausryhmän id:tä.';
COMMENT ON COLUMN lupaus."urakka-id" IS E'Tämä on tuki tulevaisuuden urakkakohtaisille lupauksille. Oletus nil = voimassa kaikissa urakoissa. Ensisijaisesti urakassa käytetään ko. urakalle erityisesti tehtyä lupausta, muutoin sitä jolla urakka-id on NIL eli ns. yleistä lupausta';
COMMENT ON COLUMN lupaus."kirjaus-kkt" IS E'Kuukaudet milloin lupausta kysytään (ja urakoitsija kirjaa mielipiteensä).';
COMMENT ON COLUMN lupaus."paatos-kk" IS E'Kuukausi milloin lupauksen onnistumisesta päätetään (aluevastaava tekee lopullisen päätöksen). 0 = kaikki';
COMMENT ON COLUMN lupaus."joustovara-kkta" IS E'Kuinka monta kuukautta lupaus saa epäonnistua, 0 = kerrasta poikki';
COMMENT ON COLUMN lupaus.kuvaus IS E'Lupauksen lyhyt kuvaus.';
COMMENT ON COLUMN lupaus.sisalto IS E'Lupauksen koko sisältö.';

CREATE TABLE lupaus_vaihtoehto
(
    id          SERIAL PRIMARY KEY,
    "lupaus-id" INTEGER NOT NULL REFERENCES lupaus (id),
    vaihtoehto  TEXT    NOT NULL, -- kälissä näytettävä teksti, esim '> 25%'
    pisteet     INT     NOT NULL  -- pisteet mitä urakoitsija saa, jos tämä vaihtoehto valitaan (esim 14)
);

COMMENT ON COLUMN lupaus_vaihtoehto.vaihtoehto IS E'kälissä näytettävä teksti, esim "> 25%"';
COMMENT ON COLUMN lupaus_vaihtoehto.pisteet IS E'pisteet mitä urakoitsija saa, jos tämä vaihtoehto valitaan (esim 14)';

CREATE TABLE lupaus_vastaus
(
    id                       SERIAL PRIMARY KEY,
    "lupaus-id"              INTEGER   NOT NULL REFERENCES lupaus (id),
    "urakka-id"              INTEGER   NOT NULL REFERENCES urakka (id),
    kuukausi                 INTEGER   NOT NULL CHECK (kuukausi BETWEEN 1 AND 12),
    vuosi                    INTEGER   NOT NULL CHECK (vuosi BETWEEN 2010 AND 2040),
    paatos                   BOOLEAN            DEFAULT FALSE,          -- yleensä hoitokauden lopussa työmaakokouksessa aluevastaava tekee lopullisen päätöksen yhdessä urakoisijan kanssa. Päätös on se mikä ratkaisee, ja urakoitsijan täyttämät vastaukset ovat 'ennusteita'.
    vastaus                  BOOLEAN,                                   -- sallittava NULL, tällöin vastaus on poistettu (tai vaihtoehto-tyyppinen lupaus)
    "lupaus-vaihtoehto-id"   INTEGER REFERENCES lupaus_vaihtoehto (id), -- voi olla NULL esim. yksittäisillä lupauksilla
    "veto-oikeutta-kaytetty" BOOLEAN   NOT NULL DEFAULT FALSE,
    "veto-oikeus-aika"       TIMESTAMP,
    -- muokkausmetatiedot
    poistettu                BOOLEAN            DEFAULT FALSE,
    muokkaaja                INTEGER REFERENCES kayttaja (id),
    muokattu                 TIMESTAMP,
    luoja                    INTEGER   NOT NULL REFERENCES kayttaja (id),
    luotu                    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT lupaus_vastaus_unique UNIQUE ("lupaus-id", "urakka-id", kuukausi, vuosi)
);

COMMENT ON COLUMN lupaus_vastaus.paatos IS E'Yleensä hoitokauden lopussa työmaakokouksessa aluevastaava tekee lopullisen päätöksen yhdessä urakoisijan kanssa. Päätös on se mikä ratkaisee, ja urakoitsijan täyttämät vastaukset ovat ''ennusteita''.';
COMMENT ON COLUMN lupaus_vastaus.vastaus IS E'Sallittava NULL, tällöin vastaus on poistettu (tai vaihtoehto-tyyppinen lupaus).';
COMMENT ON COLUMN lupaus_vastaus."lupaus-vaihtoehto-id" IS E'Voi olla NULL esim. yksittäisillä lupauksilla.';

CREATE INDEX lupaus_vastaus_lupaus_id_idx ON lupaus_vastaus("lupaus-id");
CREATE INDEX lupaus_vastaus_urakka_id_idx ON lupaus_vastaus("urakka-id");
CREATE INDEX lupaus_vastaus_vaihtoehto_id_idx ON lupaus_vastaus("lupaus-vaihtoehto-id");
CREATE INDEX lupaus_vastaus_muokkaaja_id_idx ON lupaus_vastaus(muokkaaja);
CREATE INDEX lupaus_vastaus_luoja_id_idx ON lupaus_vastaus(luoja);

CREATE TABLE lupaus_kommentti
(
    -- Ei viitata lupaus_vastaus -tauluun, koska lupausta voi kommentoida ennen vastaamista
    "lupaus-id"    INTEGER NOT NULL REFERENCES lupaus (id),
    "urakka-id"    INTEGER NOT NULL REFERENCES urakka (id),
    kuukausi       INTEGER NOT NULL CHECK (kuukausi BETWEEN 1 AND 12),
    vuosi          INTEGER NOT NULL CHECK (vuosi BETWEEN 2010 AND 2040),
    "kommentti-id" INTEGER NOT NULL REFERENCES kommentti (id),
    CONSTRAINT lupaus_kommentti_unique UNIQUE ("kommentti-id")
);
CREATE INDEX lupaus_kommentti_lupaus_id_idx ON lupaus_kommentti("lupaus-id");
CREATE INDEX lupaus_kommentti_urakka_id_idx ON lupaus_kommentti("urakka-id");


CREATE TABLE lupaus_email_muistutus
(
    id           SERIAL PRIMARY KEY,
    "urakka-id"  INTEGER NOT NULL REFERENCES urakka (id),
    kuukausi     INTEGER NOT NULL CHECK (kuukausi BETWEEN 1 AND 12),
    vuosi        INTEGER NOT NULL CHECK (vuosi BETWEEN 2010 AND 2040),
    linkki       TEXT    NOT NULL,
    lahetetty    TIMESTAMP DEFAULT NOW(),
    lahetysid    TEXT, -- esim. JMS ID jos sillä tavoin lähetetty palveluväylän kautta email-palveluun
    kuitattu     TIMESTAMP,
    lahetysvirhe TEXT  -- lähetysvirheen tiedot
);
CREATE INDEX lupaus_email_urakka_id_idx ON lupaus_email_muistutus("urakka-id");

-- lupaus_pisteet 2019 ja 2020 alkaville urakoille
CREATE TYPE lupaus_pisteet_tyyppi AS ENUM (
    'toteuma',
    'ennuste');

COMMENT ON TYPE lupaus_pisteet_tyyppi IS E'lupaus_pisteet 2019 ja 2020 alkaville urakoille';

CREATE TABLE lupaus_pisteet
(
    id             SERIAL PRIMARY KEY,
    "urakka-id"    INTEGER   NOT NULL REFERENCES urakka (id),
    kuukausi       INTEGER   NOT NULL CHECK (kuukausi BETWEEN 1 AND 12),
    vuosi          INTEGER   NOT NULL CHECK (vuosi BETWEEN 2010 AND 2040),
    pisteet        INT NOT NULL, -- saadaan excelistä
    tyyppi         lupaus_pisteet_tyyppi default 'ennuste',
    muokkaaja      INTEGER REFERENCES kayttaja (id),
    muokattu       TIMESTAMP,
    luoja          INTEGER   NOT NULL REFERENCES kayttaja (id),
    luotu          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT lupaus_ennuste_unique UNIQUE ("urakka-id", kuukausi, vuosi)
);
CREATE INDEX lupaus_pisteet_urakka_id_idx ON lupaus_pisteet ("urakka-id");
COMMENT ON TABLE lupaus_pisteet IS
    E'Vuonna 2019/2020 alkaville urakoille ei täytetä kuukausittain lupauksia vaan pisteet, joiden avulla
    voidaan näyttää mahdollinen bonus/sanktio sekä kertoa urakan sujumisestä laajemmalle yleisölle.
    Pisteet ovat pääsääntöisesti tyyppiä ennuste, mutta syyskuussa annetaan toteutuneet pisteet.';

-- Lupausten pohjadata hoitokaudelle 2021-2022
INSERT INTO lupausryhma(otsikko, jarjestys, "urakan-alkuvuosi", luotu)
VALUES
    ('Kannustavat alihankintasopimukset', 1, 2021, NOW()),
    ('Toiminnan suunnitelmallisuus', 2, 2021, NOW()),
    ('Laadunvarmistus ja reagointikyky', 3, 2021, NOW()),
    ('Turvallisuus ja osaamisen kehittäminen', 4, 2021, NOW()),
    ('Viestintä ja tienkäyttäjäasiakkaan palvelu', 5, 2021, NOW());



-- Funktio jolla lisätään lupaukselle vaihtoehdot
CREATE OR REPLACE FUNCTION luo_lupauksen_vaihtoehto(
    lupauksen_jarjestys INTEGER,
    lupauksen_urakan_alkuvuosi INTEGER,
    vaihtoehto_str TEXT,
    pistemaara INTEGER)
    RETURNS VOID AS $$
BEGIN
    INSERT INTO lupaus_vaihtoehto ("lupaus-id", vaihtoehto, pisteet)
    VALUES ((SELECT id FROM lupaus WHERE "urakan-alkuvuosi" = lupauksen_urakan_alkuvuosi AND jarjestys = lupauksen_jarjestys), vaihtoehto_str, pistemaara);

END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION luo_lupauksen_vaihtoehto IS E'Funktio jolla lisätään lupaukselle vaihtoehdot';

INSERT INTO lupaus (jarjestys, "lupausryhma-id", "urakka-id", lupaustyyppi, "pisteet", "kirjaus-kkt", "paatos-kk", "joustovara-kkta", kuvaus, sisalto, "urakan-alkuvuosi") VALUES

-- A. Kannustavat alihankintasopimukset
(1, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset'), null, 'yksittainen', 8, '{10}', 6, 0,
 'Talvihoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa talvihoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
€ tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2021),
(2, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset'), null, 'yksittainen', 8, '{10}', 9, 0,
 'Kesähoidon kannustinjärjestelmä',
 'Kehitämme yhdessä tilaajan kanssa kesähoidon alihankkijoiden kannustinjärjestelmän, joka on
käytössä vähintään kahdessa alihankintasopimuksessamme. Lupaus täyttyy myös
kannustinjärjestelmän kehittämisen ja käyttöönoton jälkeisinä hoitovuosina, mikäli sama
järjestelmä on edelleen käytössä. Tilaaja on varannut vuosittain 5 000 € ja me vähintään 15 000
€ tämän lupauksen kannustinjärjestelmään. Tilaajan ja meidän rahavarauksemme yhdistetään
ja tätä summaa käytetään samassa suhteessa maksettaessa mahdollisia yksittäisiä kannusteita.',
 2021),
(3, (SELECT id FROM lupausryhma WHERE otsikko = 'Kannustavat alihankintasopimukset'), null, 'kysely', 14, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Kyselytutkimus alihankkijoille',
 'Kyselytutkimus alihankkijoille (6 sisäistä pistevaihtoehtoa). Tarjoaja antaa lupauksen
tarjoamansa hoitourakan kyselytutkimuksen keskiarvosta.',
 2021),

-- B. Toiminnan suunnitelmallisuus
(4, (SELECT id FROM lupausryhma WHERE otsikko = 'Toiminnan suunnitelmallisuus'), null, 'yksittainen', 10, null, 0, 1,
 'Kuukausittainen töiden suunnittelu',
 'Suunnittelemme yhdessä tilaajan ja alihankkijoiden kanssa urakan töitä vähintään kerran
kuukaudessa. Töitä voidaan suunnitella esimerkiksi palaverein tai sähköisin menettelyin.
Suunnittelussa ja töiden sisältöjen (laatuvaatimukset, töiden yhteensovittaminen yms.)
läpikäynnissä tulee olla mukana ne alihankkijatahot, jotka tulevat tekemään töitä urakassa
seuraavan kuukauden aikana.',
 2021),
-- C. Laadunvarmistus ja reagointikyky
(5, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky'), null, 'monivalinta', 10, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Kunnossapitoilmoitukset',
 'Toimenpiteitä aiheuttaneiden ilmoitusten (urakoitsijaviestien) %-osuus talvihoitoon ja sorateiden
kunnossapitoon liittyvistä ilmoituksista. (6 sisäistä pistevaihtoehtoa).',
 2021),
(6, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky'), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Luovutuksen menettely',
 'Meillä (pääurakoitsijalla) on käytössä itselle luovutuksen menettely määräaikaan sidotuista töistä
/ työkokonaisuuksista, varusteiden ja laitteiden lisäämisestä ja uusimisesta, sorateiden ja siltojen
hoidosta sekä ojituksesta. Alihankkijamme tekevät itselle luovutuksen vastaavista omista
töistään / työkokonaisuuksista, jotka tarkastamme ennen tilaajalle luovuttamista.',
 2021),
(7, (SELECT id FROM lupausryhma WHERE otsikko = 'Laadunvarmistus ja reagointikyky'), null, 'yksittainen', 5, '{10, 11, 12, 1, 2, 3, 4, 5}', 6, 0,
 'Talvihoidon pistokokeet',
 'Teemme urakassa muuttuvissa keliolosuhteissa laadunseurantaa myös pistokokeina > 6 kertaa
 talvessa (esim. toimenpideajassa pysyminen, työn jälki, työmenetelmä, reagointikyky ja
 liukkaudentorjuntamateriaalien annosmäärät), joista kolme tehdään klo 20–06 välillä ja/tai
 viikonloppuisin. Laadimme jokaisesta pistokokeesta erillisen raportin ja luovutamme sen tilaajalle
 viimeistään seuraavassa työmaakokouksessa.',
 2021),

-- D. Turvallisuus ja osaamisen kehittäminen
(8, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen'), null, 'yksittainen', 5, null, 0, 0,
 'Työturvallisuuden raportointi',
 'Seuraamme urakassa systemaattisesti työturvallisuutta vaarantavia läheltä piti -tilanteita ja
teemme korjaavia toimenpiteitä ko. tilanteiden vähentämiseksi. Raportoimme em. tilanteet sekä
niihin liittyvät suunnitellut ja/tai tehdyt toimenpiteet tilaajalle työmaakokouksien yhteydessä.',
 2021),
(9, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen'), null, 'yksittainen', 5,
 '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Turvallisuuden teemakokoukset',
 'Pidämme vähintään 80 %:lle alihankkijoiden operatiivisesta henkilöstöstä vuosittain
työlajikohtaiset tai synergisesti yli työlajien nivoutuvat turvallisuuden teemakokoukset.
Kokouksien ohjelmat ja osallistujalistat todetaan viimeistään kokousta seuraavassa
työmaakokouksessa',
 2021),
(10, (SELECT id FROM lupausryhma WHERE otsikko = 'Turvallisuus ja osaamisen kehittäminen'), null, 'yksittainen', 5,
 '{10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8}', 9, 0,
 'Koulutukset',
 'Järjestämme urakassa koulutuksia, joiden aiheita voivat olla esim. menetelmätieto,
laatutietoisuus, raportointi, seurantalaitteiden käyttö ja työturvallisuus. Järjestämäämme
koulutukseen (1 htp / hoitovuosi) osallistuu vähintään 1 alihankkijan henkilö kultakin
sopimussuhteessa olevalta alihankkijalta. Osallistumisvelvollisuus on kirjattu
alihankintasopimuksiimme.',
 2021),
-- E. Viestintä ja tienkäyttäjäasiakkaan palvelu
(11, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu'), null, 'yksittainen', 2, null, 0, 0,
 'Tilanne- ja ennakkotiedotus',
 'Toteutamme tilanne- ja ennakkotiedotusta vähintään 4 kertaa kuukaudessa.',
 2021),
(12, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu'), null, 'yksittainen', 12, null, 9, 0,
 'Viestintä sidosryhmien kanssa',
 'Tunnistamme urakka-alueen tärkeimmät sidosryhmät (esim. Vapo, metsäyhtiöt, linja-autoyhtiöt,
koululaiskuljetukset, yms.). Sovimme hoitovuosittain heidän kanssaan käytävästä
vuoropuhelusta ja viestinnästä. Vuoropuhelun perusteella kehitämme toimintaamme siten, että
sidosryhmien tarpeet sopimuksen puitteissa tulevat huomioiduiksi mahdollisimman hyvin.
Olemme yhteydessä paikallismedioihin ja sovimme hoitovuosittain heidän kanssaan käytävästä
vuoropuhelusta ja viestinnästä.',
 2021),
(13, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu'), null, 'yksittainen', 8, null, 0, 0,
 'Palautteet ja kehittäminen',
 'Toimitamme tienkäyttäjäpalautteet ja urakoitsijaviestit henkilöstön ja alihankkijoiden
tietoisuuteen viikoittain. Näiden palautteiden ja omien sekä alihankkijoidemme havaintojen
perusteella kehitämme ja teemme tienkäyttäjiä palvelevia toimenpiteitä esim. reititykseen,
työmenetelmiin ja alihankinnan ohjaukseen. Keskustelemme kehittämistoimista tilaajan kanssa
sekä huomioimme ne viestinnässä.',
 2021),
(14, (SELECT id FROM lupausryhma WHERE otsikko = 'Viestintä ja tienkäyttäjäasiakkaan palvelu'), null, 'yksittainen', 3, null, 9, 0,
 'Tyytyväisyystutkimustulokset',
 'Teemme Talven tienkäyttäjätyytyväisyystutkimustuloksista (ml. vapaat vastaukset) analyysin
kerran vuodessa. Saatamme tutkimuksen ja analyysin tulokset henkilöstön ja alihankkijoiden
tietoisuuteen. Huomioimme havaitut kehitystarpeet toiminnassa ja viestinnässä. Esitämme
analyysit, havainnot ja kehitystoimet tilaajalle 2 kk:n kuluessa tulosten saamisesta.',
 2021);

-- kyselytutkimus alihankkijoille lupaukset 3 osalta toimitetaan luultavasti muualla kuin Harjassa.
-- Harjassa valitaan vain monivalintana saatu tulos
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2021, '<= 4,1 ', 0);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2021, '> 4,1', 2);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2021, '> 4,4', 4);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2021, '> 4,7', 6);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2021, '> 5,0', 10);
SELECT * FROM luo_lupauksen_vaihtoehto(3, 2021, '> 5.3', 14);

SELECT * FROM luo_lupauksen_vaihtoehto(5, 2021, '> 25 % / hoitovuosi', 0);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2021, '10-25 % / hoitovuosi', 2);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2021, '15-20 % / hoitovuosi', 4);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2021, '10-15 % / hoitovuosi', 6);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2021, '5-10 % / hoitovuosi', 8);
SELECT * FROM luo_lupauksen_vaihtoehto(5, 2021, '0-5 % / hoitovuosi', 10);
