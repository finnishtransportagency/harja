-- name: hae-koodi-harja-koodin-perusteella
SELECT kkk.tulos
FROM koodisto_konversio_koodit kkk
WHERE kkk.koodisto_konversio_id = :koodisto_id and
      kkk.lahde = :lahde;
