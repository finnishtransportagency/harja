-- name: hae-urakan-lupaustiedot
SELECT pisteet as "luvattu-pistemaara"
  FROM lupaus_sitoutuminen
 WHERE "urakka-id" = :urakkaid;