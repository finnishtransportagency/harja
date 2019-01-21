describe('Ilmoitus-näkymä', function () {
    beforeEach(function () {
        cy.visit("http://localhost:3000/#ilmoitukset/tieliikenne?")
    })

    it("Ilmoitusten default näkymä", function() {
        cy.contains('.murupolku-urakkatyyppi', 'Kaikki')
        cy.get('[data-cy=ilmoitukset-grid] .ajax-loader', {timeout: 10000}).should('not.be.visible')
        cy.get('[data-cy=ilmoitukset-grid]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            let valitseTeksti = function (rivi, otsikko) {
                return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).text().trim()
            };
            cy.wrap(valitseTeksti(0, 'Urakka')).should('equal', 'Aktiivinen Oulu Testi');
            cy.wrap(valitseTeksti(1, 'Urakka')).should('equal', 'Aktiivinen Oulu pääl. Testi');
        })
    })

    it("ilmoitusten haku selitteellä toimii", function () {
        cy.server()
        cy.route({url: "*/hae-ilmoitukset",
                  method: "POST",
                  response:
                  '[["^ ","~:yhteydenottopyynto",false,"~:tila","~:vastaanotettu","~:sijainti",["^ ","~:type","~:point","~:coordinates",[337374.71,7108394.69]],"~:ilmoitusid",50024794,"~:valitetty",["~#dt","01.08.2017 10:05:13"],"~:urakkanimi","Oulun alueurakka 2014-2019","~:tr",["^ ","~:loppuosa",null,"~:numero",775,"~:loppuetaisyys",null,"~:alkuetaisyys",null,"~:alkuosa",null],"~:ilmoitustyyppi","~:tiedoitus","~:myohassa?",false,"~:urakka",4,"~:ilmoitettu",["^9","01.08.2017 10:00:00"],"~:otsikko","Tehkää jotain","~:uusinkuittaus",["^9","01.08.2017 10:07:00"],"~:aiheutti-toimenpiteita",null,"~:id",34,"~:toimenpiteet-aloitettu",null,"~:hallintayksikko",["^ ","^I",12,"~:nimi","Pohjois-Pohjanmaa"],"~:tunniste",null,"~:lisatieto","Lisätietoa","~:selitteet",["~:hiekoitustarve","~:liukkaudentorjuntatarve","~:hoylaystarve"],"~:kuittaukset",[["^ ","~:kuittaaja",["^ ","~:sukunimi","Pöytä","~:etunimi","Mikael"],"~:kuitattu",["^9","01.08.2017 10:07:00"],"^I",60,"~:kuittaustyyppi","~:vastaanotto"],["^ ","^T",["^ ","^U","Pöytä","^V","Mikael"],"^W",["^9","01.08.2017 10:05:13"],"^I",59,"^X","~:valitys"]],"~:urakkatyyppi","~:hoito","~:ilmoittaja",["^ ","~:tyyppi",null]],["^ ","^0",false,"^1","~:lopetettu","^3",["^ ","^4","^5","^6",[337374.71,7108394.69]],"^7",50024793,"^8",null,"^:","Oulun alueurakka 2014-2019","^;",["^ ","^<",null,"^=",775,"^>",null,"^?",null,"^@",null],"^A","^B","^C",true,"^D",4,"^E",["^9","25.01.2017 06:15:17"],"^F","Urakoitsijaviesti","^G",null,"^H",null,"^I",1454930,"^J",null,"^K",["^ ","^I",12,"^L","Pohjois-Pohjanmaa"],"^M",null,"^N",null,"^O",["^R"],"^S",[],"^[","^10","^11",["^ ","^12",null]],["^ ","^0",false,"^1","^13","^3",["^ ","^4","^5","^6",[439180.23,7095708.49]],"^7",50024792,"^8",null,"^:","Espoon alueurakka 2014-2019","^;",["^ ","^<",null,"^=",4,"^>",null,"^?",null,"^@",null],"^A","^B","^C",true,"^D",22,"^E",["^9","25.01.2017 05:39:52"],"^F","Urakoitsijaviesti","^G",null,"^H",null,"^I",1454929,"^J",null,"^K",["^ ","^I",5,"^L","Uusimaa"],"^M",null,"^N","Lähestymiset kiertoliittymiin tavattoman liukkaita.","^O",["^Q"],"^S",[],"^[","^10","^11",["^ ","^12",null]],["^ ","^0",false,"^1","^13","^3",["^ ","^4","^5","^6",[333164.94,7076607.75]],"^7",50024791,"^8",null,"^:","Vantaan alueurakka 2009-2019","^;",["^ ","^<",null,"^=",757,"^>",null,"^?",null,"^@",null],"^A","^B","^C",true,"^D",21,"^E",["^9","25.01.2017 04:58:43"],"^F","Urakoitsijaviesti","^G",null,"^H",null,"^I",1454928,"^J",null,"^K",["^ ","^I",5,"^L","Uusimaa"],"^M",null,"^N","Tie on liukas ja urainen. Hiekkaa ja höyläystä kaivattaisiin kipeästi.","^O",["^P","^Q","^R"],"^S",[],"^[","^10","^11",["^ ","^12",null]],["^ ","^0",true,"^1","^13","^3",["^ ","^4","^5","^6",[357931.18,6974562.61]],"^7",50024790,"^8",null,"^:","Kajaanin alueurakka 2014-2019","^;",["^ ","^<",null,"^=",697,"^>",null,"^?",null,"^@",null],"^A","^B","^C",true,"^D",20,"^E",["^9","25.01.2017 02:03:15"],"^F","Urakoitsijaviesti","^G",null,"^H",null,"^I",1454927,"^J",null,"^K",["^ ","^I",12,"^L","Pohjois-Pohjanmaa"],"^M",null,"^N",null,"^O",["^P"],"^S",[],"^[","^10","^11",["^ ","^12",null]],["^ ","^0",false,"^1","^13","^3",["^ ","^4","^5","^6",[431758.24,7019066.96]],"^7",50024789,"^8",null,"^:","Oulun alueurakka 2014-2019","^;",["^ ","^<",null,"^=",4,"^>",null,"^?",null,"^@",null],"^A","^B","^C",true,"^D",4,"^E",["^9","25.01.2017 00:08:56"],"^F","Urakoitsijaviesti","^G",null,"^H",null,"^I",1454926,"^J",null,"^K",["^ ","^I",12,"^L","Pohjois-Pohjanmaa"],"^M",null,"^N",null,"^O",["~:tieOnLiukas","^Q"],"^S",[],"^[","^10","^11",["^ ","^12",null]]]'}
                ).as('ihaku')
        cy.get('label[for=selite] + div > input').type("Auraustarve")
        cy.get('label[for=ilmoitettu-vakioaikavali] + div > button').click()
        cy.contains("Vapaa aikaväli").click({force: true})
        cy.get('label[for=ilmoitettu-alkuaika] + .pvm-aika-kentta input.pvm').type("1.1.2015")
        cy.contains("Piilota kartta").click() // klikataan mitä vaan muuta jotta pvm-kentän muutos liipaisee päivityksen
        cy.wait('@ihaku', {timeout: 10000})
        cy.contains("Tie on liukas ja urainen")
    })
})
