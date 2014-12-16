(ns harja.skeema
  "Yleisien Harjan tietojen muotojen määrittely"
  (:require [schema.core :as s]))


(def ^{:doc "Liikennemuoto, joihin hallintayksiköt jaetaan"}
  Liikennemuoto (s/enum :tie :vesi :rata))

(def ^{:doc "Hakuehdot, joilla hallintayksiköitä voi hakea. Sisältää kulkumuodon ja vapaatekstihaun."}
  Hallintayksikko-haku
  {:liikennemuoto (s/maybe Liikennemuoto)
   (s/optional-key :haku) (s/maybe s/Str)})
