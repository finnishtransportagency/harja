// Asetuksia
let clickTimeout = 12000;
let pageloadTimeout = 30000;
let testiSanktioKuvaus = "CY-sanktio-testi";
let testiSanktioKuvaus2 = "CY-sanktio-testi2";
let testiSanktioKuvaus3 = "CY-sanktio-testi3";
let testiSanktioPerustelu = "CY-perustelu";
let testiSanktioPerustelu2 = "CY-perustelu2";
let testiSanktioPerustelu3 = "CY-perustelu3";
let testiurakka = "Rovaniemen MHU testiurakka (1. hoitovuosi)";
let testiurakka2 = "POP MHU Suomussalmi 2024-2029";
let testiurakka3 = "Oulun MHU 2019-2024";
let evk = "Lappi";
let evk2 = "Pohjois-Suomi";

// Helper: siivoa testidatan sanktiot kannasta
function siivoaKanta(kohde) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM sanktio WHERE suorasanktio = true AND id IN (SELECT s.id FROM sanktio s JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id WHERE lp.kohde = '" + kohde + "');\"");
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM laatupoikkeama WHERE kohde = '" + kohde + "';\"");
    });
}

// Helper: navigoi sanktiot ja bonukset -näkymään
let avaaSanktiotJaBonukset = function (urakkaNimi, urakkaEvk) {
    cy.intercept('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot')

    cy.visit("/")

    cy.contains('.haku-lista-item', urakkaEvk).click()
    cy.get('.ajax-loader', {timeout: pageloadTimeout}).should('not.exist')
    cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
    cy.contains('Näytä päättyneet').click();
    cy.contains('[data-cy=urakat-valitse-urakka] li', urakkaNimi, {timeout: clickTimeout}).click()
    cy.get('[data-cy=tabs-taso1-Laadunseuranta]').click()
    cy.get('[data-cy="tabs-taso2-Sanktiot ja bonukset"]').click()
    cy.wait('@sanktiot', {timeout: clickTimeout})
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
}

describe('Sanktiot toimii - MHU25 (Rovaniemi)', function () {
    before(function () {
        siivoaKanta(testiSanktioKuvaus);
    });

    it('Mene sanktiot ja bonukset -välilehdelle', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka, evk)

        // Varmistetaan, että näkymä latautui
        cy.contains('Sanktiot, bonukset ja arvonvähennykset').should('be.visible')
    })

    it('Lisää uusi sanktio MHU25', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka, evk)

        cy.intercept('POST', '_/tallenna-suorasanktio').as('tallenna')

        // Klikkaa "Lisää uusi" -nappia
        cy.contains('Lisää uusi').click()

        // Sivupaneeli aukeaa
        cy.contains('h2', 'Lisää uusi').should('be.visible')

        // Valitse "Sanktio" radio (oletuksena voi olla valittuna)
        cy.contains('label', 'Sanktio').click()

        // Sanktion laji
        cy.get('label[for*=laji] + div').valinnatValitse({valinta: 'A-ryhmä (tehtäväkohtainen sanktio)'});

        // Tyyppi
        cy.get('label[for*=tyyppi] + div').valinnatValitse({valinta: 'Talvihoito, päätiet'});

        // Tapahtumapaikka/kuvaus
        cy.get('label').contains('Tapahtumapaikka').parent().parent().parent().find('input').first().clear().type(testiSanktioKuvaus)

        // Perustelu
        cy.get('label').contains('Perustelu').parent().parent().parent().find('textarea').first().clear().type(testiSanktioPerustelu)

        // Sanktion suuruus
        cy.get('label').contains('Sanktion suuruus').parent().parent().parent().find('input').first().clear().type('500')

        // Havaittu pvm
        cy.get('label').contains('Havaittu').parent().parent().parent().find('input').first().clear().type('15.02.2026')
        // Määrätty
        cy.get('label').contains('Määrätty').parent().parent().parent().find('input').first().clear().type('15.02.2026')

        // Siirretään fokus pois päivämääräkentästä, jotta mahdollinen kalenteri sulkeutuu
        cy.get('label').contains('Perustelu').click()

        // Tallenna
        cy.get('div.lomake-footer button').contains('Tallenna').click({force: true});
        cy.wait('@tallenna', {timeout: clickTimeout})

        // Varmistetaan onnistuminen
        cy.get('.toast-viesti', {timeout: clickTimeout}).should('be.visible')
    })

    it('Avaa sanktio listasta MHU25', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka, evk)

        // Klikataan luotua sanktiota gridissä
        cy.contains('td', testiSanktioKuvaus).click()

        // Sivupaneeli aukeaa ja näyttää sanktion tiedot
        cy.contains(testiSanktioKuvaus).should('be.visible')
        cy.contains('500').should('exist')
    })
})

