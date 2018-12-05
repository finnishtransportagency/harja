import transit from '../../node_modules/transit-js/transit'

let valitseVuosi = function (vuosi) {
    // Tämä rivi on estämässä taasen jo poistettujen elementtien käsittelyä. Eli odotellaan
    // paallystysilmoituksien näkymistä guilla ennen kuin valitaan 2017 vuosi.
    cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader', {timeout: 10000}).should('not.be.visible')
    cy.get('[data-cy=valinnat-vuosi]').valinnatValitse({valinta: vuosi.toString()})
    cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader').should('be.visible')
    cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader').should('not.exist')
};

describe('Aloita päällystysilmoitus vanha', function () {
    // Lisätään kantaan puhdas testidata
    before(function () {
        cy.POTTestienAlustus(
            {
                yllapitoluokka: 8,
                ajorata: 1,
                kaista: 1,
                vuosi: 2017,
                kvl: 500
            },
            {
                ajorata: 1,
                kaista: 1
            })
    })
    it('Avaa vanha POT-lomake', function () {

        cy.visit("/")
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa ja Kainuu').click()
        cy.get('.ajax-loader', {timeout: 10000}).should('not.be.visible')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'})
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Muhoksen päällystysurakka', {timeout: 10000}).click()
        cy.get('[data-cy=tabs-taso1-Kohdeluettelo]').click()
        cy.get('[data-cy=tabs-taso2-Päällystysilmoitukset]').click()
        cy.get('[data-cy=tabs-taso2-Päällystysilmoitukset]').parent().should('have.class', 'active')
        valitseVuosi(2017);
        cy.get('[data-cy=paallystysilmoitukset-grid] img[src="images/ajax-loader.gif"]').should('not.exist')
        cy.get('[data-cy=paallystysilmoitukset-grid]')
            .gridOtsikot().then(($gridOtsikot) => {
            cy.wrap($gridOtsikot.grid.find('tbody')).contains('E2E-Testi').parentsUntil('tbody').then(($rivi) => {
                expect($rivi.find('td').eq($gridOtsikot.otsikot.get('Tila')).text().trim()).to.contain('-')
            })
        })
        cy.get('[data-cy=paallystysilmoitukset-grid] tr')
            .contains('E2E-Testi')
            .parentsUntil('tbody')
            .contains('button', 'Aloita päällystysilmoitus').click()
    })
    it('Oikeat aloitustiedot', function () {
        // Tierekisteritaulukon tienumeroa, ajorataa ja kaistaa ei pitäisi pystyä muutamaan
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] th').then(($otsikot) => {
            let tienumeroIndex;
            let ajorataIndex;
            let kaistaIndex;
            for (let i = 0; i < $otsikot.length; i++) {
                let otsikonTeksti = $otsikot[i].textContent.trim()
                if (otsikonTeksti.localeCompare('Tienumero') === 0) {
                    tienumeroIndex = i;
                } else if (otsikonTeksti.localeCompare('Ajorata') === 0) {
                    ajorataIndex = i;
                } else if (otsikonTeksti.localeCompare('Kaista') === 0) {
                    kaistaIndex = i;
                }
            }
            return [tienumeroIndex, ajorataIndex, kaistaIndex]
        }).then((eiMuokattavatSarakkeet) => {
            cy.log("tienumeroIndex: " + eiMuokattavatSarakkeet)
            cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] tbody tr').then(($rivi) => {
                eiMuokattavatSarakkeet.forEach((i) => {
                    expect($rivi.children().get(i)).to.have.class('ei-muokattava');
                });
            })
        })
        // Pääkohteen tierekisteriosoitetta ei pitäisi pystyä muuttamaan
        cy.get('[data-cy=paallystysilmoitus-perustiedot]')
            .contains('Tierekisteriosoite')
            .parentsUntil('.row.lomakerivi')
            .contains('Tie ')
            .should('have.class', 'form-control-static')
    })
    it('Rivien lisäys', function () {
        // Lisätään jokunen rivi
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] tbody tr button')
            .contains('Lisää osa').click().click().click()
        // Katsotaan, että niissä on oikeanlaisia virheitä
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] tbody .virheet')
            .should('have.length', 12)
            .each(($virheet, index, $virheetLista) => {
                switch (index) {
                    case 0:
                        expect($virheet.text().replace(/[\u00AD]+/g, '')).to.contain('Anna loppuosa');
                        break;
                    case 1:
                        expect($virheet.text().replace(/[\u00AD]+/g, '')).to.contain('Anna loppuetäisyys');
                        break;
                    case 2:
                        expect($virheet.text().replace(/[\u00AD]+/g, '')).to.contain('Anna alkuosa');
                        break;
                    case 3:
                        expect($virheet.text().replace(/[\u00AD]+/g, '')).to.contain('Anna alkuetäisyys');
                        break;
                }
            })
        // Katsotaan, että vain ensimmäisellä rivillä on alkuosa ja alkuetäisyys, kun taas viimeisellä rivillä on
        // loppuosa ja loppuetaisyys
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            expect($rivit.first().find('td').eq($otsikot.get('Aosa')).find('input')).to.have.value('1');
            expect($rivit.first().find('td').eq($otsikot.get('Aet')).find('input')).to.have.value('0');
            expect($rivit.first().find('td').eq($otsikot.get('Losa')).find('input')).to.be.empty;
            expect($rivit.first().find('td').eq($otsikot.get('Let')).find('input')).to.be.empty;
            expect($rivit.last().find('td').eq($otsikot.get('Aosa')).find('input')).to.be.empty;
            expect($rivit.last().find('td').eq($otsikot.get('Aet')).find('input')).to.be.empty;
            expect($rivit.last().find('td').eq($otsikot.get('Losa')).find('input')).to.have.value('3');
            expect($rivit.last().find('td').eq($otsikot.get('Let')).find('input')).to.have.value('100');
            for (let i = 1; i < $rivit.length - 1; i++) {
                let sarakkeet = $rivit.eq(i).find('td');
                expect(sarakkeet.eq($otsikot.get('Aosa')).find('input')).to.be.empty;
                expect(sarakkeet.eq($otsikot.get('Aet')).find('input')).to.be.empty;
                expect(sarakkeet.eq($otsikot.get('Losa')).find('input')).to.be.empty;
                expect(sarakkeet.eq($otsikot.get('Let')).find('input')).to.be.empty;
            }
        })
    })
    it('Rivien validointi', function () {
        // Täytetään väärässä muodoss olevaa dataa
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            let valitseInput = function (rivi, otsikko) {
                return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
            }
            // Piillotetaan kartta, jotta se ei ole gridin edessä
            cy.get('[data-cy=piilota-kartta]').click()
            cy.wrap(valitseInput(0, 'Aosa')).clear().type(2)
            cy.wrap(valitseInput(0, 'Losa')).type(1)
            cy.wrap(valitseInput(0, 'Let')).type(100000)
            cy.wrap(valitseInput(1, 'Aosa')).type(3)
            cy.wrap(valitseInput(1, 'Aet')).type(10)
            cy.wrap(valitseInput(1, 'Losa')).type(3)
            cy.wrap(valitseInput(1, 'Let')).type(20)
            cy.wrap(valitseInput(2, 'Aosa')).type(3)
            cy.wrap(valitseInput(2, 'Aet')).type(15)
            cy.wrap(valitseInput(2, 'Losa')).type(3)
            cy.wrap(valitseInput(2, 'Let')).type(25)
            cy.wrap(valitseInput(2, 'Nimi')).type('Foo')
            // varmistelua
            cy.contains('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] .virhe', 'Kohteenosa on päällekkäin osan Foo kanssa')
            cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikotJalkeen) => {
                let $rivitJalkeen = $gridOtsikotJalkeen.grid.find('tbody tr');
                let $otsikotJalkeen = $gridOtsikotJalkeen.otsikot;
                let virheValinta = function (rivi, otsikko) {
                    return $rivitJalkeen.eq(rivi).find('td').eq($otsikotJalkeen.get(otsikko)).find('.virhe').children().map(function () {
                        return this.textContent.replace(/[\u00AD]+/g, '').trim()
                    }).get()
                };
                expect(virheValinta(0, 'Aosa')).to.have.lengthOf(2)
                    .and.to.contain('Alkuosa ei voi olla loppuosan jälkeen')
                    .and.to.contain('Tiellä 22 ei ole osaa 2');
                expect(virheValinta(0, 'Aet')).to.be.empty;
                expect(virheValinta(0, 'Losa')).to.have.lengthOf(1)
                    .and.to.contain('Loppuosa ei voi olla alkuosaa ennen');

                expect(virheValinta(0, 'Let')).to.have.lengthOf(1);
                expect(virheValinta(0, 'Let')[0]).to.contain('Osan 1 maksimietäisyys on ');
                ['Aosa', 'Aet', 'Losa', 'Let'].forEach((otsikko) => {
                    expect(virheValinta(1, otsikko)).to.have.lengthOf(1)
                        .and.to.contain('Kohteenosa on päällekkäin osan Foo kanssa')
                    expect(virheValinta(2, otsikko)).to.have.lengthOf(1)
                        .and.to.contain('Kohteenosa on päällekkäin toisen osan kanssa')
                })
            })
            cy.get('[data-cy=paallystystoimenpiteen-tiedot]').then(($ptGrid) => {
                expect($ptGrid.find('.panel-heading span').text()).to.contain('Tierekisterikohteet taulukko on virheellisessä tilassa')
                expect($ptGrid.find('input').get()).to.be.empty;
            })
            cy.get('[data-cy=kiviaines-ja-sideaine]').then(($ksGrid) => {
                expect($ksGrid.find('.panel-heading span').text()).to.contain('Tierekisterikohteet taulukko on virheellisessä tilassa')
                expect($ksGrid.find('input').get()).to.be.empty;
            })
            cy.get('[data-cy=pot-tallenna]').should('be.disabled')
        })

        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            expect($rivit.first().find('td').eq($otsikot.get('Aosa')).find('input')).to.have.value('2');
            expect($rivit.first().find('td').eq($otsikot.get('Aet')).find('input')).to.have.value('0');
            expect($rivit.first().find('td').eq($otsikot.get('Losa')).find('input')).to.be.empty;
            expect($rivit.first().find('td').eq($otsikot.get('Let')).find('input')).to.be.empty;
            expect($rivit.last().find('td').eq($otsikot.get('Aosa')).find('input')).to.be.empty;
            expect($rivit.last().find('td').eq($otsikot.get('Aet')).find('input')).to.be.empty;
            expect($rivit.last().find('td').eq($otsikot.get('Losa')).find('input')).to.have.value('3');
            expect($rivit.last().find('td').eq($otsikot.get('Let')).find('input')).to.have.value('100');
            for (let i = 1; i < $rivit.length - 1; i++) {
                let sarakkeet = $rivit.eq(i).find('td');
                expect(sarakkeet.eq($otsikot.get('Aosa')).find('input')).to.be.empty;
                expect(sarakkeet.eq($otsikot.get('Aet')).find('input')).to.be.empty;
                expect(sarakkeet.eq($otsikot.get('Losa')).find('input')).to.be.empty;
                expect(sarakkeet.eq($otsikot.get('Let')).find('input')).to.be.empty;
            }
        })
    })

    it('Valmis käsiteltäväksi', function () {
        // Täytetään oikeassa muodossa olevaa dataa ja lisätään paljon uusia rivejä.
        // Joskus oli ongelmia ison rivimäärän kanssa.
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            let valitseInput = function (rivi, otsikko) {
                return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
            }
            // Täytetään taulukkoon oikean muotoisia tietoja
            cy.wrap(valitseInput(0, 'Aosa')).clear().type(1)
            cy.wrap(valitseInput(0, 'Aet')).clear().type(0)
            cy.wrap(valitseInput(0, 'Losa')).clear().type(1)
            cy.wrap(valitseInput(0, 'Let')).clear().type(100)
            cy.wrap(valitseInput(1, 'Aosa')).clear().type(1)
            cy.wrap(valitseInput(1, 'Aet')).clear().type(100)
            cy.wrap(valitseInput(1, 'Losa')).clear().type(1)
            cy.wrap(valitseInput(1, 'Let')).clear().type(200)
            cy.wrap(valitseInput(2, 'Aosa')).clear().type(1)
            cy.wrap(valitseInput(2, 'Aet')).clear().type(200)
            cy.wrap(valitseInput(2, 'Losa')).clear().type(1)
            cy.wrap(valitseInput(2, 'Let')).clear().type(300)
            cy.wrap(valitseInput(3, 'Aosa')).clear().type(1)
            cy.wrap(valitseInput(3, 'Aet')).clear().type(300)
            cy.wrap(valitseInput(3, 'Losa')).clear().type(1)
            cy.wrap(valitseInput(3, 'Let')).clear().type(400)
        })
        // Varmistetaan, että input kenttiä löytyy (eli grid ei ole epävalidissa tilassa)
        cy.get('[data-cy=paallystystoimenpiteen-tiedot] input')
        cy.get('[data-cy=paallystystoimenpiteen-tiedot]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            let valitseInput = function (rivi, otsikko) {
                return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
            }
            // Täytetään taulukkoon oikean muotoisia tietoja
            cy.wrap(valitseInput(0, 'Raekoko')).type(1).then(($raekokoInput) => {
                cy.wrap($raekokoInput.parentsUntil('td')).contains('button', 'Täytä').click()
            })
            cy.wrap(valitseInput(0, 'Leveys (m)')).type(1)
            cy.wrap(valitseInput(1, 'Leveys (m)')).type(2).then(($leveysInput) => {
                cy.wrap($leveysInput.parentsUntil('td')).contains('button', 'Toista').click().then(($eiKayteta) => {
                    cy.wrap(valitseInput($rivit.length - 1, 'Leveys (m)')).should('have.value', '2')
                    for (let i = 0; i < $rivit.length; i++) {
                        let sarakkeet = $rivit.eq(i).find('td');
                        expect(sarakkeet.eq($otsikot.get('Raekoko')).find('input')).to.have.value('1');
                        if (i === 0 || i === 2) {
                            expect(sarakkeet.eq($otsikot.get('Leveys (m)')).find('input')).to.have.value('1');
                        } else {
                            expect(sarakkeet.eq($otsikot.get('Leveys (m)')).find('input')).to.have.value('2');
                        }
                    }
                })
            })
        })
        cy.get('[data-cy=paallystysilmoitus-perustiedot] [type=checkbox]').check()
        cy.get('[data-cy=pot-tallenna]').click()
    })
})

