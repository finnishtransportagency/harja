(ns harja.ui.yleiset
  "Yleisiä UI komponentteja"
  (:require [harja.loki :refer [log tarkkaile!]]
            [harja.ui.ikonit :as ikonit]
            [reagent.core :refer [atom] :as r]
            [reagent.ratom :as ratom]
            [harja.ui.komponentti :as komp]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [harja.ui.dom :as dom]
            [harja.fmt :as fmt]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :as loki]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]
                   [reagent.ratom :refer [reaction run!]]))

(defn placeholder
  "Näyttää helposti huomattavan placeholder elementin, jota voi käyttää sommitteluun"
  ([teksti]
   (placeholder teksti {}))
  ([teksti {:keys [placeholderin-optiot]}]
   [:div (merge {:style {:background-color "hotpink"
                         :color "white"}}
                placeholderin-optiot)
    [ikonit/exclamation-sign] teksti]))

(def navigaation-min-korkeus 47)

(defn navigaation-korkeus []
  (Math/max
    navigaation-min-korkeus
    (some-> js/document
            (.getElementsByTagName "nav")
            (aget 0)
            .-clientHeight)))

(defn luokat
  "Yhdistää monta luokkaa yhdeksi class attribuutiksi. Poistaa nil arvot ja yhdistää
  loput arvot välilyönnillä. Jos kaikki arvot ovat nil, palauttaa nil."
  [& luokat]
  (let [luokat (remove nil? luokat)]
    (when-not (empty? luokat)
      (str/join " " luokat))))

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

(defn himmennys
  "Annetun elementin päälle piirrettävä himmentävä div, joka estää klikkaamisen.
  Himmennys-divin keskelle voidaan piirtää annettu sisältö, esim. ajax-loader."
  [{:keys [himmenna? luokka himmennyksen-sisalto]} himmennettava-elementti]
  [:div.himmennys-wrapper
   [:div.himmennys {:class (luokat (when (false? himmenna?) "hidden") luokka)}
    himmennyksen-sisalto]
   himmennettava-elementti])

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
                   #_(case tyyppi
                     :huomautus (ikonit/livicon-info-circle)
                     (ikonit/livicon-warning-sign))
                   (str " " v)]))]]))


