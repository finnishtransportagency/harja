-- Ensimmäinen ilmoitus: Oulun alueella, kysely
INSERT INTO ilmoitus
(urakka, ilmoitusid, ilmoitettu, valitetty, yhteydenottopyynto, lyhytselite, sijainti,
 tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, ilmoitustyyppi, selitteet, urakkatyyppi,
 ilmoittaja_etunimi, ilmoittaja_sukunimi, ilmoittaja_tyopuhelin, ilmoittaja_matkapuhelin, ilmoittaja_sahkoposti, ilmoittaja_tyyppi,
 lahettaja_etunimi, lahettaja_sukunimi, lahettaja_puhelinnumero, lahettaja_sahkoposti)
VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), 12345, '2005-10-01 10:00.00', '2005-10-01 10:05.13', true, 'Voisko joku soittaa?',
                                                                         ST_MakePoint(452935, 7186873)::GEOMETRY, 6, 6, 6, 6, 6, 'kysely'::ilmoitustyyppi, ARRAY['saveaTiella', 'vettaTiella']::ilmoituksenselite[],
                                                                                                                              (SELECT tyyppi FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'),
                                                                                                                              'Seppo', 'Savela', '0441231234', '0441231234', 'seppo.savela@eiole.fi', 'asukas'::ilmoittajatyyppi,
        'Mari', 'Marttala', '085674567', 'mmarttala@isoveli.com');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12345), 12345, '2005-10-01 10:07.03', 'vastaanotto'::kuittaustyyppi,
                                                          'Mikael', 'Pöytä', '04428671283', '0509288383', 'mikael.poyta@valittavaurakoitsija.fi',
                                                          'Välittävä Urakoitsija', 'Y1234');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, vapaateksti, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus,
 kasittelija_henkilo_etunimi, kasittelija_henkilo_sukunimi, kasittelija_henkilo_matkapuhelin, kasittelija_henkilo_tyopuhelin, kasittelija_henkilo_sahkoposti,
 kasittelija_organisaatio_nimi, kasittelija_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12345), 12345, '2005-10-01 10:34.50', 'Soitan kunhan kerkeän', 'vastaus'::kuittaustyyppi,
                                                          'Usko', 'Untamo', '04428121283', '0509288383', 'usko.untamo@valittavaurakoitsija.fi',
                                                          'Välittävä Urakoitsija', 'Y1234',
        'Usko', 'Untamo', '04428121283', '0509288383', 'usko.untamo@valittavaurakoitsija.fi',
        'Välittävä Urakoitsija', 'Y1234');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, vapaateksti, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus,
 kasittelija_henkilo_etunimi, kasittelija_henkilo_sukunimi, kasittelija_henkilo_matkapuhelin, kasittelija_henkilo_tyopuhelin, kasittelija_henkilo_sahkoposti,
 kasittelija_organisaatio_nimi, kasittelija_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12345), 12345, '2005-10-02 11:28.50', 'Soitan lounaan jälkeen!', 'aloitus'::kuittaustyyppi,
                                                          'Usko', 'Untamo', '04428121283', '0509288383', 'usko.untamo@valittavaurakoitsija.fi',
                                                          'Välittävä Urakoitsija', 'Y1234',
        'Usko', 'Untamo', '04428121283', '0509288383', 'usko.untamo@valittavaurakoitsija.fi',
        'Välittävä Urakoitsija', 'Y1234');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, vapaateksti, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus,
 kasittelija_henkilo_etunimi, kasittelija_henkilo_sukunimi, kasittelija_henkilo_matkapuhelin, kasittelija_henkilo_tyopuhelin, kasittelija_henkilo_sahkoposti,
 kasittelija_organisaatio_nimi, kasittelija_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12345), 12345, '2005-10-02 12:08.02',
                                                          'Homma on hoidettu. Ei siellä oikeastaan mitään tähdellistä asiaa ollutkaan..', 'lopetus'::kuittaustyyppi,
                                                          'Usko', 'Untamo', '04428121283', '0509288383', 'usko.untamo@valittavaurakoitsija.fi',
                                                          'Välittävä Urakoitsija', 'Y1234',
        'Usko', 'Untamo', '04428121283', '0509288383', 'usko.untamo@valittavaurakoitsija.fi',
        'Välittävä Urakoitsija', 'Y1234');

