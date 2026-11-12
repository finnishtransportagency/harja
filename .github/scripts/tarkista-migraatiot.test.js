'use strict';

// Testit: node --test .github/scripts/tarkista-migraatiot.test.js

const { test, describe } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync, spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const { analysoi, MIGRAATIOHAKEMISTO: H } = require('./tarkista-migraatiot.js');

const SKRIPTI = path.join(__dirname, 'tarkista-migraatiot.js');

const BASE_TIEDOSTOT = ['V1__Perustiedot.sql', 'V1_1__.sql', 'V1_2__kuvaus.sql', 'V1_3__.sql', 'R__Toistettava.sql', 'afterMigrate.sql'];

const lisatty = (nimi) => ({ tila: 'A', polku: `${H}/${nimi}` });
const muokattu = (nimi) => ({ tila: 'M', polku: `${H}/${nimi}` });
const poistettu = (nimi) => ({ tila: 'D', polku: `${H}/${nimi}` });
// git diff --no-renames näyttää uudelleennimeämisen poistona ja lisäyksenä
const nimetty = (vanha, uusi) => [poistettu(vanha), lisatty(uusi)];

const aja = (muutokset, { base = BASE_TIEDOSTOT, salliV1Muutokset = false } = {}) =>
    analysoi({ muutokset, basenTiedostot: base.map((nimi) => `${H}/${nimi}`), salliV1Muutokset });

const oletaOk = (tulos) => assert.deepEqual(tulos.virheet, []);
const oletaVirhe = (tulos, osa) => assert.ok(tulos.virheet.some((v) => v.includes(osa)), JSON.stringify(tulos.virheet));

describe('analysoi', () => {
    test('ei muutoksia', () => oletaOk(aja([])));

    test('seuraava versio ilman kuvausta', () => {
        const tulos = aja([lisatty('V1_4__.sql')]);
        oletaOk(tulos);
        assert.equal(tulos.basenSuurin, 3);
    });

    test('kaksi peräkkäistä versiota kuvauksella', () =>
        oletaOk(aja([lisatty('V1_4__uusi-kuvaus_1.sql'), lisatty('V1_5__.sql')])));

    test('aukko versioissa', () => oletaVirhe(aja([lisatty('V1_5__.sql')]), 'Odotettiin versiota 4, löytyi 5'));

    test('aukko PR:n omien versioiden välissä', () =>
        oletaVirhe(aja([lisatty('V1_4__.sql'), lisatty('V1_6__.sql')]), 'Odotettiin versiota 5, löytyi 6'));

    test('basessa jo oleva versio eri kuvauksella', () =>
        oletaVirhe(aja([lisatty('V1_3__eri_kuvaus.sql')]), 'Versio 3 on jo käytössä'));

    test('vanhempi versio on jo käytössä', () => oletaVirhe(aja([lisatty('V1_2__.sql')]), 'Versio 2 on jo käytössä'));

    test('sama uusi versio kahdesti', () =>
        oletaVirhe(aja([lisatty('V1_4__a.sql'), lisatty('V1_4__b.sql')]), 'Versio 4 esiintyy useammassa'));

    test('basen V1-migraation muokkaus', () => oletaVirhe(aja([muokattu('V1_2__kuvaus.sql')]), 'muokattu'));

    test('basen V1-migraation muokkaus ohituksella antaa varoituksen', () => {
        const tulos = aja([muokattu('V1_2__kuvaus.sql')], { salliV1Muutokset: true });
        oletaOk(tulos);
        assert.equal(tulos.varoitukset.length, 1);
    });

    test('basen V1-migraation poisto', () => oletaVirhe(aja([poistettu('V1_2__kuvaus.sql')]), 'poistettu'));

    test('basen V1-migraation uudelleennimeäminen', () =>
        oletaVirhe(aja(nimetty('V1_2__kuvaus.sql', 'V1_2__korjattu.sql')), 'poistettu'));

    test('basen V1-migraation uudelleennimeäminen ohituksella', () =>
        oletaOk(aja(nimetty('V1_2__kuvaus.sql', 'V1_2__korjattu.sql'), { salliV1Muutokset: true })));

    test('basen V1-migraatio siirretty uudeksi versioksi ohituksella', () =>
        oletaOk(aja(nimetty('V1_2__kuvaus.sql', 'V1_4__siirretty.sql'), { salliV1Muutokset: true })));

    test('basen V1-migraatio siirretty basessa jo olevaan versioon ohituksella', () =>
        oletaVirhe(aja(nimetty('V1_2__kuvaus.sql', 'V1_3__siirretty.sql'), { salliV1Muutokset: true }), 'Versio 3'));

    test('R__-migraation muokkaus, lisäys ja poisto sekä afterMigrate-muokkaus', () =>
        oletaOk(aja([muokattu('R__Toistettava.sql'), lisatty('R__Uusi.sql'), muokattu('afterMigrate.sql')])));

    test('R__-migraation poisto', () => oletaOk(aja([poistettu('R__Toistettava.sql')])));

    test('R__-migraatio nimetty seuraavaksi V1-versioksi', () =>
        oletaOk(aja(nimetty('R__Toistettava.sql', 'V1_4__vakioitu.sql'))));

    test('R__-migraatio nimetty virheelliseksi', () =>
        oletaVirhe(aja(nimetty('R__Toistettava.sql', 'V1_04__.sql')), 'Virheellinen migraatiotiedoston nimi'));

    test('uusi versio alihakemistossa', () => oletaOk(aja([lisatty('alihakemisto/V1_4__.sql')])));

    test('basessa jo oleva versio alihakemistossa', () =>
        oletaVirhe(aja([lisatty('alihakemisto/V1_3__.sql')]), 'Versio 3 on jo käytössä'));

    for (const nimi of ['V1_4_kuvaus.sql', 'V1_04__.sql', 'V1_4__ .sql', 'v1_4__.sql', 'V1___kuvaus.sql',
        'V4__.sql', 'V1_4__.SQL', 'V1_4__ä.sql', 'R__.sql', 'muu.sql']) {
        test(`virheellinen nimi '${nimi}'`, () => oletaVirhe(aja([lisatty(nimi)]), 'Virheellinen migraatiotiedoston nimi'));
    }

    test('basen poikkeavat historialliset nimet tunnistetaan', () => {
        const tulos = aja([lisatty('V1_1026__.sql')], { base: [...BASE_TIEDOSTOT, 'V1_1025__ .sql'] });
        oletaOk(tulos);
        assert.equal(tulos.basenSuurin, 1025);
    });

    describe('develop on edennyt haaran luonnin jälkeen', () => {
        test('sama versio eri kuvauksella antaa yhden virheen', () => {
            const tulos = aja([lisatty('V1_4__haarassa.sql')], { base: [...BASE_TIEDOSTOT, 'V1_4__developissa.sql'] });
            oletaVirhe(tulos, 'Versio 4 on jo käytössä');
            assert.equal(tulos.virheet.length, 1, JSON.stringify(tulos.virheet));
        });

        test('haaran versio jatkaa basea', () =>
            oletaOk(aja([lisatty('V1_5__haarassa.sql')], { base: [...BASE_TIEDOSTOT, 'V1_4__developissa.sql'] })));

        test('haaran versio on jo käytössä developissa', () =>
            oletaVirhe(
                aja([lisatty('V1_5__haarassa.sql')], { base: [...BASE_TIEDOSTOT, 'V1_4__developissa.sql', 'V1_5__developissa.sql'] }),
                'Versio 5 on jo käytössä',
            ));
    });
});

