ALTER TABLE sopimuksen_tehtavamaarat_tallennettu ADD UNIQUE(urakka);
alter table sopimus_tehtavamaara add column hoitovuosi integer;
alter table sopimus_tehtavamaara drop constraint sopimus_tehtavamaara_urakka_tehtava_key;
alter table sopimus_tehtavamaara add unique (urakka, tehtava, hoitovuosi);
