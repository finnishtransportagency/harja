import {
    avaaSanktiotJaBonuksetNakyma,
    clickTimeout,
    evkLappi,
    evkPohjoisSuomi,
    siivoaBonuksetKannasta,
    testiurakkaMhu19,
    testiurakkaMhu23,
    testiurakkaMhu25
} from '../support/sanktiotJaBonuksetFns';

// Bonusten E2E-testit (Sanktiot ja bonukset -näkymä).
// Sanktiotestit ovat omassa tiedostossaan: s2_sanktiot.cy.js
//
// HUOM! Päivämäärät ja hoitovuodet ovat kovakoodattuja. Ks. tiedoston lopun kommentti.

let testiBonusPerustelu = "CY-bonus-perustelu";
let testiBonusPerustelu2 = "CY-bonus-perustelu2";
let testiBonusPerustelu3 = "CY-bonus-perustelu3";

describe('Bonukset toimii - MHU25 (Rovaniemi)', function () {
    before(function () {
        siivoaBonuksetKannasta(testiBonusPerustelu);
    });

    it('Lisää uusi bonus MHU25', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu25, evkLappi)

        cy.intercept('POST', '_/tallenna-erilliskustannus').as('tallennaBonus')

        // Klikkaa "Lisää uusi" -nappia
        cy.contains('Lisää uusi').click()

        // Sivupaneeli aukeaa
        cy.contains('h2', 'Lisää uusi').should('be.visible')

        // Valitse "Bonus" radio
        cy.contains('label', 'Bonus').click()
        cy.wait(250) // odotetaan, että lomake päivittyy

        // Varmistetaan, että Indeksi-kenttä EI näy bonus-lomakkeella
        cy.contains('label', 'Indeksi').should('not.exist')

        // Perustelu
        cy.get('label').contains('Perustelu').parent().parent().parent().find('textarea').first().clear().type(testiBonusPerustelu)

        // Varmistetaan, että "Kulun kohdistus" -kenttä on read only, mutta siinä on jokin toimenpideinstanssi valittuna ja että button tyyppinen valinta ei ole olemassa
        cy.get('label').contains('Kulun kohdistus').parent().parent().parent().find('div').should('have.class', 'lomake-arvo')
        cy.get('label').contains('Kulun kohdistus').parent().parent().parent().find('button').should('have.length', 0)

        // Summa
        cy.get('label').contains('Summa').parent().parent().parent().find('input').first().clear().type('300')

        // Käsitelty pvm
        cy.get('label').contains('Käsitelty').parent().parent().parent().find('input').first().type('{selectall}15.02.2026')

        // Siirretään fokus pois päivämääräkentästä
        cy.get('label').contains('Perustelu').click()

        // Varmistetaan, että "Laskutuskuukausi" -kenttä on "Kohdistuu hoitovuodelle" ja hoitovuosi on valittavissa
        //cy.get('label').contains('Kohdistuu hoitovuodelle').parent().parent().parent().find('button').should('have.length', 0)
        cy.get('label[for*=perintapvm] + div').valinnatValitse({valinta: '1. hoitovuosi (2025 - 2026)'});
        cy.get('label').contains('Laskutuskuukausi').should('not.exist')

        // Varmistetaan, että "Käsittelytapa" -kenttää ei ole MHU25 urakalla
        cy.get('label').contains('Käsittelytapa').should('not.exist')

        // Tallenna
        cy.get('div.lomake-footer button').contains('Tallenna').click({force: true});
        cy.wait('@tallennaBonus', {timeout: clickTimeout})

        // Varmistetaan onnistuminen
        cy.get('.toast-viesti', {timeout: clickTimeout}).should('be.visible')
    })

    it('Avaa bonus listasta MHU25', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu25, evkLappi)

        // Klikataan luotua bonusta gridissä
        cy.contains('td', testiBonusPerustelu).click()

        // Sivupaneeli aukeaa ja näyttää bonuksen tiedot
        cy.contains(testiBonusPerustelu).should('be.visible')
        cy.contains('300').should('exist')
    })
})

