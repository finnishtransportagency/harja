(ns harja.palvelin.palvelut.reikapaikkaukset
  "Reikäpaikkausnäkymän palvelut"
  ;; TODO.. lisätty valmiiksi requireja, poista myöhemmin turhat 
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.reikapaikkaukset :as q]))


(defn hae-reikapaikkaukset [db _user tiedot]
  ;; TODO lisää oikeustarkastus! 
  (oikeudet/ei-oikeustarkistusta!)
  (let [_ (log/debug "hae-reikapaikkaukset :: tiedot" (pr-str tiedot))
        vastaus (q/hae-reikapaikkaukset db {:urakka-id (:urakka-id tiedot)})
        _ (println "\n saatiin vastaus ; " vastaus)]
    vastaus))


(defrecord Reikapaikkaukset []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]

    (julkaise-palvelu http-palvelin
      :hae-reikapaikkaukset
      (fn [user tiedot]
        (hae-reikapaikkaukset db user tiedot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-reikapaikkaukset)
    this))
