(ns harja.palvelin.palvelut.yllapitokohteet.paikkaukset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.paikkaukset :as paikkaukset]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :paikkaukset (component/using
                                       (paikkaukset/->Paikkaukset)
                                       [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-urakan-paikkauskohteet-testi
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        paikkaukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-urakan-paikkauskohteet
                                    +kayttaja-jvh+
                                    {::paikkaus/urakka-id urakka-id
                                     :ensimmainen-haku? true})
        testikohde-id (some #(when (= "Testikohde" (get-in % [::paikkaus/paikkauskohde ::paikkaus/nimi]))
                               (get-in % [::paikkaus/paikkauskohde ::paikkaus/id]))
                            (:paikkaukset paikkaukset))
        paikaukset-paikkauskohteet-filtteri (kutsu-palvelua (:http-palvelin jarjestelma)
                                                            :hae-urakan-paikkauskohteet
                                                            +kayttaja-jvh+
                                                            {::paikkaus/urakka-id urakka-id
                                                             :paikkaus-idt #{testikohde-id}})]
    (is (contains? paikkaukset :paikkaukset))
    (is (contains? paikkaukset :paikkauskohteet))
    (is (not (contains? paikaukset-paikkauskohteet-filtteri :paikkauskohteet)))
    (is (= (count (:paikkaukset paikkaukset)) 12))
    (is (empty? (remove #(= "Testikohde" (get-in % [::paikkaus/paikkauskohde ::paikkaus/nimi])) (:paikkaukset paikaukset-paikkauskohteet-filtteri))))))

(deftest hae-urakan-paikkauskohteet-ei-toimi-ilman-oikeuksia
  (let [urakka-id @oulun-alueurakan-2014-2019-id]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-urakan-paikkauskohteet
                                           +kayttaja-seppo+
                                           {::paikkaus/urakka-id urakka-id})))))

(deftest tr-filtteri-testi
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        paikkaus-kutsu #(kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-urakan-paikkauskohteet
                                        +kayttaja-jvh+
                                        {::paikkaus/urakka-id urakka-id
                                         :tr %})
        urakan-kaikkien-paikkauksine-osoitteet (map ::paikkaus/tierekisteriosoite
                                                    (:paikkaukset (paikkaus-kutsu nil)))
        testaus-template (fn [ns-tr-avaimet tr-avaimet testaus-fn]
                           (let [ryhmittely (group-by (apply juxt ns-tr-avaimet) urakan-kaikkien-paikkauksine-osoitteet)
                                 _ (is (> (count (keys ryhmittely)) 1))
                                 tr-filtteri (zipmap tr-avaimet
                                                     (second (sort (keys ryhmittely))))
                                 kutsun-vastaus (:paikkaukset (paikkaus-kutsu tr-filtteri))
                                 arvojen-lkm (apply + (keep (fn [[avain arvo]]
                                                              (when (testaus-fn avain tr-filtteri)
                                                                (count arvo)))
                                                            ryhmittely))]
                             (is (= (count kutsun-vastaus)
                                    arvojen-lkm))))]
    (testaus-template [::tierekisteri/aosa] [:alkuosa] (fn [[ryhma-aosa] {:keys [alkuosa]}]
                                                         (>= ryhma-aosa alkuosa)))
    (testaus-template [::tierekisteri/aet] [:alkuetaisyys] (fn [[ryhma-aet] {:keys [alkuetaisyys]}]
                                                             (>= ryhma-aet alkuetaisyys)))
    (testaus-template [::tierekisteri/losa] [:loppuosa] (fn [[ryhma-losa] {:keys [loppuosa]}]
                                                          (<= ryhma-losa loppuosa)))
    (testaus-template [::tierekisteri/let] [:loppuetaisyys] (fn [[ryhma-let] {:keys [loppuetaisyys]}]
                                                              (<= ryhma-let loppuetaisyys)))
    (testaus-template [::tierekisteri/aosa ::tierekisteri/aet]
                      [:alkuosa :alkuetaisyys]
                      (fn [[ryhma-aosa ryhma-aet] {:keys [alkuosa alkuetaisyys]}]
                        (or (> ryhma-aosa alkuosa)
                            (and (= ryhma-aosa alkuosa)
                                 (>= ryhma-aet alkuetaisyys)))))
    (testaus-template [::tierekisteri/aosa ::tierekisteri/losa]
                      [:alkuosa :loppuosa]
                      (fn [[ryhma-aosa ryhma-losa] {:keys [alkuosa loppuosa]}]
                        (and (>= ryhma-aosa alkuosa)
                             (<= ryhma-losa loppuosa))))
    (testaus-template [::tierekisteri/aosa ::tierekisteri/let]
                      [:alkuosa :loppuetaisyys]
                      (fn [[ryhma-aosa ryhma-let] {:keys [alkuosa loppuetaisyys]}]
                        (and (>= ryhma-aosa alkuosa)
                             (<= ryhma-let loppuetaisyys))))
    (testaus-template [::tierekisteri/aet ::tierekisteri/losa]
                      [:alkuetaisyys :loppuosa]
                      (fn [[ryhma-aet ryhma-losa] {:keys [alkuetaisyys loppuosa]}]
                        (and (>= ryhma-aet alkuetaisyys)
                             (<= ryhma-losa loppuosa))))
    (testaus-template [::tierekisteri/aet ::tierekisteri/let]
                      [:alkuetaisyys :loppuetaisyys]
                      (fn [[ryhma-aet ryhma-let] {:keys [alkuetaisyys loppuetaisyys]}]
                        (and (>= ryhma-aet alkuetaisyys)
                             (<= ryhma-let loppuetaisyys))))
    (testaus-template [::tierekisteri/losa ::tierekisteri/let]
                      [:loppuosa :loppuetaisyys]
                      (fn [[ryhma-losa ryhma-let] {:keys [loppuosa loppuetaisyys]}]
                        (or (< ryhma-losa loppuosa)
                            (and (= ryhma-losa loppuosa)
                                 (<= ryhma-let loppuetaisyys)))))
    (testaus-template [::tierekisteri/aosa ::tierekisteri/aet ::tierekisteri/losa]
                      [:alkuosa :alkuetaisyys :loppuosa]
                      (fn [[ryhma-aosa ryhma-aet ryhma-losa] {:keys [alkuosa alkuetaisyys loppuosa]}]
                        (and (or (> ryhma-aosa alkuosa)
                                 (and (= ryhma-aosa alkuosa)
                                      (>= ryhma-aet alkuetaisyys)))
                             (<= ryhma-losa loppuosa))))
    (testaus-template [::tierekisteri/aosa ::tierekisteri/aet ::tierekisteri/let]
                      [:alkuosa :alkuetaisyys :loppuetaisyys]
                      (fn [[ryhma-aosa ryhma-aet ryhma-let] {:keys [alkuosa alkuetaisyys loppuetaisyys]}]
                        (and (or (> ryhma-aosa alkuosa)
                                 (and (= ryhma-aosa alkuosa)
                                      (>= ryhma-aet alkuetaisyys)))
                             (<= ryhma-let loppuetaisyys))))
    (testaus-template [::tierekisteri/aosa ::tierekisteri/losa ::tierekisteri/let]
                      [:alkuosa :loppuosa :loppuetaisyys]
                      (fn [[ryhma-aosa ryhma-losa ryhma-let] {:keys [alkuosa loppuosa loppuetaisyys]}]
                        (and (or (< ryhma-losa loppuosa)
                                 (and (= ryhma-losa loppuosa)
                                      (<= ryhma-let loppuetaisyys)))
                             (>= ryhma-aosa alkuosa))))
    (testaus-template [::tierekisteri/aet ::tierekisteri/losa ::tierekisteri/let]
                      [:alkuetaisyys :loppuosa :loppuetaisyys]
                      (fn [[ryhma-aet ryhma-losa ryhma-let] {:keys [alkuetaisyys loppuosa loppuetaisyys]}]
                        (and (or (< ryhma-losa loppuosa)
                                 (and (= ryhma-losa loppuosa)
                                      (<= ryhma-let loppuetaisyys)))
                             (>= ryhma-aet alkuetaisyys))))
    (testaus-template [::tierekisteri/aosa ::tierekisteri/aet ::tierekisteri/losa ::tierekisteri/let]
                      [:alkuosa :alkuetaisyys :loppuosa :loppuetaisyys]
                      (fn [[ryhma-aosa ryhma-aet ryhma-losa ryhma-let] {:keys [alkuosa alkuetaisyys loppuosa loppuetaisyys]}]
                        (and
                          (or (> ryhma-aosa alkuosa)
                              (and (= ryhma-aosa alkuosa)
                                   (>= ryhma-aet alkuetaisyys)))
                          (or (< ryhma-losa loppuosa)
                              (and (= ryhma-losa loppuosa)
                                   (<= ryhma-let loppuetaisyys))))))
    ;; Kera tienumeron testit (ei 100% kattavat)
    (testaus-template [::tierekisteri/tie]
                      [:numero]
                      (fn [[ryhma-tie] {:keys [numero]}]
                        (= ryhma-tie numero)))
    (testaus-template [::tierekisteri/tie ::tierekisteri/losa]
                      [:numero :loppuosa]
                      (fn [[ryhma-tie ryhma-losa] {:keys [numero loppuosa]}]
                        (and
                          (= ryhma-tie numero)
                          (<= ryhma-losa loppuosa))))
    (testaus-template [::tierekisteri/tie ::tierekisteri/let]
                      [:numero :loppuetaisyys]
                      (fn [[ryhma-tie ryhma-let] {:keys [numero loppuetaisyys]}]
                        (and
                          (= ryhma-tie numero)
                          (<= ryhma-let loppuetaisyys))))
    (testaus-template [::tierekisteri/tie ::tierekisteri/aosa ::tierekisteri/losa]
                      [:numero :alkuosa :loppuosa]
                      (fn [[ryhma-tie ryhma-aosa ryhma-losa] {:keys [numero alkuosa loppuosa]}]
                        (and
                          (= ryhma-tie numero)
                          (and (>= ryhma-aosa alkuosa)
                               (<= ryhma-losa loppuosa)))))))

