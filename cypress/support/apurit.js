// --- Lomakkeen apufunktiot ---
// Etsi form-group labelin täsmätekstillä (ei osittaisosumaa)
function haeFormGroupOtsikolla(otsikko) {
    return cy.get(SP).find('label').then(($labels) => {
        const loytynyt = [...$labels].find((el) => el.textContent.trim() === otsikko);
        expect(loytynyt, `Labelia ei löytynyt otsikolla: ${otsikko}`).to.exist;
        return cy.wrap(loytynyt).closest('.form-group');
    });
}

export const ladataanHarjaaTimeout = 30000;
export const clickTimeout = 4000;
export const pageloadTimeout = 30000;

// Sivupaneelin juuri, jonka sisällä lomake renderöidään
export const SP = '.ei-sulje-sivupaneelia';

export function kuluvaHoitokausiAlkuvuosi(offset = 0) {
    let pvm = new Date();
    return (pvm.getMonth() >= 9 ? pvm.getFullYear() : pvm.getFullYear() - 1) + offset;
}


export function avaaHarjaTimeoutilla() {
    // Varmista, että pääsivu on ladattu ennen testien aloitusta
    cy.visit("/");
    cy.get('.ladataan-harjaa', {timeout: ladataanHarjaaTimeout}).should('not.exist')
}

export function muokkaaTarjousRiviaArvo(taulukonDataCy, rivinTunniste, sarakeIndex, uusiArvo) {
    cy.get(`[data-cy=${taulukonDataCy}]`)
        .should('be.visible')
        .contains('tbody tr', rivinTunniste)
        .find('input')
        .eq(sarakeIndex)
        .should('be.visible')
        .clear()
        .type(uusiArvo)
}

// --- Arvonvähennysten ja sanktioiden apufunktiot ---

// Helper: siivoa testidatan sanktiot kannasta
export function siivoaTietokannastaSanktiot(kohde) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM sanktio WHERE suorasanktio = true AND id IN (SELECT s.id FROM sanktio s JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id WHERE lp.kohde = '" + kohde + "');\"");
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM laatupoikkeama WHERE kohde = '" + kohde + "';\"");
    });
}

// Helper: navigoi sanktiot ja bonukset -näkymään
export function avaaSanktiotJaBonukset(urakkaNimi, urakkaEvk) {
    cy.intercept('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot')

    cy.viewport(1400, 1400)
    cy.visit("/")

    cy.contains('.haku-lista-item', urakkaEvk).click()
    cy.get('.ajax-loader', {timeout: pageloadTimeout}).should('not.exist')
    cy.get('[data-cy=murupolku-urakkatyyppi]').find('button').click()
    cy.wait(250); // Pudotusvalikko re-renderöityy avattaessa
    cy.get('[data-cy=murupolku-urakkatyyppi]').contains('ul li a', 'Hoito').click({force: true})
    cy.contains('Näytä päättyneet').click();
    cy.wait(250); // Toimii varmemmin, kun ei ole niin kiire
    cy.contains('[data-cy=urakat-valitse-urakka] li', urakkaNimi, {timeout: pageloadTimeout}).click()
    cy.get('[data-cy=tabs-taso1-Laadunseuranta]').click()
    cy.get('[data-cy="tabs-taso2-Sanktiot ja bonukset"]').click()
    cy.wait('@sanktiot', {timeout: clickTimeout})
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
}

// Avaa uusi arvonvähennyslomake sivupaneeliin
export function avaaUusiArvonvahennys() {
    cy.contains('.lisaa-nappi', 'Lisää uusi').click();
    cy.get(SP, {timeout: clickTimeout}).should('be.visible');
    // Sivupaneeli avautuu oletuksena sanktiolomakkeelle -> valitaan "Arvonvähennys"
    cy.get(SP).contains('label', 'Arvonvähennys').click();
}

// Kirjoita :text-tyyppiseen kenttään (textarea) otsikon perusteella
export function kirjoitaSivupaneelissaTekstikenttaan(otsikko, teksti) {
    cy.get(SP).contains('.form-group', otsikko).should('exist');
    cy.get(SP).contains('.form-group', otsikko).find('textarea').first().clear();
    cy.get(SP).contains('.form-group', otsikko).find('textarea').first().type(teksti);
}

// Kirjoita :euro-/:string-tyyppiseen input-kenttään otsikon perusteella
export function kirjoitaSivupaneelissaInputkenttaan(otsikko, arvo) {
    cy.get(SP).contains('.form-group', otsikko).find('input').first().clear();
    cy.get(SP).contains('.form-group', otsikko).find('input').first().type(arvo);
}

// Valitse alasvetovalikosta arvo näkyvän tekstin perusteella (otsikon perusteella löydetty kenttä)
export function valitseSivupaneelissaAlasvetoarvo(otsikko, arvoTeksti) {
    haeFormGroupOtsikolla(otsikko).within(() => {
        cy.get('button').first().click();
    });
    cy.wait(250); // Lista re-renderöityy, annetaan sen asettua
    haeFormGroupOtsikolla(otsikko).contains('ul li a', arvoTeksti).click({force: true});
}

