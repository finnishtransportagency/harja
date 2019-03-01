UPDATE toimenpidekoodi
SET  piilota = true,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE
  koodi in (
    '23121',
    '23115',
    '23111',
    '23103',
    '23101',
    '20182',
    '20178',
    '141211',
    '11111',
    '11110',
    '11105',
    '11100');

-- Piilota vanhat tehtävät (taso 4), joihin on liittyneenä tpi päättyneessä urakassa
UPDATE toimenpidekoodi
SET piilota = true,
muokattu = current_timestamp,
muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE
emo in (
(select id from toimenpidekoodi where koodi in (
'23121',
'23115',
'23111',
'23103',
'23101',
'20182',
'20178',
'141211',
'11111',
'11105') and taso = 3));
