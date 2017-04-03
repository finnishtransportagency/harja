(ns harja.views.urakka.paallystyksen-maksuerat-test
  (:require
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.pvm :refer [->pvm]]
    [harja.tiedot.urakka.paallystyksen-maksuerat :as maksuerat]
    [harja.loki :refer [log]]
    [harja.tyokalut.functor :refer [fmap]]))


(deftest maksuerien-muunto-grid-muotoon-toimi
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat [{:sisalto "Kolmas erä" :maksueranumero 3}
                        {:sisalto "Eka erä" :maksueranumero 1}
                        {:sisalto "Toka erä" :maksueranumero 2}]})
         {:maksuera1 "Eka erä" :maksuera2 "Toka erä" :maksuera3 "Kolmas erä"}))
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat [{:sisalto "Kolmas erä" :maksueranumero 3}
                        {:sisalto "Toka erä" :maksueranumero 2}]})
         {:maksuera1 "Eka erä" :maksuera3 "Kolmas erä"}))
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:yllapitokohde-id 1 :maksuerat []})
         {:yllapitokohde-id 1}))
  (is (= (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat nil})
         {})))

(deftest maksuerien-muunto-tallennusmuotoon-toimi
  (is (= (maksuerat/maksuerarivi-tallennusmuotoon
           {:maksuera3 "Ylimääräinen" :maksuera1 "Eka puolikas" :maksuera2 "Toka puolikas"})
         {:maksuerat [{:sisalto "Eka puolikas" :maksueranumero 1}
                      {:sisalto "Toka puolikas" :maksueranumero 2}
                      {:sisalto "Ylimääräinen" :maksueranumero 3}]}))
  (is (= (maksuerat/maksuerarivi-tallennusmuotoon
           ;; Käyttäjä syöttää kolmannen maksuerän, ei muita
           {:maksuera3 "Ylimääräinen"})
         {:maksuerat {:sisalto "Ylimääräinen" :maksueranumero 3}}))
  (is (= (maksuerat/maksuerarivi-tallennusmuotoon
           ;; Käyttäjä syöttää ensimmäisen ja kolmannen maksuerän, ei muita
           {:maksuera3 "Ylimääräinen" :maksuera1 "Joku maksuerä"})
         {:maksuerat [{:sisalto "Joku maksuerä" :maksueranumero 1}
                      {:sisalto "Ylimääräinen" :maksueranumero 3}]}))
  (is (= (maksuerat/maksuerarivi-tallennusmuotoon {:yllapitokohde-id 1})
         {:yllapitokohde-id 1 :maksuerat []})))

