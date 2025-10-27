ALTER TABLE analytiikka_toteumat ADD COLUMN luotu timestamp DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE analytiikka_toteumat ADD COLUMN palautettu_analytiikalle TIMESTAMP;
