-- name: hae-toteutuneiden-siirtojen-maara
-- single?: true
SELECT count(*) as maara
  FROM toteutunut_tyo tt
WHERE tt.vuosi = :vuosi
  AND tt.kuukausi = :kuukausi;

-- name: siirra-kustannusarvoidut-tyot-toteutumiin!
INSERT INTO toteutunut_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu)
SELECT k.vuosi, k.kuukausi, k.summa, k.tyyppi, k.tehtava, k.tehtavaryhma, k.toimenpideinstanssi, k.sopimus, NOW()
  FROM kustannusarvioitu_tyo k
 WHERE k.kuukausi = :kuukausi
   AND k.vuosi = :vuosi
ON CONFLICT DO NOTHING ;
