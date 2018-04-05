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


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-urakan-paikkauskohteet-testi
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        paikkaukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-urakan-paikkauskohteet
                                    +kayttaja-jvh+
                                    {::paikkaus/urakka-id urakka-id})
        paikkaukset-tr-filtteri (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-urakan-paikkauskohteet
                                                +kayttaja-jvh+
                                                {::paikkaus/urakka-id urakka-id
                                                 :tr {:numero 20, :alkuosa 1, :alkuetaisyys 1, :loppuosa 1}})
        testikohde-id (some #(when (= "Testikohde" (get-in % [::paikkaus/paikkauskohde ::paikkaus/nimi]))
                               (get-in % [::paikkaus/paikkauskohde ::paikkaus/id]))
                            paikkaukset)
        paikaukset-paikkauskohteet-filtteri (kutsu-palvelua (:http-palvelin jarjestelma)
                                                            :hae-urakan-paikkauskohteet
                                                            +kayttaja-jvh+
                                                            {::paikkaus/urakka-id urakka-id
                                                             :paikkaus-idt #{testikohde-id}})]
    (is (= (count paikkaukset) 4))
    (is (= (count paikkaukset-tr-filtteri) 2))
    (is (empty? (remove #(= "Testikohde" (get-in % [::paikkaus/paikkauskohde ::paikkaus/nimi])) paikaukset-paikkauskohteet-filtteri)))))

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
                                                    (paikkaus-kutsu nil))
        testaus-homma (fn [ns-tr-avaimet tr-avaimet testaus-fn]
                        (let [ryhmittely (group-by (apply juxt ns-tr-avaimet) urakan-kaikkien-paikkauksine-osoitteet)
                              _ (is (> (count (keys ryhmittely)) 1))
                              tr-filtteri (zipmap tr-avaimet
                                                  (second (sort (keys ryhmittely))))
                              _ (println "TR FILTTERI: " (pr-str tr-filtteri))
                              _ (println "RYHMITTLEY: ")
                              _ (clojure.pprint/pprint ryhmittely)
                              kutsun-vastaus (paikkaus-kutsu tr-filtteri)
                              arvojen-lkm (apply + (keep (fn [[avain arvo]]
                                                           (when (testaus-fn avain tr-filtteri)
                                                             (count arvo)))
                                                         ryhmittely))]
                          ;(println "KUTSUN VASTAUS")
                          ;(clojure.pprint/pprint kutsun-vastaus)
                          (println "KUTSUN LUKUMÄÄRÄ: " (pr-str (count kutsun-vastaus)))
                          (println "ARVOJEN LUKUMÄÄRÄ: " (pr-str arvojen-lkm))
                          (is (= (count kutsun-vastaus)
                                 arvojen-lkm))))]
    (testaus-homma [::tierekisteri/aosa] [:alkuosa] (fn [[ryhma-aosa] {:keys [alkuosa]}]
                                                      (>= ryhma-aosa alkuosa)))
    (testaus-homma [::tierekisteri/aet] [:alkuetaisyys] (fn [[ryhma-aet] {:keys [alkuetaisyys]}]
                                                          (>= ryhma-aet alkuetaisyys)))
    (testaus-homma [::tierekisteri/losa] [:loppuosa] (fn [[ryhma-losa] {:keys [loppuosa]}]
                                                       (<= ryhma-losa loppuosa)))
    (testaus-homma [::tierekisteri/let] [:loppuetaisyys] (fn [[ryhma-let] {:keys [loppuetaisyys]}]
                                                           (<= ryhma-let loppuetaisyys)))
    (testaus-homma [::tierekisteri/aosa ::tierekisteri/aet]
                   [:alkuosa :alkuetaisyys]
                   (fn [[ryhma-aosa ryhma-aet] {:keys [alkuosa alkuetaisyys]}]
                     (or (> ryhma-aosa alkuosa)
                         (and (= ryhma-aosa alkuosa)
                              (>= ryhma-aet alkuetaisyys)))))
    (testaus-homma [::tierekisteri/aosa ::tierekisteri/losa]
                   [:alkuosa :loppuosa]
                   (fn [[ryhma-aosa ryhma-losa] {:keys [alkuosa loppuosa]}]
                     (and (>= ryhma-aosa alkuosa)
                          (<= ryhma-losa loppuosa))))
    (testaus-homma [::tierekisteri/aosa ::tierekisteri/let]
                   [:alkuosa :loppuetaisyys]
                   (fn [[ryhma-aosa ryhma-let] {:keys [alkuosa loppuetaisyys]}]
                     (and (>= ryhma-aosa alkuosa)
                          (<= ryhma-let loppuetaisyys))))
    (testaus-homma [::tierekisteri/aet ::tierekisteri/losa]
                   [:alkuetaisyys :loppuosa]
                   (fn [[ryhma-aet ryhma-losa] {:keys [alkuetaisyys loppuosa]}]
                     (and (>= ryhma-aet alkuetaisyys)
                          (<= ryhma-losa loppuosa))))
    (testaus-homma [::tierekisteri/aet ::tierekisteri/let]
                   [:alkuetaisyys :loppuetaisyys]
                   (fn [[ryhma-aet ryhma-let] {:keys [alkuetaisyys loppuetaisyys]}]
                     (and (>= ryhma-aet alkuetaisyys)
                          (<= ryhma-let loppuetaisyys))))
    (testaus-homma [::tierekisteri/losa ::tierekisteri/let]
                   [:loppuosa :loppuetaisyys]
                   (fn [[ryhma-losa ryhma-let] {:keys [loppuosa loppuetaisyys]}]
                     (or (< ryhma-losa loppuosa)
                         (and (= ryhma-losa loppuosa)
                              (<= ryhma-let loppuetaisyys)))))
    (testaus-homma [::tierekisteri/aosa ::tierekisteri/aet ::tierekisteri/losa]
                   [:alkuosa :alkuetaisyys :loppuosa]
                   (fn [[ryhma-aosa ryhma-aet ryhma-losa] {:keys [alkuosa alkuetaisyys loppuosa]}]
                     (and (or (> ryhma-aosa alkuosa)
                              (and (= ryhma-aosa alkuosa)
                                   (>= ryhma-aet alkuetaisyys)))
                          (<= ryhma-losa loppuosa))))
    (testaus-homma [::tierekisteri/aosa ::tierekisteri/aet ::tierekisteri/let]
                   [:alkuosa :alkuetaisyys :loppuetaisyys]
                   (fn [[ryhma-aosa ryhma-aet ryhma-let] {:keys [alkuosa alkuetaisyys loppuetaisyys]}]
                     (and (or (> ryhma-aosa alkuosa)
                              (and (= ryhma-aosa alkuosa)
                                   (>= ryhma-aet alkuetaisyys)))
                          (<= ryhma-let loppuetaisyys))))
    (testaus-homma [::tierekisteri/aosa ::tierekisteri/losa ::tierekisteri/let]
                   [:alkuosa :loppuosa :loppuetaisyys]
                   (fn [[ryhma-aosa ryhma-losa ryhma-let] {:keys [alkuosa loppuosa loppuetaisyys]}]
                     (and (or (< ryhma-losa loppuosa)
                              (and (= ryhma-losa loppuosa)
                                   (<= ryhma-let loppuetaisyys)))
                          (>= ryhma-aosa alkuosa))))
    (testaus-homma [::tierekisteri/aet ::tierekisteri/losa ::tierekisteri/let]
                   [:alkuetaisyys :loppuosa :loppuetaisyys]
                   (fn [[ryhma-aet ryhma-losa ryhma-let] {:keys [alkuetaisyys loppuosa loppuetaisyys]}]
                     (and (or (< ryhma-losa loppuosa)
                              (and (= ryhma-losa loppuosa)
                                   (<= ryhma-let loppuetaisyys)))
                          (>= ryhma-aet alkuetaisyys))))
    (testaus-homma [::tierekisteri/aosa ::tierekisteri/aet ::tierekisteri/losa ::tierekisteri/let]
                   [:alkuosa :alkuetaisyys :loppuosa :loppuetaisyys]
                   (fn [[ryhma-aosa ryhma-aet ryhma-losa ryhma-let] {:keys [alkuosa alkuetaisyys loppuosa loppuetaisyys]}]
                     (and
                       (or (> ryhma-aosa alkuosa)
                           (and (= ryhma-aosa alkuosa)
                                (>= ryhma-aet alkuetaisyys)))
                       (or (< ryhma-losa loppuosa)
                              (and (= ryhma-losa loppuosa)
                                   (<= ryhma-let loppuetaisyys))))))))