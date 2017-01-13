(ns harja.views.urakka.laadunseuranta.siltatarkastukset
  "Urakan 'Siltatarkastukset' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.modal :as modal]
            [harja.ui.tierekisteri :refer [tieosoite]]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta.siltatarkastukset :as st]
            [harja.tiedot.sillat :as sillat]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.kartta :as kartta]
            [harja.ui.lomake :as lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [cljs.core.async :refer [<! >! chan]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.ui.napit :as napit]
            [harja.domain.siltatarkastus :as siltadomain]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.kartta :as kartta-tiedot])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(defonce muokattava-tarkastus (atom nil))
(def +valitse-tulos+ "- Valitse tulos -")
(def +ei-kirjattu+ "Ei kirjattu")

(def siltatarkastuksen-valiotsikot
  {1 (grid/otsikko "Alusrakenne")
   4 (grid/otsikko "Päällysrakenne")
   11 (grid/otsikko "Varusteet ja laitteet")
   20 (grid/otsikko "Siltapaikan rakenteet")})

(defn- uusi-tarkastus! [muut-tarkastukset]
  (let [kohteet
        ;; Haetaan kaikki kohteet, jotka ovat joskus olleet "EI päde"
        ;; ja merkitään ne myös uuteen
        (reduce (fn [kohteet numero]
                          (assoc kohteet numero ["-" nil]))
                        {}
                        (mapcat (fn [tarkastus]
                                  (map first
                                       (filter #(= "-" (first (second %)))
                                               (:kohteet tarkastus))))
                                muut-tarkastukset))]
    (reset! muokattava-tarkastus
            (assoc (st/uusi-tarkastus (:id @st/valittu-silta) (:id @nav/valittu-urakka))
                   :kohteet kohteet))))

(defn- muokkaa-tarkastusta! [tarkastus]
  (reset! muokattava-tarkastus tarkastus))

(defn tarkastuksen-tekija-ja-aika [silta-tai-tarkastus]
  (let [tarkastuksia? (or (> (count @st/valitun-sillan-tarkastukset) 0)
                          ;; popup tarvii tätä vaikkei silta olisi valittuna
                          (and (:tarkastusaika silta-tai-tarkastus)
                               (:tarkastaja silta-tai-tarkastus)))
        aika (if (:tarkastusaika silta-tai-tarkastus)
               (pvm/pvm (:tarkastusaika silta-tai-tarkastus))
               "Ei tietoa tarkastusajasta")
        tarkastaja (if (:tarkastaja silta-tai-tarkastus)
                     (:tarkastaja silta-tai-tarkastus)
                     "Ei tietoa tarkastajasta")]
    (if tarkastuksia?
      (str aika " (" tarkastaja ")")
      "Ei tarkastuksia")))

(defn paivita-valittu-silta []
  (let [silta @st/valittu-silta
        silta-id (:id silta)
        edellinen-tarkastus (first @st/valitun-sillan-tarkastukset)
        paivitetty-silta
        (assoc silta
          :tarkastusaika (:tarkastusaika edellinen-tarkastus)
          :tarkastaja (:tarkastaja edellinen-tarkastus))]
    (reset! st/valittu-silta paivitetty-silta)
    (sillat/paivita-silta! silta-id (constantly paivitetty-silta))))

(defn sillan-perustiedot [silta]
  [:div [:h3 (:siltanimi silta)]
   [yleiset/tietoja {}
    "Sillan tunnus: " (:siltatunnus silta)
    "Edellinen tarkastus: " (tarkastuksen-tekija-ja-aika silta)
   "Tieosoite: " [tieosoite
                     (:tr_numero silta) (:tr_alkuosa silta) (:tr_alkuetaisyys silta)
                     (:tr_loppuosa silta) (:tr_loppuetaisyys silta)]]])

(defn kohdesarake [kohteet vika-korjattu]
  [:ul.puutekohdelista {:style {:padding-left "20px"}}
   (for [[kohde [tulos _]] (seq kohteet)]
     ^{:key kohde}
     [:li.puutekohde {:style {:list-style-type "circle"}}
      (str (siltadomain/siltatarkastuskohteen-nimi kohde)
        ": "
        tulos (when vika-korjattu " \u2192 A"))])])

