-- Vaadi ei-nollakokoiset liitteet
ALTER TABLE liite ADD CONSTRAINT validi_koko CHECK (koko > 0);