describe('Bonukset toimii - MHU23 (Raahe)', function () {
    before(function () {
        siivoaBonuksetKannasta(testiBonusPerustelu2);
    });

    it('Lisää uusi bonus MHU23 (Raahe)', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu23, evkPohjoisSuomi)

        cy.intercept('POST', '_/tallenna-erilliskustannus').as('tallennaBonus')

        // Klikkaa "Lisää uusi" -nappia
        cy.contains('Lisää uusi').click()

        // Sivupaneeli aukeaa
        cy.contains('h2', 'Lisää uusi').should('be.visible')

        // Valitse "Bonus" radio
        cy.contains('label', 'Bonus').click();

        // Bonus
        cy.get('label[for*=laji] + div').valinnatValitse({valinta: 'Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta'});

        // Varmistetaan, että Indeksi-kenttä EI näy bonus-lomakkeella
        cy.contains('label', 'Indeksi').should('not.exist')

        // Perustelu
        cy.get('label').contains('Perustelu').parent().parent().parent().find('textarea').first().clear().type(testiBonusPerustelu2)

        // Varmistetaan, että "Kulun kohdistus" -kenttä on read only, mutta siinä on jokin toimenpideinstanssi valittuna ja että button tyyppinen valinta ei ole olemassa
        cy.get('label').contains('Kulun kohdistus').parent().parent().parent().find('div').should('have.class', 'lomake-arvo')
        cy.get('label').contains('Kulun kohdistus').parent().parent().parent().find('button').should('have.length', 0)

        // 23 urakoilla ei ole "kohdistuu hoitovuodelle" vaan "laskutuskuukausi"
        cy.get('label').contains('Kohdistuu hoitovuodelle').should('not.exist')
        cy.get('label').contains('Laskutuskuukausi').should('be.visible')

        // Summa
        cy.get('label').contains('Summa').parent().parent().parent().find('input').first().clear().type('400')

        // Käsitelty pvm
        cy.get('label').contains('Käsitelty').parent().parent().parent().find('input').first().type('{selectall}15.02.2026')

        // Siirretään fokus pois päivämääräkentästä
        cy.get('label').contains('Perustelu').click()

        // Varmistetaan, että "Laskutuskuukausi" -kenttä ON käytössä (ei disabled) MHU23 urakalla
        cy.get('[data-cy="koontilaskun-kk-dropdown"]').within(() => {
            cy.get('button').click({force: true});
            cy.contains('Helmikuu 2026 (3. hoitovuosi)');
        });

        // Siirretään focus pois
        cy.get('label').contains('Perustelu').parent().parent().parent().find('textarea').click();


        // Käsittelytapaa ei saa olla mhu23 urakalle
        cy.get('label').contains('Käsittelytapa').should('not.exist')

        // Tallenna
        cy.get('div.lomake-footer button').contains('Tallenna').click({force: true});
        cy.wait('@tallennaBonus', {timeout: clickTimeout})

        // Varmistetaan onnistuminen
        cy.get('.toast-viesti', {timeout: clickTimeout}).should('be.visible')
    })

    it('Avaa bonus listasta MHU23 (Raahe)', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu23, evkPohjoisSuomi)

        // Klikataan luotua bonusta gridissä
        cy.contains('td', testiBonusPerustelu2).click()

        // Sivupaneeli aukeaa ja näyttää bonuksen tiedot
        cy.contains(testiBonusPerustelu2).should('be.visible')
        cy.contains('400').should('exist')
    })
})

