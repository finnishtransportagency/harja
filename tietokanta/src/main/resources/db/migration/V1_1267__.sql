-- Tallennetaan laskutusrajan alkuperäinen arvo ennen muutostöiden korotuksia.
-- Käytetään laskutusrajan automaattisessa tarkistuksessa hoitovuoden aikana.
ALTER TABLE urakka_tavoite ADD COLUMN IF NOT EXISTS laskutusraja_alkuperainen NUMERIC;

-- Tallennetaan puuttuvat arvot.
UPDATE urakka_tavoite ut
SET laskutusraja_alkuperainen = ut.laskutusraja
    FROM urakka u
WHERE ut.urakka = u.id
  AND EXTRACT(YEAR FROM u.alkupvm) = 2025
  AND u.tyyppi = 'teiden-hoito'
  AND ut.hoitokausi = 1
  AND ut.laskutusraja IS NOT NULL
  AND ut.laskutusraja_alkuperainen IS NULL;

-- Korotetaan laskutusrajaa, jos erillisrahoituksista aiheutuneet muutostyöt ylittävät 3 % indeksikorjatusta tavoitehinnasta.
UPDATE urakka_tavoite ut
SET laskutusraja_alkuperainen = ut.tavoitehinta_indeksikorjattu,
    laskutusraja = ut.tavoitehinta_indeksikorjattu
        + COALESCE((
                       SELECT CASE
                                  WHEN SUM(kv.summa) > ut.tavoitehinta_indeksikorjattu * 0.03
                                      THEN SUM(kv.summa)
                                  ELSE 0
                                  END
                       FROM mhu_muutos m
                                LEFT JOIN mhu_muutos_kustannusvaikutus kv ON kv.muutos = m.id
                                JOIN urakka u2 ON u2.id = m.urakka
                       WHERE m.urakka = ut.urakka
                         AND m.tyyppi = 'muutostyo'
                         AND m.alityyppi = 'erillisrahoitus'
                         AND m.poistettu IS NOT TRUE
                         AND m.voimassa_alkaen BETWEEN TO_DATE(EXTRACT(YEAR FROM u2.alkupvm)::TEXT || '-10-01', 'YYYY-MM-DD')
                           AND TO_DATE((EXTRACT(YEAR FROM u2.alkupvm)::INTEGER + 1)::TEXT || '-09-30', 'YYYY-MM-DD')
                   ), 0)
WHERE ut.hoitokausi = 1
  AND ut.urakka IN (
    SELECT u.id
    FROM urakka u
    WHERE EXTRACT(YEAR FROM u.alkupvm) = 2025
      AND u.tyyppi = 'teiden-hoito'
);
