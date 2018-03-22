(ns harja.ui.napit
  (:require [harja.ui.ikonit :as ikonit]
            [harja.ui.viesti :as viesti]
            [harja.ui.modal :as modal]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as y]
            [goog.events.EventType :as EventType]
            [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn palvelinkutsu-nappi ;todo lisää onnistumisviesti
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
           :class (if (or @kysely-kaynnissa? (:disabled asetukset))
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
           :title (:title asetukset)}

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
  "Yleinen nappikomponentti, jota voi muokata optioilla.
   Yleensä kannattaa tämän sijaan käyttää tarkemmin määriteltyjä nappeja.

   Optiot:
   ikoninappi?                Jos true, nappi piirretään pienenä ja siihen mahtuu yksi ikoni
   disabled                   boolean. Jos true, nappi on disabloitu.
   luokka                     Luokka napille (string, erota välilyönnillä jos useita).
   ikoni                      Nappiin piirrettävä ikonikomponentti.
   sticky?                    Jos true, nappi naulataan selaimen yläreunaan scrollatessa alas.
   title                      Nappiin liitettävä title-teksti (tooltip)
   style                      Nappiin liitettävä style
   tallennus-kaynnissa?       Jos true, piirretään ajax-loader."
  ([teksti toiminto] (nappi teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka ikoni tallennus-kaynnissa?
                            sticky? ikoninappi? title style] :as optiot}]
   (let [naulattu? (atom false)
         disabled? (atom disabled)
         napin-etaisyys-ylareunaan (atom nil)
         maarita-sticky! (fn []
                           (if (and
                                 sticky?
                                 (not @disabled?)
                                 (> (dom/scroll-sijainti-ylareunaan) (+ @napin-etaisyys-ylareunaan 20)))
                             (reset! naulattu? true)
                             (reset! naulattu? false)))
         kasittele-scroll-event (fn [this _]
                                  (maarita-sticky!))
         kasittele-resize-event (fn [this _]
                                  (maarita-sticky!))]
     (komp/luo
       (komp/dom-kuuntelija js/window
                            EventType/SCROLL kasittele-scroll-event
                            EventType/RESIZE kasittele-resize-event)
       (komp/kun-muuttuu (fn [_ _ {:keys [disabled] :as optiot}]
                           (reset! disabled? disabled)
                           (maarita-sticky!)))
       (komp/piirretty #(reset! napin-etaisyys-ylareunaan
                                (dom/elementin-etaisyys-dokumentin-ylareunaan
                                  (r/dom-node %))))
       (fn [teksti toiminto {:keys [disabled luokka ikoni tallennus-kaynnissa?] :as optiot}]
         [:button
          {:class (str (when disabled "disabled ")
                       (when @naulattu? "nappi-naulattu ")
                       (when ikoninappi? "nappi-ikoni ")
                       luokka)
           :disabled disabled
           :style style
           :title title
           :on-click #(do
                        (.preventDefault %)
                        (.stopPropagation %)
                        (toiminto))}
          (when tallennus-kaynnissa?
            [y/ajax-loader])
          (when tallennus-kaynnissa?
            " ")

          (if (and ikoni
                   (not tallennus-kaynnissa?))
            [ikonit/ikoni-ja-teksti ikoni teksti]
            teksti)])))))

(defn takaisin
  ([toiminto] (takaisin "Takaisin" toiminto {}))
  ([teksti toiminto] (takaisin teksti toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-toissijainen" " " luokka)
                             :ikoni (ikonit/livicon-chevron-left)})]))

(defn uusi
  ([toiminto] (uusi "Uusi" toiminto {}))
  ([teksti toiminto] (uusi teksti toiminto {}))
  ([teksti toiminto {:keys [luokka disabled] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-ensisijainen" " " luokka)
                             :ikoni (ikonit/livicon-plus)
                             :disabled disabled})]))

(defn hyvaksy
  ([toiminto] (hyvaksy "OK" toiminto {}))
  ([teksti toiminto] (hyvaksy teksti toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-myonteinen" " " luokka)
                             :ikoni (ikonit/check)})]))

(defn peruuta
  ([toiminto] (peruuta "Peruuta" toiminto {}))
  ([teksti toiminto] (peruuta teksti toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-kielteinen" " " luokka)
                             :ikoni (ikonit/livicon-ban)})]))

(defn yleinen
  ([teksti tyyppi toiminto] (yleinen teksti tyyppi toiminto {}))
  ([teksti tyyppi toiminto {:keys [disabled luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (case tyyppi
                                       :ensisijainen (str "nappi-ensisijainen" " " luokka)
                                       :toissijainen (str "nappi-toissijainen" " " luokka))
                             :disabled disabled})]))

(defn yleinen-ensisijainen
  ([teksti toiminto] (yleinen-ensisijainen teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-ensisijainen" " " luokka)
                             :disabled disabled})]))

(defn yleinen-toissijainen
  ([teksti toiminto] (yleinen-toissijainen teksti toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-toissijainen" " " luokka)})]))

(defn kielteinen
  ([teksti toiminto] (kielteinen teksti toiminto {}))
  ([teksti toiminto {:keys [disabled luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-kielteinen" " " luokka)
                             :disabled disabled})]))

(defn sulje
  ([toiminto] (yleinen-toissijainen "Sulje" toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-toissijainen" " " luokka)})]))

(defn tallenna
  ([teksti toiminto] (tallenna teksti toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-ensisijainen" " " luokka)})]))

(defn kumoa
  ([teksti toiminto] (kumoa teksti toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:ikoni (ikonit/kumoa)
                             :luokka (str "nappi-toissijainen" " " luokka)})]))

(defn sulje-ruksi
  [sulje!]
  [:button.close {:on-click sulje!
                  :type "button"}
   [ikonit/remove]])

(defn poista
  ([teksti toiminto] (poista teksti toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-kielteinen" " " luokka)
                             :ikoni (ikonit/livicon-trash)})]))

(defn tarkasta
  ([teksti toiminto] (tarkasta teksti toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-toissijainen" " " luokka)
                             :ikoni (ikonit/eye-open)})]))

(defn muokkaa
  ([teksti toiminto] (muokkaa teksti toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-toissijainen" " " luokka)
                             :ikoni (ikonit/livicon-pen)})]))

(defn avaa
  ([teksti toiminto] (avaa teksti toiminto {}))
  ([teksti toiminto {:keys [luokka] :as optiot}]
   [nappi teksti toiminto (merge
                            optiot
                            {:luokka (str "nappi-toissijainen" " " luokka)
                             :ikoni (ikonit/eye-open)})]))
