(ns harja.palvelin.palvelut.integraatioloki-test
  (:require [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.palvelin.palvelut.integraatioloki :as integraatioloki-palvelu]
            [harja.palvelin.palvelut.tloik.toimenpiteen-lahetyksen-kuittausanalyysi :as tloik-kuittausanalyysi]))

(deftest integraatioloki-julkaisee-vain-kanonisen-kuittausanalyysi-endpointin-ja-delegoi-siihen
  (let [julkaistut-palvelut (atom {})
        poistetut-palvelut (atom [])
        delegointikutsu (atom nil)
        odotettu-vastaus {:ryhmat [{:ilmoitusid 123}]}
        komponentti (integraatioloki-palvelu/map->Integraatioloki
                     {:db-replica :testi-db
                      :http-palvelin :testi-http})]
    (with-redefs [http-palvelin/julkaise-palvelu
                  (fn [_http-palvelin endpoint kasittelija]
                    (swap! julkaistut-palvelut assoc endpoint kasittelija))
                  http-palvelin/poista-palvelu
                  (fn [_http-palvelin endpoint]
                    (swap! poistetut-palvelut conj endpoint))
                  tloik-kuittausanalyysi/hae-toimenpiteen-lahetyksen-kuittausanalyysi
                  (fn [db kayttaja alkaen paattyen]
                    (reset! delegointikutsu [db kayttaja alkaen paattyen])
                    odotettu-vastaus)]
      (let [_ (component/start komponentti)
            kasittelija (get @julkaistut-palvelut tloik-kuittausanalyysi/kuittausanalyysi-endpoint)
            vastaus (kasittelija {:id 1}
                                 {:alkaen #inst "2026-03-20T00:00:00.000-00:00"
                                  :paattyen #inst "2026-03-20T23:59:59.000-00:00"})]
        (is (some? kasittelija))
        (is (= odotettu-vastaus vastaus))
        (is (= [:testi-db
                {:id 1}
                #inst "2026-03-20T00:00:00.000-00:00"
                #inst "2026-03-20T23:59:59.000-00:00"]
               @delegointikutsu))
        (is (not (contains? @julkaistut-palvelut :hae-epaillyt-duplikaattikuittaukset)))
        (component/stop komponentti)
        (is (some #{tloik-kuittausanalyysi/kuittausanalyysi-endpoint} @poistetut-palvelut))
        (is (not-any? #{:hae-epaillyt-duplikaattikuittaukset} @poistetut-palvelut))))))
