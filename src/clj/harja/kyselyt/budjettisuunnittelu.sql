-- name:tallenna-budjettitavoite<!
INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, kattohinta, luotu, luoja)
VALUES (:urakka, :hoitokausi, :tavoitehinta, :kattohinta, current_timestamp, :kayttaja);

-- name:paivita-budjettitavoite<!
UPDATE urakka_tavoite
SET tavoitehinta           = :tavoitehinta,
    kattohinta             = :kattohinta,
    muokattu               = current_timestamp,
    muokkaaja              = :kayttaja
WHERE urakka = :urakka
  AND hoitokausi = :hoitokausi;

-- name:hae-budjettitavoite
SELECT *
from urakka_tavoite
WHERE urakka = :urakka;

-- name:hae-johto-ja-hallintokorvaukset
SELECT jh.tunnit,
       jh.tuntipalkka,
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

-- name:hae-urakan-omat-jh-korvaukset
SELECT id AS "toimenkuva-id",
       toimenkuva
FROM johto_ja_hallintokorvaus_toimenkuva
WHERE "urakka-id" = :urakka-id

-- name:lisaa-oma-johto-ja-hallintokorvaus-toimenkuva<!
INSERT INTO johto_ja_hallintokorvaus_toimenkuva (toimenkuva, "urakka-id")
VALUES (:toimenkuva, :urakka-id)
RETURNING id;

--name: vahvista-indeksikorjaukset-kiinteahintaisille-toille!
UPDATE kiinteahintainen_tyo kt
   SET indeksikorjaus_vahvistettu = :vahvistus-pvm::TIMESTAMP,
       vahvistaja                 = :vahvistaja
  FROM kiinteahintainen_tyo kt2
           LEFT JOIN toimenpideinstanssi tpi ON kt2.toimenpideinstanssi = tpi.id
 WHERE tpi.urakka = :urakka-id
   AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   AND kt.osio = :osio::SUUNNITTELU_OSIO
   AND kt.indeksikorjaus_vahvistettu IS NULL
   AND kt.versio = 0;

--name: vahvista-indeksikorjaukset-kustannusarvioiduille-toille!
UPDATE kustannusarvioitu_tyo kt
   SET indeksikorjaus_vahvistettu = :vahvistus-pvm::TIMESTAMP,
       vahvistaja                 = :vahvistaja
  FROM kustannusarvioitu_tyo kt2
           LEFT JOIN toimenpideinstanssi tpi ON kt2.toimenpideinstanssi = tpi.id
 WHERE tpi.urakka = :urakka-id
   AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   AND kt.osio = :osio::SUUNNITTELU_OSIO
   AND kt.indeksikorjaus_vahvistettu IS NULL
   AND kt.versio = 0;

--name: vahvista-indeksikorjaukset-jh-korvauksille!
UPDATE johto_ja_hallintokorvaus jh
   SET indeksikorjaus_vahvistettu = :vahvistus-pvm::TIMESTAMP,
       vahvistaja                 = :vahvistaja
 WHERE jh."urakka-id" = :urakka-id
   AND (concat(jh.vuosi, '-', jh.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   AND jh.indeksikorjaus_vahvistettu IS NULL
   AND jh.versio = 0;

--name: vahvista-indeksikorjaukset-urakan-tavoitteille!
UPDATE urakka_tavoite ut
   SET indeksikorjaus_vahvistettu = :vahvistus-pvm::TIMESTAMP,
       vahvistaja                 = :vahvistaja
 WHERE ut.urakka = :urakka-id
   -- hoitokausi ei ole hoitovuosi e.g. 2020, vaan hoitovuoden järjestysnumero e.g. 1
   AND ut.hoitokausi = :hoitovuosi-nro
   AND ut.indeksikorjaus_vahvistettu IS NULL
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