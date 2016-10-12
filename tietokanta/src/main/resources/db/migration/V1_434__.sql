-- Nimeä 'Pohjois-Pohjanmaa ja Kainuu' --> 'Pohjois-Pohjanmaa'
UPDATE organisaatio SET nimi = 'Pohjois-Pohjanmaa'
WHERE id = (SELECT id FROM organisaatio WHERE nimi = 'Pohjois-Pohjanmaa ja Kainuu');
