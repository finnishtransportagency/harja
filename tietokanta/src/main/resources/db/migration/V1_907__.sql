CREATE TABLE kattohinnan_oikaisu
(
    id                      SERIAL PRIMARY KEY,
    "urakka-id"             INTEGER NOT NULL REFERENCES urakka (id),
    "luoja-id"              INTEGER REFERENCES kayttaja (id),
    luotu                   TIMESTAMP,
    "muokkaaja-id"          INTEGER NOT NULL REFERENCES kayttaja (id),
    muokattu                TIMESTAMP,
    "uusi-kattohinta"       NUMERIC NOT NULL,
    "hoitokauden-alkuvuosi" INT     NOT NULL,
    poistettu               BOOLEAN DEFAULT FALSE,
    CONSTRAINT uniikki_urakka_alkuvuosi UNIQUE ("urakka-id", "hoitokauden-alkuvuosi")
);

-- urakka_tavoite.kattohinta
-- 2019 ja 2020 alkaneet urakat jätettiin pois migraatiossa 902
-- tästä aiheutui ongelmia välikatselmuksessa
with indeksikorjaus as (
    select ut.id         as ut_id,
           indeksikorjaa(
                   ut.kattohinta,
                   EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1, -- hoitokauden indeksointi alkaa 1:stä
                   10,
                   u.id) as korjattu
    from urakka_tavoite ut
             join urakka u on ut.urakka = u.id
    WHERE u.tyyppi = 'teiden-hoito'
      and EXTRACT(YEAR FROM u.alkupvm) IN (2019, 2020)
      and (ut.kattohinta_indeksikorjattu is null)
)
update urakka_tavoite ut2
set kattohinta_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                  = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                   = NOW()
from indeksikorjaus
where ut2.id = indeksikorjaus.ut_id
  and indeksikorjaus.korjattu is not null
  and ut2.kattohinta_indeksikorjattu is null;
