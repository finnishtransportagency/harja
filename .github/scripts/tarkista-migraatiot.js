#!/usr/bin/env node
'use strict';

/**
 * Tarkistaa Flyway-migraatioihin tehdyt muutokset verrattuna baseen (oletus: origin/develop).
 *
 * Säännöt koskevat vain tämän haaran muutoksia base-branchiin nähden, ei vanhaa migraatiohistoriaa:
 *   1. Lisätyn tiedoston nimen on oltava jokin seuraavista:
 *        V1_<numero>__[kuvaus].sql   (numero pakollinen, ei etunollia; kuvaus vapaaehtoinen: A-Z a-z 0-9 _ -)
 *        R__<kuvaus>.sql
 *        afterMigrate.sql
 *   2. Lisätty V1-versio ei saa olla jo basessa eikä esiintyä useammassa kuin yhdessä lisätyssä tiedostossa.
 *   3. Uusien V1-versioiden on jatkettava basen suurinta versiota aukottomasti.
 *   4. Basessa jo olevaa V1-tiedostoa ei saa muokata eikä poistaa.
 *      Uudelleennimeäminen tulkitaan poistoksi ja lisäykseksi.
 *      Ohitus: SALLI_V1_MUUTOKSET=true (esim. jos migraatiota ei ole vielä deployattu mihinkään ympäristöön).
 *      Ohituksella poistetun tiedoston versiota saa käyttää uudelleen.
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

// Lisättyjen tiedostojen sallitut nimiformaatit
const SALLITUT_NIMET = [
    /^V1_[1-9][0-9]*__[A-Za-z0-9_-]*\.sql$/,
    /^R__[A-Za-z0-9_-]+\.sql$/,
    /^afterMigrate\.sql$/,
];
const SALLITUT_MUODOT =
    'V1_<numero>__[kuvaus].sql, R__<kuvaus>.sql, afterMigrate.sql ' +
    '(kuvauksessa sallitut merkit: A-Z a-z 0-9 _ -)';
// Väljä versiotunnistus, koska basen historiassa on poikkeavia nimiä
const V1_VERSIO = /^V1_([0-9]+)__/;

const tiedostonimi = (polku) => path.posix.basename(polku);

const onV1Tiedosto = (polku) => tiedostonimi(polku).startsWith('V1_');

/** Palauttaa V1-version numeron, tai null. */
function v1Versio(polku) {
    const osuma = tiedostonimi(polku).match(V1_VERSIO);
    return osuma ? Number(osuma[1]) : null;
}

/**
 * Korvaa ohjausmerkit (esim. rivinvaihdot) näkyvillä escapeilla ja neutraloi GitHub Actionsin vanhan
 * ##[komento]-muodon, jotta tiedostonimi ei voi rikkoa tulostetta eikä injektoida workflow-komentoja.
 */
