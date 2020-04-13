(ns harja.palvelin.palvelut.yllapitokohteet.paikkaukset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.paikkaukset :as paikkaukset]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [taoensso.timbre :as log]
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


;; Paikkauskustannusten (paikkaustoteuma-taulu) testit
(deftest hae-urakan-paikkauskustannukset-testi
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-paikkausurakan-kustannukset
                                    +kayttaja-jvh+
                                    {::paikkaus/urakka-id urakka-id
                                     :ensimmainen-haku? true})
        testikohde-id (some #(when (= "Testikohde" (get % ::paikkaus/nimi))
                               (get % ::paikkaus/id))
                            (:paikkauskohteet kustannukset))
        testikohteen-kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                                  :hae-paikkausurakan-kustannukset
                                                            +kayttaja-jvh+
                                                            {::paikkaus/urakka-id urakka-id
                                                             :paikkaus-idt #{testikohde-id}})
        testikohteen-kustannus (first (:kustannukset testikohteen-kustannukset))]
    (is (contains? kustannukset :paikkauskohteet))
    (is (contains? kustannukset :kustannukset))
    (is (contains? kustannukset :tyomenetelmat))
    (is (not (contains? kustannukset :teiden-pituudet)))
    (is (not (contains? kustannukset :paikkaukset)))
    (is (not (contains? testikohteen-kustannukset :paikkauskustannukset)))
    (is (= (count kustannukset) 3))

    (is (= (count (:kustannukset kustannukset)) 3))

    ;; Koska ei ensimmäinen haku, palauttaa vain avaimen :kustannukset
    (is (= (count testikohteen-kustannukset) 1))
    (is (= (count (:kustannukset testikohteen-kustannukset)) 1))
    (is (= {:tie 20 :aosa 1 :aet 50 :let 150  :losa 1
            :paikkauskohde {:id 1 :nimi "Testikohde"}
            :tyomenetelma "massapintaus"
            :paikkaustoteuma-id 1 :hinta 3500M
            :paikkaustoteuma-poistettu nil}
           (dissoc testikohteen-kustannus :valmistumispvm :kirjattu)))))

(deftest hae-urakan-paikkauskustannukset-aikavali-testi
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :hae-paikkausurakan-kustannukset
                                     +kayttaja-jvh+
                                     {::paikkaus/urakka-id urakka-id
                                      :ensimmainen-haku? true})
        ohi-aikavalin (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae-paikkausurakan-kustannukset
                                      +kayttaja-jvh+
                                      {::paikkaus/urakka-id urakka-id
                                       :aikavali [(pvm/->pvm "1.1.1992")
                                                  (pvm/->pvm "1.1.1993")]})
        aikavali-osuu (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae-paikkausurakan-kustannukset
                                      +kayttaja-jvh+
                                      {::paikkaus/urakka-id urakka-id
                                       :aikavali [(pvm/eilinen)
                                                  (pvm/nyt)]})]
    (is (= (count (:kustannukset kustannukset))))
    (is (= (count (:kustannukset ohi-aikavalin)) 0))
    (is (= (count (:kustannukset aikavali-osuu)) 3))))

(deftest hae-urakan-paikkauskustannukset-tr-osoitteen-paattely-testi
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :hae-paikkausurakan-kustannukset
                                     +kayttaja-jvh+
                                     {::paikkaus/urakka-id urakka-id
                                      :ensimmainen-haku? true})
        paikkauskohteet (:paikkauskohteet kustannukset)]
    (is (= paikkauskohteet [#:harja.domain.paikkaus{:id 1, :nimi "Testikohde", :ulkoinen-id 666, :tierekisteriosoite {:tie 20, :aosa 1, :aet 1, :losa 3, :let 250}}

                            #:harja.domain.paikkaus{:id 3, :nimi "Testikohde 2", :ulkoinen-id 1337, :tierekisteriosoite {:tie 20, :aosa 3, :aet 200, :losa 3, :let 300}}

                            #:harja.domain.paikkaus{:id 5, :nimi "22 testikohteet", :ulkoinen-id 221337, :tierekisteriosoite {:tie 22, :aosa 3, :aet 1, :losa 5, :let 1}}

                            #:harja.domain.paikkaus{:id 4, :nimi "Testikohde 3", :ulkoinen-id 1338, :tierekisteriosoite {:tie 22, :aosa 4, :aet 1, :losa 5, :let 1}}]))))

(deftest hae-urakan-paikkauskustannukset-tyomenetelmat-testi
  (let [urakka-id @muhoksen-paallystysurakan-id
        kaikki-tyomenetelmat (:kustannukset
                               (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :hae-paikkausurakan-kustannukset
                                               +kayttaja-jvh+
                                               {::paikkaus/urakka-id urakka-id}))
        massapintaukset (:kustannukset
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-paikkausurakan-kustannukset
                                          +kayttaja-jvh+
                                          {::paikkaus/urakka-id urakka-id
                                           :tyomenetelmat #{"massapintaus"}}))
        kuumennuspintaukset (:kustannukset
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-paikkausurakan-kustannukset
                                          +kayttaja-jvh+
                                          {::paikkaus/urakka-id urakka-id
                                           :tyomenetelmat #{"kuumennuspintaus"}}))
        remix-pintaukset (:kustannukset
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-paikkausurakan-kustannukset
                                          +kayttaja-jvh+
                                          {::paikkaus/urakka-id urakka-id
                                           :tyomenetelmat #{"remix-pintaus"}}))]
    (is (= (count kaikki-tyomenetelmat) 4))
    (is (= (count massapintaukset) 2))
    (is (= (count kuumennuspintaukset) 1))
    (is (= (count remix-pintaukset) 1))

    (is (= (reduce + (keep :hinta kaikki-tyomenetelmat)) 6700M))
    (is (= (reduce + (keep :hinta massapintaukset)) 3000M))
    (is (= (reduce + (keep :hinta kuumennuspintaukset)) 1900M))
    (is (= (reduce + (keep :hinta remix-pintaukset)) 1800M))))