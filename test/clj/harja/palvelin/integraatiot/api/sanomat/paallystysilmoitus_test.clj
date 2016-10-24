(ns harja.palvelin.integraatiot.api.sanomat.paallystysilmoitus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.sanomat.paallystysilmoitus :as paallystysilmoitus]
            [cheshire.core :as cheshire]
            [harja.domain.skeema :as skeema]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]))

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
                                  :sideainetyyppi "20/30",
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
                               :edellinen-paallystetyyppi "pehmeä asfalttibetoni (v)",
                               :id 36}]},
                            :alustatoimenpiteet
                            [{:sijainti {:aosa 1, :aet 1, :losa 5, :let 15},
                              :kasittelymenetelma "Massanvaihto",
                              :paksuus 1.2,
                              :verkkotyyppi "Teräsverkko",
                              :verkon-tarkoitus "Muiden routavaurioiden ehkäisy",
                              :verkon-sijainti "Päällysteessä",
                              :tekninen-toimenpide "Rakentaminen"}],
                            :tyot
                            [{:tyyppi "Jyrsinnät",
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
                                 :sideainetyyppi "20/30",
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
                                    :edellinen-paallystetyyppi 22,
                                    :lisaaineet "lisäaineet",
                                    :leveys 1.2,
                                    :kokonaismassamaara 12.3,
                                    :sideainetyyppi 1,
                                    :muotoarvo "testi",
                                    :esiintyma "testi",
                                    :pitoisuus 1.2,
                                    :pinta-ala 2.2,
                                    :massamenekki 22,
                                    :kuulamylly 4,
                                    :raekoko 12,
                                    :tyomenetelma 72,
                                    :rc% 54,
                                    :paallystetyyppi 11,
                                    :km-arvo "testi"}],
                       :alustatoimet [{:kasittelymenetelma 1
                                       :paksuus 1.2
                                       :tekninen-toimenpide 1
                                       :tr-alkuetaisyys 1
                                       :tr-alkuosa 1
                                       :tr-loppuetaisyys 15
                                       :tr-loppuosa 5
                                       :verkkotyyppi 1
                                       :verkon-sijainti 1
                                       :verkon-tarkoitus 2}],
                       :tyot [{:tilattu-maara 1.2
                               :toteutunut-maara 1.2
                               :tyo "työtehtävä"
                               :tyyppi "jyrsinnat"
                               :yksikko "kpl"
                               :yksikkohinta 55.4}]}]
    (is (= odotettu-data ilmoitusdata))
    (is (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus+ ilmoitusdata))))
