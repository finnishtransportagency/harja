(ns harja.tiedot.tilannekuva.tilannekuva-test
  (:require [harja.tiedot.tilannekuva.tilannekuva :as tk]
            [harja.domain.tilannekuva :as tkd]
            [clojure.test :refer-macros [deftest is]]))

(deftest hyt-joiden-urakoilla-ei-arvoa
  (let [alueet {:hoito {:lappi {{:nimi :kuusamo} true
                                {:nimi :rovaniemi} false}
                        :uusimaa {{:nimi :vantaa} true
                                  {:nimi :helsinki} true}
                        :pirkanmaa {{:nimi :tampere} false
                                    {:nimi :nokia} false}}
                :paallystys {:lappi {{:nimi :kuusamo} true
                                     {:nimi :rovaniemi} false}
                             :uusimaa {{:nimi :vantaa} true
                                       {:nimi :helsinki} true}
                             :pirkanmaa {{:nimi :tampere} false
                                         {:nimi :nokia} false}}}]
    (is (= {:hoito #{:pirkanmaa} :paallystys #{:pirkanmaa}} (tk/hyt-joiden-urakoilla-ei-arvoa* alueet true)))
    (is (= {:hoito #{:uusimaa} :paallystys #{:uusimaa}} (tk/hyt-joiden-urakoilla-ei-arvoa* alueet false))))

  (let [alueet {:hoito {:lappi {{:nimi :kuusamo} true
                                {:nimi :rovaniemi} false}
                        :uusimaa {{:nimi :vantaa} true
                                  {:nimi :helsinki} true}
                        :pirkanmaa {{:nimi :tampere} false
                                    {:nimi :nokia} false}
                        :varsinais {{:nimi :poris} false
                                    {:nimi :salo} false}}
                :paallystys {:lappi {{:nimi :kuusamo} true
                                     {:nimi :rovaniemi} false}
                             :uusimaa {{:nimi :vantaa} true
                                       {:nimi :helsinki} true}
                             :pohjoispohjanmaa {{:nimi :raahe} true
                                                {:nimi :oulu} true}
                             :pirkanmaa {{:nimi :tampere} false
                                         {:nimi :nokia} false}}}]
    (is (= {:hoito #{:pirkanmaa :varsinais} :paallystys #{:pirkanmaa}} (tk/hyt-joiden-urakoilla-ei-arvoa* alueet true)))
    (is (= {:hoito #{:uusimaa} :paallystys #{:uusimaa :pohjoispohjanmaa}} (tk/hyt-joiden-urakoilla-ei-arvoa* alueet false)))))

(deftest valitse-urakka?
  (is (true? (tk/valitse-urakka?* 1 {:id 2 :nimi :foo} :hoito 1 {} {})) "Murupolun kautta valittu urakka valitaan")
  (is (true? (tk/valitse-urakka?* 1 {:id 2 :nimi :foo} :hoito 3 {} {:hoito #{:foo}})) "Urakka valitaan, jos se kuuluu hallintayksikköön, josta kaikki on valittu")
  (is (false? (tk/valitse-urakka?* 1 {:id 2 :nimi :foo} :hoito 3 {:hoito #{:foo}} {})) "Urakkaa ei valita, jos se kuuluu hallintayksikköön, josta mitään ei ole valittu")
  (is (false? (tk/valitse-urakka?* 1 {:id 2 :nimi :foo} :hoito 3 {} {})) "Oletuksena funktio palauttaa false"))

(deftest aluesuodattimet-nested-mapiksi
  (let [payload [{:tyyppi :hoito :elinvoimakeskus {:id 1 :nimi "Lappi"} :urakat [{:id 1 :nimi "Kuusamo"} {:id 2 :nimi "Rovaniemi"}]}
                 {:tyyppi :hoito :elinvoimakeskus {:id 2 :nimi "Uusimaa"} :urakat [{:id 3 :nimi "Vantaa"} {:id 4 :nimi "Helsinki"}]}
                 {:tyyppi :hoito :elinvoimakeskus {:id 3 :nimi "Sisä-Suomi"} :urakat [{:id 5 :nimi "Tampere"} {:id 6 :nimi "Nokia"}]}

                 {:tyyppi :paallystys :elinvoimakeskus {:id 1 :nimi "Lappi"} :urakat [{:id 7 :nimi "Kuusamo"} {:id 8 :nimi "Rovaniemi"}]}
                 {:tyyppi :paallystys :elinvoimakeskus {:id 2 :nimi "Uusimaa"} :urakat [{:id 9 :nimi "Vantaa"} {:id 10 :nimi "Helsinki"}]}
                 {:tyyppi :paallystys :elinvoimakeskus {:id 3 :nimi "Sisä-Suomi"} :urakat [{:id 11 :nimi "Tampere"} {:id 12 :nimi "Nokia"}]}]
        tulos (tk/aluesuodattimet-nested-mapiksi payload)]
    (is (every? true? (mapcat (fn [[tyyppi aluekokonaisuudet]]
                                (concat [(some? (#{:hoito :paallystys} tyyppi))]
                                        (map (fn [{:keys [id nimi] :as hy}] (and (some? id) (some? nimi))) (keys aluekokonaisuudet))
                                        (mapcat
                                          (fn [urakat-ja-suodattimet]
                                            (concat (map tkd/suodatin? (keys urakat-ja-suodattimet))
                                                    (map boolean? (vals urakat-ja-suodattimet))))
                                          (vals aluekokonaisuudet))))
                              tulos))
        "Palautetuun rakenteen pitäisi olla jotain tällaista {:hoito {{:id 1 :nimi :lappi} {{:id 1 :nimi :kuusamon-urakka} false {:id 2 :nimi :rovaniemen-urakka} true}}}")))

(deftest uusi-tai-vanha-suodattimen-arvo
  (is (= true (tk/uusi-tai-vanha-suodattimen-arvo nil true)) "Jos vanha arvo on nil, palautetaan uusi arvo")
  (is (= false (tk/uusi-tai-vanha-suodattimen-arvo nil false)) "Jos vanha arvo on nil, palautetaan uusi arvo")

  (is (= true (tk/uusi-tai-vanha-suodattimen-arvo true false)) "Jos vanha arvo löytyy, käytä sitä")
  (is (= false (tk/uusi-tai-vanha-suodattimen-arvo false true)) "Jos vanha arvo löytyy, käytä sitä")
  (is (= false (tk/uusi-tai-vanha-suodattimen-arvo false nil))) "Jos vanha arvo löytyy, käytä sitä")

(deftest yhdista-aluesuodattimet
  (let [evk-lappi {:nimi "Lappi" :id 13 :evknumero 10}
        urakka-kuusamo {:nimi :kuusamo :id 1}
        urakka-kajaani {:nimi :kajaani :id 1}
        urakka-rovaniemi {:nimi :rovaniemi :id 2}
        urakka-sodankyla {:nimi :sodankyla :id 3}]

    (is (empty? (tk/yhdista-aluesuodattimet {} {})))
    ;; Yhdistämisen pitäisi toimia niin, että aina kunnioitetaan uutta rakennetta,
    ;; mutta jos urakalle löytyy boolean arvo vanhasta rakenteesta, valitaan se
    ;; Erikoishuomio on, että uudessa arvossa hallintayksikkö on {:nimi :foo}, mutta vanhassa ja tuloksessa
    ;; vain :foo
    (is (= {:hoito {10 {urakka-kuusamo false}}}
           (tk/yhdista-aluesuodattimet nil
                                       {:hoito {evk-lappi {urakka-kuusamo false}}}))
        "Tyhjillä vanhoilla arvoilla palautetaan vaan uudet arvot")
    (is (= {:hoito {10 {urakka-kuusamo false}}}
           (tk/yhdista-aluesuodattimet {:hoito {123 {urakka-kajaani false}}}
                                       {:hoito {evk-lappi {urakka-kuusamo false}}}))
        "Jos vanhat arvot on jotain ihan muuta, ne ei vaikuta lopputulokseen")
    (is (= {:hoito {10 {urakka-kuusamo true}}}
           (tk/yhdista-aluesuodattimet {:hoito {10 {urakka-kuusamo true}}}
                                       {:hoito {evk-lappi {urakka-kuusamo false}}}))
        "Jos vanhoista arvoista löytyy sama urakka, käytetään sen arvoa")
    (is (= {:hoito {10 {urakka-kuusamo true}}}
           (tk/yhdista-aluesuodattimet {:hoito {10 {urakka-kuusamo true
                                                        urakka-rovaniemi false}}}
                                       {:hoito {evk-lappi {urakka-kuusamo false}}}))
        "Vanhasta joukosta lopputulokseen vaikuttavat VAIN urakat, jotka ovat myös uudessa joukossa")
    (is (= {:hoito {10 {urakka-kuusamo true
                         urakka-sodankyla true}}}
           (tk/yhdista-aluesuodattimet {:hoito {10 {urakka-kuusamo true
                                                        urakka-rovaniemi false}}}
                                       {:hoito {evk-lappi {urakka-kuusamo false
                                                           urakka-sodankyla true}}}))
        "Uudesta joukosta täytyy palautua myös urakat, joita ei ole vanhassa joukossa")))
