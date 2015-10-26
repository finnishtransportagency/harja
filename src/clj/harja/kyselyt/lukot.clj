(ns harja.kyselyt.lukot
  (:require [yesql.core :refer [defqueries]])
  (:import (java.util UUID)))

(defqueries "harja/kyselyt/lukot.sql")

(defn aseta-lukko? [db tunniste]
  (:aseta_lukko (first (harja.kyselyt.lukot/aseta-lukko db tunniste (str (UUID/randomUUID)) nil))))

(defn avaa-lukko? [db tunniste]
  (:avaa_lukko (first (harja.kyselyt.lukot/avaa-lukko db tunniste))))