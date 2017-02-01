(ns harja.ui.kartta.apurit-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [harja.ui.kartta.apurit :as apurit]))

(deftest pisteiden-taitokset
  (testing "Taitoksia tulee pisteiden lukumäärä -1"
           (let [montako? (fn [pisteet]
                            (let [lkm (if (empty? pisteet) 0 (dec (count pisteet)))]
                              (= lkm (count (apurit/pisteiden-taitokset pisteet)))))]
             (is (montako? [[1 1]]))
             (is (montako? []))
             (is (montako? [[1 1] [2 2]]))
             (is (montako? [[1 1] [2 2] [3 3]]))))

  (testing "Taitokset ovat jatkuvia"
    (let [parillinen-luku 200
          taitokset (partition 2 (take parillinen-luku (interleave (range) (range))))
          tulos (apurit/pisteiden-taitokset taitokset)
          virheellinen [{:sijainti [[1 1] [1 1]]}
                        {:sijainti [[2 2] [2 2]]}]
          tarkista #(loop [[{[_ p2] :sijainti}
                            {[p3 _] :sijainti} :as kaikki] %]
                      (cond
                        (= 1 (count kaikki))
                        true

                        (not= p2 p3)
                        false

                        :default
                        (recur (rest kaikki))))]
      (is (not (tarkista virheellinen)))
      (is (not-empty tulos))
      (is (tarkista tulos)))))

(deftest taitokset-valimatkoin
  (testing "Maksimivälimatkan vaikutus taitoksiin"
    (let [pisteet (partition 2 (take 8 (interleave (range) (range))))
          taitokset (apurit/pisteiden-taitokset pisteet)
          joka-taitos (apurit/taitokset-valimatkoin 0 0 taitokset)
          joka-taitos2 (apurit/taitokset-valimatkoin 0 1 taitokset)
          joka-toinen (apurit/taitokset-valimatkoin 0 2 taitokset)]
      (is (= (count taitokset) (count joka-taitos)))
      (is (= (count taitokset) (count joka-taitos2)))
      (is (= (int (/ (Math/floor (count taitokset)) 2)) (count joka-toinen)))))

  (testing "Kulmien vaikutus taitoksiin"
    (let [yksi-180-kulma (apurit/pisteiden-taitokset [[0 0] [1 1] [-1 -1] [-2 -2]])]
      (is (= 1 (count (apurit/taitokset-valimatkoin 0 100 yksi-180-kulma))))
      (is (= 1 (count (apurit/taitokset-valimatkoin 100 100 yksi-180-kulma))))
      (is (= (apurit/taitokset-valimatkoin 100 100 yksi-180-kulma) (apurit/taitokset-valimatkoin 0 100 yksi-180-kulma))))

    ;; Kaksi vierekkäistä kulmaa
    (let [kaksi-180-kulmaa (apurit/pisteiden-taitokset [[0 0] [1 1] [-1 -1] [2 2]])]
      (is (= 2 (count (apurit/taitokset-valimatkoin 0 100 kaksi-180-kulmaa))))
      (is (= 1 (count (apurit/taitokset-valimatkoin 100 100 kaksi-180-kulmaa)))))

    (let [*180-ja-45-kulmat (apurit/pisteiden-taitokset [[0 0] [1 1] [-1 -1] [-2 -2] [-102 98]])]
      (is (= 2 (count (apurit/taitokset-valimatkoin 0 100 *180-ja-45-kulmat))))
      (is (= 2 (count (apurit/taitokset-valimatkoin 100 100 *180-ja-45-kulmat)))))

    ;; Kolme kulmaa, mutta kaksi on vierekkäin
    (let [kaksi-180-kulmaa-ja-45-kulma (apurit/pisteiden-taitokset [[0 0] [1 1] [-1 -1] [2 2] [-98 102]])]
      (is (= 3 (count (apurit/taitokset-valimatkoin 0 100 kaksi-180-kulmaa-ja-45-kulma))))
      (is (= 2 (count (apurit/taitokset-valimatkoin 100 100 kaksi-180-kulmaa-ja-45-kulma)))))))