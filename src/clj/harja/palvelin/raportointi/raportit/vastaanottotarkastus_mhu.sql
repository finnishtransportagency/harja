-- name: hae-viranomaistehtavamaarat
-- Käytetään urakan viranomaistehtävissä avustamisen ja osallistumisen tuntimäärien hakemiseen raporttia varten.
-- Tehtävän nimi ja varmaan tarkoituskin on muuttunut aikojen saatossa.
SELECT SUM(tt.maara) as tuntia
  FROM tehtava teh
       JOIN toteuma_tehtava tt ON teh.id = tt.toimenpidekoodi
                              AND tt.hoitokauden_alkuvuosi = :hoitovuosi
                              AND tt.urakka_id = :urakka-id
       JOIN toteuma t ON tt.toteuma = t.id
                             AND t.urakka = :urakka-id

                             AND t.alkanut::DATE >= :alkupvm::DATE
                             AND t.alkanut::DATE < (:loppupvm::DATE + INTERVAL '1 day')
                             AND t.poistettu IS FALSE
WHERE teh.nimi = :nimi;

-- name: hae-bonukset-vastaanottotarkastusraportille
SELECT e.rahasumma, e.tyyppi
FROM erilliskustannus e
WHERE e.urakka = :urakka-id
  AND e.poistettu IS NOT TRUE
  AND e.laskutuskuukausi >= :alkupvm::DATE AND e.laskutuskuukausi < (:loppupvm::DATE + INTERVAL '1 day')
  AND e.tyyppi != 'muu'::erilliskustannustyyppi;

-- name: hae-sanktiot-vastaanottotarkastusraportille
SELECT s.maara * -1 AS maara, -- Sanktiot on negatiivisia uilla
       s.sakkoryhma
FROM sanktio s
         JOIN toimenpideinstanssi tpi ON tpi.urakka = :urakka-id AND tpi.id = s.toimenpideinstanssi
WHERE s.poistettu IS NOT TRUE
  AND s.perintapvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE;

-- name: hae-sakottomat-poikkeamaraportilliset-laatupoikkeamamaarat
-- Hakee raportille lukumäärän kaikki laatupoikkeamat, joilla on poikkeamaraportti, mutta joilla ei ole sakkoa
SELECT COUNT(lp.id) as kpl
FROM laatupoikkeama lp
         JOIN kayttaja k ON lp.luoja = k.id
         LEFT JOIN sanktio s ON lp.id = s.laatupoikkeama AND s.poistettu IS NOT TRUE
         LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE lp.urakka = :urakka-id
  AND lp.poistettu IS NOT TRUE
  AND lp."sisaltaa-poikkeamaraportin?" IS TRUE
  AND (aika >= :alkupvm AND aika < (:loppupvm::DATE + INTERVAL '1 day'))
  AND s.id IS NULL -- Ei saa sisältää sakkoa
  -- Ei kuulu poistettuun ylläpitokohteeseen
  AND (lp.yllapitokohde IS NULL
    OR
       lp.yllapitokohde IS NOT NULL AND
       (SELECT poistettu
        FROM yllapitokohde
        WHERE id = lp.yllapitokohde) IS NOT TRUE);
