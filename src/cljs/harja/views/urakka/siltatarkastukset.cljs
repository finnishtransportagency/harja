(ns harja.views.urakka.siltatarkastukset
  "Urakan 'Siltatarkastukset' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.modal :refer [modal] :as modal]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.siltatarkastukset :as st]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.sillat :as sillat]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.kartta :as kartta]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [harja.asiakas.tapahtumat :as tapahtumat])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(defonce uuden-syottaminen (atom false))
(def +valitse-tulos+ "- valitse tulos -")

(defn tarkastuksen-tekija-ja-aika [silta-tai-tarkastus]
  (let [tarkastuksia? (> (count @st/valitun-sillan-tarkastukset) 0)
        aika (if (:tarkastusaika silta-tai-tarkastus)
               (pvm/pvm (:tarkastusaika silta-tai-tarkastus))
               "Ei tietoa tarkastusajasta")
        tarkastaja (if (:tarkastaja silta-tai-tarkastus)
                     (:tarkastaja silta-tai-tarkastus)
                     "Ei tietoa tarkastajasta")]
    (if tarkastuksia?
      (str aika " (" tarkastaja ")")
      "Ei tarkastuksia"))
  )

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
    "Sillan numero: " (:siltanro silta)
    "Edellinen tarkastus: " (tarkastuksen-tekija-ja-aika silta)]])

(defonce klikatun-sillan-popup
  (tapahtumat/kuuntele! :silta-klikattu
                        (fn [{:keys [klikkaus-koordinaatit] :as silta}]
                          (kartta/nayta-popup! klikkaus-koordinaatit
                                               [:span
                                                [sillan-perustiedot silta]
                                                [:div.keskita
                                                 [:a {:href "#" :on-click #(reset! st/valittu-silta (dissoc silta :aihe :klikkaus-koordinaatit))}
                                                  "Avaa valittu silta"]]]))))
(defn sillat []
  (let [urakan-sillat sillat/sillat]
    (komp/luo
      (fn []
        [:div.sillat
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Siltojen hakuehto"]
          [livi-pudotusvalikko {:valinta    @sillat/listaus
                                ;;\u2014 on väliviivan unikoodi
                                :format-fn  #(case %
                                              :kaikki "Kaikki"
                                              :puutteet "Puutteelliset"
                                              :korjatut "Korjatut"
                                              "Kaikki")
                                :valitse-fn #(reset! sillat/listaus %)
                                :class      "suunnittelu-alasveto"
                                }
           [:kaikki :puutteet :korjatut]]]

         [grid/grid
          {:otsikko        "Sillat"
           :tyhja          (if (nil? @urakan-sillat) [ajax-loader "Urakan alueella olevia siltoja haetaan..."] "Urakan alueella ei ole siltoja.")
           :rivi-klikattu #(reset! st/valittu-silta %)
           }

          ;; sarakkeet
          [{:otsikko "Silta" :nimi :siltanimi :leveys "40%"}
           {:otsikko "Siltanumero" :nimi :siltanro :leveys "10%"}
           {:otsikko "Edellinen tarkastus" :nimi :tarkastusaika :tyyppi :pvm :fmt #(if % (pvm/pvm %)) :leveys "20%"}
           {:otsikko "Tarkastaja" :nimi :tarkastaja :leveys "30%"}]

          @urakan-sillat
          ]]))))



(defn ryhmittele-sillantarkastuskohteet
  "Ryhmittelee sillantarkastuskohteet"
  [kohderivit]
  (let [otsikko (fn [{:keys [kohdenro]}]
                  (case kohdenro
                    (1 2 3) "Alusrakenne"
                    (4 5 6 7 8 9 10) "Päällysrakenne"
                    (11 12 13 14 15 16 17 18 19) "Varusteet ja laitteet"
                    (20 21 22 23 24) "Siltapaikan rakenteet"
                    "Tuntematon kohdenumero."))
        otsikon-mukaan (group-by otsikko kohderivit)]
    (mapcat (fn [[otsikko rivit]]
              (concat [(grid/otsikko otsikko)] rivit))
            (seq otsikon-mukaan))))

(defn kohdetuloksen-teksti [kirjain]
  (case kirjain
    "A" "A - ei toimenpiteitä"
    "B" "B - puhdistettava"
    "C" "C - urakan kunnostettava"
    "D" "D - korjaus ohjelmoitava"
    +valitse-tulos+))

