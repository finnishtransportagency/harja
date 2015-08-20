
-------------------------------------------------------------------------------------------
-- Urakan toimenpiteet																	 --
-------------------------------------------------------------------------------------------

CREATE TABLE urakka_toimenpide (
  id serial primary key,
  urakka integer references urakka (id),
  toimenpide integer references toimenpidekoodi (id)
);

