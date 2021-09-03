import * as f from '../support/taulukkoFns.js';
import transit from "transit-js";

// Täytetään ajax kutsun vastauksen perusteella
let indeksit = [];
let ivalonUrakkaId = 35;

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
    });
}

function alustaIvalonUrakka() {
    alustaKanta();
}

function testaaTilayhteenveto(osio, onkoVahvistettu) {
    cy.get('#tilayhteenveto').then(($grid) => {
        //TODO: Testaa, että valitun hoitovuoden vahvistukset/vahvistamattomuus näkyy oikein tilayhteenvedossa
        //TODO: Testaa, että arvot näkyvät oikein tilayhteenvedossa
    })
}

/**
 * Muokkaa input kentän arvoa taulukossa päätasolla.
 *
 * Olettaa että kyseessä on taulukko, jolla on headeri. Eli päätason rivit löytyvät bodysta (index 1)
 * Tällä funktiolla voi muokata taulukon päätason rivejä, mikäli solussa on suoraan input kenttä.
 * Jos on tarvetta muokata laajennetun rivin rivejä, niin käytä muokkaaLaajennaRivinArvoa-funktiota.
 *
 *
 * @param {string} taulukonId
 * @param {number} rivinIndex Pitää olla int
 * @param {number} sarakkeenIndex Pitää olla int
 * @param {string} arvo Arvo, joka kirjoitetaan input-kenttään
 * @param {boolean} [blurEvent=false] Kutsu blur-eventtiä inputille manuaalisesti, jos inputin blur-event ei triggeröidy. Esim. kenttä on viimeinen muokattava taulukossa.
 */
function muokkaaRivinArvoa(taulukonId, rivinIndex, sarakkeenIndex, arvo, blurEvent = true) {
    const kirjoitettavaArvo = '{selectall}{backspace}' + arvo;

    cy.get('#' + taulukonId)
        .taulukonOsaPolussa([1, 0, rivinIndex, sarakkeenIndex])
        .click()
        .type(kirjoitettavaArvo)

        // Jos input-kenttä on viimeinen, johon kirjoitetaan, on syytä kutsua blur-eventtiä manuaalisesti, jotta blur-event
        // triggeröityy.
        .then(($input) => {
            if (blurEvent) {
                cy.wrap($input).blur();
            }
        });

    // Odota hetki, jotta tallennusfunktio triggeröityy varmasti.
    // Välillä tallennuksen triggeröityminen (blurrin tapahtuessa) vaikuttaisi olevan flaky.
    // FIXME: Katsotaan auttaako wait, vai pitääkö tutkia cypress blur-mekaniikkaa tarkemmin.
    cy.wait(1000)
}

/**
 * Muokkaa input kentän arvoa alitaulukossa.
 *
 * Olettaa että kyseessä on taulukko, jolla on headeri. Eli Laajennarivit löytyy bodysta (index 1)
 * Laajennarivien oletetaan myös olevan muotoa jossa on header-rivi ja body. Eli tällä voi muokata
 * bodyn jotain riviä.
 *
 * Kyseessä on siis Cypressin async event, joka ajetaan.
 *
 * @param {string} taulukonId
 * @param {number} laajennaRivinIndex Pitää olla int
 * @param {number} rivinIndex Pitää olla int
 * @param {number} sarakkeenIndex Pitää olla int
 * @param {string} arvo
 * @param {boolean} [blurEvent=false] Kutsu blur-eventtiä inputille manuaalisesti, jos inputin blur-event ei triggeröidy. Esim. kenttä on viimeinen muokattava taulukossa.
 */
function muokkaaLaajennaRivinArvoa(taulukonId, laajennaRivinIndex, rivinIndex, sarakkeenIndex, arvo, blurEvent = false) {
    const kirjoitettavaArvo = '{selectall}{backspace}' + arvo;

    cy.get('#' + taulukonId)
        .taulukonOsaPolussa([1, laajennaRivinIndex, 1, rivinIndex, sarakkeenIndex])
        .find('input')
        // Huom: Jos input-kenttä ei ole fokusoitu ennen type-kutsua, niin cypress kutsuu automaattisesti click-funktiota
        //       elementille ja fokusoi sen. Jos tämän jälkeen kirjoitetaan toiseen input-kenttään, niin tämä kyseinen
        //       input-kenttä menettää fokuksen ja kutsuu lähettää automaattisesti blur-eventin.
        .type(kirjoitettavaArvo)

        // Jos input-kenttä on viimeinen, johon kirjoitetaan, on syytä kutsua blur-eventtiä manuaalisesti, jotta blur-event
        // triggeröityy.
        .then(($input) => {
            if (blurEvent) {
                cy.wrap($input).blur();
            }
        });

    // Odota hetki, jotta tallennusfunktio triggeröityy varmasti.
    // Välillä tallennuksen triggeröityminen (blurrin tapahtuessa) vaikuttaisi olevan flaky.
    // FIXME: Katsotaan auttaako wait, vai pitääkö tutkia cypress blur-mekaniikkaa tarkemmin.
    cy.wait(1000)
}