(deftest aikavali-filtteri
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        paikkaus-kutsu #(kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-urakan-paikkauskohteet
                                        +kayttaja-jvh+
                                        {::paikkaus/urakka-id urakka-id
                                         :aikavali %})
        urakan-kaikki-paikkaukset (:paikkaukset (paikkaus-kutsu nil))
        testaus-template (fn [[alkuaika loppuaika :as aikavali]]
                           (let [haetut-paikkaukset-valilta (:paikkaukset (paikkaus-kutsu aikavali))
                                 paikkaukset-valilta (reduce (fn [tulos {paikkauksen-alkuaika ::paikkaus/alkuaika}]
                                                               (cond
                                                                 (or (and (nil? alkuaika)
                                                                          (nil? loppuaika)
                                                                          (not (nil? aikavali)))
                                                                     (nil? aikavali)) (conj tulos paikkauksen-alkuaika)
                                                                 (and (not (nil? alkuaika))
                                                                      (nil? loppuaika)) (if (>= (.getTime paikkauksen-alkuaika)
                                                                                                (.getTime alkuaika))
                                                                                          (conj tulos paikkauksen-alkuaika)
                                                                                          tulos)
                                                                 (and (nil? alkuaika)
                                                                      (not (nil? loppuaika))) (if (<= (.getTime paikkauksen-alkuaika)
                                                                                                      (.getTime loppuaika))
                                                                                                (conj tulos paikkauksen-alkuaika)
                                                                                                tulos)
                                                                 (and (not (nil? alkuaika))
                                                                      (not (nil? loppuaika))) (if (and (>= (.getTime paikkauksen-alkuaika)
                                                                                                           (.getTime alkuaika))
                                                                                                       (<= (.getTime paikkauksen-alkuaika)
                                                                                                           (.getTime loppuaika)))
                                                                                                (conj tulos paikkauksen-alkuaika)
                                                                                                tulos)))
                                                             [] urakan-kaikki-paikkaukset)]
                             (is (= (count haetut-paikkaukset-valilta)
                                    (count paikkaukset-valilta)))))]
    (testaus-template nil)
    (testaus-template [nil nil])
    (testaus-template [(java.util.Date.) nil])
    (testaus-template [nil (java.util.Date.)])
    (testaus-template [(java.util.Date. (- (.getTime (java.util.Date.)) 86400000))
                       (java.util.Date. (+ (.getTime (java.util.Date.)) (* 5 86400000)))])))
