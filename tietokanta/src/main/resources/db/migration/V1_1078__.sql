-- Lisää täppä urakoitsijan merkinnöille
ALTER TABLE tyomaapaivakirja_kommentti ADD COLUMN IF NOT EXISTS urakoitsijan_merkinta boolean DEFAULT false;
