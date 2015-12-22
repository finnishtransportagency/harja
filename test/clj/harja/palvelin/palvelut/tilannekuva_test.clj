(ns harja.palvelin.palvelut.tilannekuva-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tilannekuva :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (apply tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :toteumat (component/using
                      (->Tilannekuva)
                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(def parametrit-laaja-historia
  {:hallintayksikko nil
   :urakka-id       nil
   :urakoitsija     nil
   :urakkatyyppi    :hoito
   :nykytilanne?    false
   :alue            {:xmin -550093.049087613, :ymin 6372322.595126259, :xmax 1527526.529326106, :ymax 7870243.751025201} ; Koko Suomi
   :alku            (c/to-date (t/minus (t/now) (t/years 15)))
   :loppu           (c/to-date (t/now))
   :yllapito        {:paallystys true
                     :paikkaus   true}
   :ilmoitukset     {:tyypit {:toimenpidepyynto true
                              :kysely           true
                              :tiedoitus        true}
                     :tilat  #{:avoimet :suljetut}}
   :turvallisuus    {:turvallisuuspoikkeamat true}
   :laatupoikkeamat {:tilaaja     true
                     :urakoitsija true
                     :konsultti   true}
   :tarkastukset    {:tiesto     true
                     :talvihoito true
                     :soratie    true
                     :laatu      true
                     :pistokoe   true}
   :talvi           {"auraus ja sohjonpoisto"          true
                     "suolaus"                         true
                     "pistehiekoitus"                  true
                     "linjahiekoitus"                  true
                     "lumivallien madaltaminen"        true
                     "sulamisveden haittojen torjunta" true
                     "kelintarkastus"                  true
                     "liuossuolaus"                    true
                     "aurausviitoitus ja kinostimet"   true
                     "lumensiirto"                     true
                     "paannejaan poisto"               true
                     "muu"                             true}
   :kesa            {"tiestotarkastus"            true
                     "koneellinen niitto"         true
                     "koneellinen vesakonraivaus" true

                     "liikennemerkkien puhdistus" true

                     "sorateiden muokkaushoylays" true
                     "sorateiden polynsidonta"    true
                     "sorateiden tasaus"          true
                     "sorastus"                   true

                     "harjaus"                    true
                     "pinnan tasaus"              true
                     "paallysteiden paikkaus"     true
                     "paallysteiden juotostyot"   true

                     "siltojen puhdistus"         true

                     "l- ja p-alueiden puhdistus" true
                     "muu"                        true}})

(defn aseta-filtterit-falseksi [parametrit ryhma]
  (assoc parametrit ryhma (reduce
                            (fn [eka toka]
                              (assoc eka toka false))
                            (ryhma parametrit)
                            (keys (ryhma parametrit)))))

(deftest hae-asioita-tilannekuvaan
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit-laaja-historia)]
    (is (>= (count (:toteumat vastaus)) 1))
    ;; Testaa, että toteumat tulivat useasta urakasta
    (is (not-every? #(= (first (:urakka (:toteumat vastaus))) %)
                       (mapv :urakka (:toteumat vastaus))))
    (is (>= (count (:turvallisuuspoikkeamat vastaus)) 1))
    (is (>= (count (:tarkastukset vastaus)) 1))
    (is (>= (count (:laatupoikkeamat vastaus)) 1))
    (is (>= (count (:paikkaus vastaus)) 1))
    (is (>= (count (:paallystys vastaus)) 1))
    (is (>= (count (:ilmoitukset vastaus)) 1))))

(deftest hae-urakan-toteumat
  (let [urakka-id (hae-oulun-alueurakan-2005-2010-id)
        parametrit (assoc parametrit-laaja-historia :urakka-id urakka-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit)]
    (is (true? (every? #(= % urakka-id)
                       (mapv :urakka (:toteumat vastaus)))))))

(deftest ala-hae-laatupoikkeamia
  (let [parametrit (aseta-filtterit-falseksi parametrit-laaja-historia :laatupoikkeamat)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit)]
    (is (= (count (:laatupoikkeamat vastaus)) 0))))

(deftest ala-hae-toteumia
  (let [parametrit (-> parametrit-laaja-historia
                       (aseta-filtterit-falseksi :kesa)
                       (aseta-filtterit-falseksi :talvi))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit)]
    (is (= (count (:toteumat vastaus)) 0))))

;; Päällystysurakoista ei löydy toteumia
(deftest urakkatyyppi-filter-toimii
  (let [parametrit (assoc parametrit-laaja-historia :urakkatyyppi :paallystys)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit)]
    (is (= (count (:toteumat vastaus)) 0))))

(deftest ala-hae-tarkastuksia
  (let [parametrit (aseta-filtterit-falseksi parametrit-laaja-historia :tarkastukset)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit)]
    (is (= (count (:tarkastukset vastaus)) 0))))

(deftest ala-hae-turvallisuuspoikkeamia
  (let [parametrit (aseta-filtterit-falseksi parametrit-laaja-historia :turvallisuus)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit)]
    (is (= (count (:turvallisuus vastaus)) 0))))

(deftest ala-hae-ilmoituksia
  (let [parametrit (assoc parametrit-laaja-historia :ilmoitukset {:tyypit {:toimenpidepyynto false
                                                                           :kysely           false
                                                                           :tiedoitus        false}
                                                                  :tilat  #{:avoimet :suljetut}})
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit)]
    (is (= (count (:ilmoitukset vastaus)) 0))))

(deftest ala-hae-tyokoneita-historianakymaan
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit-laaja-historia)]
    (is (= (count (:tyokoneet vastaus)) 0))))

(deftest hae-tyokoneita-nykytilaan
  (let [parametrit (assoc parametrit-laaja-historia :nykytilanne? true)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit)]
    (is (>= (count (:tyokoneet vastaus)) 1))))

(deftest ala-hae-toteumia-liian-lahelle-zoomatussa-historianakymassa
  (let [parametrit (assoc parametrit-laaja-historia :alue {:xmin 0,
                                                           :ymin 0,
                                                           :xmax 1,
                                                           :ymax 1})
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit)]
    (is (= (count (:toteumat vastaus)) 0))))

(deftest ala-hae-tyokoneita-liian-lahelle-zoomatussa-nykytilannenakymassa
  (let [parametrit (-> parametrit-laaja-historia
                       (assoc :alue {:xmin 0,
                                     :ymin 0,
                                     :xmax 1,
                                     :ymax 1})
                       (assoc :nykytilanne? true))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit)]
    (is (= (count (:tyokoneet vastaus)) 0))))