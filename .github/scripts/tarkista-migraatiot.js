#!/usr/bin/env node
'use strict';

/**
 * Tarkistaa Flyway-migraatioihin tehdyt muutokset verrattuna baseen (oletus: origin/develop).
 *
 * Säännöt koskevat vain tämän haaran muutoksia base-branchiin nähden, ei vanhaa migraatiohistoriaa:
 *   1. Uuden tai uudelleennimetyn tiedoston nimen on oltava jokin seuraavista:
 *        V1_<numero>__[kuvaus].sql   (numero pakollinen, ei etunollia; kuvaus vapaaehtoinen: A-Z a-z 0-9 _ -)
 *        R__<kuvaus>.sql
 *        afterMigrate.sql
 *   2. Lisätty V1-versio ei saa olla jo basessa eikä esiintyä useammassa kuin yhdessä tiedostossa.
 *   3. Uusien V1-versioiden on jatkettava basen suurinta versiota aukottomasti.
 *   4. Basessa jo olevaa V1-tiedostoa ei saa muokata, poistaa eikä nimetä uudelleen.
 *      Ohitus: SALLI_V1_MUUTOKSET=true (esim. jos migraatiota ei ole vielä deployattu mihinkään ympäristöön).
 *   R__-tiedostot jäävät sääntöjen 2-4 ulkopuolelle.
 *
 * Vertailu tehdään basen ja HEADin yhteisestä esi-isästä (git diff base...HEAD), joten vain
 * commitoidut muutokset tarkistetaan.
 *
 * Käyttö (missä tahansa repon hakemistossa):
 *   node .github/scripts/tarkista-migraatiot.js [base-ref]
 *
 * Paluuarvot: 0 = OK tai ei muutoksia, 1 = sääntörikkomus, 2 = käyttövirhe
 *
 * Ei ulkoisia riippuvuuksia. Testit: node --test .github/scripts/tarkista-migraatiot.test.js
 */

const { execFileSync } = require('node:child_process');
const path = require('node:path');

const MIGRAATIOHAKEMISTO = 'tietokanta/src/main/resources/db/migration';

// Uusien tiedostojen sallitut nimiformaatit
const V1_NIMI = /^V1_([1-9][0-9]*)__[A-Za-z0-9_-]*\.sql$/;
const R_NIMI = /^R__[A-Za-z0-9_-]+\.sql$/;
const CALLBACK_NIMI = /^afterMigrate\.sql$/;
// Väljä V1-tunnistus basen olemassa oleville tiedostoille (historiassa on poikkeavia nimiä)
const V1_VALJA = /^V1_([0-9]+)__/;

const SALLITUT_MUODOT =
    'V1_<numero>__[kuvaus].sql, R__<kuvaus>.sql, afterMigrate.sql (kuvauksessa sallitut merkit: A-Z a-z 0-9 _ -)';

const tiedostonimi = (polku) => path.posix.basename(polku);

const onSallittuNimi = (polku) =>
    [V1_NIMI, R_NIMI, CALLBACK_NIMI].some((muoto) => muoto.test(tiedostonimi(polku)));

const onV1Tiedosto = (polku) => tiedostonimi(polku).startsWith('V1_');

/** Palauttaa V1-version numeron väljällä tunnistuksella, tai null. */
function v1Versio(polku) {
    const osuma = tiedostonimi(polku).match(V1_VALJA);
    return osuma ? Number(osuma[1]) : null;
}

/**
 * Korvaa ohjausmerkit (esim. rivinvaihdot) näkyvillä escapeilla ja neutraloi GitHub Actionsin vanhan
 * ##[komento]-muodon, jotta tiedostonimi ei voi rikkoa tulostetta eikä injektoida workflow-komentoja.
 */
