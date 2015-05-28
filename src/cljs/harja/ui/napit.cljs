(ns harja.ui.napit
  (:require [harja.ui.ikonit :as ikonit]
            [harja.ui.viesti :as viesti]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as y]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn palvelinkutsu-nappi ;todo lisää onnistumisviesti
  [teksti kysely asetukset kun-valmis]
  "Nappi, jonka painaminen laukaisee palvelukutsun. Kyselyn pitää olla funktio, joka palauttaa kanavan.
  Kun-valmis on callback funktio, joka saa parametrikseen kyselyn tuloksen.
  Asetukset ovat valinnaisia. Mahdolliset arvot ja niiden oletusarvot ovat:
  - luokka (nappi-toissijainen)
  - virheviesti (Virhe tapahtui)
  - virheen-esitystapa (:vertical), joko :modal, :flash, :vertical tai :horizontal
    * Nappi käyttää harja.ui.yleiset/virheviesti-sailiota, modalia ja viestia
    * horizontal asettaa sailion tyylin inline-blockiksi (8.5.2015)
  - suljettava-virhe? (false)
    * Jos virhe on suljettava, annetaan inline viestille oikeaan yläkulmaan rasti.
    * Oletuksena viestit suljetaan aina, kun tätä nappia painetaan uudelleen"

  (let [kysely-kaynnissa? (atom false)
        nayta-virheviesti? (atom false)
        luokka (if(nil? (:luokka asetukset)) :nappi-toissijainen (:luokka asetukset))
        ikoni (case luokka
                :nappi-toissijainen (ikonit/plus)
                :nappi-ensisijainen (ikonit/search)
                :nappi-kielteinen (ikonit/remove)
                (ikonit/check))
        virheviesti (if (nil? (:virheviesti asetukset)) "Virhe tapahtui." (:virheviesti asetukset))
        virheen-esitystapa (case (:virheen-esitystapa asetukset)
                             :modal :modal
                             :flash :flash
                             :vertical :vertical
                             :horizontal :horizontal
                             :vertical)
        suljettava-virhe? (if (nil? (:suljettava-virhe? asetukset)) false true)
        sulkemisfunktio #(reset! nayta-virheviesti? false)
        ]

    (fn [teksti kysely asetukset]
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
                               (when kun-valmis (kun-valmis tulos)))
                             (do
                               (reset! kysely-kaynnissa? false)
                               (log "VIRHE PALVELINKUTSUSSA!" (pr-str tulos)) ;fixme logitustaso?
                               (reset! nayta-virheviesti? true)
                               #_(when kun-valmis (kun-valmis tulos)))))))}

        (if @kysely-kaynnissa? [y/ajax-loader] ikoni) (str " " teksti)]
       (when @nayta-virheviesti?
         (case virheen-esitystapa
           :flash (viesti/nayta! virheviesti :warning 20000) ;20 sekunttia
           :modal (modal/nayta! {:otsikko virheviesti} "Ota yhteys järjestelmänvalvojaan") ;fixme :D
           :horizontal (y/virheviesti-sailio virheviesti (when suljettava-virhe? sulkemisfunktio) :inline-block)
           :vertical (y/virheviesti-sailio virheviesti (when suljettava-virhe? sulkemisfunktio))
           ))
       ])))