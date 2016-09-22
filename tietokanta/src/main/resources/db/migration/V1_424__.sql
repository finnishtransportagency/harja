ALTER TABLE valaistusurakka ADD COLUMN valaistusurakkanro VARCHAR(16);
ALTER TABLE paallystyspalvelusopimus ADD COLUMN paallystyspalvelusopimusnro VARCHAR(16);
ALTER TABLE urakka RENAME alueurakkanro TO urakkanro