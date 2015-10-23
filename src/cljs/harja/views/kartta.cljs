(ns harja.views.kartta
  "Harjan kartta."
  (:require [reagent.core :refer [atom] :as reagent]

            [goog.events :as events]
            [goog.events.EventType :as EventType]

            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.openlayers :refer [openlayers] :as openlayers]
            [harja.asiakas.tapahtumat :as t]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.kartta.tasot :as tasot]
            [cljs.core.async :refer [timeout <! >! chan] :as async]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.geo :as geo]
            [harja.ui.komponentti :as komp]
            [harja.ui.animaatio :as animaatio]
            [harja.fmt :as fmt])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))


(def kartta-kontentin-vieressa? (atom false))

(def +kartan-korkeus-s+ 26)

(def kartan-korkeus (reaction
                      (let [koko @nav/kartan-koko
                            kork @yleiset/korkeus]
                        (case koko
                          :S +kartan-korkeus-s+
                          :M (int (* 0.20 kork))
                          :L (int (* 0.60 kork))
                          :XL (int (* 0.80 kork))
                          (int (* 0.60 kork))))))

;; Kanava, jonne kartan uusi sijainti kirjoitetaan
(defonce paivita-kartan-sijainti (chan))

(defn- aseta-kartan-sijainti [x y w h naulattu?]
  (let [karttasailio (yleiset/elementti-idlla "kartta-container")
        tyyli (.-style karttasailio)]
    #_(log "ASETA-KARTAN-SIJAINTI: " x ", " y ", " w ", " h ", " naulattu?)
    (if naulattu?
      (do
        (set! (.-position tyyli) "fixed")
        (set! (.-left tyyli) (fmt/pikseleina x))
        (set! (.-top tyyli) "0px")
        (set! (.-width tyyli) (fmt/pikseleina w))
        (set! (.-height tyyli) (fmt/pikseleina h)))
      (do
        (set! (.-position tyyli) "absolute")
        (set! (.-left tyyli) (fmt/pikseleina x))
        (set! (.-top tyyli) (fmt/pikseleina y))
        (set! (.-width tyyli) (fmt/pikseleina w))
        (set! (.-height tyyli) (fmt/pikseleina h))))))

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
                                   (aseta-kartan-sijainti x (- @yleiset/korkeus) w h false)
                                   (recur nil nil nil w h nil))))
                       paikka-elt (<! (elementti-idlla-odota "kartan-paikka"))
                       [uusi-x uusi-y uusi-w uusi-h] (yleiset/sijainti paikka-elt)
                       uusi-offset-y (yleiset/offset-korkeus paikka-elt)]

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
      (komp/kuuntelija :ikkunan-koko-muuttunut #(paivita true))
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


;; Ad hoc geometrioiden näyttäminen näkymistä
;; Avain on avainsana ja arvo on itse geometria
(defonce nakyman-geometriat (atom {}))

(def kartta-ch "Karttakomponentin käskyttämisen komentokanava" (atom nil))
(def +koko-suomi-sijainti+ [431704.1 7211111])
(def +koko-suomi-zoom-taso+ 6)

(defonce kartta-sijainti (atom +koko-suomi-sijainti+))
(defonce zoom-taso (atom +koko-suomi-zoom-taso+))           ;;Miksi tämä on atomi - toimiiko todellisuudessa eri tavalla kuin kuvitellaan?

(defonce kartta-kuuntelija
         (t/kuuntele! :hallintayksikkovalinta-poistettu
                      #(do (reset! kartta-sijainti +koko-suomi-sijainti+)
                           (reset! zoom-taso +koko-suomi-zoom-taso+))))

