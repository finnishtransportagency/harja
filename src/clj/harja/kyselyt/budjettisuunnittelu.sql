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


-- name: hae-urakan-tavoitehintojen-tilat
SELECT ut.id,
       ut.urakka,
       ut.hoitokausi,
       ut.tavoitehinta,
       ut.kattohinta,
       ut.luotu,
       ut.luoja,
       ut.muokattu,
       ut.muokkaaja,
       ut.tavoitehinta_indeksikorjattu                                                        AS "tavoitehinta-indeksikorjattu",
       ut.kattohinta_indeksikorjattu                                                          AS "kattohinta-indeksikorjattu",
       ut.indeksikorjaus_vahvistettu                                                          AS "indeksikorjaus-vahvistettu",
       ut.vahvistaja,
       ut.versio,
       (EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1)::INTEGER                            AS "hoitokauden-alkuvuosi",
       ut.tarjous_tavoitehinta                                                                AS "tarjous-tavoitehinta"
  FROM urakka_tavoite ut
           LEFT JOIN urakka u ON ut.urakka = u.id
 WHERE urakka = :urakka
 ORDER BY ut.hoitokausi;

-- name: hae-budjettitavoite
WITH tavoitehinnan_oikaisut AS
         (SELECT sum(summa) AS summa, "urakka-id", "hoitokauden-alkuvuosi"
          FROM tavoitehinnan_oikaisu
          WHERE NOT poistettu
          GROUP BY "urakka-id", "hoitokauden-alkuvuosi"),
    hoivuoden_lopun_indeksikorjaus AS
         (SELECT phi.hoitokauden_lopun_indeksikorjaus, phi.hoitokauden_alkuvuosi
          FROM paatos_hoitokauden_indeksikorjaus phi
          WHERE phi.poistettu = FALSE
            AND phi.urakkaid = :urakka),
    mhu_pysyvat_muutokset AS (
        -- Pysyvät muutokset
         SELECT SUM(mmk.summa) as summa, mm.urakka, mmk.hoitokauden_alkuvuosi
           FROM mhu_muutos mm
                   JOIN mhu_muutos_kustannusvaikutus mmk ON mmk.muutos = mm.id
                   JOIN urakka_tavoite ut ON ut.urakka = mm.urakka
                   JOIN urakka u ON ut.urakka = u.id AND u.id = mm.urakka
          WHERE mm.urakka = :urakka
            AND mm.voimassa_alkaen BETWEEN
                (SELECT TO_DATE(EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 || '-10-01', 'YYYY-MM-DD'))
                AND (SELECT TO_DATE(EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi || '-09-30', 'YYYY-MM-DD'))
            AND mm.tyyppi = 'pysyva'::MHU_MUUTOSTYYPPI
            AND mm.poistettu IS FALSE
            AND mmk.hoitokauden_alkuvuosi = EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1
          GROUP BY mmk.hoitokauden_alkuvuosi, mm.urakka),
     mhu_muutostyot_muutokset AS (
         -- Muutostyot (erillisrahoitetutu)
         SELECT SUM(mmk.summa) as summa, mm.urakka, mmk.hoitokauden_alkuvuosi
         FROM mhu_muutos mm
                  JOIN mhu_muutos_kustannusvaikutus mmk ON mmk.muutos = mm.id
                  JOIN urakka_tavoite ut ON ut.urakka = mm.urakka
                  JOIN urakka u ON ut.urakka = u.id AND u.id = mm.urakka
         WHERE mm.urakka = :urakka
           AND mm.voimassa_alkaen BETWEEN
             (SELECT TO_DATE(EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 || '-10-01', 'YYYY-MM-DD'))
             AND (SELECT TO_DATE(EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi || '-09-30', 'YYYY-MM-DD'))
           AND mm.tyyppi = 'muutostyo'::MHU_MUUTOSTYYPPI
           AND mm.poistettu IS FALSE
           AND mmk.hoitokauden_alkuvuosi = EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1
         GROUP BY mmk.hoitokauden_alkuvuosi, mm.urakka),
    mhu_jjh_muutokset AS (
        -- Johto- ja hallintokorvauksen muutokset
          SELECT SUM(kokonaissumma) as summa, mm.urakka, EXTRACT (YEAR from u.alkupvm) + ut.hoitokausi - 1 as hoitokauden_alkuvuosi
            FROM mhu_muutos mm
                 JOIN mhu_muutos_kulu mmk ON (mm.id = mmk.muutos AND mm.versio = mmk.versio),
                 kulu k
                 JOIN kulu_kohdistus kk ON k.id = kk.kulu AND kk.tyyppi = 'jjh-muutos'
                 JOIN urakka_tavoite ut ON ut.urakka = :urakka
                 JOIN urakka u ON ut.urakka = u.id
           WHERE k.id = mmk.kulu
             AND mm.urakka = :urakka
             AND k.poistettu IS FALSE
             AND kk.poistettu IS FALSE
             AND k.erapaiva BETWEEN
                 (SELECT TO_DATE(EXTRACT (YEAR from u.alkupvm) + ut.hoitokausi - 1 || '-10-01', 'YYYY-MM-DD')) AND
                 (SELECT TO_DATE(EXTRACT (YEAR from u.alkupvm) + ut.hoitokausi || '-09-30', 'YYYY-MM-DD'))
            GROUP BY hoitokauden_alkuvuosi, mm.urakka)
