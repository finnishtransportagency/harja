(ns harja.palvelin.palvelut.yllapitokohteet.paikkaukset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.paikkaukset :as paikkaukset]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
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
            :tyomenetelma "UREM"
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
        paikkauskohteet (for [x (:paikkauskohteet kustannukset)] (dissoc x
                                                                         ::paikkaus/tarkistaja-id
                                                                         ::paikkaus/tarkistettu
                                                                         ::paikkaus/tila
                                                                         ::paikkaus/ilmoitettu-virhe
                                                                         ::muokkaustiedot/luotu
                                                                         ::muokkaustiedot/muokattu))]
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
        ura-remixerit (:kustannukset
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-paikkausurakan-kustannukset
                                          +kayttaja-jvh+
                                          {::paikkaus/urakka-id urakka-id
                                           :tyomenetelmat #{"UREM"}}))
        siput (:kustannukset
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-paikkausurakan-kustannukset
                                          +kayttaja-jvh+
                                          {::paikkaus/urakka-id urakka-id
                                           :tyomenetelmat #{"SIPU"}}))
        kivat (:kustannukset
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-paikkausurakan-kustannukset
                                          +kayttaja-jvh+
                                          {::paikkaus/urakka-id urakka-id
                                           :tyomenetelmat #{"KIVA"}}))]
    (is (= (count kaikki-tyomenetelmat) 4))
    (is (= (count ura-remixerit) 2))
    (is (= (count siput) 1))
    (is (= (count kivat) 1))

    (is (= (reduce + (keep :hinta kaikki-tyomenetelmat)) 6700M))
    (is (= (reduce + (keep :hinta ura-remixerit)) 3000M))
    (is (= (reduce + (keep :hinta siput)) 1800M))
    (is (= (reduce + (keep :hinta kivat)) 1900M))))


(defn- paikkauskustannus-rivit [{:keys [paikkauskohde-id hinta tyomenetelma valmistumispvm]}]
  [{:aosa 19, :tie 20, :let 301, :paikkauskohde paikkauskohde-id, :losa 19, :aet 1,
    :tyomenetelma tyomenetelma, :paikkaustoteuma-id -1, :hinta hinta, :valmistumispvm valmistumispvm}])

(defn- valinnat-tallennushetkella [{:keys [:kohteet paikkauskohteiden-idt
                                           :urakka-id urakka-id
                                           :aikavali aikavali]}]
  {:aikavali (or aikavali [(pvm/eilinen) (pvm/nyt)]), :tyomenetelmat #{"UREM", "KIVA", "SIPU"}, :paikkaus-idt paikkauskohteiden-idt, :harja.domain.paikkaus/urakka-id urakka-id})

(deftest tallenna-paikkauskustannukset
  (let [urakka-id @muhoksen-paallystysurakan-id
        paikkauskohde-id (hae-muhoksen-paallystysurakan-testipaikkauskohteen-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-paikkauskustannukset
                                +kayttaja-jvh+
                                {::paikkaus/urakka-id urakka-id
                                 :hakuparametrit (valinnat-tallennushetkella {:kohteet #{paikkauskohde-id}
                                                                              :urakka-id urakka-id})
                                 :rivit (paikkauskustannus-rivit {:paikkauskohde-id paikkauskohde-id
                                                                  :hinta 1234.56M
                                                                  :tyomenetelma "SIPU"
                                                                  :valmistumispvm (pvm/nyt)})})
        vastaus-eri-aikavali-hakuehdoissa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                          :tallenna-paikkauskustannukset
                                                          +kayttaja-jvh+
                                                          {::paikkaus/urakka-id urakka-id
                                                           :hakuparametrit (valinnat-tallennushetkella {:kohteet #{paikkauskohde-id}
                                                                                                        :urakka-id urakka-id
                                                                                                        :aikavali [(pvm/->pvm "1.1.2010")
                                                                                                                   (pvm/->pvm "1.1.2011")]})
                                                           :rivit (paikkauskustannus-rivit {:paikkauskohde-id paikkauskohde-id
                                                                                            :hinta 1234.56M
                                                                                            :tyomenetelma "SIPU"
                                                                                            :valmistumispvm (pvm/nyt)})})
        odotettu {:kustannukset [{:aosa 1, :tie 22, :let 150, :losa 1, :aet 40,
                                  :paikkauskohde {:id 6, :nimi "Testikohde Muhoksen paallystysurakassa"},
                                  :tyomenetelma "UREM", :kirjattu #inst "2020-04-13T03:32:56.827713000-00:00",
                                  :paikkaustoteuma-id 4, :hinta 1700M, :valmistumispvm #inst "2020-04-12T21:00:00.000-00:00",
                                  :paikkaustoteuma-poistettu nil}
                                 {:aosa 1, :tie 22, :let 250, :losa 1, :aet 151,
                                  :paikkauskohde {:id 6, :nimi "Testikohde Muhoksen paallystysurakassa"},
                                  :tyomenetelma "UREM", :kirjattu #inst "2020-04-13T03:32:56.827713000-00:00",
                                  :paikkaustoteuma-id 5, :hinta 1300M, :valmistumispvm #inst "2020-04-12T21:00:00.000-00:00",
                                  :paikkaustoteuma-poistettu nil}
                                 {:aosa 1, :tie 22, :let 150, :losa 1, :aet 40
                                  :paikkauskohde {:id 6, :nimi "Testikohde Muhoksen paallystysurakassa"},
                                  :tyomenetelma "SIPU", :kirjattu #inst "2020-04-13T03:32:56.827713000-00:00",
                                  :paikkaustoteuma-id 6, :hinta 1800M, :valmistumispvm #inst "2020-04-12T21:00:00.000-00:00",
                                  :paikkaustoteuma-poistettu nil}
                                 {:aosa 1, :tie 22, :let 150, :losa 1, :aet 40,
                                  :paikkauskohde {:id 6, :nimi "Testikohde Muhoksen paallystysurakassa"},
                                  :tyomenetelma "KIVA", :kirjattu #inst "2020-04-13T03:32:56.827713000-00:00",
                                  :paikkaustoteuma-id 7, :hinta 1900M, :valmistumispvm #inst "2020-04-12T21:00:00.000-00:00",
                                  :paikkaustoteuma-poistettu nil}
                                 {:aosa 19, :tie 20, :let 301, :losa 19, :aet 1
                                  :paikkauskohde {:id 6, :nimi "Testikohde Muhoksen paallystysurakassa"},
                                  :tyomenetelma "SIPU", :kirjattu #inst "2020-04-13T07:24:38.083264000-00:00",
                                  :paikkaustoteuma-id 8, :hinta 1234.56M, :valmistumispvm #inst "2020-04-12T21:00:00.000-00:00",
                                  :paikkaustoteuma-poistettu nil}]}]

    (is (= (count vastaus) (count odotettu)))
    (is (not-empty (:kustannukset vastaus)))
    (is (not-empty (:kustannukset odotettu)))
    (is (empty? (:kustannukset vastaus-eri-aikavali-hakuehdoissa)))

    ;; ei vertailla aikaleimoja testikantaan luonnin hetkellä
    (is (= (mapv #(dissoc % :kirjattu :valmistumispvm) (:kustannukset vastaus))
           (mapv #(dissoc % :kirjattu :valmistumispvm) (:kustannukset odotettu))))))


;; Ei luoteta frontin lähettämään tietoon urakka-idstä, vaan varmistetaan paikkauskohteen tiedoista tietokannasta
(deftest tallenna-paikkauskustannukset-ei-onnistu-ellei-kohde-kuulu-urakkaan
  (let [urakka-id @oulun-alueurakan-2005-2010-id ;; oikeesti on siis kyseessä Muhoksen urakassa oleva kohde, tämä on epäluotettava tieto frontilta jonka voi käyttäjä halutessaan spoofata
        paikkauskohde-id (hae-muhoksen-paallystysurakan-testipaikkauskohteen-id)
        _ (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                       :tallenna-paikkauskustannukset
                                                       +kayttaja-jvh+
                                                       {::paikkaus/urakka-id urakka-id
                                                        :hakuparametrit (valinnat-tallennushetkella {:kohteet #{paikkauskohde-id}
                                                                                                     :urakka-id urakka-id})
                                                        :rivit (paikkauskustannus-rivit {:paikkauskohde-id paikkauskohde-id
                                                                                         :hinta 1234.56M
                                                                                         :tyomenetelma "SIPU"
                                                                                         :valmistumispvm (pvm/nyt)})})))]))


(deftest tallenna-paikkauskustannukset-tarkista-pakolliset-tiedot
  (let [urakka-id @muhoksen-paallystysurakan-id
        paikkauskohde-id (hae-muhoksen-paallystysurakan-testipaikkauskohteen-id)
        kaikki-pakolliset-tiedot-on (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :tallenna-paikkauskustannukset
                                                    +kayttaja-jvh+
                                                    {::paikkaus/urakka-id urakka-id
                                                     :hakuparametrit (valinnat-tallennushetkella {:kohteet #{paikkauskohde-id}
                                                                                                  :urakka-id urakka-id})
                                                     :rivit (paikkauskustannus-rivit {:paikkauskohde-id paikkauskohde-id
                                                                                      :hinta 1234.56M
                                                                                      :tyomenetelma "SIPU"
                                                                                      :valmistumispvm (pvm/nyt)})})
        _ (is (thrown? AssertionError (kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :tallenna-paikkauskustannukset
                                                 +kayttaja-jvh+
                                                 {::paikkaus/urakka-id urakka-id
                                                  :hakuparametrit (valinnat-tallennushetkella {:kohteet #{paikkauskohde-id}
                                                                                               :urakka-id urakka-id})
                                                  :rivit (paikkauskustannus-rivit {:paikkauskohde-id paikkauskohde-id
                                                                                   :tyomenetelma "SIPU"
                                                                                   :valmistumispvm (pvm/nyt)})})))
        _ (is (thrown? AssertionError (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :tallenna-paikkauskustannukset
                                                      +kayttaja-jvh+
                                                      {::paikkaus/urakka-id urakka-id
                                                       :hakuparametrit (valinnat-tallennushetkella {:kohteet #{paikkauskohde-id}
                                                                                                    :urakka-id urakka-id})
                                                       :rivit (paikkauskustannus-rivit {:paikkauskohde-id paikkauskohde-id
                                                                                        :hinta 123
                                                                                        :valmistumispvm (pvm/nyt)})})))
        _ (is (thrown? AssertionError (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :tallenna-paikkauskustannukset
                                                      +kayttaja-jvh+
                                                      {::paikkaus/urakka-id urakka-id
                                                       :hakuparametrit (valinnat-tallennushetkella {:kohteet #{paikkauskohde-id}
                                                                                                    :urakka-id urakka-id})
                                                       :rivit (paikkauskustannus-rivit {:paikkauskohde-id paikkauskohde-id
                                                                                        :tyomenetelma "KIVA"
                                                                                        :hinta 123})})))]))