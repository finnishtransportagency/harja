/* Migraatiot V1_902 ja V1_907 yhdistettynä uudestaan tämän muutoksen takia:
   VHAR-5607: Pyöristä indeksikerroin kolmeen desimaaliin
   Tässä versiossa on 902 rajaus poistettu, ja migraatio ajetaan MH-urakoille vuosille 2019-2021.
   Muuten migraatiossa ainoa muutos on indeksikertoimen pyöristys.
*/

-- Kopioitu funktio testidata_indeksikorjaa
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
        indeksikerroin := (SELECT round((arvo / perusluku), 3)
                           FROM indeksi i
                           WHERE i.vuosi = vuosi_ - 1
                             AND i.kuukausi = 9
                             AND nimi = indeksin_nimi);
    ELSE
        indeksikerroin := (SELECT round((arvo / perusluku), 3)
                           FROM indeksi i
                           WHERE i.vuosi = vuosi_
                             AND i.kuukausi = 9
                             AND nimi = indeksin_nimi);
    END IF;
    -- Ja tallennettava arvo kuuteen.
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
)
update kiinteahintainen_tyo kt2
set summa_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja             = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu              = NOW()
from indeksikorjaus
where kt2.id = indeksikorjaus.kt_id
  and indeksikorjaus.korjattu is not null;

-- kustannusarvioitu_tyo.summa_indeksikorjattu
with indeksikorjaus as (
    select kt.id                                                as kt_id,
           indeksikorjaa(kt.summa, kt.vuosi, kt.kuukausi, u.id) as korjattu
    from kustannusarvioitu_tyo kt
             join toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
             join urakka u on tpi.urakka = u.id
    WHERE u.tyyppi = 'teiden-hoito'
)
update kustannusarvioitu_tyo kt2
set summa_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja             = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu              = NOW()
from indeksikorjaus
where kt2.id = indeksikorjaus.kt_id
  and indeksikorjaus.korjattu is not null;

-- johto_ja_hallintokorvaus.tuntipalkka_indeksikorjattu
with indeksikorjaus as (
    select jk.id                                                      as jk_id,
           indeksikorjaa(jk.tuntipalkka, jk.vuosi, jk.kuukausi, u.id) as korjattu
    from johto_ja_hallintokorvaus jk
             join urakka u on jk."urakka-id" = u.id
    WHERE u.tyyppi = 'teiden-hoito'
)
update johto_ja_hallintokorvaus jk2
set tuntipalkka_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                   = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                    = NOW()
from indeksikorjaus
where jk2.id = indeksikorjaus.jk_id
  and indeksikorjaus.korjattu is not null;

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
)
update urakka_tavoite ut2
set tavoitehinta_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                    = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                     = NOW()
from indeksikorjaus
where ut2.id = indeksikorjaus.ut_id
  and indeksikorjaus.korjattu is not null;

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
      and ut.tavoitehinta_siirretty is not null
)
update urakka_tavoite ut2
set tavoitehinta_siirretty_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                              = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                               = NOW()
from indeksikorjaus
where ut2.id = indeksikorjaus.ut_id
  and indeksikorjaus.korjattu is not null;

-- urakka_tavoite.kattohinta
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
)
update urakka_tavoite ut2
set kattohinta_indeksikorjattu = indeksikorjaus.korjattu,
    muokkaaja                  = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                   = NOW()
from indeksikorjaus
where ut2.id = indeksikorjaus.ut_id
  and indeksikorjaus.korjattu is not null;
