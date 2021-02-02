-- Korjataan lasku_kohdistus tauluun Äkilliset hoitotyöt tehtäväryhmän maksuerätyyppi kokonaishintaisesta akillinen-hoitotyo
UPDATE lasku_kohdistus SET maksueratyyppi = 'akillinen-hoitotyo'::MAKSUERATYYPPI
 WHERE tehtavaryhma IN (SELECT id FROM tehtavaryhma WHERE nimi like '%Äkilliset hoitotyöt,%')
   AND maksueratyyppi = 'kokonaishintainen';

-- Korjataan lasku_kohdistus tauluun Vahinkojen korjaukset tehtäväryhmän maksuerätyyppi kokonaishintaisesta muu
UPDATE lasku_kohdistus set maksueratyyppi = 'muu'::MAKSUERATYYPPI
 WHERE tehtavaryhma IN (SELECT id FROM tehtavaryhma WHERE nimi like '%Vahinkojen korjaukset%')
   AND maksueratyyppi = 'kokonaishintainen';