(defn siltatarkastuksen-sarakkeet [muut-tarkastukset]
  ;; fixme: sarakkeiden prosentuaaliset leveydet saatava vektorin pituuden mukaan skaalautuvaksi?
  (into []
        (concat
          [{:otsikko "#" :nimi :kohdenro  :tyyppi :string :muokattava? (constantly false) :leveys "5%"} 
           {:otsikko "Kohde" :nimi :kohde  :tyyppi :string :muokattava? (constantly false) :leveys "40%"}
           {:otsikko       "Tulos " :nimi :tulos :leveys "15%"
            :tyyppi        :valinta :valinta-arvo identity
            :valinta-nayta #(if (nil? %) +valitse-tulos+ (kohdetuloksen-teksti %))
            :valinnat      ["A" "B" "C" "D"]
            :fmt           #(kohdetuloksen-teksti %)}
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys "20%"}]
          (mapv (fn [tarkastus]
                  {:otsikko (pvm/vuosi (:tarkastusaika tarkastus))
                   :nimi    (pvm/pvm (:tarkastusaika tarkastus))
                   :leveys "5%"
                   :tyyppi :string :muokattava? (constantly false)})
                muut-tarkastukset))))

(defn paivita-siltatarkastus! [taulukon-rivit]
  (go (let [kohteet-mapissa (into {}
                                  (map (fn [rivi]
                                         [(:kohdenro rivi) [(:tulos rivi) (:lisatieto rivi)]])
                                       taulukon-rivit))
            tallennettava-tarkastus (assoc @st/valittu-tarkastus :kohteet kohteet-mapissa)
            res (<! (st/tallenna-siltatarkastus! tallennettava-tarkastus))
            muut-tarkastukset (filter (fn [tarkastus]
                                        (not (= (:id tarkastus) (:id res))))
                                      @st/valitun-sillan-tarkastukset)
            kaikki-tarkastukset (reverse (sort-by :tarkastusaika (merge muut-tarkastukset res)))]
        (reset! st/valitun-sillan-tarkastukset kaikki-tarkastukset)
        (reset! st/valittu-tarkastus res))))



(defn tallenna-uusi-siltatarkastus! [lomake taulukon-rivit]
  (go (let [kohteet-mapissa (into {}
                                  (map (fn [rivi]
                                         [(:kohdenro rivi) [(:tulos rivi) (:lisatieto rivi)]])
                                       taulukon-rivit))
            uusi-tarkastus (assoc lomake :kohteet kohteet-mapissa)
            res (<! (st/tallenna-siltatarkastus! uusi-tarkastus))
            olemassaolleet-tarkastukset @st/valitun-sillan-tarkastukset
            kaikki-tarkastukset (reverse (sort-by :tarkastusaika (merge olemassaolleet-tarkastukset res)))]
        (reset! uuden-syottaminen false)
        (reset! st/valitun-sillan-tarkastukset kaikki-tarkastukset)
        (reset! st/valittu-tarkastus res)
        (paivita-valittu-silta))))

(defn siltatarkastusten-rivit
  [valittu-tarkastus muut-tarkastukset]
  (ryhmittele-sillantarkastuskohteet
    (mapv (fn [kohdenro]
            (merge
              {:kohdenro  kohdenro
               :kohde     (st/siltatarkastuskohteen-nimi kohdenro)
               :tulos     (first (get (:kohteet valittu-tarkastus) kohdenro))
               :lisatieto (second (get (:kohteet valittu-tarkastus) kohdenro))}
              (into {}
                    (map (fn [tarkastus]
                          [(pvm/pvm (:tarkastusaika tarkastus))
                           (first (get (:kohteet tarkastus) kohdenro))])
                         muut-tarkastukset))))
          (range 1 25))))

(defn poista-siltatarkastus! []
  (go (let [silta @st/valittu-silta
            tarkastus @st/valittu-tarkastus
            res (<! (st/poista-siltatarkastus! (:id silta) (:id tarkastus)))]
        (reset! st/valitun-sillan-tarkastukset res)
        (paivita-valittu-silta))))