function naytettava(teksti) {
    return String(teksti)
        .replace(/[\x00-\x1f\x7f]/g, (merkki) => {
            const koodi = merkki.charCodeAt(0).toString(16).padStart(2, '0');
            return `\\x${koodi}`;
        })
        .replace(/##\[/g, '#\\x23[');
}

/**
 * Analysoi migraatiomuutokset. Puhdas funktio: ei lue gitiä eikä tulosta mitään.
 *
 * @param {object}   syote
 * @param {Array<{tila: string, polku: string}>} syote.muutokset
 *   git diff --name-status --no-renames -rivit
 * @param {string[]} syote.basenTiedostot  basen migraatiotiedostojen polut
 * @param {boolean}  [syote.salliV1Muutokset]
 *   salli basen V1-tiedostojen muutokset (varoitus virheen sijaan)
 * @returns {{virheet: string[], varoitukset: string[], basenSuurin: number}}
 */
function analysoi({ muutokset, basenTiedostot, salliV1Muutokset = false }) {
    const virheet = [];
    const varoitukset = [];

    const basenVersiot = new Set(basenTiedostot.map(v1Versio).filter((versio) => versio !== null));
    const basenSuurin = Math.max(0, ...basenVersiot);

    const lisatyt = new Map(); // versio -> lisätyt tiedostot
    const poistetutVersiot = new Set();

    for (const { tila, polku } of muutokset) {
        if (tila === 'A') {
            const versio = v1Versio(polku);
            const nimi = tiedostonimi(polku);
            if (!SALLITUT_NIMET.some((muoto) => muoto.test(nimi))) {
                virheet.push(
                    `Virheellinen migraatiotiedoston nimi: '${polku}'. Sallitut muodot: ${SALLITUT_MUODOT}.`,
                );
            } else if (versio !== null) {
                lisatyt.set(versio, [...(lisatyt.get(versio) ?? []), polku]);
            }
        } else if (onV1Tiedosto(polku)) {
            // D = poistettu, muut tilat (M, T) = muokattu
            const kuvaus = tila === 'D' ? 'poistettu' : 'muokattu';
            if (tila === 'D') {
                poistetutVersiot.add(v1Versio(polku));
            }
            if (salliV1Muutokset) {
                varoitukset.push(
                    `Basessa jo olevaa V1-migraatiota on ${kuvaus}: '${polku}' ` +
                    '(sallittu SALLI_V1_MUUTOKSET=true).',
                );
            } else {
                virheet.push(
                    `Basessa jo olevaa V1-migraatiota ei saa muuttaa (${kuvaus}): '${polku}'. ` +
                    'Jo deployattu migraatio kaataa Flywayn checksum-validoinnin.',
                );
            }
        }
    }

    for (const [versio, tiedostot] of lisatyt) {
        // Basen versiota saa käyttää uudelleen vain, jos basen tiedosto on samalla
        // poistettu (ohituksella).
        if (basenVersiot.has(versio) && !poistetutVersiot.has(versio)) {
            virheet.push(
                `Versio ${versio} on jo käytössä basessa: ` +
                `'${tiedostot.join("', '")}'.`,
            );
        } else if (tiedostot.length > 1) {
            virheet.push(
                `Versio ${versio} esiintyy useammassa migraatiotiedostossa: '${tiedostot.join("', '")}'.`,
            );
        }
    }

    const uudetVersiot = [...lisatyt.keys()]
        .filter((versio) => !basenVersiot.has(versio))
        .sort((a, b) => a - b);
    const aukko = uudetVersiot.findIndex((versio, i) => versio !== basenSuurin + 1 + i);
    if (aukko !== -1) {
        virheet.push(
            `Uusien V1-versioiden on jatkettava aukottomasti basen suurimmasta versiosta ${basenSuurin}. ` +
            `Odotettiin versiota ${basenSuurin + 1 + aukko}, ` +
            `löytyi ${uudetVersiot[aukko]}. ` +
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

/** Lukee basen ja HEADin väliset migraatiomuutokset muodossa [{tila, polku}]. */
function lueMuutokset(base, cwd) {
    // --no-renames: uudelleennimeäminen näkyy poistona ja lisäyksenä,
    // joten jokaisella muutoksella on yksi polku.
    const osat = nulErotellut(
        git(['diff', '--name-status', '-z', '--no-renames', `${base}...HEAD`, '--', MIGRAATIOHAKEMISTO], cwd),
    );
    const muutokset = [];
    for (let i = 0; i < osat.length; i += 2) {
        muutokset.push({ tila: osat[i], polku: osat[i + 1] });
    }
    return muutokset;
}

function lueTiedostot(ref, cwd) {
    return nulErotellut(
        git(['ls-tree', '-r', '-z', '--name-only', ref, '--', MIGRAATIOHAKEMISTO], cwd),
    );
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
// Tulostus. GitHub Actionsissa viestit annotaatioina. Tiedostonimet escapataan workflow-komentojen
// injektion estämiseksi.
// ---------------------------------------------------------------------------------------------

function tulostaViesti(taso, viesti) {
    const turvallinen = naytettava(viesti);
    if (process.env.GITHUB_ACTIONS === 'true') {
        // Rivinvaihdot on jo escapattu. %-merkki escapataan, jotta esimerkiksi
        // %0A ei tulkita rivinvaihdoksi.
        console.log(`::${taso}::${turvallinen.replace(/%/g, '%25')}`);
    } else {
        console.log(`${taso === 'error' ? '❌' : '⚠️ '} ${turvallinen}`);
    }
}

function main(argv = process.argv.slice(2)) {
    const base = argv[0] ?? 'origin/develop';
    const salliV1Muutokset = process.env.SALLI_V1_MUUTOKSET === 'true';

    // Viivalla alkava argumentti tulkittaisiin gitin optioksi
    if (base.startsWith('-')) {
        console.error(
            'Käyttö: node .github/scripts/tarkista-migraatiot.js [base-ref] (oletus: origin/develop)',
        );
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
        console.error(
            `Basea '${naytettava(base)}' ei löydy. Hae se ensin, esim. 'git fetch origin develop'.`,
        );
        return 2;
    }
    if (!onnistuu(['merge-base', base, 'HEAD'], juuri)) {
        console.error(
            `Basen '${naytettava(base)}' ja HEADin yhteistä esi-isää ei löydy (liian matala klooni?).`,
        );
        return 2;
    }

    const muutokset = lueMuutokset(base, juuri);
    if (muutokset.length === 0) {
        console.log(
            `✅ Ei muutoksia migraatioihin verrattuna baseen '${naytettava(base)}'.`,
        );
        return 0;
    }

    console.log(`Migraatiomuutokset verrattuna baseen '${naytettava(base)}':`);
    for (const { tila, polku } of muutokset) {
        console.log(`  ${tila}\t${naytettava(polku)}`);
    }
    console.log();

    const { virheet, varoitukset, basenSuurin } = analysoi({
        muutokset,
        basenTiedostot: lueTiedostot(base, juuri),
        salliV1Muutokset,
    });

    varoitukset.forEach((viesti) => tulostaViesti('warning', viesti));
    virheet.forEach((viesti) => tulostaViesti('error', viesti));

    console.log();
    if (virheet.length > 0) {
        console.log(`Migraatiotarkistus epäonnistui (${virheet.length} virhettä).`);
        console.log(
            `Basen suurin V1-versio on ${basenSuurin}, seuraava vapaa versio on ${basenSuurin + 1}.`,
        );
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
