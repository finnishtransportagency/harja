(ns harja.views.kartta
  "Tämä namespace sisältää varsinaisen, käyttäjälle näkyvän karttakomponentin.
  Karttakokonaisuus on Harjan toteutuksen aikana laajentunut paljon, ja aina kun
  tuska on kasvanut tarpeeksi suureksi, on koodia refaktoroitu eri namespaceihin.
  Valitettavasti tämä refaktorointi ei ole ikinä onnistunut täydellisesti, ja erityisesti
  tämä namespace saattaa sisältää koodia, joka kirjotettaisiin nyt eri tavalla.

  Oleellisia namespacen ominaisuuksia ovat infopaneelin, zoomausten, ja kartalla näkyvien
  selitteidein hallinta. Hyvä paikka lähteä tutkimaan kokonaisuutta on lopusta löytyvä
  komponentti, jossa luodaan openlayers-kartta, ja määritellään mm. handlerit erilaisille
  click-tapahtumille.

  harja.tiedot.kartta sisältää koodia, joka löytyi ennen tästä namespacesta. Tämä jako olisi pitänyt
  tehdä alusta alkaen, mutta ei tehty. Lopulta jako jouduttiin tekemään circular dependencyjen
  välttämiseksi. Tästä johtuen nämä kaksi namespacea saattavat sisältää koodia, jonka oikeastaan
  pitäisi olla toisessa namespacessa.

  Jos sinua kiinnostaa se, millä tyyleillä erilaiset asiat piirretään kartalle,
  katso namespaceja harja.ui.kartta.esitettavat-asiat ja harja.ui.kartta.asioiden-ulkoasu. Näissä
  namespaceissa käytetään ikään kuin DSL:ää määrittelemään asioiden tyylit. Nämä tietorakenteet
  annetaan eteenpäin, jossa ne tulkitaan ja piirretään.

  Em. tietorakenteita käsitellään frontilla namespacessa harja.ui.openlayers.featuret ja palvelimella
  harja.palvelin.palvelut.karttakuvat.piirto. Esimerkiksi tarkastusten reitit piirretään palvelimella,
  koska frontilla piirto on liian raskasta.

  harja.views.kartta.tasot sisältää tasoja, joihin kartalle piirrettävät geometriat piirtyvät. Tämä
  liittyy vahvasti Openlayersin ominaisuuteen. Tasot voivat sisältää joko frontilla piirretäviä geometrioita
  (harja.ui.openlayers.geometriataso), tai tai palvelimella piirrettäviä kuvia (harja.ui.openlayers.kuvataso)

  Infopaneelin ulkoasu määritellään namespacessa harja.views.kartta.infopaneeli, ja sisällön
  muodostaminen tapahtuu namespacessa harja.ui.kartta.infopaneelin-sisalto"
  (:require [cljs.core.async :refer [timeout <! >! chan] :as async]
            [clojure.string :as str]
            [clojure.set :as set]
            [goog.events.EventType :as EventType]
            [goog.events :as events]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.fmt :as fmt]
            [harja.geo :as geo]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.animaatio :as animaatio]
            [harja.ui.komponentti :as komp]
            [harja.ui.openlayers :refer [openlayers] :as openlayers]
            [harja.ui.dom :as dom]
            [harja.views.kartta.tasot :as tasot]
            [harja.views.kartta.infopaneeli :as infopaneeli]
            [reagent.core :refer [atom] :as reagent]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.openlayers.taso :as taso]
            [harja.ui.kartta.apurit :refer [+koko-suomi-extent+]]
            [harja.ui.openlayers.edistymispalkki :as edistymispalkki]
            [harja.tiedot.kartta :as tiedot]
            [harja.ui.kartta.ikonit :as kartta-ikonit]
            [harja.ui.kartta-debug :refer [aseta-kartta-debug-sijainti]])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go go-loop]]))


(def +kartan-napit-padding+ 26)
(def +kartan-korkeus-s+ 0)