// ---------------------------------------------------------------------------------------------
// Savutestit oikeassa git-repossa: git-tietojen luku, paluuarvot ja tulostus.
// ---------------------------------------------------------------------------------------------

function gitT(cwd, ...args) {
    return execFileSync('git', ['-c', 'user.name=testi', '-c', 'user.email=testi@example.com', '-c', 'commit.gpgsign=false', ...args], {
        cwd,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
    });
}

/** Luo tilapäisen repon, jonka develop-haarassa ovat BASE-tiedostot, ja siirtyy pr-haaraan. */
function alustaRepo(t) {
    const repo = fs.mkdtempSync(path.join(os.tmpdir(), 'migraatiot-'));
    t.after(() => fs.rmSync(repo, { recursive: true, force: true }));
    gitT(repo, 'init', '-q');
    gitT(repo, 'checkout', '-q', '-b', 'develop');
    BASE_TIEDOSTOT.forEach((nimi) => lisaa(repo, nimi));
    fs.writeFileSync(path.join(repo, 'muu.txt'), 'muu');
    commitoi(repo);
    gitT(repo, 'checkout', '-q', '-b', 'pr');
    return repo;
}

function lisaa(repo, nimi) {
    const tiedosto = path.join(repo, H, nimi);
    fs.mkdirSync(path.dirname(tiedosto), { recursive: true });
    fs.writeFileSync(tiedosto, `-- ${nimi}\n`);
}

function commitoi(repo) {
    gitT(repo, 'add', '-A');
    gitT(repo, 'commit', '-q', '--allow-empty', '-m', 'muutos');
}

function ajaSkripti(repo, { base = 'develop', env = {} } = {}) {
    // Ajoympäristön (esim. CI:n) muuttujat eivät saa vaikuttaa testeihin
    const { GITHUB_ACTIONS, SALLI_V1_MUUTOKSET, ...perus } = process.env;
    const ymparisto = { ...perus, ...env };
    const tulos = spawnSync(process.execPath, [SKRIPTI, base], { cwd: repo, env: ymparisto, encoding: 'utf8' });
    return { koodi: tulos.status, tuloste: tulos.stdout + tulos.stderr };
}

