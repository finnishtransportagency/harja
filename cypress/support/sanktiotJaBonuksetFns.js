// Yhteiset apurit "Sanktiot ja bonukset" -näkymän testeille
// (s2_sanktiot.cy.js ja s2_bonukset.cy.js).
//
// HUOM! apurit.js sisältää oman avaaSanktiotJaBonukset-funktion arvonvähennystesteille.
// Tässä on erillinen toteutus, koska nämä testit asettavat viewportin itse
// ja käyttävät valinnatValitse-komentoa urakkatyypin valintaan.

export const clickTimeout = 12000;
export const pageloadTimeout = 30000;

// Testiurakat ja niiden elinkaaren vaiheet (ELY / "evk")
export const testiurakkaMhu25 = "Rovaniemen MHU testiurakka (1. hoitovuosi)";
export const testiurakkaMhu24 = "POP MHU Suomussalmi 2024-2029";
export const testiurakkaMhu19 = "Oulun MHU 2019-2024";
export const testiurakkaMhu26 = "Sodankylän MHU 2026-2031";
export const testiurakkaMhu23 = "Raahen MHU 2023-2028";
export const evkLappi = "Lappi";
export const evkPohjoisSuomi = "Pohjois-Suomi";

// Siivoa testidatan sanktiot kannasta laatupoikkeaman kohteen tai perustelun perusteella
export function siivoaSanktiotKannasta(kohde, perustelu = null) {
    const ehto = kohde
        ? "lp.kohde = '" + kohde + "'"
        : "lp.perustelu = '" + perustelu + "'";
    cy.terminaaliKomento().then((terminaaliKomento) => {
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM sanktio WHERE suorasanktio = true AND id IN (SELECT s.id FROM sanktio s JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id WHERE " + ehto + ");\"");
        // laatupoikkeama_liite viittaa laatupoikkeamaan ilman ON DELETE CASCADE -sääntöä, joten linkit poistetaan ensin
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM laatupoikkeama_liite WHERE laatupoikkeama IN (SELECT lp.id FROM laatupoikkeama lp WHERE " + ehto + ");\"");
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM laatupoikkeama lp WHERE " + ehto + ";\"");
    });
}

// Siivoa testidatan bonukset kannasta (erilliskustannus-taulusta)
export function siivoaBonuksetKannasta(lisatieto) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM erilliskustannus WHERE lisatieto = '" + lisatieto + "';\"");
    });
}

// Navigoi urakan Laadunseuranta > Sanktiot ja bonukset -näkymään
export function avaaSanktiotJaBonuksetNakyma(urakkaNimi, urakkaEvk, urakkatyyppi = 'Hoito') {
    cy.intercept('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot')
    cy.intercept('POST', '_/hae-urakan-sanktio-konfiguraatio').as('sanktiokonfiguraatio')

    cy.visit("/")

    cy.get('.ladataan-harjaa', {timeout: pageloadTimeout}).should('not.exist')
    cy.get('.haku-lista-item', {timeout: pageloadTimeout})
        .filter((_, element) => element.textContent.trim().endsWith(urakkaEvk))
        .should('have.length', 1)
        .click()
    cy.get('.valittu-hallintayksikko', {timeout: pageloadTimeout})
        .should('have.text', urakkaEvk + ' ')
    cy.get('img[src="images/ajax-loader.gif"]', {timeout: pageloadTimeout}).should('not.exist')
    cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: urakkatyyppi})
    cy.contains('label', 'Näytä päättyneet')
        .parent()
        .find('input[type="checkbox"]')
        .check()
        .should('be.checked');
    cy.get('img[src="images/ajax-loader.gif"]', {timeout: pageloadTimeout}).should('not.exist')
    cy.contains('[data-cy=urakat-valitse-urakka] li', urakkaNimi, {timeout: pageloadTimeout}).click()
    // Ylläpitourakoilla välilehden nimi on "Sakot ja bonukset"
    const sanktiotTabNimi = urakkatyyppi === 'Päällystys' ? 'Sakot ja bonukset' : 'Sanktiot ja bonukset';
    cy.get('[data-cy=tabs-taso1-Laadunseuranta]').click()
    cy.get(`[data-cy="tabs-taso2-${sanktiotTabNimi}"]`).click()
    cy.wait('@sanktiot', {timeout: clickTimeout})
        .its('response.statusCode').should('be.within', 200, 299)
    cy.wait('@sanktiokonfiguraatio', {timeout: clickTimeout})
        .its('response.statusCode').should('be.within', 200, 299)
    cy.get('img[src="images/ajax-loader.gif"]', {timeout: clickTimeout}).should('not.exist')
}
