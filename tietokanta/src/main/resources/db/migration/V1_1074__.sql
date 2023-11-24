-- Liite tauluun tieto, jos liite on tallennettu aws s3
ALTER TABLE liite
    ADD COLUMN s3hash TEXT DEFAULT NULL,
    ADD COLUMN "virustarkastettu?" BOOLEAN DEFAULT true;