SELECT ut.id,
       ut.urakka,
       ut.hoitokausi,
       ut.tavoitehinta,
       ut.kattohinta,
       ut.luotu,
       ut.luoja,
       ut.muokattu,
       ut.muokkaaja,
       ut.tavoitehinta_indeksikorjattu                                                        AS "tavoitehinta-indeksikorjattu",
       ut.kattohinta_indeksikorjattu                                                          AS "kattohinta-indeksikorjattu",
       ut.indeksikorjaus_vahvistettu                                                          AS "indeksikorjaus-vahvistettu",
       ut.vahvistaja,
       ut.versio,
       (ut.tavoitehinta_indeksikorjattu + COALESCE(t.summa, 0))                               AS "tavoitehinta-oikaistu",
       COALESCE(t.summa, 0)                                                                   AS "oikaisut-summa",
       (ut.tavoitehinta_indeksikorjattu + COALESCE(t.summa, 0) + -- Tavoitehinta + mahdolliset oikaisut
        COALESCE(hli.hoitokauden_lopun_indeksikorjaus, 0))  -- Hoitovuoden lopun indeksikorjaus
                                                                                              AS "hoitovuoden-lopun-tavoitehinta",
       COALESCE(ko."uusi-kattohinta", -- Oikaistu kattohinta
                (ut.kattohinta_indeksikorjattu + -- Indeksikorjattu kattohinta
                 (COALESCE(t.summa,0) -- Mahdolliset oikaisut
                      * 1.1))) -- Katottihinta kasvaa 10% myös tavoitehinnan oikaisuista.
                                                                                              AS "kattohinta-oikaistu",
       COALESCE(ko."uusi-kattohinta",
                (ut.kattohinta_indeksikorjattu + (COALESCE(t.summa, 0) * 1.1)
                    + (COALESCE(hli.hoitokauden_lopun_indeksikorjaus, 0) * 1.1))) -- Katottihinta kasvaa 10% myös tavoitehinnan oikaisuista ja hoitovuoden lopun indeksikorjauksista.
                                                                                              AS "hoitovuoden-lopun-kattohinta",
       x.hk_alkuvuosi                                                                         AS "hoitokauden-alkuvuosi",
       ut.tarjous_tavoitehinta                                                                AS "tarjous-tavoitehinta",
       ut.laskutusraja                                                                        AS "laskutusraja",
       ut.laskutusraja_alkuperainen                                                           AS "laskutusraja-alkuperainen",
       (COALESCE(SUM(pmuutokset.summa),0) + COALESCE(SUM(mmuutokset.summa),0)
            + COALESCE(SUM(jmuutokset.summa), 0))                                             AS "kirjallisesti-sovitut-muutokset",
       SUM(pmuutokset.summa)                                                                  AS "pysyvat-muutokset",
       SUM(mmuutokset.summa)                                                                  AS "muutostyo-muutokset",
       SUM(jmuutokset.summa)                                                                  AS "jjh-muutokset",
       up.laskutusraja_kaytossa                                                               AS "laskutusraja-kaytossa",
       -- Menneet pysyvät muutokset täytyy indeksikorjata, kun ne haetaan
       indeksikorjaa(
           (SELECT SUM(mmk.summa)
            FROM mhu_muutos_kustannusvaikutus mmk
                     JOIN mhu_muutos m ON m.id = mmk.muutos
            WHERE m.urakka = :urakka
              AND m.tyyppi = 'pysyva'::MHU_MUUTOSTYYPPI
              AND m.poistettu IS FALSE
              -- Astunut voimaan ennen valittua hk
              -- Muutosvaikutuksen alkuvuosi on valittu hk
              AND (m.voimassa_alkaen < x.hk_alkupvm AND mmk.hoitokauden_alkuvuosi = x.hk_alkuvuosi)),
           x.hk_alkuvuosi, 10, u.id
       ) AS "menneet-muutos-summa"

