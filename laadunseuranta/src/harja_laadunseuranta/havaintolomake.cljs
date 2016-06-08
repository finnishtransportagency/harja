(ns harja-laadunseuranta.havaintolomake
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.core :as t]
            [cljs-time.local :as l]
            [cljs-time.format :as fmt] 
            [harja-laadunseuranta.asetukset :as asetukset]
            [harja-laadunseuranta.kitkamittaus :as kitkamittaus]
            [harja-laadunseuranta.kamera :as kamera]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.kartta :as kartta])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(def pvm-fmt (fmt/formatter "dd.MM.yyyy"))
(def klo-fmt (fmt/formatter "HH:mm"))
(def pvm-klo-fmt (fmt/formatter "dd.MM.yyyy HH:mm"))

(defn tr-osoite [osoite loppuosoite]
  (let [{:keys [tie aosa aet]} @osoite
        {:keys [losa let]} @loppuosoite
        lopputie (:tie @loppuosoite)]
    (if (and lopputie (not= lopputie tie))
      [:div.tr-osoite
       "Tie vaihtunut"]
      [:div.tr-osoite
       [:input {:type "text" :value tie :placeholder "Tie#"}] [:span.valiviiva " / "]
       [:input {:type "text" :value aosa :placeholder "aosa"}] [:span.valiviiva " / "]
       [:input {:type "text" :value aet :placeholder "aet"}] [:span.valiviiva " / "]
       [:input {:type "text" :value losa :placeholder "losa"}] [:span.valiviiva " / "]
       [:input {:type "text" :value let :placeholder "let"}]])))

(defn pvm-aika [aika]
  [:div.pvm-aika
   [:input {:type "text"
            :value (fmt/unparse pvm-fmt @aika)
            :name "pvm"}]
   [:input {:type "text"
            :value (fmt/unparse klo-fmt @aika)
            :name "klo"}]])

(defn- parse-and-check-value [s min max]
  (let [val (js/parseFloat s)]
    (if (js/isNaN val)
      nil
      (when (<= min val max)
        val))))

(defn input-kentta [nimi validointivirheita model {:keys [step min max]}]
  (let [arvo (atom (str @model))
        validi (atom true)]
    (fn [_ _]
      [:div
       [:input {:type "number"
                :step step
                :min min
                :max max
                :style {:width "105px"
                        :display "block"}
                :value @arvo
                :on-blur #(when @validi (reset! model (parse-and-check-value @arvo min max)))
                :on-change #(let [v (-> % .-target .-value)]
                              (reset! arvo v)
                              (if-not (empty? v)
                                (let [x (not (nil? (parse-and-check-value v min max)))]
                                  (reset! validi x)
                                  (swap! validointivirheita (if x disj conj) nimi))
                                (do (reset! validi true)
                                    (swap! validointivirheita disj nimi))))}]
       (when-not @validi [:div.validointivirhe "Arvo ei validi"])])))

(defn kentta [label komponentti]
  [:div.lomake-kentta
   [:div.label label]
   komponentti])

(defn- arviopainike [nro arvio]
  [:button.arviopainike {:on-click #(reset! arvio nro)
                         :class (when (= nro @arvio) "aktiivinen")} nro])

(defn arviokomponentti [arvio]
  [:div.arvio
   (for [n (range 1 6)]
     ^{:key (str "arvio-" n)}
     [arviopainike n arvio])])

