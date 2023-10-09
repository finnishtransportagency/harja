-- VHAR-8176 Korjaa indeksikorjaukset 2023 alkaneille urakoille

-- Indeksikorjaa kiinteahintainen_tyo.summa_indeksikorjattu
WITH indeksikorjaus AS (SELECT kt.id AS kt_id,
                               kt.summa,
                               kt.vuosi,
                               kt.kuukausi,
                               u.id,
                               indeksikorjaa(
                                   kt.summa,
                                   kt.vuosi,
                                   kt.kuukausi,
                                   u.id
                                   ) AS korjattu
                        FROM kiinteahintainen_tyo kt
                                 JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
                                 JOIN urakka u ON tpi.urakka = u.id
                        WHERE u.tyyppi = 'teiden-hoito'
                          AND EXTRACT(YEAR FROM u.alkupvm) IN (2023))
UPDATE kiinteahintainen_tyo kt2
SET summa_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja             = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
    muokattu              = NOW()
FROM indeksikorjaus
WHERE kt2.id = indeksikorjaus.kt_id
  AND indeksikorjaus.korjattu IS NOT NULL;


-- Indeksikorjaa kustannusarvioitu_tyo.summa_indeksikorjattu
WITH indeksikorjaus AS (SELECT kt.id AS kt_id,
                               indeksikorjaa(
                                   kt.summa,
                                   kt.vuosi,
                                   kt.kuukausi,
                                   u.id
                                   ) AS korjattu
                        FROM kustannusarvioitu_tyo kt
                                 JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
                                 JOIN urakka u ON tpi.urakka = u.id
                        WHERE u.tyyppi = 'teiden-hoito'
                          AND EXTRACT(YEAR FROM u.alkupvm) IN (2023))
UPDATE kustannusarvioitu_tyo kt2
SET summa_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja             = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
    muokattu              = NOW()
FROM indeksikorjaus
WHERE kt2.id = indeksikorjaus.kt_id
  AND indeksikorjaus.korjattu IS NOT NULL;

-- Indeksikorjaa johto_ja_hallintokorvaus.tuntipalkka_indeksikorjattu
WITH indeksikorjaus AS (SELECT jk.id AS jk_id,
                               indeksikorjaa(
                                   jk.tuntipalkka,
                                   jk.vuosi,
                                   jk.kuukausi,
                                   u.id
                                   ) AS korjattu
                        FROM johto_ja_hallintokorvaus jk
                                 JOIN urakka u ON jk."urakka-id" = u.id
                        WHERE u.tyyppi = 'teiden-hoito'
                          AND EXTRACT(YEAR FROM u.alkupvm) IN (2023))
UPDATE johto_ja_hallintokorvaus jk2
SET tuntipalkka_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                   = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
    muokattu                    = NOW()
FROM indeksikorjaus
WHERE jk2.id = indeksikorjaus.jk_id
  AND indeksikorjaus.korjattu IS NOT NULL;


-- Indeksikorjaa urakka_tavoite.tavoitehinta
WITH indeksikorjaus AS (SELECT ut.id AS ut_id,
                               indeksikorjaa(
                                   ut.tavoitehinta,
                                   -- hoitokauden indeksointi alkaa 1:stä, joten vähennetään 1
                                   EXTRACT(YEAR FROM u.alkupvm)::integer + ut.hoitokausi - 1,
                                   10,
                                   u.id
                                   ) AS korjattu
                        FROM urakka_tavoite ut
                                 JOIN urakka u ON ut.urakka = u.id
                        WHERE u.tyyppi = 'teiden-hoito'
                          AND EXTRACT(YEAR FROM u.alkupvm) IN (2023))
UPDATE urakka_tavoite ut2
SET tavoitehinta_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                    = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
    muokattu                     = NOW()
FROM indeksikorjaus
WHERE ut2.id = indeksikorjaus.ut_id
  AND indeksikorjaus.korjattu IS NOT NULL;


-- Indeksikorjaa urakka_tavoite.tavoitehinta_siirretty
WITH indeksikorjaus AS (SELECT ut.id AS ut_id,
                               indeksikorjaa(
                                   ut.tavoitehinta_siirretty,
                                   -- hoitokauden indeksointi alkaa 1:stä, joten vähennetään 1
                                   EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1,
                                   10,
                                   u.id
                                   ) AS korjattu
                        FROM urakka_tavoite ut
                                 JOIN urakka u ON ut.urakka = u.id
                        WHERE u.tyyppi = 'teiden-hoito'
                          AND EXTRACT(YEAR FROM u.alkupvm) IN (2023)
                          AND ut.tavoitehinta_siirretty IS NOT NULL)
UPDATE urakka_tavoite ut2
SET tavoitehinta_siirretty_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                              = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
    muokattu                               = NOW()
FROM indeksikorjaus
WHERE ut2.id = indeksikorjaus.ut_id
  AND indeksikorjaus.korjattu IS NOT NULL;


-- Indeksikorjaa urakka_tavoite.kattohinta
WITH indeksikorjaus AS (SELECT ut.id AS ut_id,
                               indeksikorjaa(
                                   ut.kattohinta,
                                   -- hoitokauden indeksointi alkaa 1:stä, joten vähennetään 1
                                   EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1,
                                   10,
                                   u.id
                                   ) AS korjattu
                        FROM urakka_tavoite ut
                                 JOIN urakka u ON ut.urakka = u.id
                        WHERE u.tyyppi = 'teiden-hoito'
                          AND EXTRACT(YEAR FROM u.alkupvm) IN (2023))
UPDATE urakka_tavoite ut2
SET kattohinta_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                  = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
    muokattu                   = NOW()
FROM indeksikorjaus
WHERE ut2.id = indeksikorjaus.ut_id
  AND indeksikorjaus.korjattu IS NOT NULL;
