-- name: tallenna-tarjous<!
INSERT INTO tarjous (hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta, luoja, luotu)
VALUES (:hoitokauden_alkuvuosi, :urakka_id, :tarjous_tavoitehinta, :tarjous_kattohinta, :luoja, NOW());

-- name: paivita-tarjous<!
UPDATE tarjous SET hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi,
                   tarjous_tavoitehinta = :tarjous_tavoitehinta,
                   tarjous_kattohinta = :tarjous_kattohinta,
                   muokkaaja = :muokkaaja, muokattu = NOW()
WHERE id = :id;

-- name: tallenna-tarjouskustannus<!
INSERT INTO tarjous_kustannukset (tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id, tehtavaryhma_id, rahavaraus_id, luoja, luotu)
VALUES (:tarjous_id, :urakka_id, :hoitokauden_alkuvuosi, :summa, :osio::suunnittelu_osio, :tehtava_id, :tehtavaryhma_id, :rahavaraus_id, :luoja, NOW());

-- name: tallenna-tarjouksen-johto-ja-hallintokorvaus<!
INSERT INTO tarjous_johto_ja_hallintokorvaus (tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, johto_ja_hallintokorvaus_toimenkuva_id, tehtavaryhma_id, tehtava_id, luoja, luotu)
VALUES (:tarjous_id, :urakka_id, :hoitokauden_alkuvuosi, :summa, :osio::suunnittelu_osio, :johto_ja_hallintokorvaus_toimenkuva_id, :tehtavaryhma_id, :tehtava_id, :luoja, NOW());

-- name: hae-tarjouksen-tiedot
SELECT t.id as "tarjous-id", t.hoitokauden_alkuvuosi, t.urakka_id, t.tarjous_tavoitehinta, t.tarjous_kattohinta,
       (SELECT array_agg(row(tk.id,
           CASE WHEN tk.osio = 'tavoitehintaiset-rahavaraukset'::suunnittelu_osio THEN r.nimi
                WHEN tk.osio = 'hankintakustannukset'::suunnittelu_osio THEN 'Kilpailutettavat hankinnat'
                WHEN tk.osio = 'erillishankinnat'::suunnittelu_osio THEN 'Erillishankinnat'
                WHEN tk.osio = 'hoidonjohtopalkkio'::suunnittelu_osio THEN 'Hoidonjohtopalkkio'
                ELSE tk.osio::text END,
            tk.summa, tk.osio, tk.tehtava_id, tk.tehtavaryhma_id, tk.rahavaraus_id))
        FROM tarjous_kustannukset tk
                 LEFT JOIN rahavaraus r ON r.id = tk.rahavaraus_id
        WHERE tk.tarjous_id = t.id) as kustannukset,
       (SELECT array_agg(row(tj.id, UPPER(LEFT(jjht.toimenkuva, 1)) || RIGHT(jjht.toimenkuva, -1), tj.summa, tj.osio, tj.johto_ja_hallintokorvaus_toimenkuva_id))
        FROM tarjous_johto_ja_hallintokorvaus tj
                 LEFT JOIN johto_ja_hallintokorvaus_toimenkuva jjht ON jjht.id = tj.johto_ja_hallintokorvaus_toimenkuva_id
        WHERE tj.tarjous_id = t.id) as toimenkuvat
  FROM tarjous t
 WHERE t.urakka_id = :urakka_id
 ORDER BY t.hoitokauden_alkuvuosi ASC;

-- name: hae-tarjous-vuodella
-- Haetaan tarjousrivi urakan ja vuoden perusteella.
SELECT id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta
  FROM tarjous
 WHERE urakka_id = :urakka_id
   AND hoitokauden_alkuvuosi = :vuosi;

-- name: hae-kustannus-tarjoukselle
SELECT id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio::text, tehtava_id, tehtavaryhma_id, rahavaraus_id
  FROM tarjous_kustannukset
 WHERE tarjous_id = :tarjous_id
   AND urakka_id = :urakka_id
   AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
   AND osio = :osio::suunnittelu_osio
   AND (:tehtava_id::INTEGER IS NULL OR tehtava_id = :tehtava_id)
   AND (:tehtavaryhma_id::INTEGER IS NULL OR tehtavaryhma_id = :tehtavaryhma_id)
   AND (:rahavaraus_id::INTEGER IS NULL OR rahavaraus_id = :rahavaraus_id);

-- name: paivita-tarjouskustannus<!
UPDATE tarjous_kustannukset
SET summa = :summa,
    osio = :osio::suunnittelu_osio,
    tehtava_id = :tehtava_id,
    tehtavaryhma_id = :tehtavaryhma_id,
    rahavaraus_id = :rahavaraus_id,
    muokkaaja = :muokkaaja, muokattu = NOW()
WHERE id = :id;

-- name: hae-toimenkuva-tarjoukselle
SELECT id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio::text,
       johto_ja_hallintokorvaus_toimenkuva_id, tehtavaryhma_id, tehtava_id
  FROM tarjous_johto_ja_hallintokorvaus
 WHERE tarjous_id = :tarjous_id
   AND johto_ja_hallintokorvaus_toimenkuva_id = :johto_ja_hallintokorvaus_toimenkuva_id
   AND urakka_id = :urakka_id
   AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
   AND osio = :osio::suunnittelu_osio
   AND (:tehtava_id::INTEGER IS NULL OR tehtava_id = :tehtava_id)
   AND (:tehtavaryhma_id::INTEGER IS NULL OR tehtavaryhma_id = :tehtavaryhma_id);

-- name: paivita-tarjouksen-johto-ja-hallintokorvaus<!
UPDATE tarjous_johto_ja_hallintokorvaus
SET summa = :summa,
    osio = :osio::suunnittelu_osio,
    johto_ja_hallintokorvaus_toimenkuva_id = :johto_ja_hallintokorvaus_toimenkuva_id,
    tehtavaryhma_id = :tehtavaryhma_id,
    tehtava_id = :tehtava_id,
    muokkaaja = :muokkaaja,
    muokattu = NOW()
WHERE id = :id;

-- name: poista-tarjouksen-johto-ja-hallintokorvaus<!
DELETE FROM tarjous_johto_ja_hallintokorvaus
 WHERE johto_ja_hallintokorvaus_toimenkuva_id = :toimenkuvaid
   AND urakka_id = :urakkaid;

-- name: hae-urakan-tarjous-tavoitehinnat
SELECT id, hoitokausi as hoitovuosinro, urakka, tarjous_tavoitehinta
  FROM urakka_tavoite
 WHERE urakka = :urakkaid
 ORDER BY hoitokausi;

-- name: lisaa-urakan-tavoite-tarjous<!
INSERT INTO urakka_tavoite (hoitokausi, urakka, tarjous_tavoitehinta, luoja, luotu)
VALUES (:hoitovuosinro, :urakkaid, :tarjous_tavoitehinta, :luoja, NOW());

-- name: paivita-urakan-tavoite-tarjous<!
UPDATE urakka_tavoite
SET tarjous_tavoitehinta = :tarjous_tavoitehinta,
    muokkaaja = :muokkaaja,
    muokattu = NOW()
WHERE id = :id;