(defn kitkakomponentti [kitka]
  (let [syottamassa-kitkaa (atom false)]
    (fn [kitka]
      [:div.kitka-arvo
       [:span {:on-click #(reset! syottamassa-kitkaa true)} (or @kitka "Syötä")]
       (when @syottamassa-kitkaa
         [:div.kitka-syottocontainer
          [kitkamittaus/kitkamittauskomponentti #(do (reset! kitka %)
                                                     (reset! syottamassa-kitkaa false))]])])))

(def lampotilan-rajat {:min -80 :max 100 :step 1})
(def muut-rajat {:min 0 :max 100 :step 1})

(defn- esitaytetty-tai-syotto [nimi esitaytetty yksikko virheita atomi]
  (if esitaytetty
    [:span (str esitaytetty) yksikko]
    [input-kentta nimi virheita atomi muut-rajat]))

(defn- tekstialue [teksti]
  [:textarea {:rows 5
              :on-change #(reset! teksti (-> % .-target .-value))
              :defaultValue ""
              :style {:resize "none"}}])

(defn havaintolomake [wmts-url wmts-url-kiinteistorajat model tallenna-fn peruuta-fn]
  (let [model (atom model)
        sijainti (reagent/cursor model [:sijainti])
        tarkastustyyppi (reagent/cursor model [:tarkastustyyppi])
        kuvaus (reagent/cursor model [:kuvaus])
        kuva (reagent/cursor model [:kuva])
        aikaleima (reagent/cursor model [:aikaleima])
        tr-os (reagent/cursor model [:tr-osoite])
        tr-alku (reagent/cursor model [:tr-alku])
        tr-loppu (reagent/cursor model [:tr-loppu])
        lumimaara (reagent/cursor model [:mittaukset :lumisuus])
        lampotila (reagent/cursor model [:mittaukset :lampotila])
        pikavalinta (reagent/cursor model [:pikavalinta])
        pikavalinnan-kuvaus (reagent/cursor model [:pikavalinnan-kuvaus])
        tasaisuus (reagent/cursor model [:mittaukset :tasaisuus])
        kiinteys (reagent/cursor model [:mittaukset :kiinteys])
        polyavyys (reagent/cursor model [:mittaukset :polyavyys])
        sivukaltevuus (reagent/cursor model [:mittaukset :sivukaltevuus])
        kuva (reagent/cursor model [:kuva])
        kayttajanimi (reagent/cursor model [:kayttajanimi])
        kitkamittaus (reagent/cursor model [:mittaukset :kitkamittaus])
        kitkan-keskiarvo (reagent/cursor model [:kitkan-keskiarvo])
        lumisuus (reagent/cursor model [:lumisuus])
        tasaisuusarvo (reagent/cursor model [:tasaisuus])
        laadunalitus? (reagent/cursor model [:laadunalitus?])
        karttaoptiot (atom {})
        virheita (atom #{})
        reittipisteet (atom [])]
    (fn [_ _ _ tallenna-fn peruuta-fn]
      [:div.havaintolomake
       [:div.lomake-kartta-container
        [kartta/karttakomponentti wmts-url wmts-url-kiinteistorajat sijainti sijainti reittipisteet reittipisteet karttaoptiot]]
       
       [:div.lomake-container
        [:div.lomake-title "Uuden havainnon perustiedot"]
        
        [:div.pvm-kellonaika-tarkastaja
         [kentta "Päivämäärä" [pvm-aika aikaleima]]         
         [kentta "Tarkastaja" [:span.tarkastaja @kayttajanimi]]]
        
        [:div.tieosuus
         [kentta "Tieosuus" [tr-osoite (if @tr-alku tr-alku tr-os) tr-loppu]]]
        
        (if @pikavalinta
          [:div.havainnot
           [:div.lomake-title @pikavalinnan-kuvaus]]
          
          (if (= :kelitarkastus @tarkastustyyppi)
            ;; talvitarkastuksen havainnot
            [:div.havainnot
             [:div.lomake-title "Havainnot"]
             [kentta "Lumimäärä" [esitaytetty-tai-syotto :lumimaara @lumisuus "cm" virheita lumimaara]]
             [kentta "Tasaisuus" [esitaytetty-tai-syotto :tasaisuus @tasaisuusarvo "" virheita tasaisuus]]
             (when-not @lumisuus
               [kentta "Kitka" (if @kitkan-keskiarvo
                                 [:span (utils/kahdella-desimaalilla (utils/avg @kitkan-keskiarvo))]
                                 [kitkakomponentti kitkamittaus])])
             [kentta "Lämpötila" [input-kentta :lampotila virheita lampotila lampotilan-rajat]]]

            ;; kesätarkastuksen havainnot
            [:div.havainnot
             [:div.lomake-title "Havainnot"]
             [kentta "Sivukaltevuus" [input-kentta :sivukaltevuus virheita sivukaltevuus muut-rajat]]
             [kentta "Lämpötila" [input-kentta :lampotila virheita lampotila lampotilan-rajat]]]))

        [:div.lisatietoja
         [:input#laatupoikkeamacheck {:type "checkbox"
                                      :on-change #(swap! laadunalitus? not)} "Laadun alitus"]
         [:div.title "Lisätietoja"]
         [tekstialue kuvaus]
         [kamera/kamerakomponentti kuva]]

        [:div.lomake-painikkeet
         [:button.tallenna {:on-click #(when (empty? @virheita)
                                         (tallenna-fn @model))
                            :disabled (not (empty? @virheita))}
          [:span.livicon-save] "Tallenna"]
         [:button.peruuta {:on-click #(peruuta-fn)}
          [:span.livicon-delete] "Peruuta"]]]])))

(def test-model (atom {:kayttajanimi "Jalmari Järjestelmävastuuhenkilö"
                       :tr-osoite {:tie 20 :aosa 3 :aet 3746}
                       :aikaleima (l/local-now)
                       :kuvaus ""
                       :havainnot {:tasaisuus 5
                                   :kitkamittaus 0.45
                                   :lampotila -12
                                   :lumisuus 3}
                       :sijainti {:lon 428147
                                  :lat 7208956
                                  :heading 45}}))

(defcard havaintolomake-card
  (fn [_ _]
    (reagent/as-element [havaintolomake (str "http://localhost:8000" asetukset/+wmts-url+)
                                        (str "http://localhost:8000" asetukset/+wmts-url-kiinteistojaotus+) test-model #() #()]))
  test-model
  {:watch-atom true
   :inspect-data true})
