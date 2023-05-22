-- name:tallenna-budjettitavoite<!
INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_indeksikorjattu, kattohinta,
                            kattohinta_indeksikorjattu, luotu, luoja)
VALUES (:urakka, :hoitokausi, :tavoitehinta, :tavoitehinta-indeksikorjattu, :kattohinta, :kattohinta-indeksikorjattu,
        CURRENT_TIMESTAMP, :kayttaja);

-- name:paivita-budjettitavoite<!
UPDATE urakka_tavoite
   SET tavoitehinta                 = :tavoitehinta,
       tavoitehinta_indeksikorjattu = :tavoitehinta-indeksikorjattu,
       kattohinta                   = COALESCE(:kattohinta, kattohinta),
       kattohinta_indeksikorjattu   = COALESCE(:kattohinta-indeksikorjattu, kattohinta_indeksikorjattu),
       muokattu                     = CURRENT_TIMESTAMP,
       muokkaaja                    = :kayttaja
 WHERE urakka = :urakka
   AND hoitokausi = :hoitokausi;


-- name:hae-budjettitavoite
WITH tavoitehinnan_oikaisut AS
         (SELECT sum(summa) AS summa, "urakka-id", "hoitokauden-alkuvuosi"
          FROM tavoitehinnan_oikaisu
          WHERE NOT poistettu
          GROUP BY "urakka-id", "hoitokauden-alkuvuosi")
SELECT ut.id,
       ut.urakka,
       ut.hoitokausi,
       ut.tavoitehinta,
       ut.tavoitehinta_siirretty,
       ut.kattohinta,
       ut.luotu,
       ut.luoja,
       ut.muokattu,
       ut.muokkaaja,
       ut.tavoitehinta_indeksikorjattu                                                        AS "tavoitehinta-indeksikorjattu",
       ut.tavoitehinta_siirretty_indeksikorjattu                                              AS "tavoitehinta-siirretty-indeksikorjattu",
       ut.kattohinta_indeksikorjattu                                                          AS "kattohinta-indeksikorjattu",
       ut.indeksikorjaus_vahvistettu                                                          AS "indeksikorjaus-vahvistettu",
       ut.vahvistaja,
       ut.versio,
       (ut.tavoitehinta_indeksikorjattu + COALESCE(t.summa, 0))                               AS "tavoitehinta-oikaistu",
       COALESCE(ko."uusi-kattohinta", (ut.kattohinta_indeksikorjattu
           + (COALESCE(t.summa,0) * 1.1))) -- Katottihinta kasvaa 10% myös tavoitehinnan oikaisuista.
           AS "kattohinta-oikaistu",
       (EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1)::INTEGER                            AS "hoitokauden-alkuvuosi",
       ut.tarjous_tavoitehinta                                                                AS "tarjous-tavoitehinta"
FROM urakka_tavoite ut
         LEFT JOIN urakka u ON ut.urakka = u.id
         LEFT JOIN kattohinnan_oikaisu ko ON (u.id = ko."urakka-id" AND
                                              EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 =
                                              ko."hoitokauden-alkuvuosi" AND
                                              NOT ko.poistettu)
         LEFT JOIN tavoitehinnan_oikaisut t ON u.id = t."urakka-id" AND
                                               EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 =
                                               t."hoitokauden-alkuvuosi"
WHERE urakka = :urakka
ORDER BY ut.hoitokausi;

-- name:hae-johto-ja-hallintokorvaukset
SELECT jh.tunnit,
       jh.tuntipalkka,
       jh.tuntipalkka_indeksikorjattu AS "tuntipalkka-indeksikorjattu",
       jh.indeksikorjaus_vahvistettu AS "indeksikorjaus-vahvistettu",
       jh.vuosi,
       jh.kuukausi,
       jh."ennen-urakkaa",
       jh."osa-kuukaudesta",
       jht.toimenkuva
FROM johto_ja_hallintokorvaus jh
  JOIN johto_ja_hallintokorvaus_toimenkuva jht ON jh."toimenkuva-id" = jht.id
WHERE jh."urakka-id" = :urakka-id AND
      jht."urakka-id" IS NULL;

-- name:hae-omat-johto-ja-hallintokorvaukset
SELECT jh.tunnit,
       jh.tuntipalkka,
       jh.tuntipalkka_indeksikorjattu AS "tuntipalkka-indeksikorjattu",
       jh.indeksikorjaus_vahvistettu AS "indeksikorjaus-vahvistettu",
       jh.vuosi,
       jh.kuukausi,
       jh."ennen-urakkaa",
       jh."osa-kuukaudesta",
       jht.toimenkuva,
       jht.id AS "toimenkuva-id",
       jht.maksukuukaudet
FROM johto_ja_hallintokorvaus jh
  JOIN johto_ja_hallintokorvaus_toimenkuva jht ON jh."toimenkuva-id" = jht.id AND jht."urakka-id" = jh."urakka-id"
WHERE jh."urakka-id" = :urakka-id;

-- name:hae-urakan-omat-jh-toimenkuvat
SELECT id AS "toimenkuva-id",
       toimenkuva
