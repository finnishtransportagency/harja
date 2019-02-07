-- Salli sijainniksi pisteen lisäksi viiva
ALTER TABLE tyokonehavainto
    ALTER COLUMN sijainti TYPE GEOMETRY USING sijainti::GEOMETRY;
