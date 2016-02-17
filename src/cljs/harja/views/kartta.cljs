(ns harja.views.kartta
  "Harjan kartta."
  (:require [cljs.core.async :refer [timeout <! >! chan] :as async]
            [clojure.string :as str]
            [clojure.set :as set]
            [goog.events.EventType :as EventType]
            [goog.events :as events]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.fmt :as fmt]
            [harja.geo :as geo]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.animaatio :as animaatio]
            [harja.ui.komponentti :as komp]
            [harja.ui.openlayers :refer [openlayers] :as openlayers]
            [harja.ui.dom :as dom]
            [harja.views.kartta.tasot :as tasot]
            [reagent.core :refer [atom] :as reagent]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kartta.varit.alpha :as varit])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go go-loop]]))


(def kartta-kontentin-vieressa? (atom false))

(def +kartan-napit-padding+ 26)
(def +kartan-korkeus-s+ 0)

(def kartan-korkeus (reaction
                      (let [koko @nav/kartan-koko
                            kork @dom/korkeus]
                        (case koko
                          :S +kartan-korkeus-s+
                          :M (int (* 0.25 kork))
                          :L (int (* 0.60 kork))
                          :XL (int (* 0.80 kork))
                          (int (* 0.60 kork))))))

;; Kanava, jonne kartan uusi sijainti kirjoitetaan
(defonce paivita-kartan-sijainti (chan))

(defn- aseta-kartan-sijainti [x y w h naulattu?]
  (when-let
    [karttasailio (dom/elementti-idlla "kartta-container")]
    (let [tyyli (.-style karttasailio)]
      #_(log "ASETA-KARTAN-SIJAINTI: " x ", " y ", " w ", " h ", " naulattu?)
      (if naulattu?
        (do
          (set! (.-position tyyli) "fixed")
          (set! (.-left tyyli) (fmt/pikseleina x))
          (set! (.-top tyyli) "0px")
          (set! (.-width tyyli) (fmt/pikseleina w))
          (set! (.-height tyyli) (fmt/pikseleina h))
          (openlayers/set-map-size! w h))
        (do
          (set! (.-position tyyli) "absolute")
          (set! (.-left tyyli) (fmt/pikseleina x))
          (set! (.-top tyyli) (fmt/pikseleina y))
          (set! (.-width tyyli) (fmt/pikseleina w))
          (set! (.-height tyyli) (fmt/pikseleina h))
          (openlayers/set-map-size! w h)))
      ;; jotta vältetään muiden kontrollien hautautuminen float:right Näytä kartta alle, kavenna kartta-container
      (when (= :S @nav/kartan-koko)
        (set! (.-left tyyli) "")
        (set! (.-right tyyli) (fmt/pikseleina 20))
        (set! (.-width tyyli) (fmt/pikseleina 100))))))

;; Kun kartan paikkavaraus poistuu, aseta flägi, joka pakottaa seuraavalla
;; kerralla paikan asetuksen... läheta false kanavaan

(defn- elementti-idlla-odota
  "Pollaa DOMia 10ms välein kunnes annettu elementti löytyy. Palauttaa kanavan, josta
  elementin voi lukea."
  [id]
  (go (loop [elt (.getElementById js/document id)]
        (if elt
          elt
          (do #_(log "odotellaan elementtiä " id)
            (<! (timeout 10))
            (recur (.getElementById js/document id)))))))

(defn odota-mount-tai-timeout
  "Odottaa, että paivita-kartan-sijainti kanavaan tulee :mount tapahtuma tai 150ms timeout.
  Paluttaa kanavan, josta voi :mount tai :timeout arvon."
  []
  (let [t (timeout 150)]
    (go (loop []
          (let [[arvo ch] (async/alts! [t paivita-kartan-sijainti])]
            (if (= ch t)
              :timeout
              (if (= arvo :mount)
                :mount
                (recur))))))))