-- Toinen ilmoitus: Oulun alueella, toimenpidepyynto
INSERT INTO ilmoitus
(urakka, ilmoitusid, ilmoitettu, valitetty, yhteydenottopyynto, lyhytselite, sijainti,
 tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, ilmoitustyyppi, selitteet, urakkatyyppi,
 ilmoittaja_etunimi, ilmoittaja_sukunimi, ilmoittaja_tyopuhelin, ilmoittaja_matkapuhelin, ilmoittaja_sahkoposti, ilmoittaja_tyyppi,
 lahettaja_etunimi, lahettaja_sukunimi, lahettaja_puhelinnumero, lahettaja_sahkoposti)
VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'), 12346, '2005-10-10 06:05.32', '2005-10-11 06:06.37', true, 'Taas täällä joku mättää!',
                                                                         ST_MakePoint(435847, 7216217)::GEOMETRY, 6, 6, 6, 6, 6, 'toimenpidepyynto'::ilmoitustyyppi,
                                                                                                                              ARRAY['kaivonKansiRikki', 'vettaTiella']::ilmoituksenselite[],
                                                                                                                              (SELECT tyyppi FROM urakka WHERE nimi='Oulun alueurakka 2005-2010'),
                                                                                                                              'Yrjö', 'Mestari', '0441271234', '0441233424', 'tyonvalvonta@isoveli.com', 'muu'::ilmoittajatyyppi,
        'Mari', 'Marttala', '085674567', 'mmarttala@isoveli.com');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12346), 12346, '2005-10-11 06:10.07', 'vastaanotto'::kuittaustyyppi,
                                                          'Mikael', 'Pöytä', '04428671283', '0509288383', 'mikael.poyta@valittavaurakoitsija.fi',
                                                          'Välittävä Urakoitsija', 'Y1234');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, vapaateksti, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus,
 kasittelija_organisaatio_nimi, kasittelija_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12346), 12346, '2005-10-11 14:02.57', 'Siirretty aliurakoitsijalle', 'muutos'::kuittaustyyppi,
                                                          'Mikael', 'Pöytä', '04428671283', '0509288383', 'mikael.poyta@valittavaurakoitsija.fi',
                                                          'Välittävä Urakoitsija', 'Y1234',
        'Veljekset Ukkola Huoltoyritys', 'Y8172');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, vapaateksti, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus,
 kasittelija_organisaatio_nimi, kasittelija_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12346), 12346, '2005-10-11 19:20.57', 'Ukkolat korjasi tilanteen', 'lopetus'::kuittaustyyppi,
                                                          'Mikael', 'Pöytä', '04428671283', '0509288383', 'mikael.poyta@valittavaurakoitsija.fi',
                                                          'Välittävä Urakoitsija', 'Y1234',
        'Veljekset Ukkola Huoltoyritys', 'Y8172');


-- Kolmas ilmoitus: Pudasjärvi, toimenpidepyynto, avoin
INSERT INTO ilmoitus
(urakka, ilmoitusid, ilmoitettu, valitetty, yhteydenottopyynto, lyhytselite, sijainti,
 tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, ilmoitustyyppi, selitteet, urakkatyyppi,
 ilmoittaja_etunimi, ilmoittaja_sukunimi, ilmoittaja_tyopuhelin, ilmoittaja_matkapuhelin, ilmoittaja_sahkoposti, ilmoittaja_tyyppi,
 lahettaja_etunimi, lahettaja_sukunimi, lahettaja_puhelinnumero, lahettaja_sahkoposti)
