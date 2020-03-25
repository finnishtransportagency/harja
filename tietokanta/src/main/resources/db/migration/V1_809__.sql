-- Luovutaan yksikköhintojen seuraamisesta paikkauksessa
ALTER TABLE paikkaustoteuma DROP COLUMN yksikkohinta;
ALTER TABLE paikkaustoteuma DROP COLUMN yksikko;
ALTER TABLE paikkaustoteuma DROP COLUMN maara;

-- Käyttäjät eivät halua kirjata jokaiselle paikkaukselle erikseen kustannusta,
-- vaan ennemmin yksi per paikkauskohde per työmenetelmä.
ALTER TABLE paikkaustoteuma ADD COLUMN tyomenetelma TEXT;
ALTER TABLE paikkaustoteuma ADD COLUMN valmistumispvm DATE;
-- Käyttäjät haluavat hakea kustannuksia TR-osoitteen perusteella -> uusi sarake
ALTER TABLE paikkaustoteuma ADD COLUMN tierekisteriosoite tr_osoite;
-- Käsin kirjattaessa ulkoista id:tä ei saada
ALTER TABLE paikkaustoteuma ALTER COLUMN "ulkoinen-id" DROP NOT NULL;