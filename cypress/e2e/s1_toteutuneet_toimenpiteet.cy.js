import {
    avaaHarjaTimeoutilla,
    clickTimeout,
    ladataanHarjaaTimeout
} from '../support/apurit.js';

const urakkaNimi = 'Rentoselän urakka';
const tehtavaNimi = 'Poijujen sijainnin ja asennon tarkastukset ml. vedenpäällisen osan silmämääräinen tarkastaminen ja heijastinkalvojen tarkastus ja tarvittaessa uusiminen';
const urakkavuosi = '1. urakkavuosi (2023—2024)';
const alkuperainenMaara = '17';
const maaramuutos = 5;
const tavoitemaara = '22,00';
const muutoksenSyy = 'VHU-kustannusnäkymän Cypress-testi';

function suoritaSql(sql) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        const rivitettySql = sql.replace(/\n/g, ' ').replace(/"/g, '\\"');
        cy.exec(`${terminaaliKomento} psql -h localhost -U harja harja -v ON_ERROR_STOP=1 -c "${rivitettySql}"`);
    });
}

function alustaTestidata() {
    suoritaSql(`
        DELETE FROM mhu_muutos_tehtava_ja_maaraluettelo mmtml
        USING mhu_muutos mm
        WHERE mmtml.muutos = mm.id
          AND mm.urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}')
          AND mm.syy = '${muutoksenSyy}';

        DELETE FROM mhu_muutos mm
        WHERE mm.urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}')
          AND mm.syy = '${muutoksenSyy}';

        DELETE FROM urakka_tehtavamaara ut
        WHERE ut.urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}')
          AND ut."hoitokauden-alkuvuosi" = 2023
          AND ut.tehtava = (SELECT id FROM tehtava WHERE nimi = '${tehtavaNimi}');

        WITH uusi_muutos AS (
            INSERT INTO mhu_muutos
                (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
            VALUES
                ((SELECT id FROM urakka WHERE nimi = '${urakkaNimi}'),
                 '2023-10-01',
                 '${muutoksenSyy}',
                 (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
                 CURRENT_TIMESTAMP,
                 false)
            RETURNING id
        )
        INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo
            (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
        SELECT uusi_muutos.id,
               (SELECT id FROM tehtava WHERE nimi = '${tehtavaNimi}'),
               2023,
               ${maaramuutos},
               1
        FROM uusi_muutos;
    `);
}

function poistaTestidata() {
    suoritaSql(`
        DELETE FROM mhu_muutos_tehtava_ja_maaraluettelo mmtml
        USING mhu_muutos mm
        WHERE mmtml.muutos = mm.id
          AND mm.syy = '${muutoksenSyy}';

        DELETE FROM mhu_muutos mm
        WHERE mm.syy = '${muutoksenSyy}';

        DELETE FROM urakka_tehtavamaara ut
        WHERE ut.urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}')
          AND ut."hoitokauden-alkuvuosi" = 2023
          AND ut.tehtava = (SELECT id FROM tehtava WHERE nimi = '${tehtavaNimi}');
    `);
}

function valitseVesivaylaurakka() {
    avaaHarjaTimeoutilla();

    cy.get('[data-cy="murupolku-urakkatyyppi"]')
        .find('button')
        .click();
    cy.get('[data-cy="murupolku-urakkatyyppi"]')
        .contains('ul li a', 'Vesiväylät ja kanavat')
        .should('be.visible')
        .click({force: true});

    cy.get('#alasveto-hallintayksikko')
        .find('button')
        .click();
    cy.get('#alasveto-hallintayksikko')
        .contains('ul li a', 'Sisävesiväylät')
        .should('be.visible')
        .click({force: true});

    cy.get('.ajax-loader', {timeout: ladataanHarjaaTimeout})
        .should('not.exist');
    cy.contains('Näytä päättyneet').click();
    cy.contains('[data-cy="urakat-valitse-urakka"] li', urakkaNimi, {timeout: ladataanHarjaaTimeout})
        .click();
}

function valitseUrakkavuosi(rajain) {
    cy.get(rajain).first().within(() => {
        cy.get('button').click();
        cy.contains('ul.livi-alasvetolista li', urakkavuosi).click();
    });
}

function tehtavarivi(grid) {
    return cy.get(grid)
        .contains('tbody tr', tehtavaNimi)
        .first();
}

