-- name: tallenna-liite<!
-- Tallentaa uuden liitteen. Liitteen pitää olla tallennettuna jo ja fileyard UUID annettava.
INSERT
  INTO liite
       (nimi, tyyppi, koko, "fileyard-uuid", pikkukuva, luoja, luotu, urakka, kuvaus, lahde)
VALUES (:nimi, :tyyppi, :koko, :fileyard-uuid, :pikkukuva, :luoja, current_timestamp, :urakka,
        :kuvaus, :lahdejarjestelma::lahde);

-- name: hae-liite-lataukseen
-- Hakee liitteen tiedot sen latausta varten.
SELECT "fileyard-uuid", liite_oid, tyyppi, koko, urakka FROM liite WHERE id = :id;

-- name: hae-pikkukuva-lataukseen
-- Hakee liitteen pikkukuvan sen latausta varten.
SELECT pikkukuva, urakka FROM liite WHERE id = :id;


-- name: hae-urakan-liite-id
-- Hakee liitteen id:llä ja urakalla liitteen. Tätä on tarkoitus käyttää
-- varmistamaan, että käyttäjän antama liitteen id kuuluu oikeaan urakkaan
-- eikä liite id:tä manipuloimalla pääse käsiksi väärän urakan tietoihin.
SELECT id FROM liite WHERE urakka = :urakka AND id = :id;
