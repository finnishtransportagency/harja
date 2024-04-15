-- name: hae-paikkaus-kustannukset
SELECT   p.id, 
         p."ulkoinen-id"             AS tunniste,
         SUM(p.kustannus)            AS kokonaiskustannus,
         p.tyomenetelma
FROM     paikkaus p
WHERE    p."urakka-id" = :urakka-id
AND      (:alkuaika::DATE IS NULL OR p.alkuaika >= :alkuaika::DATE)
AND      (:loppuaika::DATE IS NULL OR p.loppuaika <= :loppuaika::DATE)
AND      p.poistettu = FALSE
GROUP BY p.tyomenetelma, p.id;