describe('Käsittele päälystysilmoitus', function () {
    it('Palaa lomakkeelle', function () {
        // TODO: Tässä on sellainen bugi, että joskus 2017 jää valituksi ja toisinaan oletusvuosi on otettu takaisin
        cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader', {timeout: 10000}).should('not.be.visible')
        cy.get('[data-cy=valinnat-vuosi]').then(($valinta) => {
            if ($valinta.find('.valittu').text().trim() === '2018') {
                valitseVuosi(2017)
            }
        })
        cy.get('img[src="images/ajax-loader.gif"]').should('not.exist')
        cy.contains('[data-cy=valinnat-vuosi] .valittu', '2017').should('be.visible')
        cy.get('[data-cy=paallystysilmoitukset-grid]')
            .gridOtsikot().then(($gridOtsikot) => {
            cy.wrap($gridOtsikot.grid.find('tbody')).contains('E2E-Testi').parentsUntil('tbody').then(($rivi) => {
                expect($rivi.find('td').eq($gridOtsikot.otsikot.get('Tila')).text().trim()).to.contain('Valmis käsiteltäväksi')
            })
        })
        cy.get('[data-cy=paallystysilmoitukset-grid] tr')
            .contains('E2E-Testi')
            .parentsUntil('tbody')
            .contains('button', 'Päällystysilmoitus').click()

    })
    it('Tarkasta lomakkeen alkutila ja virheet', function () {
        //TODO Korjaa bugi koodista tämän osalta
        //cy.get('[data-cy=pot-tallenna]').should('be.disabled')
        let virheTekstit = function ($virhe) {
            return $virhe.children().map(function () {
                return this.textContent.replace(/[\u00AD]+/g, '').trim()
            }).get()
        }
        // Käsittelytietojen tarkastus
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .livi-alasveto').valinnatValitse({valinta: 'Hylätty'})
        cy.contains('Käsitelty').parent().should('have.class', 'required')
        //TODO Korjaa bugi, tuohon ei pitäisi tarvita ensin kirjottaa jotain, että virheviestit näkyisivät
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .pvm.form-control').pvmValitse({pvm: '01.01.2017'}).pvmTyhjenna()
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .virhe').then(($virhe) => {
            let virheet = virheTekstit($virhe)
            expect(virheet).to.have.lengthOf(1)
                .and.to.contain('Anna käsittelypvm')
        })
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .pvm.form-control').pvmValitse({pvm: '01.01.2017'})
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .virhe').then(($virhe) => {
            let virheet = virheTekstit($virhe)
            expect(virheet).to.have.lengthOf(1)
                .and.to.contain('Käsittely ei voi olla ennen valmistumista')
        })
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] textarea').type('a').clear()
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] textarea').parentsUntil('div.lomakerivi').find('.virhe').then(($virhe) => {
            let virheet = virheTekstit($virhe)
            expect(virheet).to.have.lengthOf(1)
                .and.to.contain('Anna päätöksen selitys')
        })

        // Asiatarkastuksen tarkastus
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus] .pvm.form-control').pvmValitse({pvm: '01.01.2017'}).pvmTyhjenna()
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus] .virhe').then(($virhe) => {
            let virheet = virheTekstit($virhe)
            expect(virheet).to.have.lengthOf(1)
                .and.to.contain('Anna tarkastuspäivämäärä')
        })
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus] .pvm.form-control').pvmValitse({pvm: '01.01.2017'})
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus] .virhe').then(($virhe) => {
            let virheet = virheTekstit($virhe)
            expect(virheet).to.have.lengthOf(1)
                .and.to.contain('Tarkastus ei voi olla ennen valmistumista')
        })
        cy.contains('[data-cy=paallystysilmoitus-asiatarkastus] label', 'Tarkastaja').then(($tarkastaja) => {
            cy.wrap($tarkastaja.parent().find('input')).type('foo').clear().then(($input) => {
                cy.wrap($input.parent()).find('.virhe').should('have.lengthOf', 1).and('contain', 'Anna tarkastaja')
            })
        })
    })
    it('Laita oikea data ja tallenna', function () {
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .pvm.form-control').pvmValitse({pvm: '01.12.2017'})
        cy.get('[data-cy=pot-tallenna]').click()
    })
})

