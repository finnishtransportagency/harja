
-- Lisätään organisaatioon ELY numero, jos kyseessä on ELY-keskus
-- numeroa käytetään linkkaamaan hankkeen alueurakka hallintayksikköön
ALTER TABLE organisaatio ADD COLUMN elynumero smallint;

ALTER TABLE alueurakka ADD COLUMN elynumero smallint;
