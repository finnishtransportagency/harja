-- name: hae-toteutuneiden-siirtojen-maara
-- single?: true
SELECT count(*) as maara
FROM toteutunut_tyo tt
WHERE tt.vuosi = :vuosi
  AND tt.kuukausi = :kuukausi;

-- name: siirra-kustannusarvoidut-tyot-toteutumiin!
-- Automaattisesti toteumaksi lasketaan tehtäväryhmä: Erillishankinnat (W)
-- Ja tehtävät: 3054 ja 3055
INSERT INTO toteutunut_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus_id, urakka_id, luotu)
SELECT k.vuosi, k.kuukausi, k.summa, k.tyyppi, k.tehtava, k.tehtavaryhma, k.toimenpideinstanssi, k.sopimus,
       (select s.urakka FROM sopimus s where s.id = k.sopimus) as "urakka-id", NOW()
FROM kustannusarvioitu_tyo k
WHERE k.kuukausi = :kuukausi
  AND k.vuosi = :vuosi
  AND (k.tehtavaryhma = (SELECT id FROM tehtavaryhma where nimi = 'Erillishankinta (W)')
        OR k.tehtava = (select id from toimenpidekoodi t where t.nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.') -- 3055,"Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
        OR k.tehtava = (select id from toimenpidekoodi t where t.nimi = 'Hoitourakan työnjohto') -- Hoitourakan työnjohto
    )
ON CONFLICT DO NOTHING ;

-- name: siirra-johto-ja-hallintokorvaukset-toteutumiin!
-- Tästä taulusta siirretään kaikki rivit
INSERT INTO toteutunut_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus_id, urakka_id, luotu)
SELECT j.vuosi, j.kuukausi, (j.tunnit * j.tuntipalkka) AS summa, 'laskutettava-tyo' AS tyyppi, null as tehtava,
       (SELECT id FROM tehtavaryhma WHERE nimi = 'Johto- ja hallintokorvaus (J)'),
       (SELECT tpi.id AS id
        FROM toimenpideinstanssi tpi
                 JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                 JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
             maksuera m
        WHERE tpi.urakka = j."urakka-id"
          AND m.toimenpideinstanssi = tpi.id
          AND tpk2.koodi = '23150'),
       (SELECT id
        FROM sopimus s
        WHERE s.urakka = j."urakka-id"
          AND s.poistettu IS NOT TRUE
        ORDER BY s.loppupvm DESC limit 1),
       j."urakka-id",
       NOW()
FROM johto_ja_hallintokorvaus j
WHERE j.kuukausi = :kuukausi
  AND j.vuosi = :vuosi
ON CONFLICT DO NOTHING ;
