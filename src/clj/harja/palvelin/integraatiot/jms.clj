(ns harja.palvelin.integraatiot.jms
  (:require [clojure.core.async :as async]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.sonja :as sonja]))

(defn aloita-sonja [jarjestelma]
  (async/go
    (log/info "Aloitaetaan Sonjayhteys")
    (loop []
      (let [{:keys [vastaus virhe kaskytysvirhe]} (async/<! (sonja/kasky (:sonja jarjestelma) {:aloita-yhteys nil}))]
        (when vastaus
          (log/info "Sonja yhteys aloitettu"))
        (when kaskytysvirhe
          (log/error "Sonjayhteyden aloittamisessa käskytysvirhe: " kaskytysvirhe))
        (async/<! (async/timeout 2000))
        (if (or virhe (= :kasykytyskanava-taynna kaskytysvirhe))
          (recur)
          vastaus)))))