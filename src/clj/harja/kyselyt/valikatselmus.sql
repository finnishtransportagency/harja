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
