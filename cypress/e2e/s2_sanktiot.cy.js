import {
    avaaSanktiotJaBonuksetNakyma,
    clickTimeout,
    evkLappi,
    evkPohjoisSuomi,
    siivoaSanktiotKannasta,
    testiurakkaMhu19,
    testiurakkaMhu24,
    testiurakkaMhu25
} from '../support/sanktiotJaBonuksetFns';

// Suorasanktioiden E2E-testit (Sanktiot ja bonukset -näkymä).
// Bonustestit ovat omassa tiedostossaan: s2_bonukset.cy.js
//
// HUOM! Päivämäärät ovat kovakoodattuja. Ks. tiedoston lopun kommentti.

let testiSanktioKuvaus = "CY-sanktio-testi";
let testiSanktioKuvaus2 = "CY-sanktio-testi2";
let testiSanktioKuvaus3 = "CY-sanktio-testi3";
let testiSanktioPerustelu = "CY-perustelu";
let testiSanktioPerustelu2 = "CY-perustelu2";
let testiSanktioPerustelu3 = "CY-perustelu3";

describe('Sanktiot toimii - MHU25 (Rovaniemi)', function () {
    before(function () {
        siivoaSanktiotKannasta(testiSanktioKuvaus);
    });

    it('Mene sanktiot ja bonukset -välilehdelle', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu25, evkLappi)

        // Varmistetaan, että näkymä latautui
        cy.contains('Sanktiot, bonukset ja arvonvähennykset').should('be.visible')
    })

    it('Lisää uusi sanktio MHU25', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu25, evkLappi)

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
        cy.get('label').contains('Havaittu').parent().parent().parent().find('input').first().type('{selectall}15.02.2026')
        // Määrätty
        cy.get('label').contains('Määrätty').parent().parent().parent().find('input').first().type('{selectall}15.02.2026')

        // Siirretään fokus pois päivämääräkentästä, jotta mahdollinen kalenteri sulkeutuu
        cy.get('label').contains('Perustelu').click()

        // Tallenna
        cy.get('div.lomake-footer button').contains('Tallenna').click({force: true});
        cy.wait('@tallenna', {timeout: clickTimeout})

        // Varmistetaan onnistuminen
        cy.get('.toast-viesti.onnistunut', {timeout: clickTimeout}).should('be.visible')
            .and('contain.text', 'Sanktion tallennus onnistui')
    })

    it('Näyttää laskutusraja-sanktion kentät oikein MHU25', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu25, evkLappi)

        // Klikkaa "Lisää uusi" -nappia
        cy.contains('Lisää uusi').click()

        // Sivupaneeli aukeaa
        cy.contains('h2', 'Lisää uusi').should('be.visible')

        // Valitse "Sanktio" radio
        cy.contains('label', 'Sanktio').click()

        // Valitse sanktion laji, jossa tyyppi ja tapahtumapaikka eivät ole käytössä
        cy.get('label[for*=laji] + div').valinnatValitse({valinta: 'Laskutus yli laskutusrajan'});

        // Varmistetaan pyydetyt kenttänäkyvyydet
        cy.contains('label', 'Tyyppi').should('not.exist')
        cy.contains('label', 'Tapahtumapaikka/kuvaus').should('not.exist')

        // Sanktion suuruuden otsikko pitää näkyä ja arvon olla vain luettavissa
        cy.contains('label', 'Sanktion suuruus (20% ylittävästä laskutuksesta)').should('be.visible')
        cy.get('label').contains('Sanktion suuruus (20% ylittävästä laskutuksesta)').parent().parent().parent().within(() => {
            cy.get('div.lomake-arvo').should('be.visible').invoke('text').should('not.be.empty')
            cy.get('input').should('have.length', 0)
            cy.get('button').should('have.length', 0)
        })

        // Ylityksen määrä -kenttä pitää löytyä
        cy.contains('label', 'Ylityksen määrä (€)').should('be.visible')
        cy.get('label').contains('Ylityksen määrä (€)').parent().parent().parent().find('input').first().should('be.visible')
    })

    it('Avaa sanktio listasta MHU25', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu25, evkLappi)

        // Määrätty päivämäärä pitäisi näkyä listassa
        cy.contains('td', '15.02.2026');

        // Klikataan luotua sanktiota gridissä
        cy.contains('td', testiSanktioKuvaus).click()

        // Sivupaneeli aukeaa ja näyttää sanktion tiedot
        cy.contains(testiSanktioKuvaus).should('be.visible')
        cy.contains('label', 'Määräystapa').parent().parent().parent().find('div').contains("Työmaakokous").should('be.visible') // Työmaakokous on valittuna
        cy.contains('label', 'Käsittely ja laskutus').parent().parent().parent().find('div').contains("Välikatselmus").should('be.visible') // Työmaakokous on valittuna
        cy.contains('label', 'Liitteet').parent().parent().parent().find('div').contains("Ei liitettä").should('be.visible') // Työmaakokous on valittuna
        cy.contains('label', 'Havaittu').parent().parent().parent().find('div').contains("15.02.2026").should('be.visible') // Työmaakokous on valittuna
        cy.contains('label', 'Määrätty').parent().parent().parent().find('div').contains("15.02.2026").should('be.visible') // Työmaakokous on valittuna
        cy.contains('label', 'Perustelu').parent().parent().parent().find('div').contains(testiSanktioPerustelu).should('be.visible') // Työmaakokous on valittuna
        cy.contains('500').should('exist')
    })
})

