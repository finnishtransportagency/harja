// Asetuksia
let clickTimeout = 12000;
let pageloadTimeout = 30000;
let testiSanktioKuvaus = "CY-sanktio-testi";
let testiSanktioKuvaus2 = "CY-sanktio-testi2";
let testiSanktioKuvaus3 = "CY-sanktio-testi3";
let testiSanktioPerustelu = "CY-perustelu";
let testiSanktioPerustelu2 = "CY-perustelu2";
let testiSanktioPerustelu3 = "CY-perustelu3";
let testiBonusPerustelu = "CY-bonus-perustelu";
let testiBonusPerustelu2 = "CY-bonus-perustelu2";
let testiBonusPerustelu3 = "CY-bonus-perustelu3";
let testiurakka = "Rovaniemen MHU testiurakka (1. hoitovuosi)";
let testiurakka2 = "POP MHU Suomussalmi 2024-2029";
let testiurakka3 = "Oulun MHU 2019-2024";
let testiurakka4 = "Raahen MHU 2023-2028";
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

// Helper: siivoa testidatan bonukset kannasta (erilliskustannus-taulusta)
function siivoaBonusKanta(lisatieto) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM erilliskustannus WHERE lisatieto = '" + lisatieto + "';\"");
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
    cy.wait(250); // Toimii varmemmin, kun ei ole niin kiire
    cy.contains('[data-cy=urakat-valitse-urakka] li', urakkaNimi, {timeout: pageloadTimeout}).click()
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
        cy.get('label').contains('Havaittu').parent().parent().parent().find('input').first().type('{selectall}15.02.2026')
        // Määrätty
        cy.get('label').contains('Määrätty').parent().parent().parent().find('input').first().type('{selectall}15.02.2026')

        // Siirretään fokus pois päivämääräkentästä, jotta mahdollinen kalenteri sulkeutuu
        cy.get('label').contains('Perustelu').click()

        // Checkbox tulee vasta seuraavaan versioon
        // Varmistetaan, että "Määrätty välikatselmuksessa" checkbox on oletuksena valittuna
        // cy.contains('label', 'Määrätty välikatselmuksessa').parent().find('input[type=checkbox]').should('be.checked')
        // Ja käsittelytapa on Välikatselmus
        // cy.contains('Välikatselmus').should('be.visible')

        // Otetaan checkbox pois päältä -> käsittelytapa vaihtuu Työmakokoukseksi
        // cy.contains('label', 'Määrätty välikatselmuksessa').click()
        // cy.contains('Työmaakokous').should('be.visible')

        // Laitetaan checkbox takaisin päälle -> käsittelytapa vaihtuu Välikatselmukseksi
        // cy.contains('label', 'Määrätty välikatselmuksessa').click()
        // cy.contains('Välikatselmus').should('be.visible')

        // Tallenna
        cy.get('div.lomake-footer button').contains('Tallenna').click({force: true});
        cy.wait('@tallenna', {timeout: clickTimeout})

        // Varmistetaan onnistuminen
        cy.get('.toast-viesti.onnistunut', {timeout: clickTimeout}).should('be.visible')
            .and('contain.text', 'Sanktion tallennus onnistui')
    })

    it('Näyttää laskutusraja-sanktion kentät oikein MHU25', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka, evk)

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
        avaaSanktiotJaBonukset(testiurakka, evk)

        // Määrätty päivämäärä pitäisi näkyä listassa
        cy.contains('td', '15.02.2026');

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
        cy.get('.toast-viesti.onnistunut', {timeout: clickTimeout}).should('be.visible')
            .and('contain.text', 'Sanktion tallennus onnistui')

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
        cy.get('.toast-viesti.onnistunut', {timeout: clickTimeout}).should('be.visible')
            .and('contain.text', 'Sanktion tallennus onnistui')
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

