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
              vetolaatikko-rivi vetolaatikon-tila validoi-grid]]
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

(defn ei-muokattava-elementti
  [tasaus-luokka fmt arvo]
  (let [fmt (or fmt str)
        arvo (fmt arvo)]
    [:td {:class (str "ei-muokattava " tasaus-luokka)}
     arvo]))

(defn- muokkauselementin-tila
  [{:keys [aseta nimi valinta-arvo hae]}
   {:keys [muokkaa! muokatut-atom virheet skeema id i rivi gridin-tietoja]}
   rivi-disabloitu? kentan-virheet
   tulevat-elementit]
  (let [grid-tilan-muokkaus-fn (atom (fn [uusi]
                                       (if aseta
                                         (muokkaa! muokatut-atom virheet skeema
                                                   id (fn [rivi]
                                                        (aseta rivi uusi)))
                                         (muokkaa! muokatut-atom virheet skeema
                                                   id assoc nimi uusi))))
        data-muokkaus-fn (atom (fn [uusi]
                                 (let [uusi (if valinta-arvo
                                              (valinta-arvo uusi)
                                              uusi)]
                                   (@grid-tilan-muokkaus-fn uusi))))
        arvo-atom (atom ((or hae #(get % nimi)) rivi))
        fokus? (atom false)
        fokus-elementille #(reset! fokus? true)
        fokus-pois-elementilta #(let [uusi-fokusoitu-komponentti (.-relatedTarget %)
                                      fokusoidun-komponentin-nimi (when uusi-fokusoitu-komponentti
                                                                    (.getAttribute uusi-fokusoitu-komponentti "data-komponentin-nimi"))]
                                  (when-not (#{"tayta-alas-div" "tayta-alas-nappi"} fokusoidun-komponentin-nimi)
                                    (reset! fokus? false)))
        tayta-alas-key (str (gensym "tayta"))
        kentan-key (str (gensym "kentta"))
        seuranta-avain (keyword (str (gensym (str i nimi))))
        this-node (atom nil)
        virhelaatikon-max-koko (atom nil)
        virhelaatikon-max-koon-asetus (fn [_]
                                        (when-let [grid-node (:grid-node @gridin-tietoja)]
                                          (reset! virhelaatikon-max-koko (- (-> grid-node .-offsetWidth)
                                                                            (.-offsetLeft @this-node)))))]
    (r/create-class
      {:display-name "Muokkauselementin-tila"
       :component-will-mount (fn [this]
                               (add-watch arvo-atom seuranta-avain
                                          (fn [_ _ _ uusi-arvo]
                                            (@grid-tilan-muokkaus-fn uusi-arvo))))
       :component-did-mount (fn [this]
                              (reset! this-node (r/dom-node this))
                              (.addEventListener js/window EventType/RESIZE virhelaatikon-max-koon-asetus)
                              (virhelaatikon-max-koon-asetus nil))
       :component-will-unmount (fn [& _]
                                 (.removeEventListener js/window EventType/RESIZE virhelaatikon-max-koon-asetus)
                                 (remove-watch arvo-atom seuranta-avain))
       :component-will-receive-props (fn [this new-argv]
                                       (let [[_ {vanha-valinta-arvo :valinta-arvo}
                                              {vanha-skeema :skeema vanha-rivi :rivi} _ _] (r/argv this)
                                             {:keys [aseta nimi valinta-arvo hae kentta-arity-3?]} (nth new-argv 1)
                                             {:keys [muokkaa! muokatut-atom virheet rivi skeema id]} (nth new-argv 2)
                                             hae-fn (or hae #(get % nimi))]
                                         (when (and (not kentta-arity-3?)
                                                    (not= (hae-fn rivi) (hae-fn vanha-rivi)))
                                           (reset! arvo-atom (hae-fn rivi)))
                                         (when (not= skeema vanha-skeema)
                                           (reset! grid-tilan-muokkaus-fn
                                                   (fn [uusi]
                                                     (if aseta
                                                       (muokkaa! muokatut-atom virheet skeema
                                                                 id (fn [rivi]
                                                                      (aseta rivi uusi)))
                                                       (muokkaa! muokatut-atom virheet skeema
                                                                 id assoc nimi uusi)))))
                                         (when (not= valinta-arvo vanha-valinta-arvo)
                                           (reset! data-muokkaus-fn
                                                   (fn [uusi]
                                                     (let [uusi (if valinta-arvo
                                                                  (valinta-arvo uusi)
                                                                  uusi)]
                                                       (@grid-tilan-muokkaus-fn uusi)))))))
       :reagent-render
       (fn [{:keys [nimi aseta fmt muokattava? tyyppi tasaa
                    komponentti hae kentta-arity-3? komponentti-args] :as sarake}
            {:keys [ohjaus id rivi rivi-index gridin-tietoja
                    nayta-virheet? i voi-muokata? disable-input?
                    muokatut-atom muokkaa! virheet skeema]}
            rivi-disabloitu? kentan-virheet tulevat-elementit]
         (let [sarake (assoc sarake :rivi rivi)
               hae-fn (or hae #(get % nimi))
               arvo (hae-fn rivi)
               tasaus-luokka (y/tasaus-luokka tasaa)
               tayta-alas (:tayta-alas? sarake)]
           (if (or (nil? muokattava?) (muokattava? rivi i))
             [:td {:class (str "muokattava "
                               tasaus-luokka
                               (when-not (empty? kentan-virheet)
                                 " sisaltaa-virheen"))}
              (when (and (not (empty? kentan-virheet))
                         (case nayta-virheet?
                           :fokus @fokus?
                           :aina true))
                [virheen-ohje kentan-virheet :virhe {:virheet-ulos? true
                                                     :max-width @virhelaatikon-max-koko}])

              (cond
                ;; Mikäli rivi on disabloitu, piirretään erikseen määritelty sisältö, jos se on annettu...
                (and rivi-disabloitu? (:sisalto-kun-rivi-disabloitu sarake))
                ((:sisalto-kun-rivi-disabloitu sarake) rivi i)

                ;; ... tai mikäli sisältöä ei ole määritelty, eikä ole erikseen sallittu muokkausta, ei piirretä mitään
                (and rivi-disabloitu? (not (:salli-muokkaus-rivin-ollessa-disabloituna? sarake)))
                nil

                (= tyyppi :komponentti) (apply komponentti rivi {:index i :muokataan? true} komponentti-args)
                (= tyyppi :reagent-komponentti) (vec (concat [komponentti rivi {:index i :muokataan? true}]
                                                             komponentti-args))
                (or voi-muokata?
                    disable-input?) [:span.grid-kentta-wrapper (when tayta-alas {:style {:position "relative"}})

                                     (when (and tayta-alas voi-muokata?)
                                       ^{:key tayta-alas-key}
                                       [grid-yleiset/tayta-alas-nappi {:fokus? @fokus? :fokus-atom fokus?
                                                                       :arvo arvo :tayta-alas tayta-alas
                                                                       :rivi-index rivi-index
                                                                       :tulevat-elementit tulevat-elementit
                                                                       :sarake sarake :ohjaus ohjaus :rivi rivi}])
                                     (if kentta-arity-3?
                                       ^{:key kentan-key}
                                       [tee-kentta (assoc sarake :on-focus fokus-elementille
                                                          :on-blur fokus-pois-elementilta
                                                          :disabled? (not voi-muokata?))
                                        arvo
                                        @data-muokkaus-fn]
                                       ^{:key kentan-key}
                                       [tee-kentta (assoc sarake :on-focus fokus-elementille
                                                          :on-blur fokus-pois-elementilta
                                                          :disabled? (not voi-muokata?))
                                        arvo-atom])]
                :else [nayta-arvo (assoc sarake :index i :muokataan? false)
                       (vain-luku-atomina arvo)])]
             [ei-muokattava-elementti tasaus-luokka fmt arvo])))})))

(defn- muokkauselementti [{:keys [tyyppi hae nimi] :as sarake}
                          {:keys [ohjaus vetolaatikot id tulevat-rivit i] :as elementin-asetukset}
                          rivi-disabloitu? kentan-virheet tulevat-elementit]
  (let [elementin-asetukset (dissoc elementin-asetukset :vetolaatikot :tulevat-rivit)]
    (if (= :vetolaatikon-tila tyyppi)
      ^{:key (str "vetolaatikontila" id)}
      [vetolaatikon-tila ohjaus vetolaatikot id]
      ^{:keys (str i "-" nimi)}
      [muokkauselementin-tila sarake elementin-asetukset rivi-disabloitu? kentan-virheet tulevat-elementit])))

(defn- muokkausrivi [{:keys [rivinumerot? ohjaus vetolaatikot id rivi rivi-index
                             nayta-virheet? i voi-muokata? tulevat-rivit
                             muokatut-atom muokkaa! virheet piilota-toiminnot? skeema
                             disabloi-rivi?
                             voi-poistaa? toimintonappi-fn] :as rivi-asetukset}]
  (let [rivi-disabloitu? (and disabloi-rivi? (disabloi-rivi? rivi))]
    [:tr.muokataan {:class (str (if (even? (+ i 1))
                                  "parillinen "
                                  "pariton ")
                                (when rivi-disabloitu? "disabloitu-rivi"))}
     (when rivinumerot? [:td.rivinumero (+ i 1)])
     (doall
       (map-indexed
         (fn [j {:keys [nimi hae tayta-alas?] :as sarake}]
           (let [haku-fn  #(get % nimi)
                 kentan-virheet (-> @virheet (get id) haku-fn)
                 elementin-asetukset (select-keys rivi-asetukset #{:ohjaus :vetolaatikot :id :rivi :rivi-index
                                                                   :nayta-virheet? :i :voi-muokata?
                                                                   :muokatut-atom :muokkaa! :disable-input?
                                                                   :virheet :skeema :gridin-tietoja})
                 hae (or hae #(get % nimi))
                 tulevat-elementit (when tayta-alas?
                                     (map hae tulevat-rivit))]
             ^{:key (str j nimi)}
             [muokkauselementti sarake elementin-asetukset rivi-disabloitu? kentan-virheet tulevat-elementit]))
         skeema))
     (when-not piilota-toiminnot?
       (let [rivin-virheet (get @virheet id)]
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
             (ikonit/livicon-warning-sign)])]))]))

(defn- kasketty-jarjestys [{:keys [virheet-ylos-fn jarjesta-kun-kasketaan muokatut muokatut-atom]}]
  (sort-by (juxt #(virheet-ylos-fn (second %)) #(jarjesta-kun-kasketaan %)) (seq muokatut)))

(defn- gridin-runko [asetukset]
  (let [gridin-tietoja (atom nil)]
    (r/create-class
     {:display-name "muokkausgridin-runko"
      :component-did-mount (fn [this]
                             (swap! gridin-tietoja assoc :grid-node (r/dom-node this)))
      :reagent-render
      (fn [{:keys [muokatut skeema tyhja virheet valiotsikot ohjaus vetolaatikot disable-input?
                   nayta-virheet? rivinumerot? voi-muokata? jarjesta-kun-kasketaan rivin-avaimet
                   disabloi-rivi? muokkaa! piilota-toiminnot? voi-poistaa? jarjesta jarjesta-avaimen-mukaan
                   vetolaatikot-auki virheet-ylos? toimintonappi-fn tyhja-komponentti? tyhja-args]}]
        (let [muokatut-atom muokatut
              muokatut @muokatut
              colspan (inc (count skeema))]
          [:tbody
           (if (every? :poistettu (vals muokatut))
             [:tr.tyhja [:td {:colSpan colspan}
                         (if tyhja-komponentti?
                           (vec (cons tyhja tyhja-args))
                           tyhja)]]
             (let [kaikki-virheet @virheet
                   virheet-ylos-fn (if virheet-ylos?
                                     #(nil? (get kaikki-virheet (:id %)))
                                     (fn [_] nil))
                   kasketty-jarjestamaan? (and jarjesta-kun-kasketaan (:jarjesta-gridissa (meta muokatut)))
                   jarjestetty-data (cond
                                      jarjesta (sort-by (comp (juxt virheet-ylos-fn jarjesta) second) (seq muokatut))
                                      jarjesta-avaimen-mukaan (sort-by (comp (juxt virheet-ylos-fn jarjesta-avaimen-mukaan) first) (seq muokatut))
                                      kasketty-jarjestamaan? (kasketty-jarjestys {:virheet-ylos-fn virheet-ylos-fn :jarjesta-kun-kasketaan jarjesta-kun-kasketaan :muokatut muokatut :muokatut-atom muokatut-atom})
                                      (and jarjesta-kun-kasketaan (not (:jarjesta-gridissa (meta muokatut)))) (sort-by (fn [[i rivi]]
                                                                                                                         (conj ((juxt virheet-ylos-fn) rivi) i))
                                                                                                                       (seq muokatut))
                                      :else (seq muokatut))
                   jarjestetty-data (if rivin-avaimet
                                      (map (fn [[i rivi]]
                                             [i (select-keys rivi rivin-avaimet)])
                                           jarjestetty-data)
                                      jarjestetty-data)]
               (doall
                (loop [i 0
                       [rivi-indeksineen & loput-rivit] jarjestetty-data
                       tulevat-rivit (map second loput-rivit)
                       muokkausrivit []]
                  (if-not rivi-indeksineen
                    muokkausrivit
                    (if (-> rivi-indeksineen second :poistettu)
                      (recur (inc i)
                             loput-rivit
                             (map second (rest loput-rivit))
                             muokkausrivit)
                      (let [[id rivi] rivi-indeksineen
                            otsikko (valiotsikot id)
                            muokkausrivi (doall
                                          (into (if otsikko
                                                  [^{:key (str "otsikko" i)}
                                                   [:tr.otsikko
                                                    [:td {:colSpan colspan}
                                                     (:teksti otsikko)]]]
                                                  [])
                                                [^{:key (str i "-" id)}
                                                 [muokkausrivi {:rivinumerot? rivinumerot? :ohjaus ohjaus
                                                                :vetolaatikot vetolaatikot :id id :rivi rivi
                                                                :nayta-virheet? nayta-virheet? :disable-input? disable-input?
                                                                :i i :voi-muokata? voi-muokata?
                                                                :tulevat-rivit tulevat-rivit :rivi-index i
                                                                :muokatut-atom muokatut-atom :muokkaa! muokkaa!
                                                                :disabloi-rivi? disabloi-rivi?
                                                                :virheet virheet :piilota-toiminnot? piilota-toiminnot?
                                                                :skeema skeema :voi-poistaa? voi-poistaa?
                                                                :toimintonappi-fn toimintonappi-fn
                                                                :gridin-tietoja gridin-tietoja}]
                                                 ^{:key (str i "-" id "veto")}
                                                 [vetolaatikko-rivi vetolaatikot vetolaatikot-auki id colspan]]))]
                        (recur (inc i)
                               loput-rivit
                               (map second (rest loput-rivit))
                               (concat muokkausrivit muokkausrivi)))))))))]))})))

(defn- gridin-otsikot
  [skeema rivinumerot? piilota-toiminnot?]
  [:thead
   [:tr
    (if rivinumerot? [:th {:width "40px"} " "])
    (map-indexed
      (fn [i {:keys [otsikko yksikko leveys nimi tasaa]}]
        ^{:key (str i nimi)}
        [:th.rivinumero {:width (or leveys "5%")
                         :class (y/tasaus-luokka tasaa)} otsikko (when yksikko
                                                                   [:span.kentan-yksikko yksikko])]) skeema)
    (when-not piilota-toiminnot?
      [:th.toiminnot {:width "40px"} " "])]])

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
  :virhe-viesti                   String, joka näytetään gridin otsikon oikealla puolella punaisella.
  :luomisen-jalkeen               Funktio, joka ajetaan heti taulukon luomisen jälkeen. Saa argumentikseen Gridin tilan
  :muokkauspaneeli?               Tämä on sitä varten, ettei gridin päälle jää tyhjää tilaa muokkauspaneelista laittamalla
                                  tämä falseksi.
  :rivin-avaimet                  Set, joka sisältää ne rivin avaimet, jotka otetaan rivikenttään. Hyödyllinen, jos
                                  on useampi grid saman atomin alla. Tämän avulla voi renderöidä vain tarvittavaa gridiä."
  [{:keys [otsikko yksikko tyhja tunniste voi-poistaa? rivi-klikattu rivinumerot? voi-kumota? jarjesta-kun-kasketaan
           voi-muokata? voi-lisata? jarjesta jarjesta-avaimen-mukaan piilota-toiminnot? paneelikomponentit
           muokkaa-footer muutos uusi-rivi luokat ulkoinen-validointi? virheet-dataan? virheet-ylos? validoi-alussa?
           virhe-viesti toimintonappi-fn disabloi-rivi? luomisen-jalkeen muokkauspaneeli? rivi-validointi taulukko-validointi] :as opts}
   skeema muokatut]
  (let [uusi-id (atom 0)                                    ;; tästä dekrementoidaan aina uusia id:tä
        historia (atom [])
        virheet-atom (or (:virheet opts) (atom {}))         ;; validointivirheet: (:id rivi) => [virheet]
        vetolaatikot-auki (or (:vetolaatikot-auki opts)
                              (atom #{}))
        meta-atom (atom nil)
        hoida-taulukkotason-virheet (fn [uudet-tiedot virheet]
                                      (let [taulukon-virheet (validointi/validoi-taulukko uudet-tiedot skeema taulukko-validointi)
                                            edelliset-taulukkotason-virheiden-rivit (:taulukkovirheiden-rivit @meta-atom)
                                            virheet (reduce-kv (fn [m rivi-indeksi vanhat-rivin-taulukkotason-virheet]
                                                                 (update m rivi-indeksi (fn [rivin-virheet]
                                                                                          (reduce-kv (fn [m2 sarakkeen-nimi virheet-vektori]
                                                                                                       (let [virheet (remove (fn [virheviesti]
                                                                                                                               (some #(= virheviesti %)
                                                                                                                                     (sarakkeen-nimi vanhat-rivin-taulukkotason-virheet)))
                                                                                                                             virheet-vektori)]
                                                                                                         (if-not (empty? virheet)
                                                                                                           (assoc m2 sarakkeen-nimi virheet)
                                                                                                           m2)))
                                                                                                     {} rivin-virheet))))
                                                               virheet edelliset-taulukkotason-virheiden-rivit)]
                                        (swap! meta-atom assoc :taulukkovirheiden-rivit taulukon-virheet)
                                        (reduce-kv (fn [m rivi-indeksi uudet-rivin-taulukkotason-virheet]
                                                     (update m rivi-indeksi (fn [rivin-virheet]
                                                                              (reduce-kv (fn [m2 sarakkeen-nimi virhe-vektori]
                                                                                           (update m2 sarakkeen-nimi concat virhe-vektori))
                                                                                         rivin-virheet uudet-rivin-taulukkotason-virheet))))
                                                   virheet taulukon-virheet)))
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
                                             (let [rivin-virheet (validointi/validoi-rivin-kentat uudet-tiedot (get uudet-tiedot id) (if rivi-validointi
                                                                                                                               (conj skeema {::validointi/rivi-validointi rivi-validointi})
                                                                                                                               skeema))
                                                   virheet (if (empty? rivin-virheet)
                                                             (dissoc virheet id)
                                                             (assoc virheet id rivin-virheet))]
                                               (if taulukko-validointi
                                                 (hoida-taulukkotason-virheet uudet-tiedot virheet)
                                                 virheet)))))
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
                        (let [avain-rivi-parit (sort-by first (seq @muokatut))
                              rivit (map second avain-rivi-parit)
                              uudet-rivit (apply funktio rivit args)
                              uudet-avain-rivi-parit (map-indexed
                                                       (fn [i [avain _]]
                                                         [avain (nth uudet-rivit i)])
                                                       avain-rivi-parit)
                              uudet-rivit (into {} uudet-avain-rivi-parit)]
                          (reset! muokatut uudet-rivit)))

                      (vetolaatikko-auki? [_ id]
                        (@vetolaatikot-auki id))
                      (avaa-vetolaatikko! [_ id]
                        (swap! vetolaatikot-auki conj id))
                      (sulje-vetolaatikko! [_ id]
                        (swap! vetolaatikot-auki disj id))
                      (validoi-grid [_]
                        (let [gridin-tiedot @muokatut
                              rivi-virheet (into {}
                                                 (keep (fn [[id rivin-tiedot]]
                                                         (let [rivin-virheet (when-not (:poistettu rivin-tiedot)
                                                                               (validointi/validoi-rivin-kentat gridin-tiedot rivin-tiedot (if rivi-validointi
                                                                                                                                     (conj skeema {::validointi/rivi-validointi rivi-validointi})
                                                                                                                                     skeema)))]
                                                           (when-not (empty? rivin-virheet)
                                                             [id rivin-virheet])))
                                                       gridin-tiedot))
                              kaikki-virheet (if taulukko-validointi
                                               (hoida-taulukkotason-virheet gridin-tiedot rivi-virheet)
                                               rivi-virheet)]
                          (reset! virheet kaikki-virheet)))))

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
                                                     ;; TODO taulukko-validointi tällekkin
                                                     (assoc uusi-rivi
                                                            :harja.ui.grid/virheet (validointi/validoi-rivin-kentat
                                                                                     (assoc muokatut id uusi-rivi)
                                                                                     uusi-rivi
                                                                                     (if rivi-validointi
                                                                                       (conj skeema {::validointi/rivi-validointi rivi-validointi})
                                                                                       skeema)))
                                                     uusi-rivi)))))))]
                     (when-not (= vanhat-tiedot uudet-tiedot)
                       (swap! historia conj [vanhat-tiedot vanhat-virheet])
                       (when-not ulkoinen-validointi?
                         (swap! virheet (fn [virheet]
                                          (let [uusi-rivi (get uudet-tiedot id)
                                                rivin-virheet (when-not (:poistettu uusi-rivi)
                                                                (validointi/validoi-rivin-kentat uudet-tiedot uusi-rivi (if rivi-validointi
                                                                                                                  (conj skeema {::validointi/rivi-validointi rivi-validointi})
                                                                                                                  skeema)))
                                                virheet (if (empty? rivin-virheet)
                                                          (dissoc virheet id)
                                                          (assoc virheet id rivin-virheet))]
                                            (if taulukko-validointi
                                              (hoida-taulukkotason-virheet uudet-tiedot virheet)
                                              virheet))))))
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
                          (swap! meta-atom assoc :paivita? false)))
        muokkauspaneeli? (if (some? muokkauspaneeli?) muokkauspaneeli? true)
        virheet (or (:virheet opts) virheet-atom)
        ohjaus (atom (ohjaus-fn muokatut virheet skeema))
        nil-fn (constantly nil)]
    (when validoi-alussa?
      (validoi-grid @ohjaus))
    (r/create-class

      {:display-name "Muokkausgrid"
       :reagent-render
       (fn [{:keys [otsikko yksikko tallenna jarjesta jarjesta-avaimen-mukaan voi-muokata? voi-lisata? voi-kumota?
                    rivi-klikattu rivinumerot? muokkaa-footer muokkaa-aina uusi-rivi tyhja tyhja-komponentti? tyhja-args
                    vetolaatikot uusi-id paneelikomponentit disabloi-rivi? jarjesta-kun-kasketaan rivin-avaimet disable-input?
                    nayta-virheet? valiotsikot virheet-ylos? virhe-viesti toimintonappi-fn data-cy] :as opts} skeema muokatut]
         (let [nayta-virheet? (or nayta-virheet? :aina)
               skeema (skeema/laske-sarakkeiden-leveys
                        (filterv some? skeema))
               colspan (inc (count skeema))
               ohjaus @ohjaus
               voi-muokata? (if (nil? voi-muokata?)
                              true
                              voi-muokata?)
               valiotsikot (or valiotsikot {})]
           (when-let [ohj (:ohjaus opts)]
             (aseta-grid ohj ohjaus))

           [:div.panel.panel-default.livi-grid.livi-muokkaus-grid
            (merge
              {:class (str (str/join " " luokat)
                           (if voi-muokata? " nappeja"))
               :id (:id opts)}
              (when data-cy
                {:data-cy data-cy}))
            (when muokkauspaneeli?
              [muokkauspaneeli {:otsikko otsikko :voi-muokata? voi-muokata? :historia historia
                                :voi-kumota? voi-kumota? :muokatut muokatut :virheet virheet-atom
                                :skeema skeema :voi-lisata? voi-lisata? :ohjaus ohjaus :uusi-id uusi-id
                                :opts opts :paneelikomponentit paneelikomponentit :peru! peru!
                                :virhe-viesti virhe-viesti}])
            [:div.panel-body
             [:table.grid
              [gridin-otsikot skeema rivinumerot? piilota-toiminnot?]
              [gridin-runko {:muokatut muokatut :skeema skeema :tyhja tyhja
                             :virheet virheet :valiotsikot valiotsikot :disable-input? disable-input?
                             :rivinumerot? rivinumerot? :ohjaus ohjaus
                             :vetolaatikot vetolaatikot :nayta-virheet? nayta-virheet?
                             :peru! peru! :rivin-avaimet rivin-avaimet
                             :disabloi-rivi? disabloi-rivi? :jarjesta-kun-kasketaan jarjesta-kun-kasketaan
                             :voi-muokata? voi-muokata? :muokkaa! muokkaa!
                             :piilota-toiminnot? piilota-toiminnot? :voi-poistaa? voi-poistaa?
                             :jarjesta jarjesta :jarjesta-avaimen-mukaan jarjesta-avaimen-mukaan
                             :vetolaatikot-auki vetolaatikot-auki :virheet-ylos? virheet-ylos?
                             :toimintonappi-fn (or toimintonappi-fn nil-fn) :tyhja-komponentti? tyhja-komponentti?
                             :tyhja-args tyhja-args}]]
             (when (and (not= false voi-muokata?) muokkaa-footer)
               [muokkaa-footer ohjaus])]]))
       :component-will-receive-props (fn [this new-argv]
                                       (let [old-argv (r/argv this)]
                                         (when (not= (nth new-argv 2)
                                                     (nth old-argv 2))
                                           (reset! ohjaus (ohjaus-fn muokatut virheet (nth new-argv 2))))))
       :component-did-mount (fn [this]
                              (when luomisen-jalkeen
                                (luomisen-jalkeen @muokatut)))})))
