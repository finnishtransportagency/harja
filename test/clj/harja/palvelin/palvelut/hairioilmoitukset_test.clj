(ns harja.palvelin.palvelut.hairioilmoitukset-test 

  (:require [clojure.test :refer :all]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.hairioilmoitukset :as hairioilmoitukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [clojure.string :as s]
            [slingshot.slingshot :refer [try+]]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t])
  (:import (harja.domain.roolit EiOikeutta)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hairioilmoitukset (component/using
                               (hairioilmoitukset/->Hairioilmoitukset)
                               [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest tallenna-kaikki-toimii
  (let [uusi-hairio {::hairio/tyyppi :hairio,
                     ::hairio/viesti "Nyt on paha tilanne! Sima on loppu!",
                     ::hairio/alkuaika #inst "2025-06-10T07:00:00.000-00:00",
                     ::hairio/loppuaika #inst "2025-06-10T08:00:00.000-00:00"}
        ;; -----------------------------------
        ;; Inserttaa häiriöilmoitus
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :aseta-hairioilmoitus +kayttaja-jvh+ uusi-hairio)
        id (-> vastaus :vanhat first ::hairio/id)
        viesti (-> vastaus :vanhat first ::hairio/viesti)

        _ (is (some? id) "Uusi häiriö pitäisi olla kannassa")
        _ (is (= (::hairio/viesti uusi-hairio) viesti) "Uusi häiriö pitäisi olla kannassa")

        ;; -----------------------------------
        ;; Päivitä olemassa oleva häiriöilmoitus, mikä juuri insertattiin
        paivitetty-hairio {::hairio/pvm #inst "2025-06-06T11:27:24.000-00:00",
                           ::hairio/loppuaika #inst "2025-06-09T08:00:00.000-00:00",
                           ::hairio/voimassa? true,
                           ::hairio/id id,
                           ::hairio/viesti "Nyt on paha tilanne! Sima on loppu!",
                           ::hairio/alkuaika #inst "2025-06-08T07:00:00.000-00:00",
                           ::hairio/tyyppi :hairio}

        paivitetyt {:tiedot (list paivitetty-hairio)}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-hairioilmoitukset +kayttaja-jvh+ paivitetyt)
        odotettu-viesti (-> paivitetty-hairio ::hairio/viesti)
        vastaus-viesti (-> vastaus :vanhat first ::hairio/viesti)
        _ (is (= vastaus-viesti odotettu-viesti) "Päivityksen pitäisi onnistua")

        ;; -----------------------------------
        ;; Testaa häiriöilmoituksen poisto, samalla palvelulla
        poistettu-hairio (assoc paivitetty-hairio :poistettu true)
        paivitetyt {:tiedot (list poistettu-hairio)}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-hairioilmoitukset +kayttaja-jvh+ paivitetyt)
        _ (is (= (count (:vanhat vastaus)) 0) "Ilmoitus pitäisi poistua kannasta")


        ;; -----------------------------------
        ;; Tee 2 ilmoitusta 
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :aseta-hairioilmoitus +kayttaja-jvh+ uusi-hairio)
        id (-> vastaus :vanhat first ::hairio/id)
        viesti (-> vastaus :vanhat first ::hairio/viesti)

        _ (is (some? id) "Uusi häiriö pitäisi olla kannassa")
        _ (is (= (::hairio/viesti uusi-hairio) viesti) "Uusi häiriö pitäisi olla kannassa")

        toinen-hairio {::hairio/pvm #inst "2025-06-10T11:27:24.000-00:00",
                       ::hairio/loppuaika #inst "2025-11-09T08:00:00.000-00:00",
                       ::hairio/viesti "Tultiin miihaelin kanssa holvista läpi",
                       ::hairio/alkuaika #inst "2025-06-10T11:27:24.000-00:00",
                       ::hairio/tyyppi :hairio}

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :aseta-hairioilmoitus +kayttaja-jvh+ toinen-hairio)
        _ (is (= (count (:vanhat vastaus)) 2) "Kannassa pitäisi olla 2 ilmoitusta")

        
        hairio-2-id (-> vastaus second ::hairio/id)

        ;; -----------------------------------
        ;; Aseta ilmoitukselle invalid aikaväli
        leikkaava-muokkaus {::hairio/pvm #inst "2025-06-06T11:27:24.000-00:00",
                            ::hairio/loppuaika #inst "2025-06-06T08:00:00.000-00:00",
                            ::hairio/voimassa? true,
                            ::hairio/id hairio-2-id,
                            ::hairio/viesti "Nyt on paha tilanne! Sima on loppu!",
                            ::hairio/alkuaika #inst "2025-06-12T07:00:00.000-00:00",
                            ::hairio/tyyppi :hairio}

        ;; Pitäisi palautua jokin virhe 
        paivitetyt {:tiedot (list leikkaava-muokkaus)}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-hairioilmoitukset +kayttaja-jvh+ paivitetyt)
        virhe (-> vastaus first :virhe)

        _ (is (some? virhe) "Virhe pitäisi olla olemassa")
        _ (is (= virhe "Alkuajan pitäisi olla ennen loppuaikaa.") "Odotettu virhe tapahtuu")


        ;; -----------------------------------
        ;; Aseta toisen ilmoituksen aikaväli leikkaamaan ensimmäistä
        leikkaava-muokkaus {::hairio/pvm #inst "2025-06-06T11:27:24.000-00:00",
                            ::hairio/loppuaika #inst "2025-06-12T08:00:00.000-00:00",
                            ::hairio/voimassa? true,
                            ::hairio/id hairio-2-id,
                            ::hairio/viesti "Nyt on paha tilanne! Sima on loppu!",
                            ::hairio/alkuaika #inst "2025-06-06T07:00:00.000-00:00",
                            ::hairio/tyyppi :hairio}

        paivitetyt {:tiedot (list leikkaava-muokkaus)}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-hairioilmoitukset +kayttaja-jvh+ paivitetyt)

        ;; Virhe pitäisi syntyä leikkavasta aikavälistä 
        virhe (-> vastaus first :virhe)
        _ (is (some? virhe) "Virhe pitäisi olla olemassa")
        _ (is (= virhe "Annettu aikaväli on päällekäinen olemassaolevan häiriöilmoituksen kanssa.") "Odotettu virhe tapahtuu")]))

