(ns harja.views.kartta.tasot-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [harja.views.kartta.tasot :as tasot]))


(deftest organisaatioiden-piirtaminen-kartalle
  (testing "Tilannekuvassa ei käytetä :organisaatio-tasoa"
    (is (nil? (tasot/urakat-ja-organisaatiot-kartalla* [] {} {} :tilannekuva nil {}))))

  (testing "Ilmoituksissa näytetään vain valitun hallintayksikön TAI valitun urakan raja"
    (is (nil? (tasot/urakat-ja-organisaatiot-kartalla* [] nil nil :ilmoitukset nil {})))
    (is (= [{:id 1 :valittu true}]
           (tasot/urakat-ja-organisaatiot-kartalla* [{:id 1} {:id 3}] {:id 1} nil :ilmoitukset nil {})))
    (is (= [{:id 2 :valittu true}]
           (tasot/urakat-ja-organisaatiot-kartalla* [{:id 1} {:id 3}] {:id 1} {:id 2} :ilmoitukset nil {}))))

  (testing "Jos hallintayksikköä ei ole valittu, näytetään kaikki hallintayksiköt"
    (is (= [{:id 1} {:id 3}]
           (tasot/urakat-ja-organisaatiot-kartalla* [{:id 1} {:id 3}] nil nil :raportit nil {}))))

  (testing "Jos urakkaa ei ole valittu, näytetään hallintayksikön urakat"
    (is (= [{:id 1 :valittu true} {:id 2} {:id 4}]
           (tasot/urakat-ja-organisaatiot-kartalla* [{:id 1} {:id 3}] {:id 1} nil :raportit nil [{:id 2} {:id 4}]))))

  (testing "Urakka valittu, näytetään urakan rajat"
    (is (= [{:id 2 :valittu true}]
           (tasot/urakat-ja-organisaatiot-kartalla* [{:id 1} {:id 3}] {:id 1} {:id 2} :raportit nil [{:id 2} {:id 4}]))))

  (testing "Ylläpidon kohdeluetteloissa ei piirretä urakan rajoja"
    (is (nil? (tasot/urakat-ja-organisaatiot-kartalla* [{:id 1} {:id 3}] {:id 1} {:id 2} :urakat :kohdeluettelo-paallystys [{:id 2} {:id 4}])))
    (is (nil? (tasot/urakat-ja-organisaatiot-kartalla* [{:id 1} {:id 3}] {:id 1} {:id 2} :urakat :kohdeluettelo-paikkaus [{:id 2} {:id 4}])))))

(deftest aktiivisten-tasojen-loytaminen
  (let [tasot {:foo {:id 1 :aktiivinen? true}
               :bar {:id 2 :aktiivinen? false}
               :baz {:id 3 :aktiivinen? true}}]
    (is (= '({:id 1 :aktiivinen? true})
           (tasot/aktiiviset-nakymien-tasot* (keys tasot)
                                             #{:baz}
                                             tasot
                                             :aktiivinen?)))))

(deftest nykyiset-karttatasot
  (let [atomit {:foo (atom true)
                :bar (atom false)
                :baz (atom true)}]
    (is (= '(:foo)
           (tasot/nykyiset-karttatasot* atomit #{:foo})))))