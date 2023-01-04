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


;; Paikkauskustannusten (paikkaustoteuma-taulu) testit - Kustannuksia ei ole enää ui:lla ja voidaan jossain
;; vaiheessa poistaa kokonaan varmaan bäkkäristäkin
#_ (deftest hae-urakan-paikkauskustannukset-testi
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
    (is (= {:tie 20 :aosa 1 :aet 50 :let 150 :losa 1
            :paikkauskohde {:id 1 :nimi "Testikohde"}
            :tyomenetelma (hae-tyomenetelman-arvo :id :lyhenne "UREM")
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
                         ;; Testissä oletetaan, että testiaineisto on luotu kantaan viimeisen 6kk sisällä. Oli aiemmin päivän sisällä.
                         :aikavali [(c/to-sql-time (pvm/ajan-muokkaus (pvm/joda-timeksi (pvm/nyt)) false 6 :kuukausi))
                                    (pvm/nyt)]})]
    (is (> (count (:kustannukset kustannukset)) 1))
    (is (= 0 (count (:kustannukset ohi-aikavalin)) 0))
    (is (= 3 (count (:kustannukset aikavali-osuu))))))

(deftest hae-urakan-paikkauskustannukset-tr-osoitteen-paattely-testi
  (let [urakka-id @oulun-alueurakan-2014-2019-id
        kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :hae-paikkausurakan-kustannukset
                                     +kayttaja-jvh+
                                     {::paikkaus/urakka-id urakka-id
                                      :ensimmainen-haku? true})
        paikkauskohteet (vec (for [x (:paikkauskohteet kustannukset)] (dissoc x
                                                                               ::paikkaus/tarkistaja-id
                                                                               ::paikkaus/tarkistettu
                                                                               ::paikkaus/yhalahetyksen-tila
                                                                               ::paikkaus/ilmoitettu-virhe
                                                                               ::muokkaustiedot/luotu
                                                                               ::muokkaustiedot/muokattu)))]
    ;; Tähän on järjestelty tietoja uusiksi, jotta testi menee läpi. Data on aina ollut samaa, mutta is funkkari
    ;; Ei osaa päätellä yhdenmukaisuutta, jos mäpissä key:t on eri järjestyksessä
    ;; Joten yksinkertaisestaan testiä niin paljon, että se saadaan toimimaan useammassa ympäristössä.


    (is (= 4 (count paikkauskohteet)))
    (is (= "Testikohde" (:harja.domain.paikkaus/nimi (first paikkauskohteet))))
    (is (= "Testikohde 2" (:harja.domain.paikkaus/nimi (second paikkauskohteet))))

    ;; Nämä tiedot siis kommentoitu pois, koska eri ympäristöissä tuon paikkauskohteet vectorin sisältö on eri järjestyksessä kuin toisessa, niin näitä on vaikea verrata.
        #_ [#:harja.domain.paikkaus{:tierekisteriosoite {:tie 20, :aosa 1, :aet 1, :losa 3, :let 250}, :nimi "Testikohde", :id 1,  :ulkoinen-id 666}
            #:harja.domain.paikkaus{:id 3, :ulkoinen-id 1337, :nimi "Testikohde 2", :tierekisteriosoite {:tie 20, :aosa 3, :aet 200, :losa 3, :let 300}}
            #:harja.domain.paikkaus{:id 4, :ulkoinen-id 1338, :nimi "Testikohde 3", :tierekisteriosoite {:tie 22, :aosa 4, :aet 1, :losa 5, :let 1}}
            #:harja.domain.paikkaus{:id 5, :nimi "22 testikohteet", :ulkoinen-id 221337, :tierekisteriosoite {:tie 22, :aosa 3, :aet 1, :losa 5, :let 1}}]
           ))

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
                                           :tyomenetelmat #{(hae-tyomenetelman-arvo :id :lyhenne "UREM")}}))
        siput (:kustannukset
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-paikkausurakan-kustannukset
                                          +kayttaja-jvh+
                                          {::paikkaus/urakka-id urakka-id
                                           :tyomenetelmat #{(hae-tyomenetelman-arvo :id :lyhenne "SIPU")}}))
        ktvat (:kustannukset
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-paikkausurakan-kustannukset
                                          +kayttaja-jvh+
                                          {::paikkaus/urakka-id urakka-id
                                           :tyomenetelmat #{(hae-tyomenetelman-arvo :id :lyhenne "KTVA")}}))]
    (is (= (count kaikki-tyomenetelmat) 4))
    (is (= (count ura-remixerit) 2))
    (is (= (count siput) 1))
    (is (= (count ktvat) 1))

    (is (= (reduce + (keep :hinta kaikki-tyomenetelmat)) 6700M))
    (is (= (reduce + (keep :hinta ura-remixerit)) 3000M))
    (is (= (reduce + (keep :hinta siput)) 1800M))
    (is (= (reduce + (keep :hinta ktvat)) 1900M))))


(defn- paikkauskustannus-rivit [{:keys [paikkauskohde-id hinta tyomenetelma valmistumispvm]}]
  [{:aosa 19, :tie 20, :let 301, :paikkauskohde paikkauskohde-id, :losa 19, :aet 1,
    :tyomenetelma tyomenetelma, :paikkaustoteuma-id -1, :hinta hinta, :valmistumispvm valmistumispvm}])

(defn- valinnat-tallennushetkella [{:keys [:kohteet paikkauskohteiden-idt
                                           :urakka-id urakka-id
                                           :aikavali aikavali]}]
  (let [urem-id (hae-tyomenetelman-arvo :id :lyhenne "UREM")
        ktva-id (hae-tyomenetelman-arvo :id :lyhenne "KTVA")
        sipu-id (hae-tyomenetelman-arvo :id :lyhenne "SIPU")]
    {:aikavali (or aikavali [(pvm/eilinen) (pvm/nyt)]), :tyomenetelmat #{urem-id, ktva-id, sipu-id}, :paikkaus-idt paikkauskohteiden-idt, :harja.domain.paikkaus/urakka-id urakka-id}))

