INSERT INTO integraatio (jarjestelma, nimi) VALUES ('reimari', 'hae-komponenttityypit');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('reimari', 'hae-turvalaitekomponentit');
CREATE TABLE reimari_komponenttityyppi (
       "id" TEXT PRIMARY KEY,
       "nimi" TEXT NOT NULL,
       "lisatiedot" TEXT NOT NULL,
       "luokan-id" TEXT NOT NULL,
       "luokan-nimi" TEXT NOT NULL,
       "luokan-lisatiedot" TEXT NOT NULL,
       "luokan-paivitysaika" TIMESTAMP,
       "luokan-luontiaika" TIMESTAMP,
       "merk-cod" TEXT NOT NULL,
       "paivitysaika" TIMESTAMP,
       "luontiaika" TIMESTAMP,
       "muokattu" TIMESTAMP,
       "alkupvm" TIMESTAMP,
       "loppupvm" TIMESTAMP
);
CREATE TABLE reimari_turvalaitekomponentti (
       "id" TEXT PRIMARY KEY,
       "lisatiedot" TEXT NOT NULL,
       "turvalaitenro" TEXT NOT NULL,
       "komponentti-id" TEXT NOT NULL REFERENCES reimari_komponenttityyppi(id),
       "sarjanumero" TEXT NOT NULL,
       "paivitysaika" TIMESTAMP,
       "luontiaika" TIMESTAMP,
       "luoja" TEXT NOT NULL,
       "muokkaaja" TEXT NOT NULL,
       "muokattu" TIMESTAMP,
       "alkupvm" TIMESTAMP,
       "valiaikainen" BOOLEAN NOT NULL DEFAULT FALSE,
       "loppupvm" TIMESTAMP
);
