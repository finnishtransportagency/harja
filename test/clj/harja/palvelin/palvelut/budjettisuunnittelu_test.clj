(ns harja.palvelin.palvelut.budjettisuunnittelu-test
  (:require [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.testi :refer :all]
            [harja.data.hoito.kustannussuunnitelma :as data-gen]
            [harja.domain.palvelut.budjettisuunnittelu :as bs-p]
            [harja.domain.mhu :as mhu]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :as log]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (luo-testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :budjetoidut-tyot (component/using
                                            (bs/->Budjettisuunnittelu)
                                            [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(defn maksukausi-jhlle-kannasta [{:keys [toimenkuva ennen-urakkaa kuukausi]}]
  (if ennen-urakkaa
    nil
    (cond
      (= toimenkuva "sopimusvastaava") :molemmat
      (= toimenkuva "vastuunalainen työnjohtaja") :molemmat
      (and (= toimenkuva "päätoiminen apulainen")
           (or (<= 1 kuukausi 4)
               (<= 10 kuukausi 12))) :talvi
      (= toimenkuva "päätoiminen apulainen") :kesa
      (and (= toimenkuva "apulainen/työnjohtaja")
           (or (<= 1 kuukausi 4)
               (<= 10 kuukausi 12))) :talvi
      (= toimenkuva "apulainen/työnjohtaja") :kesa
      (= toimenkuva "viherhoidosta vastaava henkilö") :molemmat
      (= toimenkuva "hankintavastaava") :molemmat
      (= toimenkuva "harjoittelija") :molemmat)))

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
      (let [jh-korvaukset (:johto-ja-hallintokorvaukset budjetoidut-tyot)
            vakio-johto-ja-hallintokorvaukset (group-by :toimenkuva (:vakiot jh-korvaukset))
            omat-johto-ja-hallintokorvaukset (:omat jh-korvaukset)
            omat-toimenkuvat (:omat-toimenkuvat jh-korvaukset)]
        (is (empty? omat-johto-ja-hallintokorvaukset) "Omat johto ja hallintokorvaukset ei ole tyhjä Pellon urakassa!")
        (is (= 2 (count omat-toimenkuvat)) "Luotuja toimenkuvia ei ole tasan kaksi sellaiselle urakalle, jolle niitä ei aikasemmin ole määritetty!")
        (is (every? #(and (contains? % :toimenkuva) (nil? (:toimenkuva %))) omat-toimenkuvat))
        (is (every? #(and (contains? % :toimenkuva-id) (integer? (:toimenkuva-id %))) omat-toimenkuvat))
        (doseq [[toimenkuva tiedot] vakio-johto-ja-hallintokorvaukset]
          (case toimenkuva
            "hankintavastaava" (is (= (into #{}
                                            (map (fn [hoitokausi]
                                                   (if (= 0 hoitokausi)
                                                     [hoitokausi nil]
                                                     [hoitokausi :molemmat]))
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

(deftest indeksikertoimien-haku
  (let [rovaniemi-urakka-id (hae-rovaniemen-maanteiden-hoitourakan-id)
        ivalo-urakka-id (hae-ivalon-maanteiden-hoitourakan-id)
        pellon-urakka-id (hae-pellon-maanteiden-hoitourakan-id)
        ;; Indeksi lyödään automaagisesti urakalle, josta syystä Pellolla saattaa olla vanha indeksi käytössä
        _ (u (str "UPDATE urakka SET indeksi = 'MAKU 2015' WHERE id = " pellon-urakka-id ";"))
        kuluvan-hoitokauden-aloitusvuosi (-> (pvm/nyt) pvm/paivamaaran-hoitokausi first pvm/vuosi)

        db (:db jarjestelma)

        rovaniemen-indeksit (bs/hae-urakan-indeksikertoimet db +kayttaja-jvh+ {:urakka-id rovaniemi-urakka-id})
        ivalon-indeksit (bs/hae-urakan-indeksikertoimet db +kayttaja-jvh+ {:urakka-id ivalo-urakka-id})
        pellon-indeksit (bs/hae-urakan-indeksikertoimet db +kayttaja-jvh+ {:urakka-id pellon-urakka-id})]
    (is (= rovaniemen-indeksit ivalon-indeksit) "Indeksit pitäisi olla sama samaan aikaan alkaneille urakoillle")))

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
        tallennetun-asian-data? (fn [tallennettava-asia {:keys [tyyppi tehtava tehtavaryhma tk_yt tr_yt]}]
                                  (case tallennettava-asia
                                    :hoidonjohtopalkkio (and (= tyyppi "laskutettava-tyo")
                                                             (= tk_yt mhu/hoidonjohtopalkkio-tunniste)
                                                             (nil? tehtavaryhma))
                                    :toimistokulut (and (= tyyppi "laskutettava-tyo")
                                                        (= tk_yt mhu/toimistokulut-tunniste)
                                                        (nil? tehtavaryhma))
                                    :erillishankinnat (and (= tyyppi "laskutettava-tyo")
                                                           (= tr_yt mhu/erillishankinnat-tunniste)
                                                           (nil? tehtava))
                                    :rahavaraus-lupaukseen-1 (and (= tyyppi "muut-rahavaraukset")
                                                                  (= tr_yt mhu/rahavaraus-lupaukseen-1-tunniste)
                                                                  (nil? tehtava))
                                    :kolmansien-osapuolten-aiheuttamat-vahingot (and (= tyyppi "vahinkojen-korjaukset")
                                                                                     (contains? #{mhu/kolmansien-osapuolten-vahingot-sorateiden-hoito-tunniste
                                                                                                 mhu/kolmansien-osapuolten-vahingot-liikenneympariston-hoito-tunniste
                                                                                                 mhu/kolmansien-osapuolten-vahingot-talvihoito-tunniste}
                                                                                                tk_yt)
                                                                                     (nil? tehtavaryhma))
                                    :akilliset-hoitotyot (and (= tyyppi "akillinen-hoitotyo")
                                                              (contains? #{mhu/akilliset-hoitotyot-sorateiden-hoito-tunniste
                                                                           mhu/akilliset-hoitotyot-liikenneympariston-hoito-tunniste
                                                                           mhu/akilliset-hoitotyot-talvihoito-tunniste}
                                                                         tk_yt)
                                                              (nil? tehtavaryhma))
                                    :toimenpiteen-maaramitattavat-tyot (and (= tyyppi "laskutettava-tyo")
                                                                            (nil? tehtava)
                                                                            (nil? tehtavaryhma))
                                    :tilaajan-varaukset (and (= tyyppi "laskutettava-tyo")
                                                             (= tr_yt mhu/tilaajan-varaukset-tunniste)
                                                             (nil? tehtava))))
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
              data-kannassa (map (fn [data]
                                   (-> data
                                       (update :tk_yt str)
                                       (update :tr_yt str)))
                                 (q-map (str "SELECT kt.vuosi, kt.kuukausi, kt.summa, kt.luotu, kt.tyyppi, tk.nimi AS tehtava, tr.nimi AS tehtavaryhma,
                                                tk.yksiloiva_tunniste AS tk_yt, tr.yksiloiva_tunniste AS tr_yt
                                         FROM kustannusarvioitu_tyo kt
                                           LEFT JOIN toimenpidekoodi tk ON tk.id = kt.tehtava
                                           LEFT JOIN tehtavaryhma tr ON tr.id = kt.tehtavaryhma
                                         WHERE kt.toimenpideinstanssi=" toimenpideinstanssi ";")))
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
              data-kannassa (map (fn [data]
                                   (-> data
                                       (update :tk_yt str)
                                       (update :tr_yt str)))
                                 (q-map (str "SELECT kt.vuosi, kt.kuukausi, kt.summa, kt.muokattu, kt.tyyppi, tk.nimi AS tehtava, tr.nimi AS tehtavaryhma,
                                                     tk.yksiloiva_tunniste AS tk_yt, tr.yksiloiva_tunniste AS tr_yt
                                              FROM kustannusarvioitu_tyo kt
                                                LEFT JOIN toimenpidekoodi tk ON tk.id = kt.tehtava
                                                LEFT JOIN tehtavaryhma tr ON tr.id = kt.tehtavaryhma
                                              WHERE kt.toimenpideinstanssi=" toimenpideinstanssi ";")))
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
        urakan-aloitus-vuosi (pvm/vuosi (ffirst (q (str "SELECT alkupvm FROM urakka WHERE id = " urakka-id))))
        paivitys-hoitokaudennumero 3
        tallennettava-data (data-gen/tallenna-johto-ja-hallintokorvaus-data urakka-id urakan-aloitus-vuosi)
        paivitettava-data (data-gen/tallenna-johto-ja-hallintokorvaus-data urakka-id urakan-aloitus-vuosi {:hoitokaudet (into #{} (range paivitys-hoitokaudennumero 6))
                                                                                                           :ennen-urakkaa-mukaan? false})]
    (testing "Tallennus onnistuu"
      (doseq [{:keys [toimenkuva maksukausi] :as parametrit} tallennettava-data]
        (let [vastaus (bs/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ parametrit)]
          (is (:onnistui? vastaus) (str "Tallennus ei onnistunut toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi)))))
    (testing "Data kannassa on oikein"
      (let [tallennettu-data (q-map (str "SELECT j_h.tunnit, j_h.tuntipalkka, j_h.kuukausi, j_h.vuosi,
                                                 tk.toimenkuva, j_h.luotu, j_h.\"ennen-urakkaa\", j_h.\"osa-kuukaudesta\"
                                          FROM johto_ja_hallintokorvaus j_h
                                            JOIN johto_ja_hallintokorvaus_toimenkuva tk ON tk.id = j_h.\"toimenkuva-id\"
                                          WHERE j_h.\"urakka-id\"=" urakka-id " AND tk.\"urakka-id\" IS NULL"))
            tallennettu-data (map (fn [data]
                                    (-> data
                                        (update :tunnit float)
                                        (update :tuntipalkka float)
                                        (update :kuukausi int)
                                        (update :vuosi int)
                                        (update :osa-kuukaudesta int)))
                                  tallennettu-data)
            tallennettu-data-ilman-ennen-urakkaa (remove :ennen-urakkaa tallennettu-data)
            tallennettu-data-kentalle-ennen-urakkaa (filter :ennen-urakkaa tallennettu-data)

            td-ryhmitelty (group-by :toimenkuva tallennettu-data-ilman-ennen-urakkaa)
            td-ryhmitelty (reduce-kv (fn [m toimenkuva data-toimenkuvalle]
                                       (assoc m
                                              toimenkuva
                                              (if (#{"päätoiminen apulainen"
                                                     "apulainen/työnjohtaja"}
                                                   toimenkuva)
                                                {:kesa (filter #(<= 5 (:kuukausi %) 9)
                                                               data-toimenkuvalle)
                                                 :talvi (remove #(<= 5 (:kuukausi %) 9)
                                                                data-toimenkuvalle)}
                                                {:molemmat data-toimenkuvalle})))
                                     {}
                                     td-ryhmitelty)]
        (is (every? :luotu tallennettu-data))
        (doseq [{:keys [toimenkuva maksukausi ennen-urakkaa? jhk-tiedot]} tallennettava-data
                :let [tallennettu-data-maksukaudelle (get-in td-ryhmitelty [toimenkuva maksukausi])]
                :when (not ennen-urakkaa?)]
          (doseq [{:keys [kuukausi vuosi] :as jhk} jhk-tiedot
                  :let [tallennettu-data-kuukaudelle (some #(when (and (= (:kuukausi %) kuukausi)
                                                                       (= (:vuosi %) vuosi))
                                                              %)
                                                           tallennettu-data-maksukaudelle)]]
            (is (= (-> jhk
                       (update :tunnit float)
                       (update :tuntipalkka float))
                   (select-keys tallennettu-data-kuukaudelle #{:tunnit :tuntipalkka :vuosi :kuukausi :osa-kuukaudesta})))))
        (is (= 5 (count tallennettu-data-kentalle-ennen-urakkaa)))
        (is (every? #(= 10 (:kuukausi %)) tallennettu-data-kentalle-ennen-urakkaa))))
    (testing "Päivitys onnistuu"
      (doseq [{:keys [toimenkuva maksukausi] :as parametrit} paivitettava-data]
        (let [vastaus (bs/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ parametrit)]
          (is (:onnistui? vastaus) (str "Päivittäminen ei onnistunut toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi)))))
    (testing "Päivitetty data kannassa on oikein"
      (let [tallennettu-data (q-map (str "SELECT j_h.tunnit, j_h.tuntipalkka, j_h.kuukausi, j_h.vuosi,
                                                 tk.toimenkuva, j_h.muokattu, j_h.\"ennen-urakkaa\", j_h.\"osa-kuukaudesta\"
                                          FROM johto_ja_hallintokorvaus j_h
                                            JOIN johto_ja_hallintokorvaus_toimenkuva tk ON tk.id = j_h.\"toimenkuva-id\"
                                          WHERE j_h.\"urakka-id\"=" urakka-id " AND tk.\"urakka-id\" IS NULL"))
            tallennettu-data (map (fn [data]
                                    (-> data
                                        (update :tunnit float)
                                        (update :tuntipalkka float)
                                        (update :kuukausi int)
                                        (update :vuosi int)
                                        (update :osa-kuukaudesta int)))
                                  tallennettu-data)
            tallennettu-data-ilman-ennen-urakkaa (remove :ennen-urakkaa tallennettu-data)
            tallennettu-data-kentalle-ennen-urakkaa (filter :ennen-urakkaa tallennettu-data)
            tallennettu-data-hoitokaudella (mapv (fn [{:keys [vuosi kuukausi] :as data}]
                                                   (assoc data :hoitokausi (inc (- (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/luo-pvm vuosi (dec kuukausi) 15))))
                                                                                   (pvm/vuosi (pvm/hoitokauden-alkupvm urakan-aloitus-vuosi))))))
                                                 tallennettu-data-ilman-ennen-urakkaa)]
        (doseq [{:keys [toimenkuva maksukausi jhk-tiedot]} paivitettava-data]
          (let [vanhat-tallennettava-jhkt (keep (fn [{:keys [vuosi kuukausi] :as data}]
                                                  (when (< (inc (- (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/luo-pvm vuosi (dec kuukausi) 15))))
                                                                   (pvm/vuosi (pvm/hoitokauden-alkupvm urakan-aloitus-vuosi))))
                                                           paivitys-hoitokaudennumero)
                                                    (-> data
                                                        (update :tunnit float)
                                                        (update :tuntipalkka float))))
                                                (some (fn [{vanha-toimenkuva :toimenkuva
                                                            vanha-jhkt :jhk-tiedot
                                                            vanha-maksukausi :maksukausi
                                                            ennen-urakkaa? :ennen-urakkaa?}]
                                                        (when (and (= vanha-toimenkuva toimenkuva)
                                                                   (= vanha-maksukausi maksukausi)
                                                                   (not ennen-urakkaa?))
                                                          vanha-jhkt))
                                                      tallennettava-data))
                paivitetyt-tallennettava-jhkt (keep (fn [data]
                                                      (-> data
                                                          (update :tunnit float)
                                                          (update :tuntipalkka float)))
                                                    jhk-tiedot)
                vanhat-kannassa-jhkt (keep (fn [{:keys [hoitokausi] :as data}]
                                             (when (and (< hoitokausi
                                                           paivitys-hoitokaudennumero)
                                                        (= toimenkuva (:toimenkuva data))
                                                        (= maksukausi (maksukausi-jhlle-kannasta data)))
                                               data))
                                           tallennettu-data-hoitokaudella)

                paivitetyt-kannassa-jhkt (keep (fn [{:keys [hoitokausi] :as data}]
                                                 (when (and (>= hoitokausi
                                                                paivitys-hoitokaudennumero)
                                                            (= toimenkuva (:toimenkuva data))
                                                            (= maksukausi (maksukausi-jhlle-kannasta data)))
                                                   data))
                                               tallennettu-data-hoitokaudella)
                sorttaus-fn (juxt :vuosi :kuukausi)]

            (is (every? :muokattu paivitetyt-kannassa-jhkt))
            (is (= 5 (count tallennettu-data-kentalle-ennen-urakkaa)))
            (is (every? #(= 10 (:kuukausi %)) tallennettu-data-kentalle-ennen-urakkaa))
            (is (= (sort-by sorttaus-fn (map #(select-keys % #{:vuosi :kuukausi :tunnit :tuntipalkka :osa-kuukaudesta}) vanhat-tallennettava-jhkt))
                   (sort-by sorttaus-fn (map #(select-keys % #{:vuosi :kuukausi :tunnit :tuntipalkka :osa-kuukaudesta}) vanhat-kannassa-jhkt)))
                (str "Vanha data ei kannassa oikein toimenkuvalle: " toimenkuva " ja maksukaudelle: " maksukausi))
            (is (= (sort-by sorttaus-fn (map #(select-keys % #{:vuosi :kuukausi :tunnit :tuntipalkka :osa-kuukaudesta}) paivitetyt-tallennettava-jhkt))
                   (sort-by sorttaus-fn (map #(select-keys % #{:vuosi :kuukausi :tunnit :tuntipalkka :osa-kuukaudesta}) paivitetyt-kannassa-jhkt)))
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
        (is (every? #(=marginaalissa? (pyorista (:tavoitehinta %)) (pyorista uusi-tavoitehinta) 0.001) data-kannassa) "Tavoitehinta ei tallentunut kantaan oikein")
        (is (every? #(=marginaalissa? (pyorista (:kattohinta %)) (pyorista (* kerroin uusi-tavoitehinta)) 0.001) data-kannassa) "Kattohinta ei tallentunut kantaan oikein")))
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
        (is (every? #(=marginaalissa? (pyorista (:tavoitehinta %)) (pyorista uusi-tavoitehinta) 0.001) vanhadata-kannassa) "Tavoitehinta ei oikein päivityksen jälkeen")
        (is (every? #(=marginaalissa? (pyorista (:kattohinta %)) (pyorista (* kerroin uusi-tavoitehinta)) 0.001) vanhadata-kannassa) "Kattohinta ei oikein päivityksen jälkeen")
        (is (every? #(=marginaalissa? (pyorista (:tavoitehinta %)) (pyorista paivitetty-tavoitehinta) 0.001) uusidata-kannassa) "Päivitetty tavoitehinta ei oikein päivityksen jälkeen")
        (is (every? #(=marginaalissa? (pyorista (:kattohinta %)) (pyorista (* kerroin paivitetty-tavoitehinta)) 0.001) uusidata-kannassa) (str "Päivitetty kattohinta ei oikein päivityksen jälkeen: " (pyorista (:kattohinta (first uusidata-kannassa))) " == " (pyorista (* kerroin paivitetty-tavoitehinta))))))))

(deftest budjettisuunnittelun-oikeustarkastukset
  (let [urakka-id (hae-rovaniemen-maanteiden-hoitourakan-id)]
    (testing "budjetoidut-tyot kutsun oikeustarkistus"
      (is (= (try+ (bs/hae-urakan-budjetoidut-tyot (:db jarjestelma) +kayttaja-seppo+ {:urakka-id urakka-id})
                   (catch harja.domain.roolit.EiOikeutta eo#
                     :ei-oikeutta-virhe))
             :ei-oikeutta-virhe)))
    (testing "hae-urakan-indeksikertoimet"
      (is (= (try+ (bs/hae-urakan-indeksikertoimet (:db jarjestelma) +kayttaja-seppo+ {:urakka-id urakka-id})
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