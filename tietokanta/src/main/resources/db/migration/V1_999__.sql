/* VHAR-5485: Migraatio kustannussuunnitelman olemassa olevalle datalle
- [X] päivitä kaikki teiden hoito -tyyppiset urakat, (jotka ovat alkaneet 2019 tai myöhemmin)
- [X] mitä tauluja pitää päivittää?
  - [X] kiinteahintainen_tyo (indeksikorjattu summa)
  - [X] kustannusarvioitu_tyo (indeksikorjattu summa)
  - [X] johto_ja_hallintokorvaus (indeksikorjattu tuntihinta)
  - [X] urakka_tavoite (indeksikorjattu kattohinta / tavoitehinta / siirretty tavoitehinta)
- [ ] automaattinen vahvistaminen
- [ ] osion päätteleminen
- [ ] testaa esim. niin, että vertaa käyttöliittymän kautta tallennettuja lukuja ja kyselyn tuottamia lukuja keskenään
*/

-- Kopioitu funktio testidata_indeksikorjaa
-- TODO: tarkista, onko tämä hyvä funktio tähän tarkoitukseen
-- Testidatan generointia varten halutaan helppo tapa indeksikorjata MHU:iden kuluja.
CREATE OR REPLACE FUNCTION indeksikorjaa(korjattava_arvo NUMERIC, vuosi_ INTEGER, kuukausi_ INTEGER,
                                         urakka_id INTEGER)
    RETURNS NUMERIC AS
$$
DECLARE
    -- Perusluku on urakalle sama riippumatta kuluvasta hoitokaudesta
    perusluku      NUMERIC := indeksilaskennan_perusluku(urakka_id);
    indeksin_nimi  TEXT    := (SELECT indeksi
                               FROM urakka u
                               WHERE u.id = urakka_id);
    -- Indeksikerroin pyöristetään kahdeksaan desimaaliin.
    indeksikerroin NUMERIC;
BEGIN
    -- Indeksikerroin on hoitokausikohtainen, katsotaan aina edellisen hoitokauden syyskuun indeksiä.
    IF kuukausi_ BETWEEN 1 AND 9
    THEN
        indeksikerroin := (SELECT round((arvo / perusluku), 8)
                           FROM indeksi i
                           WHERE i.vuosi = vuosi_ - 1
                             AND i.kuukausi = 9
                             AND nimi = indeksin_nimi);
    ELSE
        indeksikerroin := (SELECT round((arvo / perusluku), 8)
                           FROM indeksi i
                           WHERE i.vuosi = vuosi_
                             AND i.kuukausi = 9
                             AND nimi = indeksin_nimi);
    END IF;
    -- Ja tallennettava arvo kuuteen. TODO: Tarkistettava, pitääkö sekin olla 8.
    return round(korjattava_arvo * indeksikerroin, 6);
END ;
$$ language plpgsql;

-- kiinteahintainen_tyo.summa_indeksikorjattu
-- Parannettavaa: ei ajeta funktiota turhaan, jos urakalle ei ole indeksiä annetulle kuukaudelle
-- Testaus db singletonia vasten -> suoritusaika n. 2s
with indeksikorjaus as (
    select kt.id                                                as kt_id,
           kt.summa,
           kt.vuosi,
           kt.kuukausi,
           u.id,
           indeksikorjaa(kt.summa, kt.vuosi, kt.kuukausi, u.id) as korjattu
    from kiinteahintainen_tyo kt
             join toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
             join urakka u on tpi.urakka = u.id
    WHERE u.tyyppi = 'teiden-hoito'
      and kt.summa_indeksikorjattu is null
)
update kiinteahintainen_tyo kt2
set summa_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja             = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu              = NOW()
from indeksikorjaus
where kt2.id = indeksikorjaus.kt_id
  and indeksikorjaus.korjattu is not null
  and kt2.summa_indeksikorjattu is null;

-- kustannusarvioitu_tyo.summa_indeksikorjattu
with indeksikorjaus as (
    select kt.id                                                as kt_id,
           indeksikorjaa(kt.summa, kt.vuosi, kt.kuukausi, u.id) as korjattu
    from kustannusarvioitu_tyo kt
             join toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
             join urakka u on tpi.urakka = u.id
    WHERE u.tyyppi = 'teiden-hoito'
      and kt.summa_indeksikorjattu is null
)
update kustannusarvioitu_tyo kt2
set summa_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja             = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu              = NOW()
from indeksikorjaus
where kt2.id = indeksikorjaus.kt_id
  and indeksikorjaus.korjattu is not null
  and kt2.summa_indeksikorjattu is null;

