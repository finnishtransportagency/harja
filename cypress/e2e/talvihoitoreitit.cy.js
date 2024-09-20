// E2E
// Talvihoitoreitit
//

let clickTimeout = 6000;
let loaderTimeout = 30000;


describe('Talvihoitoreitit näkymä aukeaa', function ()
{
    it('Pitäisi löytää ja avata Talvihoitoreitit näkymä', function ()
    {
        cy.viewport(1100, 2000);
        cy.intercept('POST', '_/hae-urakan-talvihoitoreitit').as('hae-talvihoitoreitit');
        // Avaa päänäkymä
        cy.visit("/");

        // Avaa hallintayksikkö
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa').click();

        // Hyrrää ei pitäisi olla
        cy.get('.ajax-loader', {timeout: loaderTimeout}).should('not.exist');

        // Valitaan urakkatyyppi
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'});

        // Valitse oikea urakka
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Iin MHU 2021-2026', {timeout: clickTimeout}).click();

        // Avaa Laadunseuranta
        cy.get('[data-cy=tabs-taso1-Laadunseuranta]').click();
        // Avaa Talvihoireititys
        cy.get('[data-cy=tabs-taso2-Talvihoitoreititys]').click();

        // Odotellaan, että saadaan haettua kaikki talvihoitoreitit
        cy.wait('@hae-talvihoitoreitit', {timeout: clickTimeout});

        // Hyrrää ei pitäisi olla
        cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist');
        cy.wait(1000);

        // Otsikko löytyy
        cy.contains('Talvihoitoreititys');
    });


    it('Löydetään olennaiset tiedot sivulta', function ()
    {
        cy.contains('Reitti 1');
        cy.contains('Reitti 2');
        cy.contains('Tuo kohteet excelistä');
        cy.contains('Lataa Excel-pohja');

        // Avataan Reitti 1
        cy.get('[data-cy="avaa-reitti-Reitti 1"]').click();
        // Varmista, että sen alta löytyy grid
        cy.get('table.grid').eq(0).find('tr').eq(0).find('th').eq(0).contains('Tie');
        cy.get('table.grid').eq(0).find('tr').eq(0).find('th').eq(1).contains('Tieosoite');
        cy.get('table.grid').eq(0).find('tr').eq(0).find('th').eq(2).contains('Hoitoluokka');
        cy.get('table.grid').eq(0).find('tr').eq(0).find('th').eq(3).contains('Suunniteltu pituus (km)');
        cy.get('table.grid').eq(0).find('tr').eq(0).find('th').eq(4).contains('Laskettu pituus (km)');

        // Varmistetaan, että Reitti 1:sen alta löytyy oikeat tiedot
        cy.get('table.grid').eq(0).find('tr').eq(1).find('td').eq(0).contains('4');
        cy.get('table.grid').eq(0).find('tr').eq(1).find('td').eq(1).contains('4 - 414/1 - 420/1000');
        cy.get('table.grid').eq(0).find('tr').eq(1).find('td').eq(2).contains('Is');
        cy.get('table.grid').eq(0).find('tr').eq(1).find('td').eq(3).contains('0,05');
        cy.get('table.grid').eq(0).find('tr').eq(1).find('td').eq(4).contains('28,13');

        cy.get('table.grid').eq(0).find('tr').eq(2).find('td').eq(0).contains('4');
        cy.get('table.grid').eq(0).find('tr').eq(2).find('td').eq(1).contains('4 - 404/1 - 408/1000');
        cy.get('table.grid').eq(0).find('tr').eq(2).find('td').eq(2).contains('Ib');
        cy.get('table.grid').eq(0).find('tr').eq(2).find('td').eq(3).contains('0,01');
        cy.get('table.grid').eq(0).find('tr').eq(2).find('td').eq(4).contains('21,41');
    });

});