(defonce kartan-sijaintipaivitys
         (let [transition-end-tuettu? (animaatio/transition-end-tuettu?)]
           (go (loop [naulattu? nil
                      x nil
                      y nil
                      w nil
                      h nil
                      offset-y nil]
                 (let [ensimmainen-kerta? (nil? naulattu?)
                       paivita (<! paivita-kartan-sijainti)
                       aseta (when-not paivita
                               ;; Kartan paikkavaraus poistuu, asetetaan lähtötila, jolloin
                               ;; seuraava päivitys aina asettaa kartan paikan.

                               ;; Odotetaan joko seuraavaa eventtiä paivita-kartan-sijainti (jos uusi komponentti
                               ;; tuli näkyviin, tai timeout 20ms (jos kartta oikeasti lähti näkyvistä)
                               (case (<! (odota-mount-tai-timeout))
                                 :mount true
                                 :timeout
                                 ;; timeout, kartta oikeasti poistu, asetellaan -h paikkaan
                                 (do                        ;; (log "KARTTA LÄHTI OIKEASTI")
                                   (aseta-kartan-sijainti x (- @dom/korkeus) w h false)
                                   (recur nil nil nil w h nil))))
                       paikka-elt (<! (elementti-idlla-odota "kartan-paikka"))
                       [uusi-x uusi-y uusi-w uusi-h] (dom/sijainti paikka-elt)
                       uusi-offset-y (dom/offset-korkeus paikka-elt)]

                   ;; (log "KARTAN PAIKKA: " x "," y " (" w "x" h ") OY: " offset-y " => " uusi-x "," uusi-y " (" uusi-w "x" uusi-h ") OY: " uusi-offset-y)


                   (cond
                     ;; Eka kerta, asetetaan kartan sijainti
                     (or (= :aseta paivita) aseta (nil? naulattu?))
                     (let [naulattu? (neg? uusi-y)]
                       ;(log "EKA KERTA")
                       (aseta-kartan-sijainti uusi-x uusi-offset-y uusi-w uusi-h naulattu?)
                       (when (or (not= w uusi-w) (not= h uusi-h))
                         (reagent/next-tick #(openlayers/invalidate-size!)))
                       (recur naulattu?
                              uusi-x uusi-y uusi-w uusi-h uusi-offset-y))

                     ;; Jos kartta ei ollut naulattu yläreunaan ja nyt meni negatiiviseksi
                     ;; koko pitää asettaa
                     (and (not naulattu?) (neg? uusi-y))
                     (do (aseta-kartan-sijainti uusi-x uusi-y uusi-w uusi-h true)
                         (recur true
                                uusi-x uusi-y uusi-w uusi-h uusi-offset-y))

                     ;; Jos oli naulattu ja nyt on positiivinen, pitää naulat irroittaa
                     (and naulattu? (pos? uusi-y))
                     (do (aseta-kartan-sijainti uusi-x uusi-offset-y uusi-w uusi-h false)
                         (recur false
                                uusi-x uusi-y uusi-w uusi-h uusi-offset-y))

                     ;; jos w/h muuttuu
                     (or (not= w uusi-w)
                         (not= h uusi-h))
                     (do (when-not transition-end-tuettu?
                           (go (<! (async/timeout 150))
                               (openlayers/invalidate-size!)))
                         (recur naulattu?
                                uusi-x uusi-y uusi-w uusi-h uusi-offset-y))

                     :default
                     (recur naulattu?
                            uusi-x uusi-y uusi-w uusi-h uusi-offset-y)))))))

;; halutaan että kartan koon muutos aiheuttaa rerenderin kartan paikalle
(defn- kartan-paikkavaraus
  [kartan-koko & args]
  (let [paivita (fn [paikkavaraus]
                  (go (>! paivita-kartan-sijainti paikkavaraus)))
        scroll-kuuntelija (fn [_]
                            (paivita :scroll))]
    (komp/luo
     (komp/kuuntelija #{:ikkunan-koko-muuttunut
                        :murupolku-muuttunut}
                      #(paivita :aseta))
      {:component-did-mount    #(do
                                 (events/listen js/window
                                                EventType/SCROLL
                                                scroll-kuuntelija)
                                 (paivita :mount))
       :component-did-update   #(paivita :aseta)
       :component-will-unmount (fn [this]
                                 ;; jos karttaa ei saa näyttää, asemoidaan se näkyvän osan yläpuolelle
                                 (events/unlisten js/window EventType/SCROLL scroll-kuuntelija)
                                 (paivita false))}

      (fn []
        [:div#kartan-paikka {:style {:height        (fmt/pikseleina @kartan-korkeus)
                                     :margin-bottom "5px"
                                     :width         "100%"}}]))))

(defn kartan-paikka
  [& args]
  (let [koko @nav/kartan-koko]
    (if-not (= :hidden koko)
      [kartan-paikkavaraus koko args]
      [:span.ei-karttaa])))



