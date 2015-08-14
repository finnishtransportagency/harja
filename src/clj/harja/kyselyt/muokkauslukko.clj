(ns harja.kyselyt.muokkauslukko
  (:require [yesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/muokkauslukko.sql")

(defn onko-olemassa-idlla? [db id]
  (:exists (first (onko-olemassa-idlla db id))))