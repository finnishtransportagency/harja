(ns harja.ui.napit
  (:require [harja.ui.ikonit :as ikonit]
            [harja.ui.viesti :as viesti]
            [harja.ui.modal :as modal]
            [harja.ui.ikonit :as ikonit]
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
  - title. Asetetaan buttonin titleksi. Älä ylikäytä.
  https://www.paciellogroup.com/blog/2012/01/html5-accessibility-chops-title-attribute-use-and-abuse/

  - ikoni. Oletuksena haetaan luokan perusteella, mutta on mahdollista antaa myös itse.
  - virheen-esitystapa (:vertical), joko :modal, :flash, :vertical tai :horizontal
    * Nappi käyttää harja.ui.yleiset/virheviesti-sailiota, modalia ja viestia
    * horizontal asettaa sailion tyylin inline-blockiksi
  - suljettava-virhe? (false)
    * Jos virhe on suljettava, annetaan inline viestille oikeaan yläkulmaan rasti.
    * Oletuksena viestit suljetaan aina, kun tätä nappia painetaan uudelleen
  - disabled (false)
    * jos true, nappi on disabloitu aina
  - id. button elementin DOM id

  Napille voi callbackeja asetuksissa:
  - kun-valmis: kutsutaan AINA jos annettu. Kutsutaan ENSIMMÄISENÄ!
  - kun-virhe: kutsutaan, kun palvelinkutsu epäonnistuu
  - kun-onnistuu: kutsutaan, kun palvelinkutsu onnistuu."

  (let [kysely-kaynnissa? (atom false)
        nayta-virheviesti? (atom false)]
    (fn [teksti kysely asetukset]
      (let [luokka (if (nil? (:luokka asetukset)) "nappi-ensisijainen" (name (:luokka asetukset)))
            ikoni (:ikoni asetukset)
            virheviestin-nayttoaika (:virheviestin-nayttoaika asetukset)
            virheviesti (or (:virheviesti asetukset) "Virhe tapahtui.")
            virheen-esitystapa (case (:virheen-esitystapa asetukset)
                                 :modal :modal
                                 :flash :flash
                                 :vertical :vertical
                                 :horizontal :horizontal
                                 :flash)
            suljettava-virhe? (or (:suljettava-virhe? asetukset) false)
            sulkemisfunktio #(reset! nayta-virheviesti? false)
            kun-valmis (:kun-valmis asetukset)
            kun-virhe (:kun-virhe asetukset)
            kun-onnistuu (:kun-onnistuu asetukset)]

        [:span
         [:button
          {:id (:id asetukset)
           :disabled (or @kysely-kaynnissa? (:disabled asetukset))
           :class    (if (or @kysely-kaynnissa? (:disabled asetukset))
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
                                 (when kun-virhe (kun-virhe tulos)))))))
           :title    (:title asetukset)}

          (if (and @kysely-kaynnissa? ikoni) [y/ajax-loader] ikoni) (when ikoni (str " ")) teksti]
         (when @nayta-virheviesti?
           (case virheen-esitystapa
             :flash (do
                      (viesti/nayta! virheviesti :warning (or virheviestin-nayttoaika
                                                              viesti/viestin-nayttoaika-keskipitka))
                      (sulkemisfunktio)
                      nil)
             :modal (do (modal/nayta! {:otsikko "Virhe tapahtui" :sulje sulkemisfunktio} virheviesti) nil)
             :horizontal (y/virheviesti-sailio virheviesti (when suljettava-virhe? sulkemisfunktio) :inline-block)
             :vertical (y/virheviesti-sailio virheviesti (when suljettava-virhe? sulkemisfunktio))))]))))

(defn takaisin
  [teksti takaisin-fn]
  [:button.nappi-toissijainen {:on-click #(do
                                           (.preventDefault %)
                                           (takaisin-fn))}
   [ikonit/ikoni-ja-teksti (ikonit/livicon-chevron-left) teksti]])

(defn urakan-sivulle [teksti click-fn]
  [:button.nappi-toissijainen {:on-click #(do
                                           (.preventDefault %)
                                           (click-fn))}
   [ikonit/ikoni-ja-teksti (ikonit/livicon-chevron-left) teksti]])