(def tasot-joita-zoomataan-aina
  ^{:doc "Jos näitä tasoja liittyy uuteen kartan geometriaan,
  zoomataan aina geometrioihin."}
  #{:kokonaishintaisten-turvalaitteet
    :yksikkohintaisten-turvalaitteet})

(def kartan-korkeus (reaction
                      (let [koko @nav/kartan-koko
                            kork @dom/korkeus
                            murupolku? @nav/murupolku-nakyvissa?]
                        (case koko
                          :S +kartan-korkeus-s+
                          :M (int (* 0.25 kork))
                          :L (int (* 0.60 kork))
                          :XL (int (if murupolku?
                                     (* 0.80 kork)
                                     (- kork (yleiset/navigaation-korkeus) 5)))
                          (int (* 0.60 kork))))))

;; Kanava, jonne kartan uusi sijainti kirjoitetaan
(defonce paivita-kartan-sijainti (chan))

(defn- aseta-kartan-sijainti [x y w h naulattu?]
  (when-let
    [karttasailio (dom/elementti-idlla "kartta-container")]
    (let [tyyli (.-style karttasailio)]
      ;;(log "ASETA-KARTAN-SIJAINTI: " x ", " y ", " w ", " h ", " naulattu?)
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
                            (aseta-kartta-debug-sijainti x (- @dom/korkeus) w h false)
                            (recur nil nil nil w h nil))))
                paikka-elt (<! (dom/elementti-idlla-odota "kartan-paikka"))
                [uusi-x uusi-y uusi-w uusi-h] (dom/sijainti paikka-elt)
                uusi-offset-y (dom/offset-korkeus paikka-elt)]

            ;; (log "KARTAN PAIKKA: " x "," y " (" w "x" h ") OY: " offset-y " => " uusi-x "," uusi-y " (" uusi-w "x" uusi-h ") OY: " uusi-offset-y)


            (cond
              ;; Eka kerta, asetetaan kartan sijainti
              (or (= :aseta paivita) aseta (nil? naulattu?))
              (let [naulattu? (neg? uusi-y)]
                                        ;(log "EKA KERTA")
                (aseta-kartan-sijainti uusi-x uusi-offset-y uusi-w uusi-h naulattu?)
                (aseta-kartta-debug-sijainti uusi-x uusi-offset-y uusi-w uusi-h naulattu?)
                (when (or (not= w uusi-w) (not= h uusi-h))
                  (reagent/next-tick #(openlayers/invalidate-size!)))
                (recur naulattu?
                       uusi-x uusi-y uusi-w uusi-h uusi-offset-y))

              ;; Jos kartta ei ollut naulattu yläreunaan ja nyt meni negatiiviseksi
              ;; koko pitää asettaa
              (and (not naulattu?) (neg? uusi-y))
              (do (aseta-kartan-sijainti uusi-x uusi-y uusi-w uusi-h true)
                  (aseta-kartta-debug-sijainti uusi-x uusi-y uusi-w uusi-h true)
                  (recur true
                         uusi-x uusi-y uusi-w uusi-h uusi-offset-y))

              ;; Jos oli naulattu ja nyt on positiivinen, pitää naulat irroittaa
              (and naulattu? (pos? uusi-y))
              (do (aseta-kartan-sijainti uusi-x uusi-offset-y uusi-w uusi-h false)
                  (aseta-kartta-debug-sijainti uusi-x uusi-offset-y uusi-w uusi-h false)
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
                        :murupolku-naytetty-domissa?}
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
      (if (= :S koko)
        [:span
         [kartan-paikkavaraus koko args]
         [:div.pystyvali-karttanapille]]
        [kartan-paikkavaraus koko args])
      [:span.ei-karttaa])))

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
        v-ur @nav/valittu-urakka
        [muuta-kokoa-teksti ikoni] (case koko
                             :M ["Suurenna karttaa" (ikonit/livicon-arrow-down)]
                             :L ["Pienennä karttaa" (ikonit/livicon-arrow-up)]
                             :XL ["Pienennä karttaa" (ikonit/livicon-arrow-up)]
                             ["" nil])]
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
         [:button.btn-xs.nappi-toissijainen {:on-click #(nav/vaihda-kartan-koko!
                                                         (case koko
                                                           :M :L
                                                           :L :M
                                                           ;; jos tulee tarve, voimme hanskata kokoja kolmella napilla
                                                           ;; suurenna | pienennä | piilota
                                                           :XL :M))}
          ikoni muuta-kokoa-teksti]

         [:button.btn-xs.nappi-ensisijainen {:on-click #(nav/vaihda-kartan-koko! :S)
                                             :data-cy "piilota-kartta"}
          (ikonit/compress) " Piilota kartta"]])]]))

