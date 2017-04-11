(ns harja.ui.muokkausgrid
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
            [schema.core :as s :include-macros true]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.grid-yhteiset :refer [Grid aseta-grid vetolaatikko-rivi lisaa-rivi!
                                            vetolaatikko-rivi vetolaatikon-tila]]
            [harja.ui.ikonit :as ikonit]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.makrot :refer [fnc]]))

(defn muokkaus-grid
  "Versio gridistä, jossa on vain muokkaustila. Tilan tulee olla muokkauksen vaatimassa {<id> <tiedot>} array mapissa.
  Tiedot tulee olla atomi tai wrapatty data, jota tietojen muokkaus itsessään manipuloi.

  Optiot on mappi optioita:
  :id                 grid pääelementin DOM id
  :muokkaa-footer     optionaalinen footer komponentti joka muokkaustilassa näytetään, parametrina Grid ohjauskahva
  :muutos             jos annettu, kaikista gridin muutoksista tulee kutsu tähän funktioon.
                      Parametrina Grid ohjauskahva
  :uusi-rivi          jos annettu uuden rivin tiedot käsitellään tällä funktiolla
  :voi-muokata?       jos false, tiedot eivät ole muokattavia ollenkaan
  :voi-lisata?        jos false, uusia rivejä ei voi lisätä
  :voi-kumota?        jos false, kumoa-nappia ei näytetä
  :voi-poistaa?       funktio, joka palauttaa true tai false.
  :rivinumerot?       Lisää ylimääräisen sarakkeen, joka listaa rivien numerot alkaen ykkösestä
  :jarjesta           jos annettu funktio, sortataan rivit tämän mukaan
  :paneelikomponentit vector funktioita, jotka palauttavat komponentteja. Näytetään paneelissa.
  :piilota-toiminnot? boolean, piilotetaan toiminnot sarake jos true
  :luokat             Päätason div-elementille annettavat lisäkuokat (vectori stringejä)
  :virheet            atomi gridin virheitä {rivinid {:kentta (\"virhekuvaus\")}}, jos ei anneta
                      luodaan sisäisesti atomi virheille
  :uusi-id            seuraavan uuden luotavan rivin id, jos ei anneta luodaan uusia id:tä
                      sarjana -1, -2, -3, ...
  :nayta-virheet?     :aina (oletus) tai :fokus.
                      Jos fokus, näytetään virheviesti vain fokusoidulle kentälle,
                      virheen indikoiva punainen viiva näytetään silti aina.

  :valiotsikot        mäppäys rivin tunnisteesta, jota ennen otsikko tulee näyttää, Otsikkoon
  :ulkoinen-validointi? jos true, grid ei tee validointia muokkauksen yhteydessä.
                        Käytä tätä, jos teet validoinnin muualla (esim jos grid data on wrap,
                        jonka muutoksen yhteydessä validointi tehdään).

  :virheet-dataan?    jos true, validointivirheet asetetaan rivin datan mäppiin
                      avaimella :harja.ui.grid/virheet"
  [{:keys [otsikko tyhja tunniste voi-poistaa? rivi-klikattu rivinumerot? voi-kumota?
           voi-muokata? voi-lisata? jarjesta piilota-toiminnot? paneelikomponentit
           muokkaa-footer muutos uusi-rivi luokat ulkoinen-validointi? virheet-dataan?] :as opts}
   skeema muokatut]
  (let [uusi-id (atom 0) ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])
        virheet-atom (or (:virheet opts) (atom {})) ;; validointivirheet: (:id rivi) => [virheet]
        vetolaatikot-auki (or (:vetolaatikot-auki opts)
                              (atom #{}))
        fokus (atom nil)
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
                                                 (if virheet-dataan?
                                                   (assoc uusi-rivi
                                                     ::virheet (validointi/validoi-rivi
                                                                 (assoc muokatut id uusi-rivi)
                                                                 uusi-rivi
                                                                 skeema))
                                                   uusi-rivi))))))]

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
                    (muutos (ohjaus-fn muokatut virheet skeema)))))]

    (r/create-class
      {:reagent-render
       (fn [{:keys [otsikko tallenna jarjesta voi-poistaa? voi-muokata? voi-lisata? voi-kumota?
                    rivi-klikattu rivinumerot? muokkaa-footer muokkaa-aina uusi-rivi tyhja
                    vetolaatikot uusi-id paneelikomponentit validoi-aina?
                    nayta-virheet? valiotsikot] :as opts} skeema muokatut]
         (let [nayta-virheet? (or nayta-virheet? :aina)
               virheet (or (:virheet opts) virheet-atom)
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
            [:div.panel-heading
             (when otsikko [:h6.panel-title otsikko])
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
                               paneelikomponentit))])]
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

              [:tbody
               (let [muokatut-atom muokatut
                     muokatut @muokatut
                     colspan (inc (count skeema))]
                 (if (every? :poistettu (vals muokatut))
                   [:tr.tyhja [:td {:colSpan colspan} tyhja]]
                   (let [kaikki-virheet @virheet]
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
                                       [
                                        ^{:key (str i id)}
                                        [:tr.muokataan {:class (str (if (even? (+ i 1))
                                                                      "parillinen"
                                                                      "pariton"))}
                                         (if rivinumerot? [:td.rivinumero (+ i 1)])
                                         (doall
                                           (map-indexed
                                             (fn [j {:keys [nimi hae aseta fmt muokattava? tyyppi tasaa
                                                            komponentti] :as s}]
                                               (if (= :vetolaatikon-tila tyyppi)
                                                 ^{:key (str "vetolaatikontila" id)}
                                                 [vetolaatikon-tila ohjaus vetolaatikot id]
                                                 (let [s (assoc s :rivi rivi)
                                                       arvo (if hae
                                                              (hae rivi)
                                                              (get rivi nimi))
                                                       tasaus-luokka (y/tasaus-luokka tasaa)
                                                       kentan-virheet (get rivin-virheet nimi)]
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

                                                      (if (= tyyppi :komponentti)
                                                        (komponentti rivi {:index i
                                                                           :muokataan? true})
                                                        (if voi-muokata?
                                                          [tee-kentta (assoc s :on-focus #(reset! fokus [i nimi]))
                                                           (r/wrap
                                                             arvo
                                                             (fn [uusi]
                                                               (if aseta
                                                                 (muokkaa! muokatut-atom virheet skeema
                                                                           id (fn [rivi]
                                                                                (aseta rivi uusi)))
                                                                 (muokkaa! muokatut-atom virheet skeema
                                                                           id assoc nimi uusi))))]
                                                          [nayta-arvo (assoc s :index i :muokataan? false)
                                                           (vain-luku-atomina arvo)]))]

                                                     ^{:key (str j nimi)}
                                                     [:td {:class (str "ei-muokattava " tasaus-luokka)}
                                                      ((or fmt str) (if hae
                                                                      (hae rivi)
                                                                      (get rivi nimi)))])))) skeema))
                                         (when-not piilota-toiminnot?
                                           [:td.toiminnot
                                            (when (and (not= false voi-muokata?)
                                                       (or (nil? voi-poistaa?) (voi-poistaa? rivi)))
                                              [:span.klikattava {:on-click
                                                                 #(do (.preventDefault %)
                                                                      (muokkaa! muokatut-atom
                                                                                virheet skeema
                                                                                id assoc
                                                                                :poistettu true))}
                                               (ikonit/livicon-trash)])
                                            (when-not (empty? rivin-virheet)
                                              [:span.rivilla-virheita
                                               (ikonit/livicon-warning-sign)])])]

                                        (vetolaatikko-rivi vetolaatikot vetolaatikot-auki id colspan)]))))
                           (if jarjesta
                             (sort-by (comp jarjesta second) (seq muokatut))
                             (seq muokatut))))))))]]
             (when (and (not= false voi-muokata?) muokkaa-footer)
               [muokkaa-footer ohjaus])]]))})))