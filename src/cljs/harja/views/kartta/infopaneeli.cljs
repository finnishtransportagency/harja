(ns harja.views.kartta.infopaneeli
  "Muodostaa kartalle overlayn, joka sisältää klikatussa koordinaatissa
  olevien asioiden tiedot."
  (:require [harja.ui.komponentti :as komp]
            [cljs.core.async :as async]
            [harja.loki :refer [log tarkkaile!]])
  (:require-macros
   [cljs.core.async.macros :as async-macros]))

  ;; 1. lue kaikista kanavista kunnes menevät kiinni
  ;; 2. per tulos: jos kanavista luetaan monta asiaa, näytetään asioista pelkät otsikkotiedot. jos luetaan yksi, niin näytetään tarkat tiedot.
  ;; 3. otsikon valitseminen: otsikon klikkaaminen vaihtaa alaosan sisältöpaneen tarkemman sisällön näkyviin


(def komponentti )

;; reagent-komponentille luetut datat, jossa toteutetaan yksi/monta logiikka