(deftest kaikki-saavat-hakea-tuoreimman-hairioilmoituksen
  (let [vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :hae-voimassaoleva-hairioilmoitus
                  +kayttaja-tero+
                  {})]
    (is (map? vastaus))
    (is (= (first (keys vastaus)) :hairioilmoitus))))

(deftest ajastetut-hairioilmoitukset-toimii
  (testing "Päättyvä häiriöilmoitus näkyy"
    (let [_tee-paattyva-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :aseta-hairioilmoitus
                                         +kayttaja-jvh+
                                         {::hairio/viesti "test 1"
                                          ::hairio/loppuaika (pvm/dateksi (t/from-now (t/days 1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus ::hairio/viesti]) "test 1")))))

(deftest paattynyt-hairio-ilmoitus-ei-nay
  (testing "Päättynyt häiriöilmoitus ei näy"
    (let [_tee-paattynyt-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :aseta-hairioilmoitus
                                          +kayttaja-jvh+
                                          {::hairio/viesti "test 2"
                                           ::hairio/loppuaika (pvm/dateksi (t/from-now (t/days -1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus]) nil)))))

(deftest tuleva-hairio-ei-nay
  (testing "Tuleva häiriöilmoitus ei näy"
    (let [_tee-alkava-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :aseta-hairioilmoitus
                                       +kayttaja-jvh+
                                       {::hairio/viesti "test 3"
                                        ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days 1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus]) nil)))))

(deftest ajastettuna-alkanut-hairio-nakyy
  (testing "Ajastettuna alkava häiriöilmoitus näkyy"
    (let [_tee-alkanut-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :aseta-hairioilmoitus
                                        +kayttaja-jvh+
                                        {::hairio/viesti "test 4"
                                         ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days -1)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :hae-voimassaoleva-hairioilmoitus
                    +kayttaja-tero+
                    {})]
      (is (= (get-in vastaus [:hairioilmoitus ::hairio/viesti]) "test 4")))))

