-- Ylimääräisten toimenpidekoodien merkitseminen poistetuksi

-- VAIHE 1. Merkitse poistetuksi ja piilota hierarkiapuut, joissa ei ole lainkaan kirjauksia eikä tasoon 3 liittyvää toimenpideinstanssia

-- Taso 4
UPDATE toimenpidekoodi SET
  poistettu = true,
  piilota = true,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
where id IN (
  (select tpk4.id from toimenpidekoodi tpk4
    join toimenpidekoodi tpk3 on tpk4.emo = tpk3.id
    join toimenpidekoodi tpk2 on tpk3.emo = tpk2.id
    join toimenpidekoodi tpk1 on tpk2.emo = tpk1.id
  where tpk1.koodi in (
'13000',
'10000',
'29000',
'25000',
'22000',
'19000',
'16000',
'12000',
'15000',
'18000',
'21000',
'24000',
'31000',
'17000',
'26000',
'28000',
'30000',
'32000',
'33000',
'34000',
'35000',
'36000',
'37000',
'38000',
'39000',
'SAMPO_1')));

-- Taso 3
UPDATE toimenpidekoodi SET
  poistettu = true,
  piilota = true,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
where id IN (
  (select tpk3.id from toimenpidekoodi tpk3
    join toimenpidekoodi tpk2 on tpk3.emo = tpk2.id
    join toimenpidekoodi tpk1 on tpk2.emo = tpk1.id
  where tpk1.koodi in (
'13000',
'10000',
'29000',
'25000',
'22000',
'19000',
'16000',
'12000',
'15000',
'18000',
'21000',
'24000',
'31000',
'17000',
'26000',
'28000',
'30000',
'32000',
'33000',
'34000',
'35000',
'36000',
'37000',
'38000',
'39000',
'SAMPO_1')));

-- Taso 2
UPDATE toimenpidekoodi SET
  poistettu = true,
  piilota = true,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
where id IN (
  (select tpk2.id from toimenpidekoodi tpk2
    join toimenpidekoodi tpk1 on tpk2.emo = tpk1.id
  where tpk1.koodi in (
'13000',
'10000',
'29000',
'25000',
'22000',
'19000',
'16000',
'12000',
'15000',
'18000',
'21000',
'24000',
'31000',
'17000',
'26000',
'28000',
'30000',
'32000',
'33000',
'34000',
'35000',
'36000',
'37000',
'38000',
'39000',
'SAMPO_1')));

-- Taso 1
UPDATE toimenpidekoodi SET
  poistettu = true,
  piilota = true,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
where koodi in (
'13000',
'10000',
'29000',
'25000',
'22000',
'19000',
'16000',
'12000',
'15000',
'18000',
'21000',
'24000',
'31000',
'17000',
'26000',
'28000',
'30000',
'32000',
'33000',
'34000',
'35000',
'36000',
'37000',
'38000',
'39000',
'SAMPO_1');




-- VAIHE 2. Merkitse poistetuksi ja piilota hierarkian haarat, joissa löytyy sälytettäviä oksia.
-- Piilotetaan ja poistetaan 2 tasolta alaspäin ne joissa ei ole kirjauksia eikä tasoon 3 liittyvää toimenpideinstanssia


-- Taso 4
UPDATE toimenpidekoodi SET
  poistettu = true,
  piilota = true,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
where id IN (
  (select tpk4.id from toimenpidekoodi tpk4
    join toimenpidekoodi tpk3 on tpk4.emo = tpk3.id
    join toimenpidekoodi tpk2 on tpk3.emo = tpk2.id
  where tpk2.koodi in (
  '11140',
'11150',
'11170',
'11180',
'11190',
'11200',
'11210',
'11230',
'11240',
'11250',
'11260',
'11270',
'14110',
'14120',
'14130',
'14140',
'14150',
'14160',
'14170',
'14180',
'14190',
'14200',
'14220',
'14230',
'14240',
'20150',
'20160',
'20210',
'20220',
'27110',
'27120',
'27130',
'27140',
'AURA2_240',
'AURA2_260',
'AURA2_210',
'AURA2_110',
'AURA2_270',
'AURA2_250',
'AURA2_120',
'AURA2_230',
'AURA2_130',
'AURA2_220',
'AURA2_140'
)));

-- Taso 3
UPDATE toimenpidekoodi SET
  poistettu = true,
  piilota = true,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
where id IN (
  (select tpk3.id from toimenpidekoodi tpk3
    join toimenpidekoodi tpk2 on tpk3.emo = tpk2.id
  where tpk2.koodi in (
  '11140',
'11150',
'11170',
'11180',
'11190',
'11200',
'11210',
'11230',
'11240',
'11250',
'11260',
'11270',
'14110',
'14120',
'14130',
'14140',
'14150',
'14160',
'14170',
'14180',
'14190',
'14200',
'14220',
'14230',
'14240',
'20150',
'20160',
'20210',
'20220',
'27110',
'27120',
'27130',
'27140',
'AURA2_240',
'AURA2_260',
'AURA2_210',
'AURA2_110',
'AURA2_270',
'AURA2_250',
'AURA2_120',
'AURA2_230',
'AURA2_130',
'AURA2_220',
'AURA2_140')));

-- Taso 2
UPDATE toimenpidekoodi SET
  poistettu = true,
  piilota = true,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
