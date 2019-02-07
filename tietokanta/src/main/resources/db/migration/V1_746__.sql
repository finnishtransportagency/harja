CREATE TYPE tyokone AS ENUM ('Henkilöauto', 'Jyrä', 'Kaivinkone', 'Kuorma-auto', 'Levittäjä', 'Pakettiauto', 'Pyörökuormaaja', 'Tiehöylä', 'Traktori');
ALTER TABLE toteuma ADD COLUMN tyokonetyyppi tyokone;
ALTER TABLE toteuma ADD COLUMN tyokonetunniste TEXT;