function formatoiArvoDesimaalinumeroksi(arvo) {
    let formatoituArvo = '' + (Math.round((arvo + Number.EPSILON) * 100) / 100);
    formatoituArvo = parseFloat(formatoituArvo).toFixed(2);
    formatoituArvo = formatoituArvo.replace(/^(\d*)(\.?)/, (osuma, p1, p2, offset, kokoNumero) => {
        let numeroArray = p1.split('').reverse();
        let korvaavaArray = [];
        for (let i = 0; i < numeroArray.length; i++) {
            if ((i + 1) % 3 === 0) {
                // Google closure formatointi käyttää 160 koodia välilyönnin sijasta
                korvaavaArray.push(numeroArray[i], String.fromCharCode(160));
            } else {
                korvaavaArray.push(numeroArray[i]);
            }
        }
        return korvaavaArray.reverse().join('').trim() + p2;
    });
    return formatoituArvo.replace('.', ',');
}

function formatoiArvoEuromuotoiseksi(arvo) {
    return formatoiArvoDesimaalinumeroksi(arvo) + ' €';
}

function indeksikorjaaArvo(arvo, hoitokaudenNumero) {
    return indeksit[hoitokaudenNumero - 1] * arvo;
}

function summaaJaIndeksikorjaaArvot(arvot) {
    let yhteensaArvo = 0;
    for (let i = 0; i < arvot.length; i++) {
        yhteensaArvo += indeksikorjaaArvo(arvot[i], i + 1);
    }
    return yhteensaArvo;
}

function summaaArvot(arvot) {
    let yhteensaArvo = 0;
    arvot.forEach((arvo) => {
        yhteensaArvo += arvo;
    });
    return yhteensaArvo;
}

function hintalaskurinTarkastus(dataCy, hoitokaudenNumero, formatoituArvo) {
    let index;
    if (hoitokaudenNumero === 'yhteensa') {
        index = 6;
    } else {
        index = hoitokaudenNumero - 1;
    }
    cy.get('[data-cy=' + dataCy + ']')
        .find('.hintalaskurisarake-ala')
        .eq(index)
        .should('have.text', formatoituArvo)
}

/**
 * Tällä voi testata, että hintalaskurin arvo on oikein.
 *
 * Testaus tehdään Cypressin async eventissä.
 *
 * @param {string} dataCy
 * @param {number} hoitokaudenNumero Pitää olla int
 * @param {number} arvo
 */
function tarkastaHintalaskurinArvo(dataCy, hoitokaudenNumero, arvo) {
    let formatoituArvo = formatoiArvoEuromuotoiseksi(arvo);
    hintalaskurinTarkastus(dataCy, hoitokaudenNumero, formatoituArvo);
}

/**
 * Tällä voi testata, että indeksilaskurin arvo on oikein.
 *
 * Testaus tehdään Cypressin async eventissä.
 *
 * @param {string} dataCy
 * @param {number} hoitokaudenNumero Pitää olla int
 * @param {number} arvo
 */
function tarkastaIndeksilaskurinArvo(dataCy, hoitokaudenNumero, arvo) {
    let formatoituArvo = formatoiArvoEuromuotoiseksi(indeksikorjaaArvo(arvo, hoitokaudenNumero));
    hintalaskurinTarkastus(dataCy, hoitokaudenNumero, formatoituArvo);
}


/**
 *
 * @param {string} dataCy
 * @param {array} arvot
 */
function tarkastaHintalaskurinYhteensaArvo(dataCy, arvot) {
    let yhteensaArvo = summaaArvot(arvot);
    hintalaskurinTarkastus(dataCy, 'yhteensa', formatoiArvoEuromuotoiseksi(yhteensaArvo));
}

