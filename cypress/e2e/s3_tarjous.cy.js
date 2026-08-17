import * as ks from '../support/kustannussuunnitelmaFns.js';
import {avaaTarjous, avaaUusiKustannussuunnittelu} from "../support/kustannussuunnitelmaFns.js";

let loaderTimeout = 30000;

// Apufunktiot tarjous-näkymälle
function alustaIvalonTarjousUrakka() {
    ks.alustaKanta('Ivalon MHU testiurakka (uusi)');
}

function alustaKajaanin25TarjousUrakka() {
    ks.alustaKanta('POP MHU Kajaani 2025-2030');
}

function alustaIin21TarjousUrakka() {
    ks.alustaKanta('Iin MHU 2021-2026');
}

function avaaTarjousNakyma(urakkanimi, alue) {
    avaaTarjous(urakkanimi, alue);
}

function trimmaaArvo(arvo) {
    // Poistaa ylimääräiset välilyönnit ja trimmauksella
    return arvo.toString().replace(/\s+/g, ' ').replace('€', '').replace(' ', '').replace(',', '.').trim();
}

function tarkistaTarjousRivinInputArvo(taulukonDataCy, rivinTunniste, sarakeIndex, odotettuArvo) {
    cy.get(`[data-cy=${taulukonDataCy}]`)
        .should('be.visible')
        .contains('tbody tr', rivinTunniste)
        .find('input')
        .eq(sarakeIndex)
        .should('be.visible')
        .invoke('val')
        .then((todellinen_arvo) => {
            const trimmattuTodellinen = trimmaaArvo(todellinen_arvo);
            const trimmattuOdotettu = trimmaaArvo(odotettuArvo);
            expect(trimmattuTodellinen).to.equal(trimmattuOdotettu);
        });
}

function tarkistaTarjousRivinTextArvo(taulukonDataCy, rivinTunniste, sarakeIndex, odotettuArvo) {
    cy.get(`[data-cy=${taulukonDataCy}]`)
        .should('be.visible')
        .contains('tbody tr', rivinTunniste)
        .find('td')
        .eq(sarakeIndex)
        .contains(odotettuArvo);
}

function muokkaaTarjousRiviaArvo(taulukonDataCy, rivinTunniste, sarakeIndex, uusiArvo) {
    cy.get(`[data-cy=${taulukonDataCy}]`)
        .should('be.visible')
        .contains('tbody tr', rivinTunniste)
        .find('input')
        .eq(sarakeIndex)
        .should('be.visible')
        .clear()
        .type(uusiArvo)
}

function tarkistaYhteensaSarake(taulukonDataCy, rivinTunniste, odotettuSumma, sarakeIndex) {
    cy.get(`[data-cy=${taulukonDataCy}]`)
        .should('be.visible')
        .contains('tbody tr', rivinTunniste)
        .closest('tr')
        .find('td')
        .eq(sarakeIndex)
        .should('contain', odotettuSumma);
}

function asetaToimenkuvalleArvo(toimenkuva, vuosi1, vuosi2, vuosi3, vuosi4, vuosi5) {
    cy.contains('tbody tr', toimenkuva).find('input[type="text"]').eq(0).clear().type(vuosi1).blur();
    cy.contains('tbody tr', toimenkuva).find('input[type="text"]').eq(1).clear().type(vuosi2).blur();
    cy.contains('tbody tr', toimenkuva).find('input[type="text"]').eq(2).clear().type(vuosi3).blur();
    cy.contains('tbody tr', toimenkuva).find('input[type="text"]').eq(3).clear().type(vuosi4).blur();
    cy.contains('tbody tr', toimenkuva).find('input[type="text"]').eq(4).clear().type(vuosi5).blur();
}