(deftest hairion-pois-paalta-laittaminen
  (let [_tee-alkanut-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :aseta-hairioilmoitus
                                      +kayttaja-jvh+
                                      {::hairio/viesti "test 1"
                                       ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days -1)))})
        vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :hae-hairioilmoitukset
                  +kayttaja-jvh+
                  {})
        _laita_pois_paalta (kutsu-palvelua
                             (:http-palvelin jarjestelma)
                             :aseta-hairioilmoitus-pois
                             +kayttaja-jvh+
                             {::hairio/id (::hairio/id (:hairio (:voimassaolevat-tyypeittain vastaus)))})
        poiston-jalkeen (kutsu-palvelua
                          (:http-palvelin jarjestelma)
                          :hae-hairioilmoitukset
                          +kayttaja-jvh+
                          {})]
    (is (is (::hairio/voimassa? (:hairio (:voimassaolevat-tyypeittain vastaus)))))
    (is (= (::hairio/voimassa? (first  (:vanhat poiston-jalkeen))) false))))

(deftest hairion-loppuaika-ennen-alkuaikaa
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :aseta-hairioilmoitus
                  +kayttaja-jvh+
                  {::hairio/viesti "test 1"
                   ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days 1)))
                   ::hairio/loppuaika (pvm/nyt)})]

    (is (:virhe (first vastaus)))
    (is (s/includes? (:virhe (first vastaus)) "pitäisi olla ennen loppuaikaa"))))

(deftest hairion-loppuaika-sama-kuin-alkuaika
  (let [aika (pvm/nyt)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :aseta-hairioilmoitus
                  +kayttaja-jvh+
                  {::hairio/viesti "test 1"
                   ::hairio/alkuaika aika
                   ::hairio/loppuaika aika})]

    (is (:virhe (first vastaus)))
    (is (s/includes? (:virhe (first vastaus)) "eivät voi olla samat"))))

(deftest hairion-aikavali-leikkaa-aiempaa
  (testing "Talletettavan häiriöilmoituksen aikaväli leikkaa aiemmin talletetun äiriöilmoituksen aikaväliä"
    (let [_hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                            :aseta-hairioilmoitus
                            +kayttaja-jvh+
                            {::hairio/viesti "test 1"
                             ::hairio/alkuaika (pvm/nyt)
                             ::hairio/loppuaika (pvm/dateksi (t/from-now (t/days 2)))})
          vastaus (kutsu-palvelua
                    (:http-palvelin jarjestelma)
                    :aseta-hairioilmoitus
                    +kayttaja-jvh+
                    {::hairio/viesti "test 2"
                     ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days 1)))
                     ::hairio/loppuaika (pvm/dateksi (t/from-now (t/days 3)))})]
      (is (:virhe (first vastaus)))
      (is (= (:virhe (first vastaus)) "Annettu aikaväli on päällekäinen olemassaolevan ilmoituksen kanssa.")))))

(deftest hairion-pois-paalta-laittaminen-ei-toimi-ilman-oikeuksia
  (try+
    (let [_tee-alkanut-hairioilmoitus (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :aseta-hairioilmoitus
                                        +kayttaja-jvh+
                                        {::hairio/viesti "test 1"
                                         ::hairio/alkuaika (pvm/dateksi (t/from-now (t/days -1)))})
          ilmoitukset (kutsu-palvelua
                        (:http-palvelin jarjestelma)
                        :hae-hairioilmoitukset
                        +kayttaja-jvh+
                        {})
          _ (kutsu-palvelua (:http-palvelin jarjestelma)
              :aseta-hairioilmoitus-pois
              +kayttaja-tero+
              {::hairio/id (::hairio/id (first ilmoitukset))})])
    (is false "Nyt on joku paha oikeusongelma")
    (catch EiOikeutta e
      (is e))))