(defn jarjesta-sillat [sillat]
  (sort-by
    (fn [silta]
      (let [siltatunnus (:siltatunnus silta)
            siltatunnus-numerona (js/parseInt
                                   (apply str
                                          (filter #(#{\0,\1,\2,\3,\4,\5,\6,\7,\8,\9} %) siltatunnus)))]
        siltatunnus-numerona))
    sillat))

(defn sillat []
  (let [urakan-sillat sillat/sillat-kartalla]
    (komp/luo
     (komp/sisaan-ulos
      #(kartta-tiedot/kasittele-infopaneelin-linkit!
        {:silta {:toiminto (fn [silta]
                             (reset! st/valittu-silta silta))
                 :teksti "Avaa valittu silta"}})
      #(kartta-tiedot/kasittele-infopaneelin-linkit! nil))
      (fn []
        [:div.sillat
         [kartta/kartan-paikka]
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Siltojen hakuehto"]

          [livi-pudotusvalikko {:valinta    @sillat/listaus
                                ;;\u2014 on väliviivan unikoodi
                                :format-fn  #(case %
                                              :kaikki "Kaikki"
                                              :urakan-korjattavat "Urakan korjattavat (B-C)"
                                              :urakassa-korjatut "Urakassa korjatut (ei enää B:tä eikä C:tä)"
                                              :korjaus-ohjelmoitava "Korjaus ohjelmoitava (D)"
                                              "Kaikki")
                                :valitse-fn #(reset! sillat/listaus %)}
           [:kaikki :urakan-korjattavat :urakassa-korjatut :korjaus-ohjelmoitava]]]
         [grid/grid
          {:otsikko       "Sillat"
           :tyhja         (if (nil? @urakan-sillat)
                            [ajax-loader "Siltoja haetaan..."]
                            "Ei siltoja annetuilla kriteereillä.")
           :rivi-klikattu #(reset! st/valittu-silta %)
           :tunniste      :siltatunnus}
          ;; sarakkeet
          [{:otsikko "Silta" :nimi :siltanimi :leveys 35}
           {:otsikko "Silta\u00ADtunnus" :nimi :siltatunnus :leveys 13}
           {:otsikko "Edellinen tarkastus" :nimi :tarkastusaika :tyyppi :pvm :fmt #(if % (pvm/pvm %)) :leveys 20}
           {:otsikko "Tarkastaja" :nimi :tarkastaja :leveys 30}
           (when-let [listaus (some #{:urakan-korjattavat :urakassa-korjatut :korjaus-ohjelmoitava}
                                    [@sillat/listaus])]
             {:otsikko (case listaus
                         :urakan-korjattavat "Korjattavat"
                         :urakassa-korjatut "Korjatut"
                         :korjaus-ohjelmoitava  "Ohjelmoitavat")
              :nimi :kohteet :leveys 30
              :fmt (fn [kohteet]
                     (case listaus
                       :urakassa-korjatut [kohdesarake kohteet true]
                       [kohdesarake kohteet]))})]
          (jarjesta-sillat @urakan-sillat)]]))))

(defn ryhmittele-sillantarkastuskohteet
  "Ryhmittelee sillantarkastuskohteet"
  [kohderivit]
  (mapcat (fn [{nro :kohdenro :as rivi}]
            (if-let [otsikko (siltatarkastuksen-valiotsikot nro)]
              [otsikko rivi]
              [rivi]))
          kohderivit))

(defn kohdetuloksen-teksti [kirjain]
  (case kirjain
    "A" "A - ei toimenpiteitä"
    "B" "B - puhdistettava"
    "C" "C - urakan kunnostettava"
    "D" "D - korjaus ohjelmoitava"
    "-" "Ei päde tähän siltaan"
    +ei-kirjattu+))

(defn tarkastustulos-ja-liitteet
  "Komponentti vanhan tarkastuksen tuloksen ja liitteiden näyttämiselle. Liite on ikoni, jota klikkaamalla
   liite avataan. Jos liitteitä on useita, aukeaa lista tarkastukselle lisätyistä liitteistä."
  [tulos liitteet]
  (let [lista-auki? (atom false)]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! lista-auki? false))
      (fn [tulos liitteet]
        [:div.siltatarkastus-tulos-ja-liite
         [:div.tulos (str tulos " ")]
         [:div.liite
          (cond (= (count liitteet) 1)
                [liitteet/liite-linkki (first liitteet) (ikonit/file)]
                (> (count liitteet) 1)
                [:a.klikattava
                 {:on-click (fn []
                              (swap! lista-auki? not))}
                 (ikonit/file)])]
         (when @lista-auki?
           [liitteet/liitteet-listalla liitteet])]))))

(defn muut-tarkastukset-sarakkeet [muut-tarkastukset]
  (mapv (fn [tarkastus]
          {:otsikko (pvm/vuosi (:tarkastusaika tarkastus))
           :nimi (pvm/pvm (:tarkastusaika tarkastus))
           :leveys 2.5
           :tasaa :keskita
           :tyyppi :komponentti
           :komponentti (fn [rivi]
                          (let [sarake-arvo (get rivi (pvm/pvm (:tarkastusaika tarkastus)))
                                tulos (:tulos sarake-arvo)
                                liitteet (:liitteet sarake-arvo)]
                            [tarkastustulos-ja-liitteet tulos liitteet]))})
        muut-tarkastukset))

(defn siltatarkastuksen-sarakkeet [muut-tarkastukset]
  (into []
        (concat
          [{:otsikko "#" :nimi :kohdenro  :tyyppi :string :muokattava? (constantly false) :leveys 3}
           {:otsikko "Kohde" :nimi :kohde  :tyyppi :string :muokattava? (constantly false) :leveys 15}
           {:otsikko "Tulos " :nimi :tulos :leveys 10 :fmt kohdetuloksen-teksti
            :tyyppi :string}
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys 10}
           {:otsikko "Liitteet" :nimi :liitteet :tyyppi :komponentti :leveys 3
            :komponentti (fn [rivi]
                           [liitteet/liitteet-ikoneina (:liitteet rivi)])}]
          (muut-tarkastukset-sarakkeet muut-tarkastukset))))

