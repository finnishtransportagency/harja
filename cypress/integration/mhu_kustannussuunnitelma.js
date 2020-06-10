import * as f from '../support/taulukkoFns.js';
import transit from "transit-js";

// Täytetään ajax kutsun vastauksen perusteella
let indeksit = [];
let ivalonUrakkaId = 35;

function alustaKanta () {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista kiinteähintaiset työt
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM kiinteahintainen_tyo kht " +
            "USING toimenpideinstanssi tpi " +
            "WHERE kht.toimenpideinstanssi = tpi.id AND " +
            "      tpi.urakka = (SELECT id FROM urakka WHERE nimi = 'Ivalon MHU testiurakka (uusi)');\"");
        // Poista kustannusarvioidut työt
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM kustannusarvioitu_tyo kat " +
            "USING toimenpideinstanssi tpi " +
            "WHERE kat.toimenpideinstanssi = tpi.id AND " +
            "      tpi.urakka = (SELECT id FROM urakka WHERE nimi = 'Ivalon MHU testiurakka (uusi)');\"")
    });
}

function alustaIvalonUrakka () {
    alustaKanta();
}

function testaaSuunnitelmienTila(polku, ensimmaisenVuodenTila, toisenVuodenTila) {
    function vuodenTilanLuokka (tila) {
        switch (tila) {
            case 'valmis':
                return 'glyphicon-ok';
            case 'kesken':
                return 'livicon-question';
            case 'aloittamatta':
                return 'glyphicon-remove';
        }
    }

    let ensimmaisenVuodenLuokka = vuodenTilanLuokka(ensimmaisenVuodenTila);
    let toisenVuodenLuokka = vuodenTilanLuokka(toisenVuodenTila);

    cy.get('#suunnitelmien-taulukko').then(($taulukko) => {
        let $rivi = $taulukko;
        let indexLopusta;
        for (let i = 0; i < polku.length; i++) {
            if (i !== (polku.length - 1)) {
                indexLopusta = -1;
            } else {
                indexLopusta = 0;
            }
            $rivi = f.taulukonOsaTeksitllaSync($rivi, polku[i], indexLopusta);
        }
        cy.wrap(f.rivinSarakeSync($rivi, 1).find('span.' + ensimmaisenVuodenLuokka))
            .should('exist');
        cy.wrap(f.rivinSarakeSync($rivi, 2).find('span.' + toisenVuodenLuokka))
            .should('exist');
    })
}

/**
 * Muokkaa input kentän arvoa taulukossa.
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
 * @param {boolean} [blurEvent=false]
 */
function muokkaaLaajennaRivinArvoa(taulukonId, laajennaRivinIndex, rivinIndex, sarakkeenIndex, arvo, blurEvent=false) {
    let kirjoitettavaArvo = '{selectall}{backspace}' + arvo;
    cy.get('#' + taulukonId)
        .taulukonOsaPolussa([1, laajennaRivinIndex, 1, rivinIndex, sarakkeenIndex])
        .find('input')
        .type(kirjoitettavaArvo)
        .then(($input) => {
            if (blurEvent) {
                cy.wrap($input).blur();
            }
        });
}

