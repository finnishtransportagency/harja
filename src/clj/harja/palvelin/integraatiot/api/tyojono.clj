(ns harja.palvelin.integraatiot.api.tyojono
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [harja.palvelin.integraatiot.api.paikkaukset :as paikkaukset]
            [harja.kyselyt.api-tyojono :as tyojono-q]))

(defn- kasittele-tapahtuma [this tapahtuma]
  ;; todo: mieti missä threadissa varsinainen käsittely pitäisi ajaa
  ;; todo: tarkista miten hoidetaan, että vain 1 node ottaa tapahtuman käsittelyyn kerrallaan
  (let [{:keys [tapahtuman-nimi id]} (walk/keywordize-keys (cheshire/decode tapahtuma))]
    (log/debug (format "Vastaanotettiin uusi tapahtuma työjonosta (nimi: %s, id: %s)" tapahtuman-nimi id))
    (case tapahtuman-nimi
      "uusi-paikkaustoteuma" (paikkaukset/kirjaa-paikkaustoteuma (:api-paikkaukset this) id)
      (log/warn (format "Ei käsittelijää tapahtumalle nimi: %s, id: %s)" tapahtuman-nimi id)))

    ;; todo: äärimmäisen naivi toteutus, jossa tapahtuma poistetaan jonosta välittömästi sen käsittelyn jälkeen, riippumatta onnistuiko se vai ei
    (tyojono-q/poista-tyojonosta! (:db this) id)))

(defrecord APITyojono []
  component/Lifecycle
  (start [{klusterin-tapahtumat :klusterin-tapahtumat :as this}]
    (tapahtumat/kuuntele! klusterin-tapahtumat "api_tyojono" #(kasittele-tapahtuma this %))
    this)
  (stop [{klusterin-tapahtumat :klusterin-tapahtumat :as this}]
    (tapahtumat/kuuroudu! klusterin-tapahtumat "api_tyojono")
    this))