FROM urakka_tavoite ut
         LEFT JOIN urakka u ON ut.urakka = u.id
         CROSS JOIN LATERAL (
            -- Muodostetaan hoitovuosi, jota voidaan käyttää sisäkkäisissä hauissa
             SELECT (EXTRACT(YEAR FROM u.alkupvm)::INT + ut.hoitokausi - 1) AS hk_alkuvuosi,
                    TO_DATE(EXTRACT(YEAR FROM u.alkupvm)::INT + ut.hoitokausi - 1 || '-10-01', 'YYYY-MM-DD') AS hk_alkupvm,
                    (EXTRACT(YEAR FROM u.alkupvm)::INT + ut.hoitokausi) AS hk_loppuvuosi,
                    TO_DATE(EXTRACT(YEAR FROM u.alkupvm)::INT + ut.hoitokausi || '-09-30', 'YYYY-MM-DD') AS hk_loppupvm) x
         LEFT JOIN kattohinnan_oikaisu ko ON (u.id = ko."urakka-id" AND ko."hoitokauden-alkuvuosi" = x.hk_alkuvuosi AND NOT ko.poistettu)
         LEFT JOIN tavoitehinnan_oikaisut t ON u.id = t."urakka-id" AND t."hoitokauden-alkuvuosi" = x.hk_alkuvuosi
         LEFT JOIN hoivuoden_lopun_indeksikorjaus hli ON hli.hoitokauden_alkuvuosi =  x.hk_alkuvuosi
         LEFT JOIN mhu_pysyvat_muutokset pmuutokset ON pmuutokset.urakka = u.id AND pmuutokset.hoitokauden_alkuvuosi = x.hk_alkuvuosi
         LEFT JOIN mhu_muutostyot_muutokset mmuutokset ON mmuutokset.urakka = u.id AND mmuutokset.hoitokauden_alkuvuosi = x.hk_alkuvuosi
         LEFT JOIN mhu_jjh_muutokset jmuutokset ON jmuutokset.urakka = u.id AND jmuutokset.hoitokauden_alkuvuosi = x.hk_alkuvuosi
         LEFT JOIN urakka_parametrit up ON up.urakkaid = u.id
WHERE ut.urakka = :urakka
GROUP BY ut.id, ut.hoitokausi, u.id, ko."uusi-kattohinta", t.summa, hli.hoitokauden_lopun_indeksikorjaus, x.hk_alkuvuosi, x.hk_alkupvm, x.hk_loppuvuosi, x.hk_loppupvm, up.laskutusraja_kaytossa
ORDER BY ut.hoitokausi;

