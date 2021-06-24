-- name: hae-oikaistu-tavoitehinta
SELECT (SELECT tavoitehinta FROM urakka_tavoite ut WHERE ut.urakka = :urakka-id AND hoitokausi = :hoitokausi) +
       SUM(summa)
FROM tavoitehinnan_oikaisu
WHERE "urakka-id" = :urakka-id
  AND "hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi;