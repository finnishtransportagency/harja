(ns harja.palvelin.palvelut.hallinta.urakoiden-lyhytnimet
  (:require [com.stuartsierra.component :as component]
            [harja.tyokalut.muunnos :as muunnos]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.urakat :as q]
            [harja.kyselyt.jarjestelman-tila :as jarjestelma-kyselyt]
            [clojure.string :as str]))

(defn- parsi-urakkatyyppi [params]
  (let [urakkatyyppi (:urakkatyyppi params)]
    (if (= urakkatyyppi :kaikki)
      (assoc {} :urakkatyyppi nil)
      (assoc {} :urakkatyyppi (name urakkatyyppi)))))

(defn- muodosta-hakuehdot [params]
  (merge
    (parsi-urakkatyyppi params)
    {:vain-puuttuvat (:vain-puuttuvat params)
     :urakantila (name (:urakan-tila params))}))

(defn hae-urakoiden-nimet [db kayttaja params]
  (let [hakuehdot (muodosta-hakuehdot params)]
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-indeksit kayttaja)
    (log/debug "Haetaan urakoiden nimiä parametreilla: " params)
    (log/debug "hakuehdot: " hakuehdot)
    (into []
      (q/hae-urakoiden-nimet db hakuehdot))))

(defn- tallenna-urakka-nimi [db urakka]
  (let [urakkaid (:id urakka)
        lyhyt-nimi (if (str/blank? (:lyhyt_nimi urakka)) nil (:lyhyt_nimi urakka))]
    (when-not urakkaid (throw (Exception. "Urakka id puuttuu, ei voi tallentaa lyhytnimeä!")))
    (log/debug "Tallennetaan urakan lyhytnimi: " urakkaid ", nimi " (:nimi urakka) ", lyhytnimi " lyhyt-nimi)
    (q/tallenna-urakan-lyhytnimi! db {:urakka urakkaid
                                      :lyhytnimi lyhyt-nimi})))
(defn tallenna-urakoiden-lyhytnimet [db kayttaja tiedot]
  (let [urakat (:urakat (:tiedot tiedot))
        haku-parametrit (:haku-parametrit tiedot)]
    ; vaaditaan samoja oikeuksia kuin indeksien hallinnassa, ei tarpeen tehdä omaa roolia lyhytnimien hallintaan
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-indeksit kayttaja)
    (doseq [urakka urakat] (tallenna-urakka-nimi db urakka))
    (hae-urakoiden-nimet db kayttaja haku-parametrit)))

(defn toggle-valikatselmus-validoinnit [db kayttaja tiedot]
  (let [validointi (muunnos/keyword->bool (:validointi tiedot))]
    ; vaaditaan samoja oikeuksia kuin indeksien hallinnassa, ei tarpeen tehdä omaa roolia
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-indeksit kayttaja)
    (jarjestelma-kyselyt/toggle-valikatselmus-validoinnit! db {:validoinnit validointi :kayttajaid (:id kayttaja)})
    (keyword (str validointi))))

(defn toggle-arvonvahennys-validoinnit [db kayttaja tiedot]
  (let [validointi (muunnos/keyword->bool (:validointi tiedot))]
    ; vaaditaan samoja oikeuksia kuin indeksien hallinnassa, ei tarpeen tehdä omaa roolia
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-indeksit kayttaja)
    (jarjestelma-kyselyt/toggle-arvonvahennys-validoinnit! db {:validoinnit validointi :kayttajaid (:id kayttaja)})
    (keyword (str validointi))))

(defn hae-jarjestelma-asetukset [db kayttaja]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-indeksit kayttaja)
  (first (jarjestelma-kyselyt/hae-jarjestelman-asetukset db)))

  (defrecord UrakkaLyhytnimienHallinta []
    component/Lifecycle
    (start [{:keys [http-palvelin db] :as this}]
      (julkaise-palvelu http-palvelin :hae-urakoiden-nimet
        (fn [kayttaja tiedot]
          (hae-urakoiden-nimet db kayttaja tiedot)))
      (julkaise-palvelu http-palvelin :tallenna-urakoiden-lyhytnimet
        (fn [kayttaja tiedot]
          (tallenna-urakoiden-lyhytnimet db kayttaja tiedot)))
      (julkaise-palvelu http-palvelin :toggle-valikatselmus-validoinnit
        (fn [kayttaja tiedot]
          (toggle-valikatselmus-validoinnit db kayttaja tiedot)))
      (julkaise-palvelu http-palvelin :toggle-arvonvahennys-validoinnit
        (fn [kayttaja tiedot]
          (toggle-arvonvahennys-validoinnit db kayttaja tiedot)))
      (julkaise-palvelu http-palvelin :hae-jarjestelma-asetukset
        (fn [kayttaja]
          (hae-jarjestelma-asetukset db kayttaja)))
      this)
    (stop [{:keys [http-palvelin] :as this}]
      (poista-palvelut http-palvelin
        :hae-urakoiden-nimet
        :tallenna-urakoiden-lyhytnimet
        :toggle-valikatselmus-validoinnit
        :toggle-arvonvahennys-validoinnit
        :hae-jarjestelma-asetukset)
      this))