(defonce urakka-kuuntelija
         (t/kuuntele! :urakka-valittu
                      #(openlayers/hide-popup!)))

;; Joitain värejä... voi keksiä paremmat tai "oikeat", jos sellaiset on tiedossa
(def +varit+ ["#E04836" "#F39D41" "#8D5924" "#5696BC" "#2F5168" "wheat" "teal"])

(defonce kartan-koon-paivitys
         (run! (do @yleiset/ikkunan-koko
                   (openlayers/invalidate-size!))))

(defn kartan-koko-kontrollit
  []
  (let [koko @nav/kartan-koko
        kartan-korkeus @kartan-korkeus
        sivu @nav/sivu
        v-ur @nav/valittu-urakka
        muuta-kokoa-teksti (case koko
                             :M "Suurenna karttaa"
                             :L "Pienennä karttaa"
                             :XL "Pienennä karttaa"
                             "")]
    ;; TODO: tähän alkaa kertyä näkymäkohtaista logiikkaa, mietittävä vaihtoehtoja.
    [:div.kartan-kontrollit.kartan-koko-kontrollit {:class (when (or
                                                                   (not (empty? @nav/tarvitsen-isoa-karttaa))
                                                                   (= sivu :tilannekuva)
                                                                   (and (= sivu :urakat)
                                                                        (not v-ur))) "hide")}


     ;; käytetään tässä inline-tyylejä, koska tarvitsemme kartan-korkeus -arvoa asemointiin
     [:div.kartan-koko-napit {:style {:position   "absolute"
                                      :text-align "center"
                                      :top        (fmt/pikseleina (- kartan-korkeus +kartan-korkeus-s+))
                                      :width      "100%"
                                      :z-index    100}}
      (if (= :S koko)
        [:button.btn-xs.nappi-ensisijainen.nappi-avaa-kartta.pull-right {:on-click #(nav/vaihda-kartan-koko! :L)}
         "Näytä kartta"]
        [:span
         (when-not @kartta-kontentin-vieressa?              ;ei pointtia muuttaa korkeutta jos ollaan kontentin vieressä
           [:button.btn-xs.nappi-toissijainen {:on-click #(nav/vaihda-kartan-koko!
                                                           (case koko
                                                             :M :L
                                                             :L :M
                                                             ;; jos tulee tarve, voimme hanskata kokoja kolmella napilla
                                                             ;; suurenna | pienennä | piilota
                                                             :XL :M))}
            muuta-kokoa-teksti])

         [:button.btn-xs.nappi-ensisijainen {:on-click #(nav/vaihda-kartan-koko! :S)}
          "Piilota kartta"]])]]))

(def kartan-yleiset-kontrollit-sisalto (atom nil))

(def keskita-kartta-pisteeseen openlayers/keskita-kartta-pisteeseen!)
(def keskita-kartta-alueeseen! openlayers/keskita-kartta-alueeseen!)


(defn kartan-yleiset-kontrollit
  "Kartan yleiset kontrollit -komponentti, johon voidaan antaa mitä tahansa sisältöä, jota tietyssä näkymässä tarvitaan"
  []
  (let [sisalto @kartan-yleiset-kontrollit-sisalto]
    [:span.kartan-kontrollit.kartan-yleiset-kontrollit sisalto]))

(defn aseta-yleiset-kontrollit [uusi-sisalto]
  (reset! kartan-yleiset-kontrollit-sisalto uusi-sisalto))

(defn tyhjenna-yleiset-kontrollit []
  (reset! kartan-yleiset-kontrollit-sisalto nil))

(defn nayta-popup!
  "Näyttää popup sisällön kartalla tietyssä sijainnissa. Sijainti on vektori [lat lng], 
joka kertoo karttakoordinaatit. Sisältö annetaan sisalto-hiccup muodossa ja se renderöidään
HTML merkkijonoksi reagent render-to-string funktiolla (eikä siis ole täysiverinen komponentti)"
  [sijainti sisalto-hiccup]
  (openlayers/show-popup! sijainti sisalto-hiccup))

(defn poista-popup! []
  (openlayers/hide-popup!))

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


(defn nayta-geometria! [avain geometria]
  (assert (and (map? geometria)
               (contains? geometria :alue)) "Geometrian tulee olla mäpissä :alue avaimessa!")
  (swap! nakyman-geometriat assoc avain geometria))

(defn poista-geometria! [avain]
  (swap! nakyman-geometriat dissoc avain))

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
        (keskita-kartta-alueeseen! (geo/extent alue))))))

