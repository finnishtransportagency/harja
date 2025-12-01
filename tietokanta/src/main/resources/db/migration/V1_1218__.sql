-- Kumpaankaan kolumniin ei voida lisätä defaulttina mitään, koska vanhoilla riveillä ei ole arvoa.
ALTER TABLE toimenpideinstanssi
    ADD COLUMN luotu    TIMESTAMP,
    ADD COLUMN muokattu TIMESTAMP;
