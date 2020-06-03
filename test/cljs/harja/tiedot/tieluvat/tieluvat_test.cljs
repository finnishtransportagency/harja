(ns harja.tiedot.tieluvat.tieluvat-test
  (:require [clojure.test :refer-macros [deftest is testing]]
            [harja.tiedot.tieluvat.tieluvat :as tiedot]
            [harja.domain.tielupa :as tielupa]
            [harja.testutils.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.pvm :as pvm]))


(deftest kenttien-nayttaminen
  (let [nayta? (partial
                 tiedot/nayta-kentat?
                 (fn [_] {:skeemat
                          [{:nimi :foo}
                           {:hae :bar
                            :nimi :ei-tama}]}))]
    (is (true? (nayta? {:foo 1})))
    (is (true? (nayta? {:bar 1})))
    (is (true? (nayta? {:foo 1 :bar 1})))
    (is (true? (nayta? {:foo 1 :bar nil})))
    (is (true? (nayta? {:foo 1 :bar []})))
    (is (true? (nayta? {:bar 1 :ei-tama 1})))
    (is (true? (nayta? {:bar 1 :asd 1})))

    (is (false? (nayta? {:foo nil})))
    (is (false? (nayta? {:foo []})))
    (is (false? (nayta? {:bar nil})))
    (is (false? (nayta? {:bar []})))
    (is (false? (nayta? {:bar [] :foo nil})))
    (is (false? (nayta? {:bar [] :foo nil :ei-tama 1})))
    (is (false? (nayta? {:bar [] :foo nil :hohoo 1})))
    (is (false? (nayta? {:ei-tama 1})))
    (is (false? (nayta? {:asd 1})))))

(deftest vapaiden-sijaintien-suodattaminen
  (let [data {::tielupa/sijainnit [{::tielupa/tie 1 ::tielupa/aosa 1 ::tielupa/aet 1}
                                   {::tielupa/tie 2 ::tielupa/aosa 2 ::tielupa/aet 2}
                                   {::tielupa/tie 3 ::tielupa/aosa 3 ::tielupa/aet 3}
                                   {::tielupa/tie 4 ::tielupa/aosa 4 ::tielupa/aet 4}]
              ::tielupa/mainokset [{::tielupa/tie 1 ::tielupa/aosa 1 ::tielupa/aet 1}]
              ::tielupa/liikennemerkkijarjestelyt [{::tielupa/tie 2 ::tielupa/aosa 2 ::tielupa/aet 2}
                                                   {::tielupa/tie 3 ::tielupa/aosa 3 ::tielupa/aet 3}]
              ::tielupa/johtoasennukset []
              ::tielupa/kaapeliasennukset [{::tielupa/tie 30 ::tielupa/aosa 30 ::tielupa/aet 30}]}]
    (is (= #{{::tielupa/tie 4 ::tielupa/aosa 4 ::tielupa/aet 4}}
           (tiedot/pelkat-vapaat-sijainnit data)))))

(deftest nakymassa?
  (is (= {:nakymassa? true} (e! (tiedot/->Nakymassa? true))))
  (is (= {:nakymassa? false} (e! (tiedot/->Nakymassa? false)))))

(deftest valintojen-paivitys
  ;; TODO: Päivitä PaivitaValinnat
  #_(vaadi-async-kutsut
    #{tiedot/->HaeTieluvat}
    (let [v (zipmap tiedot/valintojen-avaimet (repeat [1 1]))]
      (let [tulos (e! (tiedot/->PaivitaValinnat v))]
        (is (= v (:valinnat tulos)))
        (is (true? (:tielupien-haku-kaynnissa? tulos)))
        (is (some? (:nykyinen-haku tulos))))))

  #_(vaadi-async-kutsut
    #{tiedot/->HaeTieluvat}
    (let [u {}
          tila {:valinnat {:foo :bar}}]
      (let [tulos (e! (tiedot/->PaivitaValinnat u) tila)]
        (is (= {:foo :bar} (:valinnat tulos)))
        (is (true? (:tielupien-haku-kaynnissa? tulos)))
        (is (some? (:nykyinen-haku tulos))))))

  #_(testing "Haku ei lähde, jos vain toinen osa aikaparametria on annettu"
    (vaadi-async-kutsut
     #{}
     (let [u {:myonnetty [1 nil]}]
       (let [tulos (e! (tiedot/->PaivitaValinnat u))]
         (is (= {:myonnetty [1 nil]} (:valinnat tulos)))
         (is (nil? (:tielupien-haku-kaynnissa? tulos)))
         (is (nil? (:nykyinen-haku tulos))))))))

(deftest tielupien-haku
  (vaadi-async-kutsut
    #{tiedot/->TieluvatEiHaettu tiedot/->TieluvatHaettu}
    (let [aikaleima (pvm/nyt)]
      (is (= {:tielupien-haku-kaynnissa? true
              :nykyinen-haku aikaleima}
            (e! (tiedot/->HaeTieluvat {} aikaleima))))))

  (vaadi-async-kutsut
    #{tiedot/->TieluvatEiHaettu tiedot/->TieluvatHaettu}
    (let [aikaleima (pvm/nyt)]
      (is (= {:tielupien-haku-kaynnissa? true
              :nykyinen-haku aikaleima}
             (e! (tiedot/->HaeTieluvat {} aikaleima)
                 {:tielupien-haku-kaynnissa? true}))))))

(deftest tielupia-ei-haettu
  (is (= {:tielupien-haku-kaynnissa? false
          :nykyinen-haku nil}
         (e! (tiedot/->TieluvatEiHaettu {} nil)))))

(deftest tieluvat-haettu
  (is (= {:tielupien-haku-kaynnissa? false
          :haetut-tieluvat [{:id 1}]
          :nykyinen-haku nil}
         (e! (tiedot/->TieluvatHaettu [{:id 1}] nil)))))

(deftest tieluvan-valinta
  (is (= {:valittu-tielupa {:bar :baz}}
         (e! (tiedot/->ValitseTielupa {:bar :baz})))))
