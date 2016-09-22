(ns harja.domain.raportointi
  (:require [harja.domain.roolit :as roolit]))

(def virhetyypit #{:info :varoitus :virhe})

(defn virhe? [solu]
  (and (vector? solu) (virhetyypit (first solu))))

(defn virheen-viesti [solu] (second solu))

(defn voi-nahda-laajemman-kontekstin-raportit? [kayttaja]
  (and (not (roolit/roolissa? roolit/tilaajan-laadunvalvontakonsultti kayttaja))
       (roolit/tilaajan-kayttaja? kayttaja)))

#?(:cljs
   (defn nykyinen-kayttaja-voi-nahda-laajemman-kontekstin-raportit? []
     (voi-nahda-laajemman-kontekstin-raportit? @harja.tiedot.istunto/kayttaja)))