/**
 *
 * @param {string} dataCy
 * @param {array} arvot
 */
function tarkastaIndeksilaskurinYhteensaArvo(dataCy, arvot) {
    let yhteensaArvo = summaaJaIndeksikorjaaArvot(arvot);
    hintalaskurinTarkastus(dataCy, 'yhteensa', formatoiArvoEuromuotoiseksi(yhteensaArvo));
}

/**
 * Näkyvissä pitäisi olla aina maksimissaan vain yksi "Kopioi allaoleviin" nappi. Tämän funktion avulla sitä klikataan.
 */
function klikkaaTaytaAlas() {
    cy.get('[data-cy=kopioi-allaoleviin]:visible').scrollIntoView().click({ force: true })
    /*then(($nappi) => {
        cy.wait(100);
        cy.wrap($nappi).click();
    })*/;
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

            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 0, 0, 1, '1');
            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 0, 11, 1, '1', true);

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(2),
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(2, 1))]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 2);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 1, 2);

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!
        });

        it('Muokkaa ensimmäisen vuoden arvoja alaskopioinnin kanssa', function () {
            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 0, 1, 1, '10');
            klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(111),
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(111, 1))]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 111);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 1, 111);

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!
        });

        it('Muokkaa toisen vuoden arvoja ilman alaskopiointia', function () {
            // Avaa 2. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-taulukko')
                .taulukonOsaPolussa([1, 1, 0, 0])
                .click();

            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 1, 0, 1, '2');
            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 1, 11, 1, '2', true);

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(115),
                        formatoiArvoDesimaalinumeroksi(summaaJaIndeksikorjaaArvot([111, 4]))]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 2, 4);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 2, 4);

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!

        });

        it('Muokkaa toisen vuoden arvoja alaskopioinnin kanssa', function () {
            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 1, 1, 1, '20');
            klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [], ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(333), formatoiArvoDesimaalinumeroksi(summaaJaIndeksikorjaaArvot([111, 222]))]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 2, 222);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 2, 222);

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!
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

            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 2, 0, 1, '5');
            klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(513),
                        formatoiArvoDesimaalinumeroksi(summaaJaIndeksikorjaaArvot([111, 222, 60, 60, 60]))]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 3, 60);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 3, 60);
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 4, 60);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 4, 60);
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 5, 60);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 5, 60);
            tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri', [111, 222, 60, 60, 60]);
            tarkastaIndeksilaskurinYhteensaArvo('tavoitehinnan-indeksilaskuri', [111, 222, 60, 60, 60]);

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!
        });
    });

// --

    // #### Määrämitattavien töiden taulukko ####
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

            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko', 0, 0, 1, '10');
            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko', 0, 11, 1, '10', true);


            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(20),
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(20, 1))]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 131);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 1, 131);

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!
        });

        it('Muokkaa ensimmäisen vuoden arvoja alaskopioinnin kanssa', function () {
            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko', 0, 1, 1, '10');
            klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(120),
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(120, 1))]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 231);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 1, 231);

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!
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

            muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko',
                1, 0, 1, '50');
            klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(2520),
                        formatoiArvoDesimaalinumeroksi(summaaJaIndeksikorjaaArvot([120, 600, 600, 600, 600]))]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 3, 660);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 3, 660);
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 4, 660);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 4, 660);
            tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 5, 660);
            tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 5, 660);
            tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri', [231, 822, 660, 660, 660]);
            tarkastaIndeksilaskurinYhteensaArvo('tavoitehinnan-indeksilaskuri', [231, 822, 660, 660, 660]);

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!
        });
    });

    // #### Toimenpiteen rahavaraukset taulukko ####
    describe('Testaa toimenpiteen rahavaraukset taulukkoa', function () {
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

        it('Muokkaa vahinkojen arvoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
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

            muokkaaLaajennaRivinArvoa(
                'rahavaraukset-taulukko',
                0, 0, 1, '10')
            muokkaaLaajennaRivinArvoa(
                'rahavaraukset-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#rahavaraukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(20),
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(20, 1))]);

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!
        })

        it('Muokkaa vahinkojen arvoja jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // NOTE: Tässä oletetaan, rivi on laajennettu ja "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            //       aktivoitu edellisessä testissä.

            // Täytä ensimmäisen kuukauden arvo
            muokkaaLaajennaRivinArvoa(
                'rahavaraukset-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#rahavaraukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(120),
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(120, 1))]);

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!
        })

        it('Muokkaa arvot tuleville hoitokausille', function () {
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
            muokkaaRivinArvoa('rahavaraukset-taulukko', 0, 1, '10')

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
            muokkaaRivinArvoa('rahavaraukset-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#rahavaraukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(120),
                        // HUOM: Yllä valittiin 3. hoitovuosi, joka on nyt valittuna. Joten tarkastetaan 3. hoitovuoden indeksi.
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(120, 3))]);

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

            // TODO: Tarkasta hankintakustannukset osion yhteenveto!
        });
    })
});


