(ns harja.views.hallinta.integraatioloki
  "Integraatiolokin näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.integraatioloki :as tiedot]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [ajax-loader livi-pudotusvalikko]]
            [harja.visualisointi :as vis]
            [harja.ui.grid :refer [grid]]
            [harja.ui.valinnat :as valinnat]
            [harja.fmt :as fmt]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.modal :as modal]
            [harja.ui.dom :as dom]
            [harja.ui.lomake :as lomake]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn kartta-merkkijonoksi [kartta]
  (when kartta
    (clojure.string/join
      (map #(str (name (key %)) ": " (val %)) kartta))))

(defn kartta-listaksi [kartta]
  (when kartta
    [:ul
     (for [elementti kartta]
       [:li
        [:b (name (key elementti))]
        (str ": " (val elementti))])]))

(defn nayta-sisalto-modaalissa-dialogissa [otsikko sisalto]
  (modal/nayta! {:otsikko otsikko
                 :leveys "80%"}
                [:div.kayttajan-tiedot sisalto]))

(defn nayta-otsikko [otsikko]
  (let [sisalto (kartta-merkkijonoksi otsikko)
        max-pituus 30]
    (if (> (count sisalto) max-pituus)
      [:div (str (fmt/leikkaa-merkkijono max-pituus {:pisteet? true} sisalto) " ")
       [:span.pull-right
        [:button.nappi-toissijainen.grid-lisaa
         {:on-click
          (fn [e]
            (nayta-sisalto-modaalissa-dialogissa "Otsikko" (kartta-listaksi otsikko)))}
         (ikonit/eye-open)]]]
      sisalto)))

(defn nayta-lisatiedot [lisatiedot]
  (let [max-pituus 60
        teksti lisatiedot]
    (if (> (count teksti) max-pituus)
      [:span
       (str (fmt/leikkaa-merkkijono max-pituus {:pisteet? true} lisatiedot) " ")
       [:button.nappi-toissijainen.grid-lisaa
        {:on-click
         (fn [e]
           (nayta-sisalto-modaalissa-dialogissa "Lisätiedot kokonaisuudessaan" [:pre {:style {:white-space "pre-line"}} teksti]))}
        (ikonit/eye-open)]]
      teksti)))

(defn lataa-tiedostona [sisalto]
  (let [encode (aget js/window "encodeURIComponent")
        url (str "data:Application/octet-stream," (encode sisalto))]
    (set! (.-location js/document) url)))

(defn nayta-sisalto [sisalto]
  (let [max-pituus 30]
    (if (> (count sisalto) max-pituus)
      [:div (str (fmt/leikkaa-merkkijono max-pituus {:pisteet? true} sisalto))
       [:span.pull-right
        [:button.nappi-toissijainen.grid-lisaa
         {:on-click
          (fn [e]
            (nayta-sisalto-modaalissa-dialogissa
              "Viestin sisältö"
              [:span.viesti
               [:pre sisalto]

               [:button.nappi-toissijainen {:type "button"
                                            :on-click #(do
                                                         (.preventDefault %)
                                                         (lataa-tiedostona sisalto))}
                (ikonit/download-alt) " Lataa"]]))}
         (ikonit/eye-open)]]]
      sisalto)))

(defn tapahtuman-tiedot [{id :id}]
  (let [viestit (atom nil)]
    (komp/luo
      {:component-did-mount (fn [_]
                              (go (reset! viestit (<! (tiedot/hae-integraatiotapahtuman-viestit id)))))}
      (fn [_]
        [:span
         [:div.container
          [grid
           {:otsikko "Viestit"}
           [{:otsikko "Suunta" :nimi :suunta :leveys 10 :tyyppi :komponentti
             :komponentti #(if (= "sisään" (:suunta %))
                             [:span.integraatioloki-onnistunut (ikonit/circle-arrow-right) " Sisään"]
                             [:span.integraatioloki-varoitus (ikonit/circle-arrow-left) " Ulos"])}
            {:otsikko "Osoite" :nimi :osoite :leveys 30}
            {:otsikko "Parametrit" :nimi :parametrit :leveys 20 :tyyppi :komponentti
             :komponentti #(fmt/leikkaa-merkkijono 50 (kartta-merkkijonoksi (:parametrit %)))}
            {:otsikko "Otsikko" :nimi :otsikko :leveys 30 :tyyppi :komponentti
             :komponentti #(nayta-otsikko (:otsikko %))}
            {:otsikko "Siirtotyyppi" :nimi :siirtotyyppi :leveys 20}
            {:otsikko "Sisältötyyppi" :nimi :sisaltotyyppi :leveys 20}
            {:otsikko "Palvelin" :nimi :kasitteleva-palvelin :leveys 30 :fmt #(if (empty? %) "Ei tiedossa" %)}
            {:otsikko "Sisältö" :nimi :sisalto :leveys 30 :tyyppi :komponentti
             :komponentti #(nayta-sisalto (:sisalto %))}]
           @viestit]]]))))

(defn tapahtumien-maarat-graafi [tiedot]
  (let [w (int (* 0.85 @dom/leveys))
        h (int (/ w 3))
        tiedot-pvm-sortattu (sort-by :pvm tiedot)
        eka-pvm (pvm/pvm (:pvm (first tiedot-pvm-sortattu)))
        vika-pvm (pvm/pvm (:pvm (last tiedot-pvm-sortattu)))
        pvm-kohtaiset-tiedot (vals (group-by :pvm tiedot-pvm-sortattu))
        pvm-kohtaiset-maarat-summattu (sort-by :pvm
                                               (map (fn [rivit]
                                                      (zipmap [:pvm :maara]
                                                              [(:pvm (first rivit))
                                                               (reduce + (keep :maara rivit))])) pvm-kohtaiset-tiedot))]
    [:span.pylvaat
     [:h5 (str "Vastaanotetut pyynnöt " eka-pvm " - " vika-pvm)]
     (let [lkm-max (reduce max (map :maara pvm-kohtaiset-maarat-summattu))
           tikit (distinct [0
                            (js/Math.round (* .25 lkm-max))
                            (js/Math.round (* .5 lkm-max))
                            (js/Math.round (* .75 lkm-max))
                            lkm-max])
           nayta-labelit? (< (count pvm-kohtaiset-maarat-summattu) 10)]
       [vis/bars {:width w
                  :height (min 200 h)
                  :label-fn #(if nayta-labelit?
                               (pvm/paiva-kuukausi (:pvm %))
                               "")
                  :value-fn :maara
                  :format-amount str
                  :ticks tikit}
        pvm-kohtaiset-maarat-summattu])]))

(defn eniten-kutsutut-integraatiot [tiedot]
  (let [ryhmittele #(group-by :integraatio %)
        ryhmitellyt (vals (ryhmittele tiedot))
        maarat-summattu (map (fn [rivit]
                               (zipmap [:integraatio :jarjestelma :nimi :maara]
                                       [(:integraatio (first rivit))
                                        (:jarjestelma (first rivit))
                                        (:nimi (first rivit))
                                        (reduce + (keep :maara rivit))]))
                             ryhmitellyt)
        eniten-kutsutut (take 5 (reverse (sort-by :maara maarat-summattu)))]
    [grid
     {:otsikko "Eniten kutsutut integraatiot"
      :voi-muokata? false
      :tunniste :integraatio}

     [{:otsikko "Järjestelmä" :nimi :jarjestelma :leveys "15%" :tyyppi :string}
      {:otsikko "Nimi" :nimi :nimi :leveys "40%" :tyyppi :string}
      {:otsikko "Määrä" :nimi :maara :leveys "10%" :tyyppi :string}]

     eniten-kutsutut]))

(defn tapahtumien-paanakyma []
  (let [maara-per-sivu 100
        aloitussivu (r/with-let [tapahtuma-id-alussa (first @tiedot/tapahtuma-id)]
                                 ;; Tämä on totta silloin, kun tullaan urlin kautta näkymään.
                                 (when tapahtuma-id-alussa
                                   (js/Math.floor
                                     (/ (ffirst
                                          (sequence (comp
                                                      (map-indexed #(identity [%1 (:id %2)]))
                                                      (filter #(= (second %) tapahtuma-id-alussa)))
                                                    @tiedot/haetut-tapahtumat))
                                        maara-per-sivu))))]
    (fn []
      [:span
       [:div.container
        [:div.label-ja-alasveto
         [:span.alasvedon-otsikko "Järjestelmä"]
         [livi-pudotusvalikko {:valinta @tiedot/valittu-jarjestelma
                               :format-fn #(if % (:jarjestelma %)
                                                 "Kaikki järjestelmät")
                               :valitse-fn #(do
                                              (reset! tiedot/valittu-jarjestelma %)
                                              (when-not (and @tiedot/valittu-jarjestelma
                                                             (contains? (:integraatiot @tiedot/valittu-jarjestelma)
                                                                        @tiedot/valittu-integraatio))
                                                (reset! tiedot/valittu-integraatio nil)))}
          (vec (concat [nil] (sort-by :jarjestelma @tiedot/jarjestelmien-integraatiot)))]]
        [harja.ui.yleiset/pudotusvalikko
         "Automaattinen päivitys"
         {:valinta @tiedot/hae-automaattisesti?
          :format-fn #(if % "Päällä" "Pois päältä")
          :valitse-fn #(reset! tiedot/hae-automaattisesti? %)}
         [true false]]

        (when @tiedot/valittu-jarjestelma
          [:div.label-ja-alasveto
           [:span.alasvedon-otsikko "Integraatio"]
           [livi-pudotusvalikko {:valinta @tiedot/valittu-integraatio
                                 :format-fn #(if % (str %) "Kaikki integraatiot")
                                 :valitse-fn #(reset! tiedot/valittu-integraatio %)}
            (vec (concat [nil] (sort (:integraatiot @tiedot/valittu-jarjestelma))))]])


        (if @tiedot/nayta-uusimmat-tilassa?
          [:button.nappi-ensisijainen {:on-click #(tiedot/nayta-tapahtumat-eilisen-jalkeen)} "Näytä/hae aikaväliltä"]
          [:span
           [valinnat/aikavali tiedot/valittu-aikavali]
           [:button.nappi-ensisijainen {:on-click #(tiedot/nayta-uusimmat-tapahtumat!)} "Näytä uusimmat"]])


        (if-not (empty? @tiedot/tapahtumien-maarat)
          [:div.integraatio-tilastoja
           [tapahtumien-maarat-graafi @tiedot/tapahtumien-maarat]
           (when-not @tiedot/valittu-integraatio
             [eniten-kutsutut-integraatiot @tiedot/tapahtumien-maarat])]
          [:div "Ei pyyntöjä annetuilla parametreillä"])

        (when (not @tiedot/nayta-uusimmat-tilassa?)
          [lomake/lomake
           {:otsikko "Hakuehdot"
            :muokkaa! #(reset! tiedot/hakuehdot %)}
           [{:otsikko "Tapahtumien tila" :nimi :tapahtumien-tila :tyyppi :valinta
             :valinta-arvo first
             :valinta-nayta second
             :valinnat [[:kaikki "Kaikki"]
                        [:onnistuneet "Onnistuneet"]
                        [:epaonnistuneet "Epäonnistuneet"]]}
            {:otsikko "Tapahtuman kesto minuuteissa yli"
             :nimi :tapahtumien-kesto
             :tyyppi :positiivinen-numero
             :kokonaisluku? true}
            (lomake/ryhma
              {:otsikko "Vapaasanahaut (Huom. voivat olla todella hitaita)"}
              {:otsikko "Otsikot"
               :nimi :otsikot
               :tyyppi :string}
              {:otsikko "Parametrit"
               :nimi :parametrit
               :tyyppi :string}
              {:otsikko "Viestin sisältö"
               :nimi :viestin-sisalto
               :tyyppi :string})
            (lomake/rivi
              {:nimi :hae
               :tyyppi :komponentti
               :komponentti (fn [_] [:button.nappi-ensisijainen {:on-click #(tiedot/hae-tapahtumat!)} "Hae"])}
              {:nimi :tyhjenna
               :tyyppi :komponentti
               :komponentti (fn [_] [:button.nappi-ensisijainen {:on-click #(swap!
                                                                              tiedot/hakuehdot
                                                                              dissoc :otsikot :parametrit :viestin-sisalto)}
                                     "Tyhjennä"])})]
           @tiedot/hakuehdot])

        [grid
         {:otsikko (if @tiedot/nayta-uusimmat-tilassa? "Uusimmat tapahtumat" "Tapahtumat")
          :tyhja (if @tiedot/haetut-tapahtumat "Ei tapahtumia" [ajax-loader "Haetaan tapahtumia"])
          :vetolaatikot (into {}
                              (map (juxt :id (fn [tapahtuma]
                                               [tapahtuman-tiedot tapahtuma]))
                                   @tiedot/haetut-tapahtumat))
          :sivuta maara-per-sivu
          :aloitussivu aloitussivu
          :vetolaatikot-auki (when @tiedot/tapahtuma-id tiedot/tapahtuma-id)
          }

         (vec
           (keep identity
                 [{:tyyppi :vetolaatikon-tila :leveys 3}
                  (when-not @tiedot/valittu-jarjestelma
                    {:otsikko "Järjestelmä" :nimi :jarjestelma :hae (comp :jarjestelma :integraatio) :leveys 10})
                  (when-not @tiedot/valittu-integraatio
                    {:otsikko "Integraatio" :nimi :integraatio :hae (comp :nimi :integraatio) :leveys 15})
                  {:otsikko "Tila" :nimi :onnistunut :leveys 10 :tyyppi :komponentti
                   :komponentti #(if (nil? (:paattynyt %))
                                   [:span.integraatioloki-varoitus (ikonit/aika) " Kesken"]
                                   (if (:onnistunut %) [:span.integraatioloki-onnistunut (ikonit/thumbs-up) " Onnistunut"]
                                                       [:span.integraatioloki-virhe (ikonit/thumbs-down) " Epäonnistunut"]))}
                  {:otsikko "Alkanut" :nimi :alkanut :leveys 15
                   :hae #(if (:alkanut %)
                           (pvm/pvm-aika-sek (:alkanut %))
                           "-")}
                  {:otsikko "Päättynyt" :nimi :paattynyt :leveys 15
                   :hae #(if (:paattynyt %)
                           (pvm/pvm-aika-sek (:paattynyt %))
                           "-")}
                  {:otsikko "Kesto"
                   :tyyppi :komponentti
                   :komponentti (fn [{:keys [alkanut paattynyt]}]
                                  (when (and alkanut paattynyt)
                                    (let [vali (t/interval alkanut paattynyt)
                                          kesto-sekunneissa (t/in-seconds vali)
                                          kesto-millisekunneissa (t/in-millis vali)
                                          kesto (str kesto-sekunneissa " s (" kesto-millisekunneissa " ms)")]
                                      (cond
                                        (< 600 kesto-sekunneissa) [:span.integraatioloki-virhe (ikonit/aika) " " kesto]
                                        (< 300 kesto-sekunneissa) [:span.integraatioloki-virhe kesto]
                                        (< 60 kesto-sekunneissa) [:span.integraatioloki-varoitus kesto]
                                        :else [:span.integraatioloki-onnistunut kesto]))))
                   :leveys 10}
                  {:otsikko "Ulkoinen id" :nimi :ulkoinenid :leveys 10}
                  {:otsikko "Lisätietoja" :nimi :lisatietoja :leveys 30 :tyyppi :komponentti :komponentti (fn [rivi] (nayta-lisatiedot (:lisatietoja rivi)))}]))

         @tiedot/haetut-tapahtumat]]])))

(defn aloita-tapahtumien-paivitys! []
  (let [paivita? (atom true)]
    (go
      (when @tiedot/nayta-uusimmat-tilassa?
        (tiedot/hae-tapahtumat!))
      (<! (timeout 2000))
      (loop []
        (<! (timeout 20000))
        (when @paivita?
          (when (and @tiedot/hae-automaattisesti? @tiedot/nayta-uusimmat-tilassa?)
            (tiedot/hae-tapahtumat!))
          (recur))))
    #(reset! paivita? false)))                                ;; palautetaan pysäytysfunktio jota komp/ulos kutsuu

(defn integraatioloki []
  (komp/luo
    (komp/ulos (aloita-tapahtumien-paivitys!))
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(when-not (empty? @tiedot/tapahtuma-id)
                    (tiedot/hae-tapahtumat!)))
    (fn []
      (if @tiedot/haetut-tapahtumat
        [:div.integraatioloki
         [tapahtumien-paanakyma]]
        [ajax-loader]))))