(def keskita-kartta-pisteeseen openlayers/keskita-kartta-pisteeseen!)

(defn kartan-ikonien-selitykset []
  (let [selitteet (reduce set/union
                          (keep #(when % (taso/selitteet %))
                                (vals @tasot/geometriat-kartalle)))
        lukumaara-str (fmt/left-pad 2 (count selitteet))
        varilaatikon-koko 20
        teksti (if @tiedot/ikonien-selitykset-auki
                 (str "Piilota | " lukumaara-str " kpl")
                 (str "Karttaselitteet | " lukumaara-str " kpl"))]
    (if (and (not= :S @nav/kartan-koko)
             (not (empty? selitteet))
             @tiedot/ikonien-selitykset-nakyvissa?)
      [:div.kartan-selitykset.kartan-ikonien-selitykset
       {:class (when (= :vasen @tiedot/ikonien-selitykset-sijainti) "kartan-ikonien-selitykset-vasen")}
       (if @tiedot/ikonien-selitykset-auki
         [:div
          [:table
           [:tbody
            (for [{:keys [img vari teksti]} (sort-by :teksti selitteet)]
              (when
                (or (not-empty vari) (not-empty img))
                ^{:key (str (or vari img) "_" teksti)}
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
                          ^{:key (str koko "_" v "--" teksti)}
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
                 [:td.kartan-ikonien-selitykset-selitys-sarake [:span.kartan-ikonin-selitys teksti]]]))]]
          [:div.kartan-ikonien-selitykset-sulje.klikattava
           {:on-click (fn [event]
                        (reset! tiedot/ikonien-selitykset-auki false)
                        (.stopPropagation event)
                        (.preventDefault event))} teksti]]
         [:span.kartan-ikonien-selitykset-avaa.klikattava {:on-click (fn [event]
                                                                       (reset! tiedot/ikonien-selitykset-auki true)
                                                                       (.stopPropagation event)
                                                                       (.preventDefault event))}
          teksti])])))

(defn kartan-yleiset-kontrollit
  "Kartan yleiset kontrollit -komponentti, johon voidaan antaa mitä tahansa sisältöä, jota tietyssä näkymässä tarvitaan"
  []
  (let [sisalto @tiedot/kartan-yleiset-kontrollit-sisalto]
    (when-not (and (empty? sisalto)
                   (not= :S @nav/kartan-koko))
      [:span
       (for [[nimi sisalto] sisalto
             :let [luokka-str (or (:class (meta sisalto))
                                  "kartan-yleiset-kontrollit")]]
         ^{:key (str nimi)}
         [:div {:class (str "kartan-kontrollit " luokka-str)} sisalto])])))

(def paivitetaan-karttaa-tila (atom false))
(defonce kuvatason-lataus (atom nil))
(defonce geometriatason-lataus (atom nil))

;; Määrittelee asiat, jotka ovat nykyisessä pisteessä.
;; Avaimet:
;; :koordinaatti  klikatun pisteen koordinatti (tai nil, jos ei valintaa)
;; :asiat         sekvenssi asioita, joita pisteestä löytyy
;; :haetaan?      true kun haku vielä kesken
(defonce asiat-pisteessa (atom {:koordinaatti nil
                                :haetaan? true
                                :asiat nil}))

(defn paivitetaan-karttaa
  []
  (when @paivitetaan-karttaa-tila
    [:div {:style {:position "absolute" :top "50%" :left "50%"}}
     [:div {:style {:position "relative" :left "-50px" :top "-30px"}}
      [:div.paivitetaan-karttaa (yleiset/ajax-loader "Päivitetään karttaa")]]]))

