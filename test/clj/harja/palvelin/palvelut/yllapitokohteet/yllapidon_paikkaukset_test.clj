(ns harja.palvelin.palvelut.yllapitokohteet.yllapidon-paikkaukset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.paikkaukset :as paikkaukset]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.domain.paikkaus :as paikkaus]
            [harja.tyokalut.paikkaus-test :refer :all]
            [taoensso.timbre :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]))

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
                                    :hae-urakan-paikkaukset
                                    +kayttaja-jvh+
                                    {::paikkaus/urakka-id urakka-id
                                     :ensimmainen-haku? true})
        vuosi 2020
        kuukausi 1
        paiva 1
        filtterit {::paikkaus/urakka-id urakka-id
                   :aikavali [
                              (c/to-date (t/local-date vuosi kuukausi paiva))
                              (c/to-date (t/local-date vuosi kuukausi paiva))]}
        paikaukset-paikkauskohteet-filtteri (kutsu-palvelua (:http-palvelin jarjestelma)
                                                            :hae-urakan-paikkaukset
                                                            +kayttaja-jvh+
                                                            filtterit)]
    ;; Löytyy paikkauskohteita
    (is (> (count paikkaukset) 0))
    ;; Löytyy paikkauksia
    (is (contains? (first paikkaukset) ::paikkaus/paikkaukset))
    ;; Löytyy enempi kuin nolla paikkaustoteumaa
    (is (> (count (::paikkaus/paikkaukset (first paikkaukset))) 0))
    ;; Annetulla aikavälillä ei löydy mitään
    (is (= 0 (count paikaukset-paikkauskohteet-filtteri)))))

(deftest hae-urakan-paikkauskohteet-ei-toimi-ilman-oikeuksia
  (let [urakka-id @oulun-alueurakan-2014-2019-id]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-urakan-paikkaukset
                                           +kayttaja-seppo+
                                           {::paikkaus/urakka-id urakka-id})))))
(defn- flattaa-tulos
  "Paikkauksia haettaessa ne tulee paikkauskohteen alle. Tässä loopataan paikkauskohteet ja haetaan niistä paikkaukset ja tehdään
  tästä kaikesta nätillä magiikalla vain yksi lista. Ollos hyvä."
  [vastaus]
  (map #(dissoc % ::paikkaus/sijainti)
       (flatten (mapcat
                  (fn [kohde]
                    [(::paikkaus/paikkaukset kohde)])
                  vastaus))))

