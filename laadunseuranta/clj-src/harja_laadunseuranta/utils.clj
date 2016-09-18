(ns harja-laadunseuranta.utils
  (:require [harja-laadunseuranta.config :as c]
            [taoensso.timbre :as log]
            [clojure.string :as str]

            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.domain.oikeudet :as oikeudet]))


(defn polku [s]
  (str (:url-prefix @c/config) s))

(defn select-non-nil-keys [c keys]
  (into {} (filterv #(not (nil? (second %))) (into [] (select-keys c keys)))))
