(ns harja.kyselyt.toteumat
  (:require [yesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/toteumat.sql")

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (log/debug "Tarkistetaan onko olemassa toteuma ulkoisella id:llä " ulkoinen-id " ja luojalla " luoja)
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))