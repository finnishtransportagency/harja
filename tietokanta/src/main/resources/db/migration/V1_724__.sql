-- Uniikki toimenpidekoodi-vaatimus ei enää koske päällystysurakoita
ALTER TABLE toimenpideinstanssi
  DROP CONSTRAINT IF EXISTS uniikki_urakka_toimenpidekoodi;