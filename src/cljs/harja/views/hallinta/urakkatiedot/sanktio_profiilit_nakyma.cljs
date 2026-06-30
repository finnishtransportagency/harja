(ns harja.views.hallinta.urakkatiedot.sanktio-profiilit-nakyma
  (:require [clojure.string :as str]
            [tuck.core :refer [tuck]]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.tiedot.hallinta.urakkatiedot.sanktio-profiilit-tiedot :as tiedot]
            [harja.views.hallinta.urakkatiedot.bonus-profiilit-nakyma :as bonus-profiilit]))


(defn- aktiivisuus-teksti [aktiivinen]
  (if aktiivinen "Aktiivinen" "Passiivinen"))

(defn- soveltuvuuskonteksti-teksti [soveltuvuuskonteksti]
  (case soveltuvuuskonteksti
    :urakka "Urakka"
    :laatupoikkeama "Laatupoikkeama"
    (name soveltuvuuskonteksti)))

(defn- hoitovuosivali-teksti [{:keys [hoitovuosi-alku hoitovuosi-loppu]}]
  (str hoitovuosi-alku "-" hoitovuosi-loppu))

(defn- paivavali-teksti [{:keys [alkupvm loppupvm]}]
  (str (tiedot/vaikutusajan-alku-teksti {:alkupvm alkupvm})
    " - "
    (tiedot/vaikutusajan-loppu-teksti {:loppupvm loppupvm})))

(defn- uudelleennimeaminen-solu [{:keys [uudelleennimetty uudelleennimeaminen]}]
  (if uudelleennimetty
    [:div
     [:div {:style {:margin-bottom "0.35rem"}}
      [:span.label.label-warning "Nimetty uudelleen"]]
     [:div uudelleennimeaminen]]
    "-"))

(defn- kontekstit-teksti [soveltuvuuskontekstit]
  (str/join ", " (map soveltuvuuskonteksti-teksti soveltuvuuskontekstit)))

(defn- vaikutusaika-visualisointi [profiili]
  [:div.sanktio-profiilit-vaikutusaika.margin-bottom-16
   [:h4.sanktio-profiilit-vaikutusaika-otsikko "Vaikutusaika"]
   [:div.sanktio-profiilit-vaikutusaika-palkki
    [:span.sanktio-profiilit-vaikutusaika-pvm (tiedot/vaikutusajan-alku-teksti profiili)]
    [:span.sanktio-profiilit-vaikutusaika-erotin " - "]
    [:span.sanktio-profiilit-vaikutusaika-pvm (tiedot/vaikutusajan-loppu-teksti profiili)]]
   [:div.sanktio-profiilit-vaikutusaika-selite
    (tiedot/vaikutusaika-teksti profiili)]])

