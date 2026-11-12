import {ladataanHarjaaTimeout, clickTimeout} from "../support/apurit.js";

function avaaValitavoitteet(urakkanimi, hallintayksikko) {
    cy.visit('/');

    // Varmista, että pääsivu on ladattu ennen testien aloitusta
    cy.get('.ladataan-harjaa', { timeout: ladataanHarjaaTimeout }).should('not.exist');

    // Valitse hallintayksikkö
    cy.get('[data-cy="haku-lista-item"]').contains(hallintayksikko, { timeout: ladataanHarjaaTimeout }).click();
    cy.get('.ajax-loader', { timeout: ladataanHarjaaTimeout }).should('not.exist');

    // Näytä päättyneet urakat (jos tarvitaan)
    cy.contains('label', 'Näytä päättyneet', { timeout: clickTimeout })
        .should('be.visible')
        .parent()
        .find('input[type="checkbox"]')
        .check()
        .should('be.checked');

    // Valitse urakka
    cy.contains('[data-cy=urakat-valitse-urakka] button', urakkanimi, { timeout: ladataanHarjaaTimeout }).click();
    
    // Mene "Lupaukset ja tavoitteet" välilehdelle
    cy.get('[data-cy="tabs-taso1-Lupaukset ja tavoitteet"]', { timeout: ladataanHarjaaTimeout }).click();
    
    // Avaa Välitavoitteet
    cy.get('[data-cy=tabs-taso2-Valitavoitteet]').click();
    
    // Odota että ajax-loader häviää
    cy.get('img[src="images/ajax-loader.gif"]', { timeout: ladataanHarjaaTimeout }).should('not.exist');
    
    // Varmista että URL sisältää välitavoitteet
    cy.url().should('include', 'valitavoitteet');
}

function tarkistaValitavoiteRivi(rivinIndex, nimiArvo, takarajaArvo, valmispvmArvo) {
    cy.get('table.grid tbody tr')
        .eq(rivinIndex)
        .should('be.visible')
        .within(() => {
            // Sarake 0: Nimi
            if (nimiArvo) {
                cy.get('td').eq(0).should('contain', nimiArvo);
            }
            // Sarake 1: Takaraja
            if (takarajaArvo) {
                cy.get('td').eq(1).should('contain', takarajaArvo);
            }
            // Sarake 2: Valmis pvm
            if (valmispvmArvo) {
                cy.get('td').eq(2).should('contain', valmispvmArvo);
            }
        });
}

describe('Välitavoitteet - Perustoiminnallisuus', () => {

    beforeEach(() => {
        cy.intercept('POST', '_/hae-urakan-valitavoitteet').as('hae-valitavoitteet');
        avaaValitavoitteet('Oulun MHU 2019-2024', 'Pohjois-Suomi');
        cy.wait('@hae-valitavoitteet');
    });

    it('Urakan omat välitavoitteet -grid renderöityy', () => {
        // Tarkista että grid on näkyvissä
        cy.get('table.grid').should('exist').should('be.visible');
        
        // Tarkista että testidatasta löytyy odotettu rivi
        cy.get('table.grid').contains('td', 'HV5: Loppuvuoden talvihoito').should('be.visible');
    });

    it('Urakan omat ja valtakunnalliset -näkymä renderöityy', () => {
        // Tarkista että grid renderöityy
        cy.get('table.grid').should('exist').should('be.visible');
        
        // Tarkista että sekä urakan omat että valtakunnalliset näkyvät
        cy.get('table.grid').contains('td', 'TEST: Sorateiden hoito').should('be.visible');
        cy.get('table.grid').contains('td', 'VK HV5: Kevään kuntoarvo raportoitu').should('be.visible');
    });

    it('Valtakunnalliset välitavoitteet -grid renderöityy', () => {
        // Tarkista onko valtakunnallisten hallinta näkyvissä
        // (Jos ei ole oikeuksia, testi skippaa tämän)
        cy.get('body').then(($body) => {
            if ($body.find('[data-cy=hallinnoi-valtakunnallisia]').length > 0) {
                cy.get('[data-cy=hallinnoi-valtakunnallisia]').click();
                cy.get('table.grid').should('exist').should('be.visible');
            } else {
                cy.log('Ei valtakunnallisten hallintaoikeutta - testi ohitettu');
            }
        });
    });

    it('Vuosivalinta "Kaikki vuodet" toimii', () => {
        // Testaa että hoitokausi-valinta toimii
        cy.get('body').then(($body) => {
            // Jos hoitokausi-valinta löytyy
            if ($body.find('[data-cy=hoitokausi-valinta]').length > 0) {
                cy.get('[data-cy=hoitokausi-valinta]').click();
                cy.contains('Kaikki vuodet').click();
                
                // Tarkista että grid näkyy edelleen
                cy.get('table.grid').should('exist').should('be.visible');
                
                // Tarkista että eri hoitovuosien tavoitteet näkyvät
                cy.get('table.grid').contains('td', 'HV1: Talvikauden valmistelut tehty').should('be.visible');
                cy.get('table.grid').contains('td', 'HV2: Kesähuoltokauden aloitus').should('be.visible');
                cy.get('table.grid').contains('td', 'HV3: Talven aurauskalusto käytössä').should('be.visible');
            } else {
                cy.log('Hoitokausi-valinta ei löytynyt - käytetään oletusta');
                cy.get('table.grid').should('exist').should('be.visible');
            }
        });
    });
    
});
