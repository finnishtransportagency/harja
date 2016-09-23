-- Poistetaan muut kuin nämä roolit
SELECT *  FROM yhteyshenkilo_urakka
WHERE rooli != 'Kunnossapitopäällikkö'
      OR rooli != 'Sillanvalvoja'
      OR rooli != 'Kelikeskus'
      OR rooli != 'Tieliikennekeskus';

-- Poistetaan yhteyshenkilöt joilla ei ole enää muita rooleja, eikä päivystystä
DELETE FROM yhteyshenkilo
WHERE yhteyshenkilo.id in (SELECT yhteyshenkilo.id FROM yhteyshenkilo
  LEFT JOIN yhteyshenkilo_urakka ON yhteyshenkilo.id = yhteyshenkilo_urakka.yhteyshenkilo
  LEFT JOIN paivystys ON yhteyshenkilo.id = paivystys.yhteyshenkilo
WHERE yhteyshenkilo_urakka.id IS NULL AND paivystys.id IS NULL);