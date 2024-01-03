-- Turi-järjestelmä on poistunut käytöstä. Harjasta ei lähetetä enää Turiin turvallisuuspoikkeamia eikä urakan työtunteja.
-- Jatkossa analytiikkaportaali hakee turvallisuuspoikkeamat Harjasta, urakan työtunteja ei välitetä Harjasta eteenpäin.
-- Siivotaan ensin lokia, jotta intergaation tietojen poistaminen onnistuu.
DELETE FROM integraatioviesti WHERE integraatiotapahtuma IN (select id from integraatiotapahtuma where integraatio in (select id from integraatio where jarjestelma = 'turi'));
DELETE FROM integraatiotapahtuma where integraatio in (select id from integraatio where jarjestelma = 'turi');
DELETE FROM integraatio WHERE jarjestelma = 'turi';

