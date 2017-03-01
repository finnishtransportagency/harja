(ns harja.palvelin.palvelut.yllapitokohteet-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet :refer :all]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
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

(def yllapitokohde-testidata {:kohdenumero 999
                              :nimi "Testiramppi4564ddf"
                              :yllapitokohdetyotyyppi :paallystys
                              :sopimuksen_mukaiset_tyot 400
                              :bitumi_indeksi 123
                              :kaasuindeksi 123})

(def yllapitokohdeosa-testidata {:nimi "Testiosa123456"
                                 :tr-numero 20
                                 :tr-alkuosa 1
                                 :tr-alkuetaisyys 1
                                 :tr-loppuosa 2
                                 :tr-loppuetaisyys 2
                                 :kvl 4
                                 :nykyinen_paallyste 2
                                 :toimenpide "Ei tehdä mitään"})

(defn yllapitokohde-id-jolla-on-paallystysilmoitus []
  (ffirst (q (str "SELECT yllapitokohde.id as paallystyskohde_id
                   FROM yllapitokohde
                   JOIN paallystysilmoitus ON yllapitokohde.id = paallystysilmoitus.paallystyskohde
                   WHERE urakka = " (hae-muhoksen-paallystysurakan-id) " AND sopimus = " (hae-muhoksen-paallystysurakan-paasopimuksen-id) ";"))))


(deftest paallystyskohteet-haettu-oikein
  (let [kohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-yllapitokohteet +kayttaja-jvh+
                            {:urakka-id @muhoksen-paallystysurakan-id
                             :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id})
        kohteiden-lkm (ffirst (q
                                (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @muhoksen-paallystysurakan-id ")")))
        leppajarven-ramppi (first (filter #(= (:nimi %) "Leppäjärven ramppi")
                                          kohteet))]
    (is (= (count kohteet) kohteiden-lkm) "Päällystyskohteiden määrä")
    (is (== (:maaramuutokset leppajarven-ramppi) 205)
        "Leppäjärven rampin määrämuutos laskettu oikein")))

(deftest paallystyskohteet-haettu-oikein-vuodelle-2017
  (let [res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-yllapitokohteet +kayttaja-jvh+
                            {:urakka-id @muhoksen-paallystysurakan-id
                             :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
                             :vuosi 2017})
        kohteiden-lkm (ffirst (q
                                (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @muhoksen-paallystysurakan-id ")
                                      AND vuodet @> ARRAY[2017]::int[]")))]
    (is (> (count res) 0) "Päällystyskohteita löytyi")
    (is (= (count res) kohteiden-lkm) "Löytyi oikea määrä kohteita")))

(deftest paallystyskohteet-haettu-oikein-vuodelle-2016
  (let [res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-yllapitokohteet +kayttaja-jvh+
                            {:urakka-id @muhoksen-paallystysurakan-id
                             :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
                             :vuosi 2016})]
    (is (= (count res) 0) "Ei päällystyskohteita vuodelle 2016")))

(deftest tallenna-paallystyskohde-kantaan
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        urakan-geometria-ennen-muutosta (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :sopimus-id sopimus-id
                                                              :kohteet [yllapitokohde-testidata]})
    (let [maara-lisayksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
          kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :urakan-yllapitokohteet
                                           +kayttaja-jvh+ {:urakka-id urakka-id
                                                           :sopimus-id sopimus-id})
          urakan-geometria-lisayksen-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))]
      (log/debug "Kohteet kannassa: " (pr-str kohteet-kannassa))
      (is (not (nil? kohteet-kannassa)))
      (is (not= urakan-geometria-ennen-muutosta urakan-geometria-lisayksen-jalkeen "Urakan geometria päivittyi"))
      (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))

      ;; Edelleen jos ylläpitokohde poistetaan, niin myös geometria päivittyy
      (let [lisatty-kohde (first (filter #(= (:nimi %) "Testiramppi4564ddf") kohteet-kannassa))
            _ (is lisatty-kohde "Lisätty kohde löytyi vastauksesta")
            lisatty-kohde (assoc lisatty-kohde :poistettu true)
            kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                       :sopimus-id sopimus-id
                                                                                       :kohteet [lisatty-kohde]})
            poistettu-kohde (first (filter #(= (:nimi %) "Testiramppi4564ddf") kohteet-kannassa))
            urakan-geometria-poiston-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))]

        (is (nil? poistettu-kohde) "Poistettua kohdetta ei ole enää vastauksessa")
        (is (not= urakan-geometria-lisayksen-jalkeen urakan-geometria-poiston-jalkeen "Geometria päivittyi")))

      ;; Siivoa sotkut
      (u (str "DELETE FROM yllapitokohde WHERE nimi = 'Testiramppi4564ddf';")))))

