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

(defn nappi
  ([teksti toiminto] (nappi teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka ikoni]}]
   [:button
    {:class (str (when disabled "disabled") " " luokka)
     :disabled disabled
     :on-click #(do
                  (.preventDefault %)
                  (toiminto))}
    (if ikoni
      [ikonit/ikoni-ja-teksti ikoni teksti]
      teksti)]))

(defn takaisin
  [teksti takaisin-fn]
  (nappi teksti takaisin-fn {:luokka "nappi-toissijainen"
                             :ikoni (ikonit/livicon-chevron-left)}))

(defn uusi
  "Nappi 'uuden asian' luonnille.
   Asetukset on optionaalinen mäppi ja voi sisältää:
   :disabled  jos true, nappi on disabloitu"
  ([toiminto] (uusi "Uusi" toiminto {}))
  ([teksti toiminto] (uusi teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka]}]
   (nappi teksti toiminto {:luokka (str "nappi-ensisijainen" " " luokka)
                           :ikoni (ikonit/livicon-plus)
                           :disabled disabled})))

(defn hyvaksy
  ([toiminto] (hyvaksy "OK" toiminto {}))
  ([teksti toiminto] (hyvaksy teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka]}]
   (nappi teksti toiminto {:luokka (str "nappi-myonteinen" " " luokka)
                           :ikoni (ikonit/check)
                           :disabled disabled})))

(defn peruuta
  ([toiminto] (peruuta "Peruuta" toiminto {}))
  ([teksti toiminto] (peruuta teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka]}]
   (nappi teksti toiminto {:luokka (str "nappi-kielteinen" " " luokka)
                           :disabled disabled
                           :ikoni (ikonit/livicon-ban)})))

(defn yleinen
  "Yleinen toimintopainike
  Asetukset on optionaalinen mäppi ja voi sisältää:
  :disabled jos true, nappi on disabloitu
  :ikoni näytettävä ikoni"
  ([teksti toiminto] (yleinen teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka ikoni]}]
   (nappi teksti toiminto {:luokka (str "nappi-toissijainen" " " luokka)
                           :disabled disabled
                           :ikoni ikoni})))

(defn tallenna
  "Yleinen 'Tallenna' nappi."
  ([sisalto toiminto-fn] (tallenna sisalto toiminto-fn {}))
  ([sisalto toiminto-fn {:keys [disabled luokka ikoni tallennus-kaynnissa?]}]
   [:button.nappi-ensisijainen
    {:class (str (when disabled "disabled ") luokka)
     :disabled disabled
     :on-click #(do (.preventDefault %)
                    (toiminto-fn))}
    (if tallennus-kaynnissa?
      [y/ajax-loader]
      ikoni)
    (when (or ikoni tallennus-kaynnissa?) " ")
    sisalto]))

(defn sulje-ruksi
  [sulje!]
  [:button.close {:on-click sulje!
                  :type "button"}
   [ikonit/remove]])

(defn poista
  ([teksti toiminto] (poista teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka]}]
   (nappi teksti toiminto {:luokka (str "nappi-kielteinen" " " luokka)
                           :disabled disabled
                           :ikoni (ikonit/livicon-trash)})))

(defn tarkasta
  ([teksti toiminto] (tarkasta teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka]}]
   (nappi teksti toiminto {:luokka (str "nappi-toissijainen" " " luokka)
                           :disabled disabled
                           :ikoni (ikonit/eye-open)})))

(defn muokkaa
  ([teksti toiminto] (muokkaa teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka]}]
   (nappi teksti toiminto {:luokka (str "nappi-toissijainen" " " luokka)
                           :disabled disabled
                           :ikoni (ikonit/livicon-pen)})))
