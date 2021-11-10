-- name: hae-oikaistu-tavoitehinta
-- single?: true
SELECT ut.tavoitehinta_indeksikorjattu + COALESCE(t.summa, 0)
FROM urakka_tavoite ut
         LEFT JOIN urakka u ON ut.urakka = u.id
         LEFT JOIN tavoitehinnan_oikaisu t ON (u.id = t."urakka-id" AND
                                               EXTRACT(YEAR FROM u.alkupvm) + ut.hoitokausi - 1 =
                                               t."hoitokauden-alkuvuosi")
WHERE ut.urakka = :urakka-id
  AND EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 = :hoitokauden-alkuvuosi;

-- name: hae-oikaistu-kattohinta
-- single?: true
SELECT COALESCE(k."uusi-kattohinta", ut.kattohinta_indeksikorjattu + COALESCE(t.summa, 0))
FROM urakka_tavoite ut
         LEFT JOIN urakka u ON ut.urakka = u.id
         LEFT JOIN tavoitehinnan_oikaisu t ON (u.id = t."urakka-id" AND
                                               EXTRACT(YEAR FROM u.alkupvm) + ut.hoitokausi - 1 =
                                               t."hoitokauden-alkuvuosi")
         LEFT JOIN kattohinnan_oikaisu k ON (u.id = k."urakka-id" AND
                                             EXTRACT(YEAR FROM u.alkupvm) + ut.hoitokausi - 1 =
                                             k."hoitokauden-alkuvuosi")
WHERE ut.urakka = :urakka-id
  AND EXTRACT(YEAR from u.alkupvm) + ut.hoitokausi - 1 = :hoitokauden-alkuvuosi;
