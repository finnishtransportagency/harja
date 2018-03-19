(ns harja.ui.grid.muokkaus
  "Harjan käyttöön soveltuva geneerinen jatkuvassa
   muokkaustilassa oleva ruudukkokomponentti."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log tarkkaile! logt] :refer-macros [mittaa-aika]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje vihje] :as y]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :refer [tee-kentta nayta-arvo vain-luku-atomina]]
            [harja.ui.validointi :as validointi]
            [harja.ui.skeema :as skeema]
            [goog.events :as events]
            [goog.events.EventType :as EventType]

            [cljs.core.async :refer [<! put! chan]]
            [clojure.string :as str]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.grid.protokollat :refer
             [Grid aseta-grid vetolaatikko-rivi lisaa-rivi!
              vetolaatikko-rivi vetolaatikon-tila]]
            [harja.ui.ikonit :as ikonit]
            [cljs-time.core :as t]
            [harja.ui.grid.yleiset :as grid-yleiset])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.makrot :refer [fnc]]))

(defn- muokkauspaneeli [{:keys [otsikko voi-muokata? voi-kumota? muokatut virheet skeema peru!
                                voi-lisata? ohjaus uusi-id opts paneelikomponentit historia
                                virhe-viesti]}]
  [:div.panel-heading
   (when otsikko [:h6.panel-title otsikko])
   (when virhe-viesti [:span.tila-virhe {:style {:margin-left "5px"}} virhe-viesti])
   (when (not= false voi-muokata?)
     [:span.pull-right.muokkaustoiminnot
      (when (not= false voi-kumota?)
        [:button.nappi-toissijainen
         {:disabled (empty? @historia)
          :on-click #(do (.stopPropagation %)
                         (.preventDefault %)
                         (peru! muokatut virheet skeema))}
         (ikonit/peru) " Kumoa"])
      (when (not= false voi-lisata?)
        [:button.nappi-toissijainen.grid-lisaa
         {:on-click #(do (.preventDefault %)
                         (lisaa-rivi! ohjaus
                                      (if uusi-id
                                        {:id uusi-id}
                                        {})))}
         (ikonit/livicon-plus) (or (:lisaa-rivi opts) "Lisää rivi")])
      (when paneelikomponentit
        (map-indexed (fn [i komponentti]
                       ^{:key i}
                       [komponentti])
                     paneelikomponentit))])])

