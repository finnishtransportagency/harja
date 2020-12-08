(ns harja.palvelin.palvelut.selainvirhe-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.selainvirhe :refer :all]
            [harja.testi :refer :all]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :http-palvelin (testi-http-palvelin)
                        :selainvirhe (component/using
                                       (->Selainvirhe true)
                                       [:http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(deftest raportoi-yhteyskatkos-testi
  (let [kayttaja +kayttaja-jvh+
        ping-1 {:aika (pvm/luo-pvm 2000 1 1) :palvelu :ping}
        ping-2 {:aika (pvm/nyt) :palvelu :ping}
        hae-ilmoitukset {:aika (pvm/nyt) :palvelu :hae-ilmoitukset}
        yhteyskatkos {:yhteyskatkokset [ping-1 hae-ilmoitukset ping-2]}
        formatoitu-yhteyskatkos (formatoi-yhteyskatkos kayttaja yhteyskatkos)]
    (is (= formatoitu-yhteyskatkos {:text (str "Käyttäjä " (:kayttajanimi kayttaja) " (" (:id kayttaja) ")" " raportoi yhteyskatkoksista palveluissa:")
                                    :fields [{:title ":ping" :value (str "Katkoksia 2 kpl|||ensimmäinen: " (c/from-date (:aika ping-1))
                                                                         "|||viimeinen: " (c/from-date (:aika ping-2)))}
                                             {:title ":hae-ilmoitukset" :value (str "Katkoksia 1 kpl|||ensimmäinen: " (c/from-date (:aika hae-ilmoitukset))
                                                                                    "|||viimeinen: " (c/from-date (:aika hae-ilmoitukset)))}]}))))
