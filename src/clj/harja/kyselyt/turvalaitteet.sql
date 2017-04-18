-- name: poista-turvalaitteet!
DELETE FROM turvalaite;

-- name: luo-turvalaite<!
INSERT INTO turvalaite (sijainti,
                        tunniste,
                        nimi,
                        alityyppi,
                        sijainnin_kuvaus,
                        vayla,
                        tila)
VALUES (:sijainti,
        :tunniste,
        :nimi,
        :alityyppi,
        :sijainnin_kuvaus,
        :vayla,
        :tila);