(ns harja.palvelin.tyokalut.koordinaatit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.geo :as geo]
            [harja.domain.oikeudet :as oikeudet]
            [taoensso.timbre :as log]))

(defn hae-piste-kartalle
  [wgs84-koordinaatit]
  (try
    (let [{x :x y :y} (geo/wgs84->euref wgs84-koordinaatit)]
      {:type :point :coordinates [x y]})
    (catch Exception e
      (let [virhe (format "Poikkeus hakiessa pistettä WGS84-koordinaateille %s" wgs84-koordinaatit)]
        (log/error e virhe)
        {:virhe virhe}))))

(defrecord Koordinaatit []
  component/Lifecycle
  (start [{:keys [http-palvelin] :as this}]
    (julkaise-palvelu
      http-palvelin
      :hae-piste-kartalle (fn [_ params]
                          (oikeudet/ei-oikeustarkistusta!)
                          (hae-piste-kartalle params)))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelu http :hae-piste-kartalle)
    this))
