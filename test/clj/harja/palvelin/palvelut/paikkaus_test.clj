(ns harja.palvelin.palvelut.paikkaus-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.paikkaus :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [cheshire.core :as cheshire]
            [schema.core :as s]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :urakan-paikkauskohteet (component/using
                                                    (->Paikkaus)
                                                    [:http-palvelin :db])
                        :urakan-paikkausilmoitus-paikkauskohteella (component/using
                                                                         (->Paikkaus)
                                                                         [:http-palvelin :db])
                        :tallenna-paikkausilmoitus (component/using
                                                       (->Paikkaus)
                                                       [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def minipot-testidata
  {:aloituspvm          (java.sql.Date. 105 9 1)
   :valmispvm_kohde     (java.sql.Date. 105 9 2)
   :valmispvm_paikkaus  (java.sql.Date. 105 9 2)
   :ilmoitustiedot
                        {:osoitteet
                         [{:tie                5
                           :aosa               3
                           :aet                5
                           :losa               4
                           :let                6
                           :paallysteen_leveys 6
                           :paikkausneliot     4}]

                         :toteumat
                         [{:suorite         3
                           :yksikko         "km"
                           :maara           5
                           :yks_hint_alv_0  5
                           :takuupvm        (java.sql.Date. 105 9 1)}]}})


(def paikkauskohde-id-jolla-ei-ilmoitusta (ffirst (q (str "SELECT paallystyskohde.id as paallystyskohde_id
                                                             FROM paallystyskohde
                                                             FULL OUTER JOIN paikkausilmoitus ON paallystyskohde.id = paikkausilmoitus.paikkauskohde
                                                             WHERE paikkausilmoitus.id IS NULL
                                                             AND urakka = " (hae-muhoksen-paikkausurakan-id) "
                                                             AND sopimus = " (hae-muhoksen-paikkausurakan-paasopimuksen-id) ";"))))
(log/debug "Paikkauskohde id ilman ilmoitusta: " paikkauskohde-id-jolla-ei-ilmoitusta)

(def paikkauskohde-id-jolla-on-ilmoitus (ffirst (q (str "SELECT paallystyskohde.id as paallystyskohde_id
                                                         FROM paallystyskohde
                                                         JOIN paikkausilmoitus ON paallystyskohde.id = paikkausilmoitus.paikkauskohde
                                                         WHERE urakka = " (hae-muhoksen-paikkausurakan-id) " AND sopimus = " (hae-muhoksen-paikkausurakan-paasopimuksen-id) ";"))))
(log/debug "Paikkauskohde id jolla on ilmoitus: " paikkauskohde-id-jolla-on-ilmoitus)

(deftest skeemavalidointi-toimii
  (let [paikkauskohde-id paikkauskohde-id-jolla-ei-ilmoitusta]
    (is (not (nil? paikkauskohde-id)))

    (let [urakka-id @muhoksen-paikkausurakan-id
          sopimus-id @muhoksen-paikkausurakan-paasopimuksen-id
          paikkausilmoitus (-> (assoc minipot-testidata :paikkauskohde-id paikkauskohde-id)
                               (assoc-in [:ilmoitustiedot :ylimaarainen-keyword] "Olen cräppidataa, en halua kantaan :("))
          maara-ennen-pyyntoa (ffirst (q
                                        (str "SELECT count(*) FROM paikkausilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paikkausilmoitus.paikkauskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :tallenna-paikkausilmoitus
                                                    +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                    :sopimus-id         sopimus-id
                                                                    :paikkausilmoitus paikkausilmoitus})))
      (let [maara-pyynnon-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM paikkausilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paikkausilmoitus.paikkauskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]
        (is (= maara-ennen-pyyntoa maara-pyynnon-jalkeen))))))

(deftest tallenna-uusi-paikkausilmoitus-kantaan
  (let [paikkauskohde-id paikkauskohde-id-jolla-ei-ilmoitusta]
    (is (not (nil? paikkauskohde-id)))

    (let [urakka-id @muhoksen-paikkausurakan-id
          sopimus-id @muhoksen-paikkausurakan-paasopimuksen-id
          paikkausilmoitus (assoc minipot-testidata :paikkauskohde-id paikkauskohde-id)
          maara-ennen-lisaysta (ffirst (q
                                         (str "SELECT count(*) FROM paikkausilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paikkausilmoitus.paikkauskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paikkausilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                 :sopimus-id         sopimus-id
                                                                 :paikkausilmoitus paikkausilmoitus})
      (let [maara-lisayksen-jalkeen (ffirst (q
                                              (str "SELECT count(*) FROM paikkausilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paikkausilmoitus.paikkauskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))
            paikkausilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-paikkausilmoitus-paikkauskohteella
                                                        +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                        :sopimus-id         sopimus-id
                                                                        :paikkauskohde-id paikkauskohde-id})]
        (log/debug "MINIPOTTI kannassa: " (pr-str paikkausilmoitus-kannassa))
        (is (not (nil? paikkausilmoitus-kannassa)))
        (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen) "Tallennuksen jälkeen päällystysilmoituksien määrä")
        (is (= (:tila paikkausilmoitus-kannassa) :valmis))
        (u (str "DELETE FROM paikkausilmoitus WHERE paikkauskohde = " paikkauskohde-id ";"))))))

(deftest paivita-paikkausilmoitukselle-paatostiedot
  (let [paikkauskohde-id paikkauskohde-id-jolla-on-ilmoitus]
    (is (not (nil? paikkauskohde-id)))

    (let [urakka-id @muhoksen-paikkausurakan-id
          sopimus-id @muhoksen-paikkausurakan-paasopimuksen-id
          paikkausilmoitus (-> (assoc minipot-testidata :paikkauskohde-id paikkauskohde-id)
                                 (assoc :paatos :hyvaksytty)
                                 (assoc :perustelu "Hyvä ilmoitus!"))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paikkausilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                   :sopimus-id         sopimus-id
                                                                   :paikkausilmoitus paikkausilmoitus})
      (let [paikkausilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-paikkausilmoitus-paikkauskohteella
                                                        +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                        :sopimus-id         sopimus-id
                                                                        :paikkauskohde-id paikkauskohde-id})]
        (log/debug "MINIPOTTI kannassa: " (pr-str paikkausilmoitus-kannassa))
        (is (not (nil? paikkausilmoitus-kannassa)))
        (is (= (:tila paikkausilmoitus-kannassa) :lukittu))
        (is (= (:paatos paikkausilmoitus-kannassa) :hyvaksytty))
        (is (= (:perustelu paikkausilmoitus-kannassa) (:perustelu paikkausilmoitus)))
        (is (= (:ilmoitustiedot paikkausilmoitus-kannassa) (:ilmoitustiedot paikkausilmoitus)))

        ; Lukittu, ei voi enää päivittää
        (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :tallenna-paikkausilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                                                   :sopimus-id         sopimus-id
                                                                                                   :paikkausilmoitus paikkausilmoitus})))

        (u (str "UPDATE paikkausilmoitus SET
                      tila = NULL,
                      paatos = NULL,
                      perustelu = NULL
                  WHERE paikkauskohde =" paikkauskohde-id ";"))))))

(deftest ala-paivita-paikkausilmoitukselle-paatostiedot-jos-ei-oikeuksia
  (let [paikkauskohde-id paikkauskohde-id-jolla-on-ilmoitus]
    (is (not (nil? paikkauskohde-id)))

    (let [urakka-id @muhoksen-paikkausurakan-id
          sopimus-id @muhoksen-paikkausurakan-paasopimuksen-id
          paikkausilmoitus (-> (assoc minipot-testidata :paikkauskohde-id paikkauskohde-id)
                                 (assoc :paatos_taloudellinen_osa :hyvaksytty)
                                 (assoc :paatos_tekninen_osa :hyvaksytty)
                                 (assoc :perustelu_tekninen_osa "Yritän haxoroida ilmoituksen hyväksytyksi ilman oikeuksia. @:D"))]

      (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paikkausilmoitus +kayttaja-tero+ {:urakka-id          urakka-id
                                                                   :sopimus-id         sopimus-id
                                                                   :paikkausilmoitus paikkausilmoitus}))))))