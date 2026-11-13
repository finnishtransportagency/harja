import {avaaHarjaTimeoutilla, valitseHoitovuosi, odotaOnnistunutVastausPalvelimelta} from "../support/apurit.js";

const timeout = 30000;
const clickTimeout = 10000;
const urakanNimi = "POP MHU Kajaani 2025-2030";
const urakanAlue = "Pohjois-Suomi";
const hoitovuosiValinta = "1. hoitovuosi (01.10.2025 − 30.09.2026)";
const lukitunHoitovuodenPvm = "15.03.2026";
const lukitsemattomanHoitovuodenPvm = "15.03.2027";
const sivupaneeli = ".ei-sulje-sivupaneelia";
const palautustestinNimi = "Palauttaa testidatan alkuperäiseen tilaan";

const tarjouskentat = [
    {taulukko: "tarjous-hankinnat-grid", rivi: "Kilpailutettavat hankinnat", sarake: 0},
    {taulukko: "tarjous-erillishankinnat-grid", rivi: "Erillishankinnat", sarake: 0},
    {taulukko: "tarjous-toimenkuvat-grid", rivi: "Valmistelukausi ennen urakka-ajan alkua", sarake: 0},
    {taulukko: "tarjous-hoidonjohtopalkkio-grid", rivi: "Hoidonjohtopalkkio", sarake: 0}
];

const tavoitehintakentat = [
    {
        selector: "#kilpailutettavat-hankinnat-elementti table.grid tbody tr:nth-child(1) td input",
        sarake: 0
    },
    {
        selector: "#erillishankinnat-elementti table.grid tbody tr:nth-child(2) td input",
        sarake: 0
    },
    {
        selector: "#johto-ja-hallintokorvaus-elementti table.grid tbody tr:nth-child(2) td input",
        sarake: 0
    },
    {
        selector: "#hoidonjohtopalkkio-elementti table.grid tbody tr:nth-child(2) td input",
        sarake: 0
    }
];

let alkuperaisetTarjousarvot;
let alkuperaisetTavoitehintaArvot;

function asetaInterceptit() {
    cy.intercept("POST", "**/hae-tarjouksen-tiedot").as("hae-tarjous");
    cy.intercept("POST", "**/hae-kustannussuunnitelman-tiedot").as("hae-kustannussuunnitelma");
    cy.intercept("POST", "**/tallenna-tarjouksen-tiedot").as("tallenna-tarjous");
    cy.intercept("POST", "**/tallenna-kilpailutettavat-hankinnat").as("tallenna-kilpailutettavat-hankinnat");
    cy.intercept("POST", "**/tallenna-erillishankinnat").as("tallenna-erillishankinnat");
    cy.intercept("POST", "**/tallenna-johto-ja-hallintokorvaukset-2025").as("tallenna-toimenkuvat-2025");
    cy.intercept("POST", "**/tallenna-hoidonjohtopalkkiot").as("tallenna-hoidonjohtopalkkiot");
    cy.intercept("POST", "**/vahvista-tavoite-ja-kattohinta").as("vahvista-tavoite-ja-kattohinta");
    cy.intercept("POST", "**/hae-valikatselmuksen-tiedot-hoitovuodelle").as("hae-valikatselmuksen-tiedot");
    cy.intercept("POST", "**/tee-tavoitehinnan-pysyvamuutospaatos").as("tee-tavoitehinnan-muutospaatos");
    cy.intercept("POST", "**/poista-paatokset-ketjutetusti").as("poista-paatos");
}

function valitseKustannussuunnitelmanHoitovuosi() {
    odotaOnnistunutVastausPalvelimelta("@hae-kustannussuunnitelma");
    cy.contains("label.alasvedon-otsikko", /^Hoitovuosi$/)
        .first()
        .parent()
        .find("button .valittu")
        .invoke("text")
        .then((valittuHoitovuosi) => {
            const hoitovuosiVaihtuu = !valittuHoitovuosi.includes("1. hoitovuosi");
            valitseHoitovuosi(1);
            if (hoitovuosiVaihtuu) {
                odotaOnnistunutVastausPalvelimelta("@hae-kustannussuunnitelma");
            }
        });
}

