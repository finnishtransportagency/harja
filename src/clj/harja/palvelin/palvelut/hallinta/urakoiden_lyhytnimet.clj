(ns harja.palvelin.palvelut.hallinta.urakoiden-lyhytnimet
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as q]
            [harja.domain.urakka :as ur]))

(defn- parsi-urakkatyyppi [params]
  (let [urakka-tyyppi (:urakkatyyppi params)
        arvo (:arvo urakka-tyyppi)]
    (println "urakkatyyppi: " urakka-tyyppi)
    (println "urakka-arvo " arvo)
    (when arvo (name arvo))))
(defn hae-urakoiden-nimet [db user params]
  (let [urakkatyyppi (parsi-urakkatyyppi params)
        ]
  (oikeudet/ei-oikeustarkistusta!)
  (log/debug "Haetaan urakoiden nimiä parametreilla: " params)
  (println "Urakkatyyppi: " urakkatyyppi)
  (into []
    (q/hae-urakoiden-nimet db {:urakkatyyppi urakkatyyppi}))))

  (defn- tallenna-urakka-nimi [db urakka]
    (let [urakkaid (:id urakka)
          lyhyt-nimi (:lyhyt_nimi urakka)]
      (assert urakkaid "Urakka id puuttuu, ei voi tallentaa lyhytnimeä!")
      (log/debug "Tallennetaan urakan lyhytnimi: " urakkaid ", nimi " (:nimi urakka) ", lyhytnimi " lyhyt-nimi)
      (q/tallenna-urakan-lyhytnimi! db {:urakka urakkaid
                                        :lyhytnimi lyhyt-nimi})))

  (defn tallenna-urakan-lyhytnimi [db tiedot]
    (let [_ (println "tallennus..." tiedot)
          urakat (:urakat tiedot)
          urakkatyyppi (parsi-urakkatyyppi tiedot)
          ]
      (oikeudet/ei-oikeustarkistusta!)
      (println "Urakkatyyppi: " urakkatyyppi)
      (doseq [urakka urakat] (tallenna-urakka-nimi db urakka))

      ;(q/hae-urakoiden-nimet db {:urakkatyyppi urakkatyyppi})))
      (hae-urakoiden-nimet db nil tiedot)))

(defrecord UrakkaLyhytnimienHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-urakoiden-nimet
      (fn [kayttaja tiedot]
        (hae-urakoiden-nimet db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :tallenna-urakan-lyhytnimi
      (fn [kayttaja tiedot]
        (tallenna-urakan-lyhytnimi db tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakoiden-nimet :tallenna-urakan-lyhytnimi)
    this))
