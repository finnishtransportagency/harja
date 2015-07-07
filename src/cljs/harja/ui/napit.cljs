(ns harja.ui.napit
  (:require [harja.ui.ikonit :as ikonit]
            [harja.ui.viesti :as viesti]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as y]
            [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn palvelinkutsu-nappi                                   ;todo lisää onnistumisviesti
  [teksti kysely asetukset]
  "Nappi, jonka painaminen laukaisee palvelukutsun.

  Kyselyn pitää olla funktio, joka palauttaa kanavan.

  Asetukset ovat valinnaisia. Mahdolliset arvot ja niiden oletusarvot ovat:
  - luokka (nappi-ensisijainen)
  - virheviesti (Virhe tapahtui)
  - ikoni. Oletuksena haetaan luokan perusteella, mutta on mahdollista antaa myös itse.
  - virheen-esitystapa (:vertical), joko :modal, :flash, :vertical tai :horizontal
    * Nappi käyttää harja.ui.yleiset/virheviesti-sailiota, modalia ja viestia
    * horizontal asettaa sailion tyylin inline-blockiksi
  - suljettava-virhe? (false)
    * Jos virhe on suljettava, annetaan inline viestille oikeaan yläkulmaan rasti.
    * Oletuksena viestit suljetaan aina, kun tätä nappia painetaan uudelleen
  - disabled (false)
    * jos true, nappi on disabloitu aina

  Napille voi callbackeja asetuksissa:
  - kun-valmis: kutsutaan AINA jos annettu. Kutsutaan ENSIMMÄISENÄ!
  - kun-virhe: kutsutaan, kun palvelinkutsu epäonnistuu
  - kun-onnistuu: kutsutaan, kun palvelinkutsu onnistuu."

  (let [kysely-kaynnissa? (atom false)
        nayta-virheviesti? (atom false)
        luokka (if (nil? (:luokka asetukset)) "nappi-ensisijainen" (name (:luokka asetukset)))
        ikoni (:ikoni asetukset)
        virheviesti (if (nil? (:virheviesti asetukset)) "Virhe tapahtui." (:virheviesti asetukset))
        virheen-esitystapa (case (:virheen-esitystapa asetukset)
                             :modal :modal
                             :flash :flash
                             :vertical :vertical
                             :horizontal :horizontal
                             :vertical)
        suljettava-virhe? (if (nil? (:suljettava-virhe? asetukset)) true false)
        sulkemisfunktio #(reset! nayta-virheviesti? false)
        kun-valmis (:kun-valmis asetukset)
        kun-virhe (:kun-virhe asetukset)
        kun-onnistuu (:kun-onnistuu asetukset)
        ]

    (fn [teksti kysely asetukset]
      [:span
       [:button
        {:class    (if (or @kysely-kaynnissa? (:disabled asetukset))
                     (str luokka " disabled")
                     luokka)
         :on-click #(do
                     (.preventDefault %)
                     (reset! kysely-kaynnissa? true)
                     (reset! nayta-virheviesti? false)
                     (go (let [tulos (<! (kysely))]
                           (when kun-valmis (kun-valmis tulos))
                           (if (not (k/virhe? tulos))
                             (do
                               (reset! kysely-kaynnissa? false)
                               (when kun-onnistuu (kun-onnistuu tulos)))
                             (do
                               (reset! kysely-kaynnissa? false)
                               (log "VIRHE PALVELINKUTSUSSA!" (pr-str tulos))
                               (reset! nayta-virheviesti? true)
                               (when kun-virhe (kun-virhe tulos)))))))}

        (if (and @kysely-kaynnissa? ikoni) [y/ajax-loader] ikoni) (when ikoni (str " ")) teksti]
       (when @nayta-virheviesti?
         (case virheen-esitystapa
           :flash (do (viesti/nayta! virheviesti :warning 20000) nil) ;20 sekunttia
           :modal (do (modal/nayta! {:otsikko "Virhe tapahtui"} virheviesti) nil)
           :horizontal (y/virheviesti-sailio virheviesti (when suljettava-virhe? sulkemisfunktio) :inline-block)
           :vertical (y/virheviesti-sailio virheviesti (when suljettava-virhe? sulkemisfunktio))
           ))
       ])))

(defn takaisin [teksti takaisin-fn]
  [:button.nappi-toissijainen {:on-click #(takaisin-fn)}
   (ikonit/chevron-left) " " teksti])

(defn uusi
  "Nappi 'uuden asian' luonnille. 
Asetukset on optionaalinen mäppi ja voi sisältää:
  :disabled  jos true, nappi on disabloitu"

  ([teksti uusi-fn] (uusi teksti uusi-fn {}))
  ([teksti uusi-fn {:keys [disabled]}]
   [:button.nappi-ensisijainen
    {:class    (when disabled "disabled")
     :disabled disabled
     :on-click #(uusi-fn)}
    (ikonit/plus-sign)
    " " teksti]))


