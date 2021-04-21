-- tierekisteriosoitteen alkuetäisyys voi olla nolla, ja sallitaan varalta toiseenkin
-- suuntaan kirjaus eli losakin voi olla nolla
ALTER TABLE pot2_alusta DROP CONSTRAINT pot2_alusta_tr_alkuetaisyys_check;
ALTER TABLE pot2_alusta ADD CONSTRAINT pot2_alusta_tr_alkuetaisyys_validi CHECK (tr_alkuetaisyys >= 0);
ALTER TABLE pot2_alusta DROP CONSTRAINT pot2_alusta_tr_loppuetaisyys_check;
ALTER TABLE pot2_alusta ADD CONSTRAINT pot2_alusta_tr_loppuetaisyys_validi CHECK (tr_loppuetaisyys >= 0);