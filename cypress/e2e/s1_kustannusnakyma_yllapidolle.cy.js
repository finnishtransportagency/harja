// E2E   
// Ylläpidon Kustannukset
//

import {avaaKustannussuunnittelu} from "../support/kustannussuunnitelmaFns";

let clickTimeout = 6000;
let loaderTimeout = 30000;

export function alustaPaikkausKustannuksetUrakalle(urakkaNimi) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista kiinteähintaiset työt
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM paikkauskustannukset pk " +
            " WHERE " +
            ` pk.urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista kiinteähintaiset työt tulos:", tulos)
            });
    });
}

describe('Kustannusnäkymä toimii paikkaus urakalle', function () {
    // Aina ennen testien ajoa deletoidaan kaikki kustannukset
    before(function () {
        alustaPaikkausKustannuksetUrakalle("Muhoksen päällystysurakka");
    })

    it('Pitäisi löytää ja avata päällystysurakan Kustannukset', function () {
        cy.viewport(1100, 2000);
        cy.intercept('POST', '_/hae-paikkaus-kustannukset').as('kustannukset');
        cy.intercept('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot');
        cy.intercept('POST', '_/paikkauskohteet-urakalle').as('paikkauskohteet');

        // Avaa päänäkymä
        cy.visit("/");

        cy.get('.ladataan-harjaa', {timeout: loaderTimeout}).should('not.exist')

        // Avaa hallintayksikkö
        cy.get('[data-cy="haku-lista-item"]').contains('Pohjois-Suomi').trigger('mousedown');

        // Hyrrää ei pitäisi olla
        cy.get('.ajax-loader', {timeout: loaderTimeout}).should('not.exist');

        // Valitaan urakkatyyppi
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'});

        // Valitse oikea urakka
        cy.contains('[data-cy=urakat-valitse-urakka] button', 'Muhoksen päällystysurakka', {timeout: clickTimeout}).click();

        // Avaa paikkaukset
        cy.get('[data-cy=tabs-taso1-Paikkaukset]').click();

        // Kutsu pitäisi triggeraa, odota että taulukko lataa ja sorttaa
        cy.wait('@paikkauskohteet', {timeout: clickTimeout});

        // Hyrrää ei pitäisi olla
        cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist');
        cy.wait(1000);
        
        // Avaa kustannusten yhteenveto
        cy.get('[data-cy="tabs-taso2-Kustannusten yhteenveto"]').click();

        // Sama homma
        cy.wait('@kustannukset', {timeout: clickTimeout});
        cy.wait('@sanktiot', {timeout: clickTimeout});
        cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist');
        cy.wait(1000);

        // Klikkaa kalenterivuotta
        cy.get('.valittu.overflow-ellipsis').eq(2).click();

        // Valitse 2024 vuosi
        cy.contains('2024').click();
    });

    it('Pitäisi lisätä uusi Arvomuutos ja tallentaa se onnistuneesti', function () {
        cy.viewport(1100, 2000);
        // Klikkaa 'Lisää kustannus'
        cy.get('button.nappi-ensisijainen[type="button"]')
            .contains('span', 'Lisää kustannus')
            .click({force: true});

        // Kustannuksen lomake aukesi
        cy.get('h2.header-yhteiset[data-cy="yllapito-kustannus-lisays"]', {timeout: clickTimeout}).contains('Lisää kustannus');

        // Kustannuksen tyyppi -> Arvonmuutokset
        cy.get('.nappi-alasveto .valittu.overflow-ellipsis').eq(0).click({force: true});
        cy.contains('Arvonmuutokset').click({force: true});

        // Tallenna napin ei pitäisi olla vielä näkyvissä
        cy.get('[data-cy="tallena-yllapito-kustannus"]').should('be.disabled');

        // Kustannus -> 88,060e
        cy.get('.form-group.maara-valinnat.required.sisaltaa-virheen', {timeout: clickTimeout})
            .find('input[type="text"]')
            .type('88060.0').blur();

        // Kaikki syötetty, tallenna napin pitäisi näkyä
        cy.get('[data-cy="tallena-yllapito-kustannus"]', {timeout: clickTimeout})
            .should('be.visible')
            .should('not.be.disabled')

        cy.intercept('POST', '_/hae-paikkaus-kustannukset').as('kustannukset');
        cy.intercept('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot');

        // Tallenna
        cy.get('[data-cy="tallena-yllapito-kustannus"]').click();

        // Kutsu pitäisi triggeraa, odota että taulukko lataa ja sorttaa
        cy.wait('@kustannukset', {timeout: clickTimeout});
        cy.wait('@sanktiot', {timeout: clickTimeout});

        // Viesti onnistumisesta pitäisi näkyä
        cy.contains('Kustannus tallennettu onnistuneesti', {timeout: clickTimeout}).should('be.visible');

        // Lomakkeen pitäisi olla nyt kiinni
        cy.get('body').find('h2.header-yhteiset[data-cy="yllapito-kustannus-lisays"]').should('not.exist');
        cy.wait(1000);
    });


    it('Pitäisi löytää tallennettu arvo taulukosta', function () {
        cy.viewport(1200, 1800);
        // Toisen gridin toinen rivi pitäisi olla (juuri lisätty) "Arvomuutokset", kun taulukko on aakkosissa
        cy.get('.grid').eq(1).find('tr').eq(0).find('td').eq(0).contains('Arvonmuutokset');
        // Arvomuutoksen kolmas sarake eli kustannus pitäisi olla 88 060,00 €
        cy.get('.grid').eq(1).find('tr').eq(0).find('td').eq(2).contains('88 060,00 €');

        // Yhteensä
        cy.get('.grid').eq(1).find('.kustannukset-yhteenveto').eq(0).find('td').eq(2).find('span').contains('337 580,00');
        cy.get('.grid').eq(1).find('.kustannukset-yhteenveto').eq(1).find('td').eq(2).find('span').contains('349 600,00');
    });


    it('Pitäisi lisätä uusi oma selitteinen kustannus ja tallentaa se onnistuneesti', function () {
        cy.viewport(1200, 1800);
        // Klikkaa 'Lisää kustannus'
        cy.get('button.nappi-ensisijainen[type="button"]')
            .contains('span', 'Lisää kustannus')
            .click({force: true});

        // Kustannuksen lomake aukesi
        cy.get('h2.header-yhteiset[data-cy="yllapito-kustannus-lisays"]', {timeout: clickTimeout}).contains('Lisää kustannus');

        // Kustannuksen tyyppi -> Muut kustannukset
        cy.get('.nappi-alasveto .valittu.overflow-ellipsis').eq(0).click({force: true});
        cy.contains('Muut kustannukset').click({force: true});

        // Selitteen pitäisi tulla näkyviin
        cy.contains('Selite', {timeout: clickTimeout});

        // Selite -> Oma cypress selite
        cy.get('label[for*="kustannus-selite"]').should('be.visible')
        cy.get('label[for*="kustannus-selite"] + div input').type('Oma cypress selite').blur();

        // Tallenna napin ei pitäisi olla vielä näkyvissä
        cy.get('[data-cy="tallena-yllapito-kustannus"]').should('be.disabled');

        // Kustannus -> 123456,12 e
        cy.get('label[for*="kustannus-G"]').should('be.visible')
        cy.get('label[for*="kustannus-G"] + span input').type('123456.12').blur();

        // Kaikki syötetty, tallenna napin pitäisi näkyä
        cy.get('[data-cy="tallena-yllapito-kustannus"]', {timeout: clickTimeout})
            .should('be.visible')
            .should('not.be.disabled');

        cy.intercept('POST', '_/hae-paikkaus-kustannukset').as('kustannukset');
        cy.intercept('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot');

        // Tallenna
        cy.get('[data-cy="tallena-yllapito-kustannus"]').click();

        // Kutsu pitäisi triggeraa, odota että taulukko lataa ja sorttaa
        cy.wait('@kustannukset', {timeout: clickTimeout});
        cy.wait('@sanktiot', {timeout: clickTimeout});

        // Viesti onnistumisesta pitäisi näkyä
        cy.contains('Kustannus tallennettu onnistuneesti', {timeout: clickTimeout}).should('be.visible');

        // Lomakkeen pitäisi olla nyt kiinni
        cy.get('body').find('h2.header-yhteiset[data-cy="yllapito-kustannus-lisays"]').should('not.exist');
        cy.wait(1000);
    });


    it('Pitäisi löytää tallennettu arvo taulukosta', function () {
        cy.viewport(1200, 1800);
        // Lisäämä oma selite pitäisi näkyä, ja yhteensä arvon muuttua
        cy.get('.grid').eq(1).find('tr').eq(2).find('td').eq(0).contains('Muut kustannukset');
        cy.get('.grid').eq(1).find('tr').eq(2).find('td').eq(1).contains('Oma cypress selite');
        cy.get('.grid').eq(1).find('tr').eq(2).find('td').eq(2).contains('123 456,12 €');

        // Yhteensä
        cy.get('.grid').eq(1).find('.kustannukset-yhteenveto').eq(0).find('td').eq(2).find('span').contains('461 036,12');
        // Urakka-ajan kustannukset yhteensä
        cy.get('.grid').eq(1).find('.kustannukset-yhteenveto').eq(1).find('td').eq(2).find('span').contains('473 056,12');
    });
}); 