function lueTarjousarvot() {
    const arvot = [];
    return cy.wrap(tarjouskentat).each((kentta) => {
        cy.get(`[data-cy="${kentta.taulukko}"]`)
            .contains("tbody tr", kentta.rivi)
            .find("input")
            .eq(kentta.sarake)
            .invoke("val")
            .then((arvo) => arvot.push(arvo));
    }).then(() => arvot);
}

function kirjoitaTarjousarvot(arvot) {
    cy.wrap(tarjouskentat).each((kentta, indeksi) => {
        const syote = `[data-cy="${kentta.taulukko}"]`;
        cy.get(syote)
            .contains("tbody tr", kentta.rivi)
            .find("input")
            .eq(kentta.sarake)
            .clear();
        if (arvot[indeksi] !== "" && arvot[indeksi] !== null && arvot[indeksi] !== undefined) {
            cy.get(syote)
                .contains("tbody tr", kentta.rivi)
                .find("input")
                .eq(kentta.sarake)
                .type(String(arvot[indeksi]));
        }
    });
}

function lueTavoitehintaArvot() {
    const arvot = [];
    return cy.wrap(tavoitehintakentat).each((kentta) => {
        cy.get(kentta.selector).eq(kentta.sarake).invoke("val").then((arvo) => arvot.push(arvo));
    }).then(() => arvot);
}

function kirjoitaTavoitehintaArvot(arvot) {
    cy.wrap(tavoitehintakentat).each((kentta, indeksi) => {
        cy.get(kentta.selector).eq(kentta.sarake).clear();
        if (arvot[indeksi]) {
            cy.get(kentta.selector).eq(kentta.sarake).type(String(arvot[indeksi]));
        }
    });
}

function tallennaTavoitehintaosiot() {
    cy.get("#kilpailutettavat-hankinnat-elementti").contains("Tallenna tiedot").click();
    odotaOnnistunutVastausPalvelimelta("@tallenna-kilpailutettavat-hankinnat");
    cy.contains("Kilpailutettavat hankinnat tallennettiin.", {timeout: clickTimeout}).should("be.visible");

    cy.get("#erillishankinnat-elementti").contains("Jaa tasan joka kuukaudelle").click();
    cy.get("#erillishankinnat-elementti").contains("Tallenna tiedot").click();
    odotaOnnistunutVastausPalvelimelta("@tallenna-erillishankinnat");
    cy.contains("Erillishankinnat tallennettiin onnistuneesti.", {timeout: clickTimeout}).should("be.visible");

    cy.get("#johto-ja-hallintokorvaus-elementti").contains("Jaa tasan joka kuukaudelle").click();
    cy.get("#johto-ja-hallintokorvaus-elementti").contains("Tallenna tiedot").click();
    odotaOnnistunutVastausPalvelimelta("@tallenna-toimenkuvat-2025");

    cy.get("#hoidonjohtopalkkio-elementti").contains("Jaa tasan joka kuukaudelle").click();
    cy.get("#hoidonjohtopalkkio-elementti").contains("Tallenna tiedot").click();
    odotaOnnistunutVastausPalvelimelta("@tallenna-hoidonjohtopalkkiot");
    cy.contains("Hoidonjohtopalkkiot tallennettiin.", {timeout: clickTimeout}).should("be.visible");
}

function avaaKajaaninUrakka() {
    cy.visit("/");
    cy.get(".ladataan-harjaa", {timeout}).should("not.exist");
    cy.contains(".haku-lista-item", urakanAlue, {timeout}).should("be.visible").click();
    cy.get(".ajax-loader", {timeout}).should("not.exist");
    cy.get("[data-cy=murupolku-urakkatyyppi]").valinnatValitse({valinta: "Hoito"});
    cy.contains("[data-cy=urakat-valitse-urakka] li", urakanNimi, {timeout}).click();
}

