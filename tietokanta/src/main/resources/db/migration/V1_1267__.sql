-- Lisätään sanktio-tauluun maarattypvm-kenttä
ALTER TABLE sanktio
    ADD COLUMN IF NOT EXISTS "maarattypvm" date;

-- Uusien muutosten myötä maarattypvm on pakollinen kenttä, joten lisätään kaikille sanktioille maarattypvm
UPDATE sanktio s
SET maarattypvm = lp.kasittelyaika
FROM laatupoikkeama lp
WHERE lp.id = s.laatupoikkeama
  AND s.maarattypvm IS NULL;


