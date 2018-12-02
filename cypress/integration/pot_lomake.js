let valitse2017 = function () {
    // Tämä rivi on estämässä taasen jo poistettujen elementtien käsittelyä. Eli odotellaan
    // paallystysilmoituksien näkymistä guilla ennen kuin valitaan 2017 vuosi.
    cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader').should('not.be.visible')
    /*cy.get('[data-cy=valinnat-vuosi] button').click()
    cy.get('[data-cy=valinnat-vuosi] .dropdown-menu').should('have.css', 'display').and('match', /block/)
    cy.contains('[data-cy=valinnat-vuosi] ul li a', '2017').click({force: true})*/
    cy.get('[data-cy=valinnat-vuosi]').valinnatValitse({valinta: '2017'})
    cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader').should('be.visible')
    cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader').should('not.exist')
};

describe('Aloita päällystysilmoitus', function () {
    // Lisätään kantaan puhdas testidata
    before(function () {
        cy.POTTestienAlustus()
        cy.server()
    })
    it('Avaa vanha POT-lomake', function () {
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa ja Kainuu').click()
        cy.get('[data-cy=murupolku-urakkatyyppi] button').click()
        // Pudotusvalikoissa pitää tarkistaa ensin, että onhan ne vaihtoehdot näkyvillä. Tämä siksi, että valikon
        // painaminen, jolloin lista vaihtoehtoja tulee näkyviin re-renderaa listan. Tämä taasen aiheuttaa sen,
        // että Cypress saattaa keretä napata tuolla seuraavalla 'contains' käskyllä elementin, jonka React
        // poistaa DOM:ista.
        cy.route('POST', '**/_/urakan-paallystysilmoitukset').as('haeUrakanPaallystysilmoitukset')
        cy.contains('[data-cy=murupolku-urakkatyyppi] ul li a', 'Päällystys').should('be.visible').click()
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Muhoksen päällystysurakka').click()
        cy.get('[data-cy=tabs-taso1-Kohdeluettelo]').click()
        cy.get('[data-cy=tabs-taso2-Päällystysilmoitukset]').click()
        cy.get('[data-cy=tabs-taso2-Päällystysilmoitukset]').parent().should('have.class', 'active')
        //cy.wait('@haeUrakanYllapitokohteet')
        valitse2017();
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
                    expect(virheValinta(1, otsikko)).to.have.length(1)
                        .and.to.contain('Kohteenosa on päällekkäin osan Foo kanssa')
                    expect(virheValinta(2, otsikko)).to.have.length(1)
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
            //Tehdään paljon rivejä lisää
            /*   let j = 2;
               let k = 0;
               for (let i = 0; i < 32; i++) {
                   if (i / (j - 1) === 3) {
                       j = j + 1;
                       k = 0;
                   }
                   let kS=k;
                   let jS=j;
                   cy.get('[data-cy=lisaa-osa-Tierekisteriosoitteet]').eq(k * j).click()
                   cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikotJalkeen) => {
                       let $rivitJalkeen = $gridOtsikotJalkeen.grid.find('tbody tr');
                       let $otsikotJalkeen = $gridOtsikotJalkeen.otsikot;
                       let valitseInputJalkeen = function (rivi, otsikko) {
                           console.log("RIVI: " + rivi);
                           console.log("OTSIKKO: " + otsikko);
                           return $rivitJalkeen.eq(rivi).find('td').eq($otsikotJalkeen.get(otsikko)).find('input')
                       }
                       cy.wrap(valitseInputJalkeen(kS * jS, 'Losa')).type(1)
                       cy.wrap(valitseInputJalkeen(kS * jS, 'Let')).type(kS * 100 + jS)
                       cy.wrap(valitseInputJalkeen(kS * jS + 1, 'Aosa')).type(1)
                       cy.wrap(valitseInputJalkeen(kS * jS + 1, 'Aet')).type(kS * 100 + jS)
                   })
                   k = k + 1;
               }*/
        })
        /* cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
             let $rivit = $gridOtsikot.grid.find('tbody tr');
             let $otsikot = $gridOtsikot.otsikot;
             let valitseInput = function (rivi, otsikko) {
                 return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
             }
             expect(valitseInput(0, 'Let')).to.have.value('100');
             expect(valitseInput(11, 'Let')).to.have.value('200');
             expect(valitseInput(23, 'Let')).to.have.value('300');
             expect(valitseInput(35, 'Let')).to.have.value('400');
         })
         cy.get('[data-cy=paallystystoimenpiteen-tiedot]').gridOtsikot().then(($gridOtsikot) => {
             let $rivit = $gridOtsikot.grid.find('tbody tr');
             let $otsikot = $gridOtsikot.otsikot;
             let valitseInput = function (rivi, otsikko) {
                 return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
             }
             cy.wrap(valitseInput(2, 'Raekoko')).type(2)
             cy.wrap(valitseInput(33, 'Raekoko')).type(33)
         })*/
        cy.get('[data-cy=paallystysilmoitus-perustiedot] [type=checkbox]').check()
        cy.get('[data-cy=pot-tallenna]').click()
    })
})