FROM johto_ja_hallintokorvaus_toimenkuva
WHERE "urakka-id" = :urakka-id

-- name:lisaa-oma-johto-ja-hallintokorvaus-toimenkuva<!
INSERT INTO johto_ja_hallintokorvaus_toimenkuva (toimenkuva, "urakka-id")
VALUES (:toimenkuva, :urakka-id)
RETURNING id;

--name: vahvista-tai-kumoa-indeksikorjaukset-kiinteahintaisille-toille!
UPDATE kiinteahintainen_tyo kt
   SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP END,
       vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja END
  FROM toimenpideinstanssi tpi
 WHERE kt.toimenpideinstanssi = tpi.id
   AND tpi.urakka = :urakka-id
   AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   AND kt.versio = 0;

--name: vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille!
UPDATE kustannusarvioitu_tyo kt
   SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP END,
       vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja END
  FROM toimenpideinstanssi tpi
 WHERE kt.toimenpideinstanssi = tpi.id
   AND tpi.urakka = :urakka-id
   AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   AND kt.osio = :osio::SUUNNITTELU_OSIO
   AND kt.versio = 0;

--name: vahvista-tai-kumoa-indeksikorjaukset-jh-korvauksille!
UPDATE johto_ja_hallintokorvaus jh
   SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP END,
       vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja END
 WHERE jh."urakka-id" = :urakka-id
   AND (CONCAT(jh.vuosi, '-', jh.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   AND jh.versio = 0;

--name: vahvista-tai-kumoa-indeksikorjaukset-urakan-tavoitteille!
UPDATE urakka_tavoite ut
   SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP END,
       vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja END
 WHERE ut.urakka = :urakka-id
   -- hoitokausi ei ole hoitovuosi e.g. 2020, vaan hoitovuoden järjestysnumero e.g. 1
   AND ut.hoitokausi = :hoitovuosi-nro
   AND ut.versio = 0;

-- name: hae-suunnitelman-tilat
select * from suunnittelu_kustannussuunnitelman_tila skt where skt.urakka = :urakka;

-- name: lisaa-suunnitelmalle-tila
   INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, luoja, vahvistaja, vahvistettu, vahvistus_pvm)
   VALUES (:urakka, :osio::SUUNNITTELU_OSIO, :hoitovuosi, :luoja, :vahvistaja, :vahvistettu, :vahvistus_pvm)
       ON CONFLICT DO NOTHING
RETURNING id;

-- name: hae-suunnitelman-osan-tila-hoitovuodelle
select * from suunnittelu_kustannussuunnitelman_tila skt
where skt.urakka = :urakka and osio = :osio::suunnittelu_osio and hoitovuosi = :hoitovuosi;

-- name: vahvista-suunnitelman-osa-hoitovuodelle
update suunnittelu_kustannussuunnitelman_tila
set vahvistettu = true,
    muokattu = current_timestamp,
    muokkaaja = :muokkaaja,
    vahvistaja = :vahvistaja,
    vahvistus_pvm = current_timestamp
where urakka = :urakka and osio = :osio::suunnittelu_osio and hoitovuosi = :hoitovuosi
returning id;

-- name: kumoa-suunnitelman-osan-vahvistus-hoitovuodelle
   UPDATE suunnittelu_kustannussuunnitelman_tila
      SET vahvistettu   = FALSE,
          muokattu      = CURRENT_TIMESTAMP,
          muokkaaja     = :muokkaaja,
          vahvistaja    = NULL,
          vahvistus_pvm = NULL
    WHERE urakka = :urakka
      AND osio = :osio::SUUNNITTELU_OSIO
      AND hoitovuosi = :hoitovuosi
RETURNING id;

-- name: paivita-kiinteahintaiset-tyot-indeksille!
-- kiinteahintainen_tyo.summa_indeksikorjattu
with muuttuneet as (
    select *
    from (
             select kt.id                                                as id,
                    kt.summa_indeksikorjattu                             as vanha,
                    indeksikorjaa(kt.summa, kt.vuosi, kt.kuukausi, u.id) as uusi
             from kiinteahintainen_tyo kt
                      join toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
                      join urakka u on tpi.urakka = u.id
             where u.tyyppi = 'teiden-hoito'
               and u.indeksi = :nimi
               and (kt.vuosi, kt.kuukausi) between (:vuosi, 10) and (:vuosi + 1, 9) -- seuraavan hoitovuoden rivit
               -- syys/loka/marraskuun indeksi vaikuttaa indeksilaskennan peruslukuun, ja sitä kautta indeksikorjauksiin
               -- indeksikerroin on edellisen hoitovuoden syyskuun arvo jaettuna perusluvulla
               and :kuukausi in (9, 10, 11)
               and indeksikorjaus_vahvistettu is null
         ) indeksikorjaus
    where vanha is distinct from uusi
)
update kiinteahintainen_tyo
set summa_indeksikorjattu = muuttuneet.uusi,
    muokkaaja             = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu              = NOW()
from muuttuneet
where muuttuneet.id = kiinteahintainen_tyo.id;

-- name: paivita-kustannusarvioidut-tyot-indeksille!
-- kustannusarvioitu_tyo.summa_indeksikorjattu
with muuttuneet as (
    select *
    from (
             select kt.id                                                as id,
                    kt.summa_indeksikorjattu                             as vanha,
                    indeksikorjaa(kt.summa, kt.vuosi, kt.kuukausi, u.id) as uusi
             from kustannusarvioitu_tyo kt
                      join toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
                      join urakka u on tpi.urakka = u.id
                      left join tehtavaryhma tr ON kt.tehtavaryhma = tr.id
             where u.tyyppi = 'teiden-hoito'
               and u.indeksi = :nimi
               and (kt.vuosi, kt.kuukausi) between (:vuosi, 10) and (:vuosi + 1, 9)
               and :kuukausi in (9, 10, 11)
               and indeksikorjaus_vahvistettu is null
               -- Tilaajan rahavarauksille ei lasketa indeksikorjauksia
               and not (
                     -- Johto- ja hallintokorvaus (J)
                     tr.yksiloiva_tunniste is not null and tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54'
                     -- MHU ja HJU Hoidon johto
                     and tpi.toimenpide = (select id from toimenpide where koodi = '23151'))
         ) indeksikorjaus
    where vanha is distinct from uusi
)
update kustannusarvioitu_tyo
set summa_indeksikorjattu = muuttuneet.uusi,
    muokkaaja             = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu              = NOW()
from muuttuneet
where muuttuneet.id = kustannusarvioitu_tyo.id;

-- name: paivita-johto-ja-hallintokorvaus-indeksille!
-- johto_ja_hallintokorvaus.tuntipalkka_indeksikorjattu
with muuttuneet as (
    select *
    from (
             select jk.id                                                as id,
                    jk.tuntipalkka_indeksikorjattu                       as vanha,
                    indeksikorjaa(jk.tuntipalkka, jk.vuosi, jk.kuukausi, u.id) as uusi
             from johto_ja_hallintokorvaus jk
                      join urakka u on jk."urakka-id" = u.id
             where u.tyyppi = 'teiden-hoito'
               and u.indeksi = :nimi
               and (jk.vuosi, jk.kuukausi) between (:vuosi, 10) and (:vuosi + 1, 9)
               and :kuukausi in (9, 10, 11)
               and indeksikorjaus_vahvistettu is null
         ) indeksikorjaus
    where vanha is distinct from uusi
)
update johto_ja_hallintokorvaus
set tuntipalkka_indeksikorjattu = muuttuneet.uusi,
    muokkaaja                   = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                    = NOW()
from muuttuneet
where muuttuneet.id = johto_ja_hallintokorvaus.id;

-- name: paivita-urakka-tavoite-indeksille!
-- urakka_tavoite.tavoitehinta
-- urakka_tavoite.tavoitehinta_siirretty
-- urakka_tavoite.kattohinta
with muuttuneet as (
    select *
    from (
             select ut.id                                     as id,
                    -- tavoitehinta_indeksikorjattu
                    ut.tavoitehinta_indeksikorjattu           as tavoitehinta_indeksikorjattu_vanha,
                    indeksikorjaa(
                            ut.tavoitehinta,
                            EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1,
                            10,
                            u.id)                             as tavoitehinta_indeksikorjattu_uusi,
                    -- tavoitehinta_siirretty_indeksikorjattu
                    ut.tavoitehinta_siirretty_indeksikorjattu as tavoitehinta_siirretty_indeksikorjattu_vanha,
                    indeksikorjaa(
                            ut.tavoitehinta_siirretty,
                            EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1,
                            10,
                            u.id)                             as tavoitehinta_siirretty_indeksikorjattu_uusi,
                    -- kattohinta_indeksikorjattu
                    ut.kattohinta_indeksikorjattu             as kattohinta_indeksikorjattu_vanha,
                    indeksikorjaa(
                            ut.kattohinta,
                            EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1,
                            10,
                            u.id)                             as kattohinta_indeksikorjattu_uusi
             from urakka_tavoite ut
                      join urakka u on ut.urakka = u.id
             where u.tyyppi = 'teiden-hoito'
               and u.indeksi = :nimi
               and EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1 between :vuosi and :vuosi + 1
               and :kuukausi in (9, 10, 11)
               and indeksikorjaus_vahvistettu is null
         ) indeksikorjaus
    where tavoitehinta_indeksikorjattu_vanha is distinct from tavoitehinta_indeksikorjattu_uusi
       or tavoitehinta_siirretty_indeksikorjattu_vanha is distinct from tavoitehinta_siirretty_indeksikorjattu_uusi
       or kattohinta_indeksikorjattu_vanha is distinct from kattohinta_indeksikorjattu_uusi
)
update urakka_tavoite
set tavoitehinta_indeksikorjattu           = muuttuneet.tavoitehinta_indeksikorjattu_uusi,
    tavoitehinta_siirretty_indeksikorjattu = muuttuneet.tavoitehinta_siirretty_indeksikorjattu_uusi,
    kattohinta_indeksikorjattu             = muuttuneet.kattohinta_indeksikorjattu_uusi,
    muokkaaja                              = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                               = NOW()
from muuttuneet
where muuttuneet.id = urakka_tavoite.id;
