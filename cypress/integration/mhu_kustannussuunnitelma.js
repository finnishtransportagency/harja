import * as ks from '../support/kustannussuunnitelmaFns.js';
import transit from "transit-js";

// ######### HUOMIOT ##########

// TODO: Testaa osioiden omat yhteenvedot (katso osioiden testien todo-kommentit)
// TODO: Testaa mahdollisesti "Tavoite- ja kattohinta" osion arvojen päivittyminen aina yhden testiblokin jälkeen (katso osioiden testien todo-kommentit)
//       HOX: Lopullinen tavoite- ja kattohinta arvo tarkastetaan kuitenkin sivun refreshin jälkeen, mutta väliaikatietoja olisi kiva saada, jotta
//       olisi helpompi selvitetllä mikä osio tarkalleen tuottaa virheellisiä lukuja, mikäli muutoksia Kustannussuunnitelman koodiin tehdään.


// Nämä testit voi implementoida sen jälkeen kun vahvistaminen, vahvistuksen peruminen ja vahvistetun osion muokkaus on saatu implementoitua:
//TODO: Tee testi, jossa vahvistetaan osio ja tarkastetaan tilayhteenevedosta näkyykö osio vahvistettuna
//TODO: Tee testi, jossa perutaan vahvistaminen.
//TODO: Tee testi, jossa osiota muokataan vahvistamisen jälkeen.

//TODO: Kannattaa harkita tämänkin testipatterin pilkkomista useampaan tiedostoon, jos testien määrä kasvaa vielä suuremmaksi.


// Täytetään ajax kutsun vastauksen perusteella
const indeksit = [];
const ivalonUrakkaId = 35;

function alustaKanta() {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista kiinteähintaiset työt
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM kiinteahintainen_tyo kht " +
            "USING toimenpideinstanssi tpi " +
            "WHERE kht.toimenpideinstanssi = tpi.id AND " +
            "      tpi.urakka = (SELECT id FROM urakka WHERE nimi = 'Ivalon MHU testiurakka (uusi)');\"")
            .then((tulos) => {
                console.log("Poista kiinteähintaiset työt tulos:", tulos)
            });
        // Poista kustannusarvioidut työt
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM kustannusarvioitu_tyo kat " +
            "USING toimenpideinstanssi tpi " +
            "WHERE kat.toimenpideinstanssi = tpi.id AND " +
            "      tpi.urakka = (SELECT id FROM urakka WHERE nimi = 'Ivalon MHU testiurakka (uusi)');\"")
            .then((tulos) => {
                console.log("Poista kustannusarvioidut työt tulos:", tulos)
            });

        // Poista johto- ja hallintokorvauksiin liittyvät asiat
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM johto_ja_hallintokorvaus jjh " +
            "WHERE jjh.\\\"urakka-id\\\" = (SELECT id FROM urakka WHERE nimi = 'Ivalon MHU testiurakka (uusi)');\"")
            .then((tulos) => {
                console.log("Poista johto- ja hallintokorvaukset tulos:", tulos)
            })
    });
}

function alustaIvalonUrakka() {
    alustaKanta();
}


// ### Testit ###


describe('Testaa Inarin MHU urakan kustannussuunnitelmanäkymää', function () {
    it('Alusta tietokanta', function () {
        alustaIvalonUrakka();
    })

    it('Avaa näkymä ja ota indeksit talteen muita testejä varten', function () {
        cy.intercept('POST', '_/budjettisuunnittelun-indeksit').as('budjettisuunnittelun-indeksit');

        cy.visit("/");

        cy.contains('.haku-lista-item', 'Lappi').click();
        cy.get('.ajax-loader', { timeout: 10000 }).should('not.exist');

        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Ivalon MHU testiurakka (uusi)', { timeout: 10000 }).click();
        cy.get('[data-cy=tabs-taso1-Suunnittelu]', { timeout: 20000 }).click();

        // Tässä otetaan indeksikertoimet talteen
        cy.wait('@budjettisuunnittelun-indeksit')
            .then(($xhr) => {
                const reader = transit.reader("json");
                const vastaus = reader.read(JSON.stringify($xhr.response.body));

                vastaus.forEach((transitIndeksiMap) => {
                    indeksit.push(transitIndeksiMap.get(transit.keyword('indeksikerroin')));
                });
            });

        cy.get('img[src="images/ajax-loader.gif"]', { timeout: 20000 }).should('not.exist');
    });

    it('Testaa tilayhteenvedon vierityslinkit', function () {
        cy.contains('#tilayhteenveto a', 'Hankintakustannukset').click();
        cy.wait(500)
        cy.get("#hankintakustannukset-osio").should("be.visible")

        cy.contains('#tilayhteenveto a', 'Erillishankinnat').click();
        cy.wait(500)
        cy.get("#erillishankinnat-osio").should("be.visible")

        cy.contains('#tilayhteenveto a', 'Johto- ja hallintokorvaus').click();
        cy.wait(500)
        cy.get("#johto-ja-hallintokorvaus-osio").should("be.visible")

        cy.contains('#tilayhteenveto a', 'Hoidonjohtopalkkio').click();
        cy.wait(500)
        cy.get("#hoidonjohtopalkkio-osio").should("be.visible")

        cy.contains('#tilayhteenveto a', 'Tavoite- ja kattohinta').click();
        cy.wait(500)
        cy.get("#tavoite-ja-kattohinta-osio").should("be.visible")

        cy.contains('#tilayhteenveto a', 'Tilaajan rahavaraukset').click();
        cy.wait(500)
        cy.get("#tilaajan-varaukset-osio").should("be.visible")
    });
});


