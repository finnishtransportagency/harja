(ns harja.palvelin.palvelut.budjettisuunnittelu-test
  (:require [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as pois-kytketyt-ominaisuudet]
            [harja.testi :refer :all]
            [harja.data.hoito.kustannussuunnitelma :as data-gen]
            [harja.domain.palvelut.budjettisuunnittelu :as bs-p]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [slingshot.slingshot :refer [try+]]))

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
  (s/with-gen ::bs-p/aika
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
  (s/with-gen ::bs-p/aika
              (fn []
                (gen/fmap (fn [_]
                            (let [aloitus-vuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))
                                  vuosi (rand-nth (range aloitus-vuosi (+ aloitus-vuosi 6)))
                                  kk (cond
                                       (= vuosi aloitus-vuosi) (rand-nth (range 10 13))
                                       (= vuosi (+ aloitus-vuosi 5)) (rand-nth (range 1 10))
                                       :else (rand-nth (range 1 13)))]
                              {:vuosi vuosi}))
                          (gen/int)))))

(defn kokonaisia-hoitokausia?
  [data]
  {:pre [(sequential? data)
         (every? map? data)
         (every? #(and (contains? % :kuukausi)
                       (contains? % :vuosi)) data)]}
  (let [paivamaarat (map #(assoc % ::pvm (pvm/luo-pvm (:vuosi %) (dec (:kuukausi %)) 15)) data)
        paivamaarat-hoitokausittain (group-by #(pvm/paivamaaran-hoitokausi (::pvm %)) paivamaarat)
        ensimmaisen-vuoden-kk (into #{} (range 10 13))
        toisen-vuoden-kk (into #{} (range 1 10))
        kokonainen-hoitokausi? (fn [hoitokauden-tiedot]
                                 (let [hoitokausi-vuosittain (group-by :vuosi hoitokauden-tiedot)
                                       [ensimmaisen-vuoden-tiedot toisen-vuoden-tiedot] (vals (sort-by key hoitokausi-vuosittain))]
                                   (and (empty? (remove #(contains? ensimmaisen-vuoden-kk (:kuukausi %))
                                                        ensimmaisen-vuoden-tiedot))
                                        (empty? (remove #(contains? toisen-vuoden-kk (:kuukausi %))
                                                        toisen-vuoden-tiedot)))))]
    (every? kokonainen-hoitokausi? (vals paivamaarat-hoitokausittain))))

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
        (println (into [] (eduction (filter #(= "akillinen-hoitotyo" (:tyyppi %)))
                                    (filter #(nil? (:haettu-asia %)))
                                    (:talvihoito kustannusarvioidut-tyot-toimenpiteittain))))
        (doseq [[toimenpide-avain tehtavat] kustannusarvioidut-tyot-toimenpiteittain
                :let [ryhmiteltyna (group-by (juxt :tyyppi :haettu-asia) tehtavat)]]
          (case toimenpide-avain
            :paallystepaikkaukset (is (= ryhmiteltyna {}))
            :mhu-yllapito (do
                            (is (= (keys ryhmiteltyna) [["muut-rahavaraukset" :rahavaraus-lupaukseen-1]]))
                            (testaa-ajat tehtavat toimenpide-avain))
            :talvihoito (do
                          (is (= (into #{} (keys ryhmiteltyna))
                                 #{["vahinkojen-korjaukset" :kolmansien-osapuolten-aiheuttamat-vahingot]
                                   ["akillinen-hoitotyo" :akilliset-hoitotyot]}))
                          (doseq [[_ tehtavat] ryhmiteltyna]
                            (testaa-ajat tehtavat toimenpide-avain)))
            :liikenneympariston-hoito (do
                                        (is (= (into #{} (keys ryhmiteltyna))
                                               #{["laskutettava-tyo" nil] ;; Tässä testidatassa on siis painettu päälle "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle"
                                                 ["vahinkojen-korjaukset" :kolmansien-osapuolten-aiheuttamat-vahingot]
                                                 ["akillinen-hoitotyo" :akilliset-hoitotyot]}))
                                        (doseq [[_ tehtavat] ryhmiteltyna]
                                          (testaa-ajat tehtavat toimenpide-avain)))
            :sorateiden-hoito (do
                                (is (= (into #{} (keys ryhmiteltyna))
                                       #{["vahinkojen-korjaukset" :kolmansien-osapuolten-aiheuttamat-vahingot]
                                         ["akillinen-hoitotyo" :akilliset-hoitotyot]}))
                                (doseq [[_ tehtavat] ryhmiteltyna]
                                  (testaa-ajat tehtavat toimenpide-avain)))
            :mhu-korvausinvestointi (is (= ryhmiteltyna {}))
            :mhu-johto (do
                         (is (= (into #{} (keys ryhmiteltyna))
                                #{["laskutettava-tyo" :erillishankinnat]
                                  ["laskutettava-tyo" :toimistokulut]
                                  ["laskutettava-tyo" :hoidonjohtopalkkio]}))
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

(deftest indeksien-haku
  (let [rovaniemi-urakka-id (hae-rovaniemen-maanteiden-hoitourakan-id)
        ivalo-urakka-id (hae-ivalon-maanteiden-hoitourakan-id)
        pellon-urakka-id (hae-pellon-maanteiden-hoitourakan-id)
        ;; Indeksi lyödään automaagisesti urakalle, josta syystä Pellolla saattaa olla vanha indeksi käytössä
        _ (u (str "UPDATE urakka SET indeksi = 'MAKU 2015' WHERE id = " pellon-urakka-id ";"))
        kuluvan-hoitokauden-aloitusvuosi (-> (pvm/nyt) pvm/paivamaaran-hoitokausi first pvm/vuosi)

        db (:db jarjestelma)

        rovaniemen-indeksit (bs/hae-urakan-indeksit db +kayttaja-jvh+ {:urakka-id rovaniemi-urakka-id})
        ivalon-indeksit (bs/hae-urakan-indeksit db +kayttaja-jvh+ {:urakka-id ivalo-urakka-id})
        pellon-indeksit (bs/hae-urakan-indeksit db +kayttaja-jvh+ {:urakka-id pellon-urakka-id})]
    (clojure.pprint/pprint ivalon-indeksit)
    (clojure.pprint/pprint pellon-indeksit)
    (is (= rovaniemen-indeksit ivalon-indeksit) "Indeksit pitäisi olla sama samaan aikaan alkaneille urakoillle")
    (is (= rovaniemen-indeksit [{:vuosi kuluvan-hoitokauden-aloitusvuosi :indeksikorjaus 1.000255}
                                {:vuosi (inc kuluvan-hoitokauden-aloitusvuosi) :indeksikorjaus 1.076707}
                                {:vuosi (inc kuluvan-hoitokauden-aloitusvuosi) :indeksikorjaus 1.076707}
                                {:vuosi (inc kuluvan-hoitokauden-aloitusvuosi) :indeksikorjaus 1.076707}
                                {:vuosi (inc kuluvan-hoitokauden-aloitusvuosi) :indeksikorjaus 1.076707}])
        "Indeksilukemat eivät ole oikein Rovaniemen testiurakalle")
    (is (= pellon-indeksit [{:vuosi (- kuluvan-hoitokauden-aloitusvuosi 2) :indeksikorjaus 1.000301}
                            {:vuosi (dec kuluvan-hoitokauden-aloitusvuosi) :indeksikorjaus 1.090554}
                            {:vuosi kuluvan-hoitokauden-aloitusvuosi :indeksikorjaus       1.180806}
                            {:vuosi (inc kuluvan-hoitokauden-aloitusvuosi) :indeksikorjaus 1.271059}
                            {:vuosi (inc kuluvan-hoitokauden-aloitusvuosi) :indeksikorjaus 1.271059}])
        "Indeksilukemat eivät ole oikein Pellon testiurakalle")))

(deftest tallenna-kiinteahintaiset-tyot
  (let [urakka-id (hae-ivalon-maanteiden-hoitourakan-id)
        tallennettava-data (data-gen/tallenna-kiinteahintaiset-tyot-data urakka-id)
        paivitettava-data (mapv (fn [data]
                                  (-> data
                                      (update :ajat (fn [ajat]
                                                      (drop (int (/ (count ajat) 2)) ajat)))
                                      (assoc :summa (gen/generate (s/gen ::bs-p/summa)))))
                                tallennettava-data)]
    (testing "Tallennus onnistuu"
      (doseq [tyo tallennettava-data]
        (let [vastaus (bs/tallenna-kiinteahintaiset-tyot (:db jarjestelma) +kayttaja-jvh+ tyo)]
          (is (:onnistui? vastaus) (str "Tallentaminen toimenpiteelle " (:toimenpide-avain tyo) " epäonnistui.")))))
    (testing "Data kannassa on oikein"
      (doseq [{:keys [toimenpide-avain urakka-id ajat summa]} tallennettava-data]
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
              (str "Ajat eivät tallentuneet kantaan oikein toimenpiteelle " toimenpide-avain)))))
    (testing "Päivitys onnistuu"
      (doseq [tyo paivitettava-data]
        (let [vastaus (bs/tallenna-kiinteahintaiset-tyot (:db jarjestelma) +kayttaja-jvh+ tyo)]
          (is (:onnistui? vastaus) (str "Päivittäminen toimenpiteelle " (:toimenpide-avain tyo) " epäonnistui.")))))
    (testing "Päivitetty data kannassa on oikein"
      (doseq [{:keys [toimenpide-avain urakka-id ajat summa]} paivitettava-data]
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
              data-kannassa (q-map (str "SELECT vuosi, kuukausi, summa, muokattu, toimenpideinstanssi FROM kiinteahintainen_tyo WHERE toimenpideinstanssi=" toimenpideinstanssi ";"))
              [vanha-summa vanhat-ajat] (some (fn [{v-tpa :toimenpide-avain
                                                    summa :summa
                                                    ajat :ajat}]
                                                (when (= v-tpa toimenpide-avain)
                                                  [summa (take (int (/ (count ajat) 2)) ajat)]))
                                              tallennettava-data)
              paivitetty-summa summa
              paivitetyt-ajat ajat
              paivitetty-data-kannassa (filter (fn [{:keys [vuosi kuukausi]}]
                                                 (some #(and (= vuosi (:vuosi %))
                                                             (= kuukausi (:kuukausi %)))
                                                       paivitetyt-ajat))
                                               data-kannassa)]
          (is (every? :muokattu paivitetty-data-kannassa) "Luomisaika ei tallennettu")
          (is (= (+ (count vanhat-ajat) (count paivitetyt-ajat)) (count data-kannassa)))
          (is (every? #(= (float (:summa %))
                          (float vanha-summa))
                      (filter (fn [{:keys [vuosi kuukausi]}]
                                (some #(and (= vuosi (:vuosi %))
                                            (= kuukausi (:kuukausi %)))
                                      vanhat-ajat))
                              data-kannassa))
              (str "Vanhat summat ei pysy kannassa oikein päivityksen jälkeen toimenpiteelle " toimenpide-avain))
          (is (every? #(= (float (:summa %))
                          (float paivitetty-summa))
                      paivitetty-data-kannassa)
              (str "Päivitetyt summat ei tallennu kantaan oikein toimenpiteelle " toimenpide-avain)))))))

