-- Lisää maksuera_alias sarake maksuera tauluun
ALTER TABLE maksuera
ADD COLUMN maksuera_alias varchar(128);