// ---------------------------------
// --- Hankintakustannukset osio ---
// ---------------------------------

describe('Hankintakustannukset osio', function () {
    // #### "Suunnitellut hankinnat" taulukko ####
    describe('Testaa "Suunnitellut hankinnat" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kiinteahintaiset-tyot').as('tallenna-kiinteahintaiset-tyot');

        });

        it('Taulukon arvot alussa oikein', function () {
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaOtsikot(['Kiinteät', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['1. hoitovuosi', '2. hoitovuosi', '3. hoitovuosi', '4. hoitovuosi', '5. hoitovuosi'])
                .testaaRivienArvot([1], [0, 1], ['', '', '', '', ''])
                .testaaRivienArvot([1], [0, 2], ['0,00', '0,00', '0,00', '0,00', '0,00'])
                .testaaRivienArvot([1], [0, 3], ['0,00', '0,00', '0,00', '0,00', '0,00'])

        });

        it('Muokkaa ensimmäisen vuoden arvoja ilman alaskopiointia', function () {
            // Disabloidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla defaulttina aktiivinen.
            cy.get('input[id="kopioi-hankinnat-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck();

            // Avaa 1. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 0, 0, 1, '1');
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 0, 11, 1, '1', true);

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '', ks.formatoiArvoDesimaalinumeroksi(2),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 2, 1))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 2);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 2);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 2);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 1, 2);

        });

        it('Muokkaa ensimmäisen vuoden arvoja alaskopioinnin kanssa', function () {
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 0, 1, 1, '10');
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')


            // -- Arvojen tarkastus ---

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(111),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 111, 1))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 111);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 111);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 111);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 1, 111);

        });

        it('Muokkaa toisen vuoden arvoja ilman alaskopiointia', function () {
            // Avaa 2. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-taulukko')
                .taulukonOsaPolussa([1, 1, 0, 0])
                .click();

            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 1, 0, 1, '2');
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 1, 11, 1, '2', true);

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')


            // -- Arvojen tarkastus ---

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(115),
                        ks.formatoiArvoDesimaalinumeroksi(ks.summaaJaIndeksikorjaaArvot(indeksit, [111, 4]))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 2, 4);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 2, 4);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 2, 4);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 2, 4);


        });

        it('Muokkaa toisen vuoden arvoja alaskopioinnin kanssa', function () {
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 1, 1, 1, '20');
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    [
                        'Yhteensä',
                        '',
                        ks.formatoiArvoDesimaalinumeroksi(333),
                        ks.formatoiArvoDesimaalinumeroksi(ks.summaaJaIndeksikorjaaArvot(indeksit, [111, 222]))
                    ]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 2, 222);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 2, 222);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 2, 222);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 2, 222);
        });

        it('Muokkaa arvot tuleville hoitokausille', function () {
            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu edellisten testien toimesta.
            cy.get('input[id="kopioi-hankinnat-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Avaa 3. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-taulukko')
                .taulukonOsaPolussa([1, 2, 0, 0])
                .click();

            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 2, 0, 1, '5');
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(513),
                        ks.formatoiArvoDesimaalinumeroksi(ks.summaaJaIndeksikorjaaArvot(indeksit, [111, 222, 60, 60, 60]))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 3, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 3, 60);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 4, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 4, 60);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 5, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 5, 60);
            ks.tarkastaHintalaskurinYhteensaArvo('hankintakustannukset-hintalaskuri',
                [111, 222, 60, 60, 60]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'hankintakustannukset-indeksilaskuri',
                [111, 222, 60, 60, 60]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 3, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 3, 60);
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 4, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 4, 60);
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 5, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 5, 60);
            ks.tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri', [111, 222, 60, 60, 60]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'tavoitehinnan-indeksilaskuri', [111, 222, 60, 60, 60]);

        });
    });