(defn zoomaa-geometrioihin
  "Zoomaa kartan joko kartalla näkyviin geometrioihin, tai jos kartalla ei ole geometrioita,
  valittuun hallintayksikköön tai urakkaan"
  []
  (if-not (empty? (keep :alue @tasot/geometriat))
    (keskita-kartta-alueeseen! (geo/extent-monelle (keep :alue @tasot/geometriat)))
    (zoomaa-valittuun-hallintayksikkoon-tai-urakkaan)))

(defonce pida-geometriat-nakyvilla? (atom true))

(defn kuuntele-valittua! [atomi]
  (add-watch atomi :kartan-valittu-kuuntelija (fn [_ _ _ uusi]
                                                (when-not uusi
                                                  (zoomaa-geometrioihin))))
  #(remove-watch atomi :kartan-valittu-kuuntelija))

(defonce zoomaa-valittuun-hallintayksikkoon-tai-urakkaan-runner
         (let [ch (chan)]
           (run! @nav/valittu-hallintayksikko
                 @nav/valittu-urakka
                 ;; Reagoidaan hallintayksikön tai urakan muutokseen vain jos geometriat ovat tyhjiä
                 ;; Tälle ei pitäisi olla tarvetta, mutta tuntui että tätä runia ajettiin joskus turhaan, ja kartta
                 ;; käyttäytyi oudosti. Nyt tässä halutaan reagoida vain kun vaihdetaan urakkaa tai hy:tä, ts. kun
                 ;; geometriat ovat tyhjät.
                 (when (empty? @tasot/geometriat)
                   (zoomaa-geometrioihin)))
           (run! (let [koko @nav/kartan-koko]
                   (go (>! ch koko))))
           (go (loop [edellinen-koko @nav/kartan-koko]
                 (let [nykyinen-koko (<! ch)]
                   (<! (timeout 150))
                   ;; Aiemmin zoomattiin vain kun edellinen koko oli S ja nykyinen ei ole S. Miksi..?
                   (zoomaa-geometrioihin)
                   (recur nykyinen-koko))))))

(defn kartta-openlayers []
  (komp/luo
    {:component-did-mount (fn [_]
                            (zoomaa-geometrioihin)

                            (add-watch tasot/geometriat :muuttuvien-geometrioiden-kuuntelija
                                       (fn [_ _ vanha uusi]
                                         ;; Jos vektoreissa olevissa mäpeissä ei ole samat avaimet,
                                         ;; niin voidaan olettaa että nyt geometriat ovat muuttuneet.
                                         ;; Tällainen workaround piti tehdä, koska asian valitseminen muuttaa
                                         ;; geometriat atomia, mutta silloin ei haluta triggeröidä zoomaamista.
                                         (when @pida-geometriat-nakyvilla?
                                           (if (or
                                                 (not (= (count vanha) (count uusi)))
                                                 (some false?
                                                       (map
                                                         (fn [vanha uusi] (= (dissoc vanha :alue) (dissoc uusi :alue)))
                                                         vanha uusi)))
                                             (zoomaa-geometrioihin))))))}
    (fn []
      (let [hals @hal/hallintayksikot
            v-hal @nav/valittu-hallintayksikko
            koko @nav/kartan-koko
            koko (if-not (empty? @nav/tarvitsen-isoa-karttaa)
                   :L
                   koko)]

        [openlayers
         {:id                 "kartta"
          :width              "100%"
          ;; set width/height as CSS units, must set height as pixels!
          :height             (fmt/pikseleina @kartan-korkeus)
          :style              (when (= koko :S)
                                {:display "none"})          ;;display none estää kartan korkeuden animoinnin suljettaessa
          :class              (when (or
                                      (= :hidden koko)
                                      (= :S koko))
                                "piilossa")
          :view               kartta-sijainti
          :zoom               zoom-taso
          :selection          nav/valittu-hallintayksikko
          :on-zoom            paivita-extent
          :on-drag            (fn [item event]
                                (paivita-extent item event)
                                (t/julkaise! {:aihe :karttaa-vedetty}))
          :on-mount           (fn [initialextent] (paivita-extent nil initialextent))
          :on-click           (fn [at]
                                (t/julkaise! {:aihe :tyhja-click :klikkaus-koordinaatit at})
                                (poista-popup!))
          :on-select          (fn [item event]
                                (let [item (assoc item :klikkaus-koordinaatit (js->clj (.-coordinate event)))]
                                  (condp = (:type item)
                                    :hy (when-not (= (:id item) (:id @nav/valittu-hallintayksikko))
                                          (nav/valitse-hallintayksikko item))
                                    :ur (when-not (= (:id item) (:id @nav/valittu-urakka))
                                          (t/julkaise! (assoc item :aihe :urakka-klikattu)))
                                    (t/julkaise! (assoc item :aihe (keyword (str (name (:type item)) "-klikattu")))))))
          :on-dblclick        nil
          :on-dblclick-select (fn [item event]
                                (let [item (assoc item :klikkaus-koordinaatit (js->clj (.-coordinate event)))]
                                  (condp = (:type item)
                                    :hy (when-not (= (:id item) (:id @nav/valittu-hallintayksikko))
                                          (nav/valitse-hallintayksikko item))
                                    :ur (when-not (= (:id item) (:id @nav/valittu-urakka))
                                          (t/julkaise! (assoc item :aihe :urakka-klikattu)))
                                    (do (keskita-kartta-alueeseen! (harja.geo/extent (:alue item)))
                                        (t/julkaise! (assoc item :aihe (keyword (str (name (:type item)) "-klikattu"))))

                                        ;; Estetään zoomaaminen kun tuplaklikillä valitaan geometria
                                        (.stopPropagation event)
                                        (.preventDefault event)))))
          :tooltip-fn         (fn [geom]
                                (and geom
                                     [:div {:class (name (:type geom))} (or (:nimi geom) (:siltanimi geom))]))
          :geometries
                              (doall (concat (cond
                                               ;; Tilannekuvassa ja ilmoituksissa ei haluta näyttää navigointiin tarkoitettuja
                                               ;; geometrioita (kuten urakat), mutta jos esim HY on valittu, voidaan näyttää sen rajat.
                                               (and (#{:tilannekuva :ilmoitukset} @nav/sivu) (nil? v-hal))
                                               nil

                                               (and (#{:tilannekuva :ilmoitukset} @nav/sivu) (nil? @nav/valittu-urakka))
                                               [(assoc v-hal :valittu true)]

                                               (and (#{:tilannekuva :ilmoitukset} @nav/sivu) @nav/valittu-urakka)
                                               [(assoc @nav/valittu-urakka :valittu true)]

                                               ;; Ei valittua hallintayksikköä, näytetään hallintayksiköt
                                               (nil? v-hal)
                                               hals

                                               ;; Ei valittua urakkaa, näytetään valittu hallintayksikkö ja sen urakat
                                               (nil? @nav/valittu-urakka)
                                               (vec (concat [(assoc v-hal
                                                               :valittu true)]
                                                            @nav/urakat-kartalla))

                                               ;; Valittu urakka, mitä näytetään?
                                               :default [(assoc @nav/valittu-urakka
                                                           :valittu true
                                                           :harja.ui.openlayers/fit-bounds true)])
                                             @tasot/geometriat
                                             (vals @nakyman-geometriat)))

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
                                                 (nth +varit+ (mod (hash (:nimi piirrettava)) (count +varit+))))
                                      :zindex (or (:zindex alue) (case (:type piirrettava)
                                                                   :hy 0
                                                                   :ur 1
                                                                   :pohjavesialueet 2
                                                                   :sillat 3
                                                                   4))
                                      ;;:marker (= :silta (:type hy))
                                      ))))

          :layers             [{:type  :mml
                                :url   (str (k/wmts-polku) "maasto/wmts")
                                :layer "taustakartta"}]}]))))

(defn kartta []
  [:div
   [kartan-koko-kontrollit]
   [kartan-yleiset-kontrollit]
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
