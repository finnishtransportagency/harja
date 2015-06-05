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

(defn palvelinkutsu-nappi ;todo lisää onnistumisviesti
  [teksti kysely asetukset callback]
  "Nappi, jonka painaminen laukaisee palvelukutsun. Kyselyn pitää olla funktio, joka palauttaa kanavan.


  Napille voi antaa callback funktion, jolloin callback on kun-onnistuu,
  tai useamman callbackin asetuksissa:
  - kun-valmis: kutsutaan AINA jos annettu. Kutsutaan ENSIMMÄISENÄ!
  - kun-virhe: kutsutaan, kun palvelinkutsu epäonnistuu
  - kun-onnistuu: kutsutaan, kun palvelinkutsu onnistuu.

  Asetukset ovat valinnaisia. Mahdolliset arvot ja niiden oletusarvot ovat:
  - luokka (nappi-toissijainen)
  - virheviesti (Virhe tapahtui)
  - ikoni. Oletuksena haetaan luokan perusteella, mutta on mahdollista antaa myös itse.
  - virheen-esitystapa (:vertical), joko :modal, :flash, :vertical tai :horizontal
    * Nappi käyttää harja.ui.yleiset/virheviesti-sailiota, modalia ja viestia
    * horizontal asettaa sailion tyylin inline-blockiksi (8.5.2015)
  - suljettava-virhe? (false)
    * Jos virhe on suljettava, annetaan inline viestille oikeaan yläkulmaan rasti.
    * Oletuksena viestit suljetaan aina, kun tätä nappia painetaan uudelleen"

  (let [kysely-kaynnissa? (atom false)
        nayta-virheviesti? (atom false)
        luokka (if(nil? (:luokka asetukset)) :nappi-toissijainen (:luokka asetukset))
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

    (fn [teksti kysely asetukset cbs]
      (log "Näytä virheviest? " @nayta-virheviesti?)
      [:span
       [:button
        {:class    (if @kysely-kaynnissa?
                     (str (name luokka) " disabled")
                     luokka)
         :on-click #(do
                     (.preventDefault %)
                     (reset! kysely-kaynnissa? true)
                     (reset! nayta-virheviesti? false)
                     (go (let [tulos (<! (kysely))]
                           (if (not (k/virhe? tulos))
                             (do
                               (reset! kysely-kaynnissa? false)
                               (log "Palvelin vastasi:" (pr-str tulos))
                               (when kun-valmis (kun-valmis tulos))
                               (when kun-onnistuu (kun-onnistuu tulos)))
                             (do
                               (reset! kysely-kaynnissa? false)
                               (log "VIRHE PALVELINKUTSUSSA!" (pr-str tulos)) ;fixme logitustaso?
                               (reset! nayta-virheviesti? true)
                               (when kun-valmis (kun-valmis tulos))
                               (when kun-virhe (kun-virhe tulos)))))))}

        (if @kysely-kaynnissa? [y/ajax-loader] ikoni) (when ikoni (str " ")) teksti]
       (when @nayta-virheviesti?
         (do
           (log "Näytetään virheviesti")
         (case virheen-esitystapa
           :flash (viesti/nayta! virheviesti :warning 20000) ;20 sekunttia
           :modal (modal/nayta! {:otsikko virheviesti} "Ota yhteys järjestelmänvalvojaan") ;fixme :D
           :horizontal (y/virheviesti-sailio virheviesti (when suljettava-virhe? sulkemisfunktio) :inline-block)
           :vertical (y/virheviesti-sailio virheviesti (when suljettava-virhe? sulkemisfunktio))
           )))
       ])))