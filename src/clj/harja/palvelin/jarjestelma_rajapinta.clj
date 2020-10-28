(ns harja.palvelin.jarjestelma-rajapinta
  (:require [taoensso.timbre :as log]))

(defonce rajapinta (atom {}))

(defn kutsu
  [palvelu & args]
  {:pre [(keyword? palvelu)]}
  (if-let [rajapinta-f (get @rajapinta palvelu)]
    (apply rajapinta-f args)
    (log/warn (str "[JÄRJESTELMÄRAJAPINTA] Kutsuttiin järjestelmäpalvelua " palvelu ", mutta sitä ei ole määritetty"))))