(defonce kuuntele-kuvatason-paivitys
         (t/kuuntele! :edistymispalkki/kuvataso
                      #(reset! kuvatason-lataus %)))

(defonce kuuntele-geometriatason-paivitys
         (t/kuuntele! :edistymispalkki/geometriataso
                      #(reset! geometriatason-lataus %)))

(defn aseta-paivitetaan-karttaa-tila! [uusi-tila]
  (reset! paivitetaan-karttaa-tila uusi-tila))

(defn kartan-ohjelaatikko
  "Kartan ohjelaatikko -komponentti, johon voidaan antaa mitä tahansa sisältöä, jota tietyssä näkymässä tarvitaan"
  []
  (let [sisalto @tiedot/kartan-ohjelaatikko-sisalto]
    (when (and sisalto (not= :S @nav/kartan-koko))
      [:div.kartan-kontrollit.kartan-ohjelaatikko sisalto])))

;; harja.views.kartta=> (viivan-piirto-aloita)
;; klikkaile kartalta pisteitä...
;; harja.views.kartta=> (viivan-piirto-lopeta)
;;
;; js consoleen logittuu koko ajan rakentuva linestring, jonka voi sijainniksi laittaa
(defonce viivan-piirto (cljs.core/atom nil))
(defn ^:export viivan-piirto-aloita []
  (let [eventit (chan)]
    (reset! viivan-piirto
            (tiedot/kaappaa-hiiri eventit))
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


(defn suomen-sisalla? [alue]
  (openlayers/extent-sisaltaa-extent? +koko-suomi-extent+ (geo/extent alue)))

(defn- nayta-infopaneelissa! [& items]
  (apply swap! asiat-pisteessa update :asiat conj items))

(defn- hae-asiat-pisteessa! [tasot event atomi]
  (let [koordinaatti (js->clj (.-coordinate event))
        extent ((juxt :xmin :ymin :xmax :ymax) @nav/kartalla-nakyva-alue)
        nayta-neula! #(tasot/nayta-geometria! :klikattu-karttapiste
                                              {:alue {:type :icon
                                                      :coordinates %
                                                      :img (kartta-ikonit/sijainti-ikoni "syaani")}}
                                              :infopaneelin-merkki)]
    (nayta-neula! koordinaatti)
    (swap! atomi assoc
           :koordinaatti koordinaatti
           :haetaan? true
           :asiat [])

    (go
      (let [in-ch (async/merge
                   (map #(taso/hae-asiat-pisteessa % koordinaatti extent)
                        (remove nil? (vals tasot))))]
        (loop [asia (<! in-ch)]
          (when asia
            (swap! atomi update :asiat conj asia)
            (recur (<! in-ch))))
        (swap! atomi assoc :haetaan? false)))))

(defn- geometria-maarat [geometriat]
  (reduce-kv (fn [m k v]
               (if (nil? v)
                 m
                 (assoc m k (count v))))
             {}
             geometriat))

(defn- tapahtuman-geometria-on-hallintayksikko-tai-urakka? [geom]
  (or (= :ur (:type geom))
      (= :hy (:type geom))))

(defn- tapahtuman-geometria-on-valittu-hallintayksikko-tai-urakka?
  ([geom] (tapahtuman-geometria-on-valittu-hallintayksikko-tai-urakka?
           (:id @nav/valittu-urakka) (:id @nav/valittu-hallintayksikko) geom))
  ([valittu-urakka valittu-hy geom]
   (or (and
         (= (:type geom) :ur)
         (= (:id geom) valittu-urakka))
       (and
         (= (:type geom) :hy)
         (= (:id geom) valittu-hy)))))

(defn kaynnista-infopaneeliin-haku-pisteesta! [tasot event asiat-pisteessa]
  (hae-asiat-pisteessa! tasot event asiat-pisteessa)
  (tiedot/nayta-infopaneeli!))

(defn- piilota-infopaneeli-jos-muuttunut
  "Jos annetut geometriat ovat muuttuneet (pl. näkymän geometriat), piilota infopaneeli."
  [vanha uusi]
  (when (not= (dissoc vanha :infopaneelin-merkki)
              (dissoc uusi :infopaneelin-merkki))
    ;; Kun karttatasoissa muuttuu jotain muuta kuin :nakyman-geometriat
    ;; (klikattu piste), piilotetaan infopaneeli ja poistetaan
    ;; klikattu piste näkymän geometrioista.
    (tiedot/piilota-infopaneeli!)))

(defn- zoomaa-geometrioihin-jos-muuttunut
  "Zoomaa geometrioihin uudelleen, jos ne ovat muuttuneet."
  [vanha uusi]
  ;; Jos vanhoissa ja uusissa geometrioissa ei ole samat määrät asioita,
  ;; niin voidaan olettaa että nyt geometriat ovat muuttuneet.
  ;; Tällainen workaround piti tehdä, koska asian valitseminen muuttaa
  ;; geometriat atomia, mutta silloin ei haluta triggeröidä zoomaamista.
  ;; Myös jos :organisaatio karttatason tiedot ovat muuttuneet, tehdään
  ;; zoomaus (urakka/hallintayksikkö muutos)
  ;; Lisätty näkymäkohtainen zoom-logiikan ylikirjoitus. Karvainen ratkaisu,
  ;; mutta tähän hätään paras. Vesiväylien toimenpidenäkymissä käy usein niin,
  ;; että kartalla on aluksi yksi turvalaite, ja siihen vaihdetaan tilalle toinen
  ;; turvalaite. Aiemmin tässä tilanteessa zoomaus ei toiminut, koska muutos
  ;; tehdään lukumäärän perusteella. Ohitettavia tasoja ei oteta huomioon, jos
  ;; edellisestä tai uudesta tilasta löytyy infopaneelin merkki
  (when @tiedot/pida-geometriat-nakyvilla?
    (let [vanha-maara (geometria-maarat vanha)
          uusi-maara (geometria-maarat uusi)]
      (when (or (and (not= 1 (:infopaneelin-merkki vanha-maara))
                     (not= 1 (:infopaneelin-merkki uusi-maara))
                     (some tasot-joita-zoomataan-aina (keys uusi-maara)))
                (not= (dissoc vanha-maara :infopaneelin-merkki)
                      (dissoc uusi-maara :infopaneelin-merkki))
                (not= (:organisaatio vanha) (:organisaatio uusi)))
        (tiedot/zoomaa-geometrioihin)))))

(defn- geometriat-muuttuneet
  "Käsittelee geometrioiden muutoksen. Parametrina vanhat ja uudet geometriat."
  [vanha uusi]
  ;; HAR-4461: kokeillaan tuleeko tämän poistamisesta haittavaikutuksia.
  #_(piilota-infopaneeli-jos-muuttunut vanha uusi)
  (zoomaa-geometrioihin-jos-muuttunut vanha uusi))

(defn- nayta-paneelissa?-fn
  [item]
  (if (contains? item :nayta-paneelissa?)
    (:nayta-paneelissa? item)
    true))

(defn klikkauksesta-seuraavat-tapahtumat
  "Funktio palauttaa mäpin, joka sisältää ohjeet, miten kyseiseen select-tapahtumaan
  tulee reagoida.

  Parametrit:
  - items: Vektori asioita, joihin klikkaus osui. Organisaatioita ja \"muita\"
  - tuplaklik?: true/false. Oliko tuplaklikkaus
  - sivu: nykyinen sivu
  - val-ur-id, val-hy-id: valitun urakan ja hallintayksikön idt:

  Paluuarvo on mäp, joka sisältää:
  - avaa-paneeli? true/false: Avataanko infopaneeli, ja aloitetaanko haku
  - nayta-nama-paneelissa [{..}, {..}]: Vektori mäppejä, jotka lisätään paneeliin
  - keskita-naihin [{}]: Vektori mäppejä. Kartta zoomataan siten, että kaikki nämä mahtuvat kartalle.
  - keskeyta-event? true/false: Käytännössä estää zoomauksen kun karttaa tuplaklikataan.
  - valitse-urakka {}: valitse klikattu urakka, esim etusivulla, raporteissa.
  - valitse-hallintayksikko {}: Valitse klikattu hy
  - valitse-ilmoitus {}: Ilmoitusnäkymässä yhden ilmoituksen klikkaaminen aukaisee suoraan lomakkeen"
  [items tuplaklik? sivu val-ur-id val-hy-id]
  (let [monta? #(< 1 (count %))
        klikatut-organisaatiot (filter tapahtuman-geometria-on-hallintayksikko-tai-urakka? items)
        paallimmainen-organisatio (first klikatut-organisaatiot)
        klikatut-asiat (remove #(or (tapahtuman-geometria-on-hallintayksikko-tai-urakka? %)
                                    (not (nayta-paneelissa?-fn %)))
                               items)
        urakka? #(= :ur (:type %))
        hallintayksikko? #(= :hy (:type %))
        valittu-organisaatio? #(tapahtuman-geometria-on-valittu-hallintayksikko-tai-urakka?
                                val-ur-id val-hy-id %)
        avaa-paneeli?-sisaltava-item (some #(when (contains? % :avaa-paneeli?)
                                              %)
                                           items)
        avaa-paneeli? (if (nil? avaa-paneeli?-sisaltava-item) true (:avaa-paneeli? avaa-paneeli?-sisaltava-item))]
    ;; Select tarkoittaa, että on klikattu jotain kartalla piirrettyä asiaa.
    ;; Tuplaklikkaukseen halutaan reagoida joko kohdentamalla tuplaklikattuun asiaan
    ;; ja avaamalla sen tiedot infopaneeliin (paitsi ilmoituksissa, missä avataan suoraan lomake),
    ;; tai jos tuplaklikattu asia oli urakka, zoomaataan vaan karttaa askel eteenpäin.
    ;; Yksittäinen select toimii asiaa klikatessa samoin kuin tuplaklikkaus, mutta kohteeseen
    ;; ei kohdenneta. Urakan selectointi tarkoittaa käyttäjän näkökulmasta "tyhjän" tai
    ;; palvelinpäässä piirretyn toteuman klikkaamista, jolloin avataan infopaneeli, ja
    ;; haetaan esim kyseiselle tielle tietoja.
    ;;
    ;; Tuplaklikkauksissa eventin keskeyttäminen tarkoittaa, että zoomausta ei tehdä.

    ;; Käyttäjä voi itse määrittää mitä tapahtuu, kun itemiä klikataan
    (some #(when (contains? % :on-item-click)
             ((:on-item-click %) %))
          items)
    (cond
      ;; Ilmoituksissa ei haluta ikinä näyttää infopaneelia,
      ;; vaan valitaan klikattu ilmoitus
      (#{:ilmoitukset} sivu)
      (when-not (empty? klikatut-asiat) ;; Älä siirrä tätä cond-ehtoon
        (merge
          {:keskeyta-event? true}
          (if (monta? klikatut-asiat)
            {:avaa-paneeli? true
             :nayta-nama-paneelissa klikatut-asiat}
            {:valitse-ilmoitus (first klikatut-asiat)})
          (when tuplaklik?
            {:keskita-naihin klikatut-asiat})))

      ;; Tilannekuvassa voidaan klikata valitsematonta hallintayksikköä
      ;; tai urakkaa, ja silti avataan infopaneeli pisteessä olevista asioista.
      (and (#{:tilannekuva} sivu)
           (empty? klikatut-asiat))
      (when-not tuplaklik?
        {:keskeyta-event? true
         :avaa-paneeli? true})

      ;; Tien klikkaaminen esim toteuma-näkymässä osuu valittuun urakkaan
      (and (empty? klikatut-asiat)
           (urakka? paallimmainen-organisatio)
           (valittu-organisaatio? paallimmainen-organisatio))
      (when-not (#{:raportit} sivu)
        (when-not tuplaklik?
          {:keskeyta-event? true
           :avaa-paneeli? true}))

      ;; "Ohi klikkaaminen" etusivulla ei tee mitään
      (and (empty? klikatut-asiat)
           (hallintayksikko? paallimmainen-organisatio)
           (valittu-organisaatio? paallimmainen-organisatio))
      nil

      ;; Urakan tai hallintayksikön valitseminen etusivulla
      (and (empty? klikatut-asiat)
           (not-empty klikatut-organisaatiot))
      (do
        (merge
          {:keskeyta-event? true}
          (if (urakka? (first klikatut-organisaatiot))
            {:valitse-urakka (first klikatut-organisaatiot)}
            {:valitse-hallintayksikko (first klikatut-organisaatiot)})
          (when tuplaklik?
            {:keskita-naihin [(first klikatut-organisaatiot)]})))

      ;; Klikattu asia ei ole hy/urakka, eikä se ole ilmoitus ilmoitusnäkymässä.
      ;; Avataan infopaneeliin klikatun asian tiedot, ja haetaan sinne mahdollisesti
      ;; muutakin
      :default
      (do
        (merge
          {:keskeyta-event? true
          :avaa-paneeli? avaa-paneeli?
          :nayta-nama-paneelissa klikatut-asiat}
          (when tuplaklik? {:keskita-naihin klikatut-asiat}))))))

(defn- kasittele-select!
  ([items event] (kasittele-select! items event false))
  ([items event tuplaklik?]
   (let [{:keys [keskeyta-event?
                 valitse-urakka
                 valitse-hallintayksikko
                 valitse-ilmoitus
                 avaa-paneeli?
                 nayta-nama-paneelissa
                 keskita-naihin]} (klikkauksesta-seuraavat-tapahtumat items tuplaklik? @nav/valittu-sivu
                                                                        (:id @nav/valittu-urakka) (:id @nav/valittu-hallintayksikko))]
     (when keskeyta-event?
       (.stopPropagation event)
       (.preventDefault event))

     (when valitse-urakka (t/julkaise! (assoc valitse-urakka :aihe :urakka-klikattu)))

     (when valitse-hallintayksikko (nav/valitse-hallintayksikko! valitse-hallintayksikko))

     (when valitse-ilmoitus (t/julkaise! (assoc valitse-ilmoitus :aihe :ilmoitus-klikattu)))

     (when avaa-paneeli? (kaynnista-infopaneeliin-haku-pisteesta! @tasot/geometriat-kartalle
                                                                  event
                                                                  asiat-pisteessa))

     (when-not (empty? nayta-nama-paneelissa)
       (apply nayta-infopaneelissa! nayta-nama-paneelissa))

     (when-not (empty? keskita-naihin)
       (tiedot/keskita-kartta-alueeseen! (harja.geo/laajenna-extent-prosentilla (harja.geo/extent-monelle (map :alue keskita-naihin))))))))

(defn- kasittele-dblclick-select! [item event]
  (kasittele-select! item event true))

(defn kartta-openlayers []
  (komp/luo

   (komp/sisaan tiedot/zoomaa-geometrioihin)

   (komp/watcher nav/kartan-koko
                 (fn [_ _ _]
                   (when @tiedot/pida-geometriat-nakyvilla?
                     (log "Kartan koko muuttui, zoomataan!")
                     (tiedot/zoomaa-geometrioihin)))

                 tasot/geometriat-kartalle
                 (fn [_ vanha uusi]
                   (geometriat-muuttuneet vanha uusi)))

   (fn []
     (let [koko (if-not (empty? @nav/tarvitsen-isoa-karttaa)
                  :L
                  @nav/kartan-koko)
           ;_ (js/console.log "kartta :: geometriat kartalle" (pr-str @tasot/geometriat-kartalle))
           ]

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

         :extent-key         (str (if (or (= :hidden koko) (= :S koko)) "piilossa" "auki") "_" (name @nav/valittu-sivu))
         :extent             @nav/kartan-extent

         :selection          nav/valittu-hallintayksikko
         :on-zoom            paivita-extent
         :on-drag            (fn [item event]
                               (paivita-extent item event)
                               (t/julkaise! {:aihe :karttaa-vedetty}))
         :on-postrender      (fn [_]
                               ;; Geometriatason pakottaminen valmiiksi postrenderissä
                               ;; tuntuu toimivan hyvin, mutta kuvatason pakottaminen ei.
                               ;; Postrender triggeröityy monta kertaa, kun kuvatasoja piirretään.
                               (edistymispalkki/geometriataso-pakota-valmistuminen!))
         :on-mount           (fn [initialextent]
                               (paivita-extent nil initialextent))
         :on-click           (fn [event]
                               ;; Click tarkoittaa tyhjän pisteen klikkaamista,
                               ;; eli esim valitun urakan "ulkopuolelle" klikkaamista.
                               (cond
                                 ;; Näissä näkymissä ei näytetä paneelia
                                 (#{:ilmoitukset :raportit} @nav/valittu-sivu)
                                 nil

                                 ;; Etusivulla urakkaa valittaessa ei haluta avata infopaneelia
                                 (and (#{:urakat} @nav/valittu-sivu)
                                      (not @nav/valittu-urakka))
                                 nil

                                 :default
                                 (kaynnista-infopaneeliin-haku-pisteesta! @tasot/geometriat-kartalle
                                                                          event
                                                                          asiat-pisteessa))
                               (.stopPropagation event)
                               (.preventDefault event))
         :on-select          kasittele-select!

         :on-dblclick        nil

         :on-dblclick-select kasittele-dblclick-select!

         :tooltip-fn         (fn [geom]
                                        ; Palauttaa funktion joka palauttaa tooltipin sisällön, tai nil jos hoverattu asia
                                        ; on valittu hallintayksikkö tai urakka.
                               (if (or (tapahtuman-geometria-on-valittu-hallintayksikko-tai-urakka? geom)
                                       (and (empty? (:nimi geom)) (empty? (:siltanimi geom))))
                                 nil
                                 (fn []
                                   (and geom
                                        [:div {:class (name (:type geom))} (or (:nimi geom) (:siltanimi geom))]))))

         :geometries         @tasot/geometriat-kartalle
         :layers             [{:type  :mml
                               :url   (str (k/wmts-polku-mml) "maasto/wmts")
                               :layer "taustakartta"
                               :default true}
                              {:type  :livi
                               :id :tienumerot
                               :nimi "tienumerot"
                               :icon (ikonit/numero-taulu-24 16 16)
                               :url   (str (k/wmts-polku-livi) "wmts")
                               :layer "liikennevirasto:PTP_HatkaPlus_Tienumerot"
                               :default true}
                              {:type :wms
                               :id :enc-merikartta
                               :nimi "ENC merikartta"
                               :icon  (ikonit/ankkuri-24 16 16)
                               :url "https://julkinen.vayla.fi/s57/wms?request=GetCapabilities&service=WMS"
                               :layer "cells"
                               :style "style-id-202"
                               :default false}]}]))))

(defn kartan-edistyminen [kuvataso geometriataso]
  (let [ladattu (+ (:ladattu kuvataso) (:ladattu geometriataso))
        ladataan (+ (:ladataan kuvataso) (:ladattu geometriataso))]
    (when (and @nav/kartta-nakyvissa? (pos? ladataan))
      [:div.kartta-progress {:style {:width (str (* 100.0 (/ ladattu ladataan)) "%")}}])))

(defn kartta []
  [:div.karttacontainer
   [paivitetaan-karttaa]
   [kartan-koko-kontrollit]
   [kartan-yleiset-kontrollit]
   [kartan-ohjelaatikko]
   (when @tiedot/infopaneeli-nakyvissa?
     [:div.kartan-infopaneeli
      [infopaneeli/infopaneeli @asiat-pisteessa tiedot/piilota-infopaneeli!
       tiedot/infopaneelin-linkkifunktiot]])
   (when-not @tiedot/infopaneeli-nakyvissa? ;; Peittää selitelaatikon, otetaan pois
     [kartan-ikonien-selitykset])
   [kartta-openlayers]
   [kartan-edistyminen @kuvatason-lataus @geometriatason-lataus]])
