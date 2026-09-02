(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.tarjous-nakyma
  "Kustannussuunnitelman etusivu määrittää, että renderöidäänkö tarjous vai kustannussuunnitelma"
  (:require
    [clojure.string :as str]
    [harja.fmt :as fmt]
    [harja.pvm :as pvm]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as tarjous-tiedot]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.ui.debug :as debug]
    [harja.ui.grid :as grid]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.komponentti :as komp]
    [harja.ui.napit :as napit]
    [harja.ui.yleiset :as yleiset]
    [tuck.core :as tuck]))

(defonce virheet-atom (atom {}))


;; Määritellään kaikkien kolumnien leveyksiä
(def nimi-leveys 20)
(def yhteensa-leveys 20)

(defn- tallennus-painikkeet [e! tallennus-kesken? viimeisin-muokkaus viimeisin-muokkaaja hankinnat toimenkuvat tallentamattomia-muutoksia?]
  [:div.painikkeet.text-right
   [:div.grid-status-viestit
    (cond
      (and tallentamattomia-muutoksia? viimeisin-muokkaus)
      [:<>
       [:div.status-viesti.tallennettu
        (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]
       [:div.status-viesti.tallentamatta
        "Tallentamattomia muutoksia"]]

      tallentamattomia-muutoksia?
      [:div.status-viesti.tallentamatta
       "Tallentamattomia muutoksia"]

      viimeisin-muokkaus
      [:div.status-viesti.tallennettu
       (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]

      :else
      [:div.status-viesti.ei-muutoksia
       "Ei tallennettuja muutoksia"])]
   [:span {:style {:margin-left "1rem"}}
    [napit/yleinen-toissijainen "Tyhjennä"
     #(e! (tarjous-tiedot/->HaeTyhjatTarjouksenTiedot))
     {:disabled (or tallennus-kesken? false)}]
    [napit/yleinen-ensisijainen "Tallenna muutokset"
     #(e! (tarjous-tiedot/->TallennaTarjouksenTiedot))
     {:disabled (or tallennus-kesken? false)}]]])

(defn- lopullinen-yhteenvetorivi [otsikko rivi]
  (flatten (conj [{:teksti otsikko
                   :luokka "yhteensa disabled lihavoitu"
                   :yhteenveto-vayla true
                   :tyyppi :string}
                  {:teksti ""
                   :luokka "yhteensa lihavoitu"}]
             rivi)))

(defn laske-vuosisummat [rivit vuosikentat]
  (let [vuosidata (->> rivit
                    (map #(select-keys % vuosikentat))
                    (apply merge-with +))
        v (->> vuosidata
            (sort-by key)
            (mapv (fn [[_ arvo]]
                    {:teksti (fmt/euro-opt false arvo)
                     :luokka "yhteensa lihavoitu"
                     :tyyppi :euro
                     :summa arvo
                     :tasaa :oikea
                     :fmt fmt/euro-opt})))
        yhteensa (apply + (map #(get % :summa 0) v))
        v (conj v {:teksti (fmt/euro-opt false yhteensa)
                   :luokka "yhteensa lihavoitu"
                   :tyyppi :euro
                   :tasaa :oikea
                   :fmt fmt/euro-opt})]
    v))

(defn johto-ja-hallintokorvaukset [e! vahvistetut-vuodet uusi-toimenkuva-valittavana kaikki-toimenkuvat vuositaulukon-otsikot vuosi-leveys toimenkuvat]
  (let [urakan-vuodet (range (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
                        (inc (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))))
        vuositaulukon-otsikot (map-indexed (fn [index rivi]
                                             (merge rivi
                                               {:muokattava? (fn [rivi _]
                                                               (let [rivi-vuosi (if (> (count urakan-vuodet) index) (nth urakan-vuodet index) (first urakan-vuodet))
                                                                     salli-muokkaus? (cond
                                                                                       ;; Valmistelukausi ennen urakka-ajan alkua on sallittu muokata vain, jos se on ensimmäinen vuosi ja kyseistä vuotta ei ole vahvistettu
                                                                                       (and (= (:nimi rivi) "Valmistelukausi ennen urakka-ajan alkua") (= 0 index)) (if (not (contains? vahvistetut-vuodet rivi-vuosi)) true false)
                                                                                       ;; Valmistelukautta ei saa muokata muulloin
                                                                                       (and (>= index 1) (= (:nimi rivi) "Valmistelukausi ennen urakka-ajan alkua")) false
                                                                                       ;; Kaikissa muissa tapauksissa saa muokata, jos vuotta ei ole vahvistettu
                                                                                       (not= (:nimi rivi) "Valmistelukausi ennen urakka-ajan alkua") (if (not (contains? vahvistetut-vuodet rivi-vuosi)) true false)
                                                                                       :else false)]
                                                                 salli-muokkaus?))}))
                                vuositaulukon-otsikot)

        ;; Estetään käyttöliittymässä poistettujen toimenkuvien näkyminen listauksessa, vaikka ei ole vielä tallennettu muutoksia kantaan
        toimenkuvat (remove #(true? (:poistettu %)) toimenkuvat)
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        ;; Rajaa toimenkuvavalinnaksi vain ne, jotka eivät ole vielä käytössä - Ja siivotaan talvi- ja kesäkausi pois nimestä, jotta estetään saman toimenkuvan lisääminen uudestaan.
        siivoa-toimenkuva-nimi (fn [toimenkuva-nimi]
                                 (-> toimenkuva-nimi
                                   (str/replace "(kesäkausi)" "")
                                   (str/replace "(talvikausi)" "")
                                   (str/trim)))
        muut-toimenkuvat (filter
                           (fn [k-toimenkuva]
                             (not (some #(= (siivoa-toimenkuva-nimi (:nimi k-toimenkuva)) (siivoa-toimenkuva-nimi (:nimi %))) toimenkuvat)))
                           kaikki-toimenkuvat)

        ;; Toimenkuvan voi aina lisätä, paitsi jos kaikki toimenkuvat on jo lisätty.
        voi-lisata? (if (and (> (count muut-toimenkuvat) 0) (not uusi-toimenkuva-valittavana))
                      true false)
        vuosiavaimet (flatten (map :nimi vuositaulukon-otsikot))
        vuosi-map (zipmap vuosiavaimet (repeat 0))
        ohjauskahva (grid/grid-ohjaus)]
    [grid/grid
     {:otsikko ""
      :data-cy "tarjous-toimenkuvat-grid"
      :muokkaa-aina true
      :voi-muokata? true
      :muokattava? (constantly true)
      :voi-poistaa? (constantly false)
      :voi-lisata? voi-lisata?
      :ohjaus ohjauskahva
      :uusi-rivi (fn [rivi]
                   (let [muokkaus-toimenkuvat (vals (grid/hae-muokkaustila ohjauskahva))]
                     (e! (tarjous-tiedot/->ToggleUusiToimenkuvaValittavana true))
                     (merge (assoc rivi :id -1 :nimi "" :jarjestys (+ 99 (count muokkaus-toimenkuvat)) :yhteensa 0) vuosi-map)))
      :voi-kumota? false
      :piilota-toiminnot? false
      :tunniste :nimi
      :jarjesta :jarjestys
      :muutos #(do
                 (let [toimenkuvat (vals (grid/hae-muokkaustila %))
                       ;; Jos muutos on ollut uuden rivin lisäys, niin asetetaan valittu toimenkuva
                       toimenkuvat (map (fn [toimenkuva]
                                          (if (and (= -1 (:id toimenkuva)) (not (nil? (:nimi toimenkuva))) (nil? (:toimenkuva-id toimenkuva)))
                                            (let [uusi-toimenkuva-kaikista (first (filter (fn [t]
                                                                                            (= (:nimi t) (:nimi toimenkuva)))
                                                                                    kaikki-toimenkuvat))
                                                  uusi-toimenkuva (merge (assoc toimenkuva
                                                                           :osio "johto-ja-hallintokorvaus"
                                                                           :maksukausi "vuosi"
                                                                           :poistettu nil
                                                                           :yhteensa 0
                                                                           :toimenkuva-id (:id uusi-toimenkuva-kaikista))
                                                                    vuosi-map)]
                                              uusi-toimenkuva)
                                            toimenkuva))
                                     toimenkuvat)
                       toimenkuvat (sort-by :jarjestys toimenkuvat)]
                   (e! (tarjous-tiedot/->PaivitaToimenkuvatGrid toimenkuvat))
                   (reset! virheet-atom (grid/hae-virheet %))))
      :rivi-jalkeen-fn (fn [rivit]
                         (let [vuosi-arvot (map :nimi vuositaulukon-otsikot)
                               yhteenvetorivi (laske-vuosisummat rivit vuosi-arvot)]
                           ^{:luokka "yhteenveto"}
                           (lopullinen-yhteenvetorivi "Johto- ja hallintokorvaus yhteensä" yhteenvetorivi)))}

     ;; Otsikot
     (concat [;; ennen 2025 alkaneet urakat eivät voi valita toimenkuvia tästä tarjouslomakkeesta
              (if (< urakan-alkuvuosi 2025)
                {:otsikko "Johto- ja hallintokorvaus"
                 :nimi :nimi
                 :tyyppi :valinta
                 :valinnat-fn #(if (= 0 (count muut-toimenkuvat))
                                 (map :nimi toimenkuvat)
                                 (map :nimi muut-toimenkuvat))
                 :aseta (fn [rivi arvo]
                          (e! (tarjous-tiedot/->ToggleUusiToimenkuvaValittavana false))
                          (merge
                            (assoc rivi :id -1
                              :nimi arvo
                              :toimenkuva (str/lower-case arvo)
                              :uusi-nimi arvo
                              :vanha-id (:toimenkuva-id rivi)
                              :osio "johto-ja-hallintokorvaus"
                              :maksukausi "vuosi"
                              :rahavaraus-id nil)
                            vuosi-map))
                 :luokka "yhteensa"
                 :leveys (str nimi-leveys "%")
                 :muokattava? (fn [rivi arvo] (if (and (= -1 (:id rivi)) (seq muut-toimenkuvat)) true false))}
                {:otsikko "Johto- ja hallintokorvaus"
                 :nimi :nimi
                 :tyyppi :valinta
                 :valinnat-fn #(map :nimi muut-toimenkuvat)
                 :aseta (fn [rivi arvo]
                          (e! (tarjous-tiedot/->ToggleUusiToimenkuvaValittavana false))
                          (merge (assoc rivi :id -1
                                   :nimi arvo
                                   :toimenkuva (str/lower-case arvo)
                                   :paivtetty? true
                                   :uusi-nimi arvo
                                   :vanha-id (:toimenkuva-id rivi)
                                   :osio "johto-ja-hallintokorvaus"
                                   :maksukausi "vuosi"
                                   :rahavaraus-id nil)
                            vuosi-map))
                 :luokka "yhteensa"
                 :leveys (str nimi-leveys "%")
                 ;; Jos on vielä mahdollista vaihtaa toimenkuvaa ja toimenkuva ei ole 'Valmistelukausi ennen urakka-ajan alkua'
                 ;; niin näytä valikko. Muuten ei näytetä valikkoa.
                 :muokattava? (fn [rivi]
                                (if (and
                                      (not= "Valmistelukausi ennen urakka-ajan alkua" (:nimi rivi))
                                      (seq muut-toimenkuvat))
                                  true false))})]
       [;; Poista nappi vain 2025 tai jälkeen alkaneissa urakoissa
        (if (>= urakan-alkuvuosi 2025)
          {:otsikko ""
           :tyyppi :komponentti
           :komponentti (fn [rivi]
                          (napit/yleinen "Poista rivi"
                            :toissijainen
                            #(e! (tarjous-tiedot/->PoistaToimenkuva rivi))
                            {:ikoni (ikonit/livicon-trash) :luokka "btn-xs"}))
           :leveys (str vuosi-leveys "%")}
          {:otsikko ""
           :tyyppi :komponentti
           :komponentti (fn [rivi]
                          [:span])
           :leveys (str vuosi-leveys "%")})]
       vuositaulukon-otsikot
       [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro
         :muokattava? (constantly false) :luokka "yhteensa"
         :hae (fn [rivi] (tarjous-tiedot/laske-rivit-yhteen rivi))
         :fmt (fn [arvo] (if arvo (fmt/euro false arvo) 0.00)) :leveys (str yhteensa-leveys "%") :tasaa :oikea}])
     toimenkuvat]))


(defn hankinnat-grid [e! vuositaulukon-otsikot nimi-leveys yhteensa-leveys hankinnat]
  [grid/grid
   {:otsikko ""
    :data-cy "tarjous-hankinnat-grid"
    :muokkaa-aina true
    :voi-muokata? true
    :muokattava? (constantly true)
    :voi-poistaa? (constantly false)
    :voi-lisata? false
    :voi-kumota? false
    :piilota-toiminnot? false
    :tunniste :nimi
    :jarjesta :jarjestys
    :muutos #(do
               (e! (tarjous-tiedot/->PaivitaHankinnatGrid (vals (grid/hae-muokkaustila %))))
               (reset! virheet-atom (grid/hae-virheet %)))
    :rivi-jalkeen-fn (fn [rivit]
                       (let [vuosi-arvot (map :nimi vuositaulukon-otsikot)
                             summat (laske-vuosisummat rivit vuosi-arvot)]
                         (into
                           [{:teksti "Kaikki hankinnat yhteensä", :luokka "yhteensa lihavoitu" :yhteenveto-vayla true :tyyppi :euro}]
                           summat)))}
   (concat [{:otsikko "Hankinnat" :nimi :nimi :tyyppi :string :luokka "yhteensa" :leveys (str nimi-leveys "%") :muokattava? (constantly false)}]
     vuositaulukon-otsikot
     [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro
       :fmt (fn [arvo]
              (if arvo (fmt/euro false arvo) 0.00))
       :leveys (str yhteensa-leveys "%")
       :hae (fn [rivi] (tarjous-tiedot/laske-rivit-yhteen rivi))
       :tasaa :oikea
       :muokattava? (fn [rivi] (if (:yhteensa rivi) false true))}])
   hankinnat])

(defn erillishankinnat-grid [e! vahvistetut-vuodet vuositaulukon-otsikot nimi-leveys vuosi-leveys yhteensa-leveys erillishankinnat]
  (let [vuositaulukon-otsikot (map #(merge % {:muokattava? (constantly false)}) vuositaulukon-otsikot)]
    [grid/grid
     {:otsikko ""
      :data-cy "tarjous-erillishankinnat-grid"
      :muokkaa-aina true
      :voi-muokata? true
      :muokattava? (constantly true)
      :voi-poistaa? (constantly false)
      :voi-lisata? false
      :voi-kumota? false
      :piilota-toiminnot? false
      :tunniste :nimi
      :muutos #(do
                 (let [muutetut-rivit (vals (grid/hae-muokkaustila %))
                       jyvitetyt-rivit (map (fn [rivi]
                                              (tarjous-tiedot/jyvita-eperhoitovuosi-hoitovuosille rivi vahvistetut-vuodet))
                                         muutetut-rivit)]
                   (e! (tarjous-tiedot/->PaivitaErillishankinnatGrid jyvitetyt-rivit))
                   (reset! virheet-atom (grid/hae-virheet %))))}
     (concat [{:otsikko "Erillishankinnat" :nimi :nimi :tyyppi :string :luokka "yhteensa" :leveys (str nimi-leveys "%") :muokattava? (constantly false)}]
       [{:otsikko "€ / hoitovuosi" :nimi :eperhoitovuosi :tyyppi :euro :leveys (str vuosi-leveys "%") :muokattava? (constantly true)}]
       vuositaulukon-otsikot
       [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro
         :fmt (fn [arvo]
                (if arvo (fmt/euro false arvo) 0.00))
         :leveys (str yhteensa-leveys "%")
         :hae (fn [rivi] (tarjous-tiedot/laske-rivit-yhteen rivi))
         :tasaa :oikea
         :muokattava? (constantly false)}])
     erillishankinnat]))

(defn hoidonjohtopalkkio-grid [e! vahvistetut-vuodet vuositaulukon-otsikot nimi-leveys vuosi-leveys yhteensa-leveys hoidonjohtopalkkiot]
  (let [vuositaulukon-otsikot (map #(merge % {:muokattava? (constantly false)}) vuositaulukon-otsikot)]
    [grid/grid
     {:otsikko ""
      :data-cy "tarjous-hoidonjohtopalkkio-grid"
      :muokkaa-aina true
      :voi-muokata? true
      :muokattava? (constantly true)
      :voi-poistaa? (constantly false)
      :voi-lisata? false
      :voi-kumota? false
      :piilota-toiminnot? false
      :tunniste :nimi
      :muutos #(do
                 (let [muutetut-rivit (vals (grid/hae-muokkaustila %))
                       jyvitetyt-rivit (map (fn [rivi]
                                              (tarjous-tiedot/jyvita-eperhoitovuosi-hoitovuosille rivi vahvistetut-vuodet))
                                         muutetut-rivit)]
                   (e! (tarjous-tiedot/->PaivitaHoidonjohtopalkkioGrid jyvitetyt-rivit))
                   (reset! virheet-atom (grid/hae-virheet %))))
      :rivi-jalkeen-fn nil}

     (concat [{:otsikko "Hoidonjohtopalkkio"
               :nimi :nimi
               :tyyppi :string
               :leveys (str nimi-leveys "%")
               :muokattava? (constantly false)}]
       [{:otsikko "€ / hoitovuosi" :nimi :eperhoitovuosi :tyyppi :euro :leveys (str vuosi-leveys "%") :muokattava? (constantly true)}]
       vuositaulukon-otsikot

       [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro :tasaa :oikea
         :muokattava? (constantly false) :luokka "yhteensa"
         :hae (fn [rivi] (tarjous-tiedot/laske-rivit-yhteen rivi))
         :fmt (fn [arvo] (if arvo (fmt/euro false arvo) 0.00)) :leveys (str yhteensa-leveys "%")}])
     hoidonjohtopalkkiot]))

(defn tavoite-ja-kattohinta-grid [e! vahvistetut-vuodet vuositaulukon-otsikot nimi-leveys yhteensa-leveys
                                  {:keys [hankinnat erillishankinnat hoidonjohtopalkkiot toimenkuvat
                                          muokkaa-kattohinta-kasin yhteensa kattohintakerroin] :as app}]
  (let [urakan-vuodet (range (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
                        (inc (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))))
        vuositaulukon-otsikot (map-indexed (fn [index rivi]
                                             (merge rivi
                                               {:muokattava? (fn [rivi _]
                                                               (let [rivi-vuosi (if (> (count urakan-vuodet) index) (nth urakan-vuodet index) (first urakan-vuodet))
                                                                     salli-muokkaus? (if (= (:nimi rivi) "Tarjouksen kattohinta") (if (not (contains? vahvistetut-vuodet rivi-vuosi)) true false)
                                                                                       false)]
                                                                 salli-muokkaus?))}))
                                vuositaulukon-otsikot)

        gridien-yhteensa (tarjous-tiedot/laske-kaikkien-gridien-yhteensa
                           hankinnat
                           erillishankinnat
                           hoidonjohtopalkkiot
                           toimenkuvat
                           vuositaulukon-otsikot)
        tavoitehinta-rivi (merge (first yhteensa)
                            (:vuosikohtaiset-summat gridien-yhteensa)
                            {:yhteensa (:yhteensa gridien-yhteensa)})
        kattohinta-rivi (if muokkaa-kattohinta-kasin
                          (second yhteensa)
                          (merge
                            (second yhteensa)
                            #_ {:nimi (str "Tarjouksen kattohinta (" (fmt/desimaaliluku kattohintakerroin nil nil false) " x tarjouksen tavoitehinta)")}
                            (zipmap (keys (:vuosikohtaiset-summat gridien-yhteensa))
                              (map #(* kattohintakerroin %) (vals (:vuosikohtaiset-summat gridien-yhteensa))))
                            {:yhteensa (* kattohintakerroin (:yhteensa gridien-yhteensa))}))]
    [grid/grid
     {:otsikko ""
      :data-cy "tarjous-tavoite-kattohinta-grid"
      :muokattava? (if muokkaa-kattohinta-kasin (constantly true) (constantly false))
      :muokkaa-aina muokkaa-kattohinta-kasin
      :voi-muokata? muokkaa-kattohinta-kasin
      :voi-poistaa? (constantly false)
      :voi-lisata? false
      :voi-kumota? false
      :piilota-toiminnot? true
      :tunniste :nimi
      :muutos #(do
                 (e! (tarjous-tiedot/->PaivitaTavoiteJaKattohintaGrid (vals (grid/hae-muokkaustila %))))
                 (reset! virheet-atom (grid/hae-virheet %)))
      :rivi-jalkeen-fn nil}


     (concat [{:otsikko "Tavoite- ja kattohinta"
               :nimi :nimi
               :tyyppi :string
               :leveys (str nimi-leveys "%")
               :muokattava? (constantly false)}]
       vuositaulukon-otsikot
       [{:otsikko "Yhteensä (€)" :nimi :yhteensa :tyyppi :euro
         :muokattava? (constantly false) :tasaa :oikea
         :hae (fn [rivi] (tarjous-tiedot/laske-rivit-yhteen rivi))
         :fmt (fn [arvo] (if arvo (fmt/euro false arvo) 0.00)) :leveys (str yhteensa-leveys "%")}])
     [tavoitehinta-rivi kattohinta-rivi]]))

(defn tarjous-nakyma [e! {:keys [tallennus-kesken? viimeisin-muokkaus viimeisin-muokkaaja hankinnat toimenkuvat
                                 hoidonjohtopalkkiot erillishankinnat tallentamattomia-muutoksia? vahvistetut-vuodet] :as app}]
  (let [ensimmainen-rivi-jossa-hoitovuodet (first (:tarjous app))
        ;; Jos ei ole dataa, käytetään oletusarvoja 5 vuodelle
        hoitovuosittaiset-arvot (:hoitovuosittaiset-arvot ensimmainen-rivi-jossa-hoitovuodet)

        hoitokausien-maara (count hoitovuosittaiset-arvot)
        vuosi-leveys (if (> hoitokausien-maara 0)
                       (/ (- 100 nimi-leveys yhteensa-leveys) hoitokausien-maara)
                       10)

        ;; Muodostetaan otsikot hoitovuosittaiset-arvot perusteella
        vuositaulukon-otsikot (map-indexed
                                (fn [index vuosi-rivi]
                                  {:otsikko (str (inc index) ". Hoitovuosi " (:vuosi vuosi-rivi) " - " (inc (:vuosi vuosi-rivi)) " (€)")
                                   :nimi (keyword (str "vuosi-" (:vuosi vuosi-rivi)))
                                   :tyyppi :euro
                                   :fmt (partial fmt/euro-opt false)
                                   :leveys (str vuosi-leveys "%")
                                   :tasaa :oikea
                                   :muokattava? (if (contains? vahvistetut-vuodet (:vuosi vuosi-rivi))
                                                  (constantly false)
                                                  (constantly true))})
                                hoitovuosittaiset-arvot)]
    [:div
     [:hr]
     ;; Custom toteutus - Tallennusnapit on taulukon yläpuolella
     [tallennus-painikkeet e! tallennus-kesken? viimeisin-muokkaus viimeisin-muokkaaja hankinnat toimenkuvat tallentamattomia-muutoksia?]

     ;;Hankinnat
     [hankinnat-grid e! vuositaulukon-otsikot nimi-leveys yhteensa-leveys hankinnat]

     ;;Erillishankinnat
     [erillishankinnat-grid e! vahvistetut-vuodet vuositaulukon-otsikot nimi-leveys vuosi-leveys yhteensa-leveys erillishankinnat]

     ;;Johto-ja-hallintokorvaus
     [johto-ja-hallintokorvaukset e! vahvistetut-vuodet (:uusi-toimenkuva-valittavana app) (:kaikki-toimenkuvat app) vuositaulukon-otsikot vuosi-leveys toimenkuvat]

     ;;Hoidonjohtopalkkio
     [hoidonjohtopalkkio-grid e! vahvistetut-vuodet vuositaulukon-otsikot nimi-leveys vuosi-leveys yhteensa-leveys hoidonjohtopalkkiot]

     ;;Tavoite-ja-kattohinta
     [tavoite-ja-kattohinta-grid e! vahvistetut-vuodet vuositaulukon-otsikot nimi-leveys yhteensa-leveys app]

     ;; Custom-toteutus. Tallennusnapit on taulukon jälkeen
     [tallennus-painikkeet e! tallennus-kesken? viimeisin-muokkaus viimeisin-muokkaaja hankinnat toimenkuvat tallentamattomia-muutoksia?]]))




(defn nakyma* [e! _app]
  (let [{:keys [sisaan ulos]} (nav/luo-muutosten-hallinta
                                :tarjous-nakyma/muutokset
                                #(get @tila/tarjous-kustannussuunnitelma :tallentamattomia-muutoksia?)
                                :beforeunload-viesti "Tarjouslomakkeella on tallentamattomia muutoksia! Jos poistut, menetät tekemäsi muutokset.")]
    (komp/luo
      (komp/sisaan
        #(do
           (e! (tarjous-tiedot/->HaeTarjouksenTiedot))
           (sisaan)))
      (komp/ulos
        #(do
           (ulos)))
      (fn [e! app]
        [:div
         (when (or (:tarjous app) (seq (concat (:hankinnat app)
                                         (:erillishankinnat app)
                                         (:hoidonjohtopalkkiot app)
                                         (:toimenkuvat app))))
           [:div
            [:div.row
             [:div.col-xs-12.col-md-6
              [:h1 "Tarjouksen tiedot"]]]
            [:div.row
             [yleiset/info-laatikko :neutraali
              [:span
               "Tarkempi kustannusten suunnittelu tehdään tarjouksen tietojen tallentamisen jälkeen "
               [yleiset/linkki "Hoitovuoden alun tavoitehinta -näkymästä"
                #(nav/aseta-valittu-valilehti! :suunnittelu :uusi-kustannussuunnitelma)]
               "."]
              nil nil {:sulje-nappi-id (gensym)}]]
            [tarjous-nakyma e! app]
            [debug/debug app]])]))))

(defn tarjous []
  (tuck/tuck tila/tarjous-kustannussuunnitelma nakyma*))