// --

    // #### Määrämitattavien töiden taulukko ####
    //      "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle: X"
    describe('Testaa määrämitattavien töiden taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kustannusarvioitu-tyo').as('tallenna-kustannusarvioitu-tyo');
        });

        // "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle"-taulukon aukaisu
        it('Aktivoidaan määrämitattavien töiden taulukko', function () {
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko').should('not.be.visible');
            cy.get('label[for="laskutukseen-perustuen"]').click();
        });

        it('Taulukon arvot alussa oikein', function () {
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaOtsikot(['', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['1. hoitovuosi', '2. hoitovuosi', '3. hoitovuosi', '4. hoitovuosi', '5. hoitovuosi'])
                .testaaRivienArvot([1], [0, 1], ['', '', '', '', ''])
                .testaaRivienArvot([1], [0, 2], ['0,00', '0,00', '0,00', '0,00', '0,00'])
                .testaaRivienArvot([1], [0, 3], ['0,00', '0,00', '0,00', '0,00', '0,00'])

        });

        it('Muokkaa ensimmäisen vuoden arvoja ilman alaskopiointia', function () {
            // Disabloidaan "Kopioi hankinnat tuleville hoitovuosille", jonka tulisi olla defaulttina päällä"
            cy.get('input[id="kopioi-hankinnat-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck();

            // Avaa 1. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko',
                0, 0, 1, '10');
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko',
                0, 11, 1, '10', true);


            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(20),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 20, 1))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 131);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 131);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 131);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 1, 131);
        });

        it('Muokkaa ensimmäisen vuoden arvoja alaskopioinnin kanssa', function () {
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko',
                0, 1, 1, '10');
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '', ks.formatoiArvoDesimaalinumeroksi(120),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 120, 1))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 231);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 231);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 231);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 1, 231);
        });

        it('Muokkaa arvot tuleville hoitokausille', function () {
            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu edellisten testien toimesta.
            cy.get('input[id="kopioi-hankinnat-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Avaa 2. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .taulukonOsaPolussa([1, 1, 0, 0])
                .click();

            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko',
                1, 0, 1, '50');
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '', ks.formatoiArvoDesimaalinumeroksi(2520),
                        ks.formatoiArvoDesimaalinumeroksi(ks.summaaJaIndeksikorjaaArvot(indeksit, [120, 600, 600, 600, 600]))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 3, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 3, 660);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 4, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 4, 660);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 5, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 5, 660);
            ks.tarkastaHintalaskurinYhteensaArvo('hankintakustannukset-hintalaskuri', [231, 822, 660, 660, 660]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'hankintakustannukset-indeksilaskuri', [231, 822, 660, 660, 660]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 3, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 3, 660);
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 4, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 4, 660);
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 5, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 5, 660);
            ks.tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri', [231, 822, 660, 660, 660]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'tavoitehinnan-indeksilaskuri', [231, 822, 660, 660, 660]);
        });
    });

    // #### Toimenpiteen rahavaraukset taulukko ####
    describe('Testaa "Toimenpiteen rahavaraukset" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kustannusarvioitu-tyo').as('tallenna-kustannusarvioitu-tyo');

        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="hankintakustannukset-rahavaraukset-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" ei ole aktiivinen.
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="hankintakustannukset-rahavaraukset-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')


            cy.get('#rahavaraukset-taulukko')
                .testaaOtsikot(['Talvihoito', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['Vahinkojen korjaukset', 'Äkillinen hoitotyö'])
                .testaaRivienArvot([1], [0, 1], ['', ''])
                .testaaRivienArvot([1], [0, 2], ['0,00', '0,00'])
                .testaaRivienArvot([1], [0, 3], ['0,00', '0,00'])
        });

        it('Muokkaa "vahinkojen korvaukset" arvoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
            // Avaa vahinkojen korvaukset alitaulukko
            cy.get('#rahavaraukset-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#rahavaraukset-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            ks.muokkaaLaajennaRivinArvoa(
                'rahavaraukset-taulukko',
                0, 0, 1, '10')
            ks.muokkaaLaajennaRivinArvoa(
                'rahavaraukset-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#rahavaraukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(20),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 20, 1))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 251);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 251);
        })

        it('Muokkaa "vahinkojen korvaukset" arvoja jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // NOTE: Tässä oletetaan, rivi on laajennettu ja "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            //       aktivoitu edellisessä testissä.

            // Täytä ensimmäisen kuukauden arvo
            ks.muokkaaLaajennaRivinArvoa(
                'rahavaraukset-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#rahavaraukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(120),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 120, 1))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 351);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 351);
        })

        it('Muokkaa "vahinkojen korvaukset" arvot tuleville hoitokausille', function () {
            // Disabloi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#rahavaraukset-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('be.checked')
                .uncheck();

            // Sulje vahinkojen korvaukset alitaulukko
            cy.get('#rahavaraukset-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0, 0])
                .click();

            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="hankintakustannukset-rahavaraukset-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä arvo "vahinkojen korvaukset" riville 1. hoitovuodelle
            ks.muokkaaRivinArvoa('rahavaraukset-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Valitse 3. hoitovuosi alasvetovalikosta
            cy.get('div[data-cy="hankintakustannukset-rahavaraukset-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('3.')
                .click();

            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu defaulttina.
            cy.get('div[data-cy="hankintakustannukset-rahavaraukset-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Täytä arvo "vahinkojen korvaukset" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            ks.muokkaaRivinArvoa('rahavaraukset-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#rahavaraukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(120),
                        // HUOM: Yllä valittiin 3. hoitovuosi, joka on nyt valittuna. Joten tarkastetaan 3. hoitovuoden indeksi.
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 120, 3))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 351);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 351);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 2, 822);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 2, 822);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 3, 780);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 3, 780);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 4, 780);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 4, 780);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 5, 780);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 5, 780);
            ks.tarkastaHintalaskurinYhteensaArvo('hankintakustannukset-hintalaskuri',
                [351, 822, 780, 780, 780]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'hankintakustannukset-indeksilaskuri',
                [351, 822, 780, 780, 780]);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Palauta 1. hoitovuosi valituksi muita testejä varten
            cy.get('div[data-cy="hankintakustannukset-rahavaraukset-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Disabloi "Kopioi hankinnat tuleville hoitovuosille".
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="hankintakustannukset-rahavaraukset-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck();
        });
    })
});


