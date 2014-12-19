(ns harja.skeema
  "Yleisien Harjan tietojen muotojen määrittely"
  (:require [schema.core :as s]))


(def ^{:doc "Liikennemuoto, joihin hallintayksiköt jaetaan"}
  Liikennemuoto (s/enum :tie :vesi :rata))