function formatoiArvoDesimaalinumeroksi (arvo) {
    let formatoituArvo = '' + (Math.round((arvo + Number.EPSILON) * 100) / 100);
    formatoituArvo = parseFloat(formatoituArvo).toFixed(2);
    formatoituArvo = formatoituArvo.replace(/^(\d*)(\.?)/, (osuma, p1, p2, offset, kokoNumero) => {
        let numeroArray = p1.split('').reverse();
        let korvaavaArray = [];
        for (let i=0; i < numeroArray.length; i++) {
            if ((i+1) % 3 === 0) {
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

function formatoiArvoEuromuotoiseksi (arvo) {
    return formatoiArvoDesimaalinumeroksi(arvo) + ' €';
}

function indeksikorjaaArvo(arvo, hoitokaudenNumero) {
    return indeksit[hoitokaudenNumero - 1]*arvo;
}

function summaaJaIndeksikorjaaArvot(arvot) {
    let yhteensaArvo = 0;
    for (let i = 0; i < arvot.length; i++) {
        yhteensaArvo += indeksikorjaaArvo(arvot[i], i+1);
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

function hintalaskurinTarkastus (dataCy, hoitokaudenNumero, formatoituArvo) {
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
function tarkastaHintalaskurinArvo (dataCy, hoitokaudenNumero, arvo) {
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
function tarkastaIndeksilaskurinArvo (dataCy, hoitokaudenNumero, arvo) {
    let formatoituArvo = formatoiArvoEuromuotoiseksi(indeksikorjaaArvo(arvo, hoitokaudenNumero));
    hintalaskurinTarkastus(dataCy, hoitokaudenNumero, formatoituArvo);
}


/**
 *
 * @param {string} dataCy
 * @param {array} arvot
 */
function tarkastaHintalaskurinYhteensaArvo (dataCy, arvot) {
    let yhteensaArvo = summaaArvot(arvot);
    hintalaskurinTarkastus(dataCy, 'yhteensa', formatoiArvoEuromuotoiseksi(yhteensaArvo));
}

/**
 *
 * @param {string} dataCy
 * @param {array} arvot
 */
function tarkastaIndeksilaskurinYhteensaArvo (dataCy, arvot) {
    let yhteensaArvo = summaaJaIndeksikorjaaArvot(arvot);
    hintalaskurinTarkastus(dataCy, 'yhteensa', formatoiArvoEuromuotoiseksi(yhteensaArvo));
}

/**
 * Näkyvissä pitäisi olla aina maksimissaan vain yksi "Kopioi allaoleviin" nappi. Tämän funktion avulla sitä klikataan.
 */
function klikkaaTaytaAlas () {
    cy.get('[data-cy=kopioi-allaoleviin]:visible').scrollIntoView().click({force: true})
    /*then(($nappi) => {
        cy.wait(100);
        cy.wrap($nappi).click();
    })*/;
}

describe('Testaa Inarin MHU urakan kustannussuunnitelmanäkymää', function () {
    before(alustaIvalonUrakka);

    it('Avaa näkymä', function () {
        cy.server();
        cy.visit("/");
        cy.contains('.haku-lista-item', 'Lappi').click();
        cy.get('.ajax-loader', {timeout: 10000}).should('not.be.visible');
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Ivalon MHU testiurakka (uusi)', {timeout: 10000}).click();
        cy.route('POST', '_/budjettisuunnittelun-indeksit').as('budjettisuunnittelun-indeksit');
        cy.get('[data-cy=tabs-taso1-Suunnittelu]', {timeout: 20000}).click();
        // Tässä otetaan indeksikertoimet talteen
        cy.wait('@budjettisuunnittelun-indeksit').then(($xhr) => {
            let reader = transit.reader("json");
            let vastaus = reader.read(JSON.stringify($xhr.response.body));
            vastaus.forEach((transitIndeksiMap) => {
                indeksit.push(transitIndeksiMap.get(transit.keyword('indeksikerroin')));
            });
        });
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');
    });
    it('Avaa suunnitelmien tila taulukon vetolaatikot', function () {
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'Talvihoito').click();
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'Liikenneympäristön hoito').click();
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'Sorateiden hoito').click();
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'Päällystepaikkaukset').click();
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'MHU Ylläpito').click();
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'MHU Korvausinvestointi').click();
    });
});

describe('Testaa hankinnat taulukkoa', function () {
    it('Taulukon arvot alussa oikein', function () {
        testaaSuunnitelmienTila(['Hankintakustannukset'], 'aloittamatta', 'aloittamatta');
        testaaSuunnitelmienTila(['Talvihoito'], 'aloittamatta', 'aloittamatta');
        testaaSuunnitelmienTila(['Talvihoito', 'Suunnitellut hankinnat'], 'aloittamatta', 'aloittamatta');
        cy.get('#suunnittellut-hankinnat-taulukko')
            .testaaOtsikot(['Kiinteät', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
            .testaaRivienArvot([1], [0, 0], ['1. hoitovuosi', '2. hoitovuosi', '3. hoitovuosi', '4. hoitovuosi', '5. hoitovuosi'])
            .testaaRivienArvot([1], [0, 1], ['', '', '', '', ''])
            .testaaRivienArvot([1], [0, 2], ['0,00', '0,00', '0,00', '0,00', '0,00'])
            .testaaRivienArvot([1], [0, 3], ['0,00', '0,00', '0,00', '0,00', '0,00'])

    });

    it('Muokkaa ensimmäisen vuoden arvoja ilman kopiointia', function () {
        cy.get('label[for="kopioi-hankinnat-tuleville-hoitovuosille"]').click();
        cy.get('#suunnittellut-hankinnat-taulukko')
            .taulukonOsaPolussa([1, 0, 0, 0])
            .click();
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-taulukko', 0, 0, 1, '1');
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-taulukko', 0, 11, 1, '1', true);
        testaaSuunnitelmienTila(['Hankintakustannukset'], 'kesken', 'aloittamatta');
        testaaSuunnitelmienTila(['Talvihoito'], 'kesken', 'aloittamatta');
        testaaSuunnitelmienTila(['Talvihoito', 'Suunnitellut hankinnat'], 'kesken', 'aloittamatta');
        cy.get('#suunnittellut-hankinnat-taulukko')
            .testaaRivienArvot([2], [], ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(2), formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(2, 1))]);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 2);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 1, 2);
    });

    it('Muokkaa ensimmäisen vuoden arvoja kopioinnin kanssa', function () {
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-taulukko', 0, 1, 1, '10');
        klikkaaTaytaAlas();
        testaaSuunnitelmienTila(['Hankintakustannukset'], 'kesken', 'aloittamatta');
        testaaSuunnitelmienTila(['Talvihoito'], 'kesken', 'aloittamatta');
        testaaSuunnitelmienTila(['Talvihoito', 'Suunnitellut hankinnat'], 'valmis', 'aloittamatta');
        cy.get('#suunnittellut-hankinnat-taulukko')
            .testaaRivienArvot([2], [], ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(111), formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(111, 1))]);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 111);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 1, 111);
    });

    it('Muokkaa toisen vuoden arvoja ilman kopiointia', function () {
        cy.get('#suunnittellut-hankinnat-taulukko')
            .taulukonOsaPolussa([1, 1, 0, 0])
            .click();
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-taulukko', 1, 0, 1, '2');
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-taulukko', 1, 11, 1, '2', true);
        testaaSuunnitelmienTila(['Hankintakustannukset'], 'kesken', 'kesken');
        testaaSuunnitelmienTila(['Talvihoito'], 'kesken', 'kesken');
        testaaSuunnitelmienTila(['Talvihoito', 'Suunnitellut hankinnat'], 'valmis', 'kesken');
        cy.get('#suunnittellut-hankinnat-taulukko')
            .testaaRivienArvot([2], [], ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(115), formatoiArvoDesimaalinumeroksi(summaaJaIndeksikorjaaArvot([111, 4]))]);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 2, 4);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 2, 4);

    });

    it('Muokkaa toisen vuoden arvoja kopioinnin kanssa', function () {
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-taulukko', 1, 1, 1, '20');
        klikkaaTaytaAlas();
        testaaSuunnitelmienTila(['Hankintakustannukset'], 'kesken', 'kesken');
        testaaSuunnitelmienTila(['Talvihoito'], 'kesken', 'kesken');
        testaaSuunnitelmienTila(['Talvihoito', 'Suunnitellut hankinnat'], 'valmis', 'valmis');
        cy.get('#suunnittellut-hankinnat-taulukko')
            .testaaRivienArvot([2], [], ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(333), formatoiArvoDesimaalinumeroksi(summaaJaIndeksikorjaaArvot([111, 222]))]);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 2, 222);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 2, 222);
    });

    it('Muokkaa arvot tuleville hoitokausille', function () {
        cy.get('#suunnittellut-hankinnat-taulukko')
            .taulukonOsaPolussa([1, 2, 0, 0])
            .click();
        cy.get('label[for="kopioi-hankinnat-tuleville-hoitovuosille"]').click();
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-taulukko', 2, 0, 1, '5');
        klikkaaTaytaAlas();
        cy.get('#suunnittellut-hankinnat-taulukko')
            .testaaRivienArvot([2], [], ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(513), formatoiArvoDesimaalinumeroksi(summaaJaIndeksikorjaaArvot([111, 222, 60, 60, 60]))]);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 3, 60);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 3, 60);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 4, 60);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 4, 60);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 5, 60);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 5, 60);
        tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri', [111, 222, 60, 60, 60]);
        tarkastaIndeksilaskurinYhteensaArvo('tavoitehinnan-indeksilaskuri', [111, 222, 60, 60, 60]);
    });
});

