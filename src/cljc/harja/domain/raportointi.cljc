(ns harja.domain.raportointi
  (:require [harja.domain.roolit :as roolit]))

(def virhetyylit
  {:virhe "rgb(221,0,0)"
   :varoitus "rgb(255,153,0)"
   :info "rbg(0,136,204)"})

(defn voi-nahda-laajemman-kontekstin-raportit? [kayttaja]
  (and (not (roolit/roolissa? roolit/tilaajan-laadunvalvontakonsultti kayttaja))
       (roolit/tilaajan-kayttaja? kayttaja)))

#?(:cljs
   (defn nykyinen-kayttaja-voi-nahda-laajemman-kontekstin-raportit? []
     (voi-nahda-laajemman-kontekstin-raportit? @harja.tiedot.istunto/kayttaja)))