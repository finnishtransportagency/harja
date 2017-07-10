CREATE TYPE toimenpidehaun_vika AS (id INTEGER, tila TEXT);
ALTER TABLE reimari_toimenpide ADD COLUMN "reimari-viat" toimenpidehaun_vika[]
  NOT NULL
  DEFAULT '{}';
