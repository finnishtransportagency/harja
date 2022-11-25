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
