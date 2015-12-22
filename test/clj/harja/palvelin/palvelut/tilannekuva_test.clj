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

(deftest hae-toteumat
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-tilannekuvaan +kayttaja-jvh+ parametrit-laaja-historia)]
    (is (>= (count (:toteumat vastaus)) 0))
    (is (>= (count (:turvallisuuspoikkeamat vastaus)) 1))
    (is (>= (count (:tarkastukset vastaus)) 1))
    (is (>= (count (:laatupoikkeamat vastaus)) 1))
    (is (>= (count (:paikkaus vastaus)) 1))
    (is (>= (count (:paallystys vastaus)) 1))
    (is (>= (count (:ilmoitukset vastaus)) 1))))