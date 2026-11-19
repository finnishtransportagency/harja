import transit from "transit-js";
import {
    avaaSanktiotJaBonuksetNakyma,
    clickTimeout,
    siivoaSanktiotKannasta
} from '../support/sanktiotJaBonuksetFns';
import {SP, valitseSivupaneelissaEnsimmainenAlasvetoarvo} from '../support/apurit';

const urakka = 'LOU tienpäällystysurakka ET1 2026,P';
const evk = 'Lounais-Suomi';
// Ylläpitourakassa kohde-kenttä on piilotettu, joten sanktion tunnisteena käytetään perustelua.
const perustelu = 'CY-yllapitourakka-20100-kohdistus-perustelu';
const toimenpideinstanssi = 'LOU Päällyste T2 20100 TP';

function lueTransit(body) {
    const teksti = typeof body === 'string' ? body : JSON.stringify(body);
    return transit.reader('json').read(teksti);
}

function onPerustelulla(sanktio) {
    return sanktio.get(transit.keyword('laatupoikkeama'))
        ?.get(transit.keyword('paatos'))
        ?.get(transit.keyword('perustelu')) === perustelu;
}

function valitsePvm(otsikko, arvo) {
    cy.get(SP).find('label').contains(otsikko)
        .parents('.form-group').first()
        .find('input').first()
        .clear()
        .type(arvo)
        .should('have.value', arvo);
    // Päivämääräkalenteri peittää labelin, joten fokus siirretään pakotetusti pois kentästä.
    cy.get(SP).find('label').contains(otsikko).click({force: true});
}

describe('Ei-MHU-ylläpitourakan sanktion kohdistus', () => {
    before(() => {
        siivoaSanktiotKannasta(null, perustelu);
    });

    after(() => {
        siivoaSanktiotKannasta(null, perustelu);
    });

    it('säilyttää T2-kohdistuksen tallennuksessa ja uudelleenavauksessa', () => {
        cy.viewport(1100, 1200);
        cy.intercept('POST', '**/_/urakan-toimenpiteet').as('haeToimenpideinstanssit');
        avaaSanktiotJaBonuksetNakyma(urakka, evk, 'Päällystys');
        let fixtureToimenpideinstanssiId;
        let tallennettuSanktioId;
        cy.wait('@haeToimenpideinstanssit', {timeout: clickTimeout}).then(({response}) => {
            expect(response && response.statusCode, JSON.stringify(response && response.body)).to.be.within(200, 299);
            const instanssit = transit.reader('json').read(JSON.stringify(response.body));
            const fixtureInstanssi = instanssit.find((instanssi) => instanssi.get(transit.keyword('tpi_nimi')) === toimenpideinstanssi);
            expect(fixtureInstanssi, `Fixture-instanssia ei löytynyt: ${toimenpideinstanssi}`).to.exist;
            fixtureToimenpideinstanssiId = fixtureInstanssi.get(transit.keyword('tpi_id'));
        });

        cy.intercept('POST', '_/tallenna-suorasanktio').as('tallenna');
        cy.intercept('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('haeTallennuksenJalkeen');

        cy.contains('button', 'Lisää uusi').click();
        cy.get(SP).should('be.visible');
        cy.get(SP).contains('label', 'Sanktio').click();
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Sanktion laji');
        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('be.visible')
            .within(() => {
                cy.get('.select-default').valinnatValitse({valinta: toimenpideinstanssi});
            });
        cy.get(SP).contains('.form-group', 'Perustelu').find('textarea')
            .clear().type(perustelu);
        cy.get(SP).contains('.form-group', 'Sanktion suuruus').find('input')
            .clear().type('1000');
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Käsittelytapa');
        valitsePvm('Havaittu', '10.11.2026');
        valitsePvm('Käsitelty', '10.11.2026');

        cy.get(SP).contains('button', 'Tallenna').click();
        cy.wait('@tallenna', {timeout: clickTimeout}).then(({request, response}) => {
            expect(response && response.statusCode, JSON.stringify(response && response.body)).to.be.within(200, 299);
            const lahetetty = lueTransit(request.body);
            expect(lahetetty.get(transit.keyword('sanktio')).get(transit.keyword('toimenpideinstanssi'))).to.equal(fixtureToimenpideinstanssiId);
        });
        cy.wait('@haeTallennuksenJalkeen', {timeout: clickTimeout})
            .then(({response}) => {
                expect(response && response.statusCode, JSON.stringify(response && response.body)).to.be.within(200, 299);
                const sanktiot = lueTransit(response.body);
                const tallennettuSanktio = sanktiot.find(onPerustelulla);
                expect(tallennettuSanktio, `Tallennettua sanktiota ei löytynyt: ${perustelu}`).to.exist;
                tallennettuSanktioId = tallennettuSanktio.get(transit.keyword('id'));
                expect(tallennettuSanktioId).to.exist;
                expect(tallennettuSanktio.get(transit.keyword('toimenpideinstanssi'))).to.equal(fixtureToimenpideinstanssiId);
            });
        cy.get('.toast-viesti.onnistunut', {timeout: clickTimeout})
            .should('be.visible').and('contain.text', 'Sanktion tallennus onnistui');

        cy.get('.sanktiot').contains('td', perustelu).click();
        cy.get(SP).should('be.visible');
        cy.intercept('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('haeUudelleenAvattu');
        avaaSanktiotJaBonuksetNakyma(urakka, evk, 'Päällystys');
        cy.wait('@haeUudelleenAvattu', {timeout: clickTimeout})
            .then(({response}) => {
                expect(response && response.statusCode, JSON.stringify(response && response.body)).to.be.within(200, 299);
                const sanktiot = lueTransit(response.body);
                const uudelleenAvattuSanktio = sanktiot.find(onPerustelulla);
                expect(uudelleenAvattuSanktio, `Uudelleen avattua sanktiota ei löytynyt: ${perustelu}`).to.exist;
                expect(uudelleenAvattuSanktio.get(transit.keyword('id'))).to.equal(tallennettuSanktioId);
                expect(uudelleenAvattuSanktio.get(transit.keyword('toimenpideinstanssi'))).to.equal(fixtureToimenpideinstanssiId);
            });
        cy.get('.sanktiot').contains('td', perustelu).click();
        cy.get(SP).should('be.visible');
        cy.get(SP).contains('.form-group', 'Kulun kohdistus')
            .should('contain.text', toimenpideinstanssi);
    });
});
