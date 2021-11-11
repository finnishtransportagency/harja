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
       ut.tavoitehinta_indeksikorjattu                                                     AS "tavoitehinta-indeksikorjattu",
       ut.tavoitehinta_siirretty_indeksikorjattu                                           AS "tavoitehinta-siirretty-indeksikorjattu",
       ut.kattohinta_indeksikorjattu                                                       AS "kattohinta-indeksikorjattu",
       ut.indeksikorjaus_vahvistettu                                                       AS "indeksikorjaus-vahvistettu",
       ut.vahvistaja,
       ut.versio,
       (ut.tavoitehinta_indeksikorjattu + COALESCE(t.summa, 0))                            AS "tavoitehinta-oikaistu",
       COALESCE(ko."uusi-kattohinta", (kattohinta_indeksikorjattu + COALESCE(t.summa, 0))) AS "kattohinta-oikaistu"
from urakka_tavoite ut
         LEFT JOIN urakka u ON ut.urakka = u.id
         LEFT JOIN kattohinnan_oikaisu ko ON (u.id = ko."urakka-id" AND
                                              EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 =
                                              ko."hoitokauden-alkuvuosi")
         LEFT JOIN (SELECT SUM(t.summa) AS summa, t."urakka-id", t."hoitokauden-alkuvuosi"
                    FROM tavoitehinnan_oikaisu t
                    WHERE NOT t.poistettu
                    GROUP BY t."urakka-id", t."hoitokauden-alkuvuosi") t ON (ut.urakka = t."urakka-id")
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
  FROM kiinteahintainen_tyo kt2
           LEFT JOIN toimenpideinstanssi tpi ON kt2.toimenpideinstanssi = tpi.id
 WHERE tpi.urakka = :urakka-id
   AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   AND kt.versio = 0;

--name: vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille!
UPDATE kustannusarvioitu_tyo kt
   SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP END,
       vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja END
  FROM kustannusarvioitu_tyo kt2
           LEFT JOIN toimenpideinstanssi tpi ON kt2.toimenpideinstanssi = tpi.id
 WHERE tpi.urakka = :urakka-id
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