// -----------------------------
// --- Erillishankinnat osio ---
// -----------------------------

describe('Erillishankinnat osio', function () {
    describe('Testaa "erillishankinnat" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kustannusarvioitu-tyo').as('tallenna-kustannusarvioitu-tyo');

        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" ei ole aktiivinen.
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')

            cy.get('#erillishankinnat-taulukko')
                .testaaOtsikot(['', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['Erillishankinnat'])
                .testaaRivienArvot([1], [0, 1], [''])
                .testaaRivienArvot([1], [0, 2], ['0,00'])
                .testaaRivienArvot([1], [0, 3], ['0,00'])
        });

        it('Muokkaa erillishankintojen arvoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
            // Avaa Erillishankinnat alitaulukko
            cy.get('#erillishankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#erillishankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            ks.muokkaaLaajennaRivinArvoa(
                'erillishankinnat-taulukko',
                0, 0, 1, '10')
            ks.muokkaaLaajennaRivinArvoa(
                'erillishankinnat-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            // FIXME: Erillishankinnat tuottaa jostain syystä tallenna-budjettitavoite kutsun duplikaattina!
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#erillishankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(20),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 20, 1))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 1, 20);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 1, 20);
        })

        it('Muokkaa vahinkojen arvoja jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // NOTE: Tässä oletetaan, rivi on laajennettu ja "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            //       aktivoitu edellisessä testissä.

            // Täytä ensimmäisen kuukauden arvo
            ks.muokkaaLaajennaRivinArvoa(
                'erillishankinnat-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#erillishankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(120),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 120, 1))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 1, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 1, 120);
        })

        it('Muokkaa arvot tuleville hoitokausille', function () {
            // Sulje Erillishankinnat alitaulukko
            cy.get('#erillishankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0, 0])
                .click();

            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä arvo "Erillishankinnat" riville 1. hoitovuodelle
            ks.muokkaaRivinArvoa('erillishankinnat-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Valitse 3. hoitovuosi alasvetovalikosta
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('3.')
                .click();

            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu defaulttina.
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Täytä arvo "Erillishankinnat" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            ks.muokkaaRivinArvoa('erillishankinnat-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#erillishankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        // Erillishankinnat-taulukon yhteenvetorivillä lasketaan kaikki hoitovuodet yhteen.
                        //   Sama pitää ottaa huomioon testissäkin.
                        ks.formatoiArvoDesimaalinumeroksi(/*1.*/ 120 + /*2.*/ 120 + /*3.*/ 0 + /*4.*/ 120 + /*5.*/ 120),
                        ks.formatoiArvoDesimaalinumeroksi(
                            /*1.*/ks.indeksikorjaaArvo(indeksit, 120, 1) +
                            /*2.*/ ks.indeksikorjaaArvo(indeksit, 0, 3) +
                            /*3.*/  ks.indeksikorjaaArvo(indeksit, 120, 3) +
                            /*4.*/ ks.indeksikorjaaArvo(indeksit, 120, 3) +
                            /*5.*/ ks.indeksikorjaaArvo(indeksit, 120, 3)
                        )]);

            // Tarkasta hankintakustannukset osion yhteenveto
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 1, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 1, 120);
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 2, 0);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 2, 0);
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 3, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 3, 120);
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 4, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 4, 120);
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 5, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 5, 120);
            ks.tarkastaHintalaskurinYhteensaArvo('erillishankinnat-hintalaskuri', [120, 0, 120, 120, 120]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'erillishankinnat-indeksilaskuri', [120, 0, 120, 120, 120]);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Palauta 1. hoitovuosi valituksi muita testejä varten
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Disabloi "Kopioi hankinnat tuleville hoitovuosille".
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck()
        });
    })
});


// --------------------------------------
// --- Johto- ja hallintokorvaus osio ---
// --------------------------------------

