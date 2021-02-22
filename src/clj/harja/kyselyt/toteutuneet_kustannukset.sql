-- name: hae-siirtamattomat-kustannukset
-- single?: true
SELECT SUM(m.maara) as maara FROM (
SELECT count(*) as maara
  FROM kustannusarvioitu_tyo kt
 WHERE kt."siirretty?" = false
   AND (kt.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c') -- Erillishankinnat (W)
     OR kt.tehtava in (SELECT id
                      FROM toimenpidekoodi t
                      WHERE t.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' -- "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."
                         OR t.yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744') -- Hoitourakan työnjohto
     )
   -- Verrataan tyyliin: 2020-02-01 00:00:00.000000 < 2021-02-01 00:00:00.000000
   AND (SELECT (date_trunc('MONTH', format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE))) < date_trunc('month', current_date)
 UNION ALL
SELECT count(*) as maara
  FROM johto_ja_hallintokorvaus jjh
WHERE jjh."siirretty?" = false
 AND (SELECT (date_trunc('MONTH', format('%s-%s-%s', jjh.vuosi, jjh.kuukausi, 1)::DATE))) <
      date_trunc('month', current_date)) AS m;

-- name: siirra-budjetoidut-tyot-toteutumiin
select siirra_budjetoidut_tyot_toteumiin();