(defn linkki
  ([otsikko toiminto]
   (linkki otsikko toiminto {}))
  ([otsikko toiminto {:keys [disabloitu? style ikoni stop-propagation block? luokka]}]
   (let [sisalto [:span
                  (when ikoni ikoni)
                  (if ikoni
                    (str " " otsikko)
                    otsikko)]]
     (if disabloitu?
       [:span.disabloitu-linkki
        {:style (merge {:cursor "not-allowed"}
                       (when block? {:display "block"})
                       style)}
        sisalto]
       [:a {:style    (if block? (merge style {:display "block"}) style)
            :href     "#"
            :class    luokka
            :on-click #(do (when stop-propagation (.stopPropagation %)) (.preventDefault %) (toiminto))}
        sisalto]))))

(defn staattinen-linkki-uuteen-ikkunaan [otsikko linkki]
  [:a {:href linkki
       :target "_blank"
       :rel "noopener noreferrer"} otsikko])

(defn tiedoston-lataus-linkki
  "Tarkoitettu esimerkiksi erillisen esxel tiedoston lataamiseen. Käyttää html5 speksin linkin download atriboottia.
  Jos atriboottia download ei täytetä, palautetaan urlissa annetun tiedoston nimi. Downloadiin voi siis tarvittaessa
  joskus lisätä tiedoston lopullinen nimi."
  ([otsikko url]
   (tiedoston-lataus-linkki otsikko url nil))
  ([otsikko url {:keys [luokat] :as _opts}]
   [:a {:class (concat ["nappi-reunaton"] luokat)
        :href url
        :download ""}
    [ikonit/ikoni-ja-teksti (ikonit/livicon-download) otsikko]]))

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

(defn linkki-jossa-valittu-checked
  [otsikko toiminto valittu?]
  [:span
   [:a.inline-block {:href "#"
                     :on-click #(do (.preventDefault %) (toiminto))}
    otsikko]
    (when valittu?
      [:span.listan-arvo-valittu
       (ikonit/ok)])])

(defn lista-item
  [{:keys [li-luokka-fn itemit-komponentteja? format-fn valitse-fn
           vaihtoehto disabled-vaihtoehdot valittu-arvo vayla-tyyli? auki?] :as kaka}]
  (let [disabled? (and disabled-vaihtoehdot
                       (contains? disabled-vaihtoehdot vaihtoehto))
        linkin-cond (cond
                      itemit-komponentteja? vaihtoehto
                      disabled? [:span.disabled (format-fn vaihtoehto)]
                      :else (let [teksti (format-fn vaihtoehto)
                                  toiminto #(do (valitse-fn vaihtoehto)
                                                (reset! auki? false)
                                                nil)]
                              (if vayla-tyyli?
                                [linkki teksti toiminto]
                                [linkki-jossa-valittu-checked
                                 teksti toiminto
                                 (= valittu-arvo vaihtoehto)])))]
    [:li.harja-alasvetolistaitemi {:class (when li-luokka-fn (li-luokka-fn vaihtoehto))}
     (if-not vayla-tyyli?
       linkin-cond
       [:span
        linkin-cond
        (when (= valittu-arvo vaihtoehto)
          [:span.listan-arvo-valittu (ikonit/ok)])])]))

(defn alasvetolista
  [{:keys [ryhmissa? nayta-ryhmat ryhman-otsikko ryhmitellyt-itemit
           li-luokka-fn itemit-komponentteja? format-fn valitse-fn
           vaihtoehdot disabled-vaihtoehdot vayla-tyyli? auki? skrollattava? valittu-arvo
           pakollinen?] :as kaka}]
  [:ul (if vayla-tyyli?
         {:class "dropdown-menu livi-alasvetolista"
          :style (merge {:padding-top "4px"
                         :z-index "1000"
                         :position :absolute
                         :display (if @auki?
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
         [:div.haku-lista-ryhma
          [:div.haku-lista-ryhman-otsikko (ryhman-otsikko ryhma)]
          (for [vaihtoehto (get ryhmitellyt-itemit ryhma)]
            ^{:key (hash vaihtoehto)}
            [lista-item {:li-luokka-fn (when li-luokka-fn (r/partial li-luokka-fn)) :itemit-komponentteja? itemit-komponentteja? :format-fn format-fn :valitse-fn valitse-fn
                         :vaihtoehto vaihtoehto :disabled-vaihtoehdot disabled-vaihtoehdot :valittu-arvo valittu-arvo :vayla-tyyli? vayla-tyyli? :auki? auki?}])])
       (for [vaihtoehto vaihtoehdot]
         ^{:key (hash vaihtoehto)}
         [lista-item {:li-luokka-fn (when li-luokka-fn (r/partial li-luokka-fn)) :itemit-komponentteja? itemit-komponentteja? :format-fn format-fn :valitse-fn valitse-fn
                      :vaihtoehto vaihtoehto :disabled-vaihtoehdot disabled-vaihtoehdot :valittu-arvo valittu-arvo :vayla-tyyli? vayla-tyyli? :auki? auki?}])))])

(defn livi-pudotusvalikko
  "Vaihtoehdot annetaan yleensä vectorina, mutta voi olla myös map.
   format-fn:n avulla muodostetaan valitusta arvosta näytettävä teksti."
  [{:keys [auki-fn! kiinni-fn! vayla-tyyli? elementin-id]} _]
  (let [elementin-id (or elementin-id (str (gensym "livi-pudotusvalikko")))
        auki? (atom false)
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
                                       (reset! auki? (not @auki?))))))

                               (do                          ;; Valitaan inputtia vastaava vaihtoehto
                                 (reset! term (char kc))
                                 (when-let [itemi (first (filter (fn [vaihtoehto]
                                                                   (= (.indexOf (.toLowerCase (str (format-fn vaihtoehto)))
                                                                                (.toLowerCase @term)) 0))
                                                                 vaihtoehdot))]
                                   (valitse-fn itemi)
                                   (reset! auki? false)))) nil)))]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(when @auki?
                                     (reset! auki? false)
                                     (when kiinni-fn! (kiinni-fn!)))
                                  {:tarkista-komponentti? true})

      (fn [{:keys [valinta format-fn valitse-fn class disabled itemit-komponentteja? naytettava-arvo
                   on-focus title li-luokka-fn ryhmittely nayta-ryhmat ryhman-otsikko data-cy vayla-tyyli? virhe?
                   pakollinen? tarkenne muokattu?] :as asetukset} vaihtoehdot]
        (let [format-fn (r/partial (or format-fn str))
              valitse-fn (r/partial (or valitse-fn (constantly nil)))
              ryhmitellyt-itemit (when ryhmittely
                                   (group-by ryhmittely vaihtoehdot))
              ryhmissa? (not (nil? ryhmitellyt-itemit))]
          [:div (merge
                  {:class (str (if vayla-tyyli?
                                 (str "select-" (if (and muokattu? virhe?) "error-" "") "default")
                                 "dropdown livi-alasveto")
                               (when class (str " " class))
                               (when @auki? " open"))}
                  (when data-cy
                    {:data-cy data-cy}))
           [:button
            {:id (str "btn-" (or elementin-id "") "-" (hash vaihtoehdot) (hash naytettava-arvo) (hash title))
             :class (str (when disabled "disabled ") (when-not vayla-tyyli? "nappi-alasveto "))
             :type "button"
             :disabled (if disabled "disabled" "")
             :title title
             :on-click (partial on-click-fn vaihtoehdot)
             :on-focus on-focus
             :on-key-down (partial on-key-down-fn
                                   {:vaihtoehdot vaihtoehdot
                                    :valinta valinta
                                    :valitse-fn valitse-fn
                                    :format-fn format-fn})}
            [:div.valittu.overflow-ellipsis (or naytettava-arvo (format-fn valinta))]
            (if @auki?
              ^{:key :auki}
              [:span.livicon-chevron-up {:id (str "chevron-up-btn-" (or elementin-id "") "-" (hash vaihtoehdot))
                                         :class (when disabled "disabled")}]
              ^{:key :kiinni}
              [:span.livicon-chevron-down {:id (str "chevron-up-btn-" (or elementin-id "") "-" (hash vaihtoehdot))
                                           :class (when disabled "disabled")}])]
           ;; tarkenne voi olla Hiccup-komponentti, esim. [:span.minun-tarkenne minun-arvo]
           (when tarkenne
             [tarkenne valinta])
           [alasvetolista (merge (select-keys asetukset #{:nayta-ryhmat :ryhman-otsikko :li-luokka-fn :itemit-komponentteja?
                                                          :disabled-vaihtoehdot :vayla-tyyli? :skrollattava?})
                                 {:ryhmissa? ryhmissa? :ryhmitellyt-itemit ryhmitellyt-itemit
                                  :format-fn format-fn :valitse-fn valitse-fn :vaihtoehdot vaihtoehdot
                                  :pakollinen? pakollinen?
                                  :valittu-arvo valinta
                                  :auki? auki?})]])))))

