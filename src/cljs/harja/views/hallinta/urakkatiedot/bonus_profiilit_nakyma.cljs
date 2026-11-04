
(ns harja.views.hallinta.urakkatiedot.bonus-profiilit-nakyma
  (:require [clojure.string :as str]
            [tuck.core :refer [tuck]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.varmista-kayttajalta :refer [varmista-kayttajalta]]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.tiedot.hallinta.urakkatiedot.bonus-profiilit-tiedot :as tiedot]
            [harja.views.hallinta.urakkatiedot.profiilit-yhteiset :as profiilit-yhteiset]))

(defn- aktiivisuus-teksti [aktiivinen]
  (if aktiivinen "Aktiivinen" "Passiivinen"))

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

(defn- vaikutusaika-visualisointi [profiili]
  [:div.sanktio-profiilit-vaikutusaika.margin-bottom-16
   [:h4.sanktio-profiilit-vaikutusaika-otsikko "Vaikutusaika"]
   [:div.sanktio-profiilit-vaikutusaika-palkki
    [:span.sanktio-profiilit-vaikutusaika-pvm (tiedot/vaikutusajan-alku-teksti profiili)]
    [:span.sanktio-profiilit-vaikutusaika-erotin " - "]
    [:span.sanktio-profiilit-vaikutusaika-pvm (tiedot/vaikutusajan-loppu-teksti profiili)]]
   [:div.sanktio-profiilit-vaikutusaika-selite
    (tiedot/vaikutusaika-teksti profiili)]])

(defn- summamaaritys-teksti [{:keys [summa-euroina maaritystapa ohjeteksti]}]
  (cond
    summa-euroina (str summa-euroina " € (" maaritystapa ")"
                    (when ohjeteksti (str " — " ohjeteksti)))
    ohjeteksti (str maaritystapa ": " ohjeteksti)
    :else "-"))

(defn- rajatut-urakat
  [urakat urakka-idt]
  (let [urakka-idt (set urakka-idt)]
    (filterv #(contains? urakka-idt (:id %)) urakat)))

(defn- suorita-urakkarajauksen-muutos!
  [e! toiminto profiili-id profiilirivi-id urakka-id]
  (e! (case toiminto
        :lisaa (tiedot/->LisaaBonusProfiilirivinUrakkarajaus
                 profiili-id profiilirivi-id urakka-id)
        :poista (tiedot/->PoistaBonusProfiilirivinUrakkarajaus
                  profiili-id profiilirivi-id urakka-id))))

(defn- varmista-urakkarajauksen-muutos!
  [e! profiili-id rivi urakka-id toiminto]
  (let [urakkarajausten-maara (or (:urakkarajausten-maara rivi) 0)
        kaikkien-urakoiden-rajaus? (zero? urakkarajausten-maara)
        viimeisen-urakkarajauksen-poisto? (= 1 urakkarajausten-maara)
        vahvistus-tarvitaan? (or (and (= :lisaa toiminto)
                                     kaikkien-urakoiden-rajaus?)
                                (and (= :poista toiminto)
                                     viimeisen-urakkarajauksen-poisto?))
        toiminto-fn #(suorita-urakkarajauksen-muutos!
                       e! toiminto profiili-id (:id rivi) urakka-id)]
    (if vahvistus-tarvitaan?
      (varmista-kayttajalta
        {:otsikko (if (= :lisaa toiminto)
                    "Ensimmäisen urakkarajauksen lisääminen"
                    "Viimeisen urakkarajauksen poistaminen")
         :sisalto (if (= :lisaa toiminto)
                    "Profiilirivi ei tämän jälkeen koske enää kaikkia urakoita. Jatketaanko?"
                    "Profiilirivi alkaa tämän jälkeen koskea kaikkia urakoita. Jatketaanko?")
         :hyvaksy "Kyllä"
         :peruuta-txt "Peruuta"
         :toiminto-fn toiminto-fn})
      (toiminto-fn))))

(defn- urakkarajaus-editori
  [e! profiili-id rivi urakat valitut-urakat muokkaus? muokkaus-kaynnissa?]
  (let [urakka-idt (or (:urakka-idt rivi) [])
        rajatut (rajatut-urakat urakat urakka-idt)
        valittavat-urakat (remove #(contains? (set urakka-idt) (:id %)) urakat)
        valittu-urakka-id (get valitut-urakat (:id rivi))]
    [:div {:class "bonus-profiilirivin-urakkarajaus"
           :data-cy (str "bonus-urakkarajaus-" (:id rivi))}
     [:div
      [:strong "Urakkarajaus: "]
      (if (seq rajatut)
        (str/join ", " (map :nimi rajatut))
        "Kaikki urakat")]
     (when (seq rajatut)
       [:div
        (for [{urakka-id :id nimi :nimi} rajatut]
          ^{:key urakka-id}
          [:button.btn.btn-link.btn-xs
           {:type "button"
            :data-cy (str "bonus-urakkarajaus-poista-" (:id rivi) "-" urakka-id)
            :disabled muokkaus-kaynnissa?
            :on-click #(varmista-urakkarajauksen-muutos!
                         e! profiili-id rivi urakka-id :poista)}
            "Poista " nimi])])
     (when muokkaus?
       [:div.bonus-profiilirivin-urakkarajaus-muokkaus
        [:div.bonus-profiilirivin-urakkarajaus-valinta
         [:label {:for (str "bonus-urakkarajaus-valinta-" (:id rivi))}
          "Valitse rajattava urakka"]
         [:select.form-control
          {:id (str "bonus-urakkarajaus-valinta-" (:id rivi))
           :data-cy (str "bonus-urakkarajaus-valinta-" (:id rivi))
           :value (or valittu-urakka-id "")
           :disabled muokkaus-kaynnissa?
           :on-change #(let [arvo (.. % -target -value)]
                         (e! (tiedot/->ValitseBonusProfiilirivinUrakka
                               (:id rivi)
                               (when (seq arvo)
                                 (js/parseInt arvo 10))))) }
          [:option {:value ""} "Valitse urakka"]
          (for [{urakka-id :id nimi :nimi} valittavat-urakat]
            ^{:key urakka-id}
            [:option {:value urakka-id} nimi])]]
        [:div.bonus-profiilirivin-urakkarajaus-toiminto
         [:button.btn.btn-primary.btn-xs
          {:type "button"
           :data-cy (str "bonus-urakkarajaus-lisaa-" (:id rivi))
           :disabled (or muokkaus-kaynnissa? (nil? valittu-urakka-id))
           :on-click #(varmista-urakkarajauksen-muutos!
                        e! profiili-id rivi valittu-urakka-id :lisaa)}
          "Lisää rajaus"]]])]))

