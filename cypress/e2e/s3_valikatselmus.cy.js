let timeout = 60000; // Minuutin timeout hitaan ci putken takia
let clickTimeout = 10000;
const testiaika = new Date(2021, 9, 15, 12).getTime() // Urakan 1. vuoden loppu

// Helper funkkareita
function siivoaKanta() {
    console.log("Siivotaan kanta!");
    let komento = 'psql -h localhost -U harja harja -c ' + "\"DELETE FROM tavoitehinnan_oikaisu tavo WHERE tavo.\\\"hoitokauden-alkuvuosi\\\" = 2022 AND tavo.\\\"urakka-id\\\" = (SELECT id FROM urakka WHERE nimi = 'Iin MHU 2021-2026');\"";
    console.log("Komento:", komento);
    // Tyhjennetään tavoitehinnan muutokset ennen testiä
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista kiinteähintaiset työt
        cy.exec(terminaaliKomento + komento)
            .then((tulos) => {
                console.log("Poista oikaisut:", tulos)
            });
        })
}

    function naytaPaattyneetUrakat() {
        cy.contains('label', 'Näytä päättyneet', {timeout})
        .should('be.visible')
        .parent()
        .find('input[type="checkbox"]')
        .check()
        .should('be.checked');
    }

describe('Välikatselmus aukeaa', () => {

    before(siivoaKanta);

    it('Välikatselmuksen voi avata päävalikosta', () => {

        //poistaOikaisut("Iin MHU 2021-2026");
        // Alustetaan muutama backend -kutsu
        cy.intercept('POST', 'hae-valikatselmuksen-tiedot-hoitovuodelle' ).as('hae-valikatselmuksen-tiedot')
        cy.intercept('POST', 'tallenna-tavoitehinnan-oikaisu' ).as('tavoitehinnan-oikaisu')
        cy.intercept('POST', 'poista-tavoitehinnan-oikaisu' ).as('poista-tavoitehinnan-oikaisu')

        cy.viewport(1000, 1400)
        cy.visit('/')
        cy.contains('.haku-lista-item', 'Pohjois-Suomi', {timeout}).click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        naytaPaattyneetUrakat()
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Iin MHU 2021-2026', {timeout}).click()
        cy.get('[data-cy=tabs-taso1-Valikatselmus]').click()
        cy.wait('@hae-valikatselmuksen-tiedot')
        cy.get('[data-cy=hoitokausi-valinta]').valinnatValitse({valinta: '2. hoitovuosi (01.10.2022 − 30.09.2023)'})

        // Tarkistellaan tietoja
        cy.contains('Välikatselmus')
        cy.contains('Iin MHU 2021-2026')
        cy.contains('2. hoitovuosi (01.10.2022 − 30.09.2023)')
        cy.contains('Tavoitehinnan muutokset')
        cy.contains('Yhteenveto')

        let hoitovuodenAlunIndeksikorjattuTavoitehinta;
        cy.contains('.valikatselmus-yhteenveto.osio .flex-row.summa-rivi-ylin', 'Hoitovuoden alun indeksikorjattu tavoitehinta')
            .find('span').eq(1)
            .invoke('text')
            .then((summa) => {
                const tavoitehinta = Number(summa.replace(/\s/g, '').replace(',', '.'));
                expect(Number.isFinite(tavoitehinta)).to.be.true;
                hoitovuodenAlunIndeksikorjattuTavoitehinta = tavoitehinta;
            });

        // Lisätään yksi tavoitehinnan oikaisu
        cy.contains('Muutos')
        cy.contains('Perustelu')
        cy.contains('Vaikutus')
        // Alkuun ei saa olla oikaisuja
        cy.contains('Ei muutoksia tavoitehintaan')

        cy.contains('Lisää rivi').click();
        cy.get('[data-cy="luokka-1"]').valinnatValitse({valinta: 'Tiestömuutokset'})
        cy.get('#0selite-1').type('{selectall}seliseli');
        var oikaisu = 100000;
        cy.get('#0summa-1').type('{selectall}'+oikaisu);
        cy.contains('Tallenna muutokset').click();
        cy.wait('@tavoitehinnan-oikaisu')
        // Viesti onnistumisesta pitäisi näkyä
        cy.contains('Oikaisu tallennettu', {timeout: clickTimeout}).should('be.visible');

        // Tarkista oikaisun tiedot
        cy.get('[data-cy="luokka-1"]').contains('Tiestömuutokset');
        cy.get('#0selite-1').should('have.value', 'seliseli')

        cy.wait(1000);

        // Tarkista oikaisun tiedot yhteenvedosta
        cy.contains('.valikatselmus-yhteenveto.osio .flex-row.summa-rivi', 'Tavoitehinnan muutokset')
            .find('span').eq(1)
            .invoke('text')
            .then((teksti) => {
                expect(teksti.replace(/\s/g, '')).to.equal(`+${oikaisu.toFixed(2).replace('.', ',')}`);
            });

        // Tarkista muuttunut tavoitehinta
        cy.contains('.valikatselmus-yhteenveto.osio .flex-row.summa-rivi', 'Hoitovuoden lopun tavoitehinta')
            .find('span').eq(1)
            .invoke('text')
            .then((summa) => {
                const odotettuTavoitehinta = hoitovuodenAlunIndeksikorjattuTavoitehinta + oikaisu;
                expect(summa.replace(/\s/g, '')).to.equal(odotettuTavoitehinta.toFixed(2).replace('.', ','));

                // Tarkista yhteenvedon kattohinta - Käytä 1.1 kerrointa, koska urakka on alkanut -21 vuonna
                const odotettuKattohinta = odotettuTavoitehinta * 1.1;
                return cy.contains('.valikatselmus-yhteenveto.osio .flex-row.summa-rivi', 'Hoitovuoden lopun kattohinta')
                    .find('span').eq(1)
                    .invoke('text')
                    .then((kattohinta) => {
                        expect(kattohinta.replace(/\s/g, '')).to.equal(odotettuKattohinta.toFixed(2).replace('.', ','));
                    });
            });
    })
})
