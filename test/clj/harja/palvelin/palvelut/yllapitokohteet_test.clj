(ns harja.palvelin.palvelut.yllapitokohteet-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.paallystys :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :urakan-yllapitokohteet (component/using
                                                  (->Yllapitokohteet)
                                                  [:http-palvelin :db])
                        :tallenna-yllapitokohdeosat (component/using
                                                      (->Yllapitokohteet)
                                                      [:http-palvelin :db])
                        :urakan-paallystysilmoitus-paallystyskohteella (component/using
                                                                         (->Paallystys)
                                                                         [:http-palvelin :db])
                        :tallenna-paallystysilmoitus (component/using
                                                       (->Paallystys)
                                                       [:http-palvelin :db])
                        :tallenna-paallystyskohde (component/using
                                                    (->Paallystys)
                                                    [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def yllapitokohde-testidata {:kohdenumero              999
                                :nimi                     "Testiramppi4564ddf"
                                :sopimuksen_mukaiset_tyot 400
                                :lisatyo                  false
                                :bitumi_indeksi           123
                                :kaasuindeksi             123})

(def yllapitokohdeosa-testidata {:nimi               "Testiosa123456"
                                   :tr_numero          1234
                                   :tr_alkuosa         3
                                   :tr_alkuetaisyys    1
                                   :tr_loppuosa        123
                                   :tr_loppuetaisyys   1
                                   :sijainti {:type :multiline :lines [{:type :line :points [[1 2] [3 4]]}]}
                                   :kvl                4
                                   :nykyinen_paallyste 2
                                   :toimenpide         "Ei tehdä mitään"})

(def yllapitokohde-id-jolla-on-paallystysilmoitus (ffirst (q (str "
                                                           SELECT yllapitokohde.id as paallystyskohde_id
                                                           FROM yllapitokohde
                                                           JOIN paallystysilmoitus ON yllapitokohde.id = paallystysilmoitus.paallystyskohde
                                                           WHERE urakka = " (hae-muhoksen-paallystysurakan-id) " AND sopimus = " (hae-muhoksen-paallystysurakan-paasopimuksen-id) ";"))))

(deftest paallystyskohteet-haettu-oikein
  (let [res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-yllapitokohteet +kayttaja-jvh+
                            {:urakka-id  @muhoksen-paallystysurakan-id
                             :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id})
        kohteiden-lkm (ffirst (q
                                (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @muhoksen-paallystysurakan-id ")")))]
    (is (= (count res) kohteiden-lkm) "Päällystyskohteiden määrä")))

(deftest tallenna-paallystyskohde-kantaan
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id  urakka-id
                                                                :sopimus-id sopimus-id
                                                                :kohteet    [yllapitokohde-testidata]})
    (let [maara-lisayksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
          kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :urakan-yllapitokohteet
                                         +kayttaja-jvh+ {:urakka-id  urakka-id
                                                         :sopimus-id sopimus-id})]
      (log/debug "Kohteet kannassa: " (pr-str kohteet-kannassa))
      (is (not (nil? kohteet-kannassa)))
      (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))
      (u (str "DELETE FROM yllapitokohde WHERE nimi = 'Testiramppi4564ddf';")))))

(deftest tallenna-paallystyskohdeosa-kantaan
  (let [yllapitokohde-id yllapitokohde-id-jolla-on-paallystysilmoitus]
    (is (not (nil? yllapitokohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          maara-ennen-lisaysta (ffirst (q
                                         (str "SELECT count(*) FROM yllapitokohdeosa
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = yllapitokohdeosa.yllapitokohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-yllapitokohdeosat +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                   :sopimus-id         sopimus-id
                                                                   :yllapitokohde-id yllapitokohde-id
                                                                   :osat [yllapitokohdeosa-testidata]})
      (let [maara-lisayksen-jalkeen (ffirst (q
                                              (str "SELECT count(*) FROM yllapitokohdeosa
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = yllapitokohdeosa.yllapitokohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))
            kohdeosat-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-yllapitokohdeosat
                                                        +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                        :sopimus-id         sopimus-id
                                                                        :yllapitokohde-id yllapitokohde-id})]
        (log/debug "Kohdeosa kannassa: " (pr-str kohdeosat-kannassa))
        (is (not (nil? kohdeosat-kannassa)))
        (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))
        (u (str "DELETE FROM yllapitokohdeosa WHERE nimi = 'Testiosa123456';"))))))

(deftest tallenna-paallystysurakan-aikataulut
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        kohteet [{:kohdenumero                 "L03", :aikataulu_paallystys_alku (pvm/->pvm-aika "19.5.2016 12:00") :aikataulu_muokkaaja 2, :urakka 5,
                  :aikataulu_kohde_valmis      (pvm/->pvm "29.5.2016"), :nimi "Leppäjärven ramppi",
                  :valmis_tiemerkintaan        (pvm/->pvm-aika "23.5.2016 12:00"), :aikataulu_paallystys_loppu (pvm/->pvm-aika "20.5.2016 12:00"),
                  :id                          1, :sopimus 8, :aikataulu_muokattu (pvm/->pvm-aika "29.5.2016 12:00"), :aikataulu_tiemerkinta_alku nil,
                  :aikataulu_tiemerkinta_loppu (pvm/->pvm "26.5.2016")}]
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-yllapitokohteiden-aikataulu +kayttaja-jvh+ {:urakka-id  urakka-id
                                                                                        :sopimus-id sopimus-id
                                                                                        :kohteet    kohteet})
        maara-paivityksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        vastaus-leppajarven-ramppi (first (filter #(= "L03" (:kohdenumero %)) vastaus))
        odotettu {:aikataulu_kohde_valmis       (pvm/->pvm "29.5.2016")
                  :aikataulu_muokkaaja         2
                  :aikataulu_paallystys_alku   (pvm/->pvm-aika "19.5.2016 12:00")
                  :aikataulu_paallystys_loppu   (pvm/->pvm-aika "20.5.2016 12:00")
                  :aikataulu_tiemerkinta_alku  nil
                  :aikataulu_tiemerkinta_loppu (pvm/->pvm "26.5.2016")
                  :id                          1
                  :kohdenumero                 "L03"
                  :nimi                        "Leppäjärven ramppi"
                  :sopimus                     8
                  :urakka                      5
                  :valmis_tiemerkintaan       (pvm/->pvm-aika "23.5.2016 12:00")}]
    (is (= maara-ennen-lisaysta maara-paivityksen-jalkeen (count vastaus)))
    (is (= (:aikataulu_paallystys_alku odotettu) (:aikataulu_paallystys_alku vastaus-leppajarven-ramppi) ) "päällystyskohteen :aikataulu_paallystys_alku")
    (is (= (:aikataulu_paallystys_loppu odotettu) (:aikataulu_paallystys_loppu vastaus-leppajarven-ramppi) ) "päällystyskohteen :aikataulu_paallystys_loppu")
    (is (= (:aikataulu_tiemerkinta_alku odotettu) (:aikataulu_tiemerkinta_alku vastaus-leppajarven-ramppi) ) "päällystyskohteen :aikataulu_tiemerkinta_alku")
    (is (= (:aikataulu_tiemerkinta_loppu odotettu) (:aikataulu_tiemerkinta_loppu vastaus-leppajarven-ramppi) ) "päällystyskohteen :aikataulu_tiemerkinta_loppu")
    (is (= (:aikataulu_kohde_valmis odotettu) (:aikataulu_kohde_valmis vastaus-leppajarven-ramppi) ) "päällystyskohteen :aikataulu_kohde_valmis")))
