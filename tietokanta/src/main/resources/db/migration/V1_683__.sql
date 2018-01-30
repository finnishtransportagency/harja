-- Keskeneräinen ominaisuus jota ei otettu käyttöön. Jos joskus
-- jatketaan kehitystä, laitetaan repeatable-migraatioon.

DROP FUNCTION urakkastats_tarkastus() CASCADE;
DROP TRIGGER tg_urakkastats_ilmoitustoimenpide ON ilmoitustoimenpide;
DROP FUNCTION urakkastats_ilmoitustoimenpide() CASCADE;
DROP TRIGGER tg_urakkastats_ilmoitus ON ilmoitus;
DROP FUNCTION urakkastats_ilmoitus() CASCADE;
DROP TRIGGER tg_urakkastats_toteuma ON toteuma;
DROP FUNCTION urakkastats_toteuma() CASCADE;
DROP TRIGGER tg_urakkastats_tyokonehavainto ON tyokonehavainto;
DROP FUNCTION urakkastats_tyokonehavainto() CASCADE;
DROP TABLE urakkastats CASCADE;
