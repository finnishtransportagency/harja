-- Turi-järjestelmä on poistunut käytöstä. Harjasta ei lähetetä enää Turiin turvallisuuspoikkeamia eikä urakan työtunteja.
-- Jatkossa analytiikkaportaali hakee turvallisuuspoikkeamat Harjasta, urakan työtunteja ei välitetä Harjasta eteenpäin.
DELETE FROM integraatio WHERE jarjestelma = 'turi';
