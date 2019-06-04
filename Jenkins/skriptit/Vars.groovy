return [// Stage nimet
        testikannanLuonti: 'Luo testikanta',
        jarJaTestit: 'Luo JAR ja aja testit',
        testiserverinKannanLuonti: 'Luo testiserverin kanta',
        testiserverinAppLuonti: 'Luo testiserverin app',
        e2eTestit: 'Aja E2E testit',
        stagingserverinKannanLuonti: 'Luo stagingserverin kanta',
        stagingserverinAppLuonti: 'Luo stagingserverin app',
        tuotantoserverinKannanLuonti: 'Luodaan tuotantokanta',
        tuotantoserverinAppLuonti: 'Luodaan tuotantoserverin app',
        e2eTestitTuotanto: 'E2E testit tuotanto',
        // Mitkä staget ajetaan
        testikannanLuontiAjetaan: STAGE_ETIAPAIN == "Kaikki",
        jarJaTestitAjetaan: ["Kaikki", "JAR ja testit"].contains(STAGE_ETIAPAIN),
        testiserverinKannanLuontiAjetaan: ["Kaikki", "JAR ja testit", "Testiserveri"].contains(STAGE_ETIAPAIN),
        testiserverinAppLuontiAjetaan: ["Kaikki", "JAR ja testit", "Testiserveri"].contains(STAGE_ETIAPAIN),
        e2eTestitAjetaan: ["Kaikki", "JAR ja testit", "Testiserveri", "E2E"].contains(STAGE_ETIAPAIN),
        stagingserverinKannanLuontiAjetaan: ["Kaikki", "JAR ja testit", "Testiserveri", "E2E", "Stagingserveri"].contains(STAGE_ETIAPAIN),
        stagingserverinAppLuontiAjetaan: ["Kaikki", "JAR ja testit", "Testiserveri", "E2E", "Stagingserveri"].contains(STAGE_ETIAPAIN)
]