;; Envelop [minx miny maxx maxy], jossa koko suomi näkyy
(def +koko-suomi-extent+ [60000 6613000 736400 7780300])

(reset! nav/kartan-extent +koko-suomi-extent+)

(defonce urakka-kuuntelija
         (t/kuuntele! :urakka-valittu
                      #(openlayers/hide-popup!)))

(defonce kartan-koon-paivitys
         (run! (do @dom/ikkunan-koko
                   (openlayers/invalidate-size!))))

(defn kartan-koko-kontrollit
  []
  (let [koko @nav/kartan-koko
        kartan-korkeus @kartan-korkeus
        sivu (nav/sivu)
        v-ur @nav/valittu-urakka
        [muuta-kokoa-teksti ikoni] (case koko
                             :M ["Suurenna karttaa" (ikonit/arrow-down)]
                             :L ["Pienennä karttaa" (ikonit/arrow-up)]
                             :XL ["Pienennä karttaa" (ikonit/arrow-up)]
                             ["" nil])]
    ;; TODO: tähän alkaa kertyä näkymäkohtaista logiikkaa, mietittävä vaihtoehtoja.
    [:div.kartan-kontrollit.kartan-koko-kontrollit {:class (when-not @nav/kartan-kontrollit-nakyvissa? "hide")}


     ;; käytetään tässä inline-tyylejä, koska tarvitsemme kartan-korkeus -arvoa asemointiin
     [:div.kartan-koko-napit {:style {:position   "absolute"
                                      :text-align "center"
                                      :top        (fmt/pikseleina (- kartan-korkeus
                                                                     (if (= :S koko)
                                                                       0
                                                                       +kartan-napit-padding+)))
                                      :width      "100%"
                                      :z-index    100}}
      (if (= :S koko)
        [:button.btn-xs.nappi-ensisijainen.nappi-avaa-kartta.pull-right
         {:on-click #(nav/vaihda-kartan-koko! :L)}
         (ikonit/expand) " Näytä kartta"]
        [:span
         (when-not @kartta-kontentin-vieressa?              ;ei pointtia muuttaa korkeutta jos ollaan kontentin vieressä
           [:button.btn-xs.nappi-toissijainen {:on-click #(nav/vaihda-kartan-koko!
                                                           (case koko
                                                             :M :L
                                                             :L :M
                                                             ;; jos tulee tarve, voimme hanskata kokoja kolmella napilla
                                                             ;; suurenna | pienennä | piilota
                                                             :XL :M))}
            ikoni muuta-kokoa-teksti])

         [:button.btn-xs.nappi-ensisijainen {:on-click #(nav/vaihda-kartan-koko! :S)}
          (ikonit/compress) " Piilota kartta"]])]]))

(def keskita-kartta-pisteeseen openlayers/keskita-kartta-pisteeseen!)
(defn keskita-kartta-alueeseen! [alue]
  (reset! nav/kartan-extent alue))

(def ikonien-selitykset-nakyvissa-oletusarvo true)
;; Eri näkymät voivat tarpeen mukaan asettaa ikonien selitykset päälle/pois komponenttiin tultaessa.
;; Komponentista poistuttaessa tulisi arvo asettaa takaisin oletukseksi
(def ikonien-selitykset-nakyvissa? (atom true))
(def ikonien-selitykset-auki (atom false))

(defn kartan-ikonien-selitykset []
  (let [selitteet (reduce set/union
                          (keep (comp :selitteet meta) (vals @tasot/geometriat)))
        varilaatikon-koko 20]
    (if (and (not= :S @nav/kartan-koko)
             (not (empty? selitteet))
             @ikonien-selitykset-nakyvissa?)
      [:div.kartan-selitykset.kartan-ikonien-selitykset
       (if @ikonien-selitykset-auki
         [:div
          [:table
           [:tbody
            (for [{:keys [img nimi vari teksti]} selitteet]
              ^{:key (str (or vari img) "_" nimi)}
              [:tr
               (cond
                 (string? vari)
                 [:td.kartan-ikonien-selitykset-ikoni-sarake
                  [:div.kartan-ikoni-vari {:style {:background-color vari
                                                   :width            (str varilaatikon-koko "px")
                                                   :height           (str varilaatikon-koko "px")}}]]

                 (coll? vari)
                 (let [vk varilaatikon-koko
                       kaikki-koot [[vk]
                                    [vk (- vk 10)]
                                    [vk (- vk 6) (- vk 12)]
                                    [vk (- vk 4) (- vk 8) (- vk 12)]]
                       koot (nth kaikki-koot (dec (count vari)) (take (count vari) (range vk 0 -2)))
                       solut (partition 2 (interleave koot vari))
                       pohja (first solut)
                       sisakkaiset (butlast (rest solut))
                       viimeinen (last solut)]
                   [:td.kartan-ikonien-selitykset-ikoni-sarake
                    [:div.kartan-ikoni-vari-pohja {:style {:background-color (second pohja)
                                                           :width            (first pohja)
                                                           :height           (first pohja)}}]
                    (doall
                      (for [[koko v] sisakkaiset]
                        ^{:key (str koko "_" v "--" nimi)}
                        [:div.kartan-ikoni-vari-sisakkainen {:style {:background-color v
                                                                     :width            koko
                                                                     :height           koko
                                                                     :margin           (/ (- varilaatikon-koko koko) 2)}}]))

                    [:div.kartan-ikoni-vari-sisakkainen {:style {:background-color (second viimeinen)
                                                                 :width            (first viimeinen)
                                                                 :height           (first viimeinen)
                                                                 :position         "relative"
                                                                 :margin           (/ (- varilaatikon-koko (first viimeinen)) 2)}}]])


                 :else [:td.kartan-ikonien-selitykset-ikoni-sarake
                        [:img.kartan-ikonien-selitykset-ikoni {:src img}]])
               [:td.kartan-ikonien-selitykset-selitys-sarake [:span.kartan-ikonin-selitys teksti]]])]]
          [:div.kartan-ikonien-selitykset-sulje.klikattava
           {:on-click (fn [event]
                        (reset! ikonien-selitykset-auki false)
                        (.stopPropagation event)
                        (.preventDefault event))} "Sulje"]]
         [:span.kartan-ikonien-selitykset-avaa.klikattava {:on-click (fn [event]
                                                                       (reset! ikonien-selitykset-auki true)
                                                                       (.stopPropagation event)
                                                                       (.preventDefault event))}
          "Karttaselitteet"])])))

(def kartan-yleiset-kontrollit-sisalto (atom nil))

(defn kartan-yleiset-kontrollit
  "Kartan yleiset kontrollit -komponentti, johon voidaan antaa mitä tahansa sisältöä, jota tietyssä näkymässä tarvitaan"
  []
  (let [sisalto @kartan-yleiset-kontrollit-sisalto
        luokka-str (or (:class (meta sisalto)) "kartan-yleiset-kontrollit")]
    (when (and sisalto (not= :S @nav/kartan-koko))
      [:div {:class (str "kartan-kontrollit " luokka-str)} sisalto])))

(def paivitetaan-karttaa-tila (atom false))

(defn paivitetaan-karttaa
  []
  (when @paivitetaan-karttaa-tila
    [:div {:style {:position "absolute" :top "50%" :left "50%"}}
     [:div {:style {:position "relative" :left "-50px" :top "-30px"}}
      [:div.paivitetaan-karttaa (yleiset/ajax-loader "Päivitetään karttaa")]]]))

(defn aseta-paivitetaan-karttaa-tila! [uusi-tila]
  (reset! paivitetaan-karttaa-tila uusi-tila))

(defn aseta-yleiset-kontrollit! [uusi-sisalto]
  (reset! kartan-yleiset-kontrollit-sisalto uusi-sisalto))

(defn tyhjenna-yleiset-kontrollit! []
  (reset! kartan-yleiset-kontrollit-sisalto nil))

(def kartan-ohjelaatikko-sisalto (atom nil))

(defn kartan-ohjelaatikko
  "Kartan ohjelaatikko -komponentti, johon voidaan antaa mitä tahansa sisältöä, jota tietyssä näkymässä tarvitaan"
  []
  (let [sisalto @kartan-ohjelaatikko-sisalto]
    (when (and sisalto (not= :S @nav/kartan-koko))
      [:div.kartan-kontrollit.kartan-ohjelaatikko sisalto])))

(defn aseta-ohjelaatikon-sisalto! [uusi-sisalto]
  (reset! kartan-ohjelaatikko-sisalto uusi-sisalto))

(defn tyhjenna-ohjelaatikko! []
  (reset! kartan-ohjelaatikko-sisalto nil))

(defn nayta-popup!
  "Näyttää popup sisällön kartalla tietyssä sijainnissa. Sijainti on vektori [lat lng], 
joka kertoo karttakoordinaatit. Sisältö annetaan sisalto-hiccup muodossa ja se renderöidään
HTML merkkijonoksi reagent render-to-string funktiolla (eikä siis ole täysiverinen komponentti)"
  [sijainti sisalto-hiccup]
  (openlayers/show-popup! sijainti sisalto-hiccup))

(defn poista-popup! []
  (openlayers/hide-popup!))

(defn poista-popup-ilman-eventtia!
  "Poistaa pop-upin ilmoittamatta siitä kuuntelijoille. Kätevä esim. silloin kun pop-up poistetaan
   ja luodaan uudelleen uuteen sijaintiin."
  []
  (openlayers/hide-popup-without-event!))

(defonce poista-popup-kun-tasot-muuttuvat
         (tapahtumat/kuuntele! :karttatasot-muuttuneet
                               (fn [_]
                                 (poista-popup!))))


(def aseta-klik-kasittelija! openlayers/aseta-klik-kasittelija!)
(def poista-klik-kasittelija! openlayers/poista-klik-kasittelija!)
(def aseta-hover-kasittelija! openlayers/aseta-hover-kasittelija!)
(def poista-hover-kasittelija! openlayers/poista-hover-kasittelija!)
(def aseta-kursori! openlayers/aseta-kursori!)
(def aseta-tooltip! openlayers/aseta-tooltip!)

(defn kaappaa-hiiri
  "Muuttaa kartan toiminnallisuutta siten, että hover ja click eventit annetaan datana annettuun kanavaan.
Palauttaa funktion, jolla kaappaamisen voi lopettaa. Tapahtumat ovat vektori, jossa on kaksi elementtiä:
tyyppi ja sijainti. Kun kaappaaminen lopetetaan, suljetaan myös annettu kanava."
  [kanava]
  (let [kasittelija #(go (>! kanava %))]
    (aseta-klik-kasittelija! kasittelija)
    (aseta-hover-kasittelija! kasittelija)

    #(do (poista-klik-kasittelija!)
         (poista-hover-kasittelija!)
         (async/close! kanava))))



;; harja.views.kartta=> (viivan-piirto-aloita)
;; klikkaile kartalta pisteitä...
;; harja.views.kartta=> (viivan-piirto-lopeta)
;;
;; js consoleen logittuu koko ajan rakentuva linestring, jonka voi sijainniksi laittaa
(defonce viivan-piirto (cljs.core/atom nil))
(defn ^:export viivan-piirto-aloita []
  (let [eventit (chan)]
    (reset! viivan-piirto
            (kaappaa-hiiri eventit))
    (go-loop [e (<! eventit)
              pisteet []]
             (log "LINESTRING("
                  (str/join ", " (map (fn [[x y]] (str x " " y)) pisteet))
                  ")")
             (when e
               (recur (<! eventit)
                      (if (= :click (:tyyppi e))
                        (conj pisteet (:sijainti e))
                        pisteet))))))

(defn ^:export viivan-piirto-lopeta []
  (@viivan-piirto)
  (reset! viivan-piirto nil))



(defn- paivita-extent [_ newextent]
  (reset! nav/kartalla-nakyva-alue {:xmin (aget newextent 0)
                                    :ymin (aget newextent 1)
                                    :xmax (aget newextent 2)
                                    :ymax (aget newextent 3)}))

(defn zoomaa-valittuun-hallintayksikkoon-tai-urakkaan
  []
  (let [v-hal @nav/valittu-hallintayksikko
        v-ur @nav/valittu-urakka]
    (if-let [alue (and v-ur (:alue v-ur))]
      (keskita-kartta-alueeseen! (geo/extent alue))
      (if-let [alue (and v-hal (:alue v-hal))]
        (keskita-kartta-alueeseen! (geo/extent alue))
        (keskita-kartta-alueeseen! +koko-suomi-extent+)))))

(def pida-geometria-nakyvilla-oletusarvo true)
(defonce pida-geometriat-nakyvilla? (atom pida-geometria-nakyvilla-oletusarvo))

(defn suomen-sisalla? [alue]
  (openlayers/extent-sisaltaa-extent? +koko-suomi-extent+ (geo/extent alue)))

(defn zoomaa-geometrioihin
  "Zoomaa kartan joko kartalla näkyviin geometrioihin, tai jos kartalla ei ole geometrioita,
  valittuun hallintayksikköön tai urakkaan"
  []
  (when @pida-geometriat-nakyvilla?
    ;; Haetaan kaikkien tasojen extentit ja yhdistetään ne laajentamalla
    ;; extentiä siten, että kaikki mahtuvat.
    ;; Jos extentiä tasoista ei ole, zoomataan urakkaan tai hallintayksikköön.
    (let [extent (reduce geo/yhdista-extent
                         (keep #(-> % meta :extent) (vals @tasot/geometriat)))
          extentin-margin-metreina geo/pisteen-extent-laajennus]
      (log "EXTENT TASOISTA: " (pr-str extent))
      (if extent
        (keskita-kartta-alueeseen! (geo/laajenna-extent extent extentin-margin-metreina))
        (zoomaa-valittuun-hallintayksikkoon-tai-urakkaan)))))


(defn kuuntele-valittua! [atomi]
  (add-watch atomi :kartan-valittu-kuuntelija (fn [_ _ _ uusi]
                                                (when-not uusi
                                                  (zoomaa-geometrioihin))))
  #(remove-watch atomi :kartan-valittu-kuuntelija))

(defn- kun-geometriaa-klikattu
  "Event handler geometrioiden yksi- ja tuplaklikkauksille"
  [item event]
  (let [item (assoc item :klikkaus-koordinaatit (js->clj (.-coordinate event)))]
    (condp = (:type item)
      :hy (when-not (= (:id item) (:id @nav/valittu-hallintayksikko))
            (nav/valitse-hallintayksikko item))
      :ur (when-not (= (:id item) (:id @nav/valittu-urakka))
            (t/julkaise! (assoc item :aihe :urakka-klikattu)))
      (t/julkaise! (assoc item :aihe (keyword (str (name (:type item)) "-klikattu")))))))

(defn- geometria-maarat [geometriat]
  (reduce-kv (fn [m k v]
               (if (nil? v)
                 m
                 (assoc m k (count v))))
             {}
             geometriat))

(defn- tapahtuman-geometria-on-valittu-hallintayksikko-tai-urakka?
  [geom]
  (or (and
        (= (:type geom) :ur)
        (= (:id geom) (:id @nav/valittu-urakka)))
      (and
        (= (:type geom) :hy)
        (= (:id geom) (:id @nav/valittu-hallintayksikko)))))

(defn kartta-openlayers []
  (komp/luo

    {:component-did-mount
     #(zoomaa-geometrioihin)}
    (komp/sisaan
      (fn [_]
        (zoomaa-geometrioihin)

        ;; Hallintayksiköt ja valittu urakka ovat nykyään :organisaatio
        ;; tasossa, joten ne eivät tarvitse erillistä kuuntelijaa.
        (add-watch tasot/geometriat :muuttuvien-geometrioiden-kuuntelija
                   (fn [_ _ vanha uusi]
                     ;; Jos vanhoissa ja uusissa geometrioissa ei ole samat määrät asioita,
                     ;; niin voidaan olettaa että nyt geometriat ovat muuttuneet.
                     ;; Tällainen workaround piti tehdä, koska asian valitseminen muuttaa
                     ;; geometriat atomia, mutta silloin ei haluta triggeröidä zoomaamista.
                     ;; Myös jos :organisaatio karttatason tiedot ovat muuttuneet, tehdään zoomaus (urakka/hallintayksikkö muutos)
                     (when @pida-geometriat-nakyvilla?
                       (when (or (not= (geometria-maarat vanha) (geometria-maarat uusi))
                                 (not= (:organisaatio vanha) (:organisaatio uusi)))
                         (zoomaa-geometrioihin)))))))
    (fn []
      (let [koko (if-not (empty? @nav/tarvitsen-isoa-karttaa)
                   :L
                   @nav/kartan-koko)]

        [openlayers
         {:id                 "kartta"
          :width              "100%"
          ;; set width/height as CSS units, must set height as pixels!
          :height             (fmt/pikseleina @kartan-korkeus)
          :style              (when (= koko :S)
                                ;; display none estää kartan korkeuden
                                ;; animoinnin suljettaessa
                                {:display "none"})
          :class              (when (or
                                      (= :hidden koko)
                                      (= :S koko))
                                "piilossa")

          ;; :extent-key muuttuessa zoomataan aina uudelleen, vaikka itse alue ei olisi muuttunut

          :extent-key         (str (if (or (= :hidden koko) (= :S koko)) "piilossa" "auki") "_" (name (nav/sivu)))
          :extent             @nav/kartan-extent

          :selection          nav/valittu-hallintayksikko
          :on-zoom            paivita-extent
          :on-drag            (fn [item event]
                                (paivita-extent item event)
                                (t/julkaise! {:aihe :karttaa-vedetty}))
          :on-mount           (fn [initialextent]
                                (paivita-extent nil initialextent))
          :on-click           (fn [at]
                                (t/julkaise! {:aihe :tyhja-click :klikkaus-koordinaatit at})
                                (poista-popup!))
          :on-select          (fn [item event]
                                (kun-geometriaa-klikattu item event)
                                (.stopPropagation event)
                                (.preventDefault event))

          :on-dblclick        nil

          :on-dblclick-select (fn [item event]
                                ;; jos tuplaklikattiin valittua hallintayksikköä tai urakkaa (eli "tyhjää"),
                                ;; niin silloin ei pysäytetä eventtiä, eli zoomataan sisään
                                (when-not (tapahtuman-geometria-on-valittu-hallintayksikko-tai-urakka? item)
                                  (.stopPropagation event)
                                  (.preventDefault event)

                                  ;; Jos tuplaklikattu asia oli jotain muuta kuin HY/urakka, niin keskitetään
                                  ;; kartta siihen.
                                  (when-not (or (= :ur (:type item))
                                                (= :hy (:type item)))
                                    (kun-geometriaa-klikattu item event)
                                    (keskita-kartta-alueeseen! (harja.geo/extent (:alue item))))))

          :tooltip-fn         (fn [geom]
                                ; Palauttaa funktion joka palauttaa tooltipin sisällön, tai nil jos hoverattu asia
                                ; on valittu hallintayksikkö tai urakka.
                                (if (or (tapahtuman-geometria-on-valittu-hallintayksikko-tai-urakka? geom)
                                        (and (not (:nimi geom)) (not (:siltanimi geom))))
                                  nil
                                  (fn []
                                    (and geom
                                         [:div {:class (name (:type geom))} (or (:nimi geom) (:siltanimi geom))]))))

          :geometries         @tasot/geometriat

          :geometry-fn        (fn [piirrettava]
                                (when-let [{:keys [stroke] :as alue} (:alue piirrettava)]
                                  (when (map? alue)
                                    (assoc alue
                                      :fill (if (:valittu piirrettava) false true)
                                      :stroke (if stroke
                                                stroke
                                                (when (or (:valittu piirrettava)
                                                          (= :silta (:type piirrettava)))
                                                  {:width 3}))
                                      ;;:harja.ui.openlayers/fit-bounds (:valittu piirrettava) ;; kerro kartalle, että siirtyy valittuun
                                      :color (or (:color alue)
                                                 (nth varit/kaikki (mod (:id piirrettava) (count varit/kaikki))))
                                      :zindex (or (:zindex alue) (case (:type piirrettava)
                                                                   :hy 0
                                                                   :ur 1
                                                                   :pohjavesialueet 2
                                                                   :sillat 3
                                                                   openlayers/oletus-zindex))
                                      ;;:marker (= :silta (:type hy))
                                      ))))

          :layers             [{:type  :mml
                                :url   (str (k/wmts-polku) "maasto/wmts")
                                :layer "taustakartta"}]}]))))

(defn kartta []
  [:div
   [paivitetaan-karttaa]
   [kartan-koko-kontrollit]
   [kartan-yleiset-kontrollit]
   [kartan-ohjelaatikko]
   [kartan-ikonien-selitykset]
   [kartta-openlayers]])


;; Käytä tätä jos haluat luoda rinnakkain sisällön ja kartan näkymääsi
;; tämä on täällä eikä ui.yleiset koska olisi tullut syklinen riippuvuus
(defn sisalto-ja-kartta-2-palstana
  "Luo BS-rivin ja sarakkeet, joissa toisella puolella parameterinä annettava sisältö, toisella kartta."
  [sisalto]
  [:div.row
   [:div {:class (if (= @nav/kartan-koko :S)
                   "col-sm-12"
                   "col-sm-6")}
    sisalto]
   [:div {:class (if (= @nav/kartan-koko :S)
                   ""
                   "col-sm-6")}
    [kartan-paikka]]])
