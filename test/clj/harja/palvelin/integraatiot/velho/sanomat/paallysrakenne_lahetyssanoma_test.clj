(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clojure.data.json :as json]
            [harja.kyselyt.koodistot :refer [konversio]]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset] 
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
   :tr-numero 20, :toimenpide 23, :pot2a_id 1, :paallystyskohde 7, :pot-id 6,
   :murske-tyyppi 1, :rakeisuus "0/40", :iskunkestavyys "LA30"})

(def alusta-verkko-esimerkki
  {:tr-kaista 12, :tr-ajorata 1, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
   :verkon-tyyppi 1, :alkaen #inst "2021-05-25T10:04:22.174276000-00:00", :tr-alkuetaisyys 1066,
   :tr-numero 20, :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2, :paallystyskohde 7, :pot-id 6,
   :murske-tyyppi 1, :rakeisuus "0/40", :iskunkestavyys "LA30"})

(def alusta-abk-esimerkki
  {:tr-kaista 12, :leveys nil, :kokonaismassamaara 23, :murske nil, :massa 1, :velho-lahetyksen-aika nil,
   :tr-ajorata 1, :paallystyskohde 27, :massamaara 34, :verkon-tarkoitus nil, :tr-loppuosa 1,
   :velho-lahetyksen-vastaus nil, :tr-alkuosa 1, :pinta-ala 12, :kasittelysyvyys nil, :tr-loppuetaisyys 2050,
   :murske-tyyppi nil, :iskunkestavyys nil, :muokkaus-grid-id 3, :lisatty-paksuus nil, :verkon-tyyppi nil,
   :sideaine2 nil, :sideainepitoisuus nil, :max-raekoko 16, :sideainetyyppi 5, :lisaaine-koodi 1,
   :alkaen #inst "2021-10-25T11:10:09.000-00:00", :tr-alkuetaisyys 2000, :tr-numero 20,
   :rakeisuus nil, :sideaine nil, :toimenpide 21, :pot-id 6, :verkon-sijainti nil, :pot2a_id 3})

(deftest muodosta-oikea-paallystekerros
  (let [koodisto-muunnin (partial konversio (:db jarjestelma) velho-yhteiset/lokita-hakuvirhe)
        sidottu-paallysrakenne-tulos (lahetyssanoma/paallystekerros->velho-muoto paallystekerros-esimerkki
                                                                                 urakka-esimerkki
                                                                                 koodisto-muunnin)
        odotettu-rakenne {:alkusijainti {:tie 20, :osa 1, :etaisyys 1066},
                          :loppusijainti {:tie 20, :osa 1, :etaisyys 3827},
                          :ominaisuudet {:sidottu-paallysrakenne {:tyyppi ["sidotun-paallysrakenteen-tyyppi/spt01"],
                                                                  :paallysteen-tyyppi "paallystetyyppi/pt14",
                                                                  :paallystemassa {:paallystemassan-runkoaine {:materiaali ["materiaali/m26"
                                                                                                                            "materiaali/m26"
                                                                                                                            "materiaali/m01"],
                                                                                                               :kuulamyllyarvo 9.2M,
                                                                                                               :litteysluku 6.5M,
                                                                                                               :maksimi-raekoko "runkoaineen-maksimi-raekoko/rmr04"},
                                                                                   :paallystemassan-sideaine {:sideaine "sideaineen-materiaali/sm05",
                                                                                                              :sideainepitoisuus 6},
                                                                                   :paallystemassan-lisa-aine {:materiaali "lisaaineen-materiaali/lm01"}}},
                                         :leveys 3M,
                                         :korjauskohdeosan-ulkoinen-tunniste "12",
                                         :massamaara 333,
                                         :vaikutukset nil,
                                         :syvyys nil,
                                         :urakan-ulkoinen-tunniste "SAMPO-ID",
                                         :pinta-ala 15000M,
                                         :materiaali nil,
                                         :korjauskohteen-ulkoinen-tunniste "27",
                                         :kiviaineksen-maksimi-raekoko nil,
                                         :lisatieto nil,
                                         :toimenpide "tienrakennetoimenpide/trtp04",
                                         :paksuus nil,
                                         :toimenpiteen-kohdeluokka ["paallyste-ja-pintarakenne/sidotut-paallysrakenteet"],
                                         :paikkaustoimenpide nil},
                          :lahdejarjestelman-id "6",
                          :paattyen nil,
                          :lahdejarjestelma "lahdejarjestelma/lj06",
                          :schemaversio 1,
                          :sijaintitarkenne {:ajoradat ["ajorata/ajr1"], :kaistat ["kaista-numerointi/kanu12"]},
                          :alkaen "2021-05-25"}]
    (is (= odotettu-rakenne sidottu-paallysrakenne-tulos))))

