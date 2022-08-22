ALTER TABLE tyokonehavainto
ADD COLUMN tyokonetunnus TEXT;

COMMENT ON COLUMN tyokonehavainto.tyokonetunnus IS 'Työkoneen yksilöivä, selkokielinen tunniste.';
COMMENT ON COLUMN tyokonehavainto.tyokoneid IS 'Työkoneen yksilöivä numeerinen id.';