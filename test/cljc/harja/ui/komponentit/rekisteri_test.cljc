(ns harja.ui.komponentit.rekisteri-test
  (:require [clojure.set]
            [clojure.test :refer [deftest is testing]]
            [harja.ui.komponentit.rekisteri :as rekisteri]))

(deftest rekisteri-on-ehja
  (is (empty? (rekisteri/virheet))))

(deftest virheet-tunnistaa-rikkinaisen-rekisterin
  (let [ok (first rekisteri/komponentit)]
    (testing "tupla-id"
      (is (seq (rekisteri/virheet rekisteri/ryhmat [ok ok]))))
    (testing "tuntematon ryhmä, luonne ja bootstrap-tila"
      (is (= 3 (count (rekisteri/virheet rekisteri/ryhmat
                        (cons (assoc ok :ryhma :ei-ole :luonne :ei-ole :bootstrap-tila :ei-ole)
                          (rest rekisteri/komponentit)))))))
    (testing "puuttuva kuvaus"
      (is (seq (rekisteri/virheet rekisteri/ryhmat [(dissoc ok :kuvaus)]))))))

(deftest virheet-tunnistaa-rakenteelliset-puutteet
  (let [ok (first rekisteri/komponentit)
        virheet (fn [komponentti]
                  (rekisteri/virheet rekisteri/ryhmat (cons komponentti (rest rekisteri/komponentit))))]
    (testing "puuttuva rooli"
      (is (seq (virheet (dissoc ok :rooli)))))
    (testing "tuntematon rooli"
      (is (seq (virheet (assoc ok :rooli :ei-ole)))))
    (testing "puuttuvat ja tyhjät slotit, tilat ja sallitut vanhemmat"
      (doseq [avain [:slotit :tilat :sallitut-vanhemmat]]
        (is (seq (virheet (dissoc ok avain))) (str "puuttuva " avain))
        (is (seq (virheet (assoc ok avain #{}))) (str "tyhjä " avain))))
    (testing "tuntematon vanhempi"
      (is (seq (virheet (assoc ok :sallitut-vanhemmat #{:ei-ole})))))
    (testing "variantit on erillinen avain eikä sekoitu tiloihin"
      (let [laatikko (rekisteri/hae :info-laatikko)]
        (is (contains? laatikko :variantit))
        (is (empty? (clojure.set/intersection (:tilat laatikko) (:variantit laatikko))))))))

(deftest ryhmittele-jarjestaa-ryhmat-ja-jattaa-tyhjat-pois
  (let [ryhmat [{:id :a :nimi "A"} {:id :tyhja :nimi "Tyhjä"} {:id :b :nimi "B"}]
        komponentit [{:id :x :ryhma :b} {:id :y :ryhma :a} {:id :z :ryhma :b}]]
    (is (= [[:a [:y]] [:b [:x :z]]]
           (map (fn [{:keys [id komponentit]}] [id (mapv :id komponentit)])
             (rekisteri/ryhmittele ryhmat komponentit))))))

(deftest hae-palauttaa-nil-tuntemattomalle-idlle
  (is (= :nappi (:id (rekisteri/hae :nappi))))
  (is (nil? (rekisteri/hae :ei-ole))))

(deftest ankkuri-johdetaan-id-sta
  (is (= "ui-komponenttien-tarkastelu-nappi" (rekisteri/ankkuri {:id :nappi}))))