function naytettava(teksti) {
    return String(teksti)
        .replace(/[\x00-\x1f\x7f]/g, (merkki) => `\\x${merkki.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .replace(/##\[/g, '#\\x23[');
}

/**
 * Analysoi migraatiomuutokset. Puhdas funktio: ei lue gitiä eikä tulosta mitään.
 *
 * @param {object}   syote
 * @param {Array<{tila: string, polku: string, uusiPolku?: string}>} syote.muutokset  git diff --name-status -rivit
 * @param {string[]} syote.basenTiedostot  basen migraatiotiedostojen polut
 * @param {string[]} syote.headinTiedostot  HEADin migraatiotiedostojen polut
 * @param {boolean}  [syote.salliV1Muutokset]  salli basen V1-tiedostojen muutokset (varoitus virheen sijaan)
 * @returns {{virheet: string[], varoitukset: string[], basenSuurin: number}}
 */
function analysoi({ muutokset, basenTiedostot, headinTiedostot, salliV1Muutokset = false }) {
    const virheet = [];
    const varoitukset = [];

    const basenVersiot = new Set(basenTiedostot.map(v1Versio).filter((versio) => versio !== null));
    const basenSuurin = Math.max(0, ...basenVersiot);

    // HEADin versiot -> tiedostot, duplikaattien tunnistamiseen
    const headinVersiot = new Map();
    for (const polku of headinTiedostot) {
        const versio = v1Versio(polku);
        if (versio !== null) {
            headinVersiot.set(versio, [...(headinVersiot.get(versio) ?? []), polku]);
        }
    }

    const lisatytVersiot = new Map(); // versio -> tiedosto
    const poistetutVersiot = new Set(); // basen versiot, joiden tiedosto on poistettu tai nimetty uudelleen

    const kirjaaLisatty = (polku) => {
        if (!onSallittuNimi(polku)) {
            virheet.push(`Virheellinen migraatiotiedoston nimi: '${polku}'. Sallitut muodot: ${SALLITUT_MUODOT}.`);
            return;
        }
        const osuma = tiedostonimi(polku).match(V1_NIMI);
        if (osuma) {
            lisatytVersiot.set(Number(osuma[1]), polku);
        }
    };

    const kirjaaBasenV1Muutos = (polku, kuvaus) => {
        if (salliV1Muutokset) {
            varoitukset.push(`Basessa jo olevaa V1-migraatiota on ${kuvaus}: '${polku}' (sallittu SALLI_V1_MUUTOKSET=true).`);
        } else {
            virheet.push(
                `Basessa jo olevaa V1-migraatiota ei saa muuttaa (${kuvaus}): '${polku}'. ` +
                'Jo deployattu migraatio kaataa Flywayn checksum-validoinnin.',
            );
        }
    };

    // Poistettu tai uudelleennimetty basen V1-tiedosto vapauttaa versionsa uudelleenkäyttöön (vain ohituksella)
    const kirjaaBasenV1Poisto = (polku, kuvaus) => {
        kirjaaBasenV1Muutos(polku, kuvaus);
        const versio = v1Versio(polku);
        if (versio !== null) {
            poistetutVersiot.add(versio);
        }
    };

    for (const { tila, polku, uusiPolku } of muutokset) {
        switch (tila[0]) {
            case 'A': // lisätty
                kirjaaLisatty(polku);
                break;
            case 'C': // kopioitu (vain jos käyttäjän git-asetukset tunnistavat kopiot)
                kirjaaLisatty(uusiPolku);
                break;
            case 'R': // nimetty uudelleen
                if (onV1Tiedosto(polku)) {
                    kirjaaBasenV1Poisto(polku, `nimetty uudelleen -> '${uusiPolku}'`);
                }
                kirjaaLisatty(uusiPolku);
                break;
            case 'D': // poistettu
                if (onV1Tiedosto(polku)) {
                    kirjaaBasenV1Poisto(polku, 'poistettu');
                }
                break;
            default: // M (muokattu), T (tyyppi muuttunut) jne.
                if (onV1Tiedosto(polku)) {
                    kirjaaBasenV1Muutos(polku, 'muokattu');
                }
        }
    }

    for (const [versio, tiedosto] of lisatytVersiot) {
        const samanVersionTiedostot = headinVersiot.get(versio) ?? [];
        // Basen version uudelleenkäyttö on sallittu vain, jos basen tiedosto on samalla poistettu
        // tai nimetty uudelleen (esim. ohituksella tehty kuvauksen korjaus)
        if (basenVersiot.has(versio) && !poistetutVersiot.has(versio)) {
            virheet.push(`Versio ${versio} on jo käytössä basessa: '${tiedosto}'.`);
        } else if (samanVersionTiedostot.length > 1) {
            virheet.push(`Versio ${versio} esiintyy useammassa migraatiotiedostossa: '${samanVersionTiedostot.join("', '")}'.`);
        }
    }

    const uudetVersiot = [...lisatytVersiot.keys()].filter((versio) => !basenVersiot.has(versio)).sort((a, b) => a - b);
    const aukko = uudetVersiot.findIndex((versio, i) => versio !== basenSuurin + 1 + i);
    if (aukko !== -1) {
        virheet.push(
            `Uusien V1-versioiden on jatkettava aukottomasti basen suurimmasta versiosta ${basenSuurin}. ` +
            `Odotettiin versiota ${basenSuurin + 1 + aukko}, löytyi ${uudetVersiot[aukko]}. ` +
            `Uudet versiot: ${uudetVersiot.join(', ')}.`,
        );
    }

    return { virheet, varoitukset, basenSuurin };
}

// ---------------------------------------------------------------------------------------------
// Git-tietojen luku. Komennot ajetaan ilman shelliä (execFileSync), joten argumentteja ei tulkita.
// -z-tulosteessa polut erotellaan NUL-merkillä ilman lainausmerkkejä tai escapeja.
// ---------------------------------------------------------------------------------------------

function git(args, cwd) {
    return execFileSync('git', args, {
        cwd,
        encoding: 'utf8',
        maxBuffer: 64 * 1024 * 1024,
        stdio: ['ignore', 'pipe', 'pipe'],
    });
}

const nulErotellut = (tuloste) => tuloste.split('\0').filter((osa) => osa !== '');

/** Lukee basen ja HEADin väliset migraatiomuutokset muodossa [{tila, polku, uusiPolku}]. */
function lueMuutokset(base, cwd) {
    const osat = nulErotellut(
        git(['diff', '--name-status', '-z', '-M', `${base}...HEAD`, '--', MIGRAATIOHAKEMISTO], cwd),
    );
    const muutokset = [];
    for (let i = 0; i < osat.length;) {
        const tila = osat[i++];
        const polku = osat[i++];
        // Uudelleennimeämisellä ja kopiolla on kaksi polkua: vanha ja uusi
        const uusiPolku = /^[RC]/.test(tila) ? osat[i++] : undefined;
        muutokset.push({ tila, polku, uusiPolku });
    }
    return muutokset;
}

function lueTiedostot(ref, cwd) {
    return nulErotellut(git(['ls-tree', '-r', '-z', '--name-only', ref, '--', MIGRAATIOHAKEMISTO], cwd));
}

function onnistuu(args, cwd) {
    try {
        git(args, cwd);
        return true;
    } catch {
        return false;
    }
}

// ---------------------------------------------------------------------------------------------
// Tulostus. GitHub Actionsissa viestit annotaatioina; data escapataan workflow-komentojen
// injektion estämiseksi (tiedostonimet tulevat PR:stä eli ovat epäluotettavaa syötettä).
// ---------------------------------------------------------------------------------------------

const githubActions = () => process.env.GITHUB_ACTIONS === 'true';

const escapeWorkflowData = (teksti) =>
    teksti.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');

function tulostaViesti(taso, viesti) {
    const turvallinen = naytettava(viesti);
    if (githubActions()) {
        console.log(`::${taso}::${escapeWorkflowData(turvallinen)}`);
    } else {
        console.log(`${taso === 'error' ? '❌' : '⚠️ '} ${turvallinen}`);
    }
}

function main(argv = process.argv.slice(2)) {
    const base = argv[0] ?? 'origin/develop';
    const salliV1Muutokset = process.env.SALLI_V1_MUUTOKSET === 'true';

    // Viivalla alkava argumentti tulkittaisiin gitin optioksi
    if (base.startsWith('-')) {
        console.error('Käyttö: node .github/scripts/tarkista-migraatiot.js [base-ref]   (oletus: origin/develop)');
        return 2;
    }

    let juuri;
    try {
        juuri = git(['rev-parse', '--show-toplevel']).trim();
    } catch {
        console.error('Skripti on ajettava git-repon sisällä.');
        return 2;
    }

    if (!onnistuu(['rev-parse', '--verify', '--quiet', `${base}^{commit}`], juuri)) {
        console.error(`Basea '${naytettava(base)}' ei löydy. Hae se ensin, esim. 'git fetch origin develop'.`);
        return 2;
    }
    if (!onnistuu(['merge-base', base, 'HEAD'], juuri)) {
        console.error(`Basen '${naytettava(base)}' ja HEADin yhteistä esi-isää ei löydy (liian matala klooni?).`);
        return 2;
    }

    const muutokset = lueMuutokset(base, juuri);
    if (muutokset.length === 0) {
        console.log(`✅ Ei muutoksia migraatioihin verrattuna baseen '${naytettava(base)}'.`);
        return 0;
    }

    console.log(`Migraatiomuutokset verrattuna baseen '${naytettava(base)}':`);
    for (const { tila, polku, uusiPolku } of muutokset) {
        console.log(`  ${tila}\t${naytettava(polku)}${uusiPolku ? ` -> ${naytettava(uusiPolku)}` : ''}`);
    }
    console.log();

    const { virheet, varoitukset, basenSuurin } = analysoi({
        muutokset,
        basenTiedostot: lueTiedostot(base, juuri),
        headinTiedostot: lueTiedostot('HEAD', juuri),
        salliV1Muutokset,
    });

    varoitukset.forEach((viesti) => tulostaViesti('warning', viesti));
    virheet.forEach((viesti) => tulostaViesti('error', viesti));

    console.log();
    if (virheet.length > 0) {
        console.log(`Migraatiotarkistus epäonnistui (${virheet.length} virhettä).`);
        console.log(`Basen suurin V1-versio on ${basenSuurin}, seuraava vapaa versio on ${basenSuurin + 1}.`);
        console.log('Ohjeet: tietokanta/README.md');
        return 1;
    }

    console.log(`✅ Migraatiotarkistus OK. Basen suurin V1-versio: ${basenSuurin}.`);
    return 0;
}

module.exports = { analysoi, MIGRAATIOHAKEMISTO };

if (require.main === module) {
    try {
        process.exitCode = main();
    } catch (virhe) {
        // Odottamaton virhe (esim. git-komento kaatui) on käyttövirhe, ei sääntörikkomus
        console.error(`Tarkistus kaatui: ${naytettava(virhe.message)}`);
        process.exitCode = 2;
    }
}
