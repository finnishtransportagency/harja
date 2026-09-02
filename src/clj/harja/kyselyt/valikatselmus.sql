-- name: hae-oikaistu-tavoitehinta
-- single?: true
SELECT ut.tavoitehinta_indeksikorjattu + COALESCE(t.summa, 0)
FROM urakka_tavoite ut
         LEFT JOIN urakka u ON ut.urakka = u.id
         LEFT JOIN (SELECT SUM(t.summa) AS summa, t."urakka-id", t."hoitokauden-alkuvuosi"
                    FROM tavoitehinnan_oikaisu t
                    WHERE NOT t.poistettu
                    GROUP BY t."urakka-id", t."hoitokauden-alkuvuosi") t ON (ut.urakka = t."urakka-id" AND t."hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi)
WHERE ut.urakka = :urakka-id
  AND EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 = :hoitokauden-alkuvuosi;

-- name: hae-hoitokauden-alun-indeksikorjattu-tavoitehinta
-- single?: true
-- Käytetään esimerkiksi tavoitepalkkion laskemisessa. Pyöristä hakiessa kahteen desimaaliin. Joskus indexin laskemat voi pyöristää sen kolmeen desimaaliin.
SELECT ROUND(ut.tavoitehinta_indeksikorjattu::NUMERIC, 2) AS tavoitehinta
  FROM urakka_tavoite ut
         JOIN urakka u ON ut.urakka = u.id
 WHERE ut.urakka = :urakka-id
   AND EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 = :hoitokauden-alkuvuosi;

-- name: hae-hoitokauden-lopun-indeksikorjaamaton-tavoitehinta
-- single?: true
-- Käytetään hoidonjohtopalkkion muutoksen laskemisessa
SELECT ut.tavoitehinta + COALESCE(t.summa, 0) as tavoitehinta
FROM urakka_tavoite ut
         JOIN urakka u ON ut.urakka = u.id
         LEFT JOIN (SELECT SUM(t.summa) AS summa, t."urakka-id", t."hoitokauden-alkuvuosi"
                    FROM tavoitehinnan_oikaisu t
                    WHERE NOT t.poistettu
                    GROUP BY t."urakka-id", t."hoitokauden-alkuvuosi") t ON (ut.urakka = t."urakka-id" AND t."hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi)
WHERE ut.urakka = :urakka-id
  AND EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 = :hoitokauden-alkuvuosi;


-- name: hae-oikaistu-kattohinta
-- single?: true
SELECT COALESCE(k."uusi-kattohinta", ut.kattohinta_indeksikorjattu
    + (COALESCE(t.summa,0) * 1.1)) -- Katottihinta kasvaa 10% myös tavoitehinnan oikaisuista.
FROM urakka_tavoite ut
         LEFT JOIN urakka u ON ut.urakka = u.id
         LEFT JOIN (SELECT SUM(t.summa) AS summa, t."urakka-id", t."hoitokauden-alkuvuosi"
                    FROM tavoitehinnan_oikaisu t
                    WHERE NOT t.poistettu AND t."hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi
                    GROUP BY t."urakka-id", t."hoitokauden-alkuvuosi") t ON (ut.urakka = t."urakka-id")
         LEFT JOIN kattohinnan_oikaisu k ON (u.id = k."urakka-id" AND
                                             EXTRACT(YEAR FROM u.alkupvm) + ut.hoitokausi - 1 =
                                             k."hoitokauden-alkuvuosi" AND
                                             NOT k.poistettu)
WHERE ut.urakka = :urakka-id
  AND EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 = :hoitokauden-alkuvuosi;

-- name: hintapaatos-tehty?
-- single?: true
SELECT EXISTS(
           SELECT pta.id
           FROM paatos_tavoitehinta_alitus pta
           WHERE pta.urakkaid = :urakka-id
             AND pta.poistettu = FALSE
             AND pta.hoitokauden_alkuvuosi in (:vuodet)
           UNION ALL
           SELECT pty.id
           FROM paatos_tavoitehinta_ylitys pty
           WHERE pty.urakkaid = :urakka-id
             AND pty.poistettu = FALSE
             AND pty.hoitokauden_alkuvuosi in (:vuodet)
           UNION ALL
           SELECT pk.id
           FROM paatos_kattohinta pk
           WHERE pk.urakkaid = :urakka-id
             AND pk.poistettu = FALSE
             AND pk.hoitokauden_alkuvuosi in (:vuodet));

-- name: hae-urakan-hintapaatokset
-- Haetaan vuosittain tulevat välikatselmukset ja niille tieto, että onko päätöstä/välikatselmusta tehty
SELECT pta.hoitokauden_alkuvuosi as "hoitokauden-alkuvuosi"
FROM paatos_tavoitehinta_alitus pta
WHERE pta.urakkaid = :urakka-id
AND pta.poistettu = FALSE
UNION ALL
SELECT pty.hoitokauden_alkuvuosi  as "hoitokauden-alkuvuosi"
FROM paatos_tavoitehinta_ylitys pty
WHERE pty.urakkaid = :urakka-id
  AND pty.poistettu = FALSE
UNION ALL
SELECT pk.hoitokauden_alkuvuosi as "hoitokauden-alkuvuosi"
FROM paatos_kattohinta pk
WHERE pk.urakkaid = :urakka-id
  AND pk.poistettu = FALSE;

-- name: hae-urakan-bonuksen-toimenpideinstanssi-id
-- single?: true
SELECT tpi.id AS id
FROM toimenpideinstanssi tpi
         JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
         JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id
WHERE tpi.urakka = :urakka-id
  AND tpk2.koodi = '23150'
limit 1;

-- name: hae-urakan-tavoitehintaan-vaikuttavat-muutokset-analytiikalle
-- Hakee kaikki välikatselmukseen liittyvät tavoitehintaan vaikuttavat muutokset palautettavaksi analytiikalle toteutuneiden kustannusten rajapinnan kautta.
-- Palauttaa myös poistetuksi merkityt muutokset.
-- Käytetään MH-urakoissa. Tavoitehintaan vaikuttavista muutoksista on käytetty koodissa myös ilmaisua tavoitehinnan oikaisut.
SELECT id                      AS "muutos-id",
       "hoitokauden-alkuvuosi" AS "muutoksen-hoitovuosi",
       summa                   AS "muutoksen-maara",
       otsikko                 AS "muutoskategoria",
       selite                  AS "muutoksen-selite",
       poistettu               AS "poistettu"
FROM tavoitehinnan_oikaisu toi
WHERE "urakka-id" = :urakka-id
ORDER BY "hoitokauden-alkuvuosi", otsikko, id;

-- name: hae-bonukset
SELECT e.rahasumma, e.tyyppi
  FROM erilliskustannus e
 WHERE e.urakka = :urakka-id
   AND e.poistettu IS NOT TRUE
   AND e.laskutuskuukausi BETWEEN :alkupvm::DATE AND :loppupvm::DATE;

-- name: hae-sanktiot
WITH urakan_tiedot AS (
    SELECT u.id,
           EXTRACT(YEAR FROM u.alkupvm)::INT AS alkuvuosi
    FROM urakka u
    WHERE u.id = :urakka-id
)
SELECT s.maara * -1 AS maara, -- Sanktiot on negatiivisia uilla
       s.sakkoryhma,
       (SELECT korotus * -1 FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi,s.maara, :urakka-id::INT, s.sakkoryhma)) AS indeksikorjaus
  FROM sanktio s
           JOIN toimenpideinstanssi tpi ON tpi.urakka = :urakka-id AND tpi.id = s.toimenpideinstanssi
           CROSS JOIN urakan_tiedot u
 WHERE s.poistettu IS NOT TRUE
   AND s.perintapvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
   AND (s.sakkoryhma != 'arvonvahennyssanktio' OR (u.alkuvuosi < 2025 AND :hoitokauden-alkuvuosi::INT <= 2025));

-- name: hae-arvonvahennykset
WITH urakan_tiedot AS (
    SELECT u.id,
           EXTRACT(YEAR FROM u.alkupvm)::INT AS alkuvuosi
    FROM urakka u
    WHERE u.id = :urakka-id
)
SELECT s.maara * -1 AS maara, -- Sanktiot on negatiivisia uilla
       s.sakkoryhma
  FROM sanktio s
           JOIN toimenpideinstanssi tpi ON tpi.urakka = :urakka-id AND tpi.id = s.toimenpideinstanssi
           CROSS JOIN urakan_tiedot u
 WHERE s.poistettu IS NOT TRUE
   AND s.perintapvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
   AND (s.sakkoryhma = 'arvonvahennyssanktio' AND (u.alkuvuosi >= 2025 OR :hoitokauden-alkuvuosi::INT >= 2026));

-- name: hae-arvonvahennykset
WITH urakan_tiedot AS (
    SELECT u.id,
           EXTRACT(YEAR FROM u.alkupvm)::INT AS alkuvuosi
    FROM urakka u
    WHERE u.id = :urakka-id
)
SELECT s.maara * -1 AS maara, -- Sanktiot on negatiivisia uilla
       s.sakkoryhma
FROM sanktio s
         JOIN toimenpideinstanssi tpi ON tpi.urakka = :urakka-id AND tpi.id = s.toimenpideinstanssi
         CROSS JOIN urakan_tiedot u
WHERE s.poistettu IS NOT TRUE
  AND s.perintapvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND (s.sakkoryhma = 'arvonvahennyssanktio' AND (u.alkuvuosi >= 2025 OR :hoitokauden-alkuvuosi::INT >= 2026));

-- name: hae-tavoitehinnan-muutokset-hoitokaudelle
select id, "urakka-id", otsikko, selite, summa
from tavoitehinnan_oikaisu
where "urakka-id" = :urakkaid
  and "hoitokauden-alkuvuosi" = :hoitokauden_alkuvuosi
  and poistettu is not true;

-- name: poista-tavoitehinnan-muutos!
UPDATE tavoitehinnan_oikaisu
SET poistettu = true,
    muokattu  = now(),
    "muokkaaja-id" = :muokkaaja-id
WHERE id = :id;
