// Asetuksia
let clickTimeout = 12000;
let pageloadTimeout = 30000;
let testiKohdeNimi = "CY-laatupoikkeama-testi";
let testiKohdeNimi2 = "CY-laatupoikkeama-testi2";
let testiurakka1 = "Rovaniemen MHU testiurakka (1. hoitovuosi)";
let testiurakka2 = "POP MHU Suomussalmi 2024-2029";
let evk1 = "Lappi";
let evk2 = "Pohjois-Suomi";

// Helper: siivoa testidatan laatupoikkeamat kannasta
function siivoaKanta(kohde) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista sanktiot, jotka liittyvät testin luomaan laatupoikkeamaan (urakan kautta rajattuna)
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM sanktio WHERE laatupoikkeama IN (SELECT id FROM laatupoikkeama WHERE kuvaus = '" + kohde + "');\"");

        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM laatupoikkeama WHERE kuvaus = '" + kohde + "';\"");
    });
}

// Helper: navigoi laatupoikkeamat-näkymään
let avaaLaatupoikkeamat = function (urakkaNimi, evk) {
    cy.intercept('POST', '_/hae-urakan-laatupoikkeamat').as('laatupoikkeamat')

    cy.visit("/")

    cy.contains('.haku-lista-item', evk).click()
    cy.get('.ajax-loader', {timeout: pageloadTimeout}).should('not.exist')
    cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
    cy.contains('Näytä päättyneet').click();
    cy.contains('[data-cy=urakat-valitse-urakka] li', urakkaNimi, {timeout: clickTimeout}).click()
    cy.get('[data-cy=tabs-taso1-Laadunseuranta]').click()
    cy.get('[data-cy=tabs-taso2-Laatupoikkeamat]').click()
    cy.wait('@laatupoikkeamat', {timeout: clickTimeout})
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
}

