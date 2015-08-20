ALTER TABLE sanktio ADD COLUMN tyyppi integer REFERENCES sanktiotyyppi (id);
