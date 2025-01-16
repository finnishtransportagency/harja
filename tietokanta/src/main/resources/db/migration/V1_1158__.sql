-- Lisätään talvihoitoreiteille poistettu sarake
ALTER TABLE talvihoitoreitti ADD COLUMN poistettu BOOLEAN DEFAULT FALSE;
