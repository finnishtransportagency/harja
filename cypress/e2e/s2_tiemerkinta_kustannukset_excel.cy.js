// E2E
// Tiemerkintäurakka: Kustannusten kirjaus > Uusien päällysteiden merkinnät
// Päällystyskohteiden tiemerkintäkustannusten Excel-lataus ja -tuonti
//

const urakkaNimi = 'Utajärven Tiemerkintäurakka POP ELY 2025–2027 (optiot 2028 ja 2029), P';
const vuosi = new Date().getFullYear();
const testiLinjamerkinnat = 987;
const testiLinjamerkinnatTekstina = '987,00';
const loaderTimeout = 30000;

const urakanKohteetSql =
    'SELECT id FROM yllapitokohde ' +
    ` WHERE suorittava_tiemerkintaurakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}')` +
    "   AND yllapitokohdetyotyyppi = 'paallystys' AND poistettu IS NOT TRUE" +
    ` AND vuodet @> ARRAY[${vuosi}]`;

function psql(sql) {
    return cy.terminaaliKomento().then((terminaaliKomento) =>
        cy.exec(`${terminaaliKomento}psql -h localhost -U harja harja -t -A -c "${sql}"`));
}

function poistaUrakanKustannukset() {
    psql(`DELETE FROM tiemerkinta_yllapitokohteen_kustannus WHERE yllapitokohde IN (${urakanKohteetSql})`);
}

// Urakan testidatassa on kustannuksia, joita muut testit käyttävät. Ne palautetaan testien jälkeen.
function varmuuskopioiUrakanKustannukset() {
    psql('DROP TABLE IF EXISTS cy_tm_kustannus_varmuus; ' +
        'CREATE TABLE cy_tm_kustannus_varmuus AS SELECT * FROM tiemerkinta_yllapitokohteen_kustannus ' +
        `WHERE yllapitokohde IN (${urakanKohteetSql})`);
}

function palautaUrakanKustannukset() {
    poistaUrakanKustannukset();
    psql('INSERT INTO tiemerkinta_yllapitokohteen_kustannus SELECT * FROM cy_tm_kustannus_varmuus; ' +
        'DROP TABLE cy_tm_kustannus_varmuus');
}

function asetaUrakanLinjamerkinnat(summa) {
    psql('INSERT INTO tiemerkinta_yllapitokohteen_kustannus (yllapitokohde, linjamerkinnat, pienmerkinnat, jyrsinnat, muut_kustannukset, luoja) ' +
        `SELECT id, ${summa}, 0, 0, 0, (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio') FROM (${urakanKohteetSql}) k`);
}

function avaaUusienPaallysteidenMerkinnat() {
    cy.intercept('POST', '_/hae-tiemerkinta-paallystyskohteiden-kustannukset').as('haeKustannukset');

    cy.visit('/');
    cy.contains('.haku-lista-item', 'Pohjois-Suomi').click();
    cy.get('.ajax-loader', {timeout: loaderTimeout}).should('not.exist');
    cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Tiemerkintä'});
    cy.contains('[data-cy=urakat-valitse-urakka] li', urakkaNimi).click();
    cy.get('[data-cy="tabs-taso1-Kustannusten kirjaus"]').click();
    cy.get('[data-cy="tabs-taso2-Uusien paallysteiden merkinnat"]').click();
    cy.wait('@haeKustannukset');
}

function lataaExcel() {
    return psql(`SELECT id FROM urakka WHERE nimi = '${urakkaNimi}'`).then((tulos) =>
        cy.request({
            method: 'POST',
            url: '/_/excel?_=tiemerkinta-paallystyskustannukset-excel',
            form: true,
            body: {parametrit: JSON.stringify(['^ ', '~:urakka-id', Number(tulos.stdout.trim()), '~:vuosi', vuosi])},
            encoding: 'binary'
        }).then((vastaus) => {
            expect(vastaus.status).to.eq(200);
            return Cypress.Buffer.from(vastaus.body, 'binary');
        }));
}

function tuoTiedosto(sisalto, tiedostonimi) {
    cy.intercept('POST', '_/tuo-tiemerkintakustannukset-excelista').as('tuoExcel');
    cy.get('[data-cy=tiemerkinta-tuo-excel] input[type=file]').selectFile({
        contents: sisalto,
        fileName: tiedostonimi
    }, {force: true});
}

describe('Tiemerkintäkustannusten Excel-tuonti ja -lataus', function () {
    before(function () {
        varmuuskopioiUrakanKustannukset();
    });

    beforeEach(function () {
        poistaUrakanKustannukset();
    });

    after(function () {
        palautaUrakanKustannukset();
    });

    it('Näkymässä on HARJA-ID-sarake sekä Excel-lataus ja -tuonti', function () {
        avaaUusienPaallysteidenMerkinnat();

        cy.get('[data-cy=tiemerkinta-kustannukset-paallystys] thead th').first().should('contain.text', 'HARJA-ID');
        cy.get('[data-cy=tiemerkinta-lataa-excel]').should('be.visible').and('contain.text', 'Lataa Excel');
        cy.get('[data-cy=tiemerkinta-tuo-excel]').should('be.visible').and('contain.text', 'Tuo kustannukset Excelistä');
        cy.get('[data-cy=tiemerkinta-kustannukset-paikkaus] thead th').first().should('not.contain.text', 'HARJA-ID');
    });

    it('Excelistä tuodut kustannukset tallentuvat', function () {
        // Muodostetaan Excel, jossa jokaisen kohteen linjamerkinnät ovat testiarvossa, ja tyhjennetään kanta ennen tuontia
        asetaUrakanLinjamerkinnat(testiLinjamerkinnat);
        lataaExcel().then((excel) => {
            poistaUrakanKustannukset();
            avaaUusienPaallysteidenMerkinnat();
            cy.get('[data-cy=tiemerkinta-kustannukset-paallystys] tbody').should('not.contain.text', testiLinjamerkinnatTekstina);

            tuoTiedosto(excel, 'tiemerkintakustannukset.xlsx');
            cy.wait('@tuoExcel').its('response.statusCode').should('eq', 200);
            cy.wait('@haeKustannukset');
            cy.get('.toast-viesti.onnistunut').should('be.visible');
            cy.get('[data-cy=tiemerkinta-kustannukset-paallystys] tbody').should('contain.text', testiLinjamerkinnatTekstina);
        });
    });

    it('Virheellisestä tiedostosta näytetään virheet', function () {
        avaaUusienPaallysteidenMerkinnat();

        tuoTiedosto(Cypress.Buffer.from('ei excel'), 'virheellinen.xlsx');
        cy.wait('@tuoExcel').its('response.statusCode').should('eq', 400);
        cy.get('[data-cy=tiemerkinta-excel-virheet]')
            .should('be.visible')
            .and('contain.text', 'Tiedosto ei ole kelvollinen Excel-tiedosto');
    });
});
