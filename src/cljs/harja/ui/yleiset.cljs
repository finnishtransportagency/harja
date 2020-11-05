(ns harja.ui.yleiset
  "Yleisiä UI komponentteja"
  (:require [harja.loki :refer [log tarkkaile!]]
            [harja.ui.ikonit :as ikonit]
            [reagent.core :refer [atom] :as r]
            [harja.ui.komponentti :as komp]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [harja.ui.dom :as dom]
            [harja.fmt :as fmt]
            [clojure.string :as str]
            [harja.ui.modal :as modal]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :as loki])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]
                   [reagent.ratom :refer [reaction run!]]))

(def navigaation-min-korkeus 47)

(defn navigaation-korkeus []
  (Math/max
    navigaation-min-korkeus
    (some-> js/document
            (.getElementsByTagName "nav")
            (aget 0)
            .-clientHeight)))

(defn murupolun-korkeus []
  (some-> js/document
          (.getElementsByClassName "murupolku")
          (aget 0)
          .-clientHeight))

(defn ajax-loader
  "Näyttää latausanimaatiokuvan ja optionaalisen viestin."
  ([] (ajax-loader nil))
  ([viesti] (ajax-loader viesti nil))
  ([viesti {:keys [luokka sama-rivi?] :as opts}]
   [:div {:class (str "ajax-loader " (when (:luokka opts) (:luokka opts)))}
    [:img {:src "images/ajax-loader.gif"}]
    (when viesti
      (if sama-rivi?
        [:span.viesti (str " " viesti)]
        [:div.viesti viesti]))]))

(defn ajax-loader-pieni
  "Näyttää pienen inline latausanimaatiokuvan ja optionaalisen viestin."
  ([] (ajax-loader-pieni nil))
  ([viesti] (ajax-loader-pieni viesti nil))
  ([viesti opts]
   [:div {:class (str "ajax-loader inline-block " (when (:luokka opts) (:luokka opts)))
          :style (when-let [tyyli (:style opts)]
                   tyyli)}
    [:img {:src "images/ajax-loader.gif" :style {:height 16}}]
    (when viesti
      [:span.viesti (str " " viesti " ")])]))

(defn ajax-loader-pisteet
  "Näyttää latausanimaatiokuvan ja optionaalisen viestin."
  ([] (ajax-loader-pisteet nil))
  ([viesti]
   [:span.ajax-loader-pisteet
    [:img {:class "ajax-loader-pisteet" :src "images/ajax-loader-pisteet.gif"}]
    (when viesti
      [:div.viesti viesti])]))

