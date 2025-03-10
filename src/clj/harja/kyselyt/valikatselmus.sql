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
-- Käytetään esimerkiksi tavoitepalkkion laskemisessa
SELECT ut.tavoitehinta_indeksikorjattu as tavoitehinta
  FROM urakka_tavoite ut
         JOIN urakka u ON ut.urakka = u.id
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
    SELECT up.id as id
      FROM urakka_paatos up
     WHERE up.poistettu = FALSE
       AND up."hoitokauden-alkuvuosi" in (:vuodet)
       AND up."urakka-id" = :urakka-id
       AND up.tyyppi IN ('tavoitehinnan-ylitys', 'kattohinnan-ylitys', 'tavoitehinnan-alitus'));

-- name: hae-urakan-hintapaatokset
-- Haetaan vuosittain tulevat välikatselmukset ja niille tieto, että onko päätöstä/välikatselmusta tehty
SELECT up."hoitokauden-alkuvuosi"
  FROM urakka_paatos up
 WHERE up.poistettu = FALSE
   AND up."urakka-id" = :urakka-id
   AND up.tyyppi IN ('tavoitehinnan-ylitys', 'kattohinnan-ylitys', 'tavoitehinnan-alitus');

-- name: hae-urakan-bonuksen-toimenpideinstanssi-id
-- single?: true
SELECT tpi.id AS id
FROM toimenpideinstanssi tpi
         JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
         JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id
WHERE tpi.urakka = :urakka-id
  AND tpk2.koodi = '23150'
limit 1;

-- name: hae-paatos
SELECT id, "hoitokauden-alkuvuosi", "urakka-id", "hinnan-erotus", "urakoitsijan-maksu", "tilaajan-maksu",
       siirto, tyyppi, "lupaus-luvatut-pisteet", "lupaus-toteutuneet-pisteet", "lupaus-tavoitehinta",
       muokattu, "muokkaaja-id", "luoja-id", luotu, poistettu, erilliskustannus_id, sanktio_id, kulu_id
FROM urakka_paatos
WHERE id = :id;

-- name: hae-urakan-hoitovuosien-paatokset-analytiikalle
-- Hakee urakan hoitokauden päättyessa suorittamiin välikatselmuksiin liittyvät tiedot palautettavaksi analytiikalle toteutuneiden kustannusten rajapinnan kautta.
-- Käytetään MH-urakoissa.
SELECT id                           AS "paatos-id",
       "hoitokauden-alkuvuosi"      AS "paatoksen-hoitovuosi",
       tyyppi                       AS "paatostyyppi", -- 'tavoitehinnan-ylitys', 'kattohinnan-ylitys', 'tavoitehinnan-alitus', 'lupausbonus', 'lupaussanktio'
       "hinnan-erotus"              AS "paatoksen-tulos_kokonaismaara",
       "urakoitsijan-maksu"         AS "paatoksen-tulos_urakoitsija-maksaa",
       "tilaajan-maksu"             AS "paatoksen-tulos_tilaaja-maksaa",
       siirto                       AS "paatoksen-tulos_siirretaan-seuraavalle-hoitovuodelle",
       "lupaus-tavoitehinta"        AS "paatoksen-tulos_tavoitehinta",
       "lupaus-luvatut-pisteet"     AS "lupausten-tulos_luvatut-pisteet",
       "lupaus-toteutuneet-pisteet" AS "lupausten-tulos_toteutuneet-pisteet",
       kulu_id                      AS "viittaukset-toteutuneisiin-kustannuksiin_kulu-id",
       sanktio_id                   AS "viittaukset-toteutuneisiin-kustannuksiin_sanktio-id",
       erilliskustannus_id          AS "viittaukset-toteutuneisiin-kustannuksiin_bonus-id",
       poistettu                    AS "poistettu"
FROM urakka_paatos up
WHERE "urakka-id" = :urakka-id
ORDER BY "hoitokauden-alkuvuosi", tyyppi;

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
SELECT s.maara,
       s.sakkoryhma,
       (SELECT korotus FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi,s.maara, :urakka-id::INT, s.sakkoryhma)) AS indeksikorjaus
  FROM sanktio s
           JOIN toimenpideinstanssi tpi ON tpi.urakka = :urakka-id AND tpi.id = s.toimenpideinstanssi
 WHERE s.poistettu IS NOT TRUE
   AND s.perintapvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE;

-- name: hae-tavoitehinnan-muutokset-hoitokaudelle
select id, "urakka-id", otsikko, selite, summa
from tavoitehinnan_oikaisu
where "urakka-id" = :urakkaid
  and "hoitokauden-alkuvuosi" = :hoitokauden_alkuvuosi
  and poistettu is not true;