// -----------------------------
// --- Erillishankinnat osio ---
// -----------------------------

describe('Erillishankinnat osio', function () {
    describe('Testaa erillishankinnat taulukkoa', function () {
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
            // Avaa vahinkojen korvaukset alitaulukko
            cy.get('#erillishankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#erillishankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            muokkaaLaajennaRivinArvoa(
                'erillishankinnat-taulukko',
                0, 0, 1, '10')
            muokkaaLaajennaRivinArvoa(
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


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#erillishankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(20),
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(20, 1))]);

            // TODO: Tarkasta erillishankinnat osion yhteenveto!
        })

        it('Muokkaa vahinkojen arvoja jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // NOTE: Tässä oletetaan, rivi on laajennettu ja "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            //       aktivoitu edellisessä testissä.

            // Täytä ensimmäisen kuukauden arvo
            muokkaaLaajennaRivinArvoa(
                'erillishankinnat-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#erillishankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(120),
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(120, 1))]);

            // TODO: Tarkasta erillishankinnat osion yhteenveto!
        })

        it('Muokkaa arvot tuleville hoitokausille', function () {
            // Disabloi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#erillishankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('be.checked')
                .uncheck();

            // Sulje vahinkojen korvaukset alitaulukko
            cy.get('#erillishankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0, 0])
                .click();

            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä arvo "vahinkojen korvaukset" riville 1. hoitovuodelle
            muokkaaRivinArvoa('erillishankinnat-taulukko', 0, 1, '10')

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

            // Täytä arvo "vahinkojen korvaukset" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            muokkaaRivinArvoa('erillishankinnat-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#erillishankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        // Erillishankinnat-taulukon yhteenvetorivillä lasketaan kaikki hoitovuodet yhteen.
                        //   Sama pitää ottaa huomioon testissäkin.
                        formatoiArvoDesimaalinumeroksi(/*1.*/ 120 + /*2.*/ 120 + /*3.*/ 0 + /*4.*/ 120 + /*5.*/ 120),
                        formatoiArvoDesimaalinumeroksi(
                            /*1.*/indeksikorjaaArvo(120, 1) +
                            /*2.*/ indeksikorjaaArvo(0, 3) +
                            /*3.*/  indeksikorjaaArvo(120, 3) +
                            /*4.*/ indeksikorjaaArvo(120, 3) +
                            /*5.*/ indeksikorjaaArvo(120, 3)
                        )]);

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

            // TODO: Tarkasta erillishankinnat osion yhteenveto!
        });
    })
});


// --------------------------------------
// --- Johto- ja hallintokorvaus osio ---
// --------------------------------------

describe('Johto- ja hallintokorvaus osio', function () {
    //TODO:
});


// -------------------------------
// --- Hoidonjohtopalkkio osio ---
// -------------------------------