describe('Johto- ja hallintokorvaus osio', function () {
    describe('Testaa "tuntimäärät ja -palkat" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset').as('tallenna-johto-ja-hallintokorvaukset');
        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" ei ole aktiivinen.
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')

            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .testaaOtsikot(['Toimenkuva', 'Tunnit/kk, h', 'Tuntipalkka, €', 'Yhteensä/kk', 'kk/v'])
                .testaaRivienArvot([1], [0, 0], ['Sopimusvastaava'])
                .testaaRivienArvot([1], [0, 1], [''])
                .testaaRivienArvot([1], [0, 2], [''])
                .testaaRivienArvot([1], [0, 3], [''])
                .testaaRivienArvot([1], [0, 4], ['12'])
        });

        it('Muokkaa Sopimusvastaava-toimenkuvan tunteja ja palkkoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
            // Avaa "Sopimusvastaava" alitaulukko
            ks.toggleLaajennaRivi('johto-ja-hallintokorvaus-laskulla-taulukko', 'Sopimusvastaava');

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('not.be.checked')
                .check();

            // Aseta tunnit/kk Sopimusvastavaalle
            ks.muokkaaLaajennaRivinArvoa(
                'johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 0, 1, '10')
            ks.muokkaaLaajennaRivinArvoa(
                'johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')

            // Disabloi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen" (seuraavia testejä varten)
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('be.checked')
                .uncheck();


            // Sulje "Sopimusvastaava" alitaulukko (seuraavia testejä varten)
            ks.toggleLaajennaRivi('johto-ja-hallintokorvaus-laskulla-taulukko', 'Sopimusvastaava');

            // Aseta tuntipalkka Sopimusvastaavalle
            //  NOTE: Rivin arvon muokkaaminen ei toimi, mikäli Sopimusvastaava-alitaulukko on auki! (Suljettu yllä)
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 2, '10', true)


            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')


            // -- Arvojen tarkastus --

            // Tarkasta yhteensä/kk -arvo muokatulta riviltä
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .testaaRivienArvot([1], [0, 3], ['vaihtelua/kk'])

            // FIXME: johto-ja-hallintokorvaus-yhteenveto-taulukko arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //        Vanhojen arvojen muokkaukset päivittyvät yhteenvetotaulukkoon normaalisti.
            // Tarkasta arvot taulukkoon liittyvästä yhteenvetotaulukosta (Tuntimäärät ja -palkat taulukon alapuolella)
            // cy.get('#johto-ja-hallintokorvaus-yhteenveto-taulukko')
            //     .testaaRivienArvot([2], [],
            //         ['Yhteensä', '',
            //             ks.formatoiArvoDesimaalinumeroksi(20)]);

            // FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            // Tarkasta Johto- ja hallintokorvau osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, 20);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, 20);
        })

        it('Muokkaa Sopimusvastaava-toimenkuvan tunteja ja palkkoja jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // Avaa "Sopimusvastaava" alitaulukko
            ks.toggleLaajennaRivi('johto-ja-hallintokorvaus-laskulla-taulukko', 'Sopimusvastaava');

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('not.be.checked')
                .check();

            // Täytä tunnit/kk ensimmäisen kuukauden arvo Sopimusvastaavalle
            ks.muokkaaLaajennaRivinArvoa(
                'johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')

            // Disabloi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen" (seuraavia testejä varten)
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('be.checked')
                .uncheck();


            // Sulje "Sopimusvastaava" alitaulukko (seuraavia testejä varten)
            cy.log('Suljetaan "Sopimusvastaava" alitaulukko seuraavia testejä varten..');
            ks.toggleLaajennaRivi('johto-ja-hallintokorvaus-laskulla-taulukko', 'Sopimusvastaava');

            // Aseta tuntipalkka Sopimusvastaavalle
            //  NOTE: Rivin arvon muokkaaminen ei toimi, mikäli Sopimusvastaava-alitaulukko on auki! (Suljettu yllä)
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 2, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')


            // -- Arvojen tarkastus --

            // Tarkasta yhteensä/kk -arvo muokatulta riviltä (10 * 10 = 100€) 1. hoitovuosi
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .testaaRivienArvot([1], [0, 3], ['100'])

            // FIXME: johto-ja-hallintokorvaus-yhteenveto-taulukko arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //        Vanhojen arvojen muokkaukset päivittyvät yhteenvetotaulukkoon normaalisti.
            // Tarkasta arvot taulukkoon liittyvästä yhteenvetotaulukosta (Tuntimäärät ja -palkat taulukon alapuolella)
            // cy.get('#johto-ja-hallintokorvaus-yhteenveto-taulukko')
            //     .testaaRivienArvot([2], [],
            //         ['Yhteensä', '',
            //             ks.formatoiArvoDesimaalinumeroksi(20)]);

            // FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            // Tarkasta Johto- ja hallintokorvau osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);
        })

        it('Muokkaa Sopimusvastaava-toimenkuvan tunteja ja palkkoja tuleville hoitokausille', function () {
            // HUOM: Tässä testissä oletetaan, että tuntimäärät ja palkat alitaulukko on suljettu (Sitä ei varmisteta..)

            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä tunnit/kk arvo "Sopimusvastaava" riville 1. hoitovuodelle
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko', 0, 1, '10')

            // Aseta tuntipalkka Sopimusvastaavalle
            //  NOTE: Rivin arvon muokkaaminen ei toimi, mikäli Sopimusvastaava-alitaulukko on auki! (Suljettu yllä)
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 2, '20')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')

            // Tarkasta yhteensä/kk -arvo muokatulta riviltä (10 * 20 = 200€)
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .testaaRivienArvot([1], [0, 3], ['200'])


            // Valitse 3. hoitovuosi alasvetovalikosta
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('3.')
                .click();

            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu defaulttina.
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Täytä arvo "Sopimusvastaava" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko', 0, 1, '10')
            // Aseta tuntipalkka Sopimusvastaavalle
            //  NOTE: Rivin arvon muokkaaminen ei toimi, mikäli Sopimusvastaava-alitaulukko on auki! (Suljettu yllä)
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 2, '5')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')


            // -- Arvojen tarkastus --

            // Tarkasta yhteensä/kk -arvo muokatulta riviltä (10 * 10 = 100€) 3. hoitovuosi
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .testaaRivienArvot([1], [0, 3], ['50'])


            // FIXME: johto-ja-hallintokorvaus-yhteenveto-taulukko arvot eivät muutu kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //        Vanhojen arvojen muokkaukset päivittyvät yhteenvetotaulukkoon normaalisti.
            // Tarkasta arvot taulukkoon liittyvästä yhteenvetotaulukosta (Tuntimäärät ja -palkat taulukon alapuolella)
            // cy.get('#johto-ja-hallintokorvaus-yhteenveto-taulukko')
            //     .testaaRivienArvot([2], [],
            //         ['Yhteensä', '',
            //             ks.formatoiArvoDesimaalinumeroksi(20)]);


            // FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            // Tarkasta Johto- ja hallintokorvau osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Palauta 1. hoitovuosi valituksi muita testejä varten
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Disabloi "Kopioi hankinnat tuleville hoitovuosille".
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck()
        });
    });


    describe('Testaa "Johto ja hallinto: Muut kulut" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kustannusarvioitu-tyo').as('tallenna-kustannusarvioitu-tyo');
        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" ei ole aktiivinen.
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')

            cy.get('#toimistokulut-taulukko')
                .testaaOtsikot(['', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['Toimistokulut, Pientarvikevarasto'])
                .testaaRivienArvot([1], [0, 1], [''])
                .testaaRivienArvot([1], [0, 2], ['0,00'])
                .testaaRivienArvot([1], [0, 3], ['0,00'])
        });

        it('Muokkaa "Toimistokulut, pientarvikevarasto" arvoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
            // Avaa "Toimistokulut, pientarvikevarasto" alitaulukko
            ks.toggleLaajennaRivi('toimistokulut-taulukko', 'Toimistokulut');

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#toimistokulut-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            ks.muokkaaLaajennaRivinArvoa(
                'toimistokulut-taulukko',
                0, 0, 1, '10')
            ks.muokkaaLaajennaRivinArvoa(
                'toimistokulut-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            // FIXME: "Johto ja hallinto: Muut kulut" taulukko tuottaa jostain syystä tallenna-budjettitavoite kutsun duplikaattina!
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#toimistokulut-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(20),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 20, 1))]);

            /// FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //         Kyseinen ongelma täytyy korjata ennen kuin voidaan testata osion yhteenvedon summia reaaliaikaisesti.
            // Tarkasta Johto- ja hallintokorvau osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Sulje "Toimistokulut, pientarvikevarasto" alitaulukko (seuraavia testejä varten)
            ks.toggleLaajennaRivi('toimistokulut-taulukko', 'Toimistokulut');
        })

        it('Muokkaa "Toimistokulut, pientarvikevarasto" arvoja jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // Avaa "Toimistokulut, pientarvikevarasto" alitaulukko
            ks.toggleLaajennaRivi('toimistokulut-taulukko', 'Toimistokulut');

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#toimistokulut-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            // Täytä ensimmäisen kuukauden arvo
            ks.muokkaaLaajennaRivinArvoa(
                'toimistokulut-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#toimistokulut-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(120),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 120, 1))]);

            /// FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //         Kyseinen ongelma täytyy korjata ennen kuin voidaan testata osion yhteenvedon summia reaaliaikaisesti.
            // Tarkasta Johto- ja hallintokorvau osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Sulje "Toimistokulut, pientarvikevarasto" alitaulukko (seuraavia testejä varten)
            ks.toggleLaajennaRivi('toimistokulut-taulukko', 'Toimistokulut');
        })

        it('Muokkaa "Toimistokulut, pientarvikevarasto" arvot tuleville hoitokausille', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä arvo "Toimistokulut, pientarvikevarasto" riville 1. hoitovuodelle
            ks.muokkaaRivinArvoa('toimistokulut-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Valitse 3. hoitovuosi alasvetovalikosta
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('3.')
                .click();

            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu defaulttina.
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Täytä arvo "Toimistokulut, pientarvikevarasto" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            ks.muokkaaRivinArvoa('toimistokulut-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#toimistokulut-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        // Hoidonjohtopalkkiot-taulukon yhteenvetorivillä lasketaan kaikki hoitovuodet yhteen.
                        //   Sama pitää ottaa huomioon testissäkin.
                        ks.formatoiArvoDesimaalinumeroksi(/*1.*/ 120 + /*2.*/ 120 + /*3.*/ 0 + /*4.*/ 120 + /*5.*/ 120),
                        ks.formatoiArvoDesimaalinumeroksi(
                            /*1.*/ks.indeksikorjaaArvo(indeksit, 120, 1) +
                            /*2.*/ ks.indeksikorjaaArvo(indeksit, 0, 3) +
                            /*3.*/  ks.indeksikorjaaArvo(indeksit, 120, 3) +
                            /*4.*/ ks.indeksikorjaaArvo(indeksit, 120, 3) +
                            /*5.*/ ks.indeksikorjaaArvo(indeksit, 120, 3)
                        )]);

            /// FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //         Kyseinen ongelma täytyy korjata ennen kuin voidaan testata osion yhteenvedon summia reaaliaikaisesti.
            // Tarkasta Johto- ja hallintokorvau osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Palauta 1. hoitovuosi valituksi muita testejä varten
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Disabloi "Kopioi hankinnat tuleville hoitovuosille".
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck()
        });
    })
});


