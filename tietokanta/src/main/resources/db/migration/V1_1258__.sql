-- Lisätään sanktiolle uusi määrätty päivä
ALTER TABLE sanktio
    ADD COLUMN maarattypvm DATE;

-- Lisätään maarattypvm kaikille olemassa oleville sanktioille laadunseuranta.kasittelyaika kolumnin perusteella
UPDATE sanktio s
SET maarattypvm = DATE(lp.kasittelyaika)
FROM laatupoikkeama lp
WHERE lp.id = s.laatupoikkeama
  AND s.maarattypvm IS NULL;
