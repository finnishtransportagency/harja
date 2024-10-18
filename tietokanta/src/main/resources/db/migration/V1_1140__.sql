-- Laita kylmä- ja kuumapaikkausten toteumat näkymään MH-urakoiden kesätöiden Päällysteiden paikkaukset-tehtävään
UPDATE tehtava
SET suoritettavatehtava = 'paallysteiden paikkaus'
WHERE nimi IN ('Päällysteiden paikkaus - kuumapäällyste', 'Päällysteiden paikkaus, kylmäpäällyste');
