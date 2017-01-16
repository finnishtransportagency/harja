(ns harja-laadunseuranta.tiedot.ilmoitukset
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [timeout <!]]
            [harja-laadunseuranta.utils :as utils]
            [cljs-time.local :as l]
            [cljs-time.core :as t]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def +ilmoituksen-nakymisaika-ms+ 5000)

(defn lisaa-ilmoitus-jonoon [teksti ilmoitukset-atom]
  (swap! ilmoitukset-atom #(conj % {:ilmoitus teksti})))

(defn ilmoita
  ([teksti ilmoitus-atom] (ilmoita teksti ilmoitus-atom {}))
  ([teksti ilmoitus-atom {:keys [tyyppi] :as optiot}]
  (reset! ilmoitus-atom {:id (l/local-now)
                         :ilmoitus teksti
                         :tyyppi tyyppi})))

(defn tyhjenna-ilmoitus-nakymisajan-jalkeen [ilmoitus ilmoitus-atom]
  (go (<! (timeout +ilmoituksen-nakymisaika-ms+))
      ;; Tyhjennä nykyinen näytettävä ilmoitus jos se on edelleen sama kuin se,
      ;; jonka tämän go-blockin oli tyhjennettävä
      (when (= ilmoitus @ilmoitus-atom)
        (reset! ilmoitus-atom nil))))

(defn ilmoitusta-painettu! []
  (reset! s/havaintolomakkeeseen-liittyva-havainto @s/ilmoitukseen-liittyva-havainto-id)
  (reset! s/liittyy-varmasti-tiettyyn-havaintoon? true)
  (reset! s/ilmoitukseen-liittyva-havainto-id nil)
  (reset! s/ilmoitus nil)
  (reset! s/havaintolomake-auki? true))