import * as ks from '../support/kustannussuunnitelmaFns.js';
import {valitseVesivaylaurakka} from '../support/apurit.js';

const tehtava = 'Poijujen ja suurviittojen ankkuroinnin tarkastus';
const tehtavaryhma = 'Kelluvien turvalaitteiden huoltotyöt ajomatkoineen';
const yksikkohinnanPuuttumisviesti = 'Ei saatavilla';

function valitseAlasvedosta(otsikko, valinta) {
    cy.contains('span.alasvedon-otsikko', otsikko)
        .parent()
        .find('button')
        .click();
    cy.get('ul.livi-alasvetolista:visible')
        .contains('a', valinta)
        .click({force: true});
}

function valitseEnsimmainenAlasvedosta(otsikko) {
    cy.contains('span.alasvedon-otsikko', otsikko)
        .parent()
        .find('button')
        .click();
    cy.get('ul.livi-alasvetolista:visible')
        .find('a')
        .first()
        .click({force: true});
}

function valitseKulunTyyppi(tyyppi) {
    valitseAlasvedosta('Kulun tyyppi*', tyyppi);
}

// Myöhemmin on mahdollista poistaa nuo tarkistukset yksikköhinnalle, kunhan ne saadaan lokaalikantaan ja ci-putkeen
function valitseTehtava() {
    valitseAlasvedosta('Tehtäväryhmä*', tehtavaryhma);
    cy.get('[data-cy="tehtava-dropdown"]', {timeout: 20000})
        .should('be.visible')
        .within(() => {
            cy.get('button').click();
            cy.get('ul.livi-alasvetolista:visible')
                .contains('a', tehtava)
                .click({force: true});
        });
}

function aloitaKulunKirjaus(laskunNumero) {
    cy.contains('button', 'Uusi kulu').click();
    cy.contains('h2', 'Uusi kulu').should('be.visible');
    cy.get('#koontilaskun-numero-input').type(laskunNumero);
}

function tallennaKulu(laskunNumero) {
    cy.contains('button', 'Tallenna')
        .should('be.enabled')
        .click();
    cy.wait('@tallennaKulu')
        .its('response.statusCode')
        .should('eq', 200);

    // Varmistetaan, että ollaan kulujen listaussivulla
    cy.contains('h1', 'Kulujen kohdistus').should('be.visible');
    cy.contains('label', 'Urakkavuosi').should('be.visible');
    cy.get('table.grid')
        .contains(`Koontilasku nro ${laskunNumero}`)
        .should('be.visible');
}

function generoiMahdollisestiPuuttuvatVhuYksikkohinnat() {
    cy.get('body').then(($body) => {
        const puuttuvaYksikkohintaNakyvissa = $body
            .find('span.kentan-otsikko')
            .filter((_, otsikko) => otsikko.textContent.trim() === 'Yksikköhinta')
            .parent()
            .find('.lukutila-arvo:visible')
            .text()
            .includes(yksikkohinnanPuuttumisviesti);

        if (puuttuvaYksikkohintaNakyvissa) {
            cy.contains(yksikkohinnanPuuttumisviesti).should('be.visible');
            cy.contains('button', 'Tallenna').should('be.disabled');

            cy.intercept('POST', '**/_/generoi-vhu-yksikkohinnat').as('generoiVhuYksikkohinnat');
            cy.get('[data-cy="generoi-vhu-yksikkohinnat"]')
                .should('be.enabled')
                .click();
            cy.wait('@generoiVhuYksikkohinnat')
                .its('response.statusCode')
                .should('eq', 200);

            cy.contains('span.kentan-otsikko', 'Yksikköhinta')
                .parent()
                .find('.lukutila-arvo')
                .should(($yksikkohinta) => {
                    expect($yksikkohinta.text()).not.to.include(yksikkohinnanPuuttumisviesti);
                });
            cy.contains('button', 'Tallenna').should('be.enabled');
        }
    });
}

function euroNumeroksi(arvo) {
    return Number(arvo
        .replace(/\s/g, '')
        .replace(/€/g, '')
        .replace(/\./g, '')
        .replace(',', '.'));
}