// -------------------------------
// --- Hoidonjohtopalkkio osio ---
// -------------------------------

describe('Hoidonjohtopalkkio osio', function () {
    describe('Testaa "hoidonjohtopalkkio" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kustannusarvioitu-tyo').as('tallenna-kustannusarvioitu-tyo');
        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" ei ole aktiivinen.
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')

            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaOtsikot(['', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['Hoidonjohtopalkkio'])
                .testaaRivienArvot([1], [0, 1], [''])
                .testaaRivienArvot([1], [0, 2], ['0,00'])
                .testaaRivienArvot([1], [0, 3], ['0,00'])
        });

        it('Muokkaa hoidonjohtopalkkion arvoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
            // Avaa hoidonjohtopalkkio alitaulukko
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            ks.muokkaaLaajennaRivinArvoa(
                'hoidonjohtopalkkio-taulukko',
                0, 0, 1, '10')
            ks.muokkaaLaajennaRivinArvoa(
                'hoidonjohtopalkkio-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            // FIXME: Hoidonjohtopalkkiot taulukko tuottaa jostain syystä tallenna-budjettitavoite kutsun duplikaattina!
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(20),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 20, 1))]);

            // Tarkasta hoidonjohtopalkkio osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 1, 20);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 1, 20);
        })

        it('Muokkaa hoidonjohtopalkkion jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // NOTE: Tässä oletetaan, rivi on laajennettu ja "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            //       aktivoitu edellisessä testissä.

            // Täytä ensimmäisen kuukauden arvo
            ks.muokkaaLaajennaRivinArvoa(
                'hoidonjohtopalkkio-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(120),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 120, 1))]);

            // Tarkasta hoidonjohtopalkkio osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 1, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 1, 120);
        })

        it('Muokkaa hoidonjohtopalkkion arvot tuleville hoitokausille', function () {
            // Disabloi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('be.checked')
                .uncheck();

            // Sulje hoidonjohtopalkkio alitaulukko
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0, 0])
                .click();

            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä arvo "hoidonjohtopalkkio" riville 1. hoitovuodelle
            ks.muokkaaRivinArvoa('hoidonjohtopalkkio-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Valitse 3. hoitovuosi alasvetovalikosta
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('3.')
                .click();

            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu defaulttina.
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Täytä arvo "hoidonjohtopalkkio" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            ks.muokkaaRivinArvoa('hoidonjohtopalkkio-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        // Hoidonjohtopalkkiot-taulukon yhteenvetorivillä lasketaan kaikki hoitovuodet yhteen.
                        //   Sama pitää ottaa huomioon testissäkin.
                        ks.formatoiArvoDesimaalinumeroksi(/*1.*/ 120 + /*2.*/ 120 + /*3.*/ 0 + /*4.*/ 120 + /*5.*/ 120),
                        ks.formatoiArvoDesimaalinumeroksi(
                            /*1.*/ks.indeksikorjaaArvo(indeksit, 120, 1) +
                            /*2.*/ ks.indeksikorjaaArvo(indeksit, 0, 3) +
                            /*3.*/  ks.indeksikorjaaArvo(indeksit, 120, 3) +
                            /*4.*/ ks.indeksikorjaaArvo(indeksit, 120, 3) +
                            /*5.*/ ks.indeksikorjaaArvo(indeksit, 120, 3)
                        )]);


            // Tarkasta hoidonjohtopalkkio osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 1, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 1, 120);
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 2, 0);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 2, 0);
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 3, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 3, 120);
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 4, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 4, 120);
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 5, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 5, 120);
            ks.tarkastaHintalaskurinYhteensaArvo('hoidonjohtopalkkio-hintalaskuri', [120, 0, 120, 120, 120]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', [120, 0, 120, 120, 120]);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Palauta 1. hoitovuosi valituksi muita testejä varten
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Disabloi "Kopioi hankinnat tuleville hoitovuosille".
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck()
        });
    })
});