describe('Bonukset toimii - MHU25 (Rovaniemi)', function () {
    before(function () {
        siivoaBonusKanta(testiBonusPerustelu);
    });

    it('Lisää uusi bonus MHU25', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka, evk)

        cy.intercept('POST', '_/tallenna-erilliskustannus').as('tallennaBonus')

        // Klikkaa "Lisää uusi" -nappia
        cy.contains('Lisää uusi').click()

        // Sivupaneeli aukeaa
        cy.contains('h2', 'Lisää uusi').should('be.visible')

        // Valitse "Bonus" radio
        cy.contains('label', 'Bonus').click()

        // Varmistetaan, että Indeksi-kenttä EI näy bonus-lomakkeella
        cy.contains('label', 'Indeksi').should('not.exist')

        //  Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta on ainoa vaihtoehto ja tekstinä lomakkeella
        cy.get('label').contains('Bonus').parent().parent().parent().find('div').should('have.class', 'lomake-arvo')
        cy.get('label').contains('Bonus').parent().parent().parent().find('button').should('have.length', 0)

        //cy.get('label[for*=laji] + div').valinnatValitse({valinta: 'Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta'});

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
        avaaSanktiotJaBonukset(testiurakka, evk)

        // Klikataan luotua bonusta gridissä
        cy.contains('td', testiBonusPerustelu).click()

        // Sivupaneeli aukeaa ja näyttää bonuksen tiedot
        cy.contains(testiBonusPerustelu).should('be.visible')
        cy.contains('300').should('exist')
    })
})

describe('Bonukset toimii - MHU23 (Raahe)', function () {
    before(function () {
        siivoaBonusKanta(testiBonusPerustelu2);
    });

    it('Lisää uusi bonus MHU23 (Raahe)', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka4, evk2)

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
        avaaSanktiotJaBonukset(testiurakka4, evk2)

        // Klikataan luotua bonusta gridissä
        cy.contains('td', testiBonusPerustelu2).click()

        // Sivupaneeli aukeaa ja näyttää bonuksen tiedot
        cy.contains(testiBonusPerustelu2).should('be.visible')
        cy.contains('400').should('exist')
    })
})

describe('Bonukset toimii - MHU19 (Oulu)', function () {
    before(function () {
        siivoaBonusKanta(testiBonusPerustelu3);
    });

    it('Lisää uusi bonus MHU19 (Oulu)', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka3, evk2)

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
        avaaSanktiotJaBonukset(testiurakka3, evk2)

        // Klikataan luotua bonusta gridissä
        cy.contains('td', testiBonusPerustelu3).click()

        // Sivupaneeli aukeaa ja näyttää bonuksen tiedot
        cy.contains(testiBonusPerustelu3).should('be.visible')
        cy.contains('500').should('exist')
    })
})

describe('Siivotaan lopuksi', function () {
    before(function () {
        siivoaKanta(testiSanktioKuvaus);
        siivoaKanta(testiSanktioKuvaus2);
        siivoaKanta(testiSanktioKuvaus3);
        siivoaBonusKanta(testiBonusPerustelu);
        siivoaBonusKanta(testiBonusPerustelu2);
        siivoaBonusKanta(testiBonusPerustelu3);
    });

    it('Tarkista, että kanta on siivottu', function () {
        cy.viewport(1100, 1200)
        avaaSanktiotJaBonukset(testiurakka, evk)
        cy.contains(testiSanktioKuvaus).should('not.exist')
        cy.contains(testiBonusPerustelu).should('not.exist')

        avaaSanktiotJaBonukset(testiurakka2, evk2)
        cy.contains(testiSanktioKuvaus2).should('not.exist')

        avaaSanktiotJaBonukset(testiurakka3, evk2)
        cy.contains(testiSanktioKuvaus3).should('not.exist')
        cy.contains(testiBonusPerustelu3).should('not.exist')

        avaaSanktiotJaBonukset(testiurakka4, evk2)
        cy.contains(testiBonusPerustelu2).should('not.exist')
    })
})