(defn tallenna-siltatarkastus!
  "Ottaa tallennettavan tarkastuksen, jossa tarkastustietojen lisäksi mahdollinen uusi-liite ja urakka-id"
  [tarkastus]
  (go (let [res (<! (st/tallenna-siltatarkastus! tarkastus))
            olemassaolleet-tarkastukset @st/valitun-sillan-tarkastukset
            kaikki-tarkastukset (reverse (sort-by :tarkastusaika (merge olemassaolleet-tarkastukset res)))]
        (reset! muokattava-tarkastus nil)
        (reset! st/valitun-sillan-tarkastukset kaikki-tarkastukset)
        (reset! st/valittu-tarkastus res)
        (paivita-valittu-silta))))

(defn siltatarkastusten-rivit
  [valittu-tarkastus muut-tarkastukset]
  (ryhmittele-sillantarkastuskohteet
    (mapv (fn [kohdenro]
            (merge
              ;; Valittu tarkastus
              {:kohdenro kohdenro
               :kohde (siltadomain/siltatarkastuskohteen-nimi kohdenro)
               :tulos (first (get (:kohteet valittu-tarkastus) kohdenro))
               :lisatieto (second (get (:kohteet valittu-tarkastus) kohdenro))
               :liitteet (filterv #(= (:kohde %) kohdenro)
                                  (:liitteet valittu-tarkastus))}
              ;; Muut tarkastukset (avain on tarkastuspvm ja arvo mappi, jossa tulos ja liitteet)
              (into {}
                    (map (fn [tarkastus]
                          [(pvm/pvm (:tarkastusaika tarkastus))
                           {:tulos (first (get (:kohteet tarkastus) kohdenro))
                            :liitteet (filterv #(= (:kohde %) kohdenro)
                                               (:liitteet tarkastus))}])
                         muut-tarkastukset))))
          (range 1 25))))