(deftest tallenna-kustannusarvioitu-tyo
  (let [{urakka-id :id urakan-alkupvm :alkupvm urakan-loppupvm :loppupvm} (first (q-map (str "SELECT id, alkupvm, loppupvm
                                                                                              FROM   urakka
                                                                                              WHERE  nimi = 'Ivalon MHU testiurakka (uusi)'")))
        muodosta-ajat (fn [vuosi]
                        (let [ensimmaisen-vuoden-ajat (map (fn [kk]
                                                             {:vuosi vuosi
                                                              :kuukausi kk})
                                                           (range 10 13))
                              toisen-vuoden-ajat (map (fn [kk]
                                                        {:vuosi (inc vuosi)
                                                         :kuukausi kk})
                                                      (range 1 10))]
                          (into []
                                (concat ensimmaisen-vuoden-ajat
                                        toisen-vuoden-ajat))))
        tallennetun-asian-data? (fn [tallennettava-asia {:keys [tyyppi tehtava tehtavaryhma]}]
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
        tallennettava-data (data-gen/tallenna-kustannusarvioitu-tyo-data-juuri-alkaneelle-urakalle urakka-id)
        paivitettava-data (mapv (fn [data]
                                  (-> data
                                      (update :ajat (fn [ajat]
                                                      (drop (int (/ (count ajat) 2)) ajat)))
                                      (assoc :summa (gen/generate (s/gen ::bs-p/summa)))))
                                tallennettava-data)]
    (testing "Tallennus onnistuu"
      (doseq [tyo tallennettava-data]
        (let [vastaus (bs/tallenna-kustannusarvioitu-tyo (:db jarjestelma) +kayttaja-jvh+ tyo)]
          (is (:onnistui? vastaus) (str "Tallentaminen toimenpiteelle " (:toimenpide-avain tyo) " epäonnistui.")))))
    (testing "Data kannassa on oikein"
      (doseq [{:keys [toimenpide-avain urakka-id ajat tallennettava-asia summa]} tallennettava-data]
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
                                         WHERE kt.toimenpideinstanssi=" toimenpideinstanssi ";"))
              tallennetun-asian-data-kannassa (filter #(tallennetun-asian-data? tallennettava-asia %) data-kannassa)]
          (is (every? :luotu data-kannassa) "Luomisaika ei tallennettu")
          (is (every? #(= (float (:summa %))
                          (float summa))
                      tallennetun-asian-data-kannassa)
              (str "Summa ei tallentunut oikein toimenpiteelle " toimenpide-avain))
          (is (= (sort-by (juxt :vuosi :kuukausi) (map #(select-keys % #{:vuosi :kuukausi}) tallennetun-asian-data-kannassa))
                 (sort-by (juxt :vuosi :kuukausi) (mapcat (fn [{:keys [vuosi]}]
                                                            (muodosta-ajat vuosi))
                                                          ajat)))
              (str "Ajat eivät tallentuneet kantaan oikein toimenpiteelle: " toimenpide-avain " ja tallennettavalle asialle: " tallennettava-asia)))))
    (testing "Päivitys onnistuu"
      (doseq [tyo paivitettava-data]
        (let [vastaus (bs/tallenna-kustannusarvioitu-tyo (:db jarjestelma) +kayttaja-jvh+ tyo)]
          (is (:onnistui? vastaus) (str "Päivittäminen toimenpiteelle " (:toimenpide-avain tyo) " epäonnistui.")))))
    (testing "Päivitetty data kannassa on oikein"
      (doseq [{:keys [toimenpide-avain urakka-id ajat tallennettava-asia summa]} paivitettava-data]
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
              data-kannassa (q-map (str "SELECT kt.vuosi, kt.kuukausi, kt.summa, kt.muokattu, kt.tyyppi, tk.nimi AS tehtava, tr.nimi AS tehtavaryhma
                                         FROM kustannusarvioitu_tyo kt
                                           LEFT JOIN toimenpidekoodi tk ON tk.id = kt.tehtava
                                           LEFT JOIN tehtavaryhma tr ON tr.id = kt.tehtavaryhma
                                         WHERE kt.toimenpideinstanssi=" toimenpideinstanssi ";"))
              tallennetun-asian-data-kannassa (filter #(tallennetun-asian-data? tallennettava-asia %) data-kannassa)


              [vanha-summa vanhat-ajat] (some (fn [{v-ta :tallennettava-asia
                                                    v-tpa :toimenpide-avain
                                                    summa :summa
                                                    ajat :ajat}]
                                                (when (and (= v-ta tallennettava-asia)
                                                           (= v-tpa toimenpide-avain))
                                                  [summa (take (int (/ (count ajat) 2)) ajat)]))
                                              tallennettava-data)
              paivitetty-summa summa
              paivitetyt-ajat ajat
              vanha-data-kannassa (filter (fn [{muokattu :muokattu}]
                                            (nil? muokattu))
                                          tallennetun-asian-data-kannassa)
              uusi-data-kannassa (remove (fn [{muokattu :muokattu}]
                                           (nil? muokattu))
                                         tallennetun-asian-data-kannassa)]
          (is (not (empty? uusi-data-kannassa)) "Muokkausaika ei tallennettu")
          (is (kokonaisia-hoitokausia? vanha-data-kannassa) "Vanha data kannassa ei käsitä kokonaisia hoitokausia")
          (is (kokonaisia-hoitokausia? uusi-data-kannassa) "Päivitetty data kannassa ei käsitä kokonaisia hoitokausia")
          (is (every? #(= (float (:summa %))
                          (float vanha-summa))
                      vanha-data-kannassa)
              (str "Vanha summa ei ole oikein päivityksen jälkeen toimenpiteelle " toimenpide-avain))
          (is (every? #(= (float (:summa %))
                          (float paivitetty-summa))
                      uusi-data-kannassa)
              (str "Päivitetty summa ei ole oikein päivityksen jälkeen toimenpiteelle " toimenpide-avain))
          (is (= (sort-by (juxt :vuosi :kuukausi) (map #(select-keys % #{:vuosi :kuukausi}) vanha-data-kannassa))
                 (sort-by (juxt :vuosi :kuukausi) (mapcat (fn [{:keys [vuosi]}]
                                                            (muodosta-ajat vuosi))
                                                          vanhat-ajat)))
              (str "Ajat eivät oikein päivityksen jälkeen vanhalle datalle toimenpiteelle: " toimenpide-avain " ja tallennettavalle asialle: " tallennettava-asia))
          (is (= (sort-by (juxt :vuosi :kuukausi) (map #(select-keys % #{:vuosi :kuukausi}) uusi-data-kannassa))
                 (sort-by (juxt :vuosi :kuukausi) (mapcat (fn [{:keys [vuosi]}]
                                                            (muodosta-ajat vuosi))
                                                          paivitetyt-ajat)))
              (str "Ajat eivät oikein päivityksen jälkeen päivitetylle datalle toimenpiteelle: " toimenpide-avain " ja tallennettavalle asialle: " tallennettava-asia)))))))

(deftest tallenna-johto-ja-hallintokorvaukset
  (let [urakka-id (hae-ivalon-maanteiden-hoitourakan-id)
        paivitysvuosi 3
        tallennettava-data (data-gen/tallenna-johto-ja-hallintokorvaus-data urakka-id)
        paivitettava-data (data-gen/tallenna-johto-ja-hallintokorvaus-data urakka-id {:hoitokaudet (into #{} (range paivitysvuosi 6))})]
    (testing "Tallennus onnistuu"
      (doseq [{:keys [toimenkuva maksukausi] :as parametrit} tallennettava-data]
        (let [vastaus (bs/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ parametrit)]
          (is (:onnistui? vastaus) (str "Tallennus ei onnistunut toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi)))))
    (testing "Data kannassa on oikein"
      (let [tallennettu-data (q-map (str "SELECT j_h.tunnit, j_h.tuntipalkka, j_h.\"kk-v\", j_h.maksukausi, j_h.hoitokausi,
                                                 tk.toimenkuva, j_h.luotu
                                          FROM johto_ja_hallintokorvaus j_h
                                            JOIN johto_ja_hallintokorvaus_toimenkuva tk ON tk.id = j_h.\"toimenkuva-id\"
                                          WHERE \"urakka-id\"=" urakka-id))
            td-ryhmitelty (group-by :toimenkuva tallennettu-data)]
        (is (every? :luotu tallennettu-data))
        (doseq [{:keys [toimenkuva maksukausi jhkt]} tallennettava-data]
          (is (= (sort-by :hoitokausi (map (fn [data]
                                             (-> data
                                                 (update :tunnit float)
                                                 (update :tuntipalkka float)
                                                 (update :kk-v float)))
                                           jhkt))
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
      (doseq [{:keys [toimenkuva maksukausi] :as parametrit} paivitettava-data]
        (let [vastaus (bs/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ parametrit)]
          (is (:onnistui? vastaus) (str "Päivittäminen ei onnistunut toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi)))))
    (testing "Päivitetty data kannassa on oikein"
      (let [tallennettu-data (q-map (str "SELECT j_h.tunnit, j_h.tuntipalkka, j_h.\"kk-v\", j_h.maksukausi, j_h.hoitokausi,
                                                 tk.toimenkuva, j_h.muokattu
                                          FROM johto_ja_hallintokorvaus j_h
                                            JOIN johto_ja_hallintokorvaus_toimenkuva tk ON tk.id = j_h.\"toimenkuva-id\"
                                          WHERE \"urakka-id\"=" urakka-id))
            td-ryhmitelty (group-by :toimenkuva tallennettu-data)]
        (doseq [{:keys [toimenkuva maksukausi jhkt]} paivitettava-data]
          (let [vanhat-tallennettava-jhkt (keep (fn [data]
                                                  (when (< (:hoitokausi data) paivitysvuosi)
                                                    (-> data
                                                        (update :tunnit float)
                                                        (update :tuntipalkka float)
                                                        (update :kk-v float))))
                                                (some (fn [{vanha-toimenkuva :toimenkuva
                                                            vanha-maksukausi :maksukausi
                                                            vanha-jhkt :jhkt}]
                                                        (when (and (= vanha-toimenkuva toimenkuva)
                                                                   (= vanha-maksukausi maksukausi))
                                                          vanha-jhkt))
                                                      tallennettava-data))
                paivitetyt-tallennettava-jhkt (keep (fn [data]
                                                      (-> data
                                                          (update :tunnit float)
                                                          (update :tuntipalkka float)
                                                          (update :kk-v float)))
                                                    jhkt)
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
                                                       (select-keys #{:hoitokausi :tunnit :tuntipalkka :kk-v :muokattu})
                                                       (update :tunnit float)
                                                       (update :tuntipalkka float)
                                                       (update :kk-v float))))
                                               (get td-ryhmitelty toimenkuva))]
            (is (every? :muokattu paivitetyt-kannassa-jhkt))
            (is (= (sort-by :hoitokausi vanhat-tallennettava-jhkt)
                   (sort-by :hoitokausi vanhat-kannassa-jhkt))
                (str "Vanha data ei kannassa oikein toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi))
            (is (= (sort-by :hoitokausi paivitetyt-tallennettava-jhkt)
                   (sort-by :hoitokausi (map #(dissoc % :muokattu) paivitetyt-kannassa-jhkt)))
                (str "Päivitetty data ei kannassa oikein toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi))))))))

(deftest budjettitavoite-haku
  (let [parametrit {:urakka-id (hae-rovaniemen-maanteiden-hoitourakan-id)}
        budjettitavoite (bs/hae-urakan-tavoite (:db jarjestelma) +kayttaja-jvh+ parametrit)
        kerroin 1.1]
    (is (every? :luotu budjettitavoite) "Luotuaika ei löytynyt")
    (doseq [kausitavoite budjettitavoite
            :let [{:keys [kattohinta tavoitehinta hoitokausi]} kausitavoite]]
      (case hoitokausi
        1 (do (is (= tavoitehinta 250000M))
              (is (= (float kattohinta)
                     (float (* kerroin 250000M)))))
        2 (do (is (= tavoitehinta 300000M))
              (is (= (float kattohinta)
                     (float (* kerroin 300000M)))))
        3 (do (is (= tavoitehinta 350000M))
              (is (= (float kattohinta)
                     (float (* kerroin 350000M)))))
        4 (do (is (= tavoitehinta 250000M))
              (is (= (float kattohinta)
                     (float (* kerroin 250000M)))))
        5 (do (is (= tavoitehinta 250000M))
              (is (= (float kattohinta)
                     (float (* kerroin 250000M)))))))))

(deftest budjettitavoite-tallennus
  (let [urakka-id (hae-ivalon-maanteiden-hoitourakan-id)
        uusi-tavoitehinta (gen/generate (s/gen ::bs-p/tavoitehinta))
        paivitetty-tavoitehinta (gen/generate (s/gen ::bs-p/tavoitehinta))
        kerroin 1.1
        paivitys-hoitokaudesta-eteenpain 3
        tallennettavat-tavoitteet (mapv (fn [hoitokausi]
                                          {:hoitokausi hoitokausi
                                           :tavoitehinta {:uusi uusi-tavoitehinta
                                                          :paivitys paivitetty-tavoitehinta}
                                           :kattohinta {:uusi (* kerroin uusi-tavoitehinta)
                                                        :paivitys (* kerroin paivitetty-tavoitehinta)}})
                                        (range 1 5))
        pyorista (fn [x] (with-precision 6 (float x)))]
    (testing "Tallennus onnistuu"
      (let [vastaus (bs/tallenna-urakan-tavoite (:db jarjestelma) +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                  :tavoitteet (mapv (fn [tavoite]
                                                                                                      (-> tavoite
                                                                                                          (update :tavoitehinta get :uusi)
                                                                                                          (update :kattohinta get :uusi)))
                                                                                                    tallennettavat-tavoitteet)})]
        (is (:onnistui? vastaus) "Budjettitavoitteen tallentaminen ei onnistunut")))
    (testing "Data kannassa on oikein"
      (let [data-kannassa (q-map (str "SELECT *
                                       FROM urakka_tavoite
                                       WHERE urakka = " urakka-id ";"))]
        (is (every? :luotu data-kannassa) "Luotu aikaa ei kannassa budjettitavoitteelle")
        (is (every? #(= (pyorista (:tavoitehinta %)) (pyorista uusi-tavoitehinta)) data-kannassa) "Tavoitehinta ei tallentunut kantaan oikein")
        (is (every? #(= (pyorista (:kattohinta %)) (pyorista (* kerroin uusi-tavoitehinta))) data-kannassa) "Kattohinta ei tallentunut kantaan oikein")))
    (testing "Päivitys onnistuu"
      (let [vastaus (bs/tallenna-urakan-tavoite (:db jarjestelma) +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                  :tavoitteet (transduce
                                                                                                (comp (filter (fn [tavoite]
                                                                                                                (>= (:hoitokausi tavoite) paivitys-hoitokaudesta-eteenpain)))
                                                                                                      (map (fn [tavoite]
                                                                                                             (-> tavoite
                                                                                                                 (update :tavoitehinta get :paivitys)
                                                                                                                 (update :kattohinta get :paivitys)))))
                                                                                                conj [] tallennettavat-tavoitteet)})]
        (is (:onnistui? vastaus) "Budjettitavoitteen päivittäminen ei onnistunut")))
    (testing "Päivitetty data kannassa on oikein"
      (let [data-kannassa (q-map (str "SELECT *
                                       FROM urakka_tavoite
                                       WHERE urakka = " urakka-id ";"))
            vanhadata-kannassa (filter (fn [tavoite]
                                         (< (:hoitokausi tavoite) paivitys-hoitokaudesta-eteenpain))
                                       data-kannassa)
            uusidata-kannassa (filter (fn [tavoite]
                                        (>= (:hoitokausi tavoite) paivitys-hoitokaudesta-eteenpain))
                                      data-kannassa)]
        (is (every? :muokattu uusidata-kannassa) "Muokattu aika ei kannassa budjettitavoitteelle")
        (is (every? #(= (pyorista (:tavoitehinta %)) (pyorista uusi-tavoitehinta)) vanhadata-kannassa) "Tavoitehinta ei oikein päivityksen jälkeen")
        (is (every? #(= (pyorista (:kattohinta %)) (pyorista (* kerroin uusi-tavoitehinta))) vanhadata-kannassa) "Kattohinta ei oikein päivityksen jälkeen")
        (is (every? #(= (pyorista (:tavoitehinta %)) (pyorista paivitetty-tavoitehinta)) uusidata-kannassa) "Päivitetty tavoitehinta ei oikein päivityksen jälkeen")
        (is (every? #(= (pyorista (:kattohinta %)) (pyorista (* kerroin paivitetty-tavoitehinta))) uusidata-kannassa) "Päivitetty kattohinta ei oikein päivityksen jälkeen")))))

(deftest budjettisuunnittelun-oikeustarkastukset
  (let [urakka-id (hae-rovaniemen-maanteiden-hoitourakan-id)]
    (testing "budjetoidut-tyot kutsun oikeustarkistus"
      (is (= (try+ (bs/hae-urakan-budjetoidut-tyot (:db jarjestelma) +kayttaja-seppo+ {:urakka-id urakka-id})
                   (catch harja.domain.roolit.EiOikeutta eo#
                     :ei-oikeutta-virhe))
             :ei-oikeutta-virhe)))
    (testing "hae-urakan-indeksit"
      (is (= (try+ (bs/hae-urakan-indeksit (:db jarjestelma) +kayttaja-seppo+ {:urakka-id urakka-id})
                   (catch harja.domain.roolit.EiOikeutta eo#
                     :ei-oikeutta-virhe))
             :ei-oikeutta-virhe)))
    (testing "budjettitavoite kutsun oikeustarkistus"
      (is (= (try+ (bs/hae-urakan-tavoite (:db jarjestelma) +kayttaja-seppo+ {:urakka-id urakka-id})
                   (catch harja.domain.roolit.EiOikeutta eo#
                     :ei-oikeutta-virhe))
             :ei-oikeutta-virhe)))
    (testing "tallenna-budjettitavoite kutsun oikeustarkistus"
      (is (= (try+ (bs/tallenna-urakan-tavoite (:db jarjestelma) +kayttaja-seppo+ {:urakka-id urakka-id})
                   (catch harja.domain.roolit.EiOikeutta eo#
                     :ei-oikeutta-virhe))
             :ei-oikeutta-virhe)))
    (testing "tallenna-kiinteahintaiset-tyot kutsun oikeustarkistus"
      (is (= (try+ (bs/tallenna-kiinteahintaiset-tyot (:db jarjestelma) +kayttaja-seppo+ {:urakka-id urakka-id
                                                                                          :toimenpide-avain :foo
                                                                                          :summa 1})
                   (catch harja.domain.roolit.EiOikeutta eo#
                     :ei-oikeutta-virhe))
             :ei-oikeutta-virhe)))
    (testing "tallenna-johto-ja-hallintokorvaukset kutsun oikeustarkistus"
      (is (= (try+ (bs/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-seppo+ {:urakka-id urakka-id
                                                                                                :toimenkuva "foo"
                                                                                                :maksukausi :bar})
                   (catch harja.domain.roolit.EiOikeutta eo#
                     :ei-oikeutta-virhe))
             :ei-oikeutta-virhe)))
    (testing "tallenna-kustannusarvioitu-tyo kutsun oikeustarkistus"
      (is (= (try+ (bs/tallenna-kustannusarvioitu-tyo (:db jarjestelma) +kayttaja-seppo+ {:urakka-id urakka-id})
                   (catch harja.domain.roolit.EiOikeutta eo#
                     :ei-oikeutta-virhe))
             :ei-oikeutta-virhe)))))

(deftest palvelun-validointi-ja-palvelu-sama
  (is (= (into #{} (keys (var-get #'harja.palvelin.palvelut.budjettisuunnittelu/toimenpide-avain->toimenpide)))
         bs-p/toimenpide-avaimet)))