(ns harja.palvelin.palvelut.yha-apurit
  "Kevyt YHA-apuri jolla ei riippuvuuksia"
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.yha :as yha-q]))

(defn lukitse-urakan-yha-sidonta [db urakka-id]
  (log/info "Lukitaan urakan " urakka-id " yha-sidonta.")
  (yha-q/lukitse-urakan-yha-sidonta<! db {:urakka urakka-id}))
