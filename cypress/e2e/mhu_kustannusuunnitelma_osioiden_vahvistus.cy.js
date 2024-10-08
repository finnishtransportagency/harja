import * as ks from "../support/kustannussuunnitelmaFns";
import {avaaKustannussuunnittelu} from "../support/kustannussuunnitelmaFns";

// Täytetään ajax kutsun vastauksen perusteella
const indeksit = [];

function alustaIvalonUrakka() {
    ks.alustaKanta('Ivalon MHU testiurakka (uusi)');
}

describe('Osion vahvistaminen', function () {

    before(function () {
        alustaIvalonUrakka();
        avaaKustannussuunnittelu('Ivalon MHU testiurakka (uusi)', 'Lappi', indeksit);
    });

    //TODO: Tee testi, jossa osiota muokataan vahvistamisen jälkeen.
    beforeEach(function () {
        cy.intercept('POST', '_/vahvista-kustannussuunnitelman-osa-vuodella')
            .as('vahvista-kustannussuunnitelman-osa-vuodella');

        cy.intercept('POST', '_/kumoa-suunnitelman-osan-vahvistus-hoitovuodelle')
            .as('kumoa-suunnitelman-osan-vahvistus-hoitovuodelle');
    });

    it('Testaa Hankintakustannukset osion vahvistaminen', function () {
        // Klikkaa osion vahvistusnappulaa
        cy.get('[data-cy="vahvista-osio-hankintakustannukset"]')
            .click()
            .find('[data-cy="vahvista-osio-btn"]')
            .click();

        // Pitäisi tulla esiin onnistumis-alert, joka piilotetaan klikkaamalla.
        cy.get('.modal')
            .find('.alert-success')
            .click();


        // Varmista, että tallennuskyselyt menevät läpi
        cy.wait('@vahvista-kustannussuunnitelman-osa-vuodella')


        // -- Palauta UI-valinnat ennalleen --

        // Pienennä osion vahvistuslaatikko
        cy.get('[data-cy="vahvista-osio-hankintakustannukset"]')
            .click();

        // -- Arvojen tarkastus --
        ks.testaaTilayhteenveto(1, 'Suunnitellut hankinnat', true);
    });

    it('Testaa Erillishankinnat osion vahvistaminen', function () {
        // Klikkaa osion vahvistusnappulaa
        cy.get('[data-cy="vahvista-osio-erillishankinnat"]')
            .click()
            .find('[data-cy="vahvista-osio-btn"]')
            .click();

        // Pitäisi tulla esiin onnistumis-alert, joka piilotetaan klikkaamalla.
        cy.get('.modal')
            .find('.alert-success')
            .click();

        // Varmista, että tallennuskyselyt menevät läpi
        cy.wait('@vahvista-kustannussuunnitelman-osa-vuodella')


        // -- Palauta UI-valinnat ennalleen --

        // Pienennä osion vahvistuslaatikko
        cy.get('[data-cy="vahvista-osio-erillishankinnat"]')
            .click();


        // -- Arvojen tarkastus --
        ks.testaaTilayhteenveto(1, 'Erillishankinnat', true);
    });

    it('Kumoa Erillishankinnat osion vahvistus', function () {
        // Klikkaa osion vahvistuksen kumoamis nappia
        cy.get('[data-cy="vahvista-osio-erillishankinnat"]')
            .click()
            .find('[data-cy="kumoa-osion-vahvistus-btn"]')
            .click();

        // Pitäisi tulla esiin onnistumis-alert, joka piilotetaan klikkaamalla.
        cy.get('.modal')
            .find('.alert-success')
            .click();

        // Varmista, että tallennuskyselyt menevät läpi
        cy.wait('@kumoa-suunnitelman-osan-vahvistus-hoitovuodelle')


        // -- Palauta UI-valinnat ennalleen --

        // Pienennä osion vahvistuslaatikko
        cy.get('[data-cy="vahvista-osio-erillishankinnat"]')
            .click();


        // -- Arvojen tarkastus --
        ks.testaaTilayhteenveto(1, 'Erillishankinnat', false);
    });

    describe('Testaa pääyhteenvedon osioiden tilat', function () {
        it('Testataan onko Suunnitellut hankinnat osio vahvistettu 1. hoitovuodelle', function () {
            ks.testaaTilayhteenveto(1, 'Suunnitellut hankinnat', true);
        });

        // Testataan tallentuiko vahvistuksen kumoaminen tietokantaan asti.
        it('Testataan, että Erillishankinnat osio ei ole enää vahvistettu.', function () {
            ks.testaaTilayhteenveto(1, 'Erillishankinnat', false);
        });
    });

});
