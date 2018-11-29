describe('POT-lomake', function () {
    // Lisätään kantaan puhdas testidata
    before(function () {
        let lisaaPallystyskohdeKomento = '"INSERT INTO yllapitokohde ' +
            '(yllapitoluokka, urakka, sopimus, yha_kohdenumero, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi, yhaid,' +
            ' tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista,' +
            ' suorittava_tiemerkintaurakka, vuodet, keskimaarainen_vuorokausiliikenne, poistettu)' +
            'VALUES' +
            '  (8, (SELECT id' +
            '       FROM urakka' +
            '       WHERE nimi = \'Muhoksen päällystysurakka\'),' +
            '      (SELECT id' +
            '       FROM sopimus' +
            '       WHERE urakka = (SELECT id' +
            '                       FROM urakka' +
            '                       WHERE nimi = \'Muhoksen päällystysurakka\') AND paasopimus IS NULL),' +
            '      323, \'L323\', \'E2E-Testi\', \'paallyste\' :: yllapitokohdetyyppi,' +
            '      \'paallystys\' ::yllapitokohdetyotyyppi, 3233231,' +
            '      22, 1, 0, 3, 100, 1, 1, (SELECT id' +
            '                             FROM urakka' +
            '                             WHERE nimi =' +
            '                                   \'Oulun tiemerkinnän palvelusopimus 2013-2018\'),' +
            '   \'{2017}\', 500, FALSE);"';
        let lisaaPaallystyskohteenAlikohdeKomento = '"INSERT INTO yllapitokohdeosa ' +
            '(id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)' +
            ' VALUES (3231, (SELECT id' +
            '              FROM yllapitokohde' +
            '              WHERE nimi = \'E2E-Testi\'), \'E2E-Testi-kohdeosa\', 22, 1, 0, 3, 100, 1, 1, ST_GeomFromText(' +
            '                 \'MULTILINESTRING((428022.17200000025 7210433.971000001,428029.1699999999 7210429.881999999,428035.78000000026 7210425.715999998,428048.9550000001 7210417.215999998,428062.8629999999 7210407.675999999,428070.62399999984 7210402.287999999,428075.29200000037 7210399.037999999,428078.5290000001 7210392.477000002,428080.3339999998 7210389.936000001,428085.8289999999 7210383.306000002,428089.8509999998 7210378.252999999,428095.27300000004 7210372.537999999,428100.5410000002 7210366.791999999,428101.3169999998 7210366.158,428116.49399999995 7210353.760000002,428137.1639999999 7210338.458999999,428163.2759999996 7210321.556000002,428183.23699999973 7210308.458000001,428192.71300000045 7210301.184,428230.5650000004 7210272.577,428259.6370000001 7210251.410999998,428262.7050000001 7210249.287999999,428267.5920000002 7210245.73,428271.41199999955 7210242.896000002,428276.29200000037 7210239.171,428292.8789999997 7210226.982999999,428304.02300000004 7210218.655000001,428320.03199999966 7210207.748,428322.28500000015 7210206.269000001,428345.034 7210191.3440000005,428365.59800000023 7210176.995000001,428370.49700000044 7210173.576000001,428376.51099999994 7210169.23,428393.784 7210157.368000001,428412.1880000001 7210145.291999999,428434.78699999955 7210129.916000001,428443.409 7210124.9070000015,428450.8969999999 7210120.965,428457.1629999997 7210117.486000001,428497.5650000004 7210095.3500000015,428540.0250000004 7210071.98,428580.43099999987 7210049.925999999,428628.06099999975 7210023.956,428674.8839999996 7209998.105,428708.426 7209978.971999999,428724.86199999973 7209969.864,428741.5599999996 7209961.359999999,428754.68099999987 7209954.77,428763.8509999998 7209950.793000001,428768.9620000003 7209948.576000001,428778.8119999999 7209945.109000001,428796.6660000002 7209939.6559999995,428812.0049999999 7209935.495999999,428827.26400000043 7209931.723999999,428848.64499999955 7209927.727000002,428870.99700000044 7209925.396000002,428889.6610000003 7209924.293000001,428916.0159999998 7209924.057999998,428940.0769999996 7209923.521000002,428975.69299999997 7209924.732999999,428998.68200000003 7209925.629999999,429036.8820000002 7209926.397,429060.4230000004 7209926.943,429079.12200000044 7209926.570999999,429095.0839999998 7209926.070999999,429114.6500000004 7209925.453000002,429140.14300000016 7209923.669,429152.50600000005 7209922.429000001,429201.32799999975 7209917.068,429255.6440000003 7209908.998,429309.9709999999 7209898.800999999,429346.5800000001 7209890.313000001,429378.7659999998 7209882.035,429406.76800000016 7209873.8440000005,429437.9759999998 7209863.364999998,429458.3439999996 7209856.596000001,429488.31599999964 7209845.714000002,429516.13999999966 7209834.210999999,429545.55200000014 7209822.250999998,429566.26300000027 7209812.374000002,429599.4570000004 7209794.827,429600.35699999984 7209794.383000001,429603.26300000027 7209792.949999999,429616.1009999998 7209785.561000001,429622.0460000001 7209783.019000001,429637.2719999999 7209773.8379999995,429650.91899999976 7209766.543000001,429665.6210000003 7209758.390999999,429689.30200000014 7209744.765999999,429714.63800000027 7209728.366999999,429749.8559999997 7209703.508000001,429774.8820000002 7209684.441,429777.8700000001 7209681.866999999,429781.58499999996 7209679.026999999,429813.31900000013 7209656.033,429872.34499999974 7209606.5249999985,429893.7089999998 7209588.408,429912.43099999987 7209571.734999999,429941.62600000016 7209542.886999998,429964.37399999984 7209518.390999999,429990.62200000044 7209489.019000001,430011.5379999997 7209464.73,430017.63599999994 7209457.193999998,430029.46499999985 7209442.374000002,430059.99899999984 7209403.283,430117.13999999966 7209324.318999998,430184.50100000016 7209225.384,430192.36400000006 7209214.076000001,430203.9349999996 7209197.116999999,430208.3269999996 7209190.228999998,430213.0020000003 7209182.789000001,430235.0020000003 7209152.021000002,430258.40299999993 7209118.477000002,430281.5619999999 7209085.101,430287.88800000027 7209075.984000001,430305.8710000003 7209051.372000001,430337.84800000023 7209009.576000001,430378.1710000001 7208960.170000002,430408.0599999996 7208925.614999998,430448.9979999997 7208880.381999999,430484.841 7208841.715999998,430535.71999999974 7208786.785999998,430576.6579999998 7208742.903000001,430621.5839999998 7208694.539999999,430662.5870000003 7208650.737,430698.9979999997 7208611.366,430738.03500000015 7208569.603,430773.3530000001 7208531.488000002,430812.0219999999 7208490.166999999,430848.18599999975 7208450.982999999,430854.63900000043 7208443.942000002,430879.7110000001 7208416.585000001,430887.13599999994 7208408.186000001,430899.8099999996 7208393.616999999,430908.9759999998 7208383.090999998,430927.31400000025 7208361.587000001,430933.8839999996 7208353.335999999,430956.29399999976 7208325.190000001,430991.67200000025 7208280.901000001,431001.45799999963 7208268.120000001,431006.66199999955 7208261.645,431025.04200000037 7208237.489,431047.74899999984 7208207.506000001,431070.9179999996 7208176.611000001,431073.4230000004 7208173.271000002,431089.7860000003 7208151.574999999,431099.17399999965 7208138.730999999,431111.08999999985 7208122.429000001,431130.1040000003 7208095.916999999,431139.5429999996 7208082.309999999,431142.59499999974 7208077.903000001,431149.7460000003 7208067.550000001,431162.11000000034 7208049.795000002,431172.18099999987 7208035.635000002,431182.051 7208020.533,431204.12399999984 7207988.009,431232.53000000026 7207946.425000001,431265.2980000004 7207898.1620000005,431299.7599999998 7207847.585000001,431341.0769999996 7207787.835000001,431374.55900000036 7207741.175000001,431396.642 7207711.612,431418.6799999997 7207682.984000001,431448.4670000002 7207645.451000001,431464.8820000002 7207625.748,431495.41000000015 7207589.447000001,431526.5650000004 7207553.971999999,431550.5889999997 7207527.984000001,431580.8799999999 7207495.603,431614.23000000045 7207461.074000001,431648.3049999997 7207427.452,431682.2000000002 7207395.2820000015,431718.9519999996 7207361.434,431755.20600000024 7207329.625999998,431801.4419999998 7207290.651000001,431847.7690000003 7207253.2179999985,431879.16700000037 7207229.169,431892.7599999998 7207218.877999999,431920.7719999999 7207197.671,431922.2410000004 7207196.559,431927.27699999977 7207192.9629999995,431937.14300000016 7207185.918000001,431978.1210000003 7207156.651000001,432003.59800000023 7207138.418000001,432026.0190000003 7207123.122000001,432061.63800000027 7207100.392000001,432102.1299999999 7207074.848999999,432107.90299999993 7207071.4070000015,432119.55900000036 7207064.300999999,432135.3799999999 7207055.403000001,432148.93099999987 7207047.384,432169.2570000002 7207036.192000002,432202.2340000002 7207018.204,432237.19099999964 7206999.557,432249.7589999996 7206993.289999999,432252.17700000014 7206992.112,432265.07299999986 7206985.826000001,432297.1579999998 7206971.048999999,432320.3269999996 7206960.625,432368.05200000014 7206938.9059999995,432389.95100000035 7206929.427999999,432413.8119999999 7206919.015000001,432470.45100000035 7206895.620999999,432512.7089999998 7206878.952,432548.70999999996 7206864.897,432590.82799999975 7206848.368000001,432646.767 7206826.607999999,432663.324 7206820.043000001,432695.04899999965 7206807.464000002,432725.8200000003 7206794.903000001,432737.42200000025 7206790.166000001,432759.43900000025 7206781.175999999,432820.824 7206755.006999999,432837.8650000002 7206747.925999999,432874.8660000004 7206731.741999999,432917.6629999997 7206712.750999998,432962.1919999998 7206692.353999998,432980.58499999996 7206683.848999999,433011.91500000004 7206669.359000001,433055.46999999974 7206648.636,433099.56599999964 7206627.1570000015,433142.0389999999 7206606.217,433194.3590000002 7206579.760000002,433245.3269999996 7206553.249000002,433255.9879999999 7206547.677999999,433281.0700000003 7206534.48,433300.7750000004 7206524.234000001,433318.24899999984 7206515.625999998,433347.73199999984 7206498.184,433364.8679999998 7206488.958999999,433400.8289999999 7206469.313999999,433439.28699999955 7206448.346000001,433456.56900000013 7206438.791000001,433484.892 7206422.763,433525.49700000044 7206400.769000001,433548.51400000043 7206389.123,433575.77300000004 7206375.623,433611.0120000001 7206359.153000001,433655.19799999986 7206336.989999998,433679.9110000003 7206324.612,433718.90299999993 7206305.289999999,433751.74899999984 7206289.647999998,433785.29399999976 7206273.511999998,433827.0719999997 7206253.864999998,433864.61899999995 7206234.899,433885.48000000045 7206225.226,433910.7570000002 7206214.324000001,433921.5379999997 7206209.291000001,433946.69799999986 7206197.015999999,433974.19799999986 7206184.4120000005,433997.4960000003 7206175.055,434030.0820000004 7206160.653000001,434070.94799999986 7206143.285999998,434119.8969999999 7206122.305,434164.75100000016 7206103.577,434209.7189999996 7206083.245000001,434238.966 7206071.495999999,434266.42700000014 7206060.096000001,434274.9060000004 7206056.1559999995,434306.1679999996 7206041.631999999,434342.6289999997 7206026.296999998,434394.0089999996 7206004.232000001,434440.6339999996 7205984.2250000015,434458.8540000003 7205976.620000001))\'));"';
        let poistaKohteenOsaKomento = '"DELETE FROM yllapitokohdeosa' +
            ' WHERE nimi=\'E2E-Testi-kohdeosa\';"';
        let poistaKohdeKomento = '"DELETE FROM yllapitokohde WHERE nimi=\'E2E-Testi\';"';
        cy.exec('/usr/local/bin/docker ps | grep harjadb', {failOnNonZeroExit: false}).then((tulos) => {
            let terminaaliKomento;
            if (tulos.code === 0 && tulos.stdout !== '') {
                terminaaliKomento = '/usr/local/bin/docker exec harjadb ';
            } else {
                terminaaliKomento = '';
            }
            return terminaaliKomento
        }).then((terminaaliKomento) => {
            cy.log("TERMINAALIKOMENTO: " + terminaaliKomento)
            cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + poistaKohteenOsaKomento)
            cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + poistaKohdeKomento)
            cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + lisaaPallystyskohdeKomento)
            cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + lisaaPaallystyskohteenAlikohdeKomento)
        })
        cy.server()
    })
    it('Avaa vanha POT-lomake', function () {
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa ja Kainuu').click()
        cy.get('[data-cy=murupolku-urakkatyyppi] button').click()
        // Pudotusvalikoissa pitää tarkistaa ensin, että onhan ne vaihtoehdot näkyvillä. Tämä siksi, että valikon
        // painaminen, jolloin lista vaihtoehtoja tulee näkyviin re-renderaa listan. Tämä taasen aiheuttaa sen,
        // että Cypress saattaa keretä napata tuolla seuraavalla 'contains' käskyllä elementin, jonka React
        // poistaa DOM:ista.
        cy.route('POST', '**/_/urakan-paallystysilmoitukset').as('haeUrakanPaallystysilmoitukset')
        cy.contains('[data-cy=murupolku-urakkatyyppi] ul li a', 'Päällystys').should('be.visible').click()
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Muhoksen päällystysurakka').click()
        cy.get('[data-cy=tabs-taso1-Kohdeluettelo]').click()
        cy.get('[data-cy=tabs-taso2-Päällystysilmoitukset]').click()
        cy.get('[data-cy=tabs-taso2-Päällystysilmoitukset]').parent().should('have.class', 'active')
        //cy.wait('@haeUrakanYllapitokohteet')
        // Tämä rivi on estämässä taasen jo poistettujen elementtien käsittelyä. Eli odotellaan
        // paallystysilmoituksien näkymistä guilla ennen kuin valitaan 2017 vuosi.
        cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader').should('not.be.visible')
        cy.get('[data-cy=valinnat-vuosi] button').click()
        cy.get('[data-cy=valinnat-vuosi] .dropdown-menu').should('have.css', 'display').and('match', /block/)
        cy.contains('[data-cy=valinnat-vuosi] ul li a', '2017').click({force: true})
        cy.get('[data-cy=paallystysilmoitukset-grid] .ajax-loader').should('be.visible')
        cy.get('[data-cy=paallystysilmoitukset-grid] tr')
            .contains('E2E-Testi')
            .parentsUntil('tbody')
            .contains('button', 'Aloita päällystysilmoitus').click()
    })
    it('Oikeat aloitustiedot', function () {
        // Tierekisteritaulukon tienumeroa, ajorataa ja kaistaa ei pitäisi pystyä muutamaan
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] th').then(($otsikot) => {
            let tienumeroIndex;
            let ajorataIndex;
            let kaistaIndex;
            for (let i = 0; i < $otsikot.length; i++) {
                let otsikonTeksti = $otsikot[i].textContent.trim()
                if (otsikonTeksti.localeCompare('Tienumero') === 0) {
                    tienumeroIndex = i;
                } else if (otsikonTeksti.localeCompare('Ajorata') === 0) {
                    ajorataIndex = i;
                } else if (otsikonTeksti.localeCompare('Kaista') === 0) {
                    kaistaIndex = i;
                }
            }
            return [tienumeroIndex, ajorataIndex, kaistaIndex]
        }).then((eiMuokattavatSarakkeet) => {
            cy.log("tienumeroIndex: " + eiMuokattavatSarakkeet)
            cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] tbody tr').then(($rivi) => {
                eiMuokattavatSarakkeet.forEach((i) => {
                    expect($rivi.children().get(i)).to.have.class('ei-muokattava');
                });
            })
        })
        // Pääkohteen tierekisteriosoitetta ei pitäisi pystyä muuttamaan
        cy.get('[data-cy=paallystysilmoitus-perustiedot]')
            .contains('Tierekisteriosoite')
            .parentsUntil('.row.lomakerivi')
            .contains('Tie ')
            .should('have.class', 'form-control-static')
    })
    it('Rivien lisäys', function () {
        // Lisätään jokunen rivi
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] tbody tr button')
            .contains('Lisää osa').click().click().click()
        // Katsotaan, että niissä on oikeanlaisia virheitä
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] tbody .virheet')
            .should('have.length', 12)
            .each(($virheet, index, $virheetLista) => {
                switch (index) {
                    case 0:
                        expect($virheet.text().replace(/[\u00AD]+/g, '')).to.contain('Anna loppuosa');
                        break;
                    case 1:
                        expect($virheet.text().replace(/[\u00AD]+/g, '')).to.contain('Anna loppuetäisyys');
                        break;
                    case 2:
                        expect($virheet.text().replace(/[\u00AD]+/g, '')).to.contain('Anna alkuosa');
                        break;
                    case 3:
                        expect($virheet.text().replace(/[\u00AD]+/g, '')).to.contain('Anna alkuetäisyys');
                        break;
                }
            })
        // Katsotaan, että vain ensimmäisellä rivillä on alkuosa ja alkuetäisyys, kun taas viimeisellä rivillä on
        // loppuosa ja loppuetaisyys
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            expect($rivit.first().find('td').eq($otsikot.get('Aosa')).find('input')).to.have.value('1');
            expect($rivit.first().find('td').eq($otsikot.get('Aet')).find('input')).to.have.value('0');
            expect($rivit.first().find('td').eq($otsikot.get('Losa')).find('input')).to.be.empty;
            expect($rivit.first().find('td').eq($otsikot.get('Let')).find('input')).to.be.empty;
            expect($rivit.last().find('td').eq($otsikot.get('Aosa')).find('input')).to.be.empty;
            expect($rivit.last().find('td').eq($otsikot.get('Aet')).find('input')).to.be.empty;
            expect($rivit.last().find('td').eq($otsikot.get('Losa')).find('input')).to.have.value('3');
            expect($rivit.last().find('td').eq($otsikot.get('Let')).find('input')).to.have.value('100');
            for (let i = 1; i < $rivit.length - 1; i++) {
                let sarakkeet = $rivit.eq(i).find('td');
                expect(sarakkeet.eq($otsikot.get('Aosa')).find('input')).to.be.empty;
                expect(sarakkeet.eq($otsikot.get('Aet')).find('input')).to.be.empty;
                expect(sarakkeet.eq($otsikot.get('Losa')).find('input')).to.be.empty;
                expect(sarakkeet.eq($otsikot.get('Let')).find('input')).to.be.empty;
            }
        })
        // Täytetään väärässä muodoss olevaa dataa
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikot) => {
            let $rivit = $gridOtsikot.grid.find('tbody tr');
            let $otsikot = $gridOtsikot.otsikot;
            let valitseInput = function (rivi, otsikko) {
                return $rivit.eq(rivi).find('td').eq($otsikot.get(otsikko)).find('input')
            }
            // Piillotetaan kartta, jotta se ei ole gridin edessä
            cy.get('[data-cy=piilota-kartta]').click()
            cy.wrap(valitseInput(0, 'Aosa')).clear().type(2)
            cy.wrap(valitseInput(0, 'Losa')).type(1)
            cy.wrap(valitseInput(0, 'Let')).type(100000)
            cy.wrap(valitseInput(1, 'Aosa')).type(3)
            cy.wrap(valitseInput(1, 'Aet')).type(10)
            cy.wrap(valitseInput(1, 'Losa')).type(3)
            cy.wrap(valitseInput(1, 'Let')).type(20)
            cy.wrap(valitseInput(2, 'Aosa')).type(3)
            cy.wrap(valitseInput(2, 'Aet')).type(15)
            cy.wrap(valitseInput(2, 'Losa')).type(3)
            cy.wrap(valitseInput(2, 'Let')).type(25)
            cy.wrap(valitseInput(2, 'Nimi')).type('Foo')
            cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet]').gridOtsikot().then(($gridOtsikotJalkeen) => {
                let $rivitJalkeen = $gridOtsikotJalkeen.grid.find('tbody tr');
                let $otsikotJalkeen = $gridOtsikotJalkeen.otsikot;
                let virheValinta = function (rivi, otsikko) {
                    return $rivitJalkeen.eq(rivi).find('td').eq($otsikotJalkeen.get(otsikko)).find('.virhe').children().map(function () {
                        return this.textContent.replace(/[\u00AD]+/g, '').trim()
                    }).get()
                };
                expect(virheValinta(0, 'Aosa')).to.have.lengthOf(2)
                    .and.to.contain('Alkuosa ei voi olla loppuosan jälkeen')
                    .and.to.contain('Tiellä 22 ei ole osaa 2');
                expect(virheValinta(0, 'Aet')).to.be.empty;
                expect(virheValinta(0, 'Losa')).to.have.lengthOf(1)
                    .and.to.contain('Loppuosa ei voi olla alkuosaa ennen');

                expect(virheValinta(0, 'Let')).to.have.lengthOf(1);
                expect(virheValinta(0, 'Let')[0]).to.contain('Osan 1 maksimietäisyys on ');
                ['Aosa', 'Aet', 'Losa', 'Let'].forEach((otsikko) => {
                    expect(virheValinta(1, otsikko)).to.have.length(1)
                        .and.to.contain('Kohteenosa on päällekkäin osan Foo kanssa')
                    expect(virheValinta(2, otsikko)).to.have.length(1)
                        .and.to.contain('Kohteenosa on päällekkäin toisen osan kanssa')
                })
            })
            cy.get('[data-cy=paallystystoimenpiteen-tiedot]').then(($ptGrid) => {
                expect($ptGrid.find('.panel-heading span').text()).to.contain('Tierekisterikohteet taulukko on virheellisessä tilassa')
                expect($ptGrid.find('input').get()).to.be.empty;
            })
            cy.get('[data-cy=kiviaines-ja-sideaine]').then(($ksGrid) => {
                expect($ksGrid.find('.panel-heading span').text()).to.contain('Tierekisterikohteet taulukko on virheellisessä tilassa')
                expect($ksGrid.find('input').get()).to.be.empty;
            })
            cy.get('[data-cy=pot-tallenna]').should('be.disabled')
        })

        // Täytetään oikeassa muodossa olevaa dataa ja lisätään paljon uusia rivejä.
        // Joskus oli ongelmia ison rivimäärän kanssa.
    })
})