(defn- profiilirivit-grid [rivit]
  [grid/grid
   {:piilota-toiminnot? true
    :voi-lisata? false
    :voi-poistaa? (constantly false)
    :reunaviiva? true
    :tunniste :id}
   [{:nimi :jarjestys :otsikko "Järjestys" :leveys 0.6 :muokattava? (constantly false)}
    {:nimi :soveltuvuuskonteksti :otsikko "Konteksti" :leveys 1 :muokattava? (constantly false)
     :fmt soveltuvuuskonteksti-teksti}
    {:nimi :sanktiotyyppi :otsikko "Sanktiotyyppi" :leveys 2 :muokattava? (constantly false)
     :fmt #(get % :nimi)}
    {:nimi :sanktiotyyppi :otsikko "Koodi" :leveys 0.7 :muokattava? (constantly false)
     :fmt #(get % :koodi)}
    {:nimi :voi-puolittaa-omailmoituksella :otsikko "50 %" :leveys 0.7 :muokattava? (constantly false)
     :fmt #(if % "Kyllä" "Ei")}
    {:nimi :summamaaritykset :otsikko "Summamääritykset" :leveys 1.8 :muokattava? (constantly false)
     :fmt #(if (seq %)
             (str/join "; "
               (map (fn [{:keys [maaritystapa summa-euroina ohjeteksti]}]
                      (str/join " "
                        (cond-> []
                          summa-euroina (conj (str summa-euroina "€"))
                          ohjeteksti (conj (str "(" ohjeteksti ")"))
                          (and (nil? summa-euroina) (nil? ohjeteksti)) (conj (name maaritystapa)))))
                 %))
             "-")}
    {:nimi :sanktiotyyppi :otsikko "Toimenpidekoodi" :leveys 1 :muokattava? (constantly false)
     :fmt #(or (get % :toimenpidekoodi) "-")}]
   rivit])

(defn- lajit-grid [lajit]
  [grid/grid
   {:piilota-toiminnot? true
    :voi-lisata? false
    :voi-poistaa? (constantly false)
    :reunaviiva? true
    :tunniste :id
    :vetolaatikot (into {}
                    (map (juxt :id (fn [laji] [profiilirivit-grid (:rivit laji)])))
                    lajit)}
   [{:tyyppi :vetolaatikon-tila :leveys 0.4 :muokattava? (constantly false)}
    {:nimi :nimi :otsikko "Laji" :leveys 2 :muokattava? (constantly false)}
    {:nimi :uudelleennimeaminen :otsikko "Uudelleennimeäminen" :leveys 2.4 :muokattava? (constantly false)
     :hae identity
     :fmt uudelleennimeaminen-solu}
    {:nimi :laji :otsikko "Koodi" :leveys 1 :muokattava? (constantly false)
     :fmt name}
    {:nimi :rivin-tyyppi :otsikko "Rivin tyyppi" :leveys 1.2 :muokattava? (constantly false)
     :fmt name}
    {:nimi :rivit :otsikko "Rivejä" :leveys 0.7 :muokattava? (constantly false)
     :fmt count}]
   lajit])

(defn- kontekstit-grid [sisalto]
  [grid/grid
   {:piilota-toiminnot? true
    :voi-lisata? false
    :voi-poistaa? (constantly false)
    :reunaviiva? true
    :tunniste :soveltuvuuskonteksti
    :vetolaatikot (into {}
                    (map (juxt :soveltuvuuskonteksti
                           (fn [konteksti]
                             [lajit-grid (:lajit konteksti)])))
                    sisalto)}
   [{:tyyppi :vetolaatikon-tila :leveys 0.4 :muokattava? (constantly false)}
    {:nimi :soveltuvuuskonteksti :otsikko "Soveltuvuuskonteksti" :leveys 2 :muokattava? (constantly false)
     :fmt soveltuvuuskonteksti-teksti}
    {:nimi :lajit :otsikko "Lajeja" :leveys 0.8 :muokattava? (constantly false)
     :fmt count}
    {:nimi :lajit :otsikko "Rivejä" :leveys 0.8 :muokattava? (constantly false)
     :fmt #(reduce + 0 (map (comp count :rivit) %))}]
   sisalto])

(defn- suodatin-rivi [e! suodattimet profiilit]
  (let [{:keys [teksti urakkatyyppi aktiivisuus]} suodattimet
        urakkatyypit (->> profiilit (map :urakkatyyppi) distinct sort)]
    [:div.row
     [:div.col-md-4
      [:label "Hae nimellä"]
      [:input.form-control
       {:type "text"
        :value teksti
        :placeholder "Kirjoita profiilin nimi"
        :on-change #(e! (tiedot/->PaivitaSuodatin :teksti (-> % .-target .-value)))}]]
     [:div.col-md-4
      [:label "Urakkatyyppi"]
      [:select.form-control
       {:value (name urakkatyyppi)
        :on-change #(let [arvo (-> % .-target .-value)]
                      (e! (tiedot/->PaivitaSuodatin :urakkatyyppi
                                                    (if (= "kaikki" arvo) :kaikki (keyword arvo)))))}
       [:option {:value "kaikki"} "Kaikki"]
       (for [nykyinen-urakkatyyppi urakkatyypit]
         ^{:key (name nykyinen-urakkatyyppi)}
         [:option {:value (name nykyinen-urakkatyyppi)} (tiedot/urakkatyyppi-teksti nykyinen-urakkatyyppi)])]]
     [:div.col-md-4
      [:label "Aktiivisuus"]
      [:select.form-control
       {:value (name aktiivisuus)
        :on-change #(e! (tiedot/->PaivitaSuodatin :aktiivisuus (keyword (-> % .-target .-value))))}
       [:option {:value "kaikki"} "Kaikki"]
       [:option {:value "aktiiviset"} "Aktiiviset"]
       [:option {:value "passiiviset"} "Passiiviset"]]]]))

(defn- profiilin-yhteenveto [{:keys [profiili]}]
  [:div
   [:h3 (:nimi profiili)]
   [:div.sanktio-profiilit-yhteenveto-laatikko.margin-bottom-16
    [yleiset/info-laatikko :neutraali (:yhteenveto profiili)]]
   [vaikutusaika-visualisointi profiili]
   [:div.row
    [:div.col-md-6
     [:p [:strong "Urakkatyyppi: "] (tiedot/urakkatyyppi-teksti (:urakkatyyppi profiili))]
     [:p [:strong "Hoitovuodet: "] (hoitovuosivali-teksti profiili)]
     [:p [:strong "Voimassaolo: "] (paivavali-teksti profiili)]]
    [:div.col-md-6
     [:p [:strong "Aktiivisuus: "] (aktiivisuus-teksti (:aktiivinen profiili))]
     [:p [:strong "Kontekstit: "] (kontekstit-teksti (:soveltuvuuskontekstit profiili))]
     [:p [:strong "Lajeja / rivejä: "] (str (:lajimaara profiili) " / " (:rivimaara profiili))]]]])

(defn- profiililista [e! profiilit valittu-profiili-id]
  (let [profiilit (mapv (fn [profiili]
                          (assoc profiili
                            :urakkatyyppi-teksti (tiedot/urakkatyyppi-teksti (:urakkatyyppi profiili))
                            :hoitovuosivali (hoitovuosivali-teksti profiili)
                            :aktiivisuus-teksti (aktiivisuus-teksti (:aktiivinen profiili))
                            :rivin-luokka (when (= (:id profiili) valittu-profiili-id)
                                            "sanktio-profiili-valittu")))
                    profiilit)]
    [grid/grid
     {:piilota-toiminnot? true
      :voi-lisata? false
      :voi-poistaa? (constantly false)
      :reunaviiva? true
      :tunniste :id
      :rivin-luokka :rivin-luokka
      :rivi-klikattu #(e! (tiedot/->ValitseSanktioProfiili (:id %)))}
     [{:nimi :nimi :otsikko "Profiili" :leveys 2 :muokattava? (constantly false)}
      {:nimi :urakkatyyppi-teksti :otsikko "Urakkatyyppi" :leveys 1.1 :muokattava? (constantly false)}
      {:nimi :hoitovuosivali :otsikko "Hoitovuodet" :leveys 1 :muokattava? (constantly false)}
      {:nimi :aktiivisuus-teksti :otsikko "Tila" :leveys 0.9 :muokattava? (constantly false)}
      {:nimi :yhteenveto :otsikko "Yhteenveto" :leveys 2.5 :muokattava? (constantly false)}]
     profiilit]))

(defn- sanktio-profiilit* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan-ulos #(do
                         (reset! tiedot/nakymassa? true)
                         (e! (tiedot/->HaeSanktioProfiilit)))
      #(reset! tiedot/nakymassa? false))
    (fn [e! {:keys [haku-kaynnissa? detalji-haku-kaynnissa? valittu-profiili-id profiilin-detaljit suodattimet profiilit] :as app}]
      (let [suodatetut-profiilit (tiedot/suodata-profiilit app)
            valitun-profiilin-detalji (get profiilin-detaljit valittu-profiili-id)]
        [:div.sanktio-profiilit-hallinta
         [:h3 "Sanktio-profiilit"]
         [:p "Selaa sanktio-profiileja profiilikeskeisesti. Vasemmalta valitaan profiili, oikealta näkyvät yhteenveto ja lajeittain ryhmitelty sisältö."]
         [suodatin-rivi e! suodattimet profiilit]
         [:div.row {:style {:margin-top "1rem"}}
          [:div.col-md-5
           (if haku-kaynnissa?
             [ajax-loader-pieni "Haetaan sanktio-profiileja..."]
             [:div.sanktio-profiilit-profiililista
              [profiililista e! suodatetut-profiilit valittu-profiili-id]])]
          [:div.col-md-7
           (cond
             (nil? valittu-profiili-id)
             [yleiset/info-laatikko :neutraali "Valitse profiili listasta."]

             (and detalji-haku-kaynnissa? (nil? valitun-profiilin-detalji))
             [ajax-loader-pieni "Haetaan profiilin sisältöä..."]

             valitun-profiilin-detalji
             [:div
              [profiilin-yhteenveto valitun-profiilin-detalji]
              [:h4 "Sisältö"]
              [kontekstit-grid (:sisalto valitun-profiilin-detalji)]]

             :else
             [yleiset/info-laatikko :varoitus "Profiilin detaljia ei saatu ladattua."])]]]))))

(defn sanktio-profiilit []
  [:div
   [:h2 "Sanktio- ja bonusprofiilit"]
   [tuck tiedot/tila sanktio-profiilit*]
   [:hr]
   [bonus-profiilit/bonus-profiilit]])
