-- Luovutaan yksikköhintojen seuraamisesta paikkauksessa
ALTER TABLE paikkaustoteuma DROP COLUMN yksikkohinta;
ALTER TABLE paikkaustoteuma DROP COLUMN yksikko;
ALTER TABLE paikkaustoteuma DROP COLUMN maara;