function peruutaPaatosJosTarpeen() {
    cy.get("[data-cy=tabs-taso1-Valikatselmus]").click();
    odotaOnnistunutVastausPalvelimelta("@hae-valikatselmuksen-tiedot");
    cy.get('img[src="images/ajax-loader.gif"]', {timeout: timeout}).should('not.exist');

    cy.get("[data-cy=hoitokausi-valinta]", {timeout}).should("be.visible");
    cy.get("[data-cy=hoitokausi-valinta]").valinnatValitse({valinta: hoitovuosiValinta});
    odotaOnnistunutVastausPalvelimelta("@hae-valikatselmuksen-tiedot");
    //cy.get('img[src="images/ajax-loader.gif"]', {timeout}).should("not.exist");
    cy.get('img[src="images/ajax-loader.gif"]', {timeout: timeout}).should('not.exist');

    cy.get("#tavhinnan-muutokset .paatos-badge", {timeout}).should(($badge) => {
        expect(["Avoin", "Päätetty"]).to.include($badge.text().trim());
    }).then(($badge) => {
        if ($badge.text().trim() === "Päätetty") {
            cy.get("#tavhinnan-muutokset .paatos-komponentti-otsikko-row").click();
            cy.get("#tavhinnan-muutokset").contains("button", "Peru päätös")
                .should("be.visible")
                .click();
            cy.contains("button", "Kyllä, peru päätös").click();
            odotaOnnistunutVastausPalvelimelta("@poista-paatos");
        }
    });

    cy.get("#tavhinnan-muutokset .paatos-badge").should("contain.text", "Avoin");
}

function peruutaVahvistusJosTarpeen() {
    cy.get("#tavoite-ja-kattohinta-elementti", {timeout}).should(($elementti) => {
        const nappienTekstit = [...$elementti.find("button")].map((button) => button.textContent);
        expect(nappienTekstit.some((teksti) =>
            teksti.includes("Peruuta vahvistus") || teksti.includes("Vahvista tavoite- ja kattohinta")
        )).to.equal(true);
    }).then(($elementti) => {
        const vahvistettu = [...$elementti.find("button")]
            .some((button) => button.textContent.includes("Peruuta vahvistus"));
        if (vahvistettu) {
            cy.get("#tavoite-ja-kattohinta-elementti").contains("button", "Peruuta vahvistus")
                .should("be.visible")
                .click();
            odotaOnnistunutVastausPalvelimelta("@vahvista-tavoite-ja-kattohinta");
        }
    });
    cy.get("#tavoite-ja-kattohinta-elementti")
        .contains("button", "Vahvista tavoite- ja kattohinta")
        .should("be.visible");
}

function palautaTestidata() {
    cy.viewport(1100, 2000);
    avaaKajaaninUrakka();

    peruutaPaatosJosTarpeen();

    cy.get("[data-cy=tabs-taso1-Suunnittelu]").click();
    cy.get("[data-cy='tabs-taso2-Hoitovuoden alun tavoitehinta']").click();
    cy.get("img[src='images/ajax-loader.gif']", {timeout}).should("not.exist");
    valitseKustannussuunnitelmanHoitovuosi();
    peruutaVahvistusJosTarpeen();

    if (alkuperaisetTavoitehintaArvot) {
        kirjoitaTavoitehintaArvot(alkuperaisetTavoitehintaArvot);
        tallennaTavoitehintaosiot();
        lueTavoitehintaArvot().then((arvot) => {
            expect(arvot).to.deep.equal(alkuperaisetTavoitehintaArvot);
        });
    }

    if (alkuperaisetTarjousarvot) {
        cy.get("[data-cy='tabs-taso2-Tarjouksen tiedot']").click();
        cy.get("img[src='images/ajax-loader.gif']", {timeout}).should("not.exist");
        kirjoitaTarjousarvot(alkuperaisetTarjousarvot);
        cy.contains("button", "Tallenna muutokset").click();
        odotaOnnistunutVastausPalvelimelta("@tallenna-tarjous");
        lueTarjousarvot().then((arvot) => {
            expect(arvot).to.deep.equal(alkuperaisetTarjousarvot);
        });
    }
}