(defn poista-siltatarkastus! []
  (go (let [silta @st/valittu-silta
            tarkastus @st/valittu-tarkastus
            res (<! (st/poista-siltatarkastus! (:id silta) (:id tarkastus)))]
        (reset! st/valitun-sillan-tarkastukset res)
        (paivita-valittu-silta))))

(defn varmista-siltatarkastuksen-poisto []
  (modal/nayta! {:otsikko "Sillan tarkastuksen poistaminen"
                 :footer  [:span
                           [:button.nappi-toissijainen {:type     "button"
                                                        :on-click #(do (.preventDefault %)
                                                                       (modal/piilota!))}
                            "Peruuta"]
                           [:button.nappi-kielteinen {:type     "button"
                                                      :on-click #(do (.preventDefault %)
                                                                     (modal/piilota!)
                                                                     (poista-siltatarkastus!))}
                            "Poista tarkastus"]
                           ]}
                [:div "Haluatko varmasti poistaa sillalle "
                 [:b (str (:siltanimi @st/valittu-silta) " (tunnus " (:siltatunnus @st/valittu-silta)
                          ") " (pvm/pvm (:tarkastusaika @st/valittu-tarkastus)))]
                 " tehdyn tarkastuksen?"]))

(defn- tarkastus-kuuluu-nykyiseen-urakkaan? [tarkastus nykyinen-urakka-id]
  (= (:urakka tarkastus) nykyinen-urakka-id))