describe('Sanktiot toimii - MHU24 (Suomussalmi)', function () {
    before(function () {
        siivoaSanktiotKannasta(testiSanktioKuvaus2);
    });

    it('Mene sanktiot ja bonukset -välilehdelle MHU24', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu24, evkPohjoisSuomi)

        // Varmistetaan, että näkymä latautui
        cy.contains('Sanktiot, bonukset ja arvonvähennykset').should('be.visible')
    })

    it('Lisää uusi sanktio MHU24', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu24, evkPohjoisSuomi)

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
        cy.get('.toast-viesti.onnistunut', {timeout: clickTimeout}).should('be.visible')
            .and('contain.text', 'Sanktion tallennus onnistui')

    })

    it('Avaa sanktio listasta MHU24', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu24, evkPohjoisSuomi)

        // Klikataan luotua sanktiota gridissä
        cy.contains('td', testiSanktioKuvaus2).click()

        // Sivupaneeli aukeaa ja näyttää sanktion tiedot
        cy.contains(testiSanktioKuvaus2).should('be.visible')
        cy.contains('750').should('exist')
    })
})

describe('Sanktiot toimii - MHU19 (Oulu)', function () {
    before(function () {
        siivoaSanktiotKannasta(testiSanktioKuvaus3);
    });

    it('Mene sanktiot ja bonukset -välilehdelle MHU19', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu19, evkPohjoisSuomi)

        // Varmistetaan, että näkymä latautui
        cy.contains('Sanktiot, bonukset ja arvonvähennykset').should('be.visible')
    })

    it('Lisää uusi sanktio MHU19', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu19, evkPohjoisSuomi)

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
        cy.get('.toast-viesti.onnistunut', {timeout: clickTimeout}).should('be.visible')
            .and('contain.text', 'Sanktion tallennus onnistui')
    })

    it('Avaa sanktio listasta MHU19', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu19, evkPohjoisSuomi)

        // Klikataan luotua sanktiota gridissä
        cy.contains('td', testiSanktioKuvaus3).click()

        // Sivupaneeli aukeaa ja näyttää sanktion tiedot
        cy.contains(testiSanktioKuvaus3).should('be.visible')
        cy.contains('600').should('exist')
    })
})

describe('Siivotaan sanktiot lopuksi', function () {
    before(function () {
        siivoaSanktiotKannasta(testiSanktioKuvaus);
        siivoaSanktiotKannasta(testiSanktioKuvaus2);
        siivoaSanktiotKannasta(testiSanktioKuvaus3);
    });

    it('Tarkista, että kanta on siivottu', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu25, evkLappi)
        cy.contains(testiSanktioKuvaus).should('not.exist')

        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu24, evkPohjoisSuomi)
        cy.contains(testiSanktioKuvaus2).should('not.exist')

        avaaSanktiotJaBonuksetNakyma(testiurakkaMhu19, evkPohjoisSuomi)
        cy.contains(testiSanktioKuvaus3).should('not.exist')
    })
})

// TODO: kovakoodatut vuodet (15.02.2026 / 15.02.2024) pitäisi laskea kuluvasta
// hoitokaudesta, esim. apurit.js:n kuluvaHoitokausiAlkuvuosi-funktiolla.
// HUOM: päättyneille urakoille (MHU19 Oulu 2019-2024) kuluva hoitokausi ei toimi,
// koska pvm:n on oltava urakan voimassaolon sisällä.