(defn- profiilirivit-grid
  [e! profiili-id rivit urakat valitut-urakat muokkaus? muokkaus-kaynnissa?]
  [:div
   [grid/grid
    {:piilota-toiminnot? true
     :voi-lisata? false
     :voi-poistaa? (constantly false)
     :reunaviiva? true
     :tunniste :id}
    [{:nimi :jarjestys :otsikko "Järjestys" :leveys 0.6 :muokattava? (constantly false)}
     {:nimi :toimenpideinstanssi-teksti :otsikko "T2-koodi" :leveys 1.1 :muokattava? (constantly false)}
     {:nimi :urakkarajausten-maara :otsikko "Urakkarajauksia" :leveys 1 :muokattava? (constantly false)
      :fmt #(or % 0)}
     {:nimi :urakat :otsikko "Rajatut urakat" :leveys 2.2 :muokattava? (constantly false)
      :fmt #(if (seq %)
              (str/join ", " %)
              "-")}
     {:nimi :summamaaritys :otsikko "Euromäärä" :leveys 2.2 :muokattava? (constantly false)
      :hae identity
      :fmt #(summamaaritys-teksti (:summamaaritys %))}]
    rivit]
   (when muokkaus?
     (for [rivi rivit]
       ^{:key (:id rivi)}
       [urakkarajaus-editori e! profiili-id rivi urakat valitut-urakat
        muokkaus? muokkaus-kaynnissa?]))])

(defn- lajit-grid
  [e! profiili-id lajit urakat valitut-urakat muokkaus? muokkaus-kaynnissa?]
  [grid/grid
   {:piilota-toiminnot? true
    :voi-lisata? false
    :voi-poistaa? (constantly false)
    :reunaviiva? true
    :tunniste :id
    :vetolaatikot (into {}
                    (map (juxt :id
                           (fn [laji]
                             [profiilirivit-grid
                              e! profiili-id (:rivit laji) urakat valitut-urakat
                              muokkaus? muokkaus-kaynnissa?])))
                    lajit)}
   [{:tyyppi :vetolaatikon-tila :leveys 0.4 :muokattava? (constantly false)}
    {:nimi :nimi :otsikko "Laji" :leveys 2 :muokattava? (constantly false)}
    {:nimi :uudelleennimeaminen :otsikko "Uudelleennimeäminen" :leveys 2.4 :muokattava? (constantly false)
     :hae identity
     :fmt uudelleennimeaminen-solu}
    {:nimi :laji :otsikko "Koodi" :leveys 1.1 :muokattava? (constantly false)
     :fmt name}
    {:nimi :kirjaustapa :otsikko "Kirjaustapa" :leveys 1.2 :muokattava? (constantly false)}
    {:nimi :automaattinen :otsikko "Automaattinen" :leveys 0.9 :muokattava? (constantly false)
     :fmt #(if % "Kyllä" "Ei")}
    {:nimi :rivit :otsikko "Rivejä" :leveys 0.7 :muokattava? (constantly false)
     :fmt count}]
   lajit])


(defn- profiilin-yhteenveto [{:keys [profiili]}]
  [:div
   [:h4 (:nimi profiili)]
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
      :rivi-klikattu #(e! (tiedot/->ValitseBonusProfiili (:id %)))}
     [{:nimi :nimi :otsikko "Profiili" :leveys 2 :muokattava? (constantly false)}
      {:nimi :urakkatyyppi-teksti :otsikko "Urakkatyyppi" :leveys 1.1 :muokattava? (constantly false)}
      {:nimi :hoitovuosivali :otsikko "Hoitovuodet" :leveys 1 :muokattava? (constantly false)}
      {:nimi :aktiivisuus-teksti :otsikko "Tila" :leveys 0.9 :muokattava? (constantly false)}
      {:nimi :yhteenveto :otsikko "Yhteenveto" :leveys 2.5 :muokattava? (constantly false)}]
     profiilit]))

(defn- bonus-profiilit* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan-ulos #(do
                         (reset! tiedot/nakymassa? true)
                         (e! (tiedot/->HaeBonusProfiilit)))
      #(reset! tiedot/nakymassa? false))
    (fn [e! {:keys [haku-kaynnissa? detalji-haku-kaynnissa? valittu-profiili-id
                    profiilin-detaljit suodattimet profiilit valitut-urakat
                    urakkarajauksen-muokkaus-kaynnissa?] :as app}]
      (let [suodatetut-profiilit (tiedot/suodata-profiilit app)
            valitun-profiilin-detalji (get profiilin-detaljit valittu-profiili-id)
            muokkaus? (oikeudet/voi-kirjoittaa? oikeudet/hallinta-laadunseuranta-profiilit)]
        [:div.sanktio-profiilit-hallinta
         [:h3 "Bonus-profiilit"]
         [:p "Selaa bonus-profiileja profiilikeskeisesti. Vasemmalta valitaan profiili, oikealta näkyvät yhteenveto ja bonuslajeittain ryhmitelty sisältö."]
         [profiilit-yhteiset/suodatin-rivi e! tiedot/->PaivitaSuodatin suodattimet profiilit]
         [:div.row {:style {:margin-top "1rem"}}
          [:div.col-md-5
           (if haku-kaynnissa?
             [ajax-loader-pieni "Haetaan bonus-profiileja..."]
             [:div.sanktio-profiilit-profiililista
              [profiililista e! suodatetut-profiilit valittu-profiili-id]])]
          [:div.col-md-7
           (cond
             (nil? valittu-profiili-id)
             [yleiset/info-laatikko :neutraali "Valitse bonus-profiili listasta."]

             (and detalji-haku-kaynnissa? (nil? valitun-profiilin-detalji))
             [ajax-loader-pieni "Haetaan profiilin sisältöä..."]

             valitun-profiilin-detalji
             [:div
              [profiilin-yhteenveto valitun-profiilin-detalji]
              [:h4 "Sisältö"]
              [lajit-grid
               e!
               (get-in valitun-profiilin-detalji [:profiili :id])
               (:lajit valitun-profiilin-detalji)
               (:urakat valitun-profiilin-detalji)
               valitut-urakat
               muokkaus?
               urakkarajauksen-muokkaus-kaynnissa?]]

             :else
             [yleiset/info-laatikko :varoitus "Bonus-profiilin detaljia ei saatu ladattua."])]]]))))

(defn bonus-profiilit []
  [tuck tiedot/tila bonus-profiilit*])
