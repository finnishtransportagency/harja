(ns harja.palvelin.jarjestelma-rajapinta
  (:require [taoensso.timbre :as log]))

(defonce rajapinta (atom {}))

(defn kutsu
  [palvelu & args]
  {:pre [(keyword? palvelu)]}
  (if (contains? @rajapinta palvelu)
    (apply (get @rajapinta palvelu) args)
    (log/warn (str "[JÄRJESTELMÄRAJAPINTA] Kutsuttiin järjestelmäpalvelua " palvelu ", mutta sitä ei ole määritetty"))))
