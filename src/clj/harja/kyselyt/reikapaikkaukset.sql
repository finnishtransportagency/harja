-- name: hae-reikapaikkaukset
SELECT 
    id, 
    sijainti, 
    tyomenetelma, 
    massatyyppi FROM paikkaus WHERE "urakka-id" = :urakka-id LIMIT 20;