describe('Hoidonjohtopalkkio osio', function () {
    describe('Testaa hoidonjohtopalkkio taulukkoa', function () {
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
            // Avaa vahinkojen korvaukset alitaulukko
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            muokkaaLaajennaRivinArvoa(
                'hoidonjohtopalkkio-taulukko',
                0, 0, 1, '10')
            muokkaaLaajennaRivinArvoa(
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


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(20),
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(20, 1))]);

            // TODO: Tarkasta hoidonjohtopalkkio osion yhteenveto!
        })

        it('Muokkaa hoidonjohtopalkkion jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // NOTE: Tässä oletetaan, rivi on laajennettu ja "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            //       aktivoitu edellisessä testissä.

            // Täytä ensimmäisen kuukauden arvo
            muokkaaLaajennaRivinArvoa(
                'hoidonjohtopalkkio-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(120),
                        formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(120, 1))]);

            // TODO: Tarkasta hoidonjohtopalkkio osion yhteenveto!
        })

        it('Muokkaa hoidonjohtopalkkion arvot tuleville hoitokausille', function () {
            // Disabloi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('be.checked')
                .uncheck();

            // Sulje vahinkojen korvaukset alitaulukko
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0, 0])
                .click();

            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä arvo "vahinkojen korvaukset" riville 1. hoitovuodelle
            muokkaaRivinArvoa('hoidonjohtopalkkio-taulukko', 0, 1, '10')

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

            // Täytä arvo "vahinkojen korvaukset" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            muokkaaRivinArvoa('hoidonjohtopalkkio-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        // Hoidonjohtopalkkiot-taulukon yhteenvetorivillä lasketaan kaikki hoitovuodet yhteen.
                        //   Sama pitää ottaa huomioon testissäkin.
                        formatoiArvoDesimaalinumeroksi(/*1.*/ 120 + /*2.*/ 120 + /*3.*/ 0 + /*4.*/ 120 + /*5.*/ 120),
                        formatoiArvoDesimaalinumeroksi(
                            /*1.*/indeksikorjaaArvo(120, 1) +
                            /*2.*/ indeksikorjaaArvo(0, 3) +
                            /*3.*/  indeksikorjaaArvo(120, 3) +
                            /*4.*/ indeksikorjaaArvo(120, 3) +
                            /*5.*/ indeksikorjaaArvo(120, 3)
                        )]);

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

            // TODO: Tarkasta hoidonjohtopalkkio osion yhteenveto!
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

        it('Muokkaa tilaajan-varauksetn arvoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
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

            muokkaaLaajennaRivinArvoa(
                'tilaajan-varaukset-taulukko',
                0, 0, 1, '10')
            muokkaaLaajennaRivinArvoa(
                'tilaajan-varaukset-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#tilaajan-varaukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(20)]);

            // TODO: Tarkasta tilaajan rahavaraukset osion yhteenveto!
        })

        it('Muokkaa tilaajan-varauksetn jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // NOTE: Tässä oletetaan, rivi on laajennettu ja "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            //       aktivoitu edellisessä testissä.

            // Täytä ensimmäisen kuukauden arvo
            muokkaaLaajennaRivinArvoa(
                'tilaajan-varaukset-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#tilaajan-varaukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        formatoiArvoDesimaalinumeroksi(120)]);

            // TODO: Tarkasta tilaajan rahavaraukset osion yhteenveto!
        })

        it('Muokkaa tilaajan-varauksetn arvot tuleville hoitokausille', function () {
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
            muokkaaRivinArvoa('tilaajan-varaukset-taulukko', 0, 1, '10')

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
            muokkaaRivinArvoa('tilaajan-varaukset-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#tilaajan-varaukset-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        // Tilaajan varaukset taulukon yhteenvetorivillä lasketaan kaikki hoitovuodet yhteen.
                        //   Sama pitää ottaa huomioon testissäkin.
                        formatoiArvoDesimaalinumeroksi(/*1.*/ 120 + /*2.*/ 120 + /*3.*/ 0 + /*4.*/ 120 + /*5.*/ 120)

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
            tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri', [591, 822, 1020, 1020, 1020]);
            tarkastaIndeksilaskurinYhteensaArvo('tavoitehinnan-indeksilaskuri', [591, 822, 1020, 1020, 1020]);

            // (Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1
            tarkastaHintalaskurinYhteensaArvo('kattohinnan-hintalaskuri', [591 * 1.1, 822 * 1.1, 1020 * 1.1, 1020 * 1.1, 1020 * 1.1]);
            tarkastaIndeksilaskurinYhteensaArvo('kattohinnan-indeksilaskuri', [591 * 1.1, 822 * 1.1, 1020 * 1.1, 1020 * 1.1, 1020 * 1.1]);
        });
    });
})

// TODO: Kaikkia osioita ei ole testattu!


// Nämä testit voi implementoida sen jälkeen kun vahvistaminen, vahvistuksen peruminen ja vahvistetun osion muokkaus on saatu implementoitua:
//TODO: Tee testi, jossa vahvistetaan osio ja tarkastetaan tilayhteenevedosta näkyykö osio vahvistettuna
//TODO: Tee testi, jossa perutaan vahvistaminen.
//TODO: Tee testi, jossa osiota muokataan vahvistamisen jälkeen.
