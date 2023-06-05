(ns harja.palvelin.palvelut.yllapitokohteet.yllapidon-paikkaukset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.paikkaukset :as paikkaukset]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tierekisteri :as tierekisteri]
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

(deftest levitinpaikkauksen-kaista-ja-pinta-ala-testi
  (let [urakka-id @muhoksen-paallystysurakan-id
        paikkaukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-urakan-paikkaukset
                                    +kayttaja-jvh+
                                    {::paikkaus/urakka-id urakka-id
                                     :ensimmainen-haku? true})
        levitinpaikkauskohde (first (filter #(= (::paikkaus/nimi %) "Levitinpaikkaus") paikkaukset))
        levitinpaikkaus (first (::paikkaus/paikkaukset levitinpaikkauskohde))]

    (is (> (count paikkaukset) 0))
    (is (= (::paikkaus/nimi levitinpaikkauskohde) "Levitinpaikkaus"))
    (is (= (:ajorata levitinpaikkauskohde) 1))
    (is (= (::paikkaus/ajorata levitinpaikkaus) 1))
    (is (= (::paikkaus/kaista levitinpaikkaus) 11))
    (is (= (::paikkaus/leveys levitinpaikkaus) 4))
    (is (= (:suirun-pituus levitinpaikkaus) 1000))
    ;; Levitin paikkauksissa pinta-ala voidaan laskea kertolaskulla, koska koko kaistan levyinen paikkaus
    (is (= (:suirun-pinta-ala levitinpaikkaus) 4000 (* (::paikkaus/leveys levitinpaikkaus)
                                                       (:suirun-pituus levitinpaikkaus))))
    (is (nil? (::paikkaus/pinta-ala levitinpaikkaus)))))

(deftest hae-urakan-paikkauskohteet-ei-toimi-ilman-oikeuksia
  (let [urakka-id @oulun-alueurakan-2014-2019-id]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-urakan-paikkaukset
                                           +kayttaja-seppo+
                                           {::paikkaus/urakka-id urakka-id})))))

(deftest paikkausrivin-pituuslaskenta-toimii-vaikka-osat-erit-kuin-tilatulla-kohteella
  ;; Löytyi urem -paikkauksen pituuden laskennasta bugi. Siellä voi tulla bugi, jos tilatussa paikkauskohteessa on esim tien 20 osat 1-3. Ja kuitenkin jos käy niin että paikataankin esim. ei osia 1-3 vaan osaa 6, ei meidän pituuden laskuri osaa tätä huomioida, vaan sillä on käytössään vain osien 1-3 pituustiedot. Bugi korjataan ja tässä testi joka sen verifioi.

  (let [paikkauskohteet [{:ajorata 0, :harja.domain.muokkaustiedot/muokattu #inst "2023-06-01T11:30:24.238000000-00:00", :harja.domain.paikkaus/id 14, :harja.domain.paikkaus/virhe "{}", :harja.domain.paikkaus/alkupvm #inst "2023-05-31T21:00:00.000-00:00", :aosa 1, :tie 20, :harja.domain.paikkaus/nimi "UREM", :harja.domain.paikkaus/yhalahetyksen-tila nil, :harja.domain.paikkaus/suunniteltu-maara 123M, :harja.domain.paikkaus/tyomenetelma 8, :harja.domain.paikkaus/tilattupvm #inst "2023-05-31T21:00:00.000-00:00", :toteutus-alkuaika #inst "2020-01-01T08:00:00.000000000-00:00", :losa 3, :harja.domain.muokkaustiedot/luotu #inst "2023-06-01T11:30:24.237000000-00:00", :harja.domain.paikkaus/lisatiedot "123", :harja.domain.paikkaus/suunniteltu-hinta 13M, :harja.domain.paikkaus/ulkoinen-id 123, :harja.domain.paikkaus/tarkistettu nil, :harja.domain.paikkaus/loppupvm #inst "2023-05-31T21:00:00.000-00:00", :harja.domain.paikkaus/ilmoitettu-virhe nil, :harja.domain.paikkaus/paikkauskohteen-tila "tilattu", :harja.domain.paikkaus/urakka-id 7, :harja.domain.paikkaus/paikkaukset (list {:harja.domain.tierekisteri/losa 6, :harja.domain.paikkaus/id 311, :harja.domain.tierekisteri/aet 0, :harja.domain.paikkaus/ajourat [1], :harja.domain.paikkaus/nimi "UREM", :harja.domain.paikkaus/reunat nil, :harja.domain.paikkaus/alkuaika #inst "2020-01-01T08:00:00.000-00:00", :harja.domain.paikkaus/tyomenetelma 8, :harja.domain.paikkaus/kpl nil, :harja.domain.paikkaus/paikkauskohde-id 14, :harja.domain.tierekisteri/aosa 6, :harja.domain.tierekisteri/tie 20, :harja.domain.paikkaus/ajorata 1, :harja.domain.paikkaus/keskisaumat nil, :harja.domain.paikkaus/raekoko 5, :harja.domain.paikkaus/sijainti "{\"type\":\"MultiLineString\",\"coordinates\":[[[369653.187,6678203.477],[369638.065,6678212.077],[369585.175,6678239.951],[369582.713,6678241.214],[369564.381968914,6678249.338985403]]]}", :harja.domain.paikkaus/kaista nil, :harja.domain.paikkaus/leveys 5.0, :harja.domain.paikkaus/massamaara 12.5, :harja.domain.paikkaus/lahde "excel", :harja.domain.paikkaus/juoksumetri nil, :harja.domain.paikkaus/tienkohta-id 309, :harja.domain.paikkaus/pinta-ala 500.0, :harja.domain.paikkaus/massatyyppi "AB, Asfalttibetoni", :harja.domain.paikkaus/yksikko "jm", :harja.domain.paikkaus/ajouravalit nil, :harja.domain.paikkaus/kuulamylly "AN5", :harja.domain.paikkaus/loppuaika #inst "2020-01-01T08:15:00.000-00:00", :harja.domain.tierekisteri/let 100, :harja.domain.paikkaus/massamenekki 25}), :harja.domain.paikkaus/yksikko "jm", :toteutus-loppuaika #inst "2020-01-01T08:15:00.000000000-00:00"}]
        db (luo-testitietokanta)
        pituus-mukana (paikkaukset/kasittele-koko-ja-sijainti db paikkauskohteet)]
    (is (= 100 (:suirun-pituus (first (::paikkaus/paikkaukset (first pituus-mukana))))) "Pituuden oltava sata metriä")))

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



