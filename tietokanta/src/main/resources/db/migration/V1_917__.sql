create index tarkastus_ennen_2015_yllapitokohde_poistettu_index
    on tarkastus_ennen_2015 (yllapitokohde, poistettu);

create index tarkastus_ennen_2015_yllapitokohde_envelope_index
    on tarkastus_ennen_2015 (envelope);


CREATE OR REPLACE FUNCTION luo_tarkastustaulun_indeksit(vuosi integer)
    RETURNS VOID AS
$$
DECLARE
    partitio text;
    quarter integer;
BEGIN

FOR quarter IN 1..4 LOOP

        partitio :=  'tarkastus_' || vuosi || '_' || 'q' || quarter ;

        raise notice 'Partitio %', partitio;

        -- OTHER INDEXES
        EXECUTE 'CREATE INDEX ' || partitio || '_yllapitokohde_poistettu_idx ON ' || partitio || '(yllapitokohde, poistettu)';
        EXECUTE 'CREATE INDEX ' || partitio || '_envelope_idx ON ' || partitio || '(envelope)';
end loop;

END
$$
LANGUAGE plpgsql;

select luo_tarkastustaulun_indeksit(2015);
select luo_tarkastustaulun_indeksit(2016);
select luo_tarkastustaulun_indeksit(2017);
select luo_tarkastustaulun_indeksit(2018);
select luo_tarkastustaulun_indeksit(2019);
select luo_tarkastustaulun_indeksit(2020);
select luo_tarkastustaulun_indeksit(2021);
select luo_tarkastustaulun_indeksit(2022);
select luo_tarkastustaulun_indeksit(2023);
select luo_tarkastustaulun_indeksit(2024);
select luo_tarkastustaulun_indeksit(2025);
select luo_tarkastustaulun_indeksit(2026);