(defn- muokkausrivi [{:keys [rivinumerot? ohjaus vetolaatikot id rivi rivin-virheet rivi-index
                             nayta-virheet? nykyinen-fokus i voi-muokata? fokus tulevat-rivit
                             muokatut-atom muokkaa! virheet piilota-toiminnot? skeema
                             disabloi-rivi?
                             voi-poistaa? toimintonappi-fn]}]
  (let [rivi-disabloitu? (and disabloi-rivi? (disabloi-rivi? rivi))]
    [:tr.muokataan {:class (str (if (even? (+ i 1))
                                  "parillinen "
                                  "pariton ")
                                (when rivi-disabloitu? "disabloitu-rivi"))}
     (when rivinumerot? [:td.rivinumero (+ i 1)])
     (doall
       (map-indexed
         (fn [j {:keys [nimi hae aseta fmt muokattava? tyyppi tasaa
                        komponentti] :as sarake}]
           (if (= :vetolaatikon-tila tyyppi)
             ^{:key (str "vetolaatikontila" id)}
             [vetolaatikon-tila ohjaus vetolaatikot id]
             (let [sarake (assoc sarake :rivi rivi)
                   hae (or hae #(get % nimi))
                   arvo (hae rivi)
                   tasaus-luokka (y/tasaus-luokka tasaa)
                   kentan-virheet (get rivin-virheet nimi)
                   tayta-alas (:tayta-alas? sarake)
                   fokus-id [i nimi]]
               (if (or (nil? muokattava?) (muokattava? rivi i))
                 ^{:key (str j nimi)}
                 [:td {:class (str "muokattava "
                                   tasaus-luokka
                                 (when-not (empty? kentan-virheet)
                                     " sisaltaa-virheen"))}
                  (when (and (not (empty? kentan-virheet))
                             (case nayta-virheet?
                               :fokus (= nykyinen-fokus [i nimi])
                               :aina true))
                    (virheen-ohje kentan-virheet))

                  (cond
                    ;; Mikäli rivi on disabloitu, piirretään erikseen määritelty sisältö, jos se on annettu...
                    (and rivi-disabloitu? (:sisalto-kun-rivi-disabloitu sarake))
                    ((:sisalto-kun-rivi-disabloitu sarake) rivi i)

                    ;; ... tai mikäli sisältöä ei ole määritelty, eikä ole erikseen sallittu muokkausta, ei piirretä mitään
                    (and rivi-disabloitu? (not (:salli-muokkaus-rivin-ollessa-disabloituna? sarake)))
                    nil

                    ;; Rivi ei ole disabloitu tai sisällön muokkaus on sallittu
                    :default
                    (if (= tyyppi :komponentti)
                      (komponentti rivi {:index i
                                         :muokataan? true})
                      (if voi-muokata?
                       [:span.grid-kentta-wrapper (when tayta-alas {:style {:position "relative"}})

                        (when tayta-alas
                          (grid-yleiset/tayta-alas-nappi {:fokus (when fokus @fokus)
                                                          :fokus-id fokus-id
                                                          :arvo arvo :tayta-alas tayta-alas
                                                          :rivi-index rivi-index
                                                          :tulevat-rivit tulevat-rivit
                                                          :hae hae
                                                          :sarake sarake :ohjaus ohjaus :rivi rivi}))

                        [tee-kentta (assoc sarake :on-focus #(reset! fokus [i nimi]))
                         (r/wrap
                           arvo
                           (fn [uusi]
                             (if aseta
                               (muokkaa! muokatut-atom virheet skeema
                                         id (fn [rivi]
                                              (aseta rivi uusi)))
                               (muokkaa! muokatut-atom virheet skeema
                                         id assoc nimi uusi))))]]
                       [nayta-arvo (assoc sarake :index i :muokataan? false)
                        (vain-luku-atomina arvo)])))]

                 ^{:key (str j nimi)}
                 [:td {:class (str "ei-muokattava " tasaus-luokka)}
                  ((or fmt str) (hae rivi))]))))
         skeema))
     (when-not piilota-toiminnot?
       [:td.toiminnot
        (or (toimintonappi-fn rivi (partial muokkaa! muokatut-atom virheet skeema id))
            (when (and (not= false voi-muokata?)
                       (or (nil? voi-poistaa?) (voi-poistaa? rivi)))
              [:span.klikattava {:on-click
                                 #(do (.preventDefault %)
                                      (muokkaa! muokatut-atom
                                                virheet skeema
                                                id assoc
                                                :poistettu true))}
               (ikonit/livicon-trash)]))
        (when-not (empty? rivin-virheet)
          [:span.rivilla-virheita
           (ikonit/livicon-warning-sign)])])]))

(defn- kasketty-jarjestys [{:keys [virheet-ylos-fn jarjesta-kun-kasketaan muokatut muokatut-atom]}]
  (let [jarjestys-alussa (sort-by (juxt #(virheet-ylos-fn (second %)) #(jarjesta-kun-kasketaan %)) (seq muokatut))
        tallennettu-jarjestys (map-indexed (fn [i [id rivi]]
                                             [id (assoc rivi :jarjestys-gridissa i)])
                                           jarjestys-alussa)]
    tallennettu-jarjestys))

(defn- gridin-runko [{:keys [muokatut skeema tyhja virheet valiotsikot ohjaus vetolaatikot
                             nayta-virheet? rivinumerot? nykyinen-fokus fokus voi-muokata? jarjesta-kun-kasketaan
                             disabloi-rivi? muokkaa! piilota-toiminnot? voi-poistaa? jarjesta jarjesta-avaimen-mukaan
                             vetolaatikot-auki virheet-ylos? toimintonappi-fn meta-atom]}]
  [:tbody
   (let [muokatut-atom muokatut
         muokatut @muokatut
         colspan (inc (count skeema))
         tulevat-rivit (fn [aloitus-idx]
                         (map #(get muokatut %) (drop (inc aloitus-idx) (keys muokatut))))]
     (if (every? :poistettu (vals muokatut))
       [:tr.tyhja [:td {:colSpan colspan} tyhja]]
       (let [kaikki-virheet @virheet
             virheet-ylos-fn (if virheet-ylos?
                               #(nil? (get kaikki-virheet (:id %)))
                               (fn [_] nil))
             kasketty-jarjestamaan? (and jarjesta-kun-kasketaan (or (:jarjesta-gridissa (meta muokatut))
                                                                    (not (:jarjestetty-kerran? (meta muokatut)))))
             jarjestetty-data (cond
                                jarjesta (sort-by (comp (juxt virheet-ylos-fn jarjesta) second) (seq muokatut))
                                jarjesta-avaimen-mukaan (sort-by (comp (juxt virheet-ylos-fn jarjesta-avaimen-mukaan) first) (seq muokatut))
                                kasketty-jarjestamaan? (kasketty-jarjestys {:virheet-ylos-fn virheet-ylos-fn :jarjesta-kun-kasketaan jarjesta-kun-kasketaan :muokatut muokatut :muokatut-atom muokatut-atom})
                                (and jarjesta-kun-kasketaan (not (:jarjesta-gridissa (meta muokatut)))) (sort-by (fn [[i rivi]]
                                                                                                                   (conj ((juxt virheet-ylos-fn :jarjestys-gridissa) rivi) i))
                                                                                                                 (seq muokatut))
                                :else (seq muokatut))
             metan-lisays-fn #(reset! meta-atom (merge (meta muokatut) {:arvot {:jarjesta-gridissa false
                                                                                :jarjestetty-kerran? true}
                                                                        :paivita? true}))]
         (when kasketty-jarjestamaan? (metan-lisays-fn))
         (doall
           (mapcat
             identity
             (keep-indexed
               (fn [i [id rivi]]
                 (let [rivin-virheet (get kaikki-virheet id)
                       otsikko (valiotsikot id)]
                   (when-not (:poistettu rivi)
                     (into (if otsikko
                             [^{:key (str "otsikko" i)}
                             [:tr.otsikko
                              [:td {:colSpan colspan}
                               (:teksti otsikko)]]]
                             [])
                           [^{:key (str i "-" id)}
                           [muokkausrivi {:rivinumerot? rivinumerot? :ohjaus ohjaus
                                          :vetolaatikot vetolaatikot :id id :rivi rivi :rivin-virheet rivin-virheet
                                          :nayta-virheet? nayta-virheet? :nykyinen-fokus nykyinen-fokus
                                          :i i :voi-muokata? voi-muokata? :fokus fokus
                                          :tulevat-rivit (tulevat-rivit i) :rivi-index i
                                          :muokatut-atom muokatut-atom :muokkaa! muokkaa!
                                          :disabloi-rivi? disabloi-rivi?
                                          :virheet virheet :piilota-toiminnot? piilota-toiminnot?
                                          :skeema skeema :voi-poistaa? voi-poistaa?
                                          :toimintonappi-fn toimintonappi-fn}]

                            (vetolaatikko-rivi vetolaatikot vetolaatikot-auki id colspan)]))))
               jarjestetty-data))))))])

(defn muokkaus-grid
  "Versio gridistä, jossa on vain muokkaustila. Tilan tulee olla muokkauksen vaatimassa {<id> <tiedot>} array mapissa.
  Tiedot tulee olla atomi tai wrapatty data, jota tietojen muokkaus itsessään manipuloi.

  Optiot on mappi optioita:
  :id                             grid pääelementin DOM id
  :muokkaa-footer                 optionaalinen footer komponentti joka muokkaustilassa näytetään, parametrina Grid ohjauskahva
  :muutos                         jos annettu, kaikista gridin muutoksista tulee kutsu tähän funktioon.
                                  Parametrina Grid ohjauskahva
  :uusi-rivi                      jos annettu uuden rivin tiedot käsitellään tällä funktiolla
  :voi-muokata?                   jos false, tiedot eivät ole muokattavia ollenkaan
  :voi-lisata?                    jos false, uusia rivejä ei voi lisätä
  :voi-kumota?                    jos false, kumoa-nappia ei näytetä
  :voi-poistaa?                   funktio, joka palauttaa true tai false.
  :rivinumerot?                   Lisää ylimääräisen sarakkeen, joka listaa rivien numerot alkaen ykkösestä
  :jarjesta                       jos annettu funktio, sortataan rivit tämän mukaan
  :jarjesta-avaimen-mukaan        jos annettu funktio, sortataan avaimen mukaan
  :jarjesta-kun-kasketaan         Järjestetään tämän funktion mukaan, kun datalle annetaan {:jarjesta-gridissa true} metadata.
                                  Tämä metadata poistetaan jarjestämisen jälkeen.
  :paneelikomponentit             vector funktioita, jotka palauttavat komponentteja. Näytetään paneelissa.
  :piilota-toiminnot?             boolean, piilotetaan toiminnot sarake jos true
  :toimintonappi-fn               funktio, joka saa parametrikseen rivin, ja rivin muokkausfunktion.
                                  Jos palauttaa nil, näytetään oletustoiminto (roskakori).
                                  Funktion pitää palauttaa hiccup-elementti, esim [:span]. Oletuksena (constantly nil).
                                  Parametrina saatava muokkausfunktio ottaa parametrikseen funktion ja sen parametrit,
                                  joilla muutos riviin tehdään. Esim (muokkaa! assoc :poistettu true)
  :luokat                         Päätason div-elementille annettavat lisäkuokat (vectori stringejä)
  :virheet                        atomi gridin virheitä {rivinid {:kentta (\"virhekuvaus\")}}, jos ei anneta
                                  luodaan sisäisesti atomi virheille
  :uusi-id                        seuraavan uuden luotavan rivin id, jos ei anneta luodaan uusia id:tä
                                  sarjana -1, -2, -3, ...
  :nayta-virheet?                 :aina (oletus) tai :fokus.
                                  Jos fokus, näytetään virheviesti vain fokusoidulle kentälle,
                                  virheen indikoiva punainen viiva näytetään silti aina.

  :valiotsikot                    mäppäys rivin tunnisteesta, jota ennen otsikko tulee näyttää, Otsikkoon
  :ulkoinen-validointi?           jos true, grid ei tee validointia muokkauksen yhteydessä.
                                  Käytä tätä, jos teet validoinnin muualla (esim jos grid data on wrap,
                                  jonka muutoksen yhteydessä validointi tehdään).

  :virheet-dataan?                jos true, validointivirheet asetetaan rivin datan mäppiin
                                  avaimella :harja.ui.grid/virheet
  :virheet-ylos?                  Jos on virheellistä dataa taulukossa ja on annettu :jarjesta tai :jarjesta-avaimen-mukaan
                                  avimille arvot, niin näytetäänkö virheellinen data ylhäällä vai ei?
  :virhe-viesti                   String, joka näytetään gridin otsikon oikealla puolella punaisella."
  [{:keys [otsikko tyhja tunniste voi-poistaa? rivi-klikattu rivinumerot? voi-kumota? jarjesta-kun-kasketaan
           voi-muokata? voi-lisata? jarjesta jarjesta-avaimen-mukaan piilota-toiminnot? paneelikomponentit
           muokkaa-footer muutos uusi-rivi luokat ulkoinen-validointi? virheet-dataan? virheet-ylos?
           virhe-viesti toimintonappi-fn disabloi-rivi?] :as opts}
   skeema muokatut]
  (let [uusi-id (atom 0) ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])
        virheet-atom (or (:virheet opts) (atom {})) ;; validointivirheet: (:id rivi) => [virheet]
        vetolaatikot-auki (or (:vetolaatikot-auki opts)
                              (atom #{}))
        fokus (atom nil)
        meta-atom (atom nil)
        ohjaus-fn (fn [muokatut virheet skeema]
                    (reify Grid
                      (lisaa-rivi! [this rivin-tiedot]
                        (let [id (or (:id rivin-tiedot) (swap! uusi-id dec))
                              vanhat-tiedot @muokatut
                              vanhat-virheet @virheet
                              uudet-tiedot (swap! muokatut assoc id
                                                  ((or uusi-rivi identity)
                                                    (merge rivin-tiedot {:id id :koskematon true})))]
                          (swap! historia conj [vanhat-tiedot vanhat-virheet])
                          (when-not ulkoinen-validointi?
                            (swap! virheet (fn [virheet]
                                             (let [rivin-virheet (validointi/validoi-rivi uudet-tiedot (get uudet-tiedot id) skeema)]
                                               (if (empty? rivin-virheet)
                                                 (dissoc virheet id)
                                                 (assoc virheet id rivin-virheet))))))
                          (when muutos
                            (muutos this))))
                      (hae-muokkaustila [_]
                        @muokatut)
                      (aseta-muokkaustila! [_ uusi-muokkaustila]
                        (let [vanhat-tiedot @muokatut
                              vanhat-virheet @virheet]
                          (swap! historia conj [vanhat-tiedot vanhat-virheet])
                          (reset! muokatut uusi-muokkaustila)))
                      (hae-virheet [_]
                        @virheet)
                      (nollaa-historia! [_]
                        (reset! historia []))

                      (aseta-virhe! [_ rivin-id kentta virheteksti]
                        (swap! virheet assoc-in [rivin-id kentta] [virheteksti]))
                      (poista-virhe! [_ rivin-id kentta]
                        (swap! virheet
                               (fn [virheet]
                                 (let [virheet (update-in virheet [rivin-id] dissoc kentta)]
                                   (if (empty? (get virheet rivin-id))
                                     (dissoc virheet rivin-id)
                                     virheet)))))

                      (muokkaa-rivit! [this funktio args]
                        ;; Käytetään annettua funktiota päivittämään data niin, että mapissa olevat avaimet
                        ;; viittaavat aina samaan päivitettyyn riviin
                        (let [avain-rivi-parit (map (fn [avain]
                                                      (-> [avain (get @muokatut avain)]))
                                                    (keys @muokatut))
                              rivit (map second avain-rivi-parit)
                              uudet-rivit (apply funktio rivit args)
                              uudet-avain-rivi-parit (map-indexed
                                                       (fn [index pari]
                                                         (-> [(first pari) (nth uudet-rivit index)]))
                                                       avain-rivi-parit)
                              uudet-rivit (reduce (fn [mappi pari]
                                                    (assoc mappi (first pari) (second pari)))
                                                  {}
                                                  uudet-avain-rivi-parit)]
                          (reset! muokatut uudet-rivit)))

                      (vetolaatikko-auki? [_ id]
                        (@vetolaatikot-auki id))
                      (avaa-vetolaatikko! [_ id]
                        (swap! vetolaatikot-auki conj id))
                      (sulje-vetolaatikko! [_ id]
                        (swap! vetolaatikot-auki disj id))))

        ;; Tekee yhden muokkauksen säilyttäen undo historian
        muokkaa! (fn [muokatut virheet skeema id funktio & argumentit]
                   (let [vanhat-tiedot @muokatut
                         vanhat-virheet @virheet
                         uudet-tiedot
                         (swap! muokatut
                                (fn [muokatut]
                                  (update-in muokatut [id]
                                             (fn [rivi]
                                               (let [uusi-rivi (apply funktio (dissoc rivi :koskematon) argumentit)]
                                                 (when uusi-rivi
                                                   (if virheet-dataan?
                                                     (assoc uusi-rivi
                                                            :harja.ui.grid/virheet (validointi/validoi-rivi
                                                                                     (assoc muokatut id uusi-rivi)
                                                                                     uusi-rivi
                                                                                     skeema))
                                                     uusi-rivi)))))))]

                     (when-not (= vanhat-tiedot uudet-tiedot)
                       (swap! historia conj [vanhat-tiedot vanhat-virheet])
                       (when-not ulkoinen-validointi?
                         (swap! virheet (fn [virheet]
                                          (let [uusi-rivi (get uudet-tiedot id)
                                                rivin-virheet (when-not (:poistettu uusi-rivi)
                                                                (validointi/validoi-rivi uudet-tiedot uusi-rivi skeema))]
                                            (if (empty? rivin-virheet)
                                              (dissoc virheet id)
                                              (assoc virheet id rivin-virheet)))))))
                     (when muutos
                       (muutos (ohjaus-fn muokatut virheet skeema)))))

        ;; Peruu yhden muokkauksen
        peru! (fn [muokatut virheet skeema]
                (when-not (empty? @virheet)
                  (let [[muok virh] (peek @historia)]
                    (reset! muokatut muok)
                    (reset! virheet virh))
                  (swap! historia pop)
                  (when muutos
                    (muutos (ohjaus-fn muokatut virheet skeema)))))
        lisaa-meta-fn (fn [muokatut-arvot virheet-arvot]
                        (let [jarjestetyt-arvot (into (sorted-map)
                                                      (kasketty-jarjestys {:virheet-ylos-fn (if virheet-ylos?
                                                                                              #(nil? (get virheet-arvot (:id %)))
                                                                                              (fn [_] nil))
                                                                           :jarjesta-kun-kasketaan jarjesta-kun-kasketaan
                                                                           :muokatut muokatut-arvot
                                                                           :muokatut-atom muokatut}))]
                          (reset! muokatut (with-meta jarjestetyt-arvot
                                             (:arvot @meta-atom)))
                          (swap! meta-atom :paivita? false)))]
    (r/create-class

      {:reagent-render
       (fn [{:keys [otsikko tallenna jarjesta jarjesta-avaimen-mukaan voi-muokata? voi-lisata? voi-kumota?
                    rivi-klikattu rivinumerot? muokkaa-footer muokkaa-aina uusi-rivi tyhja
                    vetolaatikot uusi-id paneelikomponentit validoi-aina? disabloi-rivi? jarjesta-kun-kasketaan
                    nayta-virheet? valiotsikot virheet-ylos? virhe-viesti toimintonappi-fn] :as opts} skeema muokatut]
         (let [nayta-virheet? (or nayta-virheet? :aina)
               skeema (skeema/laske-sarakkeiden-leveys
                        (filterv some? skeema))
               colspan (inc (count skeema))
               ohjaus (ohjaus-fn muokatut virheet skeema)
               voi-muokata? (if (nil? voi-muokata?)
                              true
                              voi-muokata?)
               nykyinen-fokus @fokus
               valiotsikot (or valiotsikot {})]
           (when-let [ohj (:ohjaus opts)]
             (aseta-grid ohj ohjaus))

           [:div.panel.panel-default.livi-grid.livi-muokkaus-grid
            {:class (str (str/join " " luokat)
                         (if voi-muokata? " nappeja"))
             :id (:id opts)}
            (muokkauspaneeli {:otsikko otsikko :voi-muokata? voi-muokata? :historia historia
                              :voi-kumota? voi-kumota? :muokatut muokatut :virheet virheet-atom
                              :skeema skeema :voi-lisata? voi-lisata? :ohjaus ohjaus :uusi-id uusi-id
                              :opts opts :paneelikomponentit paneelikomponentit :peru! peru!
                              :virhe-viesti virhe-viesti})
            [:div.panel-body
             [:table.grid
              [:thead
               [:tr
                (if rivinumerot? [:th {:width "40px"} " "])
                (map-indexed
                  (fn [i {:keys [otsikko leveys nimi tasaa]}]
                    ^{:key (str i nimi)}
                    [:th.rivinumero {:width (or leveys "5%")
                                     :class (y/tasaus-luokka tasaa)} otsikko]) skeema)
                (when-not piilota-toiminnot?
                  [:th.toiminnot {:width "40px"} " "])]]

              (gridin-runko {:muokatut muokatut :skeema skeema :tyhja tyhja
                             :virheet virheet-atom :valiotsikot valiotsikot
                             :rivinumerot? rivinumerot? :ohjaus ohjaus
                             :vetolaatikot vetolaatikot :nayta-virheet? nayta-virheet?
                             :nykyinen-fokus nykyinen-fokus :peru! peru!
                             :disabloi-rivi? disabloi-rivi? :jarjesta-kun-kasketaan jarjesta-kun-kasketaan
                             :fokus fokus :voi-muokata? voi-muokata? :muokkaa! muokkaa!
                             :piilota-toiminnot? piilota-toiminnot? :voi-poistaa? voi-poistaa?
                             :jarjesta jarjesta :jarjesta-avaimen-mukaan jarjesta-avaimen-mukaan
                             :vetolaatikot-auki vetolaatikot-auki :virheet-ylos? virheet-ylos?
                             :toimintonappi-fn (or toimintonappi-fn (constantly nil))
                             :meta-atom meta-atom})]
             (when (and (not= false voi-muokata?) muokkaa-footer)
               [muokkaa-footer ohjaus])]]))
       :component-did-mount (fn [this]
                              (when jarjesta-kun-kasketaan
                                (lisaa-meta-fn @muokatut @virheet-atom)))
       :component-did-update (fn [this [_ _ _ muokatut]]
                               (when (:paivita? @meta-atom)
                                 (lisaa-meta-fn @muokatut @virheet-atom)))})))