-- name: poista-turvalaitteet!
DELETE FROM turvalaite;

-- name: luo-turvalaite<!
INSERT INTO turvalaite (sijainti,
                        tunniste,
                        arvot)
VALUES (:sijainti :: GEOMETRY,
        :tunniste,
        :arvot :: JSONB);