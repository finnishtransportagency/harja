-- name: tallenna-liite<!
-- Tallentaa uuden liitteen. Liitteen large object pitää olla tallennettuna jo ja oid annettava.
INSERT
  INTO liite
       (nimi, tyyppi, koko, liite_oid, pikkukuva, luoja, luotu, urakka)
VALUES (:nimi, :tyyppi, :koko, :oid, :pikkukuva, :luoja, current_timestamp, :urakka)

-- name: hae-liite-lataukseen
-- Hakee liitteen tiedot sen latausta varten. 
SELECT liite_oid, tyyppi, koko, urakka FROM liite WHERE id = :id

-- name: hae-pikkukuva-lataukseen
-- Hakee liitteen pikkukuvan sen latausta varten.
SELECT pikkukuva, urakka FROM liite WHERE id = :id
