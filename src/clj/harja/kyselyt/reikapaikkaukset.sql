-- name: hae-reikapaikkaukset
SELECT id, tyomenetelma, massatyyppi FROM paikkaus WHERE "urakka-id" = :urakka-id LIMIT 10;