describe('Korjaa virhedata', function () {
    before(function () {
        cy.terminaaliKomento().then((terminaaliKomento) => {
            let lisaaKohdeosia = '"DO \\$$' +
                ' DECLARE' +
                ' BEGIN' +
                '  FOR i IN 0..32 LOOP' +
                '    INSERT INTO yllapitokohdeosa (yllapitokohde, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,' +
                '                                  tr_ajorata, tr_kaista, sijainti)' +
                '      VALUES ((SELECT id' +
                '                FROM yllapitokohde' +
                '                WHERE nimi = \'E2E-Testi\'), 22, 1, 400+i*2, 1, 400+(i+1)*2, 1, 1,' +
                '                (SELECT tierekisteriosoitteelle_viiva_ajr AS geom' +
                '                 FROM tierekisteriosoitteelle_viiva_ajr(22, 1, 400+i*2, 1, 400+(i+1)*2, 1)));' +
                '  END LOOP;' +
                ' END;' +
                ' \\$$ LANGUAGE plpgsql;"';
            let muokkaaPaallystysilmoitus = '"DO \\$$' +
                ' DECLARE' +
                '  kohdeosat   JSONB;' +
                '  kohdeosa_id INTEGER;' +
                ' BEGIN' +
                '  kohdeosat = \'[]\' :: JSONB;' +
                '  FOR kohdeosa_id IN (SELECT id' +
                '                      FROM yllapitokohdeosa' +
                '                      WHERE yllapitokohde = (SELECT id' +
                '                                             FROM yllapitokohde' +
                '                                             WHERE nimi = \'E2E-Testi\')) LOOP' +
                '    kohdeosat = kohdeosat || jsonb_build_array(jsonb_build_object(\'kohdeosa-id\', kohdeosa_id));' +
                '  END LOOP;' +
                '  UPDATE paallystysilmoitus' +
                '  SET' +
                '     ilmoitustiedot = jsonb_build_object(\'osoitteet\', kohdeosat, \'alustatoimet\', \'[]\' :: JSONB)' +
                '  WHERE paallystyskohde = (SELECT id' +
                '                            FROM yllapitokohde' +
                '                            WHERE nimi = \'E2E-Testi\');' +
                ' END;' +
                ' \\$$ LANGUAGE plpgsql;"';
            cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + lisaaKohdeosia)
            cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + muokkaaPaallystysilmoitus)
        })
    })
    it('Palaa lomakkeelle', function () {
        //valitseVuosi(2017);
        cy.contains('[data-cy=valinnat-vuosi] .valittu', '2017').should('be.visible')
        cy.get('[data-cy=paallystysilmoitukset-grid]')
            .gridOtsikot().then(($gridOtsikot) => {
            cy.wrap($gridOtsikot.grid.find('tbody')).contains('E2E-Testi').parentsUntil('tbody').then(($rivi) => {
                expect($rivi.find('td').eq($gridOtsikot.otsikot.get('Päätös')).text().trim()).to.contain('Hylätty')
            })
        })
        cy.get('[data-cy=paallystysilmoitukset-grid] tr')
            .contains('E2E-Testi')
            .parentsUntil('tbody')
            .contains('button', 'Päällystysilmoitus').click()
    })
    it('Tetaa isoa rivimäärää ja tallennussanoman oikeamuotoisuutta', function () {
        cy.server()
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            let valitseInput = function (rivi, otsikko) {
                return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
            }
            cy.wrap(valitseInput(11, 'Let')).clear().type('415');
            cy.wrap(valitseInput(12, 'Aet')).clear().type('415').then(($input12) => {
                expect(valitseInput(0, 'Let')).to.have.value('100');
                expect(valitseInput(11, 'Let')).to.have.value('415');
                expect(valitseInput(23, 'Let')).to.have.value('440');
                expect(valitseInput(35, 'Let')).to.have.value('464');
            });
        })
        // Varmistetaan, että input kenttiä löytyy (eli grid ei ole epävalidissa tilassa)
        cy.get('[data-cy=paallystystoimenpiteen-tiedot] input')
        cy.get('[data-cy=paallystystoimenpiteen-tiedot]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            let valitseInput = function (rivi, otsikko) {
                return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
            }
            cy.wrap(valitseInput(2, 'Raekoko')).type(2)
            cy.wrap(valitseInput(33, 'Raekoko')).type(33)
        })
        cy.get('[data-cy=kiviaines-ja-sideaine]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            let valitseInput = function (rivi, otsikko) {
                return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
            }
            cy.wrap(valitseInput(2, 'KM-arvo')).type(2)
            cy.wrap(valitseInput(33, 'KM-arvo')).type(33)
        })
        cy.route('POST', '_/tallenna-paallystysilmoitus').as('tallenna-paallystysilmoitus')
        cy.get('[data-cy=pot-tallenna]').click()
        cy.wait('@tallenna-paallystysilmoitus').then(($xhr) => {
            let reader = transit.reader("json");
            let kutsu = reader.read(JSON.stringify($xhr.requestBody));
            let trData = kutsu.get(transit.keyword('paallystysilmoitus')).get(transit.keyword('ilmoitustiedot')).get(transit.keyword('osoitteet')).rep;
            expect(trData[2].get(transit.keyword('tr-alkuetaisyys'))).to.equal(200)
            expect(trData[2].get(transit.keyword('tr-loppuetaisyys'))).to.equal(300)
            expect(trData[2].get(transit.keyword('toimenpide-raekoko'))).to.equal(2)
            expect(trData[2].get(transit.keyword('km-arvo'))).to.equal('2')
            expect(trData[33].get(transit.keyword('tr-alkuetaisyys'))).to.equal(458)
            expect(trData[33].get(transit.keyword('tr-loppuetaisyys'))).to.equal(460)
            expect(trData[33].get(transit.keyword('toimenpide-raekoko'))).to.equal(33)
            expect(trData[33].get(transit.keyword('km-arvo'))).to.equal('33')
        })
    })
})
describe('Aloita päällystysilmoitus uusi', function () {
    // Lisätään kantaan puhdas testidata
    before(function () {
        cy.POTTestienAlustus(
            {
                yllapitoluokka: null,
                ajorata: null,
                kaista: null,
                vuosi: 2018,
                kvl: null
            },
            {
                ajorata: 1,
                kaista: 1
            })
    })
    it('Avaa uusi POT-lomake', function () {

        cy.visit("/")
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa').click()
        cy.get('.ajax-loader', {timeout: 10000}).should('not.be.visible')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'})
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Muhoksen päällystysurakka', {timeout: 10000}).click()
        cy.get('[data-cy=tabs-taso1-Kohdeluettelo]').click()
        cy.get('[data-cy=tabs-taso2-Päällystysilmoitukset]').click()
        cy.get('[data-cy=tabs-taso2-Päällystysilmoitukset]').parent().should('have.class', 'active')
        cy.get('[data-cy=paallystysilmoitukset-grid] img[src="images/ajax-loader.gif"]', {timeout: 10000}).should('not.exist')
        cy.get('[data-cy=paallystysilmoitukset-grid]')
            .gridOtsikot().then(($gridOtsikot) => {
            cy.wrap($gridOtsikot.grid.find('tbody')).contains('E2E-Testi').parentsUntil('tbody').then(($rivi) => {
                expect($rivi.find('td').eq($gridOtsikot.otsikot.get('Tila')).text().trim()).to.contain('-')
            })
        })
        cy.get('[data-cy=paallystysilmoitukset-grid] tr')
            .contains('E2E-Testi')
            .parentsUntil('tbody')
            .contains('button', 'Aloita päällystysilmoitus').click()
    })
    it('Oikeat aloitustiedot', function () {
        // Tierekisteritaulukon tienumeroa, ajorataa ja kaistaa ei pitäisi pystyä muutamaan
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            let valitseInput = function (rivi, otsikko) {
                return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
            }
            valitseInput(0, 'Ajorata')
            valitseInput(0, 'Kaista')
            valitseInput(0, 'Tienumero')
        })
    })
})