describe('Sanktiot toimii - MHU24 (Suomussalmi)', function () {
    before(function () {
        siivoaKanta(testiSanktioKuvaus2);
    });

    it('Mene sanktiot ja bonukset -välilehdelle MHU24', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka2, evk2)

        // Varmistetaan, että näkymä latautui
        cy.contains('Sanktiot, bonukset ja arvonvähennykset').should('be.visible')
    })

    it('Lisää uusi sanktio MHU24', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka2, evk2)

        cy.intercept('POST', '_/tallenna-suorasanktio').as('tallenna')

        // Klikkaa "Lisää uusi" -nappia
        cy.contains('Lisää uusi').click()

        // Sivupaneeli aukeaa
        cy.contains('h2', 'Lisää uusi').should('be.visible')

        // Valitse "Sanktio" radio (oletuksena voi olla valittuna)
        cy.contains('label', 'Sanktio').click()

        // Sanktion laji
        cy.get('label[for*=laji] + div').valinnatValitse({valinta: 'A-ryhmä (tehtäväkohtainen sanktio)'});

        // Tyyppi
        cy.get('label[for*=tyyppi] + div').valinnatValitse({valinta: 'Talvihoito, päätiet'});

        // Varmistetaan, että Indeksi-kenttä ei näy
        cy.contains('label', 'Indeksi').should('not.exist')

        // Tapahtumapaikka/kuvaus
        cy.get('label').contains('Tapahtumapaikka').parent().parent().parent().find('input').first().clear().type(testiSanktioKuvaus2)

        // Perustelu
        cy.get('label').contains('Perustelu').parent().parent().parent().find('textarea').first().clear().type(testiSanktioPerustelu2)

        // Sanktion suuruus
        cy.get('label').contains('Sanktion suuruus').parent().parent().parent().find('input').first().clear().type('750')

        // Havaittu pvm
        cy.get('label').contains('Havaittu').parent().parent().parent().find('input').first().clear().type('15.02.2026')

        // Käsitelty
        cy.get('label').contains('Käsitelty').parent().parent().parent().find('input').first().clear().type('15.02.2026')

        // Lisätään focuksen siirto pois pvm divistä
        cy.get('label').contains('Perustelu').click();

        // Tyyppi
        cy.get('label[for*=kasittelytapa] + div').valinnatValitse({valinta: 'Työmaakokous'});

        // Tallenna
        cy.get('div.lomake-footer button').contains('Tallenna').click({force: true});
        cy.wait('@tallenna', {timeout: clickTimeout})

        // Varmistetaan onnistuminen
        cy.get('.toast-viesti', {timeout: clickTimeout}).should('be.visible')
    })

    it('Avaa sanktio listasta MHU24', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka2, evk2)

        // Klikataan luotua sanktiota gridissä
        cy.contains('td', testiSanktioKuvaus2).click()

        // Sivupaneeli aukeaa ja näyttää sanktion tiedot
        cy.contains(testiSanktioKuvaus2).should('be.visible')
        cy.contains('750').should('exist')
    })
})