(deftest muodosta-oikea-alusta-verkko
  (let [koodisto-muunnin (partial konversio (:db jarjestelma) velho-yhteiset/lokita-hakuvirhe)
        paallysterakenteen-lujitteet (lahetyssanoma/alusta->velho-muoto alusta-verkko-esimerkki
                                                                        urakka-esimerkki
                                                                        koodisto-muunnin)
        odotettu-rakenne {:alkusijainti {:tie 20, :osa 1, :etaisyys 1066},
                          :loppusijainti {:tie 20, :osa 1, :etaisyys 3827},
                          :sijaintitarkenne {:ajoradat ["ajorata/ajr1"], :kaistat ["kaista-numerointi/kanu12"]},
                          :ominaisuudet {:leveys nil,
                                         :korjauskohdeosan-ulkoinen-tunniste "2",
                                         :massamaara nil,
                                         :vaikutukset nil,
                                         :syvyys nil,
                                         :urakan-ulkoinen-tunniste "SAMPO-ID",
                                         :pinta-ala nil,
                                         :materiaali nil,
                                         :korjauskohteen-ulkoinen-tunniste "7",
                                         :kiviaineksen-maksimi-raekoko nil,
                                         :kantava-kerros {:materiaali "kantavan-kerroksen-materiaali/kkm03",
                                                          :rakeisuus "kantavan-kerroksen-rakeisuus/skkr02",
                                                          :iskunkestavyys "kantavan-kerroksen-iskunkestavyys/skki01"},
                                         :lisatieto nil,
                                         :toimenpide "tienrakennetoimenpide/trtp01",
                                         :paksuus nil,
                                         :toimenpiteen-kohdeluokka ["paallyste-ja-pintarakenne/sidotut-paallysrakenteet"],
                                         :paallysrakenteen-lujite {:verkko {:materiaali "verkon-materiaali/mt01",
                                                                            :toiminnallinen-kayttotarkoitus "verkon-toiminnallinen-kayttotarkoitus/vtk01",
                                                                            :verkon-sijainti "verkon-sijainti/vs01"}},
                                         :paikkaustoimenpide nil},
                          :lahdejarjestelman-id "6",
                          :paattyen nil,
                          :lahdejarjestelma "lahdejarjestelma/lj06",
                          :schemaversio 1,
                          :alkaen "2021-05-25"}]
    (is (= odotettu-rakenne paallysterakenteen-lujitteet))))

