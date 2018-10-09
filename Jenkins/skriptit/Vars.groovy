// Stage nimet
String testikannanLuonti = 'Luo testikanta'
String jarJaTestit = 'Luo JAR ja aja testit'
String testiserverinKannanLuonti = 'Luo testiserverin kanta'
String testiserverinAppLuonti = 'Luo testiserverin app'
String e2eTestit = 'Aja E2E testit'
String stagingserverinKannanLuonti = 'Luo stagingserverin kanta'
String stagingserverinAppLuonti = 'Luo stagingserverin app'
String tuotantoserverinKannanLuonti = 'Luodaan tuotantokanta'
String tuotantoserverinAppLuonti = 'Luodaan tuotantoserverin app'
String e2eTestitTuotanto = 'E2E testit tuotanto'

// Mitkä staget ajetaan
Boolean testikannanLuontiAjetaan = STAGE_ETIAPAIN == "Kaikki"
Boolean jarJaTestitAjetaan = ["Kaikki", "JAR ja testit"].contains(STAGE_ETIAPAIN)
Boolean testiserverinKannanLuontiAjetaan = ["Kaikki", "JAR ja testit", "Testiserveri"].contains(STAGE_ETIAPAIN)
Boolean testiserverinAppLuontiAjetaan = ["Kaikki", "JAR ja testit", "Testiserveri"].contains(STAGE_ETIAPAIN)
Boolean e2eTestitAjetaan = ["Kaikki", "JAR ja testit", "Testiserveri", "E2E"].contains(STAGE_ETIAPAIN)
Boolean stagingserverinKannanLuontiAjetaan = ["Kaikki", "JAR ja testit", "Testiserveri", "Stagingserveri"].contains(STAGE_ETIAPAIN)
Boolean stagingserverinAppLuontiAjetaan = ["Kaikki", "JAR ja testit", "Testiserveri", "Stagingserveri"].contains(STAGE_ETIAPAIN)

return this