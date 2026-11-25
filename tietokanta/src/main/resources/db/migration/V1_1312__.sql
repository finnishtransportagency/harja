-- Lisätään vhu urakoiden vuoksi kulu_kohdistus tauluun uudet kolumnit
ALTER TABLE kulu_kohdistus
    ADD COLUMN maara NUMERIC DEFAULT NULL,
    ADD COLUMN yksikkohinta DECIMAL(10,2) DEFAULT NULL;