let valitseKulunPvm = () => {
    const tanaan = new Date();
    const kuukaudenNimi = new Intl.DateTimeFormat('fi-FI', {month: 'long'}).format(tanaan);
    const kuukausiHoitovuosi = (teksti) => teksti.trim().replace(/\s+/g, ' ').toLocaleLowerCase('fi-FI');
    const valittuPaivamaara = [
        String(tanaan.getDate()).padStart(2, '0'),
        String(tanaan.getMonth() + 1).padStart(2, '0'),
        tanaan.getFullYear()
    ].join('.');

    cy.get('[data-cy="koontilaskun-kk-dropdown"] .valittu')
        .invoke('text')
        .then((valittuKuukausi) => {
            const hoitovuosi = valittuKuukausi.match(/(\d+)\.\s*hoitovuosi/i)?.[1];
            expect(hoitovuosi, 'valitun koontilaskun kuukauden hoitovuosi').to.exist;
            const oikeaKuukausi = `${kuukaudenNimi} - ${hoitovuosi}. hoitovuosi`;

            if (kuukausiHoitovuosi(valittuKuukausi) !== kuukausiHoitovuosi(oikeaKuukausi)) {
                cy.get('[data-cy="koontilaskun-kk-dropdown"]')
                    .find('button')
                    .click({force: true});
                cy.get('ul.livi-alasvetolista:visible')
                    .find('.koontilaskun-kuukaudet-komp')
                    .then(($kuukaudet) => {
                        const kuukausi = [...$kuukaudet].find((vaihtoehto) =>
                            kuukausiHoitovuosi(vaihtoehto.textContent) === kuukausiHoitovuosi(oikeaKuukausi));
                        expect(kuukausi, `koontilaskun kuukausi ${oikeaKuukausi}`).to.exist;
                        cy.wrap(kuukausi).click({force: true});
                    });
            }
        });

    cy.contains('h2', 'Uusi kulu').click();
    cy.get('.kalenteri-kontti input').focus().click();
    cy.get('#tanaan').should('be.visible').click();
    cy.get('.kalenteri-kontti input').should('have.value', valittuPaivamaara);
}

function tarkistaMaara(maara, dataCy = 'vhu-muukulu-maara') {
    cy.get(`[data-cy="${dataCy}"]`)
        .invoke('val')
        .then((arvo) => expect(euroNumeroksi(arvo)).to.equal(maara));
}

function tarkistaYhteensa(maara, yksikkohinnanOdotus, dataCy = 'vhu-muukulu-maara-yhteensa') {
    cy.contains('span.kentan-otsikko', 'Yksikköhinta')
        .parent()
        .find('.lukutila-arvo')
        .invoke('text')
        .then((teksti) => {
            const yksikkohinta = euroNumeroksi(teksti);
            if (yksikkohinnanOdotus !== undefined) {
                expect(yksikkohinta).to.equal(yksikkohinnanOdotus);
            }
            cy.get(`[data-cy="${dataCy}"]`)
                .find('.lukutila-arvo')
                .invoke('text')
                .then((yhteensa) => {
                    expect(euroNumeroksi(yhteensa)).to.be.closeTo(
                        Math.round(maara * yksikkohinta * 100) / 100,
                        0.001
                    );
                });
        });
}

