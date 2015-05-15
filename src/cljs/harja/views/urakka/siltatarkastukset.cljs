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

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.siltatarkastukset :as siltatarkastukset]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.sillat :as sillat]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.kartta :as kartta]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [harja.asiakas.tapahtumat :as tapahtumat])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


;; Tällä hetkellä valittu toteuma
(defonce valittu-silta (atom nil))
(defonce uusi-tarkastus (atom false))

(defn sillan-perustiedot [silta]
  [:div [:h3 (:siltanimi silta)]
   [yleiset/tietoja {}
    "Sillan numero:" (:siltanro silta)
    "Edellinen tarkastus" (if (:tarkastusaika silta)
                            (pvm/pvm (:tarkastusaika silta))
                            "Ei tietoa")
    "Tarkastaja" (if (:tarkastaja silta)
                   (:tarkastaja silta)
                   "Ei tietoa")]])
  
(defonce klikatun-sillan-popup
  (tapahtumat/kuuntele! :silta-klikattu
                        (fn [{:keys [klikkaus-koordinaatit] :as silta}]
                          (kartta/nayta-popup! klikkaus-koordinaatit
                                               [:span
                                                [sillan-perustiedot silta]
                                                [:div.keskita
                                                 [:a {:href "#" :on-click #(reset! valittu-silta (dissoc silta :aihe :klikkaus-koordinaatit))}
                                                  "Avaa valittu silta"]]]))))
(defn sillat [ur]
  (let [urakan-sillat sillat/sillat]
    (komp/luo
     {:component-will-mount (fn [_]
                              (kartta-tasot/taso-paalle! :sillat))
      :component-will-unmount (fn [_]
                                (kartta-tasot/taso-pois! :sillat))}
      
      (fn [ur]
        [:div.sillat
         [grid/grid
          {:otsikko        "Sillat"
           :tyhja          (if (nil? @urakan-sillat) [ajax-loader "Urakan alueella olevia siltoja haetaan..."] "Urakan alueella ei ole siltoja.")
           :rivi-klikattu #(reset! valittu-silta %)
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
    "-"))

(defn siltatarkastuksen-sarakkeet [valittu-tarkastus muut-tarkastukset]
  ;; fixme: sarakkeiden prosentuaaliset leveydet saatava vektorin pituuden mukaan skaalautuvaksi?
  (into []
        (concat
          [{:otsikko "#" :nimi :kohdenro  :tyyppi :string :muokattava? (constantly false) :leveys "5%"}  
           {:otsikko "Kohde" :nimi :kohde  :tyyppi :string :muokattava? (constantly false) :leveys "40%"}  
           {:otsikko       (str "Tulos " (pvm/vuosi (:tarkastusaika valittu-tarkastus))) :nimi :tulos :leveys "15%"
            :tyyppi        :valinta :valinta-arvo identity
            :valinta-nayta #(if (nil? %) "-" (kohdetuloksen-teksti %))
            :valinnat      ["A" "B" "C" "D"]
            :fmt           #(kohdetuloksen-teksti %)}
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys "20%"}]
          (mapv (fn [tarkastus]
                  {:otsikko (pvm/vuosi (:tarkastusaika tarkastus))
                   :nimi    (pvm/pvm (:tarkastusaika tarkastus))
                   :leveys "5%"
                   :tyyppi :string :muokattava? (constantly false)})
                muut-tarkastukset))))

(defn paivita-siltatarkastuksen-kohteet! [ur siltatarkastus-id tarkastuskohteet tallennettava]
  (go (let [muuttuneet-kohteet (<! (siltatarkastukset/paivita-siltatarkastuksen-kohteet! ur siltatarkastus-id tallennettava))
            kohteet-jotka-eivat-muuttuneet (filter #(not (= (:siltatarkastus %) siltatarkastus-id))
                                                   @tarkastuskohteet)
            uudet-tarkastuskohteet (into []
                                         (apply merge
                                                kohteet-jotka-eivat-muuttuneet
                                                muuttuneet-kohteet))]
        (reset! tarkastuskohteet uudet-tarkastuskohteet))))

(defn siltatarkastusten-rivit
  [valittu-tarkastus muut-tarkastukset kohteet]
  (ryhmittele-sillantarkastuskohteet
    (mapv (fn [kohdenro]
            (merge
              {:kohdenro  kohdenro
               :kohde     (siltatarkastukset/siltatarkastuskohteen-nimi kohdenro)
               :tulos     (:tulos (first (filter #(and
                                                       (= kohdenro (:kohde %))
                                                       (= (:siltatarkastus %) (:id valittu-tarkastus))) kohteet)))
               :lisatieto (:lisatieto (first (filter #(and
                                            (= kohdenro (:kohde %))
                                            (= (:siltatarkastus %) (:id valittu-tarkastus))) kohteet)))}
              (into {}
                    (map (fn [tarkastus]
                          [(pvm/pvm (:tarkastusaika tarkastus))
                           (:tulos (first (filter #(and
                                                    (= kohdenro (:kohde %))
                                                    (= (:siltatarkastus %) (:id tarkastus))) kohteet)))])
                         muut-tarkastukset))))
          (range 1 25))))

(defn poista-siltatarkastus! [ur silta tarkastus tarkastukset-atomi]
  (log "poista-tarkastus")
  (go (let [res (<! (siltatarkastukset/poista-siltatarkastus! ur silta tarkastus))]
        (log "tarkastukset-atomi before" (pr-str @tarkastukset-atomi))
        (reset! tarkastukset-atomi res)
        (log "tarkastukset-atomi after" (pr-str @tarkastukset-atomi))
        (log "res" (pr-str res)))))

(defn sillan-tarkastukset [ur]
  (let [sillan-tarkastukset (atom nil)
        valittu-tarkastus (atom nil)
        tarkastuskohteet (atom nil)
        urakka (atom nil)
        hae-tarkastustiedot (fn [ur]
                              (reset! urakka ur)
                              (go (let [tarkastukset (<! (siltatarkastukset/hae-sillan-tarkastukset (:id @valittu-silta)))
                                        val-tarkastus (first tarkastukset)
                                        kohteet (<! (siltatarkastukset/hae-siltatarkastusten-kohteet (mapv #(:id %) tarkastukset)))]
                                    (reset! sillan-tarkastukset tarkastukset)
                                    (reset! valittu-tarkastus val-tarkastus)
                                    (reset! tarkastuskohteet kohteet))))
        muut-tarkastukset (reaction (let [kaikki @sillan-tarkastukset]
                                      (when @valittu-tarkastus (filter #(not (= (:tarkastusaika %) (:tarkastusaika @valittu-tarkastus))) kaikki))))
        siltatarkastussarakkeet (reaction (when @valittu-tarkastus (siltatarkastuksen-sarakkeet @valittu-tarkastus @muut-tarkastukset)))
        siltatarkastusrivit (reaction (let [tark @valittu-tarkastus
                                            muut @muut-tarkastukset
                                            kohteet @tarkastuskohteet]
                                        (when tark (siltatarkastusten-rivit tark muut kohteet))))
        tallennus-kaynnissa (atom false)]

    (hae-tarkastustiedot ur)
    (tarkkaile! "rivit "siltatarkastusrivit)

    (log "SILTA" (pr-str @valittu-silta))
    (komp/luo
      {:component-will-receive-props
       (fn [_ & [_ ur]]
         (hae-tarkastustiedot ur)
         (log "sillan-tarkastukset sai propertyjä, urakka: " (pr-str (dissoc ur :alue))))}

      (fn [ur]
        [:div.sillat
         [:button.nappi-toissijainen {:on-click #(reset! valittu-silta nil)
                                      :style {:display "block"}}
          (ikonit/chevron-left) " Takaisin siltaluetteloon"]

         [sillan-perustiedot @valittu-silta]

         [:div.siltatarkastus-kontrollit
          [:div.label-ja-alasveto
           [:span.alasvedon-otsikko "Tarkastus"]
           [livi-pudotusvalikko {:valinta    @valittu-tarkastus
                                 ;;\u2014 on väliviivan unikoodi
                                 :format-fn  #(if % (str (pvm/pvm (:tarkastusaika %))) "Valitse")
                                 :valitse-fn #(reset! valittu-tarkastus %)
                                 :class      "suunnittelu-alasveto"
                                 }
            @sillan-tarkastukset]]

          [:button.nappi-kielteinen {:on-click #(poista-siltatarkastus! (:id ur) (:id @valittu-silta)
                                                                        (:id @valittu-tarkastus) sillan-tarkastukset)}
           (ikonit/trash) " Poista tarkastus"]
          [:button.nappi-toissijainen {:on-click #(swap! uusi-tarkastus not)}
           (ikonit/plus) " Uusi tarkastus"]]

         [grid/grid
          {:otsikko        "Sillan tarkastukset"
           :tyhja          (if (nil? @sillan-tarkastukset) [ajax-loader "Urakan alueella olevia siltoja haetaan..."] "Sillasta ei ole vielä tarkastuksia Harjassa.")
           :tunniste       :kohdenro
           :tallenna       (istunto/jos-rooli-urakassa istunto/rooli-urakanvalvoja
                                                       (:id ur)
                                                       #(paivita-siltatarkastuksen-kohteet! ur
                                                                                            (:id @valittu-tarkastus)
                                                                                            tarkastuskohteet %)
                                                       :ei-mahdollinen)
           }

          ;; sarakkeet
          @siltatarkastussarakkeet

          @siltatarkastusrivit
          ]]))))

(defn uuden-tarkastuksen-syottaminen [ur]
  [:div.uusi-siltatarkastus
   "Uusi siltatarkastus"] )

(defn siltatarkastukset [ur]
 (if @uusi-tarkastus
   [uuden-tarkastuksen-syottaminen ur]
   (if-let [vs @valittu-silta]
    [sillan-tarkastukset ur]
    [sillat ur])))
