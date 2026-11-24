(ns harja.ui.perus.simulaattori-test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.ui.perus.simulaattori :as simulaattori]))

(deftest kelvollinen-puu-hyvaksytaan
  (is (simulaattori/kelvollinen?
        [{:komponentti :lomake
          :lapset [{:komponentti :valintaryhma}]}])))

(deftest tuntematon-komponentti-hylataan
  (is (= ["Tuntematon komponentti: :ei-ole"]
         (simulaattori/virheet [{:komponentti :ei-ole}]))))

(deftest vaarallinen-vanhempi-lapsi-suhde-hylataan
  (testing "lomake ei voi sisältää nappia suoraan sivun juurena"
    (is (= [":nappi: vanhempi=:sivu ei ole sallittu"]
           (simulaattori/virheet [{:komponentti :nappi}])))))

(defn- valintaryhma-asetuksilla [asetukset]
  [{:komponentti :lomake
    :lapset [{:komponentti :valintaryhma
              :asetukset asetukset}]}])

(deftest tuntematon-asetus-hylataan
  (is (= ["valintaryhma: tuntematon asetus :ei-ole"]
         (simulaattori/virheet (valintaryhma-asetuksilla {:ei-ole "x"})))))

(deftest suojattu-asetus-hylataan
  (testing "komponentin omistamia avaimia ei voi ylikirjoittaa"
    (doseq [avain [:id :arvo :vaihda-fn]]
      (is (= [(str "valintaryhma: suojattu asetus " avain " ei ole sallittu")]
             (simulaattori/virheet (valintaryhma-asetuksilla {avain "x"})))
          (str "avain " avain)))))

(deftest funktio-hylataan-asetuksessa
  (is (= ["valintaryhma: asetus :otsikko on funktio, ei sallittu"]
         (simulaattori/virheet (valintaryhma-asetuksilla {:otsikko (fn [] "x")})))))

(deftest symboli-hylataan-asetuksessa
  (is (= ["valintaryhma: asetus :otsikko on symboli, ei sallittu"]
         (simulaattori/virheet (valintaryhma-asetuksilla {:otsikko 'puhelin})))))

(deftest funktio-hylataan-sisempaan-arvoon
  (is (= ["valintaryhma: asetus :vaihtoehdot on funktio, ei sallittu"]
         (simulaattori/virheet
           (valintaryhma-asetuksilla {:vaihtoehdot [{:arvo :hoito :nimi (fn [] "x")}]})))))

#?(:clj
   (deftest tagattu-literaali-hylataan
     (is (= ["valintaryhma: asetus :otsikko on tagattu literaali, ei sallittu"]
            (simulaattori/virheet
              (valintaryhma-asetuksilla {:otsikko (tagged-literal 'foo 1)}))))))

(deftest kelvolliset-asetukset-hyvaksytaan
  (is (simulaattori/kelvollinen?
        (valintaryhma-asetuksilla {:otsikko "Urakkatyyppi"
                                   :vaihtoehdot [{:arvo :hoito :nimi "Hoito"}]}))))

(deftest virheellinen-rakenne-antaa-virheen-eika-kaadu
  (testing "asetukset ei ole map"
    (doseq [asetukset ["x" [1 2] 5]]
      (is (= [":lomake: asetukset ei ole map"]
             (simulaattori/virheet [{:komponentti :lomake :asetukset asetukset}]))
          (pr-str asetukset))))
  (testing "lapset ei ole lista"
    (is (= [":lomake: lapset ei ole lista"]
           (simulaattori/virheet [{:komponentti :lomake :lapset "abc"}]))))
  (testing "lapsi ei ole map"
    (is (= ["Solmun pitää olla map: 1"]
           (simulaattori/virheet [{:komponentti :lomake :lapset [1]}])))))

(deftest layout-komponentit-hyvaksyvat-tyypilliset-lapsensa
  (testing "listaussivu: suodattimet, taulukko ja toiminnot"
    (is (simulaattori/kelvollinen?
          [{:komponentti :pino
            :lapset [{:komponentti :suodatinrivi
                      :lapset [{:komponentti :hoitokausi}
                               {:komponentti :livi-pudotusvalikko}
                               {:komponentti :checkbox-pudotusvalikko}
                               {:komponentti :nappi}]}
                     {:komponentti :perusgrid}
                     {:komponentti :toimintoryhma
                      :lapset [{:komponentti :nappi}
                               {:komponentti :linkki}]}]}])))
  (testing "panel sisältää tietoja ja muita pinoja"
    (is (simulaattori/kelvollinen?
          [{:komponentti :pino
            :lapset [{:komponentti :panel
                      :lapset [{:komponentti :tietoja}
                               {:komponentti :taulukkotietonakyma}
                               {:komponentti :info-laatikko}]}]}])))
  (testing "suodatin kuuluu suodatinriviin, ei suoraan sivulle"
    (is (seq (simulaattori/virheet [{:komponentti :suodatin}])))
    (is (simulaattori/kelvollinen?
          [{:komponentti :suodatinrivi
            :lapset [{:komponentti :suodatin}
                     {:komponentti :valintaryhma}]}]))))
