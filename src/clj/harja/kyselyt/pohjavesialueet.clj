(ns harja.kyselyt.pohjavesialueet
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/pohjavesialueet.sql")

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id]
  (true? (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id)))))