(defn pudotusvalikko [otsikko optiot valinnat]
  [:div {:class (or (:wrap-luokka optiot) "label-ja-alasveto")}
   (if (:vayla-tyyli? optiot)
     [:label.alasvedon-otsikko-vayla otsikko]
     [:label.alasvedon-otsikko otsikko])
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
  :piirra-viivat?               Piirtää viivat otsikoiden ja arvojen alle (oletus true)
  :tietorivi-luokka             Aseta lisäluokka tietoriville"
  [{:keys [class otsikot-omalla-rivilla? otsikot-samalla-rivilla piirra-viivat?
           tyhja-rivi-otsikon-jalkeen kavenna? jata-kaventamatta tietokentan-leveys tietorivi-luokka]} & otsikot-ja-arvot]
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
                                   {:class tietorivi-luokka}
                                   (when-not piirra-viivat?
                                     {:class (str tietorivi-luokka " tietorivi-ilman-alaviivaa")})
                                   (when (and kavenna?
                                           (not (jata-kaventamatta otsikko)))
                                     {:style {:margin-bottom "0.5em"}}))
              [:span.tietokentta (merge tietokentta-attrs rivin-attribuutit) otsikko]
              [:span.tietoarvo.max-width-3 arvo]
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
  (let [avaa-tai-sulje-haitari (fn [event]
                         (.preventDefault event)
                         (swap! rivi assoc :auki (not (:auki @rivi))))]
    ^{:key (:otsikko @rivi)}
    [:div.haitari-rivi
     [:div.haitari-heading.klikattava
      {:on-click #(avaa-tai-sulje-haitari %)
       :on-key-down #(when (dom/enter-nappain? %)
                       (avaa-tai-sulje-haitari %))}
      [:span.haitarin-tila {:tabIndex "0"} (if (:auki @rivi) (ikonit/livicon-chevron-down) (ikonit/livicon-chevron-right))]
      [:div.haitari-title (when piiloita? {:class "haitari-piilossa"}) (or (:otsikko @rivi) "")]]
     [:div.haitari-sisalto (if (:auki @rivi) {:class "haitari-auki"} {:class "haitari-kiinni"}) (:sisalto @rivi)]]))

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
            :let [auki? (auki avain)
                  avaa-tai-sulje-haitari (fn [event]
                                 (do
                                   (.preventDefault event)
                                   (toggle-osio! avain)))]]
        ^{:key (str avain)}
        [:div.haitari-rivi
         [:div.haitari-heading.klikattava
          {:on-click #(avaa-tai-sulje-haitari %)
           :on-key-down #(when (dom/enter-nappain? %)
                           (avaa-tai-sulje-haitari %))}
          [:span.haitarin-tila {:tabIndex "0"}
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

(def +vari-lemon-dark+ "#654D00")
(def +vari-black-light+ "#5C5C5C")
(def +vari-blue-dark+ "#004D99FF")

(defn vihje
  ([teksti] (vihje teksti nil 24))
  ([teksti luokka] (vihje teksti luokka 24))
  ([teksti luokka ikonin-koko]
   [:div {:class
          (str "yleinen-pikkuvihje " (or luokka ""))}
    [:div.vihjeen-sisalto
     [:div.vihjeikoni (ikonit/nelio-info ikonin-koko)]
     [:div.vihjeteksti teksti]]]))

(defn toast-viesti
  "Näyttää toast-viestin. Teksti voi olla Reagent-komponentti tai string"
  ([teksti] (toast-viesti teksti nil))
  ([teksti luokka]
   (let [ikoni-fn (if (vector? teksti)
                    ikonit/ikoni-ja-elementti
                    ikonit/ikoni-ja-teksti)
         ikoni (if (= "varoitus" luokka)
                 (ikonit/livicon-warning-sign)
                 (ikonit/status-info-inline-svg +vari-lemon-dark+))]
     [:div {:class
            (luokat
              "yleinen-pikkuvihje"
              "inline-block"
              (viesti/+toast-viesti-luokat+ (if (= "varoitus" luokka)
                                              :varoitus
                                              :neutraali))
              (or luokka ""))}
      [:div.vihjeen-sisalto
       (ikoni-fn ikoni teksti)]])))

(def tietyoilmoitus-siirtynyt-txt
  [:div.inline-block.tietyo-info
   "Tietyöilmoituksen tekeminen on siirtynyt Harjasta Fintrafficin puolelle. Voit tehdä sen "
   [staattinen-linkki-uuteen-ikkunaan "tämän linkin kautta."
    "https://tietyoilmoitus.tieliikennekeskus.fi/#/"]])

(defn tietyoilmoitus-siirtynyt-toast []
  [:div.tietyoilmoitus-toast
   [toast-viesti tietyoilmoitus-siirtynyt-txt]])

(defn vihje-elementti
  ([elementti] (vihje-elementti elementti nil 24))
  ([elementti luokka] (vihje-elementti elementti nil 24))
  ([elementti luokka ikonin-koko]
   [:div {:class
          (str "yleinen-pikkuvihje " (or luokka ""))}
    [:div.vihjeen-sisalto
     [:div.vihjeikoni (ikonit/nelio-info ikonin-koko)]
     [:div.vihjeteksti elementti]]]))

(defn keltainen-vihjelaatikko
  ([e]
   (keltainen-vihjelaatikko e nil nil))
  ([e t]
   (if (keyword? t)
     (keltainen-vihjelaatikko e nil t)
     (keltainen-vihjelaatikko e t nil)))
  ([ensisijainen-viesti toissijainen-viesti tyyppi]
   [:div
   [:div.toast-viesti.neutraali
    [:div {:style {:font-size "24px"}} (case tyyppi 
                                         :info (ikonit/nelio-info)
                                         (harja.ui.ikonit/livicon-warning-sign))]
    [:div {:style {:padding-left "10px"}} ensisijainen-viesti]
    (when toissijainen-viesti 
      [:div {:style {:padding-left "20px" :font-weight 400}} toissijainen-viesti])]]))

;; Jos haluat tehdä Toastin näköisen ilmoitustyyppisen varoitusviestin käyttäjälle.
;; Käytä tätä. Tämä on hyvin samantyyppinen kuin "vihje" funktio, mutta sisältää eri ikonin ja mahdollistaa sekundäärisen viestin.
;; Tämä tekee ikonillisen tekstikentän, jolle voi antaa sekundäärisen viestin samalle riville.
(defn varoitus-vihje [ensisijainen-viesti toissijainen-viesti]
  (keltainen-vihjelaatikko ensisijainen-viesti toissijainen-viesti))

(defn info-laatikko
  ([tyyppi ensisijainen-viesti]
   (info-laatikko tyyppi ensisijainen-viesti nil nil {}))
  ([tyyppi ensisijainen-viesti toissijainen-viesti leveys]
   (info-laatikko tyyppi ensisijainen-viesti toissijainen-viesti leveys {}))
  ([tyyppi ensisijainen-viesti toissijainen-viesti leveys {:keys [luokka]}]
   (assert (#{:varoitus :onnistunut :neutraali :vahva-ilmoitus} tyyppi)
     "Laatikon tyypin oltava varoitus, onnistunut, neutraali tai vahva-ilmoitus")
   [:div {:class (vec (keep identity ["info-laatikko" (name tyyppi) luokka]))
          :style {:width leveys :white-space "pre-line"}}
    [:div.infolaatikon-ikoni
     (case tyyppi
       :varoitus (ikonit/livicon-warning-sign)
       :onnistunut (ikonit/livicon-check)
       :vahva-ilmoitus (ikonit/status-info-inline-svg +vari-black-light+)
       :neutraali (ikonit/status-info-inline-svg +vari-black-light+))]
    [:div {:style {:width "95%" :padding-top "16px" :padding-bottom "16px"}}
     [:div {:style {:padding-left "8px" :white-space "pre-line"}}
      ensisijainen-viesti]
     (when toissijainen-viesti
       [:div {:style {:padding-left "8px" :font-weight 400}}
        toissijainen-viesti])]]))

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
      :vasen "tasaa-vasemmalle"
      :keskita "tasaa-keskita")))