describe('Käsittele päälystysilmoitus', function () {
    /*before(function () {
        cy.POTTestienAlustus()
        cy.terminaaliKomento().then((terminaaliKomento) => {
            let muokkaaKohdeosaa = '"UPDATE yllapitokohdeosa ' +
                '  SET tr_loppuosa = 1, ' +
                '      tr_loppuetaisyys = 100 ' +
                'WHERE nimi=\'E2E-Testi-kohdeosa\';"';
            let lisaaKohdeosia = '"INSERT INTO yllapitokohdeosa (yllapitokohde, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,' +
                '                              tr_ajorata, tr_kaista, sijainti)' +
                ' VALUES' +
                '  ((SELECT id' +
                '    FROM yllapitokohde' +
                '    WHERE nimi = \'E2E-Testi\'), 22, 1, 100, 1, 200, 1, 1,' +
                '   (SELECT tierekisteriosoitteelle_viiva_ajr AS geom' +
                '    FROM tierekisteriosoitteelle_viiva_ajr(22, 1, 100, 1, 200, 1))),' +
                '  ((SELECT id' +
                '    FROM yllapitokohde' +
                '    WHERE nimi = \'E2E-Testi\'), 22, 1, 200, 1, 300, 1, 1,' +
                '   (SELECT tierekisteriosoitteelle_viiva_ajr AS geom' +
                '    FROM tierekisteriosoitteelle_viiva_ajr(22, 1, 200, 1, 300, 1))),' +
                '  ((SELECT id' +
                '    FROM yllapitokohde' +
                '    WHERE nimi = \'E2E-Testi\'), 22, 1, 300, 1, 400, 1, 1,' +
                '   (SELECT tierekisteriosoitteelle_viiva_ajr AS geom' +
                '    FROM tierekisteriosoitteelle_viiva_ajr(22, 1, 300, 1, 400, 1)));"';
            let lisaaPaallystysilmoitus = '"DO \\$$' +
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
                '  INSERT INTO paallystysilmoitus (paallystyskohde, ilmoitustiedot, luotu, tila)' +
                '  VALUES' +
                '    ((SELECT id' +
                '      FROM yllapitokohde' +
                '      WHERE nimi = \'E2E-Testi\'),' +
                '     jsonb_build_object(\'osoitteet\', kohdeosat, \'alustatoimet\', \'[]\' :: JSONB),' +
                '     NOW(), \'valmis\' :: PAALLYSTYSTILA);' +
                ' END;' +
                ' \\$$ LANGUAGE plpgsql;"'
            cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + muokkaaKohdeosaa)
            cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + lisaaKohdeosia)
            cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + lisaaPaallystysilmoitus)
        })
    })*/

    it('Palaa lomakkeelle', function () {
        //valitse2017();
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
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .pvm.form-control').type('01.01.2017')
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot]').click('bottomRight')
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .pvm.form-control').clear()
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot]').click('bottomRight')
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .virhe').then(($virhe) => {
            let virheet = virheTekstit($virhe)
            expect(virheet).to.have.lengthOf(1)
                .and.to.contain('Anna käsittelypvm')
        })
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .pvm.form-control').type('01.01.2017')
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot]').click('bottomRight')
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
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus] .pvm.form-control').type('01.01.2017')
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus]').click('bottomRight')
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus] .pvm.form-control').clear()
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus]').click('bottomRight')
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus] .virhe').then(($virhe) => {
            let virheet = virheTekstit($virhe)
            expect(virheet).to.have.lengthOf(1)
                .and.to.contain('Anna tarkastuspäivämäärä')
        })
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus] .pvm.form-control').type('01.01.2017')
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus]').click('bottomRight')
        cy.get('[data-cy=paallystysilmoitus-asiatarkastus] .virhe').then(($virhe) => {
            let virheet = virheTekstit($virhe)
            expect(virheet).to.have.lengthOf(1)
                .and.to.contain('Tarkastus ei voi olla ennen valmistumista')
        })
        cy.contains('[data-cy=paallystysilmoitus-asiatarkastus] label', 'Tarkastaja').then(($tarkastaja) => {
            cy.wrap($tarkastaja.parent().find('input')).type('foo').clear().then(($input) => {
                expect(virheTekstit($tarkastaja.parent().find('.virhe'))).to.have.lengthOf(1)
                    .and.to.contain('Anna tarkastaja')
            })
        })
    })
    it('Laita oikea data ja tallenna', function() {
        cy.get('[data-cy=paallystysilmoitus-kasittelytiedot] .pvm.form-control').clear().type('01.12.2017')
        cy.get('[data-cy=pot-tallenna]').click()
    })
})

describe('Korjaa virhedata', function() {
    it('Palaa lomakkeelle', function () {
        //valitse2017();
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
})