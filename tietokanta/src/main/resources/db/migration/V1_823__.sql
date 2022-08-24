UPDATE johto_ja_hallintokorvaus jhk
SET "ennen-urakkaa" = FALSE
FROM urakka u
WHERE u.id = jhk."urakka-id" AND
      u.tyyppi = 'teiden-hoito'::urakkatyyppi AND
      "ennen-urakkaa" IS NULL;

ALTER TABLE johto_ja_hallintokorvaus
  ALTER COLUMN "ennen-urakkaa" SET NOT NULL,
  ALTER COLUMN "ennen-urakkaa" SET DEFAULT FALSE;