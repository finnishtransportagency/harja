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