(defn sillan-tarkastukset []
  (let [
        muut-tarkastukset (reaction (let [kaikki @st/valitun-sillan-tarkastukset
                                          aika (:tarkastusaika @st/valittu-tarkastus)]
                                      (when aika
                                        (filter #(not (= (:tarkastusaika %) aika)) kaikki))))
        siltatarkastussarakkeet (reaction (let [muut @muut-tarkastukset]
                                              (siltatarkastuksen-sarakkeet muut)))
        siltatarkastusrivit (reaction (let [tark @st/valittu-tarkastus
                                            muut @muut-tarkastukset]
                                        (when tark (siltatarkastusten-rivit tark muut))))]

    (komp/luo
     
     (fn []
       [:div.siltatarkastukset
        [:button.nappi-toissijainen {:on-click #(reset! st/valittu-silta nil)
                                     :style {:display "block"}}
         (ikonit/chevron-left) " Takaisin siltaluetteloon"]

          [sillan-perustiedot @st/valittu-silta]

          [:div.siltatarkastus-kontrollit
          [:div.label-ja-alasveto
           [:span.alasvedon-otsikko "Tarkastus"]
           [livi-pudotusvalikko {:valinta    @st/valittu-tarkastus
                                 ;;\u2014 on väliviivan unikoodi
                                 :format-fn  #(tarkastuksen-tekija-ja-aika %)
                                 :valitse-fn #(reset! st/valittu-tarkastus %)
                                 :class      "suunnittelu-alasveto"
                                 }
            @st/valitun-sillan-tarkastukset]]

          [:button.nappi-kielteinen {:on-click
                                     (fn []
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
                                                      [:b (str (:siltanimi @st/valittu-silta) " (nro " (:siltanro @st/valittu-silta)
                                                               ") " (pvm/pvm (:tarkastusaika @st/valittu-tarkastus)))]
                                                      " tehdyn tarkastuksen?"]))}
           (ikonit/trash) " Poista tarkastus"]
          [:button.nappi-toissijainen {:on-click #(reset! uuden-syottaminen true)}
           (ikonit/plus) " Uusi tarkastus"]]

        [grid/grid
         {:otsikko      (if @st/valittu-tarkastus
                          (str "Sillan tarkastus " (pvm/pvm (:tarkastusaika @st/valittu-tarkastus)) " (" (:tarkastaja @st/valittu-tarkastus) ")")
                          "Sillan tarkastus")
          :tyhja        (if (nil? @st/valitun-sillan-tarkastukset)
                          ;[ajax-loader "Sillan tarkastuksia haetaan..."] FIXME ajax loader jää pyörimään jos ei tarkastuksia
                          "Ei vielä tarkastuksia"
                          "Sillasta ei ole vielä tarkastuksia Harjassa.")
          :tunniste     :kohdenro
          :voi-lisata?  false
          :voi-poistaa? (constantly false)
          :tallenna     (istunto/jos-rooli-urakassa istunto/rooli-urakanvalvoja
                                                    (:id @nav/valittu-urakka)
                                                    #(paivita-siltatarkastus! %)
                                                    :ei-mahdollinen)
          }

         ;; sarakkeet
         @siltatarkastussarakkeet

         @siltatarkastusrivit]]))))

(defn uuden-siltatarkastusten-rivit [uusi-tarkastus]
  (siltatarkastusten-rivit uusi-tarkastus []))


(defn uuden-tarkastuksen-syottaminen []
  (let [uusi-tarkastus (st/uusi-tarkastus (:id @st/valittu-silta) (:id @nav/valittu-urakka))
        lomakkeen-tiedot (atom (dissoc uusi-tarkastus :kohteet))
        lomake-taytetty (reaction (and
                                    (not (nil? (:tarkastusaika @lomakkeen-tiedot)))
                                    (not (str/blank? (:tarkastaja @lomakkeen-tiedot)))))
        tallennus-kaynnissa (atom false)
        taulukon-rivit (reaction
                         (uuden-siltatarkastusten-rivit uusi-tarkastus))
        taulukon-riveilla-tulos (reaction (= (count @taulukon-rivit)
                                              (count (filter #(not (nil? (:tulos %))) @taulukon-rivit))))
        g (grid/grid-ohjaus)
        lomakkeen-virheet (atom {})
        olemassa-olevat-tarkastus-pvmt
        (reaction (into #{}
                     (mapv #(:tarkastusaika %)
                       @st/valitun-sillan-tarkastukset)))
        voi-tallentaa? (reaction (and
                                   @lomake-taytetty
                                   @taulukon-riveilla-tulos
                                   (empty? @lomakkeen-virheet)))]

    (komp/luo
      (fn []
        [:div.uusi-siltatarkastus
         [:button.nappi-toissijainen {:on-click #(reset! uuden-syottaminen false)
                                      :style {:display "block"}}
          (ikonit/chevron-left) " Palaa tallentamatta"]
        [:h3 "Luo uusi siltatarkastus"]
         [lomake {:luokka   :horizontal
                  :virheet  lomakkeen-virheet
                  :muokkaa! (fn [uusi]
                              (reset! lomakkeen-tiedot uusi))}
          [{:otsikko "Silta" :nimi :siltanimi :hae (fn [_] (:siltanimi @st/valittu-silta)) :muokattava? (constantly false)}
           {:otsikko "Sillan numero" :nimi :siltanro :hae (fn [_] (:siltanro @st/valittu-silta)) :muokattava? (constantly false)}



           {:otsikko "Tarkastus pvm" :nimi :tarkastusaika :tyyppi :pvm :leveys-col 2
            :validoi [[:ei-tyhja "Anna tarkastuksen päivämäärä"]
                      #(when (@olemassa-olevat-tarkastus-pvmt %1)
                        "Tälle päivälle on jo kirjattu tarkastus.")]}
           ;; maksimipituus tarkastajalle tietokannassa varchar(128)
           {:otsikko "Tarkastaja" :nimi :tarkastaja :leveys-col 4
            :tyyppi  :string :pituus-max 128
            :validoi [[:ei-tyhja "Anna tarkastajan nimi"]]}]

          @lomakkeen-tiedot]

         [grid/grid
          {:otsikko      "Uusi sillan tarkastus"
           :tunniste     :kohdenro
           :ohjaus       g
           :muokkaa-aina true
           :voi-lisata?  false
           :voi-poistaa? (constantly false)
           :muutos       (fn [g]
                           (reset! taulukon-rivit (vals (grid/hae-muokkaustila g))))}

          ;; sarakkeet
          [{:otsikko "#" :nimi :kohdenro :tyyppi :string :muokattava? (constantly false) :leveys "5%"}
           {:otsikko "Kohde" :nimi :kohde :tyyppi :string :muokattava? (constantly false) :leveys "40%"}
           {:otsikko       "Tulos" :nimi :tulos :leveys "15%"
            :validoi       [[:ei-tyhja "Anna kohteen tulos"]]
            :tyyppi        :valinta :valinta-arvo identity
            :valinta-nayta #(if (nil? %) +valitse-tulos+ (kohdetuloksen-teksti %))
            :valinnat      ["A" "B" "C" "D"]
            :fmt           #(kohdetuloksen-teksti %)
            ; Tarjoa alaspäin kopiointia vain arvolle A - ei toimenpiteitä
            :tayta-alas?   #(= "A" %)
            :tayta-tooltip "Kopioi sama tulos seuraavillekin kohteille"
            :tayta-fn (fn [lahtorivi tama-rivi]
                                       (assoc tama-rivi :tulos (:tulos lahtorivi)))
            :kelluta-tayta-nappi true}
           ;; Lisätiedon maksimipituus tietokantasarakkeesta jonka tyyppi varchar(255)
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys "20%"
            :pituus-max 255}]

          @taulukon-rivit]

         ;; tarkista montako kohdetta jolla tulos. Jos alle 24, näytä herja
         [:button.nappi-ensisijainen
          {:class    (when @tallennus-kaynnissa "disabled")
           :disabled (not @voi-tallentaa?)
           :on-click
                     #(do (.preventDefault %)
                          (reset! tallennus-kaynnissa true)
                          (go (let [res (<! (tallenna-uusi-siltatarkastus! @lomakkeen-tiedot (vals (grid/hae-muokkaustila g))))]
                                (if res
                                  ;; Tallennus ok
                                  (do (viesti/nayta! "Siltatarkastus tallennettu")
                                      (reset! tallennus-kaynnissa false)
                                      (reset! uuden-syottaminen false))
                                  ;; Epäonnistui jostain syystä
                                  ;; fixme: pitäisköhän näyttää se käyttäjällekin ;)
                                  (reset! tallennus-kaynnissa false)))))}
           (ikonit/ok) " Tallenna tarkastus"]
         (when (not @voi-tallentaa?)
           [:span.napin-vinkki "Täytä kaikki tiedot ennen tallennusta"])]))))

(defn siltatarkastukset []

  (komp/luo
    {:component-will-mount (fn [_]
                             (kartta-tasot/taso-paalle! :sillat))
     :component-will-unmount (fn [_]
                               (kartta-tasot/taso-pois! :sillat))}

    (fn []
      (if @uuden-syottaminen
        [uuden-tarkastuksen-syottaminen]
      (if-let [vs @st/valittu-silta]
        [sillan-tarkastukset vs]
        [sillat])))))
