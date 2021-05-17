(ns harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clojure.data.json :as json]
            [harja.kyselyt.koodistot :refer [konversio]]
            [harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma :as lahetyssanoma]
            [harja.palvelin.integraatiot.yha.kohteen-lahetyssanoma-test :as testi-tiedot]))

(use-fixtures :once tietokantakomponentti-fixture)

(def paallystysilmoitus-esimerkki
  {:maaramuutokset-ennustettu? false, :tila :aloitettu, :tr-kaista nil, :kohdenimi "Tärkeä kohde mt20", :kohdenumero "L42",
   :tr-ajorata nil, :kokonaishinta-ilman-maaramuutoksia 0, :urakka-id 7, :maaramuutokset 0, :kommentit [],
   :paallystekerros [{:kohdeosa-id 11, :tr-kaista 11, :leveys 3M, :kokonaismassamaara 5000M, :tr-ajorata 1,
                      :pinta_ala 15000M, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1,
                      :massamenekki 333M, :tr-loppuetaisyys 3827, :nimi "Tärkeä kohdeosa kaista 11",
                      :materiaali 1, :tr-alkuetaisyys 1066, :piennar true, :tr-numero 20, :toimenpide 22, :pot2p_id 1}
                     {:kohdeosa-id 12, :tr-kaista 12, :leveys 3M, :kokonaismassamaara 5000M, :tr-ajorata 1,
                      :pinta_ala 15000M, :tr-loppuosa 1, :jarjestysnro 1, :tr-alkuosa 1,
                      :massamenekki 333M, :tr-loppuetaisyys 3827, :nimi "Tärkeä kohdeosa kaista 12",
                      :materiaali 2, :tr-alkuetaisyys 1066, :piennar false, :tr-numero 20, :toimenpide 23, :pot2p_id 2}],
   :sakot-ja-bonukset nil,
   :tr-loppuosa 1,
   :yha-tr-osoite {:numero 20, :alkuosa 1, :alkuetaisyys 1066, :loppuosa 1, :loppuetaisyys 3827},
   :alusta [{:tr-kaista 11, :leveys nil, :kokonaismassamaara nil, :murske 1, :massa nil, :tr-ajorata 1,
             :massamaara 100, :verkon-tarkoitus nil, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil,
             :kasittelysyvyys nil, :tr-loppuetaisyys 3827, :lisatty-paksuus 10, :verkon-tyyppi nil,
             :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil,
             :toimenpide 23, :verkon-sijainti nil, :pot2a_id 1}
            {:tr-kaista 12, :leveys nil, :kokonaismassamaara nil, :murske nil, :massa nil, :tr-ajorata 1,
             :massamaara nil, :verkon-tarkoitus 1, :tr-loppuosa 1, :tr-alkuosa 1, :pinta-ala nil,
             :kasittelysyvyys nil, :tr-loppuetaisyys 3827, :lisatty-paksuus nil, :verkon-tyyppi 1,
             :sideaine2 nil, :sideainepitoisuus nil, :tr-alkuetaisyys 1066, :tr-numero 20, :sideaine nil,
             :toimenpide 3, :verkon-sijainti 1, :pot2a_id 2}],
   :yllapitokohdetyyppi "paallyste", :valmispvm-kohde #inst "2021-06-23T21:00:00.000-00:00",
   :tunnus nil, :tr-alkuosa 1, :sopimuksen-mukaiset-tyot nil, :tr-loppuetaisyys 3827,
   :kaasuindeksi nil, :aloituspvm #inst "2021-06-18T21:00:00.000-00:00",
   :paallystyskohde-id 27,
   :lisatiedot "Jouduttiin tekemään alustatöitä hieman suunniteltua enemmän joten meni pari päivää pitkäksi.",
   :bitumi-indeksi nil,
   :id 6, :takuupvm #inst "2024-12-30T22:00:00.000-00:00",
   :ilmoitustiedot {:osoitteet [{:kohdeosa-id 11, :tr-kaista 11, :tr-ajorata 1, :massamaara nil, :tr-loppuosa 1,
                                 :tr-alkuosa 1, :tr-loppuetaisyys 3827, :nimi "Tärkeä kohdeosa kaista 11", :raekoko nil,
                                 :tyomenetelma nil, :paallystetyyppi nil, :tr-alkuetaisyys 1066,
                                 :tr-numero 20, :toimenpide nil}
                                {:kohdeosa-id 12, :tr-kaista 12, :tr-ajorata 1, :massamaara nil, :tr-loppuosa 1,
                                 :tr-alkuosa 1, :tr-loppuetaisyys 3827, :nimi "Tärkeä kohdeosa kaista 12", :raekoko nil,
                                 :tyomenetelma nil, :paallystetyyppi nil, :tr-alkuetaisyys 1066,
                                 :tr-numero 20, :toimenpide nil}]},
   :versio 2,
   :asiatarkastus {:lisatiedot nil, :hyvaksytty nil, :tarkastusaika nil, :tarkastaja nil},
   :yllapitokohde-id 27,
   :tr-alkuetaisyys 1066, :vuodet [2021],
   :tr-numero 20, :arvonvahennykset nil,
   :tekninen-osa {:paatos nil, :kasittelyaika nil, :perustelu nil},
   :valmispvm-paallystys #inst "2021-06-20T21:00:00.000-00:00"})


(deftest muodostaa-yksinkertaisen-sanoman
  (let [koodisto-muunnin (partial konversio (:db jarjestelma))
        monimutkainen-sanoma-petar (lahetyssanoma/muodosta testi-tiedot/testiurakka paallystysilmoitus-esimerkki koodisto-muunnin)
        petar-single-paallystekerros (get-in monimutkainen-sanoma-petar [:paallystekerros 0])
        _ (println "petar single " petar-single-paallystekerros)
        sanoma (json/read-str (get-in monimutkainen-sanoma-petar [:paallystekerros 0]))
        _ (println "petar " (pr-str sanoma))
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