import transit from "transit-js";

/**
 * Testaa kustannussuunnitelman pääyhteenvedon osioiden tietoja.
 *
 * @param {number} hoitovuosi
 * @param {string} osionNimi
 * @param {boolean|undefined} onkoVahvistettu
 */

export function testaaTilayhteenveto(hoitovuosi, osionNimi, onkoVahvistettu) {
    // Valitse aluksi haluttu hoitovuosi, jotta kohdistetaan testaus tietylle hoitovuodelle yhteenvedossa.
    cy.get('[data-cy="hoitokausi-jarjestysluvulla"]')
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
 * @param {number|array} sarakkeenIndexTaiPolku Pitää olla int
 * @param {string} arvo Arvo, joka kirjoitetaan input-kenttään
 * @param {boolean} [blurEvent=false] Kutsu blur-eventtiä inputille manuaalisesti, jos inputin blur-event ei triggeröidy. Esim. kenttä on viimeinen muokattava taulukossa.
 * @param {boolean} [debug=false] Aktivoi debug-moodi, joka näyttää taulukonOsaPolussa hakutoiminnon löytämät elementit järjestyksessä lokissa.
 */
export function muokkaaRivinArvoa(taulukonId, rivinIndex, sarakkeenIndexTaiPolku, arvo, blurEvent = true, debug = false) {
    const kirjoitettavaArvo = '{selectall}{backspace}' + arvo;
    const sarakkeenPolku = Array.isArray(sarakkeenIndexTaiPolku) ? sarakkeenIndexTaiPolku : [0, sarakkeenIndexTaiPolku];

    cy.get('#' + taulukonId)
        .taulukonOsaPolussa([1, rivinIndex, ...sarakkeenPolku], debug)
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

export function toggleLaajennaRivi(taulukonId, contains) {
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

export function summaaLuvut() {
    const luvut = [...arguments];

    return luvut.reduce((acc, val) => acc + (val || 0), 0);
}

export function formatoiArvoDesimaalinumeroksi(arvo, fallbackReturn = undefined, normaaliValilyonti = false) {
    if (!Number.isFinite(arvo)) {
        return fallbackReturn;
    }

    let formatoituArvo = '' + (Math.round((arvo + Number.EPSILON) * 100) / 100);
    formatoituArvo = parseFloat(formatoituArvo).toFixed(2);
    formatoituArvo = formatoituArvo.replace(/^(\d*)(\.?)/, (osuma, p1, p2, offset, kokoNumero) => {
        let numeroArray = p1.split('').reverse();
        let korvaavaArray = [];
        for (let i = 0; i < numeroArray.length; i++) {
            if ((i + 1) % 3 === 0) {
                // Google closure formatointi käyttää 160 koodia välilyönnin sijasta
                korvaavaArray.push(numeroArray[i],
                    normaaliValilyonti ? " " : String.fromCharCode(160));
            } else {
                korvaavaArray.push(numeroArray[i]);
            }
        }
        return korvaavaArray.reverse().join('').trim() + p2;
    });
    return formatoituArvo.replace('.', ',');
}

export function formatoiArvoEuromuotoiseksi(arvo, normaaliValilyonti) {
    const numero = formatoiArvoDesimaalinumeroksi(arvo, null, normaaliValilyonti);

    if (numero) {
        return numero + ' €';
    }
}

export function indeksikorjaaArvo(indeksit, arvo, hoitokaudenNumero) {
    const indeksi = indeksit[hoitokaudenNumero - 1];

    if (Number.isFinite(indeksi) && Number.isFinite(arvo)) {
        return indeksi * arvo;
    }
}

export function summaaJaIndeksikorjaaArvot(indeksit, arvot) {
    let yhteensaArvo = 0;
    for (let i = 0; i < arvot.length; i++) {
        const korjattuArvo = indeksikorjaaArvo(indeksit, arvot[i], i + 1);

        if (korjattuArvo) {
            yhteensaArvo += korjattuArvo;
        }
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
        .should('have.text', formatoituArvo ? formatoituArvo : '')
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

    let formatoituArvo = Number.isFinite(arvo) ? formatoiArvoEuromuotoiseksi(arvo) : undefined;
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

    let formatoituArvo = Number.isFinite(arvo) ?
        formatoiArvoEuromuotoiseksi(indeksikorjaaArvo(indeksit, arvo, hoitokaudenNumero)) : undefined;
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
    cy.get('[data-cy=kopioi-allaoleviin]:visible').scrollIntoView().click({force: true})
    /*then(($nappi) => {
        cy.wait(100);
        cy.wrap($nappi).click();
    })*/;
}

/**
 * Kattohinnan manuaalisen syötön taulukko käyttää eri komponenttia, sen takia myös testeissä eroja.
 */
export const kattohintaElem = (vuosi) => `div[data-cy=manuaalinen-kattohinta-grid] .pariton > :nth-child(${vuosi})`;
export const kattohintaInput = (vuosi) => kattohintaElem(vuosi) + " input";

export const taytaKattohinta = (vuosi, arvo) => {
    cy.get(kattohintaInput(vuosi))
        .type(`{selectall}{backspace}${arvo}`)
        .then(() => {
            cy.focused().blur();
        })
}
export const tarkistaKattohinta = (vuosi, arvo, onDisabloitu) => {
    if (onDisabloitu) {
        arvo = formatoiArvoEuromuotoiseksi(parseInt(arvo), true);
        cy.contains(kattohintaElem(vuosi), arvo)
    } else {
        cy.get(kattohintaInput(vuosi)).should('have.value', arvo);
    }
}

export const indeksikorjattuKHelem = (vuosi) => `div[data-cy=manuaalinen-kattohinta-grid] .parillinen > :nth-child(${vuosi})`;

export const tarkistaIndeksikorjattuKH = (vuosi, arvo, indeksit) => {
    if (arvo) {
        let formatoituArvo = formatoiArvoEuromuotoiseksi(indeksikorjaaArvo(indeksit, arvo, vuosi), true);
        cy.get(indeksikorjattuKHelem(vuosi)).contains(formatoituArvo);
    } else {
        cy.get(indeksikorjattuKHelem(vuosi)).should('have.text', '');
    }
}

export function alustaKanta(urakkaNimi) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista kiinteähintaiset työt
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM kiinteahintainen_tyo kht " +
            "USING toimenpideinstanssi tpi " +
            "WHERE kht.toimenpideinstanssi = tpi.id AND " +
            `      tpi.urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista kiinteähintaiset työt tulos:", tulos)
            });
        // Poista kustannusarvioidut työt
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM kustannusarvioitu_tyo kat " +
            "USING toimenpideinstanssi tpi " +
            "WHERE kat.toimenpideinstanssi = tpi.id AND " +
            `      tpi.urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista kustannusarvioidut työt tulos:", tulos)
            });

        // Poista johto- ja hallintokorvauksiin liittyvät asiat
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM johto_ja_hallintokorvaus jjh " +
            `WHERE jjh.\\\"urakka-id\\\" = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista johto- ja hallintokorvaukset tulos:", tulos)
            })

        // Poista toteutuneet kustannukset
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM toteutuneet_kustannukset tk " +
            `WHERE tk.urakka_id = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista toteutuneet kustannukset tulos:", tulos)
            })

        // Poista osioiden tilaan liittyvät asiat
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM suunnittelu_kustannussuunnitelman_tila skt " +
            `WHERE skt.\\\"urakka\\\" = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista osioiden tilaan liittyvät asiat tulos:", tulos)
            })

        // Poista manuaaliseen kattohintaan liittyvät asiat
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM urakka_tavoite ut " +
            `WHERE ut.\\\"urakka\\\" = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista manuaaliseen kattohintaan liittyvät asiat tulos:", tulos)
            })

        // Poista tehdyt päätökset
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM urakka_paatos up " +
            `WHERE up.\\\"urakka-id\\\" = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista tehtyihin päätöksiin liittyvät asiat tulos:", tulos)
            })

        // Nollaa vahvistukset
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM suunnittelu_kustannussuunnitelman_tila skt " +
            `WHERE skt.\\\"urakka\\\" = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista tilan vahvistukseen liittyvät asiat tulos:", tulos)
            })

        // poista kulut
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM kulu_kohdistus kt " +
            `WHERE kt.toimenpideinstanssi IN (SELECT id FROM toimenpideinstanssi t WHERE t.urakka = ` +
            `(SELECT id FROM urakka WHERE nimi = '${urakkaNimi}'));\"`)
            .then((tulos) => {
                console.log("Poista kulujen kohdistukset tulos:", tulos)})
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM kulu k " +
            `WHERE k.urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista kulut tulos:", tulos)})

    });
}

/**
 * @param urakkaNimi Avattavan urakan nimi
 * @param alue Alue, jotta löydetään urakka käyttöliittymältä
 * @param indeksiArray Array, johon indeksit pusketaan.
 */
export function avaaKustannussuunnittelu(urakkaNimi, alue, indeksiArray) {
    cy.intercept('POST', '_/budjettisuunnittelun-indeksit').as('budjettisuunnittelun-indeksit');

    cy.visit("/");

    cy.contains('.haku-lista-item', alue, {timeout: 30000}).click();
    cy.get('.ajax-loader', {timeout: 10000}).should('not.exist');

    cy.contains('[data-cy=urakat-valitse-urakka] li', urakkaNimi, {timeout: 10000}).click();
    cy.get('[data-cy=tabs-taso1-Suunnittelu]', {timeout: 20000}).click();

    // Tässä otetaan indeksikertoimet talteen
    cy.wait('@budjettisuunnittelun-indeksit')
        .then(($xhr) => {
            const reader = transit.reader("json");
            const vastaus = reader.read(JSON.stringify($xhr.response.body));

            vastaus.forEach((transitIndeksiMap) => {
                indeksiArray.push(transitIndeksiMap?.get(transit.keyword('indeksikerroin')));
            });
        });

    cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');
}

export function valitseHoitokausi(nykyinenHoitokausi, uusiHoitokausi) {
    cy.get('[data-cy="hoitokausi-jarjestysluvulla"]').contains(nykyinenHoitokausi).click();
    cy.get('[data-cy="hoitokausi-jarjestysluvulla"]').contains('span', uusiHoitokausi).click();
}
