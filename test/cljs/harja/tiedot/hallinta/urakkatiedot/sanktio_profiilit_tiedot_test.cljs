(ns harja.tiedot.hallinta.urakkatiedot.sanktio-profiilit-tiedot-test
  (:require [cljs.test :as test :refer-macros [deftest is]]
            [harja.testutils.tuck-apurit :as tuck-apurit]
            [harja.tiedot.hallinta.urakkatiedot.sanktio-profiilit-tiedot :as tiedot]))

(defn- muodosta-tila
  []
  {:haku-kaynnissa? false
   :detalji-haku-kaynnissa? false
   :profiilit []
   :profiilin-detaljit {}
   :valittu-profiili-id nil
   :suodattimet {:teksti ""
                 :urakkatyyppi :kaikki
                 :aktiivisuus :kaikki}})

(deftest paivita-suodatin-synkronoi-valitun-profiilin-nakyvaan-listaan
  (let [tila (assoc (muodosta-tila)
               :profiilit [{:id 1 :nimi "Aktiivinen profiili" :urakkatyyppi :teiden-hoito :aktiivinen true}
                           {:id 2 :nimi "Piiloutuva profiili" :urakkatyyppi :teiden-hoito :aktiivinen false}]
               :profiilin-detaljit {1 {:profiili {:id 1}}
                                    2 {:profiili {:id 2}}}
               :valittu-profiili-id 2)
        tulos (tuck-apurit/e! (tiedot/->PaivitaSuodatin :aktiivisuus :aktiiviset) tila)]
    (is (= :aktiiviset (get-in tulos [:suodattimet :aktiivisuus])))
    (is (= 1 (:valittu-profiili-id tulos))
      "Kun valittu profiili ei enää läpäise suodatinta, valinnan pitää siirtyä näkyvään listaan")))

(deftest hae-sanktio-profiilit-onnistui-sailyttaa-vain-nakyvan-valinnan
  (let [tila (assoc (muodosta-tila)
               :suodattimet {:teksti ""
                             :urakkatyyppi :kaikki
                             :aktiivisuus :aktiiviset}
               :profiilin-detaljit {1 {:profiili {:id 1}}
                                    2 {:profiili {:id 2}}}
               :valittu-profiili-id 2)
        vastaus [{:id 1 :nimi "Aktiivinen profiili" :urakkatyyppi :teiden-hoito :aktiivinen true}
                 {:id 2 :nimi "Piiloutuva profiili" :urakkatyyppi :teiden-hoito :aktiivinen false}]
        tulos (tuck-apurit/e! (tiedot/->HaeSanktioProfiilitOnnistui vastaus) tila)]
    (is (= 1 (:valittu-profiili-id tulos))
      "Listapäivityksen jälkeen detail-paneeli ei saa jäädä osoittamaan suodatettua profiilia")))

(deftest vaikutusaika-teksti-kuvaa-alkupohjaisen-valinnan
  (is (= "Valitaan urakoille, joiden alkupäivä on 01.10.2025 - 30.09.2026. Profiili pysyy samana koko sopimuskauden."
    (tiedot/vaikutusaika-teksti {:alkupvm "20251001T000000"
             :loppupvm "20260930T000000"})))
  (is (= "Valitaan urakoille, joiden alkupäivä on 01.10.2026 tai myöhemmin. Profiili pysyy samana koko sopimuskauden."
    (tiedot/vaikutusaika-teksti {:alkupvm "20261001T000000"
             :loppupvm nil}))))

(deftest vaikutusajan-paivamaarat-formatoidaan-luettaviksi
  (is (= "01.10.2026"
    (tiedot/vaikutusajan-alku-teksti {:alkupvm "20261001T000000"})))
  (is (= "30.09.2027"
    (tiedot/vaikutusajan-loppu-teksti {:loppupvm "20270930T000000"})))
  (is (= "toistaiseksi"
    (tiedot/vaikutusajan-loppu-teksti {:loppupvm nil}))))