describe('Bonukset toimii - MHU19 (Oulu)', function () {
    before(function () {
        siivoaBonuksetKannasta(testiBonusPerustelu3);
    });

    it('Lisää uusi bonus MHU19 (Oulu)', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu19, evkPohjoisSuomi)

        cy.intercept('POST', '_/tallenna-erilliskustannus').as('tallennaBonus')

        // Klikkaa "Lisää uusi" -nappia
        cy.contains('Lisää uusi').click()

        // Sivupaneeli aukeaa
        cy.contains('h2', 'Lisää uusi').should('be.visible')

        // Valitse "Bonus" radio
        cy.contains('label', 'Bonus').click()


        // Bonus
        cy.get('label[for*=laji] + div').valinnatValitse({valinta: 'Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta'});

        // Perustelu
        cy.get('label').contains('Perustelu').parent().parent().parent().find('textarea').first().clear().type(testiBonusPerustelu3)

        // Varmistetaan, että Indeksi-kenttä on valittavissa
        cy.contains('label', 'Indeksi').should('be.visible')
        // Valitaan indeksi
        cy.get('label[for*=indeksi] + div').valinnatValitse({valinta: 'MAKU 2015'});

        // Varmistetaan, että "Kulun kohdistus" -kenttä on read only, mutta siinä on jokin toimenpideinstanssi valittuna ja että button tyyppinen valinta ei ole olemassa
        cy.get('label').contains('Kulun kohdistus').parent().parent().parent().find('div').should('have.class', 'lomake-arvo')
        cy.get('label').contains('Kulun kohdistus').parent().parent().parent().find('button').should('have.length', 0)

        // Summa
        cy.get('label').contains('Summa').parent().parent().parent().find('input').first().clear().type('500')

        // Käsitelty pvm
        cy.get('label').contains('Käsitelty').parent().parent().parent().find('input').first().type('{selectall}01.05.2024')

        // Siirretään fokus pois päivämääräkentästä
        cy.get('label').contains('Perustelu').click()

        // Varmistetaan, että "Laskutuskuukausi" -kenttä ON käytössä ja siinä on oikea kuukausi valittuna
        cy.get('[data-cy=koontilaskun-kk-dropdown]').should('not.have.class', 'disabled')
        cy.get('[data-cy=koontilaskun-kk-dropdown] .valittu').should('contain', 'Toukokuu 2024 (5. hoitovuosi)')

        // Käsittelytapaa ei saa olla mhu19 urakalla
        cy.get('label').contains('Käsittelytapa').should('not.exist')

        // Tallenna
        cy.get('div.lomake-footer button').contains('Tallenna').click({force: true});
        cy.wait('@tallennaBonus', {timeout: clickTimeout})

        // Varmistetaan onnistuminen
        cy.get('.toast-viesti', {timeout: clickTimeout}).should('be.visible')
    })

    it('Avaa bonus listasta MHU19 (Oulu)', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu19, evkPohjoisSuomi)

        // Klikataan luotua bonusta gridissä
        cy.contains('td', testiBonusPerustelu3).click()

        // Sivupaneeli aukeaa ja näyttää bonuksen tiedot
        cy.contains(testiBonusPerustelu3).should('be.visible')
        cy.contains('500').should('exist')
    })
})

describe('Siivotaan bonukset lopuksi', function () {
    before(function () {
        siivoaBonuksetKannasta(testiBonusPerustelu);
        siivoaBonuksetKannasta(testiBonusPerustelu2);
        siivoaBonuksetKannasta(testiBonusPerustelu3);
    });

    it('Tarkista, että kanta on siivottu', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu25, evkLappi)
        cy.contains(testiBonusPerustelu).should('not.exist')

        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu23, evkPohjoisSuomi)
        cy.contains(testiBonusPerustelu2).should('not.exist')

        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu19, evkPohjoisSuomi)
        cy.contains(testiBonusPerustelu3).should('not.exist')
    })
})

// TODO: kovakoodatut vuodet ja hoitovuositekstit ('1. hoitovuosi (2025 - 2026)',
// 'Helmikuu 2026 (3. hoitovuosi)', 'Toukokuu 2024 (5. hoitovuosi)') pitäisi laskea
// kuluvasta hoitokaudesta, esim. apurit.js:n kuluvaHoitokausiAlkuvuosi-funktiolla.
// HUOM: päättyneille urakoille (MHU19 Oulu 2019-2024) kuluva hoitokausi ei toimi,
// koska pvm:n on oltava urakan voimassaolon sisällä.