(defn- tooltip-sisalto [opts auki? sisalto]
  (let [x (atom nil)
        y (atom nil)
        suunta (:suunta opts)
        ;; Custom offset. Sopii tilanteisiin, joissa clientRectiä ei voi laskea tooltip-wrapperille (elementti on piirtohetkellä esim. display: none)
        wrapperin-koko (:wrapperin-koko opts)]
    (komp/luo
      (komp/piirretty
        #(let [n (r/dom-node %)
               parent-rect (aget (.getClientRects (.-parentNode n)) 0)
               width (if (map? wrapperin-koko)
                       (:leveys wrapperin-koko)
                       (some-> parent-rect (.-width)))
               height (if (map? wrapperin-koko)
                        (:korkeus wrapperin-koko)
                        (some-> parent-rect (.-height)))
               ;; Asettaa hiukan ilmaa tooltipin nuolen ja tooltipin kohteen välille.
               lisaa-valia-px 3]

           ;; Hienosäädä tooltipin lopullinen offset riippuen siitä kuinka iso komponentti tooltip wrapperin sisälle on laitettu.
           (when (and width height)
             (reset! x
               (case suunta
                 :vasen (- lisaa-valia-px)
                 :oikea (+ width lisaa-valia-px)
                 :ylos (/ width 2)
                 (/ width 2)))
             (reset! y
               (case suunta
                 :vasen (- (/ height 2))
                 :oikea (- (/ height 2))
                 :ylos (- (+ height lisaa-valia-px))
                 lisaa-valia-px)))))

      (fn [opts auki? sisalto]
        (let [s-pituus (count (str sisalto))
              suunta (case (:suunta opts)
                       :vasen "left"
                       :oikea "right"
                       :ylos "top"
                       "bottom")]
          [:div.tooltip {:class [suunta (when auki? "in")
                                 (or (:leveys opts)
                                   (if (< 150 s-pituus)
                                     "levea"
                                     "ohut"))]
                         :style {:visibility (when-not auki? "hidden")
                                 :margin-left (when @x (str @x "px"))
                                 :margin-top (when @y (str @y "px"))}}
           [:div.tooltip-arrow]
           [:div.tooltip-inner
            sisalto]])))))

