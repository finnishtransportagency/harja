-- datakorjaus bugin takia
UPDATE toteuma
SET tyyppi = 'akillinen-hoitotyo'
WHERE tyyppi = 'kokonaishintainen'
  AND id IN
      (select tt.toteuma
       from toteuma_tehtava tt
       join toimenpidekoodi t on tt.toimenpidekoodi = t.id
            and t.nimi in ('Äkillinen hoitotyö (talvihoito)',
                           'Äkillinen hoitotyö (soratiet)',
                           'Äkillinen hoitotyö (l.ymp.hoito)'));

UPDATE toteuma
SET tyyppi = 'vahinkojen-korjaukset'
WHERE tyyppi = 'kokonaishintainen'
  AND id IN
      (select tt.toteuma
       from toteuma_tehtava tt
       join toimenpidekoodi t on tt.toimenpidekoodi = t.id
            and t.nimi in ('Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (talvihoito)',
                           'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (soratiet)',
                           'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (l.ymp.hoito)'));