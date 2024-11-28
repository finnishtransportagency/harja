// E2E
// Toteumat / tehtävät - Ensimmäinen kevyt versio
//

let urakanNimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)';
let clickTimeout = 6000;
let loaderTimeout = 30000;


describe('Toteumat / Tehtävät sivu toimii', function ()
{
    it('Avataan Toteumat / Tehtävät listaus', function ()
    {
        cy.viewport(1100, 2000)
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry Toteumat päätabille
        cy.get('[data-cy=tabs-taso1-Toteumat]').click()

        // Siirry alatabille
        cy.get('[data-cy=tabs-taso2-Tehtavat]').click()
        cy.contains('Määrämitattavat').should('exist')
    });

    it('Avataan Toteumat / Tehtävät listaus', function ()
    {
        // Tarkistetaan, että ollaan oikealla sivulla
        cy.contains('Määrämitattavat').should('exist')
        cy.contains('Lisätyöt').should('exist')
        cy.contains('Lisää toteuma').should('exist')

        // Lisätään uusi toteuma - Avataan näkymä
        cy.contains('Lisää toteuma').click()

        // Valitaan Toimenpide
        cy.get('label[for=toimenpide] + div').valinnatValitse({valinta: '4 PÄÄLLYSTEIDEN PAIKKAUS'})
        cy.wait(100)

        // Valitse tehtävä
        cy.get('label[for=tehtava-0] + div').valinnatValitse({valinta: 'AB-paikkaus levittäjällä'})
        cy.wait(100)

        // Aseta määrä
        cy.get('label[for=toteutunut-maara-0] + span > input').type('{selectall}15')

        // Aseta tieosoite
        cy.get('input.tierekisteri.input-default.tr-numero').type('{selectall}4')
        cy.get('input.tierekisteri.input-default.tr-alkuosa').type('{selectall}404')
        cy.get('input.tierekisteri.input-default.tr-alkuetaisyys').type('{selectall}0')
        cy.get('input.tierekisteri.input-default.tr-loppuosa').type('{selectall}420')
        cy.get('input.tierekisteri.input-default.tr-loppuetaisyys').type('{selectall}1000')
        // Tallenna
        cy.get('[data-cy="Tallenna-toteuma-nappi"]').click();
        // Toteuma tallennettu teksti tulee näkyviin, eli tallennus on onnistunut
        cy.contains('Toteuma tallennettu', { timeout: clickTimeout }).should('be.visible');

    });

});
