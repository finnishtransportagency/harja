let ajaxLoaderTimeout = 60000;
let odotaElementtia = 45000;

let valitseVuosi = function (vuosi) {
    // Tämä rivi on estämässä taasen jo poistettujen elementtien käsittelyä. Eli odotellaan
    // paallystysilmoituksien näkymistä guilla ennen kuin valitaan 2017 vuosi.
    cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader', {timeout: ajaxLoaderTimeout}).should('not.exist')
    cy.get('[data-cy=valinnat-vuosi]').valinnatValitse({valinta: vuosi.toString()})
    cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader', {timeout: ajaxLoaderTimeout}).should('exist')
    cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader').should('not.exist')
};

let avaaPaallystysIlmoitus = function (vuosi, urakka, kohteenNimi, kohteenTila, napinTeksti) {
    cy.visit("/")
    cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa').click()
    // Ajax loader ei aina ole näkyvissä CI putkessa, joten odotetaan sitä lähes vuosi
    cy.get('.ajax-loader', {timeout: ajaxLoaderTimeout}).should('not.exist')
    cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'})
    cy.contains('[data-cy=urakat-valitse-urakka] li', urakka, {timeout: odotaElementtia}).click()
    cy.get('[data-cy=tabs-taso1-Paallystykset]').click()
    cy.get('[data-cy=tabs-taso2-Paallystysilmoitukset]').click()
    cy.get('[data-cy=tabs-taso2-Paallystysilmoitukset]').parent().should('have.class', 'active')
    cy.get('[data-cy=paallystysilmoitukset-grid] img[src="images/ajax-loader.gif"]', {timeout: ajaxLoaderTimeout}).should('not.exist')
    valitseVuosi(vuosi)
    cy.get('[data-cy=paallystysilmoitukset-grid]')
        .gridOtsikot().then(($gridOtsikot) => {
        cy.wrap($gridOtsikot.grid.find('tbody')).contains(kohteenNimi).parentsUntil('tbody').then(($rivi) => {
            expect($rivi.find('td').eq($gridOtsikot.otsikot.get('Tila')).text().trim()).to.contain(kohteenTila)
        })
    })
    cy.get('[data-cy=paallystysilmoitukset-grid] tr')
        .contains(kohteenNimi)
        .parentsUntil('tbody')
        .contains('button', napinTeksti).click({force: true})
    cy.get('div.paallystysilmoitukset')
}

// TODO: Tänne voisi tehdä vielä testit rivin poistamisesta ison rivimäärän kanssa. Lisäksi voisi testata
// 'Kumoa' napin painelemista. Myös alustatoimille pitäisi kirjoitella testejä