(defn indeksi [kokoelma itemi]
  (first (keep-indexed #(when (= %2 itemi) %1) kokoelma)))

(defn nuolivalinta
  "Tekee handlerin, joka helpottaa nuolivalinnan tekemistä. Ottaa kolme funktiota: ylös, alas ja enter,
joita kutsutaan kun niiden näppäimiä paineetaan."
  [ylos alas enter]
  #(let [kc (.-keyCode %)]
     (when (or (= kc 38)
               (= kc 40)
               (= kc 13))
       (.preventDefault %)
       (case kc
         38                                                 ;; nuoli ylös
         (ylos)

         40                                                 ;; nuoli alas
         (alas)

         13                                                 ;; enter
         (enter)))))

(defn virheen-ohje
  "Virheen ohje. Tyyppi on :virhe (oletus jos ei annettu), :varoitus, tai :huomautus."
  ([virheet] (virheen-ohje virheet :virhe))
  ([virheet tyyppi] (virheen-ohje virheet tyyppi {}))
  ([virheet tyyppi {:keys [virheet-ulos? max-width]}]
   [:div {:class (case tyyppi
                   :varoitus "varoitukset"
                   :virhe "virheet"
                   :huomautus "huomautukset")}
    [:div (merge {:class (case tyyppi
                           :varoitus "varoitus"
                           :virhe "virhe"
                           :huomautus "huomautus")}
                 (when max-width
                   {:style {:max-width max-width}}))
     (doall (for* [v (distinct virheet)]

                  [:span (when virheet-ulos?
                           {:style {:display "block"}})
                   (case tyyppi
                     :huomautus (ikonit/livicon-info-circle)
                     (ikonit/livicon-warning-sign))
                   [:span (str " " v)]]))]]))


(defn linkki
  ([otsikko toiminto]
   (linkki otsikko toiminto {}))
  ([otsikko toiminto {:keys [style ikoni]}]
   [:a {:style    style
        :href     "#"
        :on-click #(do (.preventDefault %) (toiminto))}
    [:span
     (when ikoni ikoni)
     (if ikoni
       (str " " otsikko)
       otsikko)]]))

(defn staattinen-linkki-uuteen-ikkunaan [otsikko linkki]
  [:a {:href linkki :target "_blank"} otsikko])

(defn raksiboksi
  [{:keys [teksti toiminto info-teksti nayta-infoteksti? komponentti disabled?]} checked]
  (let [toiminto-fn (fn [e] (when-not disabled?
                              (do (.preventDefault e) (toiminto) nil)))]
    [:span.raksiboksi
     [:div.input-group
      [:div.input-group-addon
       [:input.klikattava {:type      "checkbox"
                           :checked   (if checked "checked" "")
                           :disabled  (when disabled? "disabled")
                           :on-change #(toiminto-fn %)}]
       [:span.raksiboksi-teksti {:class    (when-not disabled? "klikattava")
                                 :on-click #(toiminto-fn %)} teksti]]
      (when komponentti
        komponentti)]
     (when nayta-infoteksti?
       info-teksti)]))

(defn alasveto-ei-loydoksia [teksti]
  [:div.alasveto-ei-loydoksia teksti])

(defn virheviesti-sailio
  "Luo virheviestin 'sivun sisään'. Jos toinen parametri on jotain muuta kuin nil tai false,
  säiliön display asetetaan inline-blockiksi."
  ([viesti] (virheviesti-sailio viesti nil false))
  ([viesti rasti-funktio] (virheviesti-sailio viesti rasti-funktio false))
  ([viesti rasti-funktio inline-block?]
   (let [sulkemisnappi [:button.inlinenappi.nappi-kielteinen {:on-click #(rasti-funktio)}
                        [ikonit/remove] " Sulje"]]
     (if inline-block?
       [:div.virheviesti-sailio {:style {:display :inline-block}} viesti
        (when rasti-funktio sulkemisnappi)]
       [:div.virheviesti-sailio viesti
        (when rasti-funktio sulkemisnappi)]))))

(def valinta-ul-max-korkeus-px "420px")

(defn livi-pudotusvalikko
  "Vaihtoehdot annetaan yleensä vectorina, mutta voi olla myös map.
   format-fn:n avulla muodostetaan valitusta arvosta näytettävä teksti."
  [{:keys [klikattu-ulkopuolelle-params auki-fn! kiinni-fn!]} _]
  (let [auki? (atom false)
        lista-item (fn [li-luokka-fn itemit-komponentteja? format-fn valitse-fn vaihtoehto disabled-vaihtoehdot]
                     (let [disabled? (and disabled-vaihtoehdot
                                          (contains? disabled-vaihtoehdot vaihtoehto))]
                       [:li.harja-alasvetolistaitemi {:class (when li-luokka-fn (li-luokka-fn vaihtoehto))}
                        (cond
                          itemit-komponentteja? vaihtoehto
                          disabled? [:span.disabled (format-fn vaihtoehto)]
                          :else [linkki (format-fn vaihtoehto) #(do (valitse-fn vaihtoehto)
                                                                    (reset! auki? false)
                                                                    nil)])]))
        term (atom "")
        on-click-fn (fn [vaihtoehdot _]
                      (when-not (empty? vaihtoehdot)
                        (if (swap! auki? not)
                          (when auki-fn! (auki-fn!))
                          (when kiinni-fn! (kiinni-fn!)))
                        nil))
        on-key-down-fn (fn [{:keys [vaihtoehdot valinta valitse-fn format-fn]} event]
                         (let [kc (.-keyCode event)
                               vaihtoehdot (if (map? vaihtoehdot)
                                             (mapv (fn [avain]
                                                     (-> [avain (get vaihtoehdot avain)]))
                                                   (keys vaihtoehdot))
                                             vaihtoehdot)]
                           ;; keycode 9 on TAB, ei tehdä silloin mitään, jotta kenttien
                           ;; välillä liikkumista ei estetä
                           (when-not (= kc 9)
                             (.preventDefault event)
                             (.stopPropagation event)
                             (if (or (= kc 38)
                                     (= kc 40)
                                     (= kc 13))
                               (do
                                 (when-not (empty? vaihtoehdot)
                                   (let [nykyinen-valittu-idx (loop [i 0]
                                                                (if (= i (count vaihtoehdot))
                                                                  nil
                                                                  (if (= (nth vaihtoehdot i) valinta)
                                                                    i
                                                                    (recur (inc i)))))]
                                     (case kc
                                       38                   ;; nuoli ylös
                                       (if (or (nil? nykyinen-valittu-idx)
                                               (= 0 nykyinen-valittu-idx))
                                         (valitse-fn (nth vaihtoehdot (dec (count vaihtoehdot))))
                                         (valitse-fn (nth vaihtoehdot (dec nykyinen-valittu-idx))))

                                       40                   ;; nuoli alas
                                       (if (or (nil? nykyinen-valittu-idx)
                                               (= (dec (count vaihtoehdot)) nykyinen-valittu-idx))
                                         (valitse-fn (nth vaihtoehdot 0))
                                         (valitse-fn (nth vaihtoehdot (inc nykyinen-valittu-idx))))

                                       13                   ;; enter
                                       (reset! auki? false)))))

                               (do                          ;; Valitaan inputtia vastaava vaihtoehto
                                 (reset! term (char kc))
                                 (when-let [itemi (first (filter (fn [vaihtoehto]
                                                                   (= (.indexOf (.toLowerCase (str (format-fn vaihtoehto)))
                                                                                (.toLowerCase @term)) 0))
                                                                 vaihtoehdot))]
                                   (valitse-fn itemi)
                                   (reset! auki? false)))) nil)))
        alasvetolista (fn [{:keys [ryhmissa? nayta-ryhmat ryhman-otsikko ryhmitellyt-itemit
                                   li-luokka-fn itemit-komponentteja? format-fn valitse-fn
                                   vaihtoehdot disabled-vaihtoehdot vayla-tyyli? auki? skrollattava?]}]
                        [:ul (if vayla-tyyli?
                               {:style (merge {:display (if auki?
                                                          "block"
                                                          "none")}
                                              (when skrollattava?
                                                {:overflow "scroll"
                                                 :max-height valinta-ul-max-korkeus-px}))}
                               {:class "dropdown-menu livi-alasvetolista"
                                :style {:max-height valinta-ul-max-korkeus-px}})
                         (doall
                           (if ryhmissa?
                             (for [ryhma nayta-ryhmat]
                               ^{:key ryhma}
                               [:div.harja-alasvetolista-ryhma
                                [:div.harja-alasvetolista-ryhman-otsikko (ryhman-otsikko ryhma)]
                                (for [vaihtoehto (get ryhmitellyt-itemit ryhma)]
                                  ^{:key (hash vaihtoehto)}
                                  [lista-item (when li-luokka-fn (r/partial li-luokka-fn)) itemit-komponentteja? format-fn valitse-fn vaihtoehto disabled-vaihtoehdot])])
                             (for [vaihtoehto vaihtoehdot]
                               ^{:key (hash vaihtoehto)}
                               [lista-item (when li-luokka-fn (r/partial li-luokka-fn)) itemit-komponentteja? format-fn valitse-fn vaihtoehto disabled-vaihtoehdot])))])]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(when @auki?
                                     (reset! auki? false)
                                     (when kiinni-fn! (kiinni-fn!)))
                                  klikattu-ulkopuolelle-params)

      (fn [{:keys [valinta format-fn valitse-fn class disabled itemit-komponentteja? naytettava-arvo
                   on-focus title li-luokka-fn ryhmittely nayta-ryhmat ryhman-otsikko data-cy vayla-tyyli? virhe?] :as asetukset} vaihtoehdot]
        (let [format-fn (r/partial (or format-fn str))
              valitse-fn (r/partial (or valitse-fn (constantly nil)))
              ryhmitellyt-itemit (when ryhmittely
                                   (group-by ryhmittely vaihtoehdot))
              ryhmissa? (not (nil? ryhmitellyt-itemit))]
          [:div (merge
                  {:class (str (if vayla-tyyli?
                                 (str "select-" (if virhe? "error-" "") "default")
                                 "dropdown livi-alasveto")
                               (when class (str " " class))
                               (when @auki? " open"))}
                  (when data-cy
                    {:data-cy data-cy}))
           [:button.nappi-alasveto
            {:class       (when disabled "disabled")
             :type        "button"
             :disabled    (if disabled "disabled" "")
             :title       title
             :on-click    (partial on-click-fn vaihtoehdot)
             :on-focus    on-focus
             :on-key-down (partial on-key-down-fn
                                   {:vaihtoehdot vaihtoehdot
                                    :valinta     valinta
                                    :valitse-fn  valitse-fn
                                    :format-fn   format-fn})}
            [:div.valittu (or naytettava-arvo (format-fn valinta))]
            (if (and @auki? vayla-tyyli?)
              ^{:key :auki}
              [:span.livicon-chevron-up {:class (when disabled "disabled")}]
              ^{:key :kiinni}
              [:span.livicon-chevron-down {:class (when disabled "disabled")}])]
           [alasvetolista (merge (select-keys asetukset #{:nayta-ryhmat :ryhman-otsikko :li-luokka-fn :itemit-komponentteja?
                                                          :disabled-vaihtoehdot :vayla-tyyli? :skrollattava?})
                                 {:ryhmissa? ryhmissa? :ryhmitellyt-itemit ryhmitellyt-itemit
                                  :format-fn format-fn :valitse-fn valitse-fn :vaihtoehdot vaihtoehdot
                                  :auki?     @auki?})]])))))

(defn pudotusvalikko [otsikko optiot valinnat]
  [:div.label-ja-alasveto
   [:span.alasvedon-otsikko otsikko]
   [livi-pudotusvalikko optiot valinnat]])

(defn alasveto-toiminnolla
  [_ _]
  (let [auki? (r/atom false)]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! auki? false))
      (fn [toiminto {:keys [valittu valinnat valinta-fn formaatti-fn virhe? disabled]}]
        [:div {:class #{(str "select-" (if virhe?
                                         "error-"
                                         "") "default") (when @auki? "open")}}
         [:button.nappi-alasveto {:on-click #(swap! auki? not) :disabled disabled}
          [:div.valittu
           (or (formaatti-fn valittu) "Ei valittu")]
          [:div.livicon-chevron-down]]
         [:ul {:style {:display (if @auki?
                                  "block"
                                  "none")}}
          (for [v valinnat]
            ^{:key (gensym "alasveto-item-")}
            [:li.harja-alasvetolistaitemi
             {:on-click #(do
                           (swap! auki? not)
                           (valinta-fn v))}
             [:span (formaatti-fn v)]])
          [:li.harja-alasvetolistaitemi [toiminto {:sulje #(swap! auki? not)}]]]]))))


(defn kaksi-palstaa-otsikkoja-ja-arvoja
  "Tekee geneeriset kaksi palstaa. Optiossa voi olla :class, joka asetaan containerin lisäluokaksi."
  [{:keys [class]} & otsikot-ja-arvot]
  [:div.tietoja.container {:class class}
   (keep-indexed
     (fn [i [otsikko arvo]]
       (when arvo
         ^{:key (str i otsikko)}
         [:div.tietorivi.row
          [:div.col-md-4.tietokentta otsikko]
          [:div.col-md-8.tietoarvo arvo]]))
     (partition 2 otsikot-ja-arvot))])

(defn tietoja
  "Tekee geneerisen tietonäkymän.

   Optiot on mäppi, joka tukee seuraavia optioita:
  :class                        Asetetaan lisäluokaksi containerille
  :tietokentan-leveys           Tietokentän minimileveys (oletus 150px)
  :kavenna?                     Jos true, jättää tyhjää tilaa tietorivien väliin
  :jata-kaventamatta            Setti otsikoita, joita ei kavenneta, vaikka 'kavenna?' olisi true
  :otsikot-omalla-rivilla?      jos true, otsikot ovat blockeja (oletus false)
  :otsikot-samalla-rivilla      Setti otsikoita, jotka ovat samalla rivillä
  :tyhja-rivi-otsikon-jalkeen   Setti otsikoita, joiden jälkeen tyhjä rivi
  :piirra-viivat?               Piirtää viivat otsikoiden ja arvojen alle (oletus true)"
  [{:keys [class otsikot-omalla-rivilla? otsikot-samalla-rivilla piirra-viivat?
           tyhja-rivi-otsikon-jalkeen kavenna? jata-kaventamatta tietokentan-leveys]} & otsikot-ja-arvot]
  (let [tyhja-rivi-otsikon-jalkeen (or tyhja-rivi-otsikon-jalkeen #{})
        otsikot-samalla-rivilla (or otsikot-samalla-rivilla #{})
        jata-kaventamatta (or jata-kaventamatta #{})
        tietokentta-attrs {:style
                           (merge
                             (when otsikot-omalla-rivilla?
                               {:display "block"})
                             (when tietokentan-leveys
                               {:min-width tietokentan-leveys}))}]
    [:div.tietoja {:class class}
     (keep-indexed
       (fn [i [otsikko arvo]]
         (when arvo
           (let [rivin-attribuutit (when (otsikot-samalla-rivilla otsikko)
                                     {:style {:display "auto"}})]
             ^{:key (str i otsikko)}
             [:div.tietorivi (merge
                               (when-not piirra-viivat?
                                 {:class "tietorivi-ilman-alaviivaa"})
                               (when (and kavenna?
                                          (not (jata-kaventamatta otsikko)))
                                 {:style {:margin-bottom "0.5em"}}))
              [:span.tietokentta (merge tietokentta-attrs rivin-attribuutit) otsikko]
              [:span.tietoarvo arvo]
              (when (tyhja-rivi-otsikon-jalkeen otsikko)
                [:span [:br] [:br]])])))
       (partition 2 otsikot-ja-arvot))]))

(defn taulukkotietonakyma
  "Tekee geneerisen taulukko-tietonäkymän. Optioihin style tai table-style."
  [{:keys [style table-style]} & otsikot-ja-arvot]
  [:div.taulukko-tietonakyma {:style style}
   [:table {:style table-style}
    [:tbody
     (for [[otsikko arvo] (partition 2 otsikot-ja-arvot)
           :when arvo]
       ^{:key otsikko}
       [:tr
        [:td.taulukko-tietonakyma-tietokentta [:span otsikko]]
        [:td.taulukko-tietonakyma-tietoarvo [:span arvo]]])]]])

(defn kuvaus-ja-avainarvopareja
  [kuvaus & avaimet-ja-arvot]
  [:div
   [:div.kuvaus kuvaus]
   (vec (concat [tietoja {}] avaimet-ja-arvot))])


;; Yleinen tietopaneeleissa käytettävä tietueen koko.
;; Suurella näytöllä, 4 elementtiä vierekkäin, pienimmällä vain 1 per rivi.
(def tietopaneelin-elementtikoko {:lg 3 :md 4 :sm 6 :xs 12})

(defn rivi
  "Tekee bootstrap .row divin, jossa jokaisella komponentilla on sama koko.
Jos annettu koko on numero, tulee luokaksi col-lg-<koko>, jos koko on mäppi {:lg <iso koko> :md <medium koko> ...}
lisätään eri kokoluokka jokaiselle mäpissä mainitulle koolle."
  [{:keys [koko luokka]} & komponentit]
  (let [cls (if-not (map? koko)
              koko
              (apply str (map (fn [[koko-luokka koko]]
                                (str "col-" (name koko-luokka) "-" koko " "))
                              (seq koko))))]
    [:div.row
     (map-indexed
       (fn [i komponentti]
         ^{:key i}
         [:div {:class (str cls luokka)}
          komponentti])
       (keep identity komponentit))]))

(defn otsikolla
  "Käärii annetun komponentin <span> elementiin, ja lisää <h4> otsikon ennen sitä."
  [otsikko komp]
  [:span [:h4 otsikko]
   komp])

(defn keskita
  "Div-elementti, joka on absoluuttisesti positioitu top 50% left 50%"
  [& sisalto]
  [:div {:style {:position "absolute" :top "50%" :left "50%"}}
   sisalto])

(def +korostuksen-kesto+ 4000)

(defn taulukko2
  "Otsikoille ja arvoille annettavien luokkien tulee noudattaa Bootstrap-sarakkeille annettavien luokkien kaavaa."
  [otsikko-class arvo-class otsikko-max-pituus arvo-max-pituus & otsikot-ja-arvot]
  [:span
   (keep-indexed (fn [i [otsikko arvo]]
                   (and otsikko arvo
                        ^{:key i}
                        [:div.row
                         [:div {:class otsikko-class :style {:max-width otsikko-max-pituus}} otsikko]
                         [:div {:class arvo-class :style {:max-width arvo-max-pituus}} arvo]]))
                 (partition 2 otsikot-ja-arvot))])

(defn- luo-haitarin-rivi [piiloita? rivi]
  ^{:key (:otsikko @rivi)}
  [:div.haitari-rivi
   [:div.haitari-heading.klikattava
    {:on-click #(do
                  (swap! rivi assoc :auki (not (:auki @rivi)))
                  (.preventDefault %))}
    [:span.haitarin-tila (if (:auki @rivi) (ikonit/livicon-chevron-down) (ikonit/livicon-chevron-right))]
    [:div.haitari-title (when piiloita? {:class "haitari-piilossa"}) (or (:otsikko @rivi) "")]]
   [:div.haitari-sisalto (if (:auki @rivi) {:class "haitari-auki"} {:class "haitari-kiinni"}) (:sisalto @rivi)]])

(defn- pakota-haitarin-rivi-auki
  ([rivit] (pakota-haitarin-rivi-auki rivit (first (keys @rivit))))
  ([rivit avattavan-avain]
   (swap! rivit assoc-in [avattavan-avain :auki] true)))

(defn laske-haitarin-paikka [asetus]
  (cond
    (true? asetus) "185px"
    (number? asetus) (str asetus "px")
    (string? asetus) asetus))

(defn haitari-paneelit
  "Näyttää haitarin, jossa paneelit määritellään antamalla otsikko, avain ja komponentti.
  Ensimmäisenä paramametrina on optiot mäp, jossa seuraavat arvot:

  :toggle-osio!  funktio, jota kutsutaan paneelin avaimella, kun paneeli halutaan auki/kiinni

  :auki          avain -> truthy funktio, joka kertoo onko paneeli auki (esim #{} avaimia)

  :luokka        lisäluokka joka annetaan haitarikomponentin päätasolle

  :leijuva?      jos true, haitari leijuu parent komponentin päällä

  :otsikko       mahdollinen otsikko koko haitarille"

  [{:keys [toggle-osio! auki luokka leijuva? otsikko]} & otsikko-avain-ja-komponentti]
  [:div.harja-haitari (when luokka {:class luokka})
   [:div.haitari
    (doall
      (for [[otsikko avain komponentti] (partition 3 otsikko-avain-ja-komponentti)
            :let [auki? (auki avain)]]
        ^{:key (str avain)}
        [:div.haitari-rivi
         [:div.haitari-heading.klikattava
          {:on-click #(do
                        (toggle-osio! avain)
                        (.preventDefault %))}
          [:span.haitarin-tila
           (if auki?
             (ikonit/livicon-chevron-down)
             (ikonit/livicon-chevron-right))]
          [:div.haitari-title
           (when-not auki? {:class "haitari-piilossa"})
           otsikko]]
         [:div.haitari-sisalto
          {:class (if auki? "haitari-auki" "haitari-kiinni")}
          komponentti]]))]])

(defn haitari
  ([rivit] (haitari rivit {}))
  ([rivit {:keys [vain-yksi-auki? otsikko aina-joku-auki? piiloita-kun-kiinni? leijuva? luokka]}]
   (let [piilota? (and piiloita-kun-kiinni? (not (some (fn [[_ r]] (:auki r)) @rivit)))]
     (when aina-joku-auki?
       (when-not (some (fn [[_ r]] (:auki r)) @rivit)
         (pakota-haitarin-rivi-auki rivit)))
     [:div {:class (str "harja-haitari " (when luokka luokka))}
      (when leijuva? {:class "leijuva" :style {:top (laske-haitarin-paikka leijuva?)}})
      (when otsikko [:div.haitari-otsikko (if (string? otsikko)
                                            otsikko
                                            [otsikko])])
      [:div.haitari
       (for [[avain rivi] @rivit]
         (luo-haitarin-rivi
           piilota?
           (r/wrap
             rivi
             (fn [uusi]
               (swap! rivit assoc avain uusi)
               ;; Jos vain yksi voi olla auki ja tämä rivi aukaistiin, sulje muut.
               (when (and (:auki uusi) vain-yksi-auki?)
                 (reset! rivit (into {} (map
                                          (fn [[a r]]
                                            (if-not (= avain a)
                                              [a (assoc r :auki false)]
                                              [a r]))
                                          @rivit))))

               ;; Jos rivi suljettiin, ja jonkun pitää olla auki, ja yksikään ei ole auki,
               ;; niin älä sulje riviä.
               (when (and (not (:auki uusi)) aina-joku-auki?)
                 (when-not (some (fn [[_ r]] (:auki r)) @rivit)
                   (swap! rivit assoc-in [avain :auki] true)))))))]])))

(defn pudotuspaneeli
  ([sisalto] (pudotuspaneeli sisalto {}))
  ([sisalto opts]
   (let [piilota? (or (:piilota-kun-kiinni? opts) false)
         rivi (atom (assoc opts :sisalto sisalto
                                :auki (or (:auki opts) false)))]
     (fn [sisalto opts]
       [:div.harja-haitari [:div.haitari (luo-haitarin-rivi piilota? rivi)]]))))

(def +valitse-kuukausi+
  "- Valitse kuukausi -")

(def +valitse-indeksi+
  "- Valitse indeksi -")

(def +ei-sidota-indeksiin+
  "Ei sidota indeksiin")

(defn vihje
  ([teksti] (vihje teksti nil))
  ([teksti luokka]
   [:div {:class
          (str "yleinen-pikkuvihje " (or luokka ""))}
    [:div.vihjeen-sisalto
     (ikonit/ikoni-ja-teksti (harja.ui.ikonit/livicon-info-sign) teksti)]]))

(defn vihje-elementti
  ([elementti] (vihje-elementti elementti nil))
  ([elementti luokka]
   [:div {:class
          (str "yleinen-pikkuvihje " (or luokka ""))}
    [:div.vihjeen-sisalto
     (ikonit/ikoni-ja-elementti (harja.ui.ikonit/livicon-info-sign) elementti)]]))

(def +tehtavien-hinta-vaihtoehtoinen+ "Urakan tehtävillä voi olla joko yksikköhinta tai muutoshinta")

(defn pitka-teksti
  "Näyttää pitkän tekstin, josta näytetään oletuksena vain ensimmäinen rivi. Käyttäjä voi näyttää/piilottaa
jatkon."
  ([teksti] (pitka-teksti teksti true))
  ([teksti piilotettu?]
   (let [piilossa? (atom piilotettu?)]
     (fn [teksti _]
       (if-not teksti
         [:span]
         [:span.pitka-teksti
          (if @piilossa?
            [:span.piilossa
             (.substring teksti 0 80)
             (when (> (count teksti) 80)
               [:a.nayta-tai-piilota {:href "#" :on-click #(do (.preventDefault %)
                                                               (swap! piilossa? not))}
                "Lisää..."])]
            [:span.naytetaan
             teksti
             [:a.nayta-tai-piilota {:href "#" :on-click #(do (.preventDefault %)
                                                             (swap! piilossa? not))}
              "Piilota"]])])))))

(defn tasaus-luokka
  "Palauttaa CSS-luokan, jolla teksti tasataan.
  Mahdolliset arvot ovat :oikea (teksti tasataan oikealle) tai
  :keskita (teksti keskitetään)."
  [tasaus]
  (when tasaus
    (case tasaus
      :oikea "tasaa-oikealle"
      :keskita "tasaa-keskita")))

(defn luokat
  "Yhdistää monta luokkaa yhdeksi class attribuutiksi. Poistaa nil arvot ja yhdistää
  loput arvot välilyönnillä. Jos kaikki arvot ovat nil, palauttaa nil."
  [& luokat]
  (let [luokat (remove nil? luokat)]
    (when-not (empty? luokat)
      (str/join " " luokat))))

(defn- tooltip-sisalto [auki? sisalto]
  (let [x (atom nil)]
    (komp/luo
      (komp/piirretty
        #(let [n (r/dom-node %)
               rect (aget (.getClientRects n) 0)
               parent-rect (aget (.getClientRects (.-parentNode n)) 0)]
           (reset! x
                   (+ (/ (.-width rect) -2)
                      (/ (.-width parent-rect) 2)))))
      (fn [auki? sisalto]
        [:div.tooltip.bottom {:class (when auki? "in")
                              :style {:position  "absolute"
                                      :min-width 150
                                      :left      (when-let [x @x]
                                                   x)}}
         [:div.tooltip-arrow]
         [:div.tooltip-inner
          sisalto]]))))

(defn tooltip [opts komponentti tooltipin-sisalto]
  (let [tooltip-nakyy? (atom false)
        leveys (atom 0)]
    (komp/luo
      (fn [opts komponentti tooltipin-sisalto]
        [:div.inline-block
         {:style          {:position "relative"}            ;:div.inline-block
          :on-mouse-enter #(reset! tooltip-nakyy? true)
          :on-mouse-leave #(reset! tooltip-nakyy? false)}
         komponentti

         [tooltip-sisalto @tooltip-nakyy? tooltipin-sisalto]]))))

(defn wrap-if
  "If condition is truthy, return container-component with
  the containee placed inside it otherwise return containee.
  Container-component must be a vector that has a value :%
  in it. The :% value is replaced with the containee."
  [condition container-component containee]
  (if condition
    (mapv #(if (= :% %)
             containee
             %) container-component)
    containee))

(defn totuus-ikoni [arvo]
  (when arvo
    (ikonit/livicon-check)))

(defn tallenna-excel-nappi
  [url]
  [(ikonit/save) "Tallenna Excel" "raporttixls" url])

(defn tallenna-pdf-nappi
  [url]
  [(ikonit/print) "Tallenna PDF" "raporttipdf" url])

(def ^{:doc "Mahdolliset raportin vientimuodot"}
+raportin-vientimuodot+
  [(tallenna-excel-nappi (k/excel-url :raportointi))
   (tallenna-pdf-nappi (k/pdf-url :raportointi))])

(defn sahkopostiosoitteet-str->set [osoitteet]
  (into #{}
        (keep not-empty
              (map
                #(str/trim %)
                (str/split osoitteet #",")))))

(defn str-suluissa-opt [s]
  (when s
    (str " (" s ")")))

(defn- tooltip-kentta [avain arvo]
  [:div {:style {:margin-bottom "8px"}}
   [:div.tooltip-otsikko avain]
   [:span arvo]])

(defn avain-arvo-tooltip [otsikko {:keys [container-style]}
                          & avaimet-ja-arvot]
  [:div {:style container-style}
   [:div.bold {:style {:margin-bottom "8px"}} otsikko]
   (keep-indexed (fn [i [avain arvo]]
                   ^{:key i}
                   [tooltip-kentta avain arvo])
                 (partition 2 avaimet-ja-arvot))])