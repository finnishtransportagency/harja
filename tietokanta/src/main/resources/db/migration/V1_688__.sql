UPDATE vatu_turvalaite SET luoja = NULL WHERE luoja = 'Integraatio';
ALTER TABLE vatu_turvalaite ALTER COLUMN luoja TYPE integer USING luoja::integer;
UPDATE vatu_turvalaite SET muokkaaja = NULL WHERE muokkaaja = 'Integraatio';
ALTER TABLE vatu_turvalaite ALTER COLUMN muokkaaja TYPE integer USING muokkaaja::integer;
