(ns harja.palvelin.integraatiot.tierekisteri.tietueet
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus :as vastaussanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn hae-tietueet [integraatioloki url id tietolaji]
  (log/debug "Haetaen tietu " id ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä."))