describe('Sanktiot toimii - MHU19 (Oulu)', function () {
    before(function () {
        siivoaKanta(testiSanktioKuvaus3);
    });

    it('Mene sanktiot ja bonukset -välilehdelle MHU19', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka3, evk2)

        // Varmistetaan, että näkymä latautui
        cy.contains('Sanktiot, bonukset ja arvonvähennykset').should('be.visible')
    })

    it('Lisää uusi sanktio MHU19', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka3, evk2)

        cy.intercept('POST', '_/tallenna-suorasanktio').as('tallenna')

        // Klikkaa "Lisää uusi" -nappia
        cy.contains('Lisää uusi').click()

        // Sivupaneeli aukeaa
        cy.contains('h2', 'Lisää uusi').should('be.visible')

        // Valitse "Sanktio" radio (oletuksena voi olla valittuna)
        cy.contains('label', 'Sanktio').click()

        // Sanktion laji
        cy.get('label[for*=laji] + div').valinnatValitse({valinta: 'A-ryhmä (tehtäväkohtainen sanktio)'});

        // Tyyppi
        cy.get('label[for*=tyyppi] + div').valinnatValitse({valinta: 'Talvihoito, päätiet'});

        // Varmistetaan, että Indeksi-kenttä NÄKYY MHU19 urakassa
        cy.contains('label', 'Indeksi').should('be.visible')
        // Valitaan indeksi
        cy.get('label[for*=indeksi] + div').valinnatValitse({valinta: 'MAKU 2015'});

        // Tapahtumapaikka/kuvaus
        cy.get('label').contains('Tapahtumapaikka').parent().parent().parent().find('input').first().clear().type(testiSanktioKuvaus3)

        // Perustelu
        cy.get('label').contains('Perustelu').parent().parent().parent().find('textarea').first().clear().type(testiSanktioPerustelu3)

        // Sanktion suuruus
        cy.get('label').contains('Sanktion suuruus').parent().parent().parent().find('input').first().clear().type('600')

        // Havaittu pvm
        cy.get('label').contains('Havaittu').parent().parent().parent().find('input').first().clear().type('15.02.2024')

        // Käsitelty
        cy.get('label').contains('Käsitelty').parent().parent().parent().find('input').first().clear().type('15.02.2024')

        // Lisätään focuksen siirto pois pvm divistä
        cy.get('label').contains('Perustelu').click()

        // Käsittelytapa
        cy.get('label[for*=kasittelytapa] + div').valinnatValitse({valinta: 'Työmaakokous'});

        // Siirretään fokus pois päivämääräkentästä
        cy.get('label').contains('Perustelu').click()

        // Tallenna
        cy.get('div.lomake-footer button').contains('Tallenna').click({force: true});
        cy.wait('@tallenna', {timeout: clickTimeout})

        // Varmistetaan onnistuminen
        cy.get('.toast-viesti', {timeout: clickTimeout}).should('be.visible')
    })

    it('Avaa sanktio listasta MHU19', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka3, evk2)

        // Klikataan luotua sanktiota gridissä
        cy.contains('td', testiSanktioKuvaus3).click()

        // Sivupaneeli aukeaa ja näyttää sanktion tiedot
        cy.contains(testiSanktioKuvaus3).should('be.visible')
        cy.contains('600').should('exist')
    })
})

// TODO: Bonusten testit ja olisi hyvä lisätä myöhemmin myös Arvonvähennykselle testit
describe.skip('Bonukset toimii', function () {
    it('Lisää uusi bonus', function () {
        // Implementoidaan myöhemmin
    })

    it('Avaa bonus listasta', function () {
        // Implementoidaan myöhemmin
    })
})

describe('Siivotaan lopuksi', function () {
    before(function () {
        siivoaKanta(testiSanktioKuvaus);
        siivoaKanta(testiSanktioKuvaus2);
        siivoaKanta(testiSanktioKuvaus3);
    });

    it('Tarkista, että kanta on siivottu', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka, evk)
        cy.contains(testiSanktioKuvaus).should('not.exist')

        avaaSanktiotJaBonukset(testiurakka2, evk2)
        cy.contains(testiSanktioKuvaus2).should('not.exist')

        avaaSanktiotJaBonukset(testiurakka3, evk2)
        cy.contains(testiSanktioKuvaus3).should('not.exist')
    })
})