// ------------------------------------
// --- Tilaajan rahavaraukset osio ---
// ------------------------------------

// Varaukset mm. bonuksien laskemista varten. Näitä varauksia ei lasketa mukaan tavoitehintaan.
// Tämän blokin testien rahasummia ei siis testata tavoitehinnan yhteenvedosta testipatterin viimeisessä testissä!
describe('Tilaajan rahavaraukset osio', function () {
    describe('Testaa tilaajan varaukset taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kustannusarvioitu-tyo').as('tallenna-kustannusarvioitu-tyo');
        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="tilaajan-varaukset-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" ei ole aktiivinen.
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="tilaajan-varaukset-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')

            cy.get('#tilaajan-varaukset-taulukko')
                .testaaOtsikot(['', 'Määrä €/kk', 'Yhteensä'])
                .testaaRivienArvot([1], [0, 0], ['Tilaajan varaukset'])
                .testaaRivienArvot([1], [0, 1], [''])
                .testaaRivienArvot([1], [0, 2], ['0,00'])
        });

        it('Muokkaa tilaajan-varaukset arvoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
            // Avaa vahinkojen korvaukset alitaulukko
            cy.get('#tilaajan-varaukset-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#tilaajan-varaukset-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            ks.muokkaaLaajennaRivinArvoa(
                'tilaajan-varaukset-taulukko',
                0, 0, 1, '10')
            ks.muokkaaLaajennaRivinArvoa(
                'tilaajan-varaukset-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#tilaajan-varaukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(20)]);

            // TODO: Tarkasta tilaajan rahavaraukset osion yhteenveto!
        })

        it('Muokkaa tilaajan-varaukset jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // NOTE: Tässä oletetaan, rivi on laajennettu ja "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            //       aktivoitu edellisessä testissä.

            // Täytä ensimmäisen kuukauden arvo
            ks.muokkaaLaajennaRivinArvoa(
                'tilaajan-varaukset-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#tilaajan-varaukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(120)]);

            // TODO: Tarkasta tilaajan rahavaraukset osion yhteenveto!
        })

        it('Muokkaa tilaajan-varaukset arvot tuleville hoitokausille', function () {
            // Disabloi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#tilaajan-varaukset-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('be.checked')
                .uncheck();

            // Sulje vahinkojen korvaukset alitaulukko
            cy.get('#tilaajan-varaukset-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0, 0])
                .click();

            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="tilaajan-varaukset-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä arvo "vahinkojen korvaukset" riville 1. hoitovuodelle
            ks.muokkaaRivinArvoa('tilaajan-varaukset-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Valitse 3. hoitovuosi alasvetovalikosta
            cy.get('div[data-cy="tilaajan-varaukset-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('3.')
                .click();

            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu defaulttina.
            cy.get('div[data-cy="tilaajan-varaukset-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Täytä arvo "vahinkojen korvaukset" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            ks.muokkaaRivinArvoa('tilaajan-varaukset-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#tilaajan-varaukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        // Tilaajan varaukset taulukon yhteenvetorivillä lasketaan kaikki hoitovuodet yhteen.
                        //   Sama pitää ottaa huomioon testissäkin.
                        ks.formatoiArvoDesimaalinumeroksi(
                            /*1.*/ 120 +
                            /*2.*/ 120 +
                            /*3.*/ 0 +
                            /*4.*/ 120 +
                            /*5.*/ 120)

                        // -- Rahavarauksille ei lasketa indeksikorjauksia. --
                    ]);

            // Palauta 1. hoitovuosi valituksi muita testejä varten
            cy.get('div[data-cy="tilaajan-varaukset-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Disabloi "Kopioi hankinnat tuleville hoitovuosille".
            // FIXME: Tämä vaihe muuttuu tarpeettomaksi, kun toteutetaan: https://issues.solita.fi/browse/VHAR-5213
            cy.get('div[data-cy="tilaajan-varaukset-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck()

            // TODO: Tarkasta tilaajan rahavaraukset osion yhteenveto!
        });
    })

})

