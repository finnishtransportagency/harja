-- asetetaan virheelliset arvot nollaksi ja lisätään constraint
update urakka_tehtavamaara set maara = 0 where maara = 'NaN';
alter table urakka_tehtavamaara add constraint urakka_tehtavamaara_ei_nan check (maara != 'NaN');
