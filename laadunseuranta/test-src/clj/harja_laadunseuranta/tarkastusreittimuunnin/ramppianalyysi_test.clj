(ns harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi-test
  (:require [clojure.test :refer :all]
            [harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi :as ramppianalyysi]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [com.stuartsierra.component :as component]
            [harja.domain.tierekisteri :as tr-domain]
            [harja-laadunseuranta.core :as harja-laadunseuranta]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :harja-laadunseuranta
                        (component/using
                          (harja-laadunseuranta/->Laadunseuranta nil)
                          [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest ramppianalyysi-korjaa-virheelliset-rampit
  (let [tarkastusajo-id 754
        merkinnat (hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                       {:tarkastusajo tarkastusajo-id
                                                        :treshold 100})
        korjatut-merkinnat (ramppianalyysi/korjaa-virheelliset-rampit merkinnat)]

    ;; Ramppeja ei pitäisi enää olla, koska testattavassa ajossa osa
    ;; pisteistä on virheellisesti geometrisoitunut rampeille
    (is (not-any? #(tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))
                  korjatut-merkinnat))))