where koodi in (
'11140',
'11150',
'11170',
'11180',
'11190',
'11200',
'11210',
'11230',
'11240',
'11250',
'11260',
'11270',
'14110',
'14120',
'14130',
'14140',
'14150',
'14160',
'14170',
'14180',
'14190',
'14200',
'14220',
'14230',
'14240',
'20150',
'20160',
'20210',
'20220',
'27110',
'27120',
'27130',
'27140',
'AURA2_240',
'AURA2_260',
'AURA2_210',
'AURA2_110',
'AURA2_270',
'AURA2_250',
'AURA2_120',
'AURA2_230',
'AURA2_130',
'AURA2_220',
'AURA2_140');

-- Taso 1. Säilytetty.


-- VAIHE 3. Merkitse poistetuksi ja piilota hierarkian haarat, joissa löytyy sälytettäviä oksia.
-- Piilotetaan ja poistetaan 3 tasolta alaspäin ne joissa ei ole kirjauksia eikä tasoon 3 liittyvää toimenpideinstanssia

-- Taso 4
UPDATE toimenpidekoodi SET
  poistettu = true,
  piilota = true,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
where id IN (
  (select tpk4.id from toimenpidekoodi tpk4
    join toimenpidekoodi tpk3 on tpk4.emo = tpk3.id
  where tpk3.koodi in (
'11101',
'11102',
'11103',
'11104',
'11106',
'11107',
'11108',
'11109',
'11122',
'11123',
'11124',
'11125',
'11126',
'11127',
'11131',
'11132',
'11133',
'11134',
'11161',
'11162',
'11163',
'11164',
'11222',
'11223',
'11224',
'14101',
'14102',
'14103',
'14104',
'14106',
'14107',
'14108',
'141212',
'141214',
'141215',
'141216',
'20104',
'20105',
'20122',
'20131',
'20132',
'20133',
'20134',
'20141',
'20142',
'20171',
'20173',
'20174',
'20175',
'20176',
'20177',
'20181',
'20191',
'20192',
'20201',
'20202',
'20203',
'20204',
'20205',
'20206',
'20208',
'20209',
'23102',
'23112',
'23113',
'23122',
'23123',
'23131',
'23132',
'23133',
'23142',
'26101',
'26102',
'26103',
'26104',
'26105',
'27104',
'27103',
'27102',
'27101',
'AURA3_240',
'AURA3_260',
'AURA3_210',
'AURA3_140',
'AURA3_110',
'AURA3_270',
'AURA3_230',
'AURA3_130',
'AURA3_220',
'AURA3_120',
'AURA3_250'
)));

-- Taso 3
UPDATE toimenpidekoodi SET
  poistettu = true,
  piilota = true,
  muokattu = current_timestamp,
  muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
where koodi IN (
'11101',
'11102',
'11103',
'11104',
'11106',
'11107',
'11108',
'11109',
'11122',
'11123',
'11124',
'11125',
'11126',
'11127',
'11131',
'11132',
'11133',
'11134',
'11161',
'11162',
'11163',
'11164',
'11222',
'11223',
'11224',
'14101',
'14102',
'14103',
'14104',
'14106',
'14107',
'14108',
'141212',
'141214',
'141215',
'141216',
'20104',
'20105',
'20122',
'20131',
'20132',
'20133',
'20134',
'20141',
'20142',
'20171',
'20173',
'20174',
'20175',
'20176',
'20177',
'20181',
'20191',
'20192',
'20201',
'20202',
'20203',
'20204',
'20205',
'20206',
'20208',
'20209',
'23102',
'23112',
'23113',
'23122',
'23123',
'23131',
'23132',
'23133',
'23142',
'26101',
'26102',
'26103',
'26104',
'26105',
'27104',
'27103',
'27102',
'27101',
'AURA3_240',
'AURA3_260',
'AURA3_210',
'AURA3_140',
'AURA3_110',
'AURA3_270',
'AURA3_230',
'AURA3_130',
'AURA3_220',
'AURA3_120',
'AURA3_250');

-- Taso 2. Säilytetty.
-- Taso 1. Säilytetty.




-- Päivitä toimenpi
UPDATE toimenpidekoodi SET nimi = 'Talvihoito laaja TPI', tuotenumero = 203, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '23104' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Liikenneympäristön hoito laaja TPI', tuotenumero = 203, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '23116' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Soratien hoito laaja TPI', tuotenumero = 203, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '23124' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Päällystepaikkaukset laaja TPI', tuotenumero = 205, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '20107' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Päällystetyn tien rakenne laaja TPI', tuotenumero = 205, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '20112' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Soratien rakenne laaja TPI', tuotenumero = 205, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '20143' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Varuste ja laite laaja TPI', tuotenumero = 205, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '20179' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Päällyste laaja TPI', tuotenumero = 205, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '20106' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Tiesilta laaja TPI', tuotenumero = 205, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '20135' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Liikenneympäristön parantaminen laaja TPI', tuotenumero = 205, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '20183' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Tieväylä laaja TPI', tuotenumero = 205, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '14109' and taso = 3;
UPDATE toimenpidekoodi SET nimi = 'Varuste ja laite laaja TPI', tuotenumero = 205, muokattu = CURRENT_TIMESTAMP, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE koodi = '141217' and taso = 3;