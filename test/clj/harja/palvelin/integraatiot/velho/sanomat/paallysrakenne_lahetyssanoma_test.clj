(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clojure.data.json :as json]
            [harja.kyselyt.koodistot :refer [konversio]]
            [harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma :as lahetyssanoma]
            [harja.palvelin.integraatiot.yha.kohteen-lahetyssanoma-test :as testi-tiedot]))

(use-fixtures :once tietokantakomponentti-fixture)

(def urakka-esimerkki {:sampoid "SAMPO-ID"})

(def paallystekerros-esimerkki

  {:kohdeosa-id 12, :tr-kaista 12, :lisaaineet "Kuitu: 0.5%",
   :leveys 3M, :kokonaismassamaara 5000M, :max-raekoko 16,
   :tr-ajorata 1, :sideainetyyppi 5, :muotoarvo 6.5M, :kohde-id 27,
   :nimen-tarkenne nil, :esiintyma "Sammalkallio", :pitoisuus 5.5M,
   :tr-loppuosa 1, :jarjestysnro 1, :kuulamyllyarvo 9.2M, :tr-alkuosa 1,
   :kuulamyllyluokka "AN7", :pinta-ala 15000M, :massamenekki 333M,
   :tr-loppuetaisyys 3827, :rc% 5.0M, :paallystetyyppi 14,
   :lisaaine-koodi 1, :pot2-tyomenetelma 23, :alkaen #inst "2021-05-25T10:04:22.174276000-00:00",
   :tr-alkuetaisyys 1066, :piennar false, :tr-numero 20,
   :karttapaivamaara nil, :lisatieto nil, :pot-id 6, :km-arvo 9.2M,
   :runkoaine-koodit "1, 3, 2", :pot2p_id 2})

(def alusta-esimerkki
  {:tr-kaista 11, :murske 1, :tr-ajorata 1, :massamaara 100, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
   :lisatty-paksuus 10, :alkaen #inst "2021-05-25T10:04:22.174276000-00:00", :tr-alkuetaisyys 1066,
   :tr-numero 20, :toimenpide 23, :pot2a_id 1})

(def alusta-verkko-esimerkki
  {:tr-kaista 12, :tr-ajorata 1, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
   :verkon-tyyppi 1, :alkaen #inst "2021-05-25T10:04:22.174276000-00:00", :tr-alkuetaisyys 1066,
   :tr-numero 20, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2})

(deftest muodosta-oikea-paallystekerros
  (let [koodisto-muunnin (partial konversio (:db jarjestelma))
        sidottu-paallysrakenne-tulos (lahetyssanoma/paallystekerroksesta-velho-muottoon paallystekerros-esimerkki
                                                                                        urakka-esimerkki
                                                                                        koodisto-muunnin)
        odotettu-rakenne {:alkusijainti {:osa 1,
                                         :tie 20,
                                         :etaisyys 1066,
                                         :ajorata 1},
                          :loppusijainti {:osa 1,
                                          :tie 20,
                                          :etaisyys 3827,
                                          :ajorata 1},
                          :sijaintitarkenne {:kaista 12},
                          :ominaisuudet {:sidottu-paallysrakenne {:tyyppi "sidotun-paallysrakenteen-tyyppi/spt01",
                                                                  :paallysteen-tyyppi "paallystetyyppi/pt14",
                                                                  :paallystemassa {:asfalttirouheen-osuus-asfalttimassassa 5.0M,
                                                                                   :bitumiprosentti 5.5M,
                                                                                   :paallystemassan-runkoaine {:materiaali ["jakavan-kerroksen-materiaali/NULL_1"
                                                                                                                            "jakavan-kerroksen-materiaali/NULL_3"
                                                                                                                            "jakavan-kerroksen-materiaali/jkm09"],
                                                                                                               :uusiomateriaalin-kayttomaara nil,
                                                                                                               :kuulamyllyarvo 9.2M,
                                                                                                               :kuulamyllyarvon-luokka "AN7",
                                                                                                               :litteysluku 6.5M,
                                                                                                               :maksimi-raekoko "runkoaineen-maksimi-raekoko/rmr04"},
                                                                                   :paallystemassan-sideaine {:sideaine "sideaineen-materiaali/sm05"},
                                                                                   :paallystemassan-lisa-aine {:materiaali "tienrakennetoimenpide/trtp32"}}},
                                         :leveys 3M,
                                         :korjauskohdeosan-ulkoinen-tunniste 12,
                                         :paallysrakenteen-lujitteet nil,
                                         :massamaara 5000M,
                                         :syvyys nil,
                                         :urakan-ulkoinen-tunniste "SAMPO-ID",
                                         :pinta-ala 15000M,
                                         :sitomattomat-pintarakenteet nil,
                                         :korjauskohteen-ulkoinen-tunniste 27,
                                         :lisatieto nil,
                                         :toimenpide "tienrakennetoimenpide/trtp04",
                                         :paksuus nil},
                          :lahdejarjestelman-id 6,
                          :lahdejarjestelma "lahdejarjestelma/lj06",
                          :alkaen #inst "2021-05-25T10:04:22.174-00:00",
                          :paattyen nil}]
    (is (= odotettu-rakenne sidottu-paallysrakenne-tulos))))