(deftest muodosta-oikea-alusta
  (let [koodisto-muunnin (partial konversio (:db jarjestelma) velho-yhteiset/lokita-hakuvirhe)
        paallysterakenteen-lujitteet (lahetyssanoma/alusta->velho-muoto alusta-esimerkki
                                                                        urakka-esimerkki
                                                                        koodisto-muunnin)
        odotettu-uusi-rakenne {:alkusijainti {:tie 20, :osa 1, :etaisyys 1066},
                               :loppusijainti {:tie 20, :osa 1, :etaisyys 3827},
                               :sijaintitarkenne {:ajoradat ["ajorata/ajr1"],
                                                  :kaistat ["kaista-numerointi/kanu11"]},
                               :ominaisuudet {:leveys nil,
                                              :korjauskohdeosan-ulkoinen-tunniste "1",
                                              :massamaara 100,
                                              :vaikutukset nil,
                                              :syvyys nil,
                                              :urakan-ulkoinen-tunniste "SAMPO-ID",
                                              :pinta-ala nil,
                                              :materiaali nil,
                                              :korjauskohteen-ulkoinen-tunniste "7",
                                              :kiviaineksen-maksimi-raekoko nil,
                                              :lisatieto nil,
                                              :toimenpide "tienrakennetoimenpide/trtp39",
                                              :paksuus 10,
                                              :kantava-kerros {:materiaali "kantavan-kerroksen-materiaali/kkm03",
                                                               :rakeisuus "kantavan-kerroksen-rakeisuus/skkr02",
                                                               :iskunkestavyys "kantavan-kerroksen-iskunkestavyys/skki01"},
                                              :toimenpiteen-kohdeluokka ["paallysrakennekerrokset/kantavat-kerrokset"],
                                              :paikkaustoimenpide nil},
                               :lahdejarjestelman-id "6",
                               :paattyen nil,
                               :lahdejarjestelma "lahdejarjestelma/lj06",
                               :schemaversio 1,
                               :alkaen "2021-05-25"}]
    (is (= odotettu-uusi-rakenne paallysterakenteen-lujitteet))))

(deftest muodosta-oikea-alusta-abk
  (let [koodisto-muunnin (partial konversio (:db jarjestelma) velho-yhteiset/lokita-hakuvirhe)
        paallysterakenteen-lujitteet (lahetyssanoma/alusta->velho-muoto alusta-abk-esimerkki
                                                                        urakka-esimerkki
                                                                        koodisto-muunnin)
        odotettu-rakenne {:alkusijainti {:osa 1, :tie 20, :etaisyys 2000},
                          :loppusijainti {:osa 1, :tie 20, :etaisyys 2050},
                          :ominaisuudet {:sidottu-paallysrakenne {:tyyppi ["sidotun-paallysrakenteen-tyyppi/spt02"],
                                                                  :paallysteen-tyyppi "paallystetyyppi/pt05",
                                                                  :paallystemassa {:paallystemassan-runkoaine {:materiaali nil,
                                                                                                               :kuulamyllyarvo nil,
                                                                                                               :litteysluku nil,
                                                                                                               :maksimi-raekoko "runkoaineen-maksimi-raekoko/rmr04"},
                                                                                   :paallystemassan-sideaine {:sideaine "sideaineen-materiaali/sm05"},
                                                                                   :paallystemassan-lisa-aine {:materiaali "lisaaineen-materiaali/lm01"}}},
                                         :leveys nil,
                                         :korjauskohdeosan-ulkoinen-tunniste "3",
                                         :massamaara 34,
                                         :vaikutukset nil,
                                         :syvyys nil,
                                         :urakan-ulkoinen-tunniste "SAMPO-ID",
                                         :pinta-ala 12,
                                         :materiaali nil,
                                         :korjauskohteen-ulkoinen-tunniste "27",
                                         :kiviaineksen-maksimi-raekoko nil,
                                         :lisatieto nil,
                                         :toimenpide "tienrakennetoimenpide/trtp01",
                                         :paksuus nil,
                                         :toimenpiteen-kohdeluokka ["paallyste-ja-pintarakenne/sidotut-paallysrakenteet"],
                                         :paikkaustoimenpide nil},
                          :lahdejarjestelman-id "6",
                          :paattyen nil,
                          :lahdejarjestelma "lahdejarjestelma/lj06",
                          :schemaversio 1,
                          :sijaintitarkenne {:ajoradat ["ajorata/ajr1"],
                                             :kaistat ["kaista-numerointi/kanu12"]},
                          :alkaen "2021-10-25"}]
    (is (= odotettu-rakenne paallysterakenteen-lujitteet))))