describe('Tarjous-näkymä', function () {

    before(function () {
        alustaIvalonTarjousUrakka();
        alustaKajaanin25TarjousUrakka();
        alustaIin21TarjousUrakka();
    });

    beforeEach(function () {
        cy.intercept('POST', '_/tallenna-tarjouksen-tiedot').as('tallenna-tarjous');
        cy.intercept('POST', '_/hae-tarjouksen-tiedot').as('hae-tarjous');
    });

    describe('Johto- ja hallintokorvaus-gridin testit 2025 urakalle', function () {

        beforeEach(function () {
            avaaTarjousNakyma('POP MHU Kajaani 2025-2030', 'Pohjois-Suomi');
        });

        it('Johto- ja hallintokorvaus-grid näkyy oikein', function () {

            cy.contains('POP MHU Kajaani 2025-2030').should('be.visible');
            cy.wait('@hae-tarjous');
            // Tarkista että Johto- ja hallintokorvaus-taulukko näkyy
            cy.get('.grid').should('contain', 'Johto- ja hallintokorvaus');

            // Tarkista että Valmistelukausi ennen urakka-ajan alkua - toimenkuva on muokattavissa ensimmäiselle hoitovuodelle
            cy.contains('tbody tr', 'Sopimusvastaava')
                .find('input[type="text"]')
                .should('be.visible')
                .should('not.be.disabled');
        });

        it('Johto- ja hallintokorvaus tallennus toimii', function () {

            cy.contains('POP MHU Kajaani 2025-2030').should('be.visible');

            cy.contains('tbody tr', 'Valmistelukausi ennen urakka-ajan alkua')
                .find('input[type="text"]')
                .should('be.visible')
                .should('not.be.disabled');

            asetaToimenkuvalleArvo('Vastuunalainen työnjohtaja', 1000, 2000, 3000, 4000, 5000);
            asetaToimenkuvalleArvo('2. työnjohtaja', 1000, 2000, 3000, 4000, 5000);
            asetaToimenkuvalleArvo('3. työnjohtaja', 1000, 2000, 3000, 4000, 5000);
            asetaToimenkuvalleArvo('Viherhoidosta vastaava henkilö', 1000, 2000, 3000, 4000, 5000);
            asetaToimenkuvalleArvo('Hankintavastaava', 1000, 2000, 3000, 4000, 5000);
            asetaToimenkuvalleArvo('Harjoittelija', 1000, 2000, 3000, 4000, 5000);

            // Tallenna muutokset
            cy.contains('button', 'Tallenna muutokset').click();

            // Tarkista että tallennuskutsu tehdään
            cy.wait('@tallenna-tarjous')
                .its('response.statusCode')
                .should('equal', 200);

            // Tarkista että success-viesti näkyy tai arvo säilyy
            cy.reload();
            cy.get('.ladataan-harjaa', {timeout: loaderTimeout}).should('not.exist');
            cy.wait('@hae-tarjous');
            cy.get('h1').should('contain', 'Tarjouksen tiedot');

            // Varmista että tallennettu arvo säilyy

            cy.get('[data-cy=tarjous-toimenkuvat-grid] table.grid tbody').contains('Johto- ja hallintokorvaus yhteensä').next().next().contains('6 000,00');
            cy.get('[data-cy=tarjous-toimenkuvat-grid] table.grid tbody').contains('Johto- ja hallintokorvaus yhteensä').next().next().next().contains('12 000,00');
            cy.get('[data-cy=tarjous-toimenkuvat-grid] table.grid tbody').contains('Johto- ja hallintokorvaus yhteensä').next().next().next().next().contains('18 000,00');
            cy.get('[data-cy=tarjous-toimenkuvat-grid] table.grid tbody').contains('Johto- ja hallintokorvaus yhteensä').next().next().next().next().next().contains('24 000,00');
            cy.get('[data-cy=tarjous-toimenkuvat-grid] table.grid tbody').contains('Johto- ja hallintokorvaus yhteensä').next().next().next().next().next().next().contains('30 000,00');
            cy.get('[data-cy=tarjous-toimenkuvat-grid] table.grid tbody').contains('Johto- ja hallintokorvaus yhteensä').next().next().next().next().next().next().next().contains('90 000,00');

            tarkistaTarjousRivinInputArvo('tarjous-toimenkuvat-grid', 'Vastuunalainen työnjohtaja', 0, '1 000,00');
            tarkistaTarjousRivinInputArvo('tarjous-toimenkuvat-grid', 'Vastuunalainen työnjohtaja', 1, '2 000,00');
            tarkistaTarjousRivinInputArvo('tarjous-toimenkuvat-grid', 'Vastuunalainen työnjohtaja', 2, '3 000,00');
            tarkistaTarjousRivinInputArvo('tarjous-toimenkuvat-grid', 'Vastuunalainen työnjohtaja', 3, '4 000,00');
            tarkistaTarjousRivinInputArvo('tarjous-toimenkuvat-grid', 'Vastuunalainen työnjohtaja', 4, '5 000,00');

            tarkistaTarjousRivinInputArvo('tarjous-toimenkuvat-grid', 'Harjoittelija', 0, '1 000,00');
            tarkistaTarjousRivinInputArvo('tarjous-toimenkuvat-grid', 'Harjoittelija', 1, '2 000,00');
            tarkistaTarjousRivinInputArvo('tarjous-toimenkuvat-grid', 'Harjoittelija', 2, '3 000,00');
            tarkistaTarjousRivinInputArvo('tarjous-toimenkuvat-grid', 'Harjoittelija', 3, '4 000,00');
            tarkistaTarjousRivinInputArvo('tarjous-toimenkuvat-grid', 'Harjoittelija', 4, '5 000,00');
        });
    });

    describe('Varmista tarjouksen tietojen näkyminen Kustannussuunnitelmassa 2025 urakalla', function () {

        beforeEach(function () {
            avaaTarjousNakyma('POP MHU Kajaani 2025-2030', 'Pohjois-Suomi');
        });

        it('Tallennetut tarjoushinnat on kustannussuunnitelmassa', function () {

            let tarkistaTarjoushinta = function (otsikko, odotettuArvo) {
                cy.contains(otsikko).parent().parent().parent().within(() => {
                    cy.contains('Tarjouksen määrä').next()
                        .invoke('text')
                        .then((todellinen_arvo) => {
                            const trimmattuTodellinen = trimmaaArvo(todellinen_arvo);
                            const trimmattuOdotettu = trimmaaArvo(odotettuArvo);
                            expect(trimmattuTodellinen).to.equal(trimmattuOdotettu);
                        });
                });
            }

            cy.contains('POP MHU Kajaani 2025-2030').should('be.visible');

            // Aseta kilpailutettavat hankinnat
            var kilpailutettavatHankinnat = '50000.00';
            muokkaaTarjousRiviaArvo('tarjous-hankinnat-grid', 'Kilpailutettavat hankinnat', 0, kilpailutettavatHankinnat);

            var akillisethoitotyot = '40000.00';
            muokkaaTarjousRiviaArvo('tarjous-hankinnat-grid', 'Äkilliset hoitotyöt', 0, akillisethoitotyot);

            var erillishankinnat = '41000.00';
            muokkaaTarjousRiviaArvo('tarjous-erillishankinnat-grid', 'Erillishankinnat', 0, erillishankinnat);

            var hoidonjohtopalkkio = '41000.00';
            muokkaaTarjousRiviaArvo('tarjous-hoidonjohtopalkkio-grid', 'Hoidonjohtopalkkio', 0, hoidonjohtopalkkio);

            var toimenkuvat = '21000.00';
            var kaikkiToimenkuvatYhteensa = '26000.00'; // Vanhoista testeistä -25 urakalla on jo summia ensimmäiselle hoitokaudelle tallennettuna
            asetaToimenkuvalleArvo('Sopimusvastaava', toimenkuvat, toimenkuvat, toimenkuvat, toimenkuvat, toimenkuvat);

            // Tallenna muutokset
            cy.contains('button', 'Tallenna muutokset').click();

            // Tarkista että tallennuskutsu tehdään
            cy.wait('@tallenna-tarjous')
                .its('response.statusCode')
                .should('equal', 200);

            cy.reload();
            cy.get('.ladataan-harjaa', {timeout: loaderTimeout}).should('not.exist');
            cy.wait('@hae-tarjous');
            cy.get('h1').should('contain', 'Tarjouksen tiedot');

            // Siirrytään kustiksen puolelle
            avaaUusiKustannussuunnittelu('POP MHU Kajaani 2025-2030', 'Pohjois-Suomi');

            // Varmista tarjoussummat kustiksessa
            cy.contains('Kilpailutettavat hankinnat').should('be.visible');
            tarkistaTarjoushinta('Kilpailutettavat hankinnat', kilpailutettavatHankinnat);
            tarkistaTarjoushinta('Rahavaraukset', akillisethoitotyot);
            tarkistaTarjoushinta('Erillishankinnat', erillishankinnat);
            tarkistaTarjoushinta('Johto- ja hallintokorvaus', kaikkiToimenkuvatYhteensa);
            tarkistaTarjoushinta('Hoidonjohtopalkkio', hoidonjohtopalkkio);
        });
    });
});