(defn tooltip
  "Asettaa annetulle komponentille tooltipin, joka aukeaa joko alas/ylös/vasemmalle/oikealle.
  Tooltip asetetaan automaattisesti oikeaan kohtaan sisällä olevan komponentin mukaan.

  Joskus tulee kuitenkin tilanteita, että komponentin koko ei pysty laskemaan automaattisesti piirtohetkellä.
  Tällaisia tilanteita on esim. Harjan taulukoissa, joissa piilotetut rivit ovat aina 'display:none;', jolloin
  mittojen laskenta epäonnistuu.
  Tällöin käyttäjän täytyy itse kertoa tooltip-komponentille sisällä olevan komponentin (esim. ikonin/napin) mitat,
  jotta tooltip voidaan asettaa oikeaan kohtaan.
  Tämä hoituu asetuksella ':wrapperin-koko {:leveys 20 :korkeus 20}'

  -- Parametrit --

  opts:
    suunta: :vasen / :oikea / :ylos / :alas
    leveys: :levea / :ohut
    wrapper-luokka: Custom luokan nimi tai vektori luokan nimiä, joilla voidaan tyylitellä tooltip wrapperia ja sen lapsia.
    wrapperin-koko: Käyttäjän määrittelemä koko tooltip-wrapprille. Vaikuttaa siihen, miten tooltip asetellaan annetun komponentin viereen.
                    Käytä ainoastaan silloin, kun tooltipin wrapperin kokoa ei voida laskea automaattisesti (esim. display: none;)
   tooltip-disabloitu?: Mikäli arvo on true ei renderöidä tooltippiä vaan palautetaan vain annettu komponentti.

  komponentti: Komponentti, jolle tooltip asetetaan.
  sisalto: Tooltipin teksti tai hiccup-html.
  "

  [{:keys [suunta leveys wrapper-luokka wrapperin-koko tooltip-disabloitu?] :as opts} komponentti sisalto]
  (if-not tooltip-disabloitu? (r/with-let [tooltip-visible?-atom (atom false)]
                                [:div.inline-block
                                 {:class wrapper-luokka
                                  :on-mouse-enter #(reset! tooltip-visible?-atom true)
                                  :on-mouse-leave #(reset! tooltip-visible?-atom false)}
                                 komponentti
                                 [tooltip-sisalto opts @tooltip-visible?-atom sisalto]])
    komponentti))

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
  ([url]
  [[ikonit/livicon-download] "Tallenna Excel" "raporttixls" url])
  ([url id]
   [[ikonit/livicon-download] "Tallenna Excel" id url]))

