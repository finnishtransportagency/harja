(ns harja.palvelin.integraatiot.api.sanomat.paallystysilmoitus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.sanomat.paallystysilmoitus :as paallystysilmoitus]
            [cheshire.core :as cheshire]))

(deftest tarkista-paallystysiloituksen-rakentaminen
  (let [paallystysilmoitus {:yllapitokohde
                            {:sijainti {:aosa 1, :aet 1, :losa 5, :let 16},
                             :alikohteet
                             [{:leveys 1.2,
                               :kokonaismassamaara 12.3,
                               :sijainti {:aosa 1, :aet 1, :losa 5, :let 16},
                               :kivi-ja-sideaineet
                               [{:kivi-ja-sideaine
                                 {:esiintyma "testi",
                                  :km-arvo "testi",
                                  :muotoarvo "testi",
                                  :sideainetyyppi "1",
                                  :pitoisuus 1.2,
                                  :lisa-aineet "lisäaineet"}}],
                               :tunnus "A",
                               :pinta-ala 2.2,
                               :massamenekki 22,
                               :kuulamylly "N14",
                               :nimi "1. testialikohde",
                               :raekoko 12,
                               :tyomenetelma "Uraremix",
                               :rc-prosentti 54,
                               :paallystetyyppi "avoin asfaltti",
                               :id 36}]},
                            :alustatoimenpiteet
                            [{:sijainti {:aosa 1, :aet 1, :losa 5, :let 15},
                              :kasittelymenetelma "Massanvaihto",
                              :paksuus 1.2,
                              :verkkotyyppi "Teräsverkko",
                              :verkon-tarkoitus "Tasaukset",
                              :verkon-sijainti "Päällysteessä",
                              :tekninen-toimenpide "Rakentaminen"}],
                            :tyot
                            [{:tyyppi "tasaukset",
                              :tyotehtava "työtehtävä",
                              :tilattu-maara 1.2,
                              :toteutunut-maara 1.2,
                              :yksikko "kpl",
                              :yksikkohinta 55.4}],
                            :alikohteet
                            [{:leveys 1.2,
                              :kokonaismassamaara 12.3,
                              :sijainti {:aosa 1, :aet 1, :losa 5, :let 16},
                              :kivi-ja-sideaineet
                              [{:kivi-ja-sideaine
                                {:esiintyma "testi",
                                 :km-arvo "testi",
                                 :muotoarvo "testi",
                                 :sideainetyyppi "1",
                                 :pitoisuus 1.2,
                                 :lisa-aineet "lisäaineet"}}],
                              :tunnus "A",
                              :pinta-ala 2.2,
                              :massamenekki 22,
                              :kuulamylly "N14",
                              :nimi "1. testialikohde",
                              :raekoko 12,
                              :tyomenetelma "Uraremix",
                              :rc-prosentti 54,
                              :paallystetyyppi "avoin asfaltti"}]}
        ilmoitusdata (clojure.walk/keywordize-keys (cheshire/decode (paallystysilmoitus/rakenna paallystysilmoitus)))
        odotettu-data {:osoitteet [{:kohdeosa-id 36,
                                    :edellinen-paallystetyyppi nil,
                                    :lisaaineet "lisäaineet",
                                    :leveys 1.2,
                                    :kokonaismassamaara 12.3,
                                    :sideainetyyppi "1",
                                    :muotoarvo "testi",
                                    :esiintyma "testi",
                                    :pitoisuus 1.2,
                                    :pinta-ala 2.2,
                                    :massamenekki 22,
                                    :kuulamylly nil,
                                    :raekoko 12,
                                    :tyomenetelma "Uraremix",
                                    :rc% 54,
                                    :paallystetyyppi "avoin asfaltti",
                                    :km-arvo "testi"}],
                       :alustatoimet [{:verkkotyyppi "Teräsverkko",
                                       :aosa 1,
                                       :let 15,
                                       :verkon-tarkoitus "Tasaukset",
                                       :kasittelymenetelma "Massanvaihto",
                                       :losa 5,
                                       :aet 1,
                                       :tekninen-toimenpide "Rakentaminen",
                                       :paksuus 1.2,
                                       :verkon-sijainti "Päällysteessä"}],
                       :tyot [{:tilattu-maara 1.2
                               :toteutunut-maara 1.2
                               :tyo "työtehtävä"
                               :tyyppi "tasaukset"
                               :yksikko "kpl"
                               :yksikkohinta 55.4}]}]
    (is (= odotettu-data ilmoitusdata))))
