(ns harja.palvelin.palvelut.budjettisuunnittelu-test
  (:require [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as pois-kytketyt-ominaisuudet]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (luo-testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet (component/using
                                                      (pois-kytketyt-ominaisuudet/->PoisKytketytOminaisuudet #{})
                                                      [:http-palvelin])
                        :budjetoidut-tyot (component/using
                                            (bs/->Budjettisuunnittelu)
                                            [:http-palvelin :db :pois-kytketyt-ominaisuudet])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(s/def ::aika-kuukaudella-ivalon-urakalle
  (s/with-gen ::bs/aika
              (fn []
                (gen/fmap (fn [_]
                            (let [aloitus-vuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))
                                  vuosi (rand-nth (range aloitus-vuosi (+ aloitus-vuosi 6)))
                                  kk (cond
                                       (= vuosi aloitus-vuosi) (rand-nth (range 10 13))
                                       (= vuosi (+ aloitus-vuosi 5)) (rand-nth (range 1 10))
                                       :else (rand-nth (range 1 13)))]
                              {:vuosi vuosi
                               :kuukausi kk}))
                          (gen/int)))))

(s/def ::aika-vuodella-ivalon-urakalle
  (s/with-gen ::bs/aika
              (fn []
                (gen/fmap (fn [_]
                            (let [aloitus-vuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))
                                  vuosi (rand-nth (range aloitus-vuosi (+ aloitus-vuosi 6)))
                                  kk (cond
                                       (= vuosi aloitus-vuosi) (rand-nth (range 10 13))
                                       (= vuosi (+ aloitus-vuosi 5)) (rand-nth (range 1 10))
                                       :else (rand-nth (range 1 13)))]
                              {:vuosi vuosi
                               :kuukausi kk}))
                          (gen/int)))))



:budjettitavoite
:tallenna-budjettitavoite

