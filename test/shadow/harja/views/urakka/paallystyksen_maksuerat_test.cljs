(ns harja.views.urakka.paallystyksen-maksuerat-test
  (:require [cljs.test :refer-macros [deftest is]]
            [harja.tiedot.urakka.paallystyksen-maksuerat :as maksuerat]))

(deftest maksuerien-muunto-grid-muotoon-toimi
  (is (= {:maksuera1 "Eka erä"
          :maksuera2 "Toka erä"
          :maksuera3 "Kolmas erä"}
         (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat [{:id 1 :sisalto "Kolmas erä" :maksueranumero 3}
                        {:id 2 :sisalto "Eka erä" :maksueranumero 1}
                        {:id 3 :sisalto "Toka erä" :maksueranumero 2}]})))
  (is (= {:maksuera2 "Toka erä"
          :maksuera3 "Kolmas erä"}
         (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat [{:sisalto "Kolmas erä" :maksueranumero 3}
                        {:sisalto "Toka erä" :maksueranumero 2}]})))
  (is (= {:yllapitokohde-id 1}
         (maksuerat/maksuerarivi-grid-muotoon
           {:yllapitokohde-id 1 :maksuerat []})))
  (is (= {}
         (maksuerat/maksuerarivi-grid-muotoon
           {:maksuerat nil}))))

(deftest maksuerien-muunto-tallennusmuotoon-toimi
  (is (= {:maksuerat [{:maksueranumero 1 :sisalto "Eka puolikas"}
                      {:maksueranumero 2 :sisalto "Toka puolikas"}
                      {:maksueranumero 3 :sisalto "Ylimääräinen"}]}
         (maksuerat/maksuerarivi-tallennusmuotoon
           {:maksuera3 "Ylimääräinen"
            :maksuera1 "Eka puolikas"
            :maksuera2 "Toka puolikas"})))
  (is (= {:maksuerat [{:maksueranumero 3 :sisalto "Ylimääräinen"}]}
         (maksuerat/maksuerarivi-tallennusmuotoon
           {:maksuera3 "Ylimääräinen"})))
  (is (= {:maksuerat [{:maksueranumero 1 :sisalto "Joku maksuerä"}
                      {:maksueranumero 3 :sisalto "Ylimääräinen"}]}
         (maksuerat/maksuerarivi-tallennusmuotoon
           {:maksuera3 "Ylimääräinen"
            :maksuera1 "Joku maksuerä"})))
  (is (= {:yllapitokohde-id 1 :maksuerat []}
         (maksuerat/maksuerarivi-tallennusmuotoon
           {:yllapitokohde-id 1}))))
