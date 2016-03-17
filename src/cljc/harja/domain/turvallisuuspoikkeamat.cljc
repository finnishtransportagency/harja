(ns harja.domain.turvallisuuspoikkeamat)

(def turpo-tyypit
  {:tyotapaturma         "Työtapaturma"
   :vaaratilanne         "Vaaratilanne"
   :turvallisuushavainto "Turvallisuushavainto"
   :muu                  "Muu"})

(def vahinkoluokittelu-tyypit
  {:henkilovahinko   "Henkilövahinko"
   :omaisuusvahinko  "Omaisuusvahinko"
   :ymparistovahinko "Ympäristövahinko"})

(def turpo-vakavuusasteet
  {:lieva  "Lievä"
   :vakava "Vakava"})

(def turpo-tyontekijan-ammatit
  {:aluksen_paallikko                        "Aluksen päällikkö"
   :asentaja                                 "Asentaja "
   :asfalttityontekija                       "Asfalttityöntekijä"
   :harjoittelija                            "Harjoittelija"
   :hitsaaja                                 "Hitsaaja"
   :kunnossapitotyontekija                   "Kunnossapitotyöntekijä"
   :kansimies                                "Kansimies"
   :kiskoilla_liikkuvan_tyokoneen_kuljettaja "Kiskoilla liikkuvan työkoneen kuljettaja"
   :konemies                                 "Konemies"
   :kuorma-autonkuljettaja                   "Kuorma-auton kuljettaja"
   :liikenteenohjaaja                        "Liikenteenohjaaja"
   :mittamies                                "Mittamies"
   :panostaja                                "Panostaja"
   :peramies                                 "Perämies"
   :porari                                   "Porari"
   :rakennustyontekija                       "Rakennustyöntekijä"
   :ratatyontekija                           "Ratatyöntekijä"
   :ratatyosta_vastaava                      "Ratatyöstä vastaava"
   :sukeltaja                                "Sukeltaja"
   :sahkotoiden_ammattihenkilo               "Sähkötöiden ammattihenkilö"
   :tilaajan_edustaja                        "Tilaajan edustaja"
   :turvalaiteasentaja                       "Turvalaiteasentaja"
   :turvamies                                "Turvamies"
   :tyokoneen_kuljettaja                     "Työkoneen kuljettaja"
   :tyonjohtaja                              "Työnjohtaja"
   :valvoja                                  "Valvoja"
   :veneenkuljettaja                         "Veneenkuljettaja"
   :vaylanhoitaja                            "Väylänhoitaja"
   :muu_tyontekija                           "Muu työntekijä"
   :tyomaan_ulkopuolinen                     "Työmään ulkopuolinen"})

(defn kuvaile-tyontekijan-ammatti [turvallisuuspoikkeama]
  (if (= (:tyontekijanammatti turvallisuuspoikkeama) :muu_tyontekija)
    (:tyontekijanammattimuu turvallisuuspoikkeama)
    (turpo-tyontekijan-ammatit
      (:tyontekijanammatti turvallisuuspoikkeama))))