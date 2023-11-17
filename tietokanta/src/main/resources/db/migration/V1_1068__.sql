-- Lisätään valaistusurakoihin liittyvät suoritettavat tehtävät
ALTER TYPE suoritettavatehtava ADD VALUE 'ryhmavaihto' AFTER 'roskien keruu';
ALTER TYPE suoritettavatehtava ADD VALUE 'huoltokierros' AFTER 'harjaus';
ALTER TYPE suoritettavatehtava ADD VALUE 'muut valaistusurakan toimenpiteet' AFTER 'muu';
