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
WHERE jh."urakka-id" = :urakka-id
