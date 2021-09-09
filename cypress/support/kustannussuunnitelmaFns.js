/**
 * Testaa kustannussuunnitelman pääyhteenvedon osioiden tietoja.
 *
 * @param {number} hoitovuosi
 * @param {string} osionNimi
 * @param {boolean|undefined} onkoVahvistettu
*/

export function testaaTilayhteenveto(hoitovuosi, osionNimi, onkoVahvistettu) {
    // Valitse aluksi haluttu hoitovuosi, jotta kohdistetaan testaus tietylle hoitovuodelle yhteenvedossa.
    cy.get('[data-cy="hoitovuosi-rivivalitsin"]')
        .contains('button', hoitovuosi)
        .click();

    // Testaa osion tila
    if (onkoVahvistettu !== undefined) {
        cy.get('#tilayhteenveto')
            .find(`[data-cy="osion-yhteenveto-rivi-${osionNimi}"]`)
            .contains(onkoVahvistettu ? "Vahvistettu" : "Odottaa vahvistusta")
    }

    // TODO: Testaa yhteenvetorivin arvoja, mikäli annettu argumentteina tälle funktiolle.
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
 * @param {boolean} [debug=false] Aktivoi debug-moodi, joka näyttää taulukonOsaPolussa hakutoiminnon löytämät elementit järjestyksessä lokissa.
 */
export function muokkaaRivinArvoa(taulukonId, rivinIndex, sarakkeenIndex, arvo, blurEvent = true, debug = false) {
    const kirjoitettavaArvo = '{selectall}{backspace}' + arvo;

    cy.get('#' + taulukonId)
        .taulukonOsaPolussa([1, rivinIndex, 0, sarakkeenIndex], debug)
        .click()
        .type(kirjoitettavaArvo)

        // Jos input-kenttä on viimeinen, johon kirjoitetaan, on syytä kutsua blur-eventtiä manuaalisesti, jotta blur-event
        // triggeröityy.
        .then(($input) => {
            if (blurEvent) {
                cy.focused().blur();
            }
        });

    // Odota hetki, jotta tallennusfunktio triggeröityy varmasti.
    // Välillä tallennuksen triggeröityminen (blurrin tapahtuessa) vaikuttaisi olevan flaky.
    // FIXME: Katsotaan auttaako wait, vai pitääkö tutkia cypress blur-mekaniikkaa tarkemmin.
    cy.wait(1000)
}

export function toggleLaajennaRivi (taulukonId, contains) {
    cy.get('#' + taulukonId)
        .contains('[data-cy*=laajenna]', contains)
        .click();
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
export function muokkaaLaajennaRivinArvoa(taulukonId, laajennaRivinIndex, rivinIndex, sarakkeenIndex, arvo, blurEvent = false) {
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

export function formatoiArvoDesimaalinumeroksi(arvo) {
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

export function formatoiArvoEuromuotoiseksi(arvo) {
    return formatoiArvoDesimaalinumeroksi(arvo) + ' €';
}

export function indeksikorjaaArvo(indeksit, arvo, hoitokaudenNumero) {
    return indeksit[hoitokaudenNumero - 1] * arvo;
}

export function summaaJaIndeksikorjaaArvot(indeksit, arvot) {
    let yhteensaArvo = 0;
    for (let i = 0; i < arvot.length; i++) {
        yhteensaArvo += indeksikorjaaArvo(indeksit, arvot[i], i + 1);
    }
    return yhteensaArvo;
}

export function summaaArvot(arvot) {
    let yhteensaArvo = 0;
    arvot.forEach((arvo) => {
        yhteensaArvo += arvo;
    });
    return yhteensaArvo;
}

export function hintalaskurinTarkastus(dataCy, hoitokaudenNumero, formatoituArvo) {
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
export function tarkastaHintalaskurinArvo(dataCy, hoitokaudenNumero, arvo) {
    cy.log(`Tarkastetaan hintalaskurin: ${dataCy} arvo ${hoitokaudenNumero}. hoitovuodelle...`);

    let formatoituArvo = formatoiArvoEuromuotoiseksi(arvo);
    hintalaskurinTarkastus(dataCy, hoitokaudenNumero, formatoituArvo);
}

/**
 * Tällä voi testata, että indeksilaskurin arvo on oikein.
 *
 * Testaus tehdään Cypressin async eventissä.
 *
 * @param {array} indeksit
 * @param {string} dataCy
 * @param {number} hoitokaudenNumero Pitää olla int
 * @param {number} arvo
 */
export function tarkastaIndeksilaskurinArvo(indeksit, dataCy, hoitokaudenNumero, arvo) {
    cy.log(`Tarkastetaan indeksilaskurin: ${dataCy} arvo ${hoitokaudenNumero}. hoitovuodelle......`);

    let formatoituArvo = formatoiArvoEuromuotoiseksi(indeksikorjaaArvo(indeksit, arvo, hoitokaudenNumero));
    hintalaskurinTarkastus(dataCy, hoitokaudenNumero, formatoituArvo);
}


/**
 *
 * @param {string} dataCy
 * @param {array} arvot
 */
export function tarkastaHintalaskurinYhteensaArvo(dataCy, arvot) {
    cy.log(`Tarkastetaan hintalaskurin: ${dataCy} "Yhteensä"-arvo...`);

    let yhteensaArvo = summaaArvot(arvot);
    hintalaskurinTarkastus(dataCy, 'yhteensa', formatoiArvoEuromuotoiseksi(yhteensaArvo));
}

/**
 * @param {array} indeksit
 * @param {string} dataCy
 * @param {array} arvot
 */
export function tarkastaIndeksilaskurinYhteensaArvo(indeksit, dataCy, arvot) {
    cy.log(`Tarkastetaan indeksilaskurin: ${dataCy} "Yhteensä"-arvo...`);

    let yhteensaArvo = summaaJaIndeksikorjaaArvot(indeksit, arvot);
    hintalaskurinTarkastus(dataCy, 'yhteensa', formatoiArvoEuromuotoiseksi(yhteensaArvo));
}

/**
 * Näkyvissä pitäisi olla aina maksimissaan vain yksi "Kopioi allaoleviin" nappi. Tämän funktion avulla sitä klikataan.
 */
export function klikkaaTaytaAlas() {
    cy.get('[data-cy=kopioi-allaoleviin]:visible').scrollIntoView().click({ force: true })
    /*then(($nappi) => {
        cy.wait(100);
        cy.wrap($nappi).click();
    })*/;
}
