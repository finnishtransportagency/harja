
-------------------------------------------------------------------------------------------
-- Viite urakoista hankkeeseen															 --
-------------------------------------------------------------------------------------------

ALTER TABLE urakka ADD hanke INT NULL;
ALTER TABLE urakka ADD CONSTRAINT urakka_hanke_fkey 
FOREIGN KEY (hanke) REFERENCES hanke(id);