// -----------------------------------
// --- Tavoite- ja kattohinta osio ---
// -----------------------------------

describe('Tavoite- ja kattohinta osio', function () {
    //TODO: Kattohinta: Tulevaisuudessa 2019 ja 2020 alkaneille urakoille käyttäjä syöttää kattohinnan manuaalisesti
    //       * Kenttien validointi: Ko vuoden Kattohinta ei saa olla pienempi kuin ko vuoden Tavoitehinta
    //       * Virheilmoitus käyttäjälle: "Kattohinta ei voi olla pienempi kuin tavoitehinta."
    //       * Lasketaan indeksikorjaus
    //       * Ts. seuraavat 5 vuotta tulee olemaan urakoita, joissa kattohinta pitää kirjata manuaalisesti
});


describe('Tarkasta tallennetut arvot', function () {
    describe('Lataa sivu uudestaan ja tarkasta, että kaikki osioissa tallennettu data löytyy.', function () {
        it('Lataa sivu', function () {

            // HUOM:
            //   Reload saattaa cancelata meneillään olevan tallennus XHR-kyselyn edeltävästä testistä.
            //   Siksi on tärkeää käyttää cy.wait-komentoa edeltävissä testeissä, jotka tekevät muokkauksia ja tallentavat arvoja tietokantaan.
            //   Jokainen tallennuskysely tulee interceptata ja odottaa erikseen cy.wait-kutsulla. (Katso esimerkkiä testeistä).
            //   Näin varmistutaan, että kaikki arvot on tallennettu tietokantaan ennen lopullista arvojen testaamista.
            cy.reload();
            cy.get('.ajax-loader', { timeout: 40000 }).should('not.exist');
        });

        // Tavoite- ja kattohinta osion yhteenvetolaatikoiden testit
        it('Testaa arvot tavoite- ja kattohinta osiossa', function () {
            // Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio
            ks.tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri',
                [3111, 822, 1740, 1740, 1740]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'tavoitehinnan-indeksilaskuri',
                [3111, 822, 1740, 1740, 1740]);

            // (Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1
            ks.tarkastaHintalaskurinYhteensaArvo('kattohinnan-hintalaskuri',
                [3111 * 1.1, 822 * 1.1, 1740 * 1.1, 1740 * 1.1, 1740 * 1.1]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'kattohinnan-indeksilaskuri',
                [3111 * 1.1, 822 * 1.1, 1740 * 1.1, 1740 * 1.1, 1740 * 1.1]);
        });
    });
})