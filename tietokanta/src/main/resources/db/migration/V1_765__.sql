ALTER TABLE siltatarkastuskohde
  ALTER COLUMN tulos TYPE char[]
  USING array[tulos]::char[];
