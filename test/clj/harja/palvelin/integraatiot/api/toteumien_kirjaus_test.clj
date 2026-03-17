(ns harja.palvelin.integraatiot.api.toteumien-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.pistetoteuma :as api-pistetoteuma]
            [harja.palvelin.integraatiot.api.toteuma :as api-toteuma]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json-tyokalut]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.sopimukset :as q-sopimukset]
            [harja.kyselyt.toteumat :as q-toteumat]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [specql.core :refer [fetch columns]]
            [harja.domain.reittipiste :as rp]
            [harja.pvm :as pvm])
  (:import (java.util Date)))

(def kayttaja "yit-rakennus")
(def kayttaja-jvh "jvh")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
   kayttaja
   :api-pistetoteuma (component/using
                      (api-pistetoteuma/->Pistetoteuma)
                      [:http-palvelin :db :integraatioloki])
   :api-reittitoteuma (component/using
                       (api-reittitoteuma/->Reittitoteuma)
                       [:http-palvelin :db :db-replica :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(defn hae-vapaa-toteuma-ulkoinen-id []
  (let [id (rand-int 10000)
        vastaus (q (str "SELECT * FROM toteuma WHERE ulkoinen_id = '" id "';"))]
    (if (empty? vastaus) id (recur))))


(deftest tallenna-pistetoteuma
  (let [urakka (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        ulkoinen-id (hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja)
        _ (anna-kirjoitusoikeus kayttaja-jvh)
        vastaus-lisays (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja portti
                         (-> "test/resurssit/api/toteumat/pistetoteuma_yksittainen.json"
                           slurp
                           (.replace "__LAHDE__" "koneellinen")
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                           (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")))]
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
          toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy" "kokonaishintainen"]))
      (is (= (count toteuma-tehtava-idt) 1))

      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja-jvh portti
                               (-> "test/resurssit/api/toteumat/pistetoteuma_yksittainen.json"
                                 slurp
                                 (.replace "__LAHDE__" "kasin")
                                 (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                 (.replace "__ID__" (str ulkoinen-id))
                                 (.replace "__SUORITTAJA_NIMI__" "Peltikoneen Pojat Oy")
                                 (.replace "__TOTEUMA_TYYPPI__" "kokonaishintainen")))]
        (is (= 200 (:status vastaus-paivitys)))
        (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, tyyppi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))]
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy" "kokonaishintainen"]))
          (is (= (count toteuma-tehtava-idt) 1)))

        (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id))
        (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id))
        (u (str "DELETE FROM toteuma WHERE ulkoinen_id = " ulkoinen-id))))
    (let [vastaus-poisto (api-tyokalut/delete-kutsu ["/api/urakat/" urakka "/toteumat/piste"] kayttaja-jvh portti
                           (-> "test/resurssit/api/toteuman-poisto.json"
                             slurp
                             (.replace "__SOPIMUS_ID__" (str sopimus-id))
                             (.replace "__ID__" (str ulkoinen-id))
                             (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                             (.replace "__PVM__" (json-tyokalut/json-pvm (Date.)))))
          toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE poistettu IS NOT TRUE AND ulkoinen_id = " ulkoinen-id)))]
      (is (= 200 (:status vastaus-poisto)))
      (is (empty? toteuma-id)))))

(deftest tallenna-ja-poista-reittitoteuma
  (let [urakka (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        ulkoinen-id (hae-vapaa-toteuma-ulkoinen-id)
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka)
        _ (anna-kirjoitusoikeus kayttaja-jvh)
        fn-tee-kutsu (fn []
                       (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                         (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                           slurp
                           (.replace "__LAHDE__" "koneellinen")
                           (.replace "__SOPIMUS_ID__" (str sopimus-id))
                           (.replace "__ID__" (str ulkoinen-id))
                           (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy"))))

        ;; Poistetaan oikeudet 
        _ (poista-kayttajan-api-oikeudet kayttaja)
        ;; Näillä oikeuksilla ei pysty tallentamaan toteumia 
        _ (anna-lukuoikeus kayttaja)
        _ (anna-analytiikkaoikeus kayttaja)
        _ (anna-tielupaoikeus kayttaja)
        vastaus-lisays (fn-tee-kutsu)

        ;; Käyttäjällä ei ole kirjoitusoikeutta
        _ (is (= 403 (:status vastaus-lisays)) "Käyttäjältä ei löydy kirjoitus oikeuksia")
        _ (is (str/includes? (:body vastaus-lisays) "Käyttäjätunnuksella puutteelliset oikeudet") "Virheviesti löytyy")

        ;; Annetaan kirjoitus oikeudet ja tehdään kutsu uudelleen
        _ (poista-kayttajan-api-oikeudet kayttaja)
        _ (anna-kirjoitusoikeus kayttaja)
        vastaus-lisays (fn-tee-kutsu)]
    (log/info "vastaus-lisays: " vastaus-lisays)
    (is (= 200 (:status vastaus-lisays)))
    (let [toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))]
      (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tienpesijät Oy"]))

      ; Päivitetään toteumaa ja tarkistetaan, että se päivittyy
      (let [vastaus-paivitys (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja-jvh portti
                               (-> "test/resurssit/api/toteumat/reittitoteuma_yksittainen.json"
                                 slurp
                                 (.replace "__LAHDE__" "koneellinen")
                                 (.replace "__SOPIMUS_ID__" (str sopimus-id))
                                 (.replace "__ID__" (str ulkoinen-id))
                                 (.replace "__SUORITTAJA_NIMI__" "Peltikoneen Pojat Oy")))]
        (is (= 200 (:status vastaus-paivitys)))
        (let [toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              _ (odota-reittipisteet toteuma-id)
              {reittipisteet ::rp/reittipisteet} (first (fetch ds ::rp/toteuman-reittipisteet
                                                          (columns ::rp/toteuman-reittipisteet)
                                                          {::rp/toteuma-id toteuma-id}))
              toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi, lisatieto FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-tehtava-idt (into [] (flatten (q (str "SELECT id FROM toteuma_tehtava WHERE toteuma = " toteuma-id))))
              toteuma-materiaali-idt (into [] (flatten (q (str "SELECT id FROM toteuma_materiaali WHERE toteuma = " toteuma-id))))
              toteuman-materiaali (ffirst (q (str "SELECT nimi FROM toteuma_materiaali
                                                            JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
                                                            WHERE toteuma = " toteuma-id)))]
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Peltikoneen Pojat Oy" "Normisuolaus"]))
          (is (= (count reittipisteet) 3))
          (is (= (count toteuma-tehtava-idt) 3))
          (is (= (count toteuma-materiaali-idt) 1))
          (is (= toteuman-materiaali "Talvisuolaliuos NaCl"))

          (doseq [reittipiste reittipisteet]
            (let [reitti-tehtava-idt (into [] (map ::rp/toimenpidekoodi) (::rp/tehtavat reittipiste))
                  reitti-materiaali-idt (into [] (map ::rp/materiaalikoodi) (::rp/materiaalit reittipiste))
                  reitti-hoitoluokka (::rp/soratiehoitoluokka reittipiste)]
              (is (= (count reitti-tehtava-idt) 3))
              (is (= (count reitti-materiaali-idt) 1))
              (is (= reitti-hoitoluokka 7))))               ; testidatassa on reittipisteen koordinaateille hoitoluokka


          (u (str "DELETE FROM toteuman_reittipisteet WHERE toteuma = " toteuma-id))
          (u (str "DELETE FROM toteuma_materiaali WHERE toteuma = " toteuma-id))
          (u (str "DELETE FROM toteuma_tehtava WHERE toteuma = " toteuma-id)))))
    (let [vastaus-poisto (api-tyokalut/delete-kutsu ["/api/urakat/" urakka "/toteumat/reitti"] kayttaja portti
                           (-> "test/resurssit/api/toteuman-poisto.json"
                             slurp
                             (.replace "__ID__" (str ulkoinen-id))
                             (.replace "__SUORITTAJA_NIMI__" "Tienpesijät Oy")
                             (.replace "__PVM__" (json-tyokalut/json-pvm (Date.)))))
          toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE poistettu IS NOT TRUE AND ulkoinen_id = " ulkoinen-id)))
          toteuma-id-poistettu (first (q (str "SELECT id FROM toteuma WHERE poistettu IS TRUE AND ulkoinen_id = " ulkoinen-id)))]
      (is (= 200 (:status vastaus-poisto)))
      (is (empty? toteuma-id))
      (is (not-empty toteuma-id-poistettu)))))


(deftest poista-toteumat-integraatio-merkitsee-toteuman-poistetuksi
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        sopimus-id (hae-annetun-urakan-paasopimuksen-id urakka-id)
        kayttaja-id (hae-kayttajan-id-kayttajanimella kayttaja-jvh)
        ulkoinen-id (hae-vapaa-toteuma-ulkoinen-id)
        toteuma-lkm (fn [poistettu?]
                      (ffirst (q (str "SELECT count(*)\n"
                                      "  FROM toteuma\n"
                                      " WHERE urakka = " urakka-id "\n"
                                      "   AND ulkoinen_id = " ulkoinen-id "\n"
                                      "   AND poistettu IS " (if poistettu? "TRUE" "NOT TRUE") ";"))))
        alkanut (-> "30.01.2016" pvm/->pvm-date-timeksi pvm/dateksi)
        paattynyt (-> "30.01.2016" pvm/->pvm-date-timeksi pvm/dateksi)]
    (try
      (q-toteumat/luo-toteuma<!
        ds
        {:urakka urakka-id
         :sopimus sopimus-id
         :alkanut alkanut
         :paattynyt paattynyt
         :tyyppi "kokonaishintainen"
         :kayttaja kayttaja-id
         :suorittaja "Testiurakoitsija Oy"
         :ytunnus "1234567-8"
         :lisatieto "Integraatiotesti"
         :ulkoinen_id ulkoinen-id
         :reitti nil
         :numero nil
         :alkuosa nil
         :alkuetaisyys nil
         :loppuosa nil
         :loppuetaisyys nil
         :lahde "harja-api"
         :tyokonetyyppi nil
         :tyokonetunniste nil
         :tyokoneen-lisatieto nil})

      (is (= 1 (toteuma-lkm false)) "Toteuma löytyy kannasta ennen poistoa")

      (let [vastaus (api-toteuma/poista-toteumat
                     ds
                     {:id kayttaja-id :kayttajanimi kayttaja-jvh}
                     [ulkoinen-id]
                     urakka-id)]
        (is (= "Toteumat poistettu onnistuneesti. Poistettiin: 1 toteumaa."
               (:ilmoitukset vastaus)))
        (is (= 0 (toteuma-lkm false)) "Toteumaa ei löydy ei-poistettuna")
        (is (= 1 (toteuma-lkm true)) "Toteuma on merkitty poistetuksi"))
      (finally
        (u (str "DELETE FROM toteuma WHERE urakka = " urakka-id " AND ulkoinen_id = " ulkoinen-id))))))


(deftest poista-usea-toteuma-paivittaa-urakan-materiaalicachen-aikavalin-min-max
  (let [urakka-id (ffirst (q "SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'"))
        min-pvm "01.01.2019"
        max-pvm "02.01.2019"
        pvm-str->date (fn [pvm-str]
                        (-> pvm-str
                            pvm/->pvm-date-timeksi
                            pvm/dateksi))
        min-alkanut (pvm-str->date min-pvm)
        max-alkanut (pvm-str->date max-pvm)
        paivitys-args (atom nil)]
    (with-redefs [harja.kyselyt.toteumat/hae-poistettavien-toteumien-paivat-ja-aikavali-ulkoisella-idlla
                  (fn [_ _]
                    [{:alkanut min-alkanut
                      :min_alkanut min-alkanut
                      :max_alkanut max-alkanut}
                     {:alkanut max-alkanut
                      :min_alkanut min-alkanut
                      :max_alkanut max-alkanut}])

                  harja.kyselyt.toteumat/poista-toteumat-ulkoisilla-idlla-ja-luojalla!
                  (fn [& _] 2)
                  harja.kyselyt.sopimukset/hae-urakan-sopimus-idt
                  (fn [& _] [{:id 123}])
                  materiaalit/paivita-sopimuksen-materiaalin-kaytto
                  (fn [& _] nil)
                  materiaalit/paivita-urakan-materiaalin-kaytto-hoitoluokittain
                  (fn [_ args]
                    (reset! paivitys-args args)
                    nil)]
      (api-toteuma/poista-toteumat
        ds
        {:id 1 :kayttajanimi kayttaja-jvh}
        [111 222]
        urakka-id))

    (is (some? @paivitys-args) "Urakan materiaalicachen päivitystä kutsutaan")
    (is (= urakka-id (:urakka @paivitys-args)))
    (is (= (pvm/iso8601 (pvm/->pvm min-pvm))
           (pvm/iso8601 (:alkupvm @paivitys-args)))
        "Urakan materiaalicachen päivityksen alkupvm on min")
    (is (= (pvm/iso8601 (pvm/->pvm max-pvm))
           (pvm/iso8601 (:loppupvm @paivitys-args)))
        "Urakan materiaalicachen päivityksen loppupvm on max")))

(deftest poista-toteumat-palauttaa-onnistumisviestin-ja-valittaa-oikeat-argumentit
  (let [urakka-id 12345
        ulkoiset-idt [111 222]
        kirjaaja {:id 987 :kayttajanimi kayttaja-jvh}
        poisto-kutsu-args (atom nil)
        vastaus (with-redefs [harja.kyselyt.toteumat/hae-poistettavien-toteumien-paivat-ja-aikavali-ulkoisella-idlla
                              (fn [& _] [])
                              harja.kyselyt.toteumat/poista-toteumat-ulkoisilla-idlla-ja-luojalla!
                              (fn [_ kayttaja-id ulkoiset-idt* urakka-id*]
                                (reset! poisto-kutsu-args [kayttaja-id ulkoiset-idt* urakka-id*])
                                2)
                              harja.kyselyt.sopimukset/hae-urakan-sopimus-idt
                              (fn [& _] [])
                              materiaalit/paivita-sopimuksen-materiaalin-kaytto
                              (fn [& _] nil)
                              materiaalit/paivita-urakan-materiaalin-kaytto-hoitoluokittain
                              (fn [& _] nil)]
                  (api-toteuma/poista-toteumat ds kirjaaja ulkoiset-idt urakka-id))]
    (is (= "Toteumat poistettu onnistuneesti. Poistettiin: 2 toteumaa."
           (:ilmoitukset vastaus)))
    (is (= [(:id kirjaaja) ulkoiset-idt urakka-id]
           @poisto-kutsu-args)
        "Poistokyselyä kutsutaan oikeilla argumenteilla")))

(deftest poista-toteumat-palauttaa-ei-loytynyt-viestin-eika-paivita-materiaalicachea
  (let [urakka-id 12345
        ulkoiset-idt [111]
        kirjaaja {:id 987 :kayttajanimi kayttaja-jvh}
        sopimus-paivitys-kutsuttu? (atom false)
        urakka-paivitys-kutsuttu? (atom false)
        vastaus (with-redefs [harja.kyselyt.toteumat/hae-poistettavien-toteumien-paivat-ja-aikavali-ulkoisella-idlla
                              (fn [& _] [])
                              harja.kyselyt.toteumat/poista-toteumat-ulkoisilla-idlla-ja-luojalla!
                              (fn [& _] 0)
                              harja.kyselyt.sopimukset/hae-urakan-sopimus-idt
                              (fn [& _] [{:id 123}])
                              materiaalit/paivita-sopimuksen-materiaalin-kaytto
                              (fn [& _]
                                (reset! sopimus-paivitys-kutsuttu? true)
                                nil)
                              materiaalit/paivita-urakan-materiaalin-kaytto-hoitoluokittain
                              (fn [& _]
                                (reset! urakka-paivitys-kutsuttu? true)
                                nil)]
                  (api-toteuma/poista-toteumat ds kirjaaja ulkoiset-idt urakka-id))]
    (is (= "Tunnisteita vastaavia toteumia ei löytynyt käyttäjän kirjaamista urakan toteumista."
           (:ilmoitukset vastaus)))
    (is (false? @sopimus-paivitys-kutsuttu?) "Sopimuksen materiaalicachea ei päivitetä kun mitään ei poisteta")
    (is (false? @urakka-paivitys-kutsuttu?) "Urakan materiaalicachea ei päivitetä kun mitään ei poisteta")))

(deftest poista-toteumat-paivittaa-sopimusten-materiaalicachen-kaikille-uniikeille-alkupvmille
  (let [urakka-id 12345
        ulkoiset-idt [111 222]
        kirjaaja {:id 987 :kayttajanimi kayttaja-jvh}
        pvm-str->date (fn [pvm-str]
                        (-> pvm-str
                            pvm/->pvm-date-timeksi
                            pvm/dateksi))
        d1 (pvm-str->date "01.01.2019")
        d2 (pvm-str->date "02.01.2019")
        paivitykset (atom [])
        odotetut (set (for [sopimus [10 20]
                            pvm ["01.01.2019" "02.01.2019"]]
                        [sopimus pvm]))]
    (with-redefs [harja.kyselyt.toteumat/hae-poistettavien-toteumien-paivat-ja-aikavali-ulkoisella-idlla
                  (fn [& _]
                    [{:alkanut d1
                      :min_alkanut d1
                      :max_alkanut d2}
                     {:alkanut d2
                      :min_alkanut d1
                      :max_alkanut d2}])
                  harja.kyselyt.toteumat/poista-toteumat-ulkoisilla-idlla-ja-luojalla!
                  (fn [& _] 2)
                  harja.kyselyt.sopimukset/hae-urakan-sopimus-idt
                  (fn [& _] [{:id 10} {:id 20}])
                  materiaalit/paivita-sopimuksen-materiaalin-kaytto
                  (fn [_ args]
                    (swap! paivitykset conj args)
                    nil)
                  materiaalit/paivita-urakan-materiaalin-kaytto-hoitoluokittain
                  (fn [& _] nil)]
      (api-toteuma/poista-toteumat ds kirjaaja ulkoiset-idt urakka-id))

    (is (= #{10 20} (set (map :sopimus @paivitykset))) "Kaikkien sopimusten cache päivitetään")
    (is (= 4 (count @paivitykset)) "Päivityksiä tulee sopimusten_lkm * uniikkien_pvm_lkm")
    (is (= odotetut
           (set (map (fn [{:keys [sopimus alkupvm]}]
                       [sopimus (pvm/pvm alkupvm)])
                     @paivitykset)))
        "Sopimuskohtainen päivitys ajetaan jokaiselle uniikille alkupvm:lle")))

(deftest poista-toteumat-tyhjalla-alkupvm-listalla-ei-kutsu-urakan-paivitysta
  (let [urakka-id 12345
        ulkoiset-idt [111]
        kirjaaja {:id 987 :kayttajanimi kayttaja-jvh}
        urakka-paivitys-kutsuttu? (atom false)]
    (with-redefs [harja.kyselyt.toteumat/hae-poistettavien-toteumien-paivat-ja-aikavali-ulkoisella-idlla
                  (fn [& _] [])
                  harja.kyselyt.toteumat/poista-toteumat-ulkoisilla-idlla-ja-luojalla!
                  (fn [& _] 1)
                  harja.kyselyt.sopimukset/hae-urakan-sopimus-idt
                  (fn [& _] [{:id 10}])
                  materiaalit/paivita-sopimuksen-materiaalin-kaytto
                  (fn [& _] nil)
                  materiaalit/paivita-urakan-materiaalin-kaytto-hoitoluokittain
                  (fn [& _]
                    (reset! urakka-paivitys-kutsuttu? true)
                    nil)]
      (let [vastaus (api-toteuma/poista-toteumat ds kirjaaja ulkoiset-idt urakka-id)]
        (is (= "Toteumat poistettu onnistuneesti. Poistettiin: 1 toteumaa."
               (:ilmoitukset vastaus)))
        (is (false? @urakka-paivitys-kutsuttu?) "Urakan cachea ei päivitetä ilman aikaväliä")))))


