(ns harja.palvelin.palvelut.hallinta.urakoiden-lyhytnimet
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.urakat :as q]))

(defn- parsi-urakkatyyppi [params]
  (let [arvo (:arvo (:urakkatyyppi params))]
    (if (= arvo :kaikki) (assoc {} :urakkatyyppi nil) (assoc {} :urakkatyyppi (name arvo)))))

(defn- muodosta-hakuehdot [params]
  (merge (parsi-urakkatyyppi params) {:vain_puuttuvat (:vain-puuttuvat params) :urakantila (:urakan-tila params)}))
(defn hae-urakoiden-nimet [db user params]
  (let [hakuehdot (muodosta-hakuehdot params)]
    (oikeudet/ei-oikeustarkistusta!)
    (log/debug "Haetaan urakoiden nimiä parametreilla: " params)
    (log/debug "hakuehdot: " hakuehdot)
    (into []
      (q/hae-urakoiden-nimet db hakuehdot))))

(defn- tallenna-urakka-nimi [db urakka]
  (let [urakkaid (:id urakka)
        lyhyt-nimi (:lyhyt_nimi urakka)]
    (assert urakkaid "Urakka id puuttuu, ei voi tallentaa lyhytnimeä!")
    (assert lyhyt-nimi "Lyhyt nimi puuttuu, ei voi tallentaa!")
    (log/debug "Tallennetaan urakan lyhytnimi: " urakkaid ", nimi " (:nimi urakka) ", lyhytnimi " lyhyt-nimi)
    (oikeudet/ei-oikeustarkistusta!)
    (q/tallenna-urakan-lyhytnimi! db {:urakka urakkaid
                                      :lyhytnimi lyhyt-nimi})))
(defn tallenna-urakoiden-lyhytnimet [db user tiedot]
  (let [urakat (:urakat tiedot)
        haku-parametrit (:haku-parametrit tiedot)]
    (oikeudet/ei-oikeustarkistusta!)
    (doseq [urakka urakat] (tallenna-urakka-nimi db urakka))
    (hae-urakoiden-nimet db nil haku-parametrit)))

(defrecord UrakkaLyhytnimienHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-urakoiden-nimet
      (fn [kayttaja tiedot]
        (hae-urakoiden-nimet db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :tallenna-urakoiden-lyhytnimet
      (fn [kayttaja tiedot]
        (tallenna-urakoiden-lyhytnimet db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakoiden-nimet :tallenna-urakoiden-lyhytnimet)
    this))
