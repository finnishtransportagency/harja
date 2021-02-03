-- Siirretään kaikki rivit mikäli tehtäväryhmä: Erillishankinnat (W) tai tehtävät: Toimistotarvike- ja ICT-kulut ja Hoitourakan työnjohto
-- kustannusarvoidut_tyot taulusta toteutuneet_kustannukset tauluun,
INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus_id,
                            urakka_id, luotu, rivin_tunnistin)
SELECT k.vuosi,
       k.kuukausi,
       k.summa,
       k.tyyppi,
       k.tehtava,
       k.tehtavaryhma,
       k.toimenpideinstanssi,
       k.sopimus,
       (select s.urakka FROM sopimus s where s.id = k.sopimus) as "urakka-id",
       NOW(),
       MD5(concat(k.id, k.vuosi, k.kuukausi, k.summa, k.tyyppi, k.tehtava, k.tehtavaryhma,
                  k.toimenpideinstanssi, k.sopimus, k.luotu, k.luoja, k.muokattu, k.muokkaaja)::TEXT)
FROM kustannusarvioitu_tyo k
WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', k.vuosi, k.kuukausi, 1)::DATE))) <
      date_trunc('month', current_date)
  AND (k.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c')
    OR k.tehtava in (SELECT id
                       FROM toimenpidekoodi t
                      WHERE t.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                         OR t.yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744') -- Hoitourakan työnjohto
    );

-- Siirretään kaikki olemassaolevat rivit johto_ja_hallintakorvaus taulusta toteutuneet_kustannukset tauluun
INSERT INTO toteutuneet_kustannukset (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                            sopimus_id, urakka_id, luotu, rivin_tunnistin)
SELECT j.vuosi,
       j.kuukausi,
       (j.tunnit * j.tuntipalkka)                     AS summa,
       'laskutettava-tyo'                             AS tyyppi,
       null                                           AS tehtava,
       (SELECT id
        FROM tehtavaryhma
        WHERE nimi = 'Johto- ja hallintokorvaus (J)') AS tehtavaryhma,
       (SELECT tpi.id AS id
        FROM toimenpideinstanssi tpi
                 JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                 JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
             maksuera m
        WHERE tpi.urakka = j."urakka-id"
          AND m.toimenpideinstanssi = tpi.id
          AND tpk2.koodi = '23150')                   AS toimenpideinstanssi,
       (SELECT id
        FROM sopimus s
        WHERE s.urakka = j."urakka-id"
          AND s.poistettu IS NOT TRUE
        ORDER BY s.loppupvm DESC)                     AS sopimus_id,
       j."urakka-id",
       NOW(),
       MD5(concat(j.id, j."urakka-id", j."toimenkuva-id", j.tunnit, j.tuntipalkka,
                  j.luotu, j.luoja, j.muokattu, j.muokkaaja, j.vuosi, j.kuukausi,
                  j."ennen-urakkaa", j."osa-kuukaudesta")::TEXT)
FROM johto_ja_hallintokorvaus j
WHERE (SELECT (date_trunc('MONTH', format('%s-%s-%s', j.vuosi, j.kuukausi, 1)::DATE))) <
      date_trunc('month', current_date);