describe('Testaa hankinnat laskulle taulukkoa', function () {
    it('Valitaan määrämitattavien työ taulukko', function () {
        cy.get('#suunnittellut-hankinnat-laskutukseen-perustuen-taulukko').should('not.be.visible');
        cy.get('label[for="laskutukseen-perustuen"]').click();
    });
    it('Taulukon arvot alussa oikein', function () {
        testaaSuunnitelmienTila(['Hankintakustannukset'], 'kesken', 'kesken');
        testaaSuunnitelmienTila(['Talvihoito'], 'kesken', 'kesken');
        testaaSuunnitelmienTila(['Talvihoito', 'Suunnitellut hankinnat'], 'kesken', 'kesken');
        cy.get('#suunnittellut-hankinnat-laskutukseen-perustuen-taulukko')
            .testaaOtsikot(['', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
            .testaaRivienArvot([1], [0, 0], ['1. hoitovuosi', '2. hoitovuosi', '3. hoitovuosi', '4. hoitovuosi', '5. hoitovuosi'])
            .testaaRivienArvot([1], [0, 1], ['', '', '', '', ''])
            .testaaRivienArvot([1], [0, 2], ['0,00', '0,00', '0,00', '0,00', '0,00'])
            .testaaRivienArvot([1], [0, 3], ['0,00', '0,00', '0,00', '0,00', '0,00'])

    });

    it('Muokkaa ensimmäisen vuoden arvoja ilman kopiointia', function () {
        cy.get('label[for="kopioi-hankinnat-tuleville-hoitovuosille"]').click();
        cy.get('#suunnittellut-hankinnat-laskutukseen-perustuen-taulukko')
            .taulukonOsaPolussa([1, 0, 0, 0])
            .click();
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-laskutukseen-perustuen-taulukko', 0, 0, 1, '10');
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-laskutukseen-perustuen-taulukko', 0, 11, 1, '10', true);
        cy.get('#suunnittellut-hankinnat-laskutukseen-perustuen-taulukko')
            .testaaRivienArvot([2], [], ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(20), formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(20, 1))]);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 131);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 1, 131);
    });

    it('Muokkaa ensimmäisen vuoden arvoja kopioinnin kanssa', function () {
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-laskutukseen-perustuen-taulukko', 0, 1, 1, '10');
        klikkaaTaytaAlas();
        testaaSuunnitelmienTila(['Hankintakustannukset'], 'kesken', 'kesken');
        testaaSuunnitelmienTila(['Talvihoito'], 'kesken', 'kesken');
        testaaSuunnitelmienTila(['Talvihoito', 'Suunnitellut hankinnat'], 'valmis', 'kesken');
        cy.get('#suunnittellut-hankinnat-laskutukseen-perustuen-taulukko')
            .testaaRivienArvot([2], [], ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(120), formatoiArvoDesimaalinumeroksi(indeksikorjaaArvo(120, 1))]);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 231);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 1, 231);
    });

    it('Muokkaa arvot tuleville hoitokausille', function () {
        cy.get('#suunnittellut-hankinnat-laskutukseen-perustuen-taulukko')
            .taulukonOsaPolussa([1, 1, 0, 0])
            .click();
        cy.get('label[for="kopioi-hankinnat-tuleville-hoitovuosille"]').click();
        muokkaaLaajennaRivinArvoa('suunnittellut-hankinnat-laskutukseen-perustuen-taulukko', 1, 0, 1, '50');
        klikkaaTaytaAlas();
        cy.get('#suunnittellut-hankinnat-laskutukseen-perustuen-taulukko')
            .testaaRivienArvot([2], [], ['Yhteensä', '', formatoiArvoDesimaalinumeroksi(2520), formatoiArvoDesimaalinumeroksi(summaaJaIndeksikorjaaArvot([120, 600, 600, 600, 600]))]);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 3, 660);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 3, 660);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 4, 660);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 4, 660);
        tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 5, 660);
        tarkastaIndeksilaskurinArvo('tavoitehinnan-indeksilaskuri', 5, 660);
        tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri', [231, 822, 660, 660, 660]);
        tarkastaIndeksilaskurinYhteensaArvo('tavoitehinnan-indeksilaskuri', [231, 822, 660, 660, 660]);
    });
});

describe('Lataa sivu uudestaan ja tarkasta, että kaikki tallennettu data löytyy.', function() {
   it('Lataa sivu', function() {
       cy.reload();
       cy.get('.ajax-loader', {timeout: 10000}).should('not.be.visible');
   });
   it('Testaa arvot', function() {
       tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri', [231, 822, 660, 660, 660]);
       tarkastaIndeksilaskurinYhteensaArvo('tavoitehinnan-indeksilaskuri', [231, 822, 660, 660, 660]);
   });
});