describe('Sisävesiväylän urakan kulujen kirjaus', function () {
    beforeEach(function () {
        ks.alustaKanta('Rentoselän urakka');
        valitseVesivaylaurakka('Rentoselän urakka');
        cy.intercept('POST', '**/_/tallenna-kulu').as('tallennaKulu');
        cy.intercept('POST', 'hae-urakan-hintapaatokset').as('hae-hintapaatokset');
        cy.get('[data-cy="tabs-taso1-Kulut"]', {timeout: 20000})
            .should('be.visible')
            .click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000})
            .should('not.exist');
        cy.wait('@hae-hintapaatokset')
            .its('response.statusCode')
            .should('eq', 200);
    });

    it('Lisää Muu kulu', function () {

        aloitaKulunKirjaus('E2E-VHU-MUU');
        valitseKulunPvm();
        valitseKulunTyyppi('Muu kulu');
        cy.contains('label', 'Kuuluu tavoitehintaan').click();
        valitseTehtava();
        generoiMahdollisestiPuuttuvatVhuYksikkohinnat();

        cy.get('[data-cy="vhu-muukulu-maara"]').type('1').blur();
        tarkistaYhteensa(1);

        tallennaKulu('E2E-VHU-MUU');

        cy.get('table.grid tr.klikattava').contains(tehtavaryhma).click();
        cy.contains('h2', 'Muokkaa kulua').should('be.visible');
        tarkistaMaara(1);
        tarkistaYhteensa(1);
        cy.get('[data-cy="vhu-muukulu-maara"]').clear().type('2').blur();
        tarkistaYhteensa(2);
        tallennaKulu('E2E-VHU-MUU');

        cy.get('table.grid tr.klikattava').contains(tehtavaryhma).click();
        cy.contains('h2', 'Muokkaa kulua').should('be.visible');
        tarkistaMaara(2);
        tarkistaYhteensa(2);
    });

    it('Lisää ei-tavoitehintaisen Muu kulun hinnalla', function () {
        aloitaKulunKirjaus('E2E-VHU-MUU-EI-TAVOITE');
        valitseKulunPvm();
        valitseKulunTyyppi('Muu kulu');
        cy.contains('label', 'Ei kuulu tavoitehintaan').click();
        valitseEnsimmainenAlasvedosta('Toimenpide*');
        cy.contains(yksikkohinnanPuuttumisviesti).should('not.exist');
        cy.contains('span.kentan-otsikko', 'Hinta € *').should('be.visible');
        cy.get('#kohdistuksen-summa-0').type('100').blur();

        tallennaKulu('E2E-VHU-MUU-EI-TAVOITE');
    });

    it('Lisää Lisätyö -kulu', function () {
        aloitaKulunKirjaus('E2E-VHU-LISATYO');
        valitseKulunPvm();
        valitseKulunTyyppi('Lisätyö');
        valitseEnsimmainenAlasvedosta('Toimenpide*');
        cy.contains('label', /^Lisätieto \*$/)
            .parent()
            .find('input')
            .type('Cypressin tekemä lisätyö');
        cy.get('#kohdistuksen-summa-0')
            .type('{selectall}100')
            .blur();

        tallennaKulu('E2E-VHU-LISATYO');
    });

    it('Hankintakulu - Tarkista liitteen pakollisuus', function () {
        aloitaKulunKirjaus('E2E-VHU-HANKINTA-EI-LIITETTA');
        valitseKulunTyyppi('Hankintakulu (kiinteä tai määrämitattava)');
        valitseTehtava();
        generoiMahdollisestiPuuttuvatVhuYksikkohinnat();
        cy.get('[data-cy="vhu-hankintakulu-maara"]').type('1').blur();

        cy.contains('.punainen-teksti', 'Lisää toimenpiteen lisätiedot liitteenä.').should('be.visible');
        cy.contains('button', 'Tallenna').should('be.disabled');
    });

    it('Nollaa määrä ja yhteissumma tehtäväryhmää vaihdettaessa', function () {
        aloitaKulunKirjaus('E2E-VHU-NOLLAUS');
        valitseKulunTyyppi('Muu kulu');
        cy.contains('label', 'Kuuluu tavoitehintaan').click();
        valitseTehtava();
        generoiMahdollisestiPuuttuvatVhuYksikkohinnat();
        cy.get('[data-cy="vhu-muukulu-maara"]')
            .type('1')
            .blur();
        cy.get('[data-cy="vhu-muukulu-maara-yhteensa"]')
            .find('.lukutila-arvo')
            .invoke('text')
            .then((summa) => {
                cy.contains('span.alasvedon-otsikko', 'Tehtäväryhmä*')
                    .parent()
                    .find('button')
                    .click();
                cy.get('ul.livi-alasvetolista:visible a')
                    .then(($vaihtoehdot) => {
                        const toinenTehtavaryhma = [...$vaihtoehdot]
                            .find((vaihtoehto) => !vaihtoehto.textContent.includes(tehtavaryhma));
                        expect(toinenTehtavaryhma).to.exist;
                        cy.wrap(toinenTehtavaryhma).click({force: true});
                    });
                cy.get('[data-cy="vhu-muukulu-maara"]')
                    .should('have.value', '');
                cy.get('[data-cy="vhu-muukulu-maara-yhteensa"]')
                    .find('.lukutila-arvo')
                    .should(($yhteensa) => {
                        expect($yhteensa.text().trim()).not.to.equal(summa.trim());
                    });
            });
    });

    it('Lisää liitteellinen Hankintakulu', function () {
        aloitaKulunKirjaus('E2E-VHU-HANKINTA');
        valitseKulunPvm();
        valitseKulunTyyppi('Hankintakulu (kiinteä tai määrämitattava)');
        valitseTehtava();
        generoiMahdollisestiPuuttuvatVhuYksikkohinnat();

        cy.get('[data-cy="vhu-hankintakulu-maara"]')
            .type('1')
            .blur();

        const tiedostonNimi = 'rentoselan-e2e-liite.txt';
        cy.intercept('POST', '**/_/tallenna-liite').as('tallennaLiite');
        cy.get('input[type="file"]').selectFile({
            // Keksitään lennosta jotain sisältöä liitteeseen. Sen lisääminen hankintakululle on vhu-urakoilla pakollista.
            contents: Cypress.Buffer.from('Rentoselän urakan Cypress-testin liite.'),
            fileName: tiedostonNimi,
            mimeType: 'text/plain'
        }, {force: true});
        cy.wait('@tallennaLiite')
            .its('response.statusCode')
            .should('eq', 200);
        cy.contains('.liiterivi', tiedostonNimi)
            .should('be.visible');

        tallennaKulu('E2E-VHU-HANKINTA');

        cy.get('table.grid tr.klikattava').contains(tehtavaryhma).click();
        cy.contains('h2', 'Muokkaa kulua').should('be.visible');
        cy.get('#koontilaskun-numero-input')
            .should('have.value', 'E2E-VHU-HANKINTA');
        tarkistaMaara(1, 'vhu-hankintakulu-maara');
        tarkistaYhteensa(1, undefined, 'vhu-hankintakulu-maara-yhteensa');
        cy.contains('.liiterivi', tiedostonNimi)
            .should('be.visible');
    });
});
