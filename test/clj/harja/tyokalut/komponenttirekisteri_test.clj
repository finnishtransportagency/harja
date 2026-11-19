(ns harja.tyokalut.komponenttirekisteri-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [harja.tyokalut.komponenttirekisteri :as komponenttirekisteri]))

(defn- funktio-loytyy? [arvo]
  (let [loydetty (atom false)]
    (walk/postwalk (fn [x] (when (fn? x) (reset! loydetty true)) x) arvo)
    @loydetty))

(deftest muotoile-palauttaa-kaikki-ilman-funktioita
  (testing "Ilman suodattimia tulos sisältää kaikki komponentit ilman funktioita"
    (let [{:keys [tulos virhe]} (komponenttirekisteri/muotoile {})]
      (is (nil? virhe))
      (is (= (count (komponenttirekisteri/hae-kaikki)) (count tulos)))
      (is (every? #(contains? % :ankkuri) tulos))
      (is (every? #(contains? % :asetukset) tulos))
      (is (not (funktio-loytyy? tulos))))))

(deftest muotoile-suodattaa-id-ja-ryhma
  (testing "Id-suodatin palauttaa vain yhden komponentin"
    (let [{:keys [tulos]} (komponenttirekisteri/muotoile {:id :nappi})]
      (is (= [:nappi] (map :id tulos))))
    (testing "Ryhmä-suodatin palauttaa vain ryhmän komponentit"
      (let [{:keys [tulos]} (komponenttirekisteri/muotoile {:ryhma :lomakkeet})]
        (is (seq tulos))
        (is (every? #(= :lomakkeet (:ryhma %)) tulos))))))

(deftest muotoile-tuntematon-id-antaa-virheen
  (testing "Tuntematon id palauttaa virheen eikä tulosta"
    (let [{:keys [tulos virhe]} (komponenttirekisteri/muotoile {:id :ei-ole})]
      (is (nil? tulos))
      (is (re-find #"ei-ole" virhe)))))

(deftest parsi-argumentit-muuttaa-avain-arvo-parit
  (testing "Komentoriviargumentit muuttuvat suodattimiksi"
    (is (= {:id :nappi} (komponenttirekisteri/parsi-argumentit [":id" "nappi"])))
    (is (= {:ryhma :lomakkeet} (komponenttirekisteri/parsi-argumentit [":ryhma" "lomakkeet"])))))
