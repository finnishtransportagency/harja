-- name: hae-oikaistu-tavoitehinta
-- single?: true
SELECT ut.tavoitehinta_indeksikorjattu + COALESCE(t.summa, 0)
FROM urakka_tavoite ut
         LEFT JOIN urakka u ON ut.urakka = u.id
         LEFT JOIN (SELECT SUM(t.summa) AS summa, t."urakka-id", t."hoitokauden-alkuvuosi"
                    FROM tavoitehinnan_oikaisu t
                    WHERE NOT t.poistettu
                    GROUP BY t."urakka-id", t."hoitokauden-alkuvuosi") t ON (ut.urakka = t."urakka-id")
WHERE ut.urakka = :urakka-id
  AND EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 = :hoitokauden-alkuvuosi;

-- name: hae-oikaistu-kattohinta
-- single?: true
SELECT COALESCE(k."uusi-kattohinta", ut.kattohinta_indeksikorjattu + COALESCE(t.summa, 0))
FROM urakka_tavoite ut
         LEFT JOIN urakka u ON ut.urakka = u.id
         LEFT JOIN (SELECT SUM(t.summa) AS summa, t."urakka-id", t."hoitokauden-alkuvuosi"
                    FROM tavoitehinnan_oikaisu t
                    WHERE NOT t.poistettu
                    GROUP BY t."urakka-id", t."hoitokauden-alkuvuosi") t ON (ut.urakka = t."urakka-id")
         LEFT JOIN kattohinnan_oikaisu k ON (u.id = k."urakka-id" AND
                                             EXTRACT(YEAR FROM u.alkupvm) + ut.hoitokausi - 1 =
                                             k."hoitokauden-alkuvuosi" AND
                                             NOT k.poistettu)
WHERE ut.urakka = :urakka-id
  AND EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 = :hoitokauden-alkuvuosi;

-- name: onko-valikatselmus-pidetty?
-- single?: true
SELECT EXISTS(
    SELECT up.id as id
      FROM urakka_paatos up
     WHERE up.poistettu = FALSE
       AND up."hoitokauden-alkuvuosi" in (:vuodet)
       AND up."urakka-id" = :urakka-id
       AND up.tyyppi IN ('tavoitehinnan-ylitys', 'kattohinnan-ylitys', 'tavoitehinnan-alitus'));

-- name: hae-urakan-valikatselmukset-vuosittain
-- Haetaan vuosittain tulevat välikatselmukset ja niille tieto, että onko päätöstä/välikatselmusta tehty
SELECT up."hoitokauden-alkuvuosi"
  FROM urakka_paatos up
 WHERE up.poistettu = FALSE
   AND up."urakka-id" = :urakka-id
   AND up.tyyppi IN ('tavoitehinnan-ylitys', 'kattohinnan-ylitys', 'tavoitehinnan-alitus')

-- name: hae-urakan-bonuksen-toimenpideinstanssi-id
-- single?: true
SELECT tpi.id AS id
FROM toimenpideinstanssi tpi
         JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
         JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
     maksuera m
WHERE tpi.urakka = :urakka-id
  AND m.toimenpideinstanssi = tpi.id
  AND tpk2.koodi = '23150'
limit 1;

-- name: hae-paatos
SELECT id, "hoitokauden-alkuvuosi", "urakka-id", "hinnan-erotus", "urakoitsijan-maksu", "tilaajan-maksu",
       siirto, tyyppi, "lupaus-luvatut-pisteet", "lupaus-toteutuneet-pisteet", "lupaus-tavoitehinta",
       muokattu, "muokkaaja-id", "luoja-id", luotu, poistettu, erilliskustannus_id, sanktio_id
FROM urakka_paatos
WHERE id = :id;