-- johto_ja_hallintokorvaus.tuntipalkka_indeksikorjattu
with indeksikorjaus as (
    select jk.id                                                      as jk_id,
           indeksikorjaa(jk.tuntipalkka, jk.vuosi, jk.kuukausi, u.id) as korjattu
    from johto_ja_hallintokorvaus jk
             join urakka u on jk."urakka-id" = u.id
    WHERE u.tyyppi = 'teiden-hoito'
      and jk.tuntipalkka_indeksikorjattu is null
)
update johto_ja_hallintokorvaus jk2
set tuntipalkka_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                   = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                    = NOW()
from indeksikorjaus
where jk2.id = indeksikorjaus.jk_id
  and indeksikorjaus.korjattu is not null
  and jk2.tuntipalkka_indeksikorjattu is null;

-- urakka_tavoite.tavoitehinta
with indeksikorjaus as (
    select ut.id         as ut_id,
           indeksikorjaa(
                   ut.tavoitehinta,
                   EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1, -- hoitokauden indeksointi alkaa 1:stä
                   10,
                   u.id) as korjattu
    from urakka_tavoite ut
             join urakka u on ut.urakka = u.id
    WHERE u.tyyppi = 'teiden-hoito'
      and ut.tavoitehinta_indeksikorjattu is null
)
update urakka_tavoite ut2
set tavoitehinta_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                    = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                     = NOW()
from indeksikorjaus
where ut2.id = indeksikorjaus.ut_id
  and indeksikorjaus.korjattu is not null
  and ut2.tavoitehinta_indeksikorjattu is null;

-- urakka_tavoite.tavoitehinta_siirretty
with indeksikorjaus as (
    select ut.id         as ut_id,
           indeksikorjaa(
                   ut.tavoitehinta_siirretty,
                   EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1, -- hoitokauden indeksointi alkaa 1:stä
                   10,
                   u.id) as korjattu
    from urakka_tavoite ut
             join urakka u on ut.urakka = u.id
    WHERE u.tyyppi = 'teiden-hoito'
      and (ut.tavoitehinta_siirretty is not null and
           ut.tavoitehinta_siirretty_indeksikorjattu is null)
)
update urakka_tavoite ut2
set tavoitehinta_siirretty_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                              = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                               = NOW()
from indeksikorjaus
where ut2.id = indeksikorjaus.ut_id
  and indeksikorjaus.korjattu is not null
  and ut2.tavoitehinta_indeksikorjattu is null;

-- urakka_tavoite.kattohinta
-- jätetään pois 2019 ja 2020 alkaneet urakat
-- Selvitetään, mitä tehdään näiden urakoiden kattohinnoille (Tea/Maarit tms)
-- -> ei tarvitse laskea migraatiossa, koska joutuvat kuitenkin täyttämään manuaalisesti
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
      -- jätetään pois 2019 ja 2020 alkaneet urakat
      and EXTRACT(YEAR FROM u.alkupvm) NOT IN (2019, 2020)
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

---- Aseta kustannusarvioitu_tyo-taulun riveille osio.

-- Hankintakustannukset
WITH hankintakustannukset AS
         (SELECT kat.id
          FROM kustannusarvioitu_tyo kat
                   LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
                   LEFT JOIN urakka u ON tpi.urakka = u.id
                   LEFT JOIN toimenpidekoodi tpik_tpi ON tpik_tpi.id = tpi.toimenpide
                   LEFT JOIN toimenpidekoodi tpik_t ON tpik_t.id = kat.tehtava
                   LEFT JOIN tehtavaryhma tr ON kat.tehtavaryhma = tr.id
          WHERE u.tyyppi = 'teiden-hoito'
            -- Määrämitattavat
            AND ((kat.tyyppi = 'laskutettava-tyo'
              AND tpik_t.yksiloiva_tunniste IS NULL
              AND tr.yksiloiva_tunniste IS NULL
              AND tpik_tpi.koodi IN ('20107',
                                     '20191',
                                     '23104',
                                     '23116',
                                     '23124',
                                     '14301',
                                     '23151'))
              -- Rahavaraukset
              OR ((tr.yksiloiva_tunniste = '0e78b556-74ee-437f-ac67-7a03381c64f6') -- Tilaajan rahavaraukset
                  OR tpik_t.yksiloiva_tunniste IN
                     ('49b7388b-419c-47fa-9b1b-3797f1fab21d', -- Kolmansien osapuolten vahingot talvihoito
                      '63a2585b-5597-43ea-945c-1b25b16a06e2', -- Kolmansien osapuolten vahingot liikenneympäristön hoito
                      'b3a7a210-4ba6-4555-905c-fef7308dc5ec', -- Kolmansien osapuolten vahingot sorateiden hoito
                      '1f12fe16-375e-49bf-9a95-4560326ce6cf', -- Äkilliset hoitotyöt talvihoito
                      '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974', -- Äkilliset hoitotyöt liikenneympäristön hoito
                      'd373c08b-32eb-4ac2-b817-04106b862fb1') -- Äkilliset hoitotyöt sorateiden hoito
                     ))
         )
