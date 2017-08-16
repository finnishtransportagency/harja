(ns harja.palvelin.palvelut.vesivaylat.tyot
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.domain.oikeudet :as oikeudet]

            [harja.domain.urakka :as ur]
            [harja.domain.roolit :as roolit]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.vesivaylat.kiintiot :as q]
            [harja.kyselyt.vesivaylat.toimenpiteet :as q-toimenpiteet]))

;; Tällä hetkellä töillä ei ole omia palveluita lainkaan, vaan näitä käsitellään
;; hinnoittelujen haun / tallennuksen yhteydessä.