(defn tallenna-pdf-nappi
  ([url]
  [[ikonit/livicon-download] "Tallenna PDF" "raporttipdf" url])
  ([url id]
   [[ikonit/livicon-download] "Tallenna PDF" id url]))

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

(defn valitys-vertical
  ([]
   (valitys-vertical "2rem"))
  ([korkeus]
   [:div {:style {:margin-top (or korkeus "2rem")}}]))

(defn infolaatikko
  [ikoni teksti tyyppi]
  [:span {:class (str "infolaatikko "
                   (case tyyppi
                     ::info
                     "info"
                     ::ok
                     "ok"
                     ::huomio
                     "huomio"))}
   [ikoni] teksti])

;; Toisinaan tarpeen ajaa esim. erilaisia click handlereitä pienellä viiveellä, jos klikin
;; lähde-elementti unmountataan
(defn fn-viiveella
  "Ajaa funktion viiveellä, käyttäen js/setTimeoutia. Default viive 10ms"
  ([fn-to-run]
   (fn-viiveella fn-to-run 10))
  ([fn-to-run ms]
   (js/setTimeout (fn [] (fn-to-run)) ms)))

(def valitse-text "-valitse-")

(defn tila-indikaattori
  "fmt-fn annetaan arvo ja se formatoi sen jotenkin
  class-skeema on mappi, josta eri tiloja ja niitä vastaavia luokkia palluralle (ei siis pakko käyttää oletusvärejä tms, vaan voi olla muita)
  luokka määrittäää tekstiosan tyylin"
  ([tila]
   (tila-indikaattori tila {}))
  ([tila {:keys [fmt-fn class-skeema luokka wrapper-luokka]}]
   [:div {:class wrapper-luokka}
    [:div {:class (str "circle "
                    (if class-skeema
                      (or (get class-skeema tila)
                        "tila-ehdotettu")
                      (cond
                        (= "tilattu" tila) "tila-tilattu"
                        (= "ehdotettu" tila) "tila-ehdotettu"
                        (= "valmis" tila) "tila-valmis"
                        (= "hylatty" tila) "tila-hylatty"
                        (= "kesken" tila) "tila-kesken"
                        :else "tila-ehdotettu")))}]
    [:span (merge {} (when luokka {:class luokka}))
     (if fmt-fn
       (fmt-fn tila)
       tila)]]))