UPDATE kustannusarvioitu_tyo
SET osio = 'hankintakustannukset'
WHERE id IN (SELECT id FROM hankintakustannukset);

-- Erillishankinnat
WITH erillishankinat AS
         (SELECT kat.id
          FROM kustannusarvioitu_tyo kat
                   LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
                   LEFT JOIN toimenpidekoodi tpik_tpi ON tpik_tpi.id = tpi.toimenpide
                   LEFT JOIN toimenpidekoodi tpik_t ON tpik_t.id = kat.tehtava
                   LEFT JOIN tehtavaryhma tr ON kat.tehtavaryhma = tr.id
                   LEFT JOIN urakka u ON tpi.urakka = u.id
          WHERE u.tyyppi = 'teiden-hoito'
            AND tr.yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c' -- Erillishankinnat
         )
UPDATE kustannusarvioitu_tyo
SET osio = 'erillishankinnat'
WHERE id IN (SELECT id FROM erillishankinat);

-- Hoidonjohtopalkkio
WITH hoidonjohtopalkkio AS
         (SELECT kat.id
          FROM kustannusarvioitu_tyo kat
                   LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
                   LEFT JOIN toimenpidekoodi tpik_tpi ON tpik_tpi.id = tpi.toimenpide
                   LEFT JOIN toimenpidekoodi tpik_t ON tpik_t.id = kat.tehtava
                   LEFT JOIN tehtavaryhma tr ON kat.tehtavaryhma = tr.id
                   LEFT JOIN urakka u ON tpi.urakka = u.id
          WHERE u.tyyppi = 'teiden-hoito'
            AND tpik_t.yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8' -- Hoidonjohtopalkkiot
         )
UPDATE kustannusarvioitu_tyo
SET osio = 'hoidonjohtopalkkio'
WHERE id IN (SELECT id FROM hoidonjohtopalkkio);

-- Johto- ja hallintakorvaukset
WITH hoidonjohtopalkkio AS
         (SELECT kat.id
          FROM kustannusarvioitu_tyo kat
                   LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
                   LEFT JOIN toimenpidekoodi tpik_tpi ON tpik_tpi.id = tpi.toimenpide
                   LEFT JOIN toimenpidekoodi tpik_t ON tpik_t.id = kat.tehtava
                   LEFT JOIN tehtavaryhma tr ON kat.tehtavaryhma = tr.id
                   LEFT JOIN urakka u ON tpi.urakka = u.id
          WHERE u.tyyppi = 'teiden-hoito'
            AND tpik_t.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' -- Hoidonjohtopalkkiot
         )
UPDATE kustannusarvioitu_tyo
SET osio = 'johto-ja-hallintokorvaus'
WHERE id IN (SELECT id FROM hoidonjohtopalkkio);

-- Tilaajan rahavaraukset
WITH tilaajan_rahavaraukset AS
         (SELECT kat.id
          FROM kustannusarvioitu_tyo kat
                   LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
                   LEFT JOIN toimenpidekoodi tpik_tpi ON tpik_tpi.id = tpi.toimenpide
                   LEFT JOIN toimenpidekoodi tpik_t ON tpik_t.id = kat.tehtava
                   LEFT JOIN tehtavaryhma tr ON kat.tehtavaryhma = tr.id
                   LEFT JOIN urakka u ON tpi.urakka = u.id
          WHERE u.tyyppi = 'teiden-hoito'
            AND tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54' -- Tilaajan rahavaraus
         )
UPDATE kustannusarvioitu_tyo
SET osio = 'tilaajan-rahavaraukset'
WHERE id IN (SELECT id FROM tilaajan_rahavaraukset);