(defn uusi
  "Nappi 'uuden asian' luonnille.
Asetukset on optionaalinen mäppi ja voi sisältää:
  :disabled  jos true, nappi on disabloitu"

  ([teksti uusi-fn] (uusi teksti uusi-fn {}))
  ([teksti uusi-fn {:keys [disabled luokka]}]
   [:button.nappi-ensisijainen
    {:class    (str (when disabled "disabled ") (or luokka ""))
     :disabled disabled
     :on-click #(do
                 (.preventDefault %)
                 (uusi-fn))}
    [ikonit/ikoni-ja-teksti [ikonit/livicon-plus] teksti]]))

(defn hyvaksy
  ([hyvaksy-fn] (hyvaksy "OK" hyvaksy-fn {}))
  ([teksti hyvaksy-fn] (hyvaksy teksti hyvaksy-fn))
  ([teksti hyvaksy-fn {:keys [disabled luokka]}]
    [:button.nappi-myonteinen
     {:class (str (when disabled "disabled") (or luokka ""))
      :disabled disabled
      :on-click #(do
                  (.preventDefault %)
                  (hyvaksy-fn))}
     [ikonit/ikoni-ja-teksti [ikonit/check] teksti]]))

(defn peruuta
  ([teksti peruuta-fn] (peruuta teksti peruuta-fn {}))
  ([teksti peruuta-fn {:keys [disabled luokka]}]
   [:button.nappi-kielteinen
    {:class    (str (when disabled "disabled ") (or luokka ""))
     :disabled disabled
     :on-click #(do
                 (.preventDefault %)
                 (peruuta-fn))}
    [ikonit/ikoni-ja-teksti [ikonit/livicon-ban] teksti]]))

(defn yleinen
  "Yleinen toimintopainike
  Asetukset on optionaalinen mäppi ja voi sisältää:
  :disabled jos true, nappi on disabloitu
  :ikoni näytettävä ikoni"
  ([teksti toiminto-fn] (yleinen teksti toiminto-fn {}))
  ([teksti toiminto-fn {:keys [disabled luokka ikoni]}]
   [:button.nappi-toissijainen
    {:class (str (when disabled "disabled ") (or luokka ""))
     :disabled disabled
     :on-click #(do
                 (.preventDefault %)
                 (toiminto-fn))}
    (if ikoni
      [:span ikoni (str " " teksti)]
      teksti)]))

(defn tallenna
  "Yleinen 'Tallenna' nappi."
  ([sisalto toiminto-fn] (tallenna sisalto toiminto-fn {}))
  ([sisalto toiminto-fn {:keys [disabled luokka ikoni tallennus-kaynnissa?]}]
   [:button.nappi-ensisijainen
    {:class (str (when disabled "disabled " luokka))
     :disabled disabled
     :on-click #(do (.preventDefault %)
                    (toiminto-fn))}
    (if tallennus-kaynnissa?
      [y/ajax-loader]
      ikoni)
    (when (or ikoni tallennus-kaynnissa?) " ")
    sisalto]))

(defn sulje
  "'Sulje' ruksi"
  [sulje!]
  [:button.close {:on-click sulje!
                  :type "button"}
   [ikonit/remove]])

(defn poista
  ([teksti poista-fn] (poista teksti poista-fn {}))
  ([teksti poista-fn {:keys [disabled luokka]}]
   [:button.nappi-kielteinen
    {:class    (str (when disabled "disabled ") (or luokka ""))
     :disabled disabled
     :on-click #(do
                  (.preventDefault %)
                  (poista-fn))}
    [ikonit/ikoni-ja-teksti [ikonit/livicon-trash] teksti]]))

(defn tarkasta
  ([teksti tarkasta-fn] (tarkasta teksti tarkasta-fn {}))
  ([teksti tarkasta-fn {:keys [disabled luokka]}]
   [:button.nappi-toissijainen
    {:class    (str (when disabled "disabled ") (or luokka ""))
     :disabled disabled
     :on-click #(do
                  (.preventDefault %)
                  (tarkasta-fn))}
    [ikonit/ikoni-ja-teksti [ikonit/eye-open] teksti]]))

(defn muokkaa
  ([teksti muokkaa-fn] (muokkaa teksti muokkaa-fn {}))
  ([teksti muokkaa-fn {:keys [disabled luokka]}]
   [:button.nappi-toissijainen
    {:class    (str (when disabled "disabled ") (or luokka ""))
     :disabled disabled
     :on-click #(do
                  (.preventDefault %)
                  (muokkaa-fn))}
    [ikonit/ikoni-ja-teksti [ikonit/livicon-pen] teksti]]))