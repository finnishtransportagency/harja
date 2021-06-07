-- name: hae-koodi-harja-koodin-perusteella
SELECT kkk.koodi
FROM koodisto_konversio_koodit kkk
WHERE kkk.koodisto_konversio_id = :koodisto_id and
      kkk.harja_koodi = :harja_koodi;
