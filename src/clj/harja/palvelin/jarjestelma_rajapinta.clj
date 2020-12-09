(ns harja.palvelin.jarjestelma-rajapinta
  (:require [taoensso.timbre :as log]
            [slingshot.slingshot :refer [throw+]]))

(defonce rajapinta (atom {}))

(defn kutsu
  [palvelu & args]
  {:pre [(keyword? palvelu)]}
  (if-let [rajapinta-f (get @rajapinta palvelu)]
    (apply rajapinta-f args)
    (do (log/warn (str "[JÄRJESTELMÄRAJAPINTA] Kutsuttiin järjestelmäpalvelua " palvelu ", mutta sitä ei ole määritetty"))
        (throw+ {:type :jarjestelma-rajapinta
                 :virheet [{:koodi :rajapintaa-ei-maaritetty
                            :viesti (str "Rjapintaa ei määritelty palvelulle " palvelu)}]}))))