describe('skripti git-repossa', () => {
    test('ei muutoksia migraatioihin', (t) => {
        const repo = alustaRepo(t);
        fs.writeFileSync(path.join(repo, 'muu.txt'), 'muutos');
        commitoi(repo);
        const { koodi, tuloste } = ajaSkripti(repo);
        assert.equal(koodi, 0, tuloste);
        assert.match(tuloste, /Ei muutoksia migraatioihin/);
    });

    test('oikein numeroitu lisäys', (t) => {
        const repo = alustaRepo(t);
        lisaa(repo, 'V1_4__.sql');
        commitoi(repo);
        const { koodi, tuloste } = ajaSkripti(repo);
        assert.equal(koodi, 0, tuloste);
        assert.match(tuloste, /Migraatiotarkistus OK/);
    });

    test('aukko versioissa', (t) => {
        const repo = alustaRepo(t);
        lisaa(repo, 'V1_5__.sql');
        commitoi(repo);
        const { koodi, tuloste } = ajaSkripti(repo);
        assert.equal(koodi, 1, tuloste);
        assert.match(tuloste, /seuraava vapaa versio on 4/);
    });

    test('uudelleennimeäminen luetaan poistona ja lisäyksenä, ohitus labelilla', (t) => {
        const repo = alustaRepo(t);
        gitT(repo, 'mv', `${H}/V1_2__kuvaus.sql`, `${H}/V1_2__korjattu.sql`);
        commitoi(repo);
        const { koodi, tuloste } = ajaSkripti(repo);
        assert.equal(koodi, 1, tuloste);
        assert.match(tuloste, /\(poistettu\): '.*V1_2__kuvaus\.sql'/);
        assert.equal(ajaSkripti(repo, { env: { SALLI_V1_MUUTOKSET: 'true' } }).koodi, 0);
    });

    test('develop mergetty haaraan (vastaa CI:n PR-merge-committia)', (t) => {
        const repo = alustaRepo(t);
        gitT(repo, 'checkout', '-q', 'develop');
        lisaa(repo, 'V1_4__developissa.sql');
        commitoi(repo);
        gitT(repo, 'checkout', '-q', 'pr');
        lisaa(repo, 'V1_4__haarassa.sql');
        commitoi(repo);
        gitT(repo, 'merge', '-q', '--no-edit', 'develop');
        const { koodi, tuloste } = ajaSkripti(repo);
        assert.equal(koodi, 1, tuloste);
        assert.match(tuloste, /Versio 4/);
    });

    test('develop edennyt, haaraa ei mergetty (paikallinen ajo)', (t) => {
        const repo = alustaRepo(t);
        gitT(repo, 'checkout', '-q', 'develop');
        lisaa(repo, 'V1_4__developissa.sql');
        commitoi(repo);
        gitT(repo, 'checkout', '-q', 'pr');
        lisaa(repo, 'V1_4__haarassa.sql');
        commitoi(repo);
        const { koodi, tuloste } = ajaSkripti(repo);
        assert.equal(koodi, 1, tuloste);
        assert.match(tuloste, /Versio 4 on jo käytössä basessa/);
        // Developissa lisätty tiedosto ei saa näkyä haaran poistona
        assert.doesNotMatch(tuloste, /poistettu/);
    });

    test('basea ei löydy', (t) => {
        const repo = alustaRepo(t);
        assert.equal(ajaSkripti(repo, { base: 'ei-olemassa-olevaa' }).koodi, 2);
    });

    test('viivalla alkava base on käyttövirhe', (t) => {
        const repo = alustaRepo(t);
        assert.equal(ajaSkripti(repo, { base: '--output=/tmp/x' }).koodi, 2);
    });

    test('git-repon ulkopuolella', (t) => {
        const hakemisto = fs.mkdtempSync(path.join(os.tmpdir(), 'ei-repo-'));
        t.after(() => fs.rmSync(hakemisto, { recursive: true, force: true }));
        assert.equal(ajaSkripti(hakemisto).koodi, 2);
    });

    test('GitHub Actions: tiedostonimi ei voi injektoida workflow-komentoja', (t) => {
        const repo = alustaRepo(t);
        lisaa(repo, 'V1_4__a\n::warning::injektio.sql');
        lisaa(repo, 'V1_5__##[warning]vanha%0A.sql');
        commitoi(repo);
        const { koodi, tuloste } = ajaSkripti(repo, { env: { GITHUB_ACTIONS: 'true' } });
        assert.equal(koodi, 1, tuloste);
        assert.match(tuloste, /^::error::Virheellinen migraatiotiedoston nimi/m);
        assert.doesNotMatch(tuloste, /^::warning::injektio/m);
        assert.doesNotMatch(tuloste, /##\[/);
        // %-merkki escapataan workflow-komennoissa, jottei %0A tulkita rivinvaihdoksi
        assert.match(tuloste, /vanha%250A/);
    });
});
