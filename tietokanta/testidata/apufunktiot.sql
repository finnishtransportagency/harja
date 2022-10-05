-- Testidatan generointia varten halutaan helppo tapa indeksikorjata MHU:iden kuluja.
CREATE OR REPLACE FUNCTION testidata_indeksikorjaa(korjattava_arvo NUMERIC, vuosi_ INTEGER, kuukausi_ INTEGER,
                                                   urakka_id INTEGER)
    RETURNS NUMERIC AS
$$
DECLARE
    -- Perusluku on urakalle sama riippumatta kuluvasta hoitokaudesta
    perusluku      NUMERIC := indeksilaskennan_perusluku(urakka_id);
    indeksin_nimi  TEXT    := (SELECT indeksi
                               FROM urakka u
                               WHERE u.id = urakka_id);
    -- Indeksikerroin pyöristetään kolmeen desimaaliin.
    indeksikerroin NUMERIC;
BEGIN
    -- Indeksikerroin on hoitokausikohtainen, katsotaan aina edellisen hoitokauden syyskuun indeksiä.
    IF kuukausi_ BETWEEN 1 AND 9 THEN
        indeksikerroin := (SELECT round((arvo / perusluku), 3)
                           FROM indeksi i
                           WHERE i.vuosi = vuosi_ - 1
                             AND i.kuukausi = 9
                             AND nimi = indeksin_nimi);
    ELSE
        indeksikerroin := (SELECT round((arvo / perusluku), 3)
                           FROM indeksi i
                           WHERE i.vuosi = vuosi_
                             AND i.kuukausi = 9
                             AND nimi = indeksin_nimi);
    END IF;
    -- Ja tallennettava arvo kuuteen.
    return round(korjattava_arvo * indeksikerroin, 6);
END ;
$$ language plpgsql;

create or replace function luo_testitarjousmaarat_tehtavalle(urakka_id integer, tpk integer, maara integer, urakan_alkuvuosi integer, urakan_loppuvuosi integer) 
returns boolean as 
$$
declare 
	tpk_rivi record;
	urakka_rivi record;
begin
	for v in urakan_alkuvuosi..urakan_loppuvuosi loop 
		raise notice 'vuosi :: %', v;
		insert into sopimus_tehtavamaara(urakka, tehtava, maara, muokattu, hoitovuosi)
		values (urakka_id, tpk, maara, now(), v) on conflict do nothing;
	end loop ;
	return true;

end
$$ language plpgsql;

create or replace function luo_kaikille_tehtaville_testitarjousmaarat(urakka_nimi varchar, maara integer) returns boolean as 
$$
declare 
	tpk_rivi record;
	urakan_loppuvuosi integer;
	urakan_alkuvuosi integer;
    urakka_rivi record;
begin
	select * into urakka_rivi from urakka ur where ur.nimi = urakka_nimi;
	select extract(year from urakka_rivi.alkupvm) into urakan_alkuvuosi;
	select extract(year from urakka_rivi.loppupvm) into urakan_loppuvuosi;

	for tpk_rivi in select tpk.id from toimenpidekoodi tpk join tehtavaryhma tr on tr.id = tpk.tehtavaryhma and tpk.yksikko is not null and tpk.poistettu is not null and tpk.aluetieto = true loop 
	raise notice 'aluetiedot tpk id :: %', tpk_rivi.id;
		insert into sopimus_tehtavamaara(urakka, tehtava, maara, muokattu, hoitovuosi) values (urakka_rivi.id, tpk_rivi.id, maara, now(), urakan_alkuvuosi) on conflict do nothing;
	end loop ;

for tpk_rivi in select tpk.id from toimenpidekoodi tpk join tehtavaryhma tr on tr.id = tpk.tehtavaryhma and tpk.yksikko is not null and tpk.poistettu is not null and tpk.aluetieto = false loop 
	raise notice 'maaratiedot tpk id :: %', tpk_rivi.id;
		perform luo_testitarjousmaarat_tehtavalle(urakka_rivi.id, tpk_rivi.id, maara, urakan_alkuvuosi, urakan_loppuvuosi - 1);
	end loop ;
	return true;
end
$$ language plpgsql;

CREATE OR REPLACE FUNCTION kuukauden_nimi(kuukausi INT) RETURNS TEXT AS
$$
BEGIN
    RETURN
        CASE kuukausi
               WHEN 1 THEN 'tammikuu'
               WHEN 2 THEN 'helmikuu'
               WHEN 3 THEN 'maaliskuu'
               WHEN 4 THEN 'huhtikuu'
               WHEN 5 THEN 'toukokuu'
               WHEN 6 THEN 'kesakuu'
               WHEN 7 THEN 'heinakuu'
               WHEN 8 THEN 'elokuu'
               WHEN 9 THEN 'syyskuu'
               WHEN 10 THEN 'lokakuu'
               WHEN 11 THEN 'marraskuu'
               WHEN 12 THEN 'joulukuu'
        END;
    END;
$$ LANGUAGE plpgsql;
