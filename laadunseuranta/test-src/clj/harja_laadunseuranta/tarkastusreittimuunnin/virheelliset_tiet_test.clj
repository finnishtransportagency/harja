(ns harja-laadunseuranta.tarkastusreittimuunnin.virheelliset-tiet-test
  (:require [clojure.test :refer :all]
            [harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi :as ramppianalyysi]
            [harja-laadunseuranta.tarkastusreittimuunnin.testityokalut :as tyokalut]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [com.stuartsierra.component :as component]
            [harja.domain.tierekisteri :as tr-domain]
            [harja-laadunseuranta.core :as harja-laadunseuranta]
            [harja-laadunseuranta.tarkastusreittimuunnin.virheelliset-tiet :as virheelliset-tiet]))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :mobiili-laadunseuranta
                        (component/using
                          (harja-laadunseuranta/->Laadunseuranta)
                          [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

;; HOX! Tässä tehdään testejä kannassa löytyville ajoille, joilla on tietty id.
;; Ajojen tekstuaalisen selityksen löydät: testidata/tarkastusajot.sql
;; Tai käytä #tr näkymää piirtämään pisteet kartalle.

(deftest siltaan-osuva-piste-projisoidaan-edelliseen-tiehen-ajossa-999
  (let [tarkastusajo-id 999
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")
    (is (= (count (distinct (map #(get-in % [:tr-osoite :tie]) merkinnat))) 2)
        "Osa testidatan merkinnöistä on eri tiellä (yksi osuu sillalle ja muut moottoritielle)")

    (let [korjatut-merkinnat (virheelliset-tiet/korjaa-virheelliset-tiet merkinnat)]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      ;; Korjauksen jälkeen kaikki pisteet projisoitu moottoritielle
      (is (= (count (distinct (map #(get-in % [:tr-osoite :tie]) korjatut-merkinnat))) 1)
          "Korjauksen jälkeen kaikki pisteet projisoitu moottoritielle")
      (is (every? #(= 4 %) (map #(get-in % [:tr-osoite :tie]) korjatut-merkinnat))))))

(deftest siltaan-osuva-piste-projisoidaan-edelliseen-tiehen-oikeassa-ajossa-1
  (let [tarkastusajo-id 1
        merkinnat (q/hae-reitin-merkinnat-tieosoitteilla (:db jarjestelma)
                                                         {:tarkastusajo tarkastusajo-id
                                                          :laheiset_tiet_threshold 100})]

    (is (> (count merkinnat) 1) "Ainakin yksi merkintä testidatassa")

    (let [korjatut-merkinnat (virheelliset-tiet/korjaa-virheelliset-tiet merkinnat)
          osa-3-tie-4 (take (- 301 92) (drop 92 korjatut-merkinnat))]
      (is (= (count korjatut-merkinnat) (count merkinnat)))
      ;; Kaikki pisteet osuvat tiehen 4 paitsi yksi, joka osuu eri tielle
      ;; yli-/alikulun kohdalla. Varmistutaan siitä, että kyseinen piste on korjattu
      ;; oikealle tielle
      (is (every? #(= (get-in % [:tr-osoite :tie]) 4) osa-3-tie-4)))))