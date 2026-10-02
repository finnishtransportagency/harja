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
export const testiurakkaMhu23 = "Raahen MHU 2023-2028";
export const evkLappi = "Lappi";
export const evkPohjoisSuomi = "Pohjois-Suomi";

// Siivoa testidatan sanktiot kannasta laatupoikkeaman kohteen perusteella
export function siivoaSanktiotKannasta(kohde) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM sanktio WHERE suorasanktio = true AND id IN (SELECT s.id FROM sanktio s JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id WHERE lp.kohde = '" + kohde + "');\"");
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM laatupoikkeama WHERE kohde = '" + kohde + "';\"");
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
export function avaaSanktiotJaBonuksetNakyma(urakkaNimi, urakkaEvk) {
    cy.intercept('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot')

    cy.visit("/")

    cy.contains('.haku-lista-item', urakkaEvk).click()
    cy.get('.ajax-loader', {timeout: pageloadTimeout}).should('not.exist')
    cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
    cy.contains('Näytä päättyneet').click();
    cy.wait(250); // Toimii varmemmin, kun ei ole niin kiire
    cy.contains('[data-cy=urakat-valitse-urakka] li', urakkaNimi, {timeout: pageloadTimeout}).click()
    cy.get('[data-cy=tabs-taso1-Laadunseuranta]').click()
    cy.get('[data-cy="tabs-taso2-Sanktiot ja bonukset"]').click()
    cy.wait('@sanktiot', {timeout: clickTimeout})
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
}