describe('Laatupoikkeamat latautuu oikein', function () {
    before(function () {
        siivoaKanta(testiKohdeNimi);
        siivoaKanta(testiKohdeNimi2);
    });

    it('Mene laatupoikkeamat-välilehdelle palvelun juuresta', function () {
        cy.viewport(1100, 2000)
        avaaLaatupoikkeamat(testiurakka1, evk1)

        // Varmistetaan, että otsikko näkyy
        cy.contains('h1', 'Laatupoikkeamat').should('be.visible')
    })

    it('Lisää uusi laatupoikkeama MHU25 urakalle', function () {
        cy.viewport(1100, 2000)
        avaaLaatupoikkeamat(testiurakka1, evk1)

        cy.intercept('POST', '_/tallenna-laatupoikkeama').as('tallenna')

        // Klikkaa "Uusi laatupoikkeama" -nappia
        cy.contains('Uusi laatupoikkeama').click()

        // Täytetään lomakkeen kentät
        // Havaittu (pvm-aika)
        cy.get('label').contains('Havaittu').parent().parent().parent().find('input').first().clear().type('15.10.2025 08:00')

        // Kohde
        cy.get('label').contains('Kohde').parent().parent().parent().find('input').first().clear().type('Testitie')

        // Kuvaus
        cy.get('textarea').first().clear().type(testiKohdeNimi)

        // Tallenna
        cy.contains('Tallenna laatupoikkeama').click()
        cy.wait('@tallenna', {timeout: 60000})

        // Tarkista, että tallennus onnistui ja palattiin listaan
        cy.contains('h1', 'Laatupoikkeamat', {timeout: clickTimeout}).should('be.visible')
        cy.contains(testiKohdeNimi).should('exist')
    })

    it('Avaa laatupoikkeama listasta - MHU25 urakalle', function () {
        cy.viewport(1100, 2000)
        avaaLaatupoikkeamat(testiurakka1, evk1)
        let perustelu = "Joku perustelu"

        // Klikataan luotua laatupoikkeamaa gridissä
        cy.contains(testiKohdeNimi).click()

        // Varmistetaan lomakkeen avautuminen
        cy.contains('Laatupoikkeaman tiedot').should('be.visible')
        cy.contains('Takaisin laatupoikkeamaluetteloon').should('be.visible')

        // Lisää sanktio
        // Käsittelyn pvm
        //cy.get('label[for=filtteri-aikavali] + div .pvm-kentta > .pvm-ikoni > .input-default').first().focus().type("01.01.2021" ).clear().type("01.01.2021" );
        cy.get('label').contains('Käsittelyn pvm').parent().parent().parent().find('input').first().clear().type("01.01.2026");
        cy.get('label').contains('Käsittelyn pvm').parent().parent().parent().find('input').eq(1).clear().type("10.12");
        // Käsitelty
        cy.get('label[for*=kasittelytapa] + div').valinnatValitse({valinta: 'Puhelimitse'});
        // Päätös
        cy.get('label[for*=paatos-paatos] + div').valinnatValitse({valinta: 'Sanktio'});
        // Varmista, että errorilaatikko on näkyvissä
        cy.get('.info-laatikko.varoitus').contains("Sanktiota ei voida lisätä, sillä osa laatupoikkeaman pakollisista tiedoista puuttuu").should('be.visible');
        // Perustelu
        cy.get('label').contains('Perustelu').parent().parent().parent().find('textarea').first().clear().type(perustelu);

        // Lisää uusi sanktio
        cy.get('button').contains("Lisää uusi").click();
        // Varmistetaan, että Indeksi-kenttä ei näy
        cy.contains('label', 'Indeksi').should('not.exist')
        // Sivupaaneelista tyyppi
        cy.get('label[for*=tyyppi] + div').valinnatValitse({valinta: 'Talvihoito, päätiet'});
        // Sivupaneelista sanktion suuruus
        cy.get('label').contains('Sanktion suuruus').parent().parent().parent().find('input').first().clear().type(1234);
        // Tallenna sanktio
        cy.get('button').contains('Tallenna').click()
        // Tallenna laatupoikkeama
        cy.get('button').contains('Tallenna ja lukitse laatupoikkeama').click();
        cy.contains('Tallenna ja lukitse laatupoikkeama?').should('be.visible');
        cy.get('.modal button').contains('Tallenna ja lukitse').click();
        cy.contains('h1', 'Laatupoikkeamat').should('be.visible')
    })

    it('Lisää uusi laatupoikkeama MHU24 urakalle', function () {
        cy.viewport(1100, 2000)
        avaaLaatupoikkeamat(testiurakka2, evk2)

        cy.intercept('POST', '_/tallenna-laatupoikkeama').as('tallenna')

        // Klikkaa "Uusi laatupoikkeama" -nappia
        cy.contains('Uusi laatupoikkeama').click()

        // Täytetään lomakkeen kentät
        // Havaittu (pvm-aika)
        cy.get('label').contains('Havaittu').parent().parent().parent().find('input').first().clear().type('15.10.2025 08:00')

        // Kohde
        cy.get('label').contains('Kohde').parent().parent().parent().find('input').first().clear().type('Testitie')

        // Kuvaus
        cy.get('textarea').first().clear().type(testiKohdeNimi2)

        // Tallenna
        cy.contains('Tallenna laatupoikkeama').click()
        cy.wait('@tallenna', {timeout: 60000})

        // Tarkista, että tallennus onnistui ja palattiin listaan
        cy.contains('h1', 'Laatupoikkeamat', {timeout: clickTimeout}).should('be.visible')
        cy.contains(testiKohdeNimi2).should('exist')
    })

    it('Avaa laatupoikkeama listasta - MHU24 urakalle', function () {
        cy.viewport(1100, 2000)
        avaaLaatupoikkeamat(testiurakka2, evk2)
        let perustelu = "Joku perustelu"

        // Klikataan luotua laatupoikkeamaa gridissä
        cy.contains(testiKohdeNimi2).click()

        // Varmistetaan lomakkeen avautuminen
        cy.contains('Laatupoikkeaman tiedot').should('be.visible')
        cy.contains('Takaisin laatupoikkeamaluetteloon').should('be.visible')

        // Lisää sanktio
        // Käsittelyn pvm
        //cy.get('label[for=filtteri-aikavali] + div .pvm-kentta > .pvm-ikoni > .input-default').first().focus().type("01.01.2021" ).clear().type("01.01.2021" );
        cy.get('label').contains('Käsittelyn pvm').parent().parent().parent().find('input').first().clear().type("01.01.2026");
        cy.get('label').contains('Käsittelyn pvm').parent().parent().parent().find('input').eq(1).clear().type("10.12");
        // Käsitelty
        cy.get('label[for*=kasittelytapa] + div').valinnatValitse({valinta: 'Puhelimitse'});
        // Päätös
        cy.get('label[for*=paatos-paatos] + div').valinnatValitse({valinta: 'Sanktio'});
        // Varmista, että errorilaatikko on näkyvissä
        cy.get('.info-laatikko.varoitus').contains("Sanktiota ei voida lisätä, sillä osa laatupoikkeaman pakollisista tiedoista puuttuu").should('be.visible');
        // Perustelu
        cy.get('label').contains('Päätöksen selitys').parent().parent().parent().find('textarea').first().clear().type(perustelu);

        // Lisää uusi sanktio
        cy.get('button').contains("Lisää uusi").click();
        // Varmistetaan, että Indeksi-kenttä ei näy
        cy.contains('label', 'Indeksi').should('not.exist')
        // Sivupaaneelista tyyppi
        cy.get('label[for*=tyyppi] + div').valinnatValitse({valinta: 'Talvihoito, päätiet'});
        // Sivupaneelista sanktion suuruus
        cy.get('label').contains('Sanktion suuruus').parent().parent().parent().find('input').first().clear().type(1234);
        // Tallenna sanktio
        cy.get('button').contains('Tallenna').click()
        // Tallenna laatupoikkeama
        cy.get('button').contains('Tallenna ja lukitse laatupoikkeama').click();
        cy.contains('Tallenna ja lukitse laatupoikkeama?').should('be.visible');
        cy.get('.modal button').contains('Tallenna ja lukitse').click();
        cy.contains('h1', 'Laatupoikkeamat').should('be.visible')
    })
})

describe('Siivotaan lopuksi', function () {
    before(function () {
        siivoaKanta(testiKohdeNimi);
        siivoaKanta(testiKohdeNimi2);
    });

    it('Tarkista, että kanta on siivottu', function () {
        cy.viewport(1100, 2000)
        avaaLaatupoikkeamat(testiurakka1, evk1);
        cy.contains(testiKohdeNimi).should('not.exist')
        avaaLaatupoikkeamat(testiurakka2, evk2);
        cy.contains(testiKohdeNimi2).should('not.exist')
    })
})

