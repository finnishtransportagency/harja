(ns harja.palvelin.integraatiot.api.sanomat.paikkaussanoma-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.sanomat.paikkaussanoma :as paikkaustoteumasanoma]))

(deftest paikkaustoteuma-api->domain
  (let [paikkaustoteuma {:leveys 10
                         :sijainti {:tie 20
                                    :aosa 1
                                    :aet 1
                                    :losa 5
                                    :let 16
                                    :ajoradat [{:ajorata 1
                                                :tienkohdat {:reunat [{:reuna 1}]
                                                             :ajourat [{:ajoura 2} {:ajoura 3}]
                                                             :ajouravalit [{:ajouravali 5}
                                                                           {:ajouravali 7}]
                                                             :keskisaumat [{:keskisauma 1}
                                                                           {:keskisauma 1}]}}]}
                         :loppuaika "2018-01-30T18:00:00Z"
                         :paikkauskohde {:nimi "Kuusamontien paikkaus" :tunniste {:id 567}}
                         :alkuaika "2018-01-30T12:00:00Z"
                         :kivi-ja-sideaineet [{:kivi-ja-sideaine {:esiintyma "testi"
                                                                  :km-arvo "testi"
                                                                  :muotoarvo "testi"
                                                                  :sideainetyyppi "20/30"
                                                                  :pitoisuus 1.2M
                                                                  :lisa-aineet "lisäaineet"}}]
                         :massamenekki 12
                         :kuulamylly "AN5"
                         :raekoko 1
                         :tyomenetelma "massapintaus"
                         :massatyyppi "AB, Asfalttibetoni"
                         :tunniste {:id 123}}
        odotettu {:harja.domain.paikkaus/alkuaika #inst "2018-01-30T12:00:00.000-00:00"
                  :harja.domain.paikkaus/kuulamylly "AN5"
                  :harja.domain.paikkaus/leveys 10M
                  :harja.domain.paikkaus/loppuaika #inst "2018-01-30T18:00:00.000-00:00"
                  :harja.domain.paikkaus/massamenekki 12
                  :harja.domain.paikkaus/massatyyppi "AB, Asfalttibetoni"
                  :harja.domain.paikkaus/materiaalit [{:harja.domain.paikkaus/esiintyma "testi"
                                                       :harja.domain.paikkaus/kuulamylly-arvo "testi"
                                                       :harja.domain.paikkaus/lisa-aineet "lisäaineet"
                                                       :harja.domain.paikkaus/muotoarvo "testi"
                                                       :harja.domain.paikkaus/pitoisuus 1.2M
                                                       :harja.domain.paikkaus/sideainetyyppi "20/30"}]
                  :harja.domain.paikkaus/paikkauskohde {:harja.domain.paikkaus/nimi "Kuusamontien paikkaus"
                                                        :harja.domain.paikkaus/ulkoinen-id 567}
                  :harja.domain.paikkaus/raekoko 1
                  :harja.domain.paikkaus/tienkohdat [{:harja.domain.paikkaus/ajorata 1
                                                      :harja.domain.paikkaus/ajourat [2
                                                                                      3]
                                                      :harja.domain.paikkaus/ajouravalit [5
                                                                                          7]
                                                      :harja.domain.paikkaus/keskisaumat [1
                                                                                          1]
                                                      :harja.domain.paikkaus/reunat [1]}]
                  :harja.domain.paikkaus/tierekisteriosoite {:harja.domain.tierekisteri/aet 1
                                                             :harja.domain.tierekisteri/aosa 1
                                                             :harja.domain.tierekisteri/let 16
                                                             :harja.domain.tierekisteri/losa 5
                                                             :harja.domain.tierekisteri/tie 20}
                  :harja.domain.paikkaus/tyomenetelma "massapintaus"
                  :harja.domain.paikkaus/ulkoinen-id 123
                  :harja.domain.paikkaus/urakka-id 666}]

    (is (= odotettu (paikkaustoteumasanoma/api->domain 666 paikkaustoteuma)))))