;; sampoa varten likaiseksi merkitseminen
;; oikeustarkistukset
;; Datan päivitys
(deftest budjetoidut-tyot-haku
  (let [{urakka-id :id urakan-alkupvm :alkupvm} (first (q-map "SELECT id, alkupvm FROM urakka WHERE nimi='Pellon MHU testiurakka (3. hoitovuosi)';"))
        budjetoidut-tyot (bs/hae-urakan-budjetoidut-tyot (:db jarjestelma) +kayttaja-jvh+ {:urakka-id urakka-id})
        testaa-ajat (fn [tehtavat toimenpide]
                      (let [tehtavat-vuosittain (group-by :vuosi tehtavat)]
                        (is (= (ffirst (sort-by key tehtavat-vuosittain))
                               (pvm/vuosi urakan-alkupvm))
                            (str "Urakan ensimmäinen vuosi pitäisi olla " (pvm/vuosi urakan-alkupvm)
                                 ", mutta se oli " (ffirst (sort-by key tehtavat-vuosittain))))
                        (is (= 6 (count tehtavat-vuosittain)) (str "Tehtäviä pitäisi olla merkattuna kuudelle kalenterivuodelle. Toimenpiteelle "
                                                                   toimenpide " löytyi " (count tehtavat-vuosittain) " vuodelle"))
                        (is (= #{10 11 12}
                               (into #{}
                                     (map :kuukausi
                                          (val (first (sort-by key tehtavat-vuosittain))))))
                            "Ensimmäisellä kalenterivuodella pitäisi olla arvoja vain kolmelle viimeiselle kuukaudelle")
                        (doseq [[_ tehtavat-vuodelle] (butlast (rest (sort-by key tehtavat-vuosittain)))]
                          (is (= (into #{} (range 1 13))
                                 (into #{} (map :kuukausi tehtavat-vuodelle))))
                          "Muille kalenterivuosille pitäisi arvo olla joka kuulle")
                        (is (= (into #{} (range 1 10))
                               (into #{}
                                     (map :kuukausi
                                          (val (last (sort-by key tehtavat-vuosittain))))))
                            "Viimeisellä kalenterivuodella pitäisi olla arvoja vain tammikuusta syysykuuhun")))]
    (testing "Kiinteähintaiset työt on oikein"
      (let [kiinteahintaiset-tyot-toimenpiteittain (group-by :toimenpide (:kiinteahintaiset-tyot budjetoidut-tyot))]
        (doseq [[toimenpide tehtavat] kiinteahintaiset-tyot-toimenpiteittain]
          (is (nil? (some (fn [{:keys [tehtava tehtavaryhma]}]
                            (when (or tehtava tehtavaryhma)
                              true))
                          tehtavat))
              "Kiinteähintainen tehtävä tulisi olla tallennettuna vain toimenpidetasolle")
          (is (nil? (some (fn [{:keys [summa]}]
                            (when-not (number? summa)
                              true))
                          tehtavat))
              "Kaikille summille pitäisi olla jokin arvo")
          (testaa-ajat tehtavat toimenpide))))
    (testing "Kustannusarvioidut työt on oikein"
      (let [kustannusarvioidut-tyot-toimenpiteittain (group-by :toimenpide-avain (:kustannusarvioidut-tyot budjetoidut-tyot))]
        (doseq [[toimenpide-avain tehtavat] kustannusarvioidut-tyot-toimenpiteittain
                :let [ryhmiteltyna (group-by (juxt :tyyppi :tehtava-nimi) tehtavat)]]
          (case toimenpide-avain
            :paallystepaikkaukset (is (= ryhmiteltyna {}))
            :mhu-yllapito (do
                            (is (= (keys ryhmiteltyna) [["muut-rahavaraukset" nil]]))
                            (testaa-ajat tehtavat toimenpide-avain))
            :talvihoito (do
                          (is (= (into #{} (keys ryhmiteltyna))
                                 #{["vahinkojen-korjaukset" nil]
                                   ["akillinen-hoitotyo" nil]}))
                          (doseq [[_ tehtavat] ryhmiteltyna]
                            (testaa-ajat tehtavat toimenpide-avain)))
            :liikenneympariston-hoito (do
                                        (is (= (into #{} (keys ryhmiteltyna))
                                               #{["laskutettava-tyo" nil] ;; Tässä testidatassa on siis painettu päälle "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle"
                                                 ["vahinkojen-korjaukset" nil]
                                                 ["akillinen-hoitotyo" nil]}))
                                        (doseq [[_ tehtavat] ryhmiteltyna]
                                          (testaa-ajat tehtavat toimenpide-avain)))
            :sorateiden-hoito (do
                                (is (= (into #{} (keys ryhmiteltyna))
                                       #{["vahinkojen-korjaukset" nil]
                                         ["akillinen-hoitotyo" nil]}))
                                (doseq [[_ tehtavat] ryhmiteltyna]
                                  (testaa-ajat tehtavat toimenpide-avain)))
            :mhu-korvausinvestointi (is (= ryhmiteltyna {}))
            :mhu-johto (do
                         (is (= (into #{} (keys ryhmiteltyna))
                                #{["laskutettava-tyo" nil]
                                  ["laskutettava-tyo" "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne."]
                                  ["laskutettava-tyo" "Hoitourakan työnjohto"]}))
                         (doseq [[_ tehtavat] ryhmiteltyna]
                           (testaa-ajat tehtavat toimenpide-avain)))))))
    (testing "Johto ja hallintokorvaukset ovat oikein"
      (let [johto-ja-hallintokorvaukset (group-by :toimenkuva (:johto-ja-hallintokorvaukset budjetoidut-tyot))]
        (doseq [[toimenkuva tiedot] johto-ja-hallintokorvaukset]
          (case toimenkuva
            "hankintavastaava" (is (= (into #{}
                                            (mapcat (fn [hoitokausi]
                                                      [[hoitokausi :molemmat]])
                                                    (range 0 6)))
                                      (into #{} (keys (group-by (juxt :hoitokausi :maksukausi) tiedot)))))
            "sopimusvastaava" (is (= (into #{}
                                           (mapcat (fn [hoitokausi]
                                                     [[hoitokausi :molemmat]])
                                                   (range 1 6)))
                                     (into #{} (keys (group-by (juxt :hoitokausi :maksukausi) tiedot)))))
            "vastuunalainen työnjohtaja" (is (= (into #{}
                                                      (mapcat (fn [hoitokausi]
                                                                [[hoitokausi :molemmat]])
                                                              (range 1 6)))
                                                (into #{} (keys (group-by (juxt :hoitokausi :maksukausi) tiedot)))))
            "päätoiminen apulainen" (is (= (into #{}
                                                 (mapcat (fn [hoitokausi]
                                                           [[hoitokausi :talvi]
                                                            [hoitokausi :kesa]])
                                                         (range 1 6)))
                                           (into #{} (keys (group-by (juxt :hoitokausi :maksukausi) tiedot)))))
            "apulainen/työnjohtaja" (is (= (into #{}
                                                 (mapcat (fn [hoitokausi]
                                                           [[hoitokausi :talvi]
                                                            [hoitokausi :kesa]])
                                                         (range 1 6)))
                                           (into #{} (keys (group-by (juxt :hoitokausi :maksukausi) tiedot)))))
            "viherhoidosta vastaava henkilö" (is (= (into #{}
                                                          (mapcat (fn [hoitokausi]
                                                                    [[hoitokausi :molemmat]])
                                                                  (range 1 6)))
                                                    (into #{} (keys (group-by (juxt :hoitokausi :maksukausi) tiedot)))))
            "harjoittelija" (is (= (into #{}
                                         (mapcat (fn [hoitokausi]
                                                   [[hoitokausi :molemmat]])
                                                 (range 1 6)))
                                   (into #{} (keys (group-by (juxt :hoitokausi :maksukausi) tiedot)))))))))))

(deftest tallenna-kiinteahintaiset-tyot
  (let [urakka-id (hae-ivalon-maanteiden-hoitourakan-id)
        tallennettavat-tyot [{:urakka-id urakka-id
                              :toimenpide-avain :paallystepaikkaukset
                              ;; Ajoille tämmöinen hirvitys, että saadaan generoitua random dataa, mutta siten,
                              ;; että lopulta kaikkien aikojen vuosi-kuukausi yhdistelmä on uniikki
                              :ajat (mapv first
                                          (vals (group-by (juxt :vuosi :kuukausi)
                                                          (gen/sample (s/gen ::aika-kuukaudella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :toimenpide-avain :mhu-yllapito
                              :ajat (mapv first
                                          (vals (group-by (juxt :vuosi :kuukausi)
                                                          (gen/sample (s/gen ::aika-kuukaudella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :toimenpide-avain :talvihoito
                              :ajat (mapv first
                                          (vals (group-by (juxt :vuosi :kuukausi)
                                                          (gen/sample (s/gen ::aika-kuukaudella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :toimenpide-avain :liikenneympariston-hoito
                              :ajat (mapv first
                                          (vals (group-by (juxt :vuosi :kuukausi)
                                                          (gen/sample (s/gen ::aika-kuukaudella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :toimenpide-avain :sorateiden-hoito
                              :ajat (mapv first
                                          (vals (group-by (juxt :vuosi :kuukausi)
                                                          (gen/sample (s/gen ::aika-kuukaudella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :toimenpide-avain :mhu-korvausinvestointi
                              :ajat (mapv first
                                          (vals (group-by (juxt :vuosi :kuukausi)
                                                          (gen/sample (s/gen ::aika-kuukaudella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}]]
    (testing "Tallennus onnistuu"
      (doseq [tyo tallennettavat-tyot]
        (let [vastaus (bs/tallenna-kiinteahintaiset-tyot (:db jarjestelma) +kayttaja-jvh+ tyo)]
          (is (:onnistui? vastaus) (str "Tallentaminen toimenpiteelle " (:toimenpide-avain tyo) " epäonnistui.")))))
    (testing "Data kannassa on oikein"
      (doseq [{:keys [toimenpide-avain urakka-id ajat summa]} tallennettavat-tyot]
        (let [toimenpidekoodi (case toimenpide-avain
                                 :paallystepaikkaukset "20107"
                                 :mhu-yllapito "20191"
                                 :talvihoito "23104"
                                 :liikenneympariston-hoito "23116"
                                 :sorateiden-hoito "23124"
                                 :mhu-korvausinvestointi "14301"
                                 :mhu-johto "23151")
              toimenpide-id (ffirst (q (str "SELECT id FROM toimenpidekoodi WHERE taso = 3 AND koodi = '" toimenpidekoodi "';")))
              toimenpideinstanssi (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " AND toimenpide = " toimenpide-id ";")))
              data-kannassa (q-map (str "SELECT vuosi, kuukausi, summa, luotu, toimenpideinstanssi FROM kiinteahintainen_tyo WHERE toimenpideinstanssi=" toimenpideinstanssi ";"))]
          (is (every? :luotu data-kannassa) "Luomisaika ei tallennettu")
          (is (every? #(= (float (:summa %))
                          (float summa))
                      data-kannassa)
              (str "Summa ei tallentunut oikein toimenpiteelle " toimenpide-avain))
          (is (= (sort-by (juxt :vuosi :kuukausi) (map #(select-keys % #{:vuosi :kuukausi}) data-kannassa))
                 (sort-by (juxt :vuosi :kuukausi) ajat))
              (str "Ajat eivät tallentuneet kantaan oikein toimenpiteelle " toimenpide-avain)))))))

(deftest tallenna-kustannusarvioitu-tyo
  (let [urakka-id (hae-ivalon-maanteiden-hoitourakan-id)
        tallennettavat-tyot [{:urakka-id urakka-id
                              :tallennettavat-asiat #{:toimenpiteen-maaramitattavat-tyot}
                              :toimenpide-avain :paallystepaikkaukset
                              ;; Ajoille tämmöinen hirvitys, että saadaan generoitua random dataa, mutta siten,
                              ;; että lopulta kaikkien aikojen vuosi on uniikki
                              :ajat (mapv first
                                          (vals (group-by :vuosi
                                                          (gen/sample (s/gen ::aika-vuodella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :tallennettavat-asiat #{:rahavaraus-lupaukseen-1
                                                      :toimenpiteen-maaramitattavat-tyot}
                              :toimenpide-avain :mhu-yllapito
                              :ajat (mapv first
                                          (vals (group-by :vuosi
                                                          (gen/sample (s/gen ::aika-vuodella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :tallennettavat-asiat #{:kolmansien-osapuolten-aiheuttamat-vahingot
                                                      :akilliset-hoitotyot
                                                      :toimenpiteen-maaramitattavat-tyot}
                              :toimenpide-avain :talvihoito
                              :ajat (mapv first
                                          (vals (group-by :vuosi
                                                          (gen/sample (s/gen ::aika-vuodella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :tallennettavat-asiat #{:kolmansien-osapuolten-aiheuttamat-vahingot
                                                      :akilliset-hoitotyot
                                                      :toimenpiteen-maaramitattavat-tyot}
                              :toimenpide-avain :liikenneympariston-hoito
                              :ajat (mapv first
                                          (vals (group-by :vuosi
                                                          (gen/sample (s/gen ::aika-vuodella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :tallennettavat-asiat #{:kolmansien-osapuolten-aiheuttamat-vahingot
                                                      :akilliset-hoitotyot
                                                      :toimenpiteen-maaramitattavat-tyot}
                              :toimenpide-avain :sorateiden-hoito
                              :ajat (mapv first
                                          (vals (group-by :vuosi
                                                          (gen/sample (s/gen ::aika-vuodella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :tallennettavat-asiat #{:toimenpiteen-maaramitattavat-tyot}
                              :toimenpide-avain :mhu-korvausinvestointi
                              :ajat (mapv first
                                          (vals (group-by :vuosi
                                                          (gen/sample (s/gen ::aika-vuodella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}
                             {:urakka-id urakka-id
                              :tallennettavat-asiat #{:hoidonjohtopalkkio
                                                      :toimistokulut
                                                      :erillishankinnat}
                              :toimenpide-avain :mhu-johto
                              :ajat (mapv first
                                          (vals (group-by :vuosi
                                                          (gen/sample (s/gen ::aika-vuodella-ivalon-urakalle)))))
                              :summa (gen/generate (s/gen ::bs/summa))}]]
    (testing "Tallennus onnistuu"
      (doseq [tyo tallennettavat-tyot
              :let [tallennettavat-asiat (:tallennettavat-asiat tyo)]
              tallennettava-asia tallennettavat-asiat
              :let [tyo (-> tyo (dissoc :tallennettavat-asiat) (assoc :tallennettava-asia tallennettava-asia))]]
        (let [vastaus (bs/tallenna-kustannusarvioitu-tyo (:db jarjestelma) +kayttaja-jvh+ tyo)]
          (is (:onnistui? vastaus) (str "Tallentaminen toimenpiteelle " (:toimenpide-avain tyo) " epäonnistui.")))))
    (testing "Data kannassa on oikein"
      (doseq [{:keys [toimenpide-avain urakka-id ajat summa tallennettavat-asiat]} tallennettavat-tyot]
        (let [toimenpidekoodi (case toimenpide-avain
                                :paallystepaikkaukset "20107"
                                :mhu-yllapito "20191"
                                :talvihoito "23104"
                                :liikenneympariston-hoito "23116"
                                :sorateiden-hoito "23124"
                                :mhu-korvausinvestointi "14301"
                                :mhu-johto "23151")
              toimenpide-id (ffirst (q (str "SELECT id FROM toimenpidekoodi WHERE taso = 3 AND koodi = '" toimenpidekoodi "';")))
              toimenpideinstanssi (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " AND toimenpide = " toimenpide-id ";")))
              data-kannassa (q-map (str "SELECT kt.vuosi, kt.kuukausi, kt.summa, kt.luotu, kt.tyyppi, tk.nimi AS tehtava, tr.nimi AS tehtavaryhma
                                         FROM kustannusarvioitu_tyo kt
                                           LEFT JOIN toimenpidekoodi tk ON tk.id = kt.tehtava
                                           LEFT JOIN tehtavaryhma tr ON tr.id = kt.tehtavaryhma
                                         WHERE kt.toimenpideinstanssi=" toimenpideinstanssi ";"))]
          (is (every? :luotu data-kannassa) "Luomisaika ei tallennettu")
          (is (every? #(= (float (:summa %))
                          (float summa))
                      data-kannassa)
              (str "Summa ei tallentunut oikein toimenpiteelle " toimenpide-avain))
          (doseq [tallennettava-asia tallennettavat-asiat]
            (let [tallennetun-asian-data? (fn [{:keys [tyyppi tehtava tehtavaryhma]}]
                                            (case tallennettava-asia
                                              :hoidonjohtopalkkio (and (= tyyppi "laskutettava-tyo")
                                                                       (= tehtava "Hoitourakan työnjohto")
                                                                       (nil? tehtavaryhma))
                                              :toimistokulut (and (= tyyppi "laskutettava-tyo")
                                                                  (= tehtava "Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.")
                                                                  (nil? tehtavaryhma))
                                              :erillishankinnat (and (= tyyppi "laskutettava-tyo")
                                                                     (nil? tehtava)
                                                                     (= tehtavaryhma "ERILLISHANKINNAT"))
                                              :rahavaraus-lupaukseen-1 (and (= tyyppi "muut-rahavaraukset")
                                                                            (nil? tehtava)
                                                                            (= tehtavaryhma "TILAAJAN RAHAVARAUS"))
                                              :kolmansien-osapuolten-aiheuttamat-vahingot (and (= tyyppi "vahinkojen-korjaukset")
                                                                                               (= tehtava "Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen")
                                                                                               (nil? tehtavaryhma))
                                              :akilliset-hoitotyot (and (= tyyppi "akillinen-hoitotyo")
                                                                        (= tehtava "Äkillinen hoitotyö")
                                                                        (nil? tehtavaryhma))
                                              :toimenpiteen-maaramitattavat-tyot (and (= tyyppi "laskutettava-tyo")
                                                                                      (nil? tehtava)
                                                                                      (nil? tehtavaryhma))))
                  tallennetun-asian-data-kannassa (filter tallennetun-asian-data? data-kannassa)]
              (is (= (sort-by (juxt :vuosi :kuukausi) (map #(select-keys % #{:vuosi :kuukausi}) tallennetun-asian-data-kannassa))
                     (sort-by (juxt :vuosi :kuukausi) ajat))
                  (str "Ajat eivät tallentuneet kantaan oikein toimenpiteelle: " toimenpide-avain " ja tallennettavalle asialle: " tallennettava-asia)))))))))

(deftest tallenna-johto-ja-hallintokorvaukset
  (let [urakka-id (hae-ivalon-maanteiden-hoitourakan-id)
        tallennettava-data [{:toimenkuva "hankintavastaava"
                             :maksukaudet #{:molemmat}
                             :jhkt {:molemmat (mapv (fn [hoitokausi kk-v]
                                                      {:hoitokausi hoitokausi :tunnit {:uusi (gen/generate (s/gen ::bs/tunnit))
                                                                                       :paivitys (gen/generate (s/gen ::bs/tunnit))}
                                                       :tuntipalkka {:uusi (gen/generate (s/gen ::bs/tuntipalkka))
                                                                     :paivitys (gen/generate (s/gen ::bs/tuntipalkka))} :kk-v kk-v})
                                                    (range 0 6) (cons 4.5 (repeat 5 12)))}}
                            {:toimenkuva "sopimusvastaava"
                             :maksukaudet #{:molemmat}
                             :jhkt {:molemmat (mapv (fn [hoitokausi kk-v]
                                                      {:hoitokausi hoitokausi :tunnit {:uusi (gen/generate (s/gen ::bs/tunnit))
                                                                                       :paivitys (gen/generate (s/gen ::bs/tunnit))}
                                                       :tuntipalkka {:uusi (gen/generate (s/gen ::bs/tuntipalkka))
                                                                     :paivitys (gen/generate (s/gen ::bs/tuntipalkka))} :kk-v kk-v})
                                                    (range 1 6) (repeat 5 12))}}
                            {:toimenkuva "vastuunalainen työnjohtaja"
                             :maksukaudet #{:molemmat}
                             :jhkt {:molemmat (mapv (fn [hoitokausi kk-v]
                                                      {:hoitokausi hoitokausi :tunnit {:uusi (gen/generate (s/gen ::bs/tunnit))
                                                                                       :paivitys (gen/generate (s/gen ::bs/tunnit))}
                                                       :tuntipalkka {:uusi (gen/generate (s/gen ::bs/tuntipalkka))
                                                                     :paivitys (gen/generate (s/gen ::bs/tuntipalkka))} :kk-v kk-v})
                                                    (range 1 6) (repeat 5 12))}}
                            {:toimenkuva "päätoiminen apulainen"
                             :maksukaudet #{:talvi :kesa}
                             :jhkt {:kesa (mapv (fn [hoitokausi kk-v]
                                                  {:hoitokausi hoitokausi :tunnit {:uusi (gen/generate (s/gen ::bs/tunnit))
                                                                                   :paivitys (gen/generate (s/gen ::bs/tunnit))}
                                                   :tuntipalkka {:uusi (gen/generate (s/gen ::bs/tuntipalkka))
                                                                 :paivitys (gen/generate (s/gen ::bs/tuntipalkka))} :kk-v kk-v})
                                                (range 1 6) (repeat 5 5))
                                    :talvi (mapv (fn [hoitokausi kk-v]
                                                   {:hoitokausi hoitokausi :tunnit {:uusi (gen/generate (s/gen ::bs/tunnit))
                                                                                    :paivitys (gen/generate (s/gen ::bs/tunnit))}
                                                    :tuntipalkka {:uusi (gen/generate (s/gen ::bs/tuntipalkka))
                                                                  :paivitys (gen/generate (s/gen ::bs/tuntipalkka))} :kk-v kk-v})
                                                 (range 1 6) (repeat 5 7))}}
                            {:toimenkuva "apulainen/työnjohtaja"
                             :maksukaudet #{:talvi :kesa}
                             :jhkt {:kesa (mapv (fn [hoitokausi kk-v]
                                                  {:hoitokausi hoitokausi :tunnit {:uusi (gen/generate (s/gen ::bs/tunnit))
                                                                                   :paivitys (gen/generate (s/gen ::bs/tunnit))}
                                                   :tuntipalkka {:uusi (gen/generate (s/gen ::bs/tuntipalkka))
                                                                 :paivitys (gen/generate (s/gen ::bs/tuntipalkka))} :kk-v kk-v})
                                                (range 1 6) (repeat 5 5))
                                    :talvi (mapv (fn [hoitokausi kk-v]
                                                   {:hoitokausi hoitokausi :tunnit {:uusi (gen/generate (s/gen ::bs/tunnit))
                                                                                    :paivitys (gen/generate (s/gen ::bs/tunnit))}
                                                    :tuntipalkka {:uusi (gen/generate (s/gen ::bs/tuntipalkka))
                                                                  :paivitys (gen/generate (s/gen ::bs/tuntipalkka))} :kk-v kk-v})
                                                 (range 1 6) (repeat 5 7))}}
                            {:toimenkuva "viherhoidosta vastaava henkilö"
                             :maksukaudet #{:molemmat}
                             :jhkt {:molemmat (mapv (fn [hoitokausi kk-v]
                                                      {:hoitokausi hoitokausi :tunnit {:uusi (gen/generate (s/gen ::bs/tunnit))
                                                                                       :paivitys (gen/generate (s/gen ::bs/tunnit))}
                                                       :tuntipalkka {:uusi (gen/generate (s/gen ::bs/tuntipalkka))
                                                                     :paivitys (gen/generate (s/gen ::bs/tuntipalkka))} :kk-v kk-v})
                                                    (range 1 6) (repeat 5 5))}}
                            {:toimenkuva "harjoittelija"
                             :maksukaudet #{:molemmat}
                             :jhkt {:molemmat (mapv (fn [hoitokausi kk-v]
                                                      {:hoitokausi hoitokausi :tunnit {:uusi (gen/generate (s/gen ::bs/tunnit))
                                                                                       :paivitys (gen/generate (s/gen ::bs/tunnit))}
                                                       :tuntipalkka {:uusi (gen/generate (s/gen ::bs/tuntipalkka))
                                                                     :paivitys (gen/generate (s/gen ::bs/tuntipalkka))} :kk-v kk-v})
                                                    (range 1 6) (repeat 4 12))}}]
        paivitysvuosi 3]
    (testing "Tallennus onnistuu"
      (doseq [{:keys [toimenkuva maksukaudet jhkt]} tallennettava-data
              maksukausi maksukaudet]
        (let [parametrit {:urakka-id urakka-id
                          :toimenkuva toimenkuva
                          :maksukausi maksukausi
                          :jhkt (mapv (fn [data]
                                        (-> data
                                            (update :tunnit get :uusi)
                                            (update :tuntipalkka get :uusi)))
                                      (get jhkt maksukausi))}
              vastaus (bs/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ parametrit)]
          (is (:onnistui? vastaus) (str "Tallennus ei onnistunut toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi)))))
    (testing "Data kannassa on oikein"
      (let [tallennettu-data (q-map (str "SELECT j_h.tunnit, j_h.tuntipalkka, j_h.\"kk-v\", j_h.maksukausi, j_h.hoitokausi,
                                                 tk.toimenkuva
                                          FROM johto_ja_hallintokorvaus j_h
                                            JOIN johto_ja_hallintokorvaus_toimenkuva tk ON tk.id = j_h.\"toimenkuva-id\"
                                          WHERE \"urakka-id\"=" urakka-id))
            td-ryhmitelty (group-by :toimenkuva tallennettu-data)]
        (doseq [{:keys [toimenkuva maksukaudet jhkt]} tallennettava-data
                maksukausi maksukaudet]
          (is (= (sort-by :hoitokausi (map (fn [data]
                                             (-> data
                                                 (update :tunnit get :uusi)
                                                 (update :tunnit float)
                                                 (update :tuntipalkka get :uusi)
                                                 (update :tuntipalkka float)
                                                 (update :kk-v float)))
                                           (get jhkt maksukausi)))
                 (sort-by :hoitokausi (keep (fn [data]
                                              (when (= (keyword (:maksukausi data)) maksukausi)
                                                (-> data
                                                    (select-keys #{:hoitokausi :tunnit :tuntipalkka :kk-v})
                                                    (update :tunnit float)
                                                    (update :tuntipalkka float)
                                                    (update :kk-v float))))
                                            (get td-ryhmitelty toimenkuva))))
              (str "Data ei kannassa oikein toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi)))))
    (testing "Päivitys onnistuu"
      (doseq [{:keys [toimenkuva maksukaudet jhkt]} tallennettava-data
              maksukausi maksukaudet]
        (let [parametrit {:urakka-id urakka-id
                          :toimenkuva toimenkuva
                          :maksukausi maksukausi
                          :jhkt (transduce
                                  (comp (remove (fn [{:keys [hoitokausi]}]
                                                  (< hoitokausi paivitysvuosi)))
                                        (map (fn [data]
                                               (-> data
                                                   (update :tunnit get :paivitys)
                                                   (update :tuntipalkka get :paivitys)))))
                                  conj [] (get jhkt maksukausi))}
              vastaus (bs/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ parametrit)]
          (is (:onnistui? vastaus) (str "Tallennus ei onnistunut toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi)))))
    (testing "Päivitetty data kannassa on oikein"
      (let [tallennettu-data (q-map (str "SELECT j_h.tunnit, j_h.tuntipalkka, j_h.\"kk-v\", j_h.maksukausi, j_h.hoitokausi,
                                                 tk.toimenkuva
                                          FROM johto_ja_hallintokorvaus j_h
                                            JOIN johto_ja_hallintokorvaus_toimenkuva tk ON tk.id = j_h.\"toimenkuva-id\"
                                          WHERE \"urakka-id\"=" urakka-id))
            td-ryhmitelty (group-by :toimenkuva tallennettu-data)]
        (doseq [{:keys [toimenkuva maksukaudet jhkt]} tallennettava-data
                maksukausi maksukaudet]
          (let [vanhat-tallennettava-jhkt (keep (fn [data]
                                                  (when (< (:hoitokausi data) paivitysvuosi)
                                                    (-> data
                                                        (update :tunnit get :uusi)
                                                        (update :tunnit float)
                                                        (update :tuntipalkka get :uusi)
                                                        (update :tuntipalkka float)
                                                        (update :kk-v float))))
                                                (get jhkt maksukausi))
                paivitetyt-tallennettava-jhkt (keep (fn [data]
                                                      (when (>= (:hoitokausi data) paivitysvuosi)
                                                        (-> data
                                                            (update :tunnit get :paivitys)
                                                            (update :tunnit float)
                                                            (update :tuntipalkka get :paivitys)
                                                            (update :tuntipalkka float)
                                                            (update :kk-v float))))
                                                    (get jhkt maksukausi))
                vanhat-kannassa-jhkt (keep (fn [data]
                                             (when (and (< (:hoitokausi data) paivitysvuosi)
                                                        (= (keyword (:maksukausi data)) maksukausi))
                                               (-> data
                                                   (select-keys #{:hoitokausi :tunnit :tuntipalkka :kk-v})
                                                   (update :tunnit float)
                                                   (update :tuntipalkka float)
                                                   (update :kk-v float))))
                                           (get td-ryhmitelty toimenkuva))
                paivitetyt-kannassa-jhkt (keep (fn [data]
                                                 (when (and (>= (:hoitokausi data) paivitysvuosi)
                                                            (= (keyword (:maksukausi data)) maksukausi))
                                                   (-> data
                                                       (select-keys #{:hoitokausi :tunnit :tuntipalkka :kk-v})
                                                       (update :tunnit float)
                                                       (update :tuntipalkka float)
                                                       (update :kk-v float))))
                                               (get td-ryhmitelty toimenkuva))]
            (is (= (sort-by :hoitokausi vanhat-tallennettava-jhkt)
                   (sort-by :hoitokausi vanhat-kannassa-jhkt))
                (str "Vanha data ei kannassa oikein toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi))
            (is (= (sort-by :hoitokausi paivitetyt-tallennettava-jhkt)
                   (sort-by :hoitokausi paivitetyt-kannassa-jhkt))
                (str "Päivitetty data ei kannassa oikein toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi))))))))