(deftest tr-filtteri-testi
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        paikkaus-kutsu #(kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-urakan-paikkaukset
                                        +kayttaja-jvh+
                                        {::paikkaus/urakka-id urakka-id
                                         :tr %})
        vastaus (paikkaus-kutsu nil)
        paikkaukset (flattaa-tulos vastaus)
        urakan-kaikkien-paikkauksien-osoitteet (reduce (fn [lista p]
                                                         (conj lista
                                                               {:tie ( ::tierekisteri/tie p)
                                                                :aosa (::tierekisteri/aosa p)
                                                                :losa (::tierekisteri/losa p)
                                                                :aet (::tierekisteri/aet p)
                                                                :let (::tierekisteri/let p)}))
                                                       []
                                                       paikkaukset)
        testaus-template (fn [ns-tr-avaimet tr-avaimet testaus-fn]
                           (let [ryhmittely (group-by (apply juxt ns-tr-avaimet) urakan-kaikkien-paikkauksien-osoitteet)
                                 _ (is (> (count (keys ryhmittely)) 1))
                                 tr-filtteri (zipmap tr-avaimet
                                                     (second (sort (keys ryhmittely))))
                                 kutsun-vastaus-paikkaukset (flattaa-tulos (paikkaus-kutsu tr-filtteri))
                                 arvojen-lkm (apply + (keep (fn [[avain arvo]]
                                                              (when (testaus-fn avain tr-filtteri)
                                                                (count arvo)))
                                                            ryhmittely))]
                             (is (= (count kutsun-vastaus-paikkaukset)
                                    arvojen-lkm))))]

    ;; Varmista, että löytyy tie haulla
    (testaus-template [:tie] [:numero] (fn [[ryhma-numero] {:keys [numero]}] (= ryhma-numero numero)))
    ;; Alkuosan testausta
    (testaus-template [:tie :aosa] [:numero :alkuosa] (fn [[ryhma-numero ryhma-aosa] {:keys [numero alkuosa]}]
                                                        (and
                                                          (= ryhma-numero numero)
                                                          (>= ryhma-aosa alkuosa))))

    (testaus-template [:aet] [:alkuetaisyys] (fn [[ryhma-aet] {:keys [alkuetaisyys]}]
                                                               (>= ryhma-aet alkuetaisyys)))
    (testaus-template [:losa] [:loppuosa] (fn [[ryhma-losa] {:keys [loppuosa]}]
                                                            (<= ryhma-losa loppuosa)))
    (testaus-template [:let] [:loppuetaisyys] (fn [[ryhma-let] {:keys [loppuetaisyys]}]
                                                                (<= ryhma-let loppuetaisyys)))

    (testaus-template [:aosa :aet]
                        [:alkuosa :alkuetaisyys]
                        (fn [[ryhma-aosa ryhma-aet] {:keys [alkuosa alkuetaisyys]}]
                          ;; Joko alkuosa on suurempi kuin annettu ja alkuetäisyys on sama tai suurempi
                          (or (and (> ryhma-aosa alkuosa) (>= ryhma-aet alkuetaisyys))
                              ;; Tai alkuosa on sama kuin annettu ja alkuetäisyys on sama tai suurempi kuin annettu
                              (and (= ryhma-aosa alkuosa) (>= ryhma-aet alkuetaisyys)))))
    (testaus-template [:aosa :losa]
                        [:alkuosa :loppuosa]
                        (fn [[ryhma-aosa ryhma-losa] {:keys [alkuosa loppuosa]}]
                          (and (>= ryhma-aosa alkuosa)
                               (<= ryhma-losa loppuosa))))
    (testaus-template [:aosa :let]
                        [:alkuosa :loppuetaisyys]
                        (fn [[ryhma-aosa ryhma-let] {:keys [alkuosa loppuetaisyys]}]
                          (and (>= ryhma-aosa alkuosa)
                               (<= ryhma-let loppuetaisyys))))
    (testaus-template [:aet :losa]
                        [:alkuetaisyys :loppuosa]
                        (fn [[ryhma-aet ryhma-losa] {:keys [alkuetaisyys loppuosa]}]
                          (and (>= ryhma-aet alkuetaisyys)
                               (<= ryhma-losa loppuosa))))
    (testaus-template [:aet :let]
                        [:alkuetaisyys :loppuetaisyys]
                        (fn [[ryhma-aet ryhma-let] {:keys [alkuetaisyys loppuetaisyys]}]
                          (and (>= ryhma-aet alkuetaisyys)
                               (<= ryhma-let loppuetaisyys))))
    (testaus-template [:losa :let]
                        [:loppuosa :loppuetaisyys]
                        (fn [[ryhma-losa ryhma-let] {:keys [loppuosa loppuetaisyys]}]
                          (or (< ryhma-losa loppuosa)
                              (and (= ryhma-losa loppuosa)
                                   (<= ryhma-let loppuetaisyys)))))

    (testaus-template [:aosa :aet :losa]
                        [:alkuosa :alkuetaisyys :loppuosa]
                        (fn [[ryhma-aosa ryhma-aet ryhma-losa] {:keys [alkuosa alkuetaisyys loppuosa]}]
                          (and (or (and (> ryhma-aosa alkuosa) (>= ryhma-aet alkuetaisyys))
                                   (and (= ryhma-aosa alkuosa) (>= ryhma-aet alkuetaisyys)))
                               (<= ryhma-losa loppuosa))))

    (testaus-template [:aosa :aet :let]
                        [:alkuosa :alkuetaisyys :loppuetaisyys]
                        (fn [[ryhma-aosa ryhma-aet ryhma-let] {:keys [alkuosa alkuetaisyys loppuetaisyys]}]
                          (and (or (and (> ryhma-aosa alkuosa) (>= ryhma-aet alkuetaisyys))
                                   (and (= ryhma-aosa alkuosa) (>= ryhma-aet alkuetaisyys)))
                               (<= ryhma-let loppuetaisyys))))
    (testaus-template [:aosa :losa :let]
                        [:alkuosa :loppuosa :loppuetaisyys]
                        (fn [[ryhma-aosa ryhma-losa ryhma-let] {:keys [alkuosa loppuosa loppuetaisyys]}]
                          (and (or (< ryhma-losa loppuosa)
                                   (and (= ryhma-losa loppuosa)
                                        (<= ryhma-let loppuetaisyys)))
                               (>= ryhma-aosa alkuosa))))

    (testaus-template [:aet :losa :let]
                        [:alkuetaisyys :loppuosa :loppuetaisyys]
                        (fn [[ryhma-aet ryhma-losa ryhma-let] {:keys [alkuetaisyys loppuosa loppuetaisyys]}]
                          (and (or (and (< ryhma-losa loppuosa) (<= ryhma-let loppuetaisyys))
                                   (and (= ryhma-losa loppuosa) (<= ryhma-let loppuetaisyys)))
                               (>= ryhma-aet alkuetaisyys))))
    (testaus-template [:aosa :aet :losa :let]
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
    (testaus-template [:tie]
                        [:numero]
                        (fn [[ryhma-tie] {:keys [numero]}]
                          (= ryhma-tie numero)))

    (testaus-template [:tie :losa]
                        [:numero :loppuosa]
                        (fn [[ryhma-tie ryhma-losa] {:keys [numero loppuosa]}]
                          (and
                            (= ryhma-tie numero)
                            (<= ryhma-losa loppuosa))))

    (testaus-template [:tie :let]
                        [:numero :loppuetaisyys]
                        (fn [[ryhma-tie ryhma-let] {:keys [numero loppuetaisyys]}]
                          (and
                            (= ryhma-tie numero)
                            (<= ryhma-let loppuetaisyys))))

    (testaus-template [:tie :aosa :losa]
                        [:numero :alkuosa :loppuosa]
                        (fn [[ryhma-tie ryhma-aosa ryhma-losa] {:keys [numero alkuosa loppuosa]}]
                          (and
                            (= ryhma-tie numero)
                            (and (>= ryhma-aosa alkuosa)
                                 (<= ryhma-losa loppuosa)))))))

(deftest aikavali-filtteri
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        paikkaus-kutsu #(kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-urakan-paikkaukset
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



