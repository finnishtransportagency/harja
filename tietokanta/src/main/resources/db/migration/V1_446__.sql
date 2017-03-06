-- Aikataulun apin pilkkomisen integraatiomuutokset
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'kirjaa-tiemerkinnan-aikataulu');

UPDATE integraatio SET nimi = 'kirjaa-paallystyksen-aikataulu'
WHERE nimi = 'kirjaa-yllapidon-aikataulu';