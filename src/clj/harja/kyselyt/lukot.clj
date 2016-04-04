(ns harja.kyselyt.lukot
  (:require [jeesql.core :refer [defqueries]])
  (:import (java.util UUID)))

(defqueries "harja/kyselyt/lukot.sql"
  {:positional? true})

(defn aseta-lukko? [db tunniste aikaraja]
  (:aseta_lukko (first (harja.kyselyt.lukot/aseta-lukko db tunniste (str (UUID/randomUUID)) aikaraja))))

(defn avaa-lukko? [db tunniste]
  (:avaa_lukko (first (harja.kyselyt.lukot/avaa-lukko db tunniste))))
