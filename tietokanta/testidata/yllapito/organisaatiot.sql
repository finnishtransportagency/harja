-- Päivitetään oikea nimi ja puuttuva lyhenne Pohjois-Pohjanmaalle
UPDATE organisaatio
SET lyhenne = 'POP', nimi = 'Pohjois-Pohjanmaa X'
WHERE id = 9;