-- name: hae-valikatselmus-siirrot-ed-vuodelta
-- single?: true
(SELECT COALESCE(SUM(x.siirto), 0)
 FROM (SELECT COALESCE(SUM(pta.siirron_maara) * -1, 0) as siirto
       FROM paatos_tavoitehinta_alitus pta
       WHERE pta.urakkaid = :urakka
         AND pta.hoitokauden_alkuvuosi = (EXTRACT(YEAR FROM :alkupvm::DATE) - 1) -- Haetaan edellisen vuoden päätöksestä
         AND pta.siirron_maara != 0
         AND pta.poistettu = FALSE
       UNION ALL
       SELECT COALESCE(SUM(pk.siirrettava_maara), 0) as siirto
       FROM paatos_kattohinta pk
       WHERE pk.urakkaid = :urakka
         AND pk.hoitokauden_alkuvuosi = (EXTRACT(YEAR FROM :alkupvm::DATE) - 1) -- Haetaan edellisen vuoden päätöksestä
         AND pk.siirrettava_maara != 0
         AND pk.poistettu = FALSE) as x);

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
WHERE "urakka-id" = :urakka-id;

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

--name: merkitse-urakan-tavoitteiden-indeksikorjaukset-vahvistetuksi!
UPDATE urakka_tavoite ut
   SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP END,
       vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja END
 WHERE ut.urakka = :urakka-id
   -- hoitokausi ei ole hoitovuosi e.g. 2020, vaan hoitovuoden järjestysnumero e.g. 1
   AND ut.hoitokausi = :hoitovuosi-nro
   AND ut.versio = 0;

-- name: hae-suunnitelman-tilat
select * from suunnittelu_kustannussuunnitelman_tila skt where skt.urakka = :urakka;

-- name: onko-kustannussuunnitelma-vahvistettu
SELECT EXISTS (
    SELECT vahvistettu
    FROM suunnittelu_kustannussuunnitelman_tila skt
    WHERE skt.urakka = :urakkaid
      AND skt.osio = 'tavoite-ja-kattohinta'::SUUNNITTELU_OSIO
      AND skt.hoitovuosi = :hoitovuosinro
      AND skt.vahvistettu = true);

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
               and :kuukausi in (8, 9, 10, 11) -- MAKU indeksin kuukausi 
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
               and :kuukausi in (8, 9, 10, 11) -- MAKU indeksin kuukausi 
               and indeksikorjaus_vahvistettu is null
               -- Tilaajan rahavarauksille ei lasketa indeksikorjauksia
               and not (
                     -- J - Johto- ja hallintokorvaus
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
               and :kuukausi in (8, 9, 10, 11) -- MAKU indeksin kuukausi 
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
                    -- kattohinta_indeksikorjattu
                    ut.kattohinta_indeksikorjattu             as kattohinta_indeksikorjattu_vanha,
                    indeksikorjaa(
                            ut.kattohinta,
                            EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1,
                            10,
                            u.id)                             as kattohinta_indeksikorjattu_uusi,
                    ut.laskutusraja                           as laskutusraja_vanha,
                    ut.laskutusraja_alkuperainen              as laskutusraja_alkuperainen_vanha
             from urakka_tavoite ut
                      join urakka u on ut.urakka = u.id
             where u.tyyppi = 'teiden-hoito'
               and u.indeksi = :nimi
               and EXTRACT(YEAR FROM u.alkupvm)::integer + hoitokausi - 1 between :vuosi and :vuosi + 1
               and :kuukausi in (8, 9, 10, 11)  -- MAKU indeksin kuukausi 
               and indeksikorjaus_vahvistettu is null
         ) indeksikorjaus
    where tavoitehinta_indeksikorjattu_vanha is distinct from tavoitehinta_indeksikorjattu_uusi
       or kattohinta_indeksikorjattu_vanha is distinct from kattohinta_indeksikorjattu_uusi
)
update urakka_tavoite
set tavoitehinta_indeksikorjattu           = muuttuneet.tavoitehinta_indeksikorjattu_uusi,
    kattohinta_indeksikorjattu             = muuttuneet.kattohinta_indeksikorjattu_uusi,
    -- Uusi laskutusraja = tavoitehinta_indeksikorjattu_uusi + muutostöiden osuus
    laskutusraja = CASE
                       WHEN muuttuneet.laskutusraja_vanha IS NOT NULL
                            AND EXISTS (SELECT 1
                                          FROM urakka_parametrit up
                                         WHERE up.urakkaid = urakka_tavoite.urakka
                                           AND up.laskutusraja_kaytossa = TRUE)
                           THEN muuttuneet.tavoitehinta_indeksikorjattu_uusi
                                + (muuttuneet.laskutusraja_vanha
                                   - COALESCE(muuttuneet.laskutusraja_alkuperainen_vanha, muuttuneet.laskutusraja_vanha))
                       ELSE NULL
                   END,
    -- Uusi laskutusraja_alkuperainen = tavoitehinta_indeksikorjattu_uusi
    laskutusraja_alkuperainen = CASE
                                    WHEN muuttuneet.laskutusraja_alkuperainen_vanha IS NOT NULL
                                         AND EXISTS (SELECT 1
                                                       FROM urakka_parametrit up
                                                      WHERE up.urakkaid = urakka_tavoite.urakka
                                                        AND up.laskutusraja_kaytossa = TRUE)
                                        THEN muuttuneet.tavoitehinta_indeksikorjattu_uusi
                                    ELSE NULL
                                END,
    muokkaaja                              = (select id from kayttaja where kayttajanimi = 'Integraatio'),
    muokattu                               = NOW()
