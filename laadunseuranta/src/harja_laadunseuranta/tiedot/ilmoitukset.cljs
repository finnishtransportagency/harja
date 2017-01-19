(ns harja-laadunseuranta.tiedot.ilmoitukset
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [timeout <!]]
            [harja-laadunseuranta.utils :as utils]
            [cljs-time.local :as l]
            [cljs-time.core :as t]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- lisaa-havainto-ehdolle-ilmoitukseen
  [ilmoitukseen-liittyva-havainto-id-atom havainto-id]
  (reset! ilmoitukseen-liittyva-havainto-id-atom havainto-id))

(defn ilmoita
  "Asettaa ilmoituksen näkyville käyttöliittymään. Parametrit on:

   teksti                               Ilmoituksessa näkyvä teksti
   ilmoitus-atom                        Atomi (löytyy sovelluksen tilasta)

   Vapaaehtoiset optiot:
   - taydennettavan-havainnon-id        IndexedDB:n kirjatun reittimerkinnan id, johon ilmoitus liittyy.
   - Tyyppi                             Jokin näistä: :onnistui, :virhe"
  ([teksti ilmoitus-atom] (ilmoita teksti ilmoitus-atom {}))
  ([teksti ilmoitus-atom {:keys [tyyppi taydennettavan-havainnon-id] :as optiot}]
   (if taydennettavan-havainnon-id
     (lisaa-havainto-ehdolle-ilmoitukseen s/ilmoitukseen-liittyva-havainto-id taydennettavan-havainnon-id)
     (reset! s/ilmoitukseen-liittyva-havainto-id nil))
   (reset! ilmoitus-atom {:id (l/local-now)
                          :ilmoitus teksti
                          :tyyppi tyyppi})))

(defn tyhjenna-ilmoitus-nakymisajan-jalkeen [tyhjennettava-ilmoitus
                                             nykyinen-ilmoitus-atom
                                             ilmoitukseen-liittyva-havainto-id-atom]
  (go (<! (timeout asetukset/+ilmoituksen-nakymisaika-ms+))
      ;; Sama ilmoitus on edelleen näkyvillä, tyhjennä se.
      (when (= tyhjennettava-ilmoitus @nykyinen-ilmoitus-atom)
        (reset! ilmoitukseen-liittyva-havainto-id-atom nil)
        (reset! nykyinen-ilmoitus-atom nil))))

(defn ilmoitusta-painettu! []
  (reset! s/havaintolomakkeeseen-liittyva-havainto @s/ilmoitukseen-liittyva-havainto-id)
  (reset! s/liittyy-varmasti-tiettyyn-havaintoon? true)
  (reset! s/ilmoitukseen-liittyva-havainto-id nil)
  (reset! s/ilmoitus nil)
  (reset! s/havaintolomake-auki? true))