(def rajapinnan-kautta-lisattyja-ei-voi-muokata
  "Rajapinnan kautta raportoituja toteumia ei voi käsin muokata, vaan muokkaukset on tehtävä lähdejärjestelmässä.")

(defn tr-kentan-elementti
  [{:keys [otsikko valitse-fn luokka arvo] :as optiot}]
  [:div
   [:input {:on-change valitse-fn
            :class (str luokka " form-control ")
            :placeholder otsikko
            :value arvo
            :size 5 :max-length 10}]
   [:label.ala-otsikko otsikko]])

(defn tr-kentat-flex
  "Tuck yhteensopiva TR-tierekisterikenttä.
  (Tämä voisi olla myös valinnat tai tierekisteri namespacessa)"
  [{:keys [wrap-luokka]} {:keys [tie aosa aeta losa leta]}]
  (let [osio (fn [komponentti otsikko] komponentti)]
    (fn [{:keys [wrap-luokka]} {:keys [tie aosa aeta losa leta]}]
      [:div {:class (or wrap-luokka "col-md-3 filtteri tr-osoite")}
       [:label.alasvedon-otsikko-vayla "Tieosoite"]
      [:div
       [:div.varusteet.tr-osoite-flex
        [osio tie "Tie"]
        [osio aosa "aosa"]
        [osio aeta "aet"]
        [osio losa "losa"]
        [osio leta "let"]]]])))