VALUES ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), 12347, '2007-12-01 20:01.20', '2007-12-07 08:07.50',
                                                                               false, 'Kauhea kuoppa tiessä',
                                                                               ST_MakePoint(499687, 7248153)::GEOMETRY, 6, 6, 6, 6, 6, 'toimenpidepyynto'::ilmoitustyyppi,
                                                                                                                                    ARRAY['kuoppiaTiessa', 'vettaTiella']::ilmoituksenselite[],
                                                                                                                                    (SELECT tyyppi FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'),
                                                                                                                                    'Paavo', 'Poliisimies', '086727461', '0448261234', 'paavo.poliisimies@poliisi.fi', 'viranomainen'::ilmoittajatyyppi,
        'Mika', 'Vaihdemies', '085612567', 'vaihde@valituspalvelu.fi');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12347), 12347, '2007-12-07 08:47.50', 'vastaanotto'::kuittaustyyppi,
                                                          'Merituuli', 'Salmela', '04020671222', '081234512', 'merituuli.salmela@vainamoinen.fi',
                                                          'Väinämöinen', 'Y72787');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, vapaateksti, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12347), 12347, '2007-12-07 08:48.05', 'Anteeksi kauheasti olin kahvilla!',
                                                          'vastaus'::kuittaustyyppi, 'Merituuli', 'Salmela', '04020671222', '081234512', 'merituuli.salmela@vainamoinen.fi',
                                                          'Väinämöinen', 'Y72787');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, vapaateksti, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus,
 kasittelija_organisaatio_nimi, kasittelija_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12347), 12347, '2007-12-07 11:27.07', 'Aliurakoitsija käy katsomassa',
                                                          'muutos'::kuittaustyyppi,
                                                          'Merituuli', 'Salmela', '04020671222', '081234512', 'merituuli.salmela@vainamoinen.fi',
                                                          'Väinämöinen', 'Y72787',
        'Veljekset Ukkola Huoltoyritys', 'Y8172');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, vapaateksti, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus,
 kasittelija_organisaatio_nimi, kasittelija_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12347), 12347, '2007-12-07 15:07.30', 'Ukkolat aloitti työt',
                                                          'aloitus'::kuittaustyyppi,
                                                          'Merituuli', 'Salmela', '04020671222', '081234512', 'merituuli.salmela@vainamoinen.fi',
                                                          'Väinämöinen', 'Y72787',
        'Veljekset Ukkola Huoltoyritys', 'Y8172');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, vapaateksti, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus,
 kasittelija_organisaatio_nimi, kasittelija_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12347), 12347, '2007-12-17 09:17.30', 'Työt ei edisty, hoidetaan itse.',
                                                          'muutos'::kuittaustyyppi,
                                                          'Merituuli', 'Salmela', '04020671222', '081234512', 'merituuli.salmela@vainamoinen.fi',
                                                          'Väinämöinen', 'Y72787',
        'Väinämöinen', 'Y72787');

INSERT INTO ilmoitustoimenpide
(ilmoitus, ilmoitusid, kuitattu, vapaateksti, kuittaustyyppi,
 kuittaaja_henkilo_etunimi, kuittaaja_henkilo_sukunimi, kuittaaja_henkilo_matkapuhelin, kuittaaja_henkilo_tyopuhelin, kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi, kuittaaja_organisaatio_ytunnus,
 kasittelija_organisaatio_nimi, kasittelija_organisaatio_ytunnus)
VALUES ((SELECT id FROM ilmoitus WHERE ilmoitusid=12347), 12347, '2007-12-18 19:17.30', 'Normaalia kiperämpi kuoppa.',
                                                          'vastaus'::kuittaustyyppi,
                                                          'Merituuli', 'Salmela', '04020671222', '081234512', 'merituuli.salmela@vainamoinen.fi',
                                                          'Väinämöinen', 'Y72787',
        'Väinämöinen', 'Y72787');


-- Neljäs ilmoitus: Turun alueella, tiedoitus. Ei kuittauksia!
INSERT INTO ilmoitus
(ilmoitusid, ilmoitettu, valitetty, yhteydenottopyynto, lyhytselite, sijainti,
 tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, ilmoitustyyppi, selitteet,
 ilmoittaja_etunimi, ilmoittaja_sukunimi, ilmoittaja_tyopuhelin, ilmoittaja_matkapuhelin, ilmoittaja_sahkoposti, ilmoittaja_tyyppi,
 lahettaja_etunimi, lahettaja_sukunimi, lahettaja_puhelinnumero, lahettaja_sahkoposti)
VALUES (12348, '2006-02-13 00:00.00', '2005-02-13 00:00.00', false, 'Täällä joku pommi räjähti!!',
               ST_MakePoint(249863, 6723867)::GEOMETRY, 6, 6, 6, 6, 6, 'tiedoitus'::ilmoitustyyppi, ARRAY['virkaApupyynto']::ilmoituksenselite[],
                                                                       'George', 'Doe', '05079163872', '05079163872', '', 'tienkayttaja'::ilmoittajatyyppi,
                                                                       'Mika', 'Vaihdemies', '085612567', 'vaihde@valityspalvelu.fi');