function odotaLukitusvirhe(alias, odotettuTeksti) {
    cy.wait(alias, {timeout}).then(({response}) => {
        expect(response, "Tallennuspyynnön vastauksen pitää tulla").to.exist;
        expect(JSON.stringify(response.body)).to.contain(odotettuTeksti);
    });
}

function alustaKantaanTalvisuolat(urakkaNimi) {
    // Poista urakalta talvisuolat
    cy.terminaaliKomento().then((terminaaliKomento) => {
        const sql = `
            UPDATE toteuma
            SET poistettu = true
            WHERE urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');
        `;

        // Poista uudet rivit komennosta, ja pidä yllä oleva query on luettavana
        cy.exec(`${terminaaliKomento} psql -h localhost -U harja harja -c "${sql.replace(/\n/g, ' ')}"`).then((tulos) => {
            console.log("Kanta alustettu:", tulos.stdout);
        });
    });
}

describe("Välikatselmuksen päätös lukitsee ensimmäisen hoitovuoden", () => {
    before(function () {
        asetaInterceptit();
        if (this.currentTest.title !== palautustestinNimi) {
            alkuperaisetTarjousarvot = undefined;
            alkuperaisetTavoitehintaArvot = undefined;
            palautaTestidata();
            alustaKantaanTalvisuolat(urakanNimi);
        }
    });

    it("vahvistaa tavoitehinnan, tekee päätöksen ja estää ensimmäisen hoitovuoden kirjaukset", () => {
        cy.viewport(1100, 2000);
        avaaHarjaTimeoutilla();
        cy.contains(".haku-lista-item", urakanAlue, {timeout}).click();
        cy.get(".ajax-loader", {timeout}).should("not.exist");
        cy.get("[data-cy=murupolku-urakkatyyppi]").valinnatValitse({valinta: "Hoito"});
        cy.contains("[data-cy=urakat-valitse-urakka] li", urakanNimi, {timeout}).click();

        cy.get("[data-cy=tabs-taso1-Suunnittelu]").click();
        cy.get("[data-cy='tabs-taso2-Tarjouksen tiedot']").click();
        cy.wait("@hae-tarjous", {timeout});
        cy.get("img[src='images/ajax-loader.gif']", {timeout}).should("not.exist");
        lueTarjousarvot().then((arvot) => {
            alkuperaisetTarjousarvot = arvot;
        });

        kirjoitaTarjousarvot(["10", "20", "30", "40"]);
        cy.contains("button", "Tallenna muutokset").click();
        odotaOnnistunutVastausPalvelimelta("@tallenna-tarjous");

        cy.get("[data-cy='tabs-taso2-Hoitovuoden alun tavoitehinta']").click();
        cy.get("img[src='images/ajax-loader.gif']", {timeout}).should("not.exist");
        valitseKustannussuunnitelmanHoitovuosi();
        lueTavoitehintaArvot().then((arvot) => {
            alkuperaisetTavoitehintaArvot = arvot;
        });

        kirjoitaTavoitehintaArvot(["10", "20", "30", "40"]);
        tallennaTavoitehintaosiot();

        cy.get("#tavoite-ja-kattohinta-elementti")
            .contains("button", "Vahvista tavoite- ja kattohinta")
            .click();
        odotaOnnistunutVastausPalvelimelta("@vahvista-tavoite-ja-kattohinta");
        cy.get("#tavoite-ja-kattohinta-elementti").contains("button", "Peruuta vahvistus").should("be.visible");

        // Tee päätös välikatselmuksen tavoitehintaan

        cy.get("[data-cy=tabs-taso1-Valikatselmus]").click();
        cy.wait("@hae-valikatselmuksen-tiedot", {timeout});
        cy.get("[data-cy=hoitokausi-valinta] button")
            .should("be.visible")
            .and("be.enabled");
        cy.get("[data-cy=hoitokausi-valinta]").valinnatValitse({valinta: hoitovuosiValinta});
        cy.wait("@hae-valikatselmuksen-tiedot", {timeout});
        cy.contains("1. hoitovuosi (01.10.2025 − 30.09.2026)").should("be.visible");
        cy.get("#tavhinnan-muutokset .paatos-badge").should("contain.text", "Avoin");
        cy.get("#tavhinnan-muutokset").contains("button", "Vahvista päätös").click();
        odotaOnnistunutVastausPalvelimelta("@tee-tavoitehinnan-muutospaatos");
        cy.get("#tavhinnan-muutokset .paatos-badge").should("contain.text", "Päätetty");

        // Kulujen kirjaus

        cy.get("[data-cy=tabs-taso1-Kulut]").click();
        cy.get("[data-cy='tabs-taso2-Kulujen kohdistus']").click();
        cy.get("button").contains("Uusi kulu").click();
        cy.get("[data-cy=koontilaskun-kk-dropdown]").within(() => {
            cy.get("button").click();
        });
        cy.contains(".koontilaskun-kuukaudet-komp", "Syyskuu - 1. hoitovuosi", {timeout})
            .scrollIntoView()
            .should("be.visible")
            .click();
        cy.contains("Kuluja ei voi kirjata 1. hoitovuodelle 2025-2026", {timeout})
            .should("be.visible");
        cy.get(".kululomake").contains("button", "Tallenna").should("be.disabled");
        cy.get(".kululomake").contains("button", "Takaisin").click();

        // Tehtävätoteumat

        cy.intercept("POST", "**/maarien-toteutumien-toimenpiteiden-tehtavat").as("hae-tehtavat");
        cy.intercept("POST", "**/tallenna-toteuma").as("tallenna-tehtavatoteuma");
        cy.get("[data-cy=tabs-taso1-Toteumat]").click();
        cy.get("[data-cy=tabs-taso2-Tehtavat]").click();
        cy.wait("@hae-tehtavat", {timeout});
        valitseHoitovuosi(1);
        cy.get("[data-cy=lisaa-toteuma-nappi]").click();
        const tarkistaPaivamaaranValidoinnit = (otsikko) => {
            const kentta = () => cy.get(".toteumamaarat-lomake")
                .contains("label", otsikko)
                .parents(".form-group");
            const paivamaarakentta = () => kentta().find("input").first();
            paivamaarakentta().clear();
            paivamaarakentta().type(lukitsemattomanHoitovuodenPvm);
            paivamaarakentta().blur();
            paivamaarakentta().clear();
            kentta().contains("Anna päivämäärä").should("be.visible");
            paivamaarakentta().type(lukitunHoitovuodenPvm);
            paivamaarakentta().blur();
            kentta().contains("Päivämäärää ei voida asettaa 1. hoitovuodelle 2025-2026")
                .should("be.visible");
        };
        // Tarkista päivämäärävalidoinnit molemmissa toteumatyypeissä.
        tarkistaPaivamaaranValidoinnit("Työ valmis");
        cy.get(".toteumamaarat-lomake").contains("label", "Lisätyö").click();
        tarkistaPaivamaaranValidoinnit("Pvm");
        cy.get(".toteumamaarat-lomake").contains("label", "Määrämitattava tehtävä").click();
        const tyovalmisPaivamaarakentta = () => cy.get(".toteumamaarat-lomake")
            .contains("label", "Työ valmis")
            .parents(".form-group").find("input").first();
        tyovalmisPaivamaarakentta().clear();
        tyovalmisPaivamaarakentta().type(lukitunHoitovuodenPvm);
        tyovalmisPaivamaarakentta().blur();
        cy.get('[data-cy="toimenpide-valikko"]').valinnatValitse({valinta: '4 PÄÄLLYSTEIDEN PAIKKAUS'});
        cy.get('[data-cy="tehtava-valikko-0"]').should("be.visible");
        cy.get('[data-cy="tehtava-valikko-0"]').valinnatValitse({valinta: 'AB-paikkaus levittäjällä'});
        cy.get("[data-cy=maara-input-0]").should("be.visible").and("be.enabled");
        cy.get("[data-cy=maara-input-0]").clear();
        cy.get("[data-cy=maara-input-0]").type("15");

        // Aseta tieosoite
        cy.get('input.tierekisteri.input-default.tr-numero').type('{selectall}4')
        cy.get('input.tierekisteri.input-default.tr-alkuosa').type('{selectall}404')
        cy.get('input.tierekisteri.input-default.tr-alkuetaisyys').type('{selectall}0')
        cy.get('input.tierekisteri.input-default.tr-loppuosa').type('{selectall}420')
        cy.get('input.tierekisteri.input-default.tr-loppuetaisyys').type('{selectall}1000')
        cy.contains("Et voi kirjata toteumaa 1. hoitovuodelle 2025-2026")
            .should("be.visible");
        cy.contains("Toteutumia ei voi enää lisätä tai muokata, koska hoitovuoden välikatselmuksen päätös tavoitehinnan muutoksista on jo vahvistettu.")
            .should("be.visible");
        cy.get("[data-cy=Tallenna-toteuma-nappi]").click();
        odotaLukitusvirhe("@tallenna-tehtavatoteuma", "lukitsee hoitovuoden 2025");


        // Suolatoteumat

        cy.intercept("POST", "**/tallenna-suolatoteumat").as("tallenna-suolatoteuma");
        cy.intercept("POST", "**/hae-suolatoteumat").as("hae-suolatoteumat");
        cy.get("[data-cy=tabs-taso2-Talvisuola]").click();
        cy.contains("Talvisuolan käyttö", {timeout}).should("be.visible");
        cy.contains("Hae suolatoteumia tieosoiteväliltä", {timeout}).should("be.visible");

        // Hae toteumat aikaväliltä
        cy.get("span.valinnat div.dropdown").eq(0).within(() => {
            cy.get("button").click();
            cy.contains("ul li a", "Valittu aikaväli", {timeout}).click();
        });

        cy.get(".suolatoteumat div.aikavali-valinnat input").eq(0).clear();
        cy.get(".suolatoteumat div.aikavali-valinnat input").eq(0).type("01.01.2026");
        cy.get(".suolatoteumat div.aikavali-valinnat input").eq(1).clear();
        cy.get(".suolatoteumat div.aikavali-valinnat input").eq(1).type("01.11.2026");
        cy.get(".suolatoteumat button").contains("Hae").click();
        cy.wait("@hae-suolatoteumat", {timeout});

        cy.get(".suolatoteumat button").contains("Muokkaa").click();
        cy.get(".suolatoteumat table.grid tbody").then(($tbody) => {
            const muokkausrivienMaara = $tbody.find("tr.muokataan").length;
            cy.get(".suolatoteumat button.grid-lisaa", {timeout})
                .should("be.visible")
                .and("be.enabled")
                .click();
            cy.get(".suolatoteumat table.grid tbody tr.muokataan", {timeout})
                .should("have.length", muokkausrivienMaara + 1);
        });

        const uusiSuolatoteumanRivi = () =>
            cy.get(".suolatoteumat table.grid tbody tr.muokataan").last();
        const kaytetynMaaranKentta = () =>
            uusiSuolatoteumanRivi().find("td").eq(3).find("input");
        const toteumienLukumaaraKentta = () =>
            uusiSuolatoteumanRivi().find("td").eq(4).find("input");
        kaytetynMaaranKentta().clear();
        kaytetynMaaranKentta().type(1);
        toteumienLukumaaraKentta().clear();
        toteumienLukumaaraKentta().type(2);



        uusiSuolatoteumanRivi().find("td").eq(1).find("button").click();
        cy.contains("ul li a", "Talvisuola, rakeinen NaCl", {timeout}).click();
        //Tarkistetaan, että tallenna -nappi on näkyvissä, koska päivämäärää ei ole asetettu ja toteuma ei voi olla vielä lukitulla vuodella
        cy.get(".suolatoteumat button.grid-tallenna").should("be.visible");

        const suolatoteumanPaivamaarakentta = () =>
            uusiSuolatoteumanRivi().find("td").eq(2).find("input");
        suolatoteumanPaivamaarakentta().clear();
        suolatoteumanPaivamaarakentta().type(lukitunHoitovuodenPvm);
        suolatoteumanPaivamaarakentta().type("{esc}");
        // Tarkistetaan, että tallenna -nappi on piilotettu, koska päivämäärä on asetettu lukitulle hoitovuodelle
        cy.get(".suolatoteumat button.grid-tallenna").should("be.disabled");

        // Materiaalitoteumat

        cy.intercept("POST", "**/hae-materiaalitoteuman-lukitustiedot").as("hae-materiaalitoteuman-lukitus");
        cy.get("[data-cy='tabs-taso2-Muut materiaalit']").click();
        cy.contains("Materiaalien käyttö", {timeout}).should("be.visible");
        cy.contains("button", "Lisää toteuma").click();
        cy.wait("@hae-materiaalitoteuman-lukitus", {timeout});
        cy.get(".toteuman-tiedot").contains("label", "Aloitus")
            .parents(".form-group").find("input").first().type('{selectAll}'+lukitunHoitovuodenPvm);
        cy.get(".toteuman-tiedot").contains("label", "Lopetus")
            .parents(".form-group").find("input").first().focus();
        cy.contains("Päivämäärää ei voida asettaa 1. hoitovuodelle 2025-2026, koska hoitovuoden tiedot on lukittu välikatselmuksen päätöksen johdosta.")
            .should("be.visible");

        cy.get(".materiaalit-toteuma-grid").contains("button", "Lisää materiaali").click();
        const uusiMateriaalirivi = () =>
            cy.get(".materiaalit-toteuma-grid table.grid tbody tr.muokataan").last();
        uusiMateriaalirivi().contains("button", "- valitse materiaali -").click();
        cy.contains("ul li a", "Natriumformiaatti", {timeout}).click();
        uusiMateriaalirivi().find("input").first().clear();
        uusiMateriaalirivi().find("input").first().type("1");

        cy.get(".toteuman-tiedot").contains("button", "Tallenna toteuma").should("be.disabled");
        cy.get(".toteuman-tiedot").contains("button", "Takaisin materiaaliluetteloon").click();

        cy.intercept("POST", "**/hae-urakan-sanktiot-ja-bonukset").as("hae-sanktiot");
        cy.get("[data-cy=tabs-taso1-Laadunseuranta]").click();
        cy.get("[data-cy='tabs-taso2-Sanktiot ja bonukset']").click();
        cy.wait("@hae-sanktiot", {timeout});
        valitseHoitovuosi(1);
        cy.contains(".lisaa-nappi", "Lisää uusi").click();
        cy.get(sivupaneeli, {timeout}).should("be.visible");
        cy.get(sivupaneeli).contains("label", "Arvonvähennys").click();
        cy.contains("Arvonvähennyksiä ei voi kirjata 1. hoitovuodelle 2025-2026")
            .should("be.visible");
        cy.get(sivupaneeli).contains("button", "Tallenna").should("not.exist");
    });

    it(palautustestinNimi, () => {
        asetaInterceptit();
        alustaKantaanTalvisuolat(urakanNimi);
        avaaKajaaninUrakka();
        peruutaPaatosJosTarpeen();
        cy.get("[data-cy=tabs-taso1-Suunnittelu]").click();
        cy.get("[data-cy='tabs-taso2-Hoitovuoden alun tavoitehinta']").click();
        cy.get("img[src='images/ajax-loader.gif']", {timeout}).should("not.exist");
        valitseKustannussuunnitelmanHoitovuosi();
        peruutaVahvistusJosTarpeen();
        //palautaTestidata();
    });
});