(defn sillan-tarkastukset []
  (komp/luo
    (fn []
      (let [muut-tarkastukset (reaction (let [kaikki @st/valitun-sillan-tarkastukset
                                              aika (:tarkastusaika @st/valittu-tarkastus)]
                                          (when aika
                                            (filter #(not (= (:tarkastusaika %) aika)) kaikki))))
            siltatarkastussarakkeet (reaction (let [muut @muut-tarkastukset]
                                                (siltatarkastuksen-sarakkeet muut)))
            siltatarkastusrivit (reaction (let [tarkastukset @st/valitun-sillan-tarkastukset
                                                tark @st/valittu-tarkastus
                                                muut @muut-tarkastukset]
                                            (when tarkastukset
                                              (if tark
                                               (siltatarkastusten-rivit tark muut)
                                               []))))
            tarkastus-kuuluu-urakkaan? (tarkastus-kuuluu-nykyiseen-urakkaan? @st/valittu-tarkastus
                                                                             (:id @nav/valittu-urakka))]
        [:div.siltatarkastukset
         [napit/takaisin "Takaisin siltaluetteloon" #(reset! st/valittu-silta nil)]

         [sillan-perustiedot @st/valittu-silta]

         [:div.siltatarkastus-kontrollit
          [:div.label-ja-alasveto.alasveto-sillan-tarkastaja
           [:span.alasvedon-otsikko "Tarkastus"]
           [livi-pudotusvalikko {:valinta    @st/valittu-tarkastus
                                 ;;\u2014 on väliviivan unikoodi
                                 :format-fn  #(tarkastuksen-tekija-ja-aika %)
                                 :valitse-fn #(reset! st/valittu-tarkastus %)}
            @st/valitun-sillan-tarkastukset]]

          [:span (when (not tarkastus-kuuluu-urakkaan?) {:title "Tarkastus ei kuulu tähän urakkaan."})
           [:button.nappi-ensisijainen {:on-click #(muokkaa-tarkastusta! @st/valittu-tarkastus)
                                       :disabled (or (empty? @siltatarkastusrivit)
                                                     (not tarkastus-kuuluu-urakkaan?))}
           (ikonit/livicon-pen) " Muokkaa tarkastusta"]]
          [:span (when (not tarkastus-kuuluu-urakkaan?) {:title "Tarkastus ei kuulu tähän urakkaan."})
           [:button.nappi-kielteinen {:on-click varmista-siltatarkastuksen-poisto
                                     :disabled (or (empty? @siltatarkastusrivit)
                                                   (not tarkastus-kuuluu-urakkaan?))}
           (ikonit/livicon-trash) " Poista tarkastus"]]
          [napit/uusi "Uusi tarkastus" #(uusi-tarkastus! (conj @muut-tarkastukset
                                                               @st/valittu-tarkastus))]]

         [grid/grid
          {:otsikko (if @st/valittu-tarkastus
                      (str "Sillan tarkastus "
                           (pvm/pvm (:tarkastusaika @st/valittu-tarkastus))
                           " (" (:tarkastaja @st/valittu-tarkastus) ")")
                      "Sillan tarkastus")
           :tyhja (if (nil? @siltatarkastusrivit)
                    [ajax-loader "Ladataan..."]
                    "Sillasta ei ole tarkastuksia Harjassa")
           :piilota-toiminnot? true
           :tunniste :kohdenro
           :voi-lisata? false
           :voi-poistaa? (constantly false)}

          ;; sarakkeet
          @siltatarkastussarakkeet

          @siltatarkastusrivit]]))))

(defn tarkastuksen-muokkauslomake
  "Lomake uuden tarkastuksen luomiseen ja vanhan muokkaamiseen"
  [muokattava-tarkastus]
  (let [tallennus-kaynnissa (atom false)
        muut-tarkastukset @st/valitun-sillan-tarkastukset

        olemassa-olevat-tarkastus-pvmt
        (reaction (into #{}
                        (map :tarkastusaika)
                        @st/valitun-sillan-tarkastukset))
        otsikko (if-not (:id @muokattava-tarkastus)
                  "Luo uusi siltatarkastus"
                  (str "Muokkaa tarkastusta " (pvm/pvm (:tarkastusaika @muokattava-tarkastus))))
        uudet-liitteet (atom nil)
        alkuperainen-tarkastusaika (:tarkastusaika @muokattava-tarkastus)]
    (fn [muokattava-tarkastus]
      (let [tarkastus @muokattava-tarkastus
            tarkastusrivit (dissoc
                            (into {}
                                  (map (juxt :kohdenro identity))
                                  (siltatarkastusten-rivit tarkastus muut-tarkastukset))
                            nil)
            taulukon-rivit (r/wrap
                            tarkastusrivit
                            #(swap! muokattava-tarkastus
                                    assoc :kohteet
                                    (fmap (juxt :tulos :lisatieto) %)))
            riveja (count (vals tarkastusrivit))
            riveja-taytetty (count (filter #(not (nil? (:tulos %)))
                                           (vals tarkastusrivit)))
            ainakin-yksi-tulos? (> riveja-taytetty 0)
            voi-tallentaa? (and (lomake/voi-tallentaa? tarkastus)
                                ainakin-yksi-tulos?)]
        [:div.uusi-siltatarkastus
         [napit/takaisin "Palaa tallentamatta" #(reset! muokattava-tarkastus nil)]

         [lomake {:otsikko otsikko
                  :muokkaa! (fn [uusi]
                              (reset! muokattava-tarkastus uusi))}
          [{:otsikko "Silta" :nimi :siltanimi :hae (fn [_] (:siltanimi @st/valittu-silta)) :muokattava? (constantly false)}
           {:otsikko "Sillan tunnus" :nimi :siltatunnus :hae (fn [_] (:siltatunnus @st/valittu-silta)) :muokattava? (constantly false)}
           {:otsikko "Tarkastus pvm" :nimi :tarkastusaika :pakollinen? true
            :tyyppi :pvm
            :validoi [[:ei-tyhja "Anna tarkastuksen päivämäärä"]
                      #(when (and (@olemassa-olevat-tarkastus-pvmt %1)
                                  (not= %1 alkuperainen-tarkastusaika))
                        "Tälle päivälle on jo kirjattu tarkastus.")]
            :huomauta [[:urakan-aikana]]}
           ;; maksimipituus tarkastajalle tietokannassa varchar(128)
           {:otsikko "Tarkastaja" :nimi :tarkastaja :pakollinen? true
            :tyyppi :string :pituus-max 128
            :validoi [[:ei-tyhja "Anna tarkastajan nimi"]]}]
          tarkastus]

         [grid/muokkaus-grid
          {:otsikko      otsikko
           :tunniste :kohdenro
           :piilota-toiminnot? true
           :voi-lisata?  false
           :voi-poistaa? (constantly false)
           :jarjesta :kohdenro
           :valiotsikot siltatarkastuksen-valiotsikot}

          ;; sarakkeet
          (into [{:otsikko "#" :nimi :kohdenro :tyyppi :string :muokattava? (constantly false)
                  :leveys 2}
                 {:otsikko "Kohde" :nimi :kohde :tyyppi :string :muokattava? (constantly false)
                  :leveys 15}]

                (concat
                 (for [tulos ["A" "B" "C" "D" "-"]]
                   {:otsikko (if (= tulos "-")
                               (ikonit/ban-circle)
                               tulos)
                    :tasaa :keskita
                    :nimi (str "tulos-" tulos) :leveys 2
                    :tyyppi :radio
                    :valinnat [tulos]
                    :valinta-nayta (constantly "")
                    :hae :tulos
                    :aseta #(assoc %1 :tulos %2)})
                 [{:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys 10
                   :pituus-max 255}
                  {:otsikko "Liitteet" :nimi :liitteet :tyyppi :komponentti :leveys 5
                   :komponentti (fn [rivi]
                                  [liitteet/liitteet
                                   (:id @nav/valittu-urakka)
                                   (:liitteet @taulukon-rivit)
                                   {:uusi-liite-atom (r/wrap nil
                                                       (fn [uusi-arvo]
                                                         (reset! uudet-liitteet
                                                                 (assoc @uudet-liitteet (:kohdenro rivi) uusi-arvo))))
                                    :grid? true}])}]
                 (muut-tarkastukset-sarakkeet muut-tarkastukset)))
          taulukon-rivit]

         ;; tarkista montako kohdetta jolla tulos. Jos alle 24, näytä herja
         [:button.nappi-ensisijainen
          {:class (when @tallennus-kaynnissa "disabled")
           :disabled (not voi-tallentaa?)
           :on-click
           #(do (.preventDefault %)
                (reset! tallennus-kaynnissa true)
                (go (let [tallennettava-tarkastus (-> tarkastus
                                                      (assoc :uudet-liitteet @uudet-liitteet)
                                                      (assoc :urakka-id (:id @nav/valittu-urakka)))
                          res (<! (tallenna-siltatarkastus! tallennettava-tarkastus))]
                      (if (k/virhe? res)
                        ;; Epäonnistui jostain syystä
                        (do
                          (viesti/nayta! "Tallentaminen epäonnistui" :warning viesti/viestin-nayttoaika-lyhyt)
                          (reset! tallennus-kaynnissa false))
                        ;; Tallennus ok
                        (do (viesti/nayta! "Siltatarkastus tallennettu")
                            (reset! tallennus-kaynnissa false)
                            (reset! muokattava-tarkastus nil))))))}
          (ikonit/tallenna) " Tallenna tarkastus"]
         (let [vinkki (if (= 24 riveja-taytetty)
                        (str "Kaikki " riveja-taytetty "/" riveja " kohdetta arvioitu.")
                        (if (and ainakin-yksi-tulos? (< riveja-taytetty 24))
                          (str "Vasta " riveja-taytetty "/" riveja " kohdetta arvioitu. Voit tallentaa lomakkeen myös keskeneräisenä.")
                          (when (= 0 riveja-taytetty)
                            "Arvioi vähintään yksi kohde ennen tallentamista.")))]
           [:span.napin-vinkki
            vinkki])]))))

(defn siltatarkastukset []

  (komp/luo
    (komp/sisaan-ulos #(do
                        (kartta-tasot/taso-paalle! :sillat)
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :L))
                      #(do
                        (kartta-tasot/taso-pois! :sillat)
                        (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)))
    (fn []
      (if @muokattava-tarkastus
        [tarkastuksen-muokkauslomake muokattava-tarkastus]
        (if-let [vs @st/valittu-silta]
          [sillan-tarkastukset vs]
          [sillat])))))
