-- name: hae-erilliskustannus
SELECT id, sopimus, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi,
       CASE
           WHEN tyyppi::TEXT IN ('lupausbonus', 'asiakastyytyvaisyysbonus')
               THEN (SELECT korotus
                     FROM sanktion_indeksikorotus(pvm, indeksin_nimi, rahasumma, :urakka-id::INTEGER,
                                                  NULL::SANKTIOLAJI))
           ELSE 0
           END                AS indeksikorjaus,
       lisatieto, urakka, ulkoinen_id,
       tyyppi, kasittelytapa, laskutuskuukausi, luoja, luotu, muokkaaja, muokattu
FROM erilliskustannus
WHERE id = :id;

-- name: poista-erilliskustannus!
UPDATE erilliskustannus set poistettu = true, muokattu = NOW(), muokkaaja = :kayttaja-id WHERE id = :id;

-- name: hae-urakan-bonukset-analytiikalle
-- Hakee kaikki urakan bonukset palautettavaksi analytiikalle toteutuneiden kustannusten rajapinnan kautta.
-- Palauttaa myös poistetut bonukset.
-- Käytetään MH-urakoissa, soveltuu myös vanhojen alueurakoiden bonusten palauttamiseen.
SELECT ek.id               AS "bonus-id",
       ek.laskutuskuukausi AS "bonuksen-ajankohta",
       ek.indeksin_nimi    AS "indeksi",
       ek.rahasumma        AS "bonuksen-maara",
       tp.id               AS "toimenpide-id",
       ek.tyyppi           AS "bonustyyppi",
       ek.pvm              AS "bonuksen-kasittelyajankohta",
       ek.kasittelytapa    AS "bonuksen-kasittelytapa",
       ek.poistettu        AS "poistettu"
FROM erilliskustannus ek
         JOIN toimenpideinstanssi tpi ON ek.toimenpideinstanssi = tpi.id
         JOIN toimenpide tp ON tpi.toimenpide = tp.id
         JOIN urakka u ON tpi.urakka = u.id
WHERE u.id = :urakka-id
  AND ek.tyyppi != 'muu';
