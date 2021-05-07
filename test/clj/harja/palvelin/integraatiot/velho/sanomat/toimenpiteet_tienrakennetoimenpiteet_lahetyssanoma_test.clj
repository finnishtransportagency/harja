(ns harja.palvelin.integraatiot.velho.sanomat.toimenpiteet-tienrakennetoimenpiteet-lahetyssanoma-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [harja.palvelin.integraatiot.velho.sanomat.toimenpiteet-tienrakennetoimenpiteet-lahetyssanoma :as lahetyssanoma]
            [harja.palvelin.integraatiot.yha.kohteen-lahetyssanoma-test :as testi-tiedot]))

(deftest muodostaa-yksinkertaisen-sanoman
  (let [json-sanoma (lahetyssanoma/muodosta testi-tiedot/testiurakka testi-tiedot/testikohteet)
        sanoma (json/read-str json-sanoma)
        odotettu-sanoma {"alkusijainti" {"osa" 1,
                                         "tie" 20,
                                         "etaisyys" 10,
                                         "ajorata" 1},
                         "loppusijainti" {"osa" 1,
                                          "tie" 20,
                                          "etaisyys" 1000,
                                          "ajorata" 1},
                         "sijaintitarkenne" {"kaista" 11},
                         "ominaisuudet" {"toimenpide" "tienrakennetoimenpide/trtp01",
                                         "sidottu-paallysrakenne" {"tyyppi" "sidotun-paallysrakenteen-tyyppi/spt01",
                                                                   "paallysteen-tyyppi" "paallystetyyppi/pt02",
                                                                   "paallystemassa" {"asfalttirouheen-osuus-asfalttimassassa" 6,
                                                                                     "bitumiprosentti" 6.6,
                                                                                     "paallystemassan-runkoaine" {"materiaali" ["materiaali/m37",
                                                                                                                                "materiaali/m36"],
                                                                                                                  "uusiomateriaalin-kayttomaara" 6,
                                                                                                                  "kuulamyllyarvo" 6.6,
                                                                                                                  "kuulamyllyarvon-luokka" "KM-arvoluokka",
                                                                                                                  "litteysluku" 6.6,
                                                                                                                  "maksimi-raekoko" "runkoaineen-maksimi-raekoko/rmr01"},
                                                                                     "paallystemassan-sideaine" {"sideaine" "sideaineen-materiaali/sm01"},
                                                                                     "paallystemassan-lisa-aine" {"materiaali" "lisaaineen-materiaali/lm05"}}},
                                         "sitomattomat-pintarakenteet" nil,
                                         "paallysrakenteen-lujitteet" nil,
                                         "paksuus" 6,
                                         "leveys" 6.6,
                                         "syvyys" 6,
                                         "pinta-ala" 6,
                                         "massamaara" 6,
                                         "lisatieto" "Harjan testitoimenpide",
                                         "urakan-ulkoinen-tunniste" "Esim. Sampon ID",
                                         "yllapitokohteen-ulkoinen-tunniste" "666",
                                         "yllapitokohdeosan-ulkoinen-tunniste" "666/1"},
                         "lahdejarjestelman-id" "123",
                         "lahdejarjestelma" "lahdejarjestelma/lj06",
                         "alkaen" "2020-04-21T03:06:29Z",
                         "paattyen" nil}]
    (is (= odotettu-sanoma sanoma) "Tuli joku JSON")))