(deftest muodosta-oikea-alusta-verkko
  (let [koodisto-muunnin (partial konversio (:db jarjestelma))
        paallysterakenteen-lujitteet (lahetyssanoma/alustasta-velho-muottoon alusta-verkko-esimerkki
                                                                             urakka-esimerkki
                                                                             koodisto-muunnin)
        odotettu-rakenne {:alkusijainti {:osa 1,
                                         :tie 20,
                                         :etaisyys 1066,
                                         :ajorata 1},
                          :loppusijainti {:osa 1,
                                          :tie 20,
                                          :etaisyys 3827,
                                          :ajorata 1},
                          :sijaintirakenne {:kaista 12},
                          :ominaisuudet {:sidottu-paallysrakenne nil,
                                         :leveys nil,
                                         :korjauskohdeosan-ulkoinen-tunniste nil,
                                         :paallysrakenteen-lujitteet {:materiaali "verkon-materiaali/mt01",
                                                                      :toiminnallinen-kayttotarkoitus "verkon-toiminnallinen-kayttotarkoitus/vtk01",
                                                                      :verkon-sijainti "verkon-sijainti/vs01"},
                                         :massamaara nil,
                                         :syvyys nil,
                                         :urakan-ulkoinen-tunniste "SAMPO-ID",
                                         :pinta-ala nil,
                                         :sitomattomat-pintarakenteet nil,
                                         :korjauskohteen-ulkoinen-tunniste nil,
                                         :lisatieto nil,
                                         :toimenpide "tienrakennetoimenpide/trtp01",
                                         :paksuus nil},
                          :lahdejarjestelman-id nil,
                          :lahdejarjestelma "lahdejarjestelma/lj06",
                          :alkaen #inst "2021-05-25T10:04:22.174-00:00",
                          :paattyen nil}]
    (is (= odotettu-rakenne paallysterakenteen-lujitteet))))

(deftest muodosta-oikea-alusta
  (let [koodisto-muunnin (partial konversio (:db jarjestelma))
        paallysterakenteen-lujitteet (lahetyssanoma/alustasta-velho-muottoon alusta-esimerkki
                                                                             urakka-esimerkki
                                                                             koodisto-muunnin)
        odotettu-rakenne {:alkusijainti {:osa 1,
                                         :tie 20,
                                         :etaisyys 1066,
                                         :ajorata 1},
                          :loppusijainti {:osa 1,
                                          :tie 20,
                                          :etaisyys 3827,
                                          :ajorata 1},
                          :sijaintirakenne {:kaista 11},
                          :ominaisuudet {:sidottu-paallysrakenne nil,
                                         :leveys nil,
                                         :korjauskohdeosan-ulkoinen-tunniste nil,
                                         :paallysrakenteen-lujitteet nil,
                                         :massamaara 100,
                                         :syvyys nil,
                                         :urakan-ulkoinen-tunniste "SAMPO-ID",
                                         :pinta-ala nil,
                                         :sitomattomat-pintarakenteet nil,
                                         :korjauskohteen-ulkoinen-tunniste nil,
                                         :lisatieto nil,
                                         :toimenpide "tienrakennetoimenpide/trtp39",
                                         :paksuus 10},
                          :lahdejarjestelman-id nil,
                          :lahdejarjestelma "lahdejarjestelma/lj06",
                          :alkaen #inst "2021-05-25T10:04:22.174-00:00",
                          :paattyen nil}]
    (is (= odotettu-rakenne paallysterakenteen-lujitteet))))