// Valitse sivupaneelissa alasvetovalikon ensimmäinen oikea vaihtoehto (kun arvo on datariippuvainen)
export function valitseSivupaneelissaEnsimmainenAlasvetoarvo(otsikko) {
    cy.get(SP).contains('.form-group', otsikko).within(() => {
        cy.get('button').first().click();
    });
    cy.wait(250);
    cy.get(SP).contains('.form-group', otsikko).find('ul li a').first().click({force: true});
}

// Valitse sivupaneelissa alasvetovalikon ensimmäinen oikea vaihtoehto (kun arvo on datariippuvainen)
export function valitseLaskutusyhteenvedollaEnsimmainenAlasvetoarvo(otsikko) {
    cy.get('.laskutusyhteenveto').contains('label', otsikko).parent().within(() => {
        cy.get('button').first().click();
    });
    cy.wait(250);
    cy.get('.laskutusyhteenveto').contains('label', otsikko).parent().find('ul li a').first().click({force: true});
}

// Valitse radio-painike sen näkyvän tekstin perusteella
export function valitseSivupaneelissaRadio(teksti) {
    // Vayla-radio renderöityy muodossa: input.vayla-radio + label[for=input-id].
    // Valitaan varsinainen radio-input labelin for-attribuutin kautta, koska pelkkä
    // labelin klikkaus ei ole tässä komponentissa aina luotettava Cypressissä.
    cy.get(SP).contains('label', teksti).then(($label) => {
        let inputId = $label.attr('for');
        expect(inputId, 'radio-labelilla pitää olla for-attribuutti').to.exist;

        cy.get(SP).find('input[type="radio"][id="' + inputId + '"]').as('radioKentta');
        cy.get('@radioKentta').check({force: true});
        cy.get('@radioKentta').should('be.checked');
    });
}

// Valitse päivämäärä :pvm-kenttään otsikon perusteella
export function valitseSivupaneelissaPvm(otsikko, pvm) {
    // Päivämääräkentät ovat lomakkeella ryhmässä, joten haetaan input nimenomaan labelin kautta.
    // Kenttä on :pvm, joten arvo annetaan muodossa "p.k.vvvv" ilman kellonaikaa.
    cy.get(SP).find('label').contains(otsikko)
        .parents('.form-group').first()
        .find('input').first()
        .as('pvmKentta');

    cy.get('@pvmKentta').clear().type(pvm);
    cy.get('@pvmKentta').should('have.value', pvm);

    // Päivämääräpopup jää muuten helposti auki seuraavien kenttien päälle.
    // Suljetaan se siirtämällä fokus ja klikkaamalla labelia
    cy.get(SP).find('label').contains(otsikko).click({force: true});
}

// Tallenna arvonvähennyslomake ja odota tallennuskutsua
export function tallennaArvonvahennyslomake() {
    cy.intercept('POST', '_/tallenna-suorasanktio').as('tallennaSanktio');
    cy.get(SP).contains('button', 'Tallenna').click();
    cy.wait('@tallennaSanktio', {timeout: clickTimeout}).then(({response}) => {
        const vastaus = JSON.stringify(response && response.body);
        expect(response, `Tallennuspyynnön vastaus puuttuu. Body: ${vastaus}`).to.exist;
        expect(response.statusCode, `Tallennus epäonnistui. Body: ${vastaus}`).to.be.within(200, 299);
    });
    cy.get('.toast-viesti.onnistunut', {timeout: clickTimeout}).should('be.visible')
        .and('contain.text', 'Sanktion tallennus onnistui')
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist');
}

// Tallenna suorasanktiolomake ja odota tallennuskutsua
export function tallennaSuorasanktiolomake() {
    cy.intercept('POST', '_/tallenna-suorasanktio').as('tallennaSanktio');
    cy.get(SP).contains('button', 'Tallenna').click();
    cy.wait('@tallennaSanktio', {timeout: clickTimeout}).then(({response}) => {
        const vastaus = JSON.stringify(response && response.body);
        expect(response, `Tallennuspyynnön vastaus puuttuu. Body: ${vastaus}`).to.exist;
        expect(response.statusCode, `Tallennus epäonnistui. Body: ${vastaus}`).to.be.within(200, 299);
    });
    cy.get('.toast-viesti.onnistunut', {timeout: clickTimeout}).should('be.visible')
        .and('contain.text', 'Sanktion tallennus onnistui')
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist');
}

// Avaa listalta tallennettu arvonvähennys (lukutilaan) kuvauksen perusteella
export function avaaSivupaneelissaTallennettu(kuvaus) {
    cy.get('.sanktiot').contains('td', kuvaus, {timeout: clickTimeout}).click();
    cy.get(SP, {timeout: clickTimeout}).should('be.visible');
}

// Siirry lukutilasta muokkaustilaan
export function siirrySivupaneelissaMuokkaustilaan() {
    cy.get(SP).contains('button', 'Muokkaa').click();
}
