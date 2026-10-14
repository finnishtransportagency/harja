(ns harja.ui.kartta.infopaneelin-sisalto-test
  (:require [cljs.test :refer-macros [deftest is]]
            [harja.ui.kartta.infopaneelin-sisalto :as paneeli]))

(deftest skeemamuodossa-suodattaa-ja-jarjestaa
  (let [asiat [{:tyyppi-kartalla :tuntematon
                :id 1}
               {:tyyppi-kartalla :talvihoitoreitit
                :id 2
                :infopaneelin-tiedot {:nimi "Reitti A"
                                      :pituus 12}}
               {:tyyppi-kartalla :talvihoitoreitit
                :id 3
                :infopaneelin-tiedot {:nimi "Reitti B"
                                      :pituus 18}}]
        skeemat (paneeli/skeemamuodossa asiat)]
    (is (= 2 (count skeemat)))
    (is (= ["Talvihoitoreitti: Reitti B"
            "Talvihoitoreitti: Reitti A"]
           (mapv :otsikko skeemat)))
    (is (= [{:nimi "Reitti B"
             :pituus 18}
            {:nimi "Reitti A"
             :pituus 12}]
           (mapv :data skeemat)))))

(deftest vain-uniikit-sailyttaa-tunnisteettomat
  (is (= [{:tunniste :id
           :data {:id 1}}
          {:tunniste :id
           :data {:id 2}}
          {:tunniste :homma
           :data {:homma 2}}
          {:data {:id 3}}
          {:data {:id 3}}]
         (paneeli/vain-uniikit [{:tunniste :id
                                 :data {:id 1}}
                                {:tunniste :id
                                 :data {:id 1}}
                                {:tunniste :id
                                 :data {:id 1}}
                                {:tunniste :id
                                 :data {:id 2}}
                                {:tunniste :homma
                                 :data {:homma 2}}
                                {:data {:id 3}}
                                {:data {:id 3}}]))))
