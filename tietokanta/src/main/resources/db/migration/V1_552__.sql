INSERT INTO integraatio (jarjestelma, nimi) VALUES ('reimari', 'hae-komponenttityypit');
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
       -- Tarvitaanko Harjan vakiot muokkaustiedot?
);