(deftest tallenna-paallystyskohde-kantaan-vuodelle-2015
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :sopimus-id sopimus-id
                                                              :vuosi 2015
                                                              :kohteet [yllapitokohde-testidata]})
    (let [kohteet-kannassa (ffirst (q
                                     (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @muhoksen-paallystysurakan-id ")
                                      AND vuodet @> ARRAY[2015]::int[]")))]
      (is (= kohteet-kannassa 1) "Kohde tallentui oikein")
      (u (str "DELETE FROM yllapitokohde WHERE nimi = 'Testiramppi4564ddf';")))))

(deftest ala-poista-paallystyskohdetta-jolla-ilmoitus
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        maara-ennen-testia (ffirst (q
                                     (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        nykyiset-kohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :urakan-yllapitokohteet +kayttaja-jvh+
                                         {:urakka-id @muhoksen-paallystysurakan-id
                                          :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id})
        kohde-jolla-ilmoitus (first (filter :paallystysilmoitus-id nykyiset-kohteet))
        paivitetyt-kohteet (map
                             (fn [kohde] (if (= (:id kohde) (:id kohde-jolla-ilmoitus))
                                           (assoc kohde :poistettu true)
                                           kohde))
                             nykyiset-kohteet)]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :sopimus-id sopimus-id
                                                              :kohteet paivitetyt-kohteet})
    (let [maara-testin-jalkeen (ffirst (q
                                         (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
          kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :urakan-yllapitokohteet
                                           +kayttaja-jvh+ {:urakka-id urakka-id
                                                           :sopimus-id sopimus-id})]
      (is (= maara-ennen-testia maara-testin-jalkeen))
      (is (= kohteet-kannassa nykyiset-kohteet)))))

(deftest tallenna-yllapitokohdeosa-kantaan
  (let [yllapitokohde-id (yllapitokohde-id-jolla-on-paallystysilmoitus)]
    (is (not (nil? yllapitokohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          urakan-geometria-ennen-muutosta (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))
          maara-ennen-lisaysta (ffirst (q
                                         (str "SELECT count(*) FROM yllapitokohdeosa
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = yllapitokohdeosa.yllapitokohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-yllapitokohdeosat +kayttaja-jvh+ {:urakka-id urakka-id
                                                                  :sopimus-id sopimus-id
                                                                  :yllapitokohde-id yllapitokohde-id
                                                                  :osat [yllapitokohdeosa-testidata]})
      (let [maara-lisayksen-jalkeen (ffirst (q
                                              (str "SELECT count(*) FROM yllapitokohdeosa
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = yllapitokohdeosa.yllapitokohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))
            kohdeosat-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :yllapitokohteen-yllapitokohdeosat
                                               +kayttaja-jvh+ {:urakka-id urakka-id
                                                               :sopimus-id sopimus-id
                                                               :yllapitokohde-id yllapitokohde-id})
            urakan-geometria-muutoksen-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))]
        (log/debug "Kohdeosa kannassa: " (pr-str kohdeosat-kannassa))
        (is (not (nil? kohdeosat-kannassa)))
        (is (every? :sijainti kohdeosat-kannassa) "Geometria muodostettiin")
        (is (not= urakan-geometria-ennen-muutosta urakan-geometria-muutoksen-jalkeen "Urakan geometria päivittyi"))
        (is (match (first kohdeosat-kannassa)
                   {:tr-kaista nil
                    :sijainti _
                    :tr-ajorata nil
                    :tr-loppuosa 2
                    :tunnus nil
                    :tr-alkuosa 1
                    :tr-loppuetaisyys 2
                    :nimi "Testiosa123456"
                    :id _
                    :tr-alkuetaisyys 1
                    :tr-numero 20
                    :toimenpide "Ei tehdä mitään"}
                   true))
        (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))
        (u (str "DELETE FROM yllapitokohdeosa WHERE nimi = 'Testiosa123456';"))))))

(deftest tallenna-paallystysurakan-aikataulut
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        kohteet [{:kohdenumero "L03"
                  :aikataulu-paallystys-alku (pvm/->pvm-aika "19.5.2017 12:00") :aikataulu-muokkaaja 2
                  :urakka (hae-muhoksen-paallystysurakan-id),
                  :aikataulu-kohde-valmis (pvm/->pvm "29.5.2017")
                  :nimi "Leppäjärven ramppi",
                  :valmis-tiemerkintaan (pvm/->pvm-aika "23.5.2017 12:00")
                  :aikataulu-paallystys-loppu (pvm/->pvm-aika "20.5.2017 12:00"),
                  :id 1
                  :sopimus (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                  :aikataulu-muokattu (pvm/->pvm-aika "29.5.2017 12:00")
                  :aikataulu-tiemerkinta-takaraja (pvm/->pvm "1.6.2017")
                  :aikataulu-tiemerkinta-alku nil,
                  :aikataulu-tiemerkinta-loppu (pvm/->pvm "26.5.2017")}]
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-yllapitokohteiden-aikataulu +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                      :sopimus-id sopimus-id
                                                                                      :kohteet kohteet})
        maara-paivityksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        vastaus-leppajarven-ramppi (first (filter #(= "Leppäjärven ramppi" (:nimi %)) vastaus))
        odotettu {:aikataulu-kohde-valmis (pvm/->pvm "29.5.2017")
                  :aikataulu-muokkaaja 2
                  :aikataulu-paallystys-alku (pvm/->pvm-aika "19.5.2017 12:00")
                  :aikataulu-paallystys-loppu (pvm/->pvm-aika "20.5.2017 12:00")
                  :aikataulu-tiemerkinta-takaraja (pvm/->pvm "1.6.2017")
                  :aikataulu-tiemerkinta-alku (pvm/->pvm-aika "22.5.2017 00:00")
                  :aikataulu-tiemerkinta-loppu (pvm/->pvm-aika "23.5.2017 00:00")
                  :id 1
                  :kohdenumero "L03"
                  :nimi "Leppäjärven ramppi"
                  :sopimus 8
                  :urakka 5
                  :valmis-tiemerkintaan (pvm/->pvm-aika "23.5.2017 12:00")}]
    (is (= maara-ennen-lisaysta maara-paivityksen-jalkeen (count vastaus)))
    (is (= (:aikataulu-paallystys-alku odotettu) (:aikataulu-paallystys-alku vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-paallystys-alku")
    (is (= (:aikataulu-paallystys-loppu odotettu) (:aikataulu-paallystys-loppu vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-paallystys-loppu")
    (is (= (:aikataulu-tiemerkinta-takaraja odotettu) (:aikataulu-tiemerkinta-takaraja vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-tiemerkinta-takaraja")
    (is (= (:aikataulu-tiemerkinta-alku odotettu) (:aikataulu-tiemerkinta-alku vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-tiemerkinta-alku")
    (is (= (:aikataulu-tiemerkinta-loppu odotettu) (:aikataulu-tiemerkinta-loppu vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-tiemerkinta-loppu")
    (is (= (:aikataulu-kohde-valmis odotettu) (:aikataulu-kohde-valmis vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-kohde-valmis")))

(deftest testidatassa-validit-kohteet
  ;; On kiva jos meidän oma testidata on validia
  (let [kohteet (q "SELECT
                   ypko.tr_numero,
                   ypko.tr_alkuosa,
                   ypko.tr_alkuetaisyys,
                   ypko.tr_loppuosa,
                   ypko.tr_loppuetaisyys,
                   ypk.tr_numero as kohde_tr_numero,
                   ypk.tr_alkuosa as kohde_tr_alkuosa,
                   ypk.tr_alkuetaisyys as kohde_tr_alkuetaisyys,
                   ypk.tr_loppuosa as kohde_tr_loppuosa,
                   ypk.tr_loppuetaisyys as kohde_tr_loppuetaisyys
                   FROM yllapitokohdeosa ypko
                   LEFT JOIN yllapitokohde ypk ON ypko.yllapitokohde = ypk.id;")]
    (doseq [kohde kohteet]
      (is (= (get kohde 0) (get kohde 5)) "Alikohteen tienumero on sama kuin pääkohteella")
      (is (and (>= (get kohde 1) (get kohde 6))
               (<= (get kohde 1) (get kohde 8))) "Alikohde on kohteen sisällä")
      (is (and (>= (get kohde 3) (get kohde 6))
               (<= (get kohde 3) (get kohde 8))) "Alikohde on kohteen sisällä"))))