describe('Aloita päällystysilmoitus vanha', function () {
    // Lisätään kantaan puhdas testidata
    before(function () {
        cy.POTTestienAlustus(
            {
                yllapitoluokka: 8,
                ajorata: 1,
                kaista: 11,
                vuosi: 2017,
                kvl: 500
            },
            {
                ajorata: 1,
                kaista: 11
            })
    })
    it('Avaa vanha POT-lomake', function () {

        cy.viewport(1800, 2000)
        cy.server()
        cy.route('POST', '_/urakan-paallystysilmoitus-paallystyskohteella').as('avaa-ilmoitus')

        cy.visit("/")
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa').click()
        cy.get('.ajax-loader', {timeout: ajaxLoaderTimeout}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'})
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Muhoksen päällystysurakka', {timeout: odotaElementtia}).click()
        cy.get('[data-cy=tabs-taso1-Paallystykset]').click()
        cy.get('[data-cy=tabs-taso2-Paallystysilmoitukset]').click()
        cy.get('[data-cy=tabs-taso2-Paallystysilmoitukset]').parent().should('have.class', 'active')
        cy.get('img[src="images/ajax-loader.gif"]').should('not.exist')
        cy.get('[data-cy=piilota-kartta]').click()
        valitseVuosi(2017);
        cy.get('[data-cy=paallystysilmoitukset-grid]')
            .gridOtsikot().then(($gridOtsikot) => {
            cy.wrap($gridOtsikot.grid.find('tbody')).contains('E2E-Testi').parentsUntil('tbody').then(($rivi) => {
                expect($rivi.find('td').eq($gridOtsikot.otsikot.get('Tila')).text().trim()).to.contain('Ei aloitettu')
            })
        })
        cy.get('[data-cy=paallystysilmoitukset-grid] tr')
            .contains('E2E-Testi')
            .parentsUntil('tbody')
            .contains('button', 'Aloita').click()
        cy.wait('@avaa-ilmoitus', {timeout: ajaxLoaderTimeout})
        cy.get('h1', {timeout: odotaElementtia}).should('exist')
    })
    it('Oikeat aloitustiedot', function () {
        cy.viewport(1500, 2000)
        cy.get('h1', {timeout: odotaElementtia}).should('exist')
        // Tierekisteritaulukon tienumeroa, ajorataa ja kaistaa ei pitäisi pystyä muutamaan
        cy.get('[data-cy=paallystysilmoitus-perustiedot] th').then(($otsikot) => {
            let tienumeroIndex;
            let ajorataIndex;
            let kaistaIndex;
            for (let i = 0; i < $otsikot.length; i++) {
                let otsikonTeksti = $otsikot[i].textContent.trim()
                if (otsikonTeksti.localeCompare('Tie') === 0) {
                    tienumeroIndex = i;
                } else if (otsikonTeksti.localeCompare('Ajorata') === 0) {
                    ajorataIndex = i;
                } else if (otsikonTeksti.localeCompare('Kaista') === 0) {
                    kaistaIndex = i;
                }
            }
            return [tienumeroIndex, ajorataIndex, kaistaIndex]
        })
            /*.then((eiMuokattavatSarakkeet) => {
            cy.log("tienumeroIndex: " + eiMuokattavatSarakkeet)
            cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] tbody tr').then(($rivi) => {
                eiMuokattavatSarakkeet.forEach((i) => {
                    expect($rivi.children().get(i)).to.have.class('ei-muokattava');
                });
            })
        })*/
        // Pääkohteen tierekisteriosoitetta pitäisi pystyä muuttamaan
        cy.get('[data-cy=paallystysilmoitus-perustiedot] table td').then(($trTd) => {
            expect($trTd.eq(0).find('input')).to.have.value('22');
            // Vanhoissa urakoissa on ajorata ja kaista näkyvillä
            expect($trTd.eq(1).find('input')).to.have.value('1');
            expect($trTd.eq(2).find('input')).to.have.value('11');
            expect($trTd.eq(3).find('input')).to.have.value('1');
            expect($trTd.eq(4).find('input')).to.have.value('65');
            expect($trTd.eq(5).find('input')).to.have.value('3');
            expect($trTd.eq(6).find('input')).to.have.value('100');
        })
    })
    it('Rivien lisäys', function () {
        cy.viewport(1100, 2000)
        // Lisätään jokunen rivi
        cy.get('[data-cy=lisaa-osa-nappi]').click({force: true}).click({force: true}).click({force: true})
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
            expect($rivit.first().find('td').eq($otsikot.get('Aet')).find('input')).to.have.value('65');
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
        cy.viewport(1100, 2000)
        // Täytetään väärässä muodoss olevaa dataa
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            let valitseInput = function (rivi, otsikko) {
                return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
            }
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
            cy.wrap(valitseInput(2, 'Let')).type(125)
            cy.wrap(valitseInput(2, 'Nimi')).type('Foo')
            cy.wrap(valitseInput(3, 'Aosa')).type(3)
            cy.wrap(valitseInput(3, 'Aet')).type(50)
            // varmistelua
            cy.contains('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] .virhe', 'Kohteenosa on päällekkäin osan "Foo" kanssa')
            cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikotJalkeen) => {
                let $rivitJalkeen = $gridOtsikotJalkeen.grid.find('tbody tr');
                let $otsikotJalkeen = $gridOtsikotJalkeen.otsikot;
                let virheValinta = function (rivi, otsikko) {
                    return $rivitJalkeen.eq(rivi).find('td').eq($otsikotJalkeen.get(otsikko)).find('.virhe').children().map(function () {
                        return this.textContent.replace(/[\u00AD]+/g, '').trim()
                    }).get()
                };
                expect(virheValinta(0, 'Aosa')).to.have.lengthOf(1)
                    .and.to.contain('Alkuosa ei voi olla loppuosan jälkeen');
                expect(virheValinta(0, 'Losa')).to.have.lengthOf(1)
                    .and.to.contain('Loppuosa ei voi olla alkuosaa ennen');

                ['Aosa', 'Aet', 'Losa', 'Let'].forEach((otsikko) => {
                    expect(virheValinta(1, otsikko)).to.have.lengthOf(1)
                        .and.to.contain('Kohteenosa on päällekkäin osan "Foo" kanssa')
                    expect(virheValinta(2, otsikko)).to.have.lengthOf(2)
                        .and.to.contain('Kohteenosa on päällekkäin toisen osan kanssa')
                        .and.to.contain('Alikohde ei voi olla pääkohteen ulkopuolella')
                })
            })
            cy.get('[data-cy=paallystystoimenpiteen-tiedot]').then(($ptGrid) => {
                expect($ptGrid.find('.panel-heading span').text()).to.contain('Tarkista kohteen tr-osoite ennen tallentamista')
                expect($ptGrid.find('td.ei-muokattava').eq(0));
            })
            cy.get('[data-cy=kiviaines-ja-sideaine]').then(($ksGrid) => {
                expect($ksGrid.find('.panel-heading span').text()).to.contain('Tarkista kohteen tr-osoite ennen tallentamista')
                expect($ksGrid.find('td.ei-muokattava').eq(0));
            })
            cy.get('[data-cy=pot-tallenna]').should('be.disabled')
        })

        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            expect($rivit.first().find('td').eq($otsikot.get('Aosa')).find('input')).to.have.value('2');
            expect($rivit.first().find('td').eq($otsikot.get('Aet')).find('input')).to.have.value('65');
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
                kaista: 11
            })
    })
    it('Avaa uusi POT-lomake mutta ei pot2 jos vuosi on 2020', function () {
        cy.viewport(1100, 2000)
        avaaPaallystysIlmoitus(2020, 'Muhoksen päällystysurakka', 'Nakkilan ramppi', 'Valmis käsiteltäväksi', 'Avaa ilmoitus')
        cy.get('div.pot2-lomake').should('not.exist')
    })
    it('Oikeat aloitustiedot', function () {
        cy.viewport(1100, 2000)
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

describe("POT2", function() {
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
                kaista: 11
            })
    })
    it('Avaa POT2-lomake jos vuosi on 2021 tai sen jälkeen', function () {
        cy.viewport(1100, 2000)
        avaaPaallystysIlmoitus(2021, 'Utajärven päällystysurakka', 'Tärkeä kohde mt20', 'Kesken', 'Muokkaa')
        cy.get('div.pot2-lomake')
    })
})
