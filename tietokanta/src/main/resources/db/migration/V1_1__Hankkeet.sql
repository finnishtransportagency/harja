
-------------------------------------------------------------------------------------------
-- Hankkeet: Samposta tulevien hankkeiden perustiedot									 --
-------------------------------------------------------------------------------------------

CREATE TABLE hanke (
  id serial primary key,
  nimi varchar(128),                      							  -- hankkeen nimi (esim. 'Ivalo alueurakka 2009-2014, H')
  alkupvm date,                           							  -- alkamispäivä
  loppupvm date,                          							  -- loppumispäivä
  alueurakkanro varchar(16),                    					  -- alueurakkanumero
  sampoid varchar(16)                    							  -- sampoid
);