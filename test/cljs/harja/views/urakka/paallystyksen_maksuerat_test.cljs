(ns harja.views.urakka.paallystyksen-maksuerat-test
  (:require
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.pvm :refer [->pvm]]
    [harja.tiedot.urakka.paallystyksen-maksuerat :as maksuerat]
    [harja.loki :refer [log]]
    [harja.tyokalut.functor :refer [fmap]]))


(deftest maksuerien-muunto-grid-muotoon-toimi
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat [{:id 1 :sisalto "Kolmas erä" :maksueranumero 3}
                        {:id 2 :sisalto "Eka erä" :maksueranumero 1}
                        {:id 3 :sisalto "Toka erä" :maksueranumero 2}]})
         {:maksuera1 "Eka erä" :maksuera2 "Toka erä" :maksuera3 "Kolmas erä"}))
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat [{:sisalto "Kolmas erä" :maksueranumero 3}
                        {:sisalto "Toka erä" :maksueranumero 2}]})
         {:maksuera2 "Toka erä" :maksuera3 "Kolmas erä"}))
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:yllapitokohde-id 1 :maksuerat []})
         {:yllapitokohde-id 1}))
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat nil})
         {})))

(deftest maksuerien-muunto-tallennusmuotoon-toimi
  (is (= (maksuerat/maksuerarivi-tallennusmuotoon
           {:maksuera3 "Ylimääräinen" :maksuera1 "Eka puolikas" :maksuera2 "Toka puolikas"})
         {:maksuerat [{:maksueranumero 1 :sisalto "Eka puolikas" }
                      {:maksueranumero 2 :sisalto "Toka puolikas"}
                      {:maksueranumero 3 :sisalto "Ylimääräinen"}]}))
  (is (= (maksuerat/maksuerarivi-tallennusmuotoon
           ;; Käyttäjä syöttää kolmannen maksuerän, ei muita
           {:maksuera3 "Ylimääräinen"})
         {:maksuerat [{:maksueranumero 3 :sisalto "Ylimääräinen"}]}))
  (is (= (maksuerat/maksuerarivi-tallennusmuotoon
           ;; Käyttäjä syöttää ensimmäisen ja kolmannen maksuerän, ei muita
           {:maksuera3 "Ylimääräinen" :maksuera1 "Joku maksuerä"})
         {:maksuerat [{:maksueranumero 1 :sisalto "Joku maksuerä"}
                      {:maksueranumero 3 :sisalto "Ylimääräinen"}]}))
  (is (= (maksuerat/maksuerarivi-tallennusmuotoon {:yllapitokohde-id 1})
         {:yllapitokohde-id 1 :maksuerat []})))

