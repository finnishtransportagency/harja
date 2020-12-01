-- name: hae-toteutuneiden-siirtojen-maara
-- single?: true
SELECT count(*)
  FROM toteutunut_tyo tt
WHERE tt.vuosi = :vuosi
  AND tt.kuukausi = :kuukausi;

-- name: hae-kustannusarvioituun-tyohon-liittyvat-urakat
SELECT u.id as urakka_id, s.id as sopimus_id
  FROM urakka u
   JOIN sopimus s ON s.urakka = u.id
 WHERE u.tyyppi = 'teiden-hoito'::urakkatyyppi;

-- name: siirra-kustannusarvoidut-tyot-toteutumiin
INSERT INTO toteutunut_tyo
SELECT k.vuosi, k.kuukausi, k.summa, k.tyyppi, k.tehtava, k.tehtavaryhma, k.toimenpideinstanssi, k.sopimus
  FROM kustannusarvioitu_tyo k
 WHERE k.kuukausi = :kuukausi
   AND k.vuosi = :vuosi
ON CONFLICT DO NOTHING ;
