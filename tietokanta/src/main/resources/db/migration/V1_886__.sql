-- luodaan lupausten tietomalli
CREATE TABLE lupausryhma (
    id SERIAL PRIMARY KEY,
    otsikko TEXT NOT NULL,
    jarjestys INTEGER,
    "urakan-alkuvuosi" INTEGER NOT NULL CHECK ("urakan-alkuvuosi" BETWEEN 2010 AND 2040),
    luotu TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TYPE lupaustyyppi AS ENUM ('yksittainen', 'monivalinta', 'kyselytutkimus');

CREATE TABLE lupaus (
	id SERIAL PRIMARY KEY,
	"lupausryhma-id" INTEGER NOT NULL REFERENCES lupausryhma(id),
	lupaustyyppi lupaustyyppi NOT NULL DEFAULT 'yksittainen',
	"max-pisteet" INTEGER,
	"kysytaan-kkt" INTEGER[], -- kuukaudet milloin lupausta kysytään
	"merkitseva-kk" INTEGER, --  kuukausi mikä ratkaisee onnistumisen tulkinnan
	"joustavara-kkta"  INTEGER CHECK ("joustavara-kkta" BETWEEN  0 AND 13), -- kuinka monta kuukautta lupaus saa epäonnistua, 0 = kerrasta poikki
	sisalto TEXT,
	"urakan-alkuvuosi" INTEGER NOT NULL CHECK ("urakan-alkuvuosi" BETWEEN 2010 AND 2040),
    luotu TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE TABLE lupaus_kommentti (
	"lupaus-id" INTEGER NOT NULL REFERENCES lupaus(id),
    kommentti INTEGER NOT NULL REFERENCES kommentti(id)
);

CREATE TABLE lupaus_vaihtoehto (
    id SERIAL PRIMARY KEY,
    "lupaus-id" INTEGER NOT NULL REFERENCES lupaus(id),
    vaihtoehto TEXT, -- kälissä näytettävä teksti, esim '> 25%'
    pisteet INT -- pisteet mitä urakoitsija saa, jos tämä vaihtoehto valitaan (esim 14)
);

CREATE TABLE lupaus_vastaus (
    "lupaus-id" INTEGER NOT NULL REFERENCES lupaus(id),
    "urakka-id" INTEGER NOT NULL references urakka (id),
    kuukausi INTEGER NOT NULL CHECK (kuukausi BETWEEN 1 AND 12),
    vuosi INTEGER NOT NULL CHECK (vuosi BETWEEN 2010 AND 2040),
	taytetty BOOLEAN, -- sallittava NULL, tällöin vastausta ei ole tai se on poistettu
	"lupaus-vaihtoehto-id" INTEGER REFERENCES lupaus_vaihtoehto (id), -- voi olla NULL esim. yksittäisillä lupauksilla
	"veto-oikeutta-kaytetty" BOOLEAN NOT NULL DEFAULT FALSE,
	"veto-oikeus-aika" TIMESTAMP,
    -- muokkausmetatiedot
    poistettu BOOLEAN DEFAULT FALSE,
    muokkaaja INTEGER REFERENCES kayttaja(id),
    muokattu TIMESTAMP,
    luoja INTEGER NOT NULL REFERENCES kayttaja(id),
    luotu TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE lupaus_email_muistutus(
	id SERIAL PRIMARY KEY,
	"urakka-id" INTEGER NOT NULL REFERENCES urakka (id),
	kuukausi INTEGER NOT NULL CHECK (kuukausi BETWEEN 1 AND 12),
	vuosi INTEGER NOT NULL CHECK (vuosi BETWEEN 2010 AND 2040),
	linkki TEXT NOT NULL,
	lahetetty TIMESTAMP DEFAULT NOW(),
	lahetysid TEXT, -- esim. JMS ID jos sillä tavoin lähetetty palveluväylän kautta email-palveluun
	kuitattu TIMESTAMP,
    lahetysvirhe TEXT -- lähetysvirheen tiedot
);