describe('Toteutuneiden toimenpiteiden kustannukset', () => {
    before(() => {
        alustaTestidata();
        valitseVesivaylaurakka();
    });

    after(() => {
        poistaTestidata();
    });

    it('Näyttää tehtävä- ja määräluettelon määrän muutokset Tavoitemäärä-sarakkeessa', () => {
        cy.get('[data-cy="tabs-taso1-Suunnittelu"]', {timeout: ladataanHarjaaTimeout})
            .should('be.visible')
            .click();
        cy.get('[data-cy="tabs-taso2-Tehtava- ja maaraluettelo"]')
            .should('be.visible')
            .click();
        cy.get('[data-cy="tehtavat-ja-maarat-grid"]', {timeout: ladataanHarjaaTimeout})
            .should('be.visible');

        valitseUrakkavuosi('div.label-ja-alasveto.hoitokausi div.dropdown');

        cy.get('[data-cy="tehtavat-ja-maarat-grid"]')
            .contains('tbody tr', 'VUOSITARKASTUKSET')
            .find('img[alt="Expander"]')
            .click();
        cy.get('[placeholder="Hae tehtävää..."]').type(tehtavaNimi);
        cy.get('[data-cy="btn-muokkaa-sopimuksen-maaria"]')
            .click();
        tehtavarivi('[data-cy="tehtavat-ja-maarat-grid"]')
            .find('td.muokattava input')
            .clear()
            .type(alkuperainenMaara);

        cy.intercept('POST', '_/tallenna-tehtavat-ja-maarat').as('tallenna-tehtavat-ja-maarat');
        cy.contains('div.painikkeet button', 'Tallenna').click();
        cy.wait('@tallenna-tehtavat-ja-maarat')
            .its('response.statusCode')
            .should('equal', 200);
        tehtavarivi('[data-cy="tehtavat-ja-maarat-grid"]')
            .find('td')
            .eq(3)
            .should('have.text', alkuperainenMaara)
            .next()
            .should('have.text', `+${maaramuutos}`)
            .next()
            .should('have.text', tavoitemaara.replace(',00', ''));

        cy.get('[data-cy="tabs-taso1-Kulut"]', {timeout: ladataanHarjaaTimeout})
            .should('be.visible')
            .click();
        cy.intercept('POST', '_/hae-vhu-toteutuneet-toimenpiteet').as('hae-toteutuneet-toimenpiteet');
        cy.get('[data-cy="tabs-taso2-Toteutuneet toimenpiteet"]')
            .should('be.visible')
            .click();
        cy.wait('@hae-toteutuneet-toimenpiteet')
            .its('response.statusCode')
            .should('equal', 200);
        valitseUrakkavuosi('.urakkavuosi-ja-kuukausi .filtteri');

        cy.contains('h1', 'Toteutuneiden toimenpiteiden kustannukset')
            .should('be.visible');
        cy.get('[data-cy="toteutuneet-toimenpiteet-suodattimet"]')
            .should('be.visible');
        tehtavarivi('[data-cy="toteutuneet-toimenpiteet-grid"]')
            .find('td')
            .eq(1)
            .should('have.text', tavoitemaara);
    });

    it('Hakee VHU-tehtävät ja rajaa tulokset urakkavuoden ja kuukauden mukaan', () => {
        cy.intercept('POST', '_/hae-vhu-toteutuneet-toimenpiteet').as('hae-toteutuneet-toimenpiteet');

        cy.get('[data-cy="toteutuneet-toimenpiteet-grid"]')
            .should('be.visible');
        cy.get('[data-cy="toteutuneet-toimenpiteet-suodattimet"] input[type="radio"]')
            .eq(1)
            .check({force: true});
        cy.wait('@hae-toteutuneet-toimenpiteet')
            .its('response.statusCode')
            .should('equal', 200);
        cy.get('[data-cy="toteutuneet-toimenpiteet-suodattimet"] input[type="radio"]')
            .eq(0)
            .check({force: true});
        cy.wait('@hae-toteutuneet-toimenpiteet')
            .its('response.statusCode')
            .should('equal', 200);

        valitseUrakkavuosi('.urakkavuosi-ja-kuukausi .filtteri');
        cy.get('#kuukausi').click();
        cy.get('.urakkavuosi-ja-kuukausi .filtteri.kuukausi ul.livi-alasvetolista li')
            .first()
            .should('contain.text', 'Kaikki');
        cy.get('.urakkavuosi-ja-kuukausi .filtteri.kuukausi ul.livi-alasvetolista li')
            .eq(1)
            .should('contain.text', 'Lokakuu 2023');
        cy.get('.urakkavuosi-ja-kuukausi .filtteri.kuukausi ul.livi-alasvetolista li')
            .last()
            .should('contain.text', 'Syyskuu 2024');
        cy.get('.urakkavuosi-ja-kuukausi .filtteri.kuukausi ul.livi-alasvetolista li')
            .should('not.contain.text', 'Elokuu 2023');
        cy.get('.urakkavuosi-ja-kuukausi .filtteri.kuukausi')
            .contains('ul.livi-alasvetolista li', 'Kaikki')
            .click();
        cy.wait('@hae-toteutuneet-toimenpiteet').then(({request, response}) => {
            expect(response.statusCode).to.equal(200);
            const payload = JSON.stringify(request.body);
            expect(payload).to.contain('2023-10-01');
            expect(payload).to.contain('2024-09-30');
        });
        cy.get('#kuukausi', {timeout: clickTimeout})
            .should('contain.text', 'Kaikki')
            .click();
        cy.get('.urakkavuosi-ja-kuukausi .filtteri.kuukausi')
            .contains('ul.livi-alasvetolista li', 'Lokakuu 2023')
            .click();
        cy.wait('@hae-toteutuneet-toimenpiteet')
            .its('response.statusCode')
            .should('equal', 200);
        cy.get('#kuukausi')
            .should('contain.text', 'Lokakuu 2023');
    });
});
