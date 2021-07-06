(ns harja.tyokalut.paikkaus-test
  (:require
    [harja.domain.paikkaus :as paikkaus]
    [harja.testi :refer :all]))

(defn hae-tyomenetelma-arvolla [avain hakusana tyomenetelmat]
  (let [avain-idx (case avain
                    :id 0
                    :nimi 1
                    :lyhenne 2)]
    (first (filter #(= (nth % avain-idx) hakusana) tyomenetelmat))))

(defn hae-tyomenetelman-arvo
  ([palautettava-avain haettava-avain hakusana tyomenetelmat]
   (let [avain-idx (case palautettava-avain
                     :id 0
                     :nimi 1
                     :lyhenne 2)]
     (nth (hae-tyomenetelma-arvolla haettava-avain hakusana tyomenetelmat) avain-idx)))
  ([arvo haettava-avain hakusana]
   (hae-tyomenetelman-arvo arvo haettava-avain hakusana @paikkauskohde-tyomenetelmat)))