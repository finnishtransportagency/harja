(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clojure.data.json :as json]
            [harja.kyselyt.koodistot :refer [konversio]]
            [harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma :as lahetyssanoma]
            [harja.palvelin.integraatiot.yha.kohteen-lahetyssanoma-test :as testi-tiedot]))

(use-fixtures :once tietokantakomponentti-fixture)

(def paallystekerros-esimerkki
  {:kohdeosa-id 12, :tr-kaista 12, :lisaaineet "Kuitu: 0.5%",
   :leveys 3M, :kokonaismassamaara 5000M, :max-raekoko 16,
   :tr-ajorata 1, :sideainetyyppi 5, :muotoarvo 6.5M,
   :pinta-ala 15000M, :nimen-tarkenne nil, :esiintyma "Sammalkallio",
   :litteysluku 6.5M, :pitoisuus 5.5M, :tr-loppuosa 1, :jarjestysnro 1,
   :kuulamyllyarvo 9.2M, :tr-alkuosa 1, :massamenekki 333M, :tr-loppuetaisyys 3827,
   :rc% 5.0M, :paallystetyyppi 2, :massatyyppi 14,
   :lisaaine-koodi 1, :tr-alkuetaisyys 1066, :piennar false,
   :tr-numero 20, :karttapaivamaara nil, :lisatieto nil,
   :toimenpide 23, :km-arvo 9.2M,
   :runkoaine-koodit "1, 3, 2", :pot2p_id 2})

(def alusta-esimerkki
  {:tr-kaista 11, :murske 1, :tr-ajorata 1, :massamaara 100, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
   :lisatty-paksuus 10, :tr-alkuetaisyys 1066, :tr-numero 20, :toimenpide 23, :pot2a_id 1})

(def alusta-verkko-esimerkki
  {:tr-kaista 12, :tr-ajorata 1, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
   :verkon-tyyppi 1, :tr-alkuetaisyys 1066, :tr-numero 20, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2})

(deftest muodosta-oikea-paallystekerros
  (let [koodisto-muunnin (partial konversio (:db jarjestelma))
        sidottu-paallysrakenne-tulos (lahetyssanoma/paallystekerroksesta-velho-muottoon paallystekerros-esimerkki koodisto-muunnin)
        odotettu-rakenne {:alkusijainti {:osa 1, :tie 20, :etaisyys 1066, :ajorata 1},
                          :loppusijainti {:osa 1, :tie 20, :etaisyys 3827, :ajorata 1},
                          :sijaintirakenne {:kaista 12},
                          :ominaisuudet {:sidottu-paallysrakenne {:tyyppi "sidotun-paallysrakenteen-tyyppi/spt01",
                                                                  :paallysteen-tyyppi "paallystetyyppi/pt14",
                                                                  :paallystemassa {:asfalttirouheen-osuus-asfalttimassassa 5.0M,
                                                                                   :bitumiprosentti 6.6,
                                                                                   :paallystemassan-runkoaine {:materiaali ["jakavan-kerroksen-materiaali/NULL_1"
                                                                                                                            "jakavan-kerroksen-materiaali/NULL_3"
                                                                                                                            "jakavan-kerroksen-materiaali/jkm09"],
                                                                                                               :uusiomateriaalin-kayttomaara 6,
                                                                                                               :kuulamyllyarvo 9.2M,
                                                                                                               :kuulamyllyarvon-luokka "KM-arvoluokka",
                                                                                                               :litteysluku 6.5M,
                                                                                                               :maksimi-raekoko "runkoaineen-maksimi-raekoko/rmr04"},
                                                                                   :paallystemassan-sideaine {:sideaine "sideaineen-materiaali/sm05"},
                                                                                   :paallystemassan-lisa-aine {:materiaali "tienrakennetoimenpide/trtp32"}}},
                                         :leveys 3M,
                                         :paallysrakenteen-lujitteet nil,
                                         :yllapitokohdeosan-ulkoinen-tunniste "666/1",
                                         :massamaara 5000M,
                                         :syvyys 1,
                                         :urakan-ulkoinen-tunniste "Esim. Sampon ID",
                                         :pinta-ala 15000M,
                                         :yllapitokohteen-ulkoinen-tunniste "666",
                                         :sitomattomat-pintarakenteet nil,
                                         :lisatieto nil,
                                         :toimenpide "tienrakennetoimenpide/trtp04",
                                         :paksuus 1},
                          :lahdejarjestelman-id "what here?",
                          :lahdejarjestelma "lahdejarjestelma/lj06",
                          :alkaen nil,
                          :paatyen nil}]
    (is (= sidottu-paallysrakenne-tulos odotettu-rakenne))))

(deftest muodosta-oikea-alusta-verkko
  (let [koodisto-muunnin (partial konversio (:db jarjestelma))
        paallysterakenteen-lujitteet (lahetyssanoma/alustasta-velho-muottoon alusta-verkko-esimerkki koodisto-muunnin)
        odotettu-rakenne {:alkusijainti {:osa 1, :tie 20, :etaisyys 1066, :ajorata 1},
                          :loppusijainti {:osa 1, :tie 20, :etaisyys 3827, :ajorata 1},
                          :sijaintirakenne {:kaista 12},
                          :ominaisuudet {:sidottu-paallysrakenne nil,
                                         :leveys nil,
                                         :paallysrakenteen-lujitteet {:materiaali "verkon-materiaali/mt01",
                                                                      :toiminnallinen-kayttotarkoitus "verkon-toiminnallinen-kayttotarkoitus/vtk01",
                                                                      :verkon-sijainti "verkon-sijainti/vs01"},
                                         :yllapitokohdeosan-ulkoinen-tunniste "666/1",
                                         :massamaara nil,
                                         :syvyys nil,
                                         :urakan-ulkoinen-tunniste "Esim. Sampon ID",
                                         :pinta-ala nil,
                                         :yllapitokohteen-ulkoinen-tunniste "666",
                                         :sitomattomat-pintarakenteet nil,
                                         :lisatieto nil,
                                         :toimenpide "tienrakennetoimenpide/trtp01",
                                         :paksuus nil},
                          :lahdejarjestelman-id "what here?",
                          :lahdejarjestelma "lahdejarjestelma/lj06",
                          :alkaen nil,
                          :paatyen nil}]
    (is (= paallysterakenteen-lujitteet odotettu-rakenne))))

(deftest muodosta-oikea-alusta
  (let [koodisto-muunnin (partial konversio (:db jarjestelma))
        paallysterakenteen-lujitteet (lahetyssanoma/alustasta-velho-muottoon alusta-esimerkki koodisto-muunnin)
        odotettu-rakenne {:alkusijainti {:osa 1, :tie 20, :etaisyys 1066, :ajorata 1},
                          :loppusijainti {:osa 1, :tie 20, :etaisyys 3827, :ajorata 1},
                          :sijaintirakenne {:kaista 11},
                          :ominaisuudet {:sidottu-paallysrakenne nil,
                                         :leveys nil,
                                         :paallysrakenteen-lujitteet nil,
                                         :yllapitokohdeosan-ulkoinen-tunniste "666/1",
                                         :massamaara 100,
                                         :syvyys nil,
                                         :urakan-ulkoinen-tunniste "Esim. Sampon ID",
                                         :pinta-ala nil,
                                         :yllapitokohteen-ulkoinen-tunniste "666",
                                         :sitomattomat-pintarakenteet nil,
                                         :lisatieto nil,
                                         :toimenpide "tienrakennetoimenpide/trtp39",
                                         :paksuus 10},
                          :lahdejarjestelman-id "what here?",
                          :lahdejarjestelma "lahdejarjestelma/lj06",
                          :alkaen nil,
                          :paatyen nil}]
    (is (= paallysterakenteen-lujitteet odotettu-rakenne))))