from muuttuneet
where muuttuneet.id = urakka_tavoite.id;

-- name: hae-urakoiden-tarjoushinnat
SELECT u.id                           AS urakka,
       u.nimi                         AS "urakka-nimi",
       ut.id,
       ut.tarjous_tavoitehinta        AS "tarjous-tavoitehinta",
       ut.hoitokausi,
       u.loppupvm < CURRENT_DATE      AS "urakka-paattynyt?",
       u.alkupvm                      as "urakka-alkupvm",
       (EXTRACT(YEAR FROM u.loppupvm) - EXTRACT(YEAR FROM u.alkupvm)) + 1   AS urakan_pituus -- vuosista saadaan yhden liian lyhyt
FROM urakka u
     LEFT JOIN urakka_tavoite ut ON ut.urakka = u.id
WHERE u.tyyppi = 'teiden-hoito'
ORDER BY u.alkupvm DESC, ut.hoitokausi, u.nimi ;


-- name: paivita-tarjoushinta<!
UPDATE urakka_tavoite
SET tarjous_tavoitehinta = :tarjous-tavoitehinta,
    muokkaaja = :kayttaja_id,
    muokattu = CURRENT_TIMESTAMP
WHERE id = :id;

-- name: lisaa-tarjoushinta<!
INSERT INTO urakka_tavoite (urakka, hoitokausi, tarjous_tavoitehinta, luotu, luoja)
VALUES (:urakka_id, :hoitokausi, :tarjous-tavoitehinta, CURRENT_TIMESTAMP, :kayttaja_id);

-- name: hae-kiinteat-kustannukset
-- Kiinteät kustannukset analytiikan api hakuun
SELECT kit.id as "kustannus-id",
       kit.vuosi as ajankohta_vuosi,
       kit.kuukausi as ajankohta_kuukausi,
       kit.summa as kustannus_summa,
       kit.summa_indeksikorjattu as "kustannus_indeksikorjattu-summa",
       kit.indeksikorjaus_vahvistettu as "kustannus_indeksikorjauksen-vahvistusajankohta",
       tp.id as kohdistus_toimenpide,
       tr.id as kohdistus_tehtavaryhma,
       null as kohdistus_rahavaraus,
       te.id as kohdistus_tehtava,
       kit.versio as versio
  from kiinteahintainen_tyo kit
           JOIN toimenpideinstanssi tpi on kit.toimenpideinstanssi = tpi.id
           JOIN urakka u on tpi.urakka = u.id
           JOIN toimenpide tp on tpi.toimenpide = tp.id
           LEFT JOIN tehtavaryhma tr on kit.tehtavaryhma = tr.id
           LEFT JOIN tehtava te on kit.tehtava = te.id
 WHERE u.id = :urakka-id
 ORDER BY ajankohta_vuosi, ajankohta_kuukausi, kohdistus_toimenpide, kohdistus_tehtavaryhma, kohdistus_rahavaraus, kohdistus_tehtava;

-- name: hae-arvioidut-kustannukset
-- Arvioidut kustannukset analytiikan kustannustensuunnitteluun
SELECT kat.id as "kustannus-id",
       kat.vuosi as ajankohta_vuosi,
       kat.kuukausi as ajankohta_kuukausi,
       kat.summa as kustannus_summa,
       kat.summa_indeksikorjattu as "kustannus_indeksikorjattu-summa",
       kat.indeksikorjaus_vahvistettu as "kustannus_indeksikorjauksen-vahvistusajankohta",
       tp.id as kohdistus_toimenpide,
       tr.id as kohdistus_tehtavaryhma,
       rv.id as kohdistus_rahavaraus,
       te.id as kohdistus_tehtava,
       kat.versio as versio
  from kustannusarvioitu_tyo kat
           JOIN toimenpideinstanssi tpi on kat.toimenpideinstanssi = tpi.id
           JOIN urakka u on tpi.urakka = u.id
           JOIN toimenpide tp on tpi.toimenpide = tp.id
           LEFT JOIN tehtavaryhma tr on kat.tehtavaryhma = tr.id
           LEFT JOIN rahavaraus rv on kat.rahavaraus_id = rv.id
           LEFT JOIN tehtava te on kat.tehtava = te.id
 WHERE u.id = :urakka-id
 ORDER BY ajankohta_vuosi, ajankohta_kuukausi, kohdistus_toimenpide, kohdistus_tehtavaryhma, kohdistus_rahavaraus, kohdistus_tehtava;

-- name: johto-ja-hallintokorvaukset-analytiikan-kustannustensuunnitteluun
-- Johto- ja hallintokorvaukset
SELECT jhk.id as "kustannus-id",
       tk.id as "toimenkuva_id",
       tk.toimenkuva as "toimenkuva_nimi",
       jhk.vuosi as "toimenkuvan-ajankohta_vuosi",
       jhk.kuukausi as "toimenkuvan-ajankohta_kuukausi",
       jhk."ennen-urakkaa" as "toimenkuvan-ajankohta_ennen-urakkaa",
       jhk.tunnit as "toimenkuvan-kustannus_tunnit",
       jhk.tuntipalkka as "toimenkuvan-kustannus_tuntipalkka",
       (jhk.tuntipalkka * jhk.tunnit) as "toimenkuvan-kustannus_summa",
       (jhk.tuntipalkka_indeksikorjattu * jhk.tunnit) as "toimenkuvan-kustannus_indeksikorjattu-summa",
       jhk.indeksikorjaus_vahvistettu as "toimenkuvan-kustannus_indeksikorjauksen-vahvistusajankohta"
  FROM johto_ja_hallintokorvaus jhk
       JOIN johto_ja_hallintokorvaus_toimenkuva tk ON jhk."toimenkuva-id" = tk.id
 WHERE jhk."urakka-id" = :urakka-id
 ORDER BY "toimenkuvan-ajankohta_vuosi", "toimenkuvan-ajankohta_kuukausi";

-- name: hae-johto-ja-hallintokorvauksen-tehtavaryhma
SELECT id
  FROM tehtavaryhma
 WHERE yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54';

-- name: hae-johto-ja-hallintokorvauksen-toimenpide
SELECT id
  FROM toimenpide
 WHERE koodi = '23151';
