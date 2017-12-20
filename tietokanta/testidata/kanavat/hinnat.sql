insert into kan_hinta (otsikko, yksikko, yksikkohinta, ryhma, maara, luoja) values ('Henkilöstö: työnjohto', 'h', 55, 'yksikkohinnoiteltu-tyo', 3, 42);
insert into kan_hinta (otsikko, yksikko, yksikkohinta, ryhma, maara, luoja) values ('Ylimääräinen työ', 'h', 35, 'muu-tyo', 3, 42);
insert into kan_hinta (otsikko, yksikko, yksikkohinta, yleiskustannuslisa, ryhma, maara, luoja) values ('SAFF SUN 10-5', 'kpl', 100, 14, 'varaosat-ja-materiaalit', 1, 42);
insert into kan_hinta (summa, ryhma, yleiskustannuslisa, luoja) values (101, 'yleiset-materiaalit', 8, 42);
insert into kan_hinta (summa, ryhma, luoja) values (44.25, 'matkakulut', 42);
insert into kan_hinta (otsikko, summa, ryhma, yleiskustannuslisa, luoja) values ('Muita sekalaisia hankintoja', 44.25, 'muut-kulut', 3.1, 42);

insert into kan_toimenpide_hinta values ((select max(ktp.id) from kan_toimenpide ktp, sopimus s where kohde = 3 and tyyppi = 'muutos-lisatyo' and sopimus = s.id and s.nimi = 'Saimaan huollon pääsopimus'), (select max(id) from kan_hinta));
