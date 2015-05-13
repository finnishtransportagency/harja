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

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


;; Tällä hetkellä valittu toteuma
(defonce valittu-silta (atom nil))

(defn sillat [ur]
  (let [urakan-sillat (atom nil)
        urakka (atom nil)
        aseta-urakka (fn [ur]
                       (reset! urakka ur))]
    (aseta-urakka ur)
    (run! (let [urakka-id (:id @urakka)]
            (when urakka-id
              (go (reset! urakan-sillat (<! (siltatarkastukset/hae-urakan-sillat urakka-id)))))))

    (log "URAKAN SILLAT: " (pr-str (dissoc ur :alue)))
    (komp/luo
      {:component-will-receive-props
       (fn [_ & [_ ur]]
         (log "UUSI URAKKA: " (pr-str (dissoc ur :alue)))
         (aseta-urakka ur))}

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
           {:otsikko "Edellinen tarkastus" :nimi :uusin_aika :tyyppi :pvm :fmt #(if % (pvm/pvm %)) :leveys "20%"}
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

(defn siltatarkastuksen-sarakkeet [valittu-tarkastus muut-tarkastukset]
  ;; fixme: sarakkeiden prosentuaaliset leveydet saatava vektorin pituuden mukaan skaalautuvaksi?
  (into []
        (concat
          [{:otsikko "#" :nimi :kohdenro  :tyyppi :string :muokattava? (constantly false) :leveys "5%"}  
           {:otsikko "Kohde" :nimi :kohde  :tyyppi :string :muokattava? (constantly false) :leveys "40%"}  
           {:otsikko (str "Tulos " (pvm/vuosi (:tarkastusaika valittu-tarkastus))) :nimi :tulos :leveys "10%"
            :tyyppi :valinta :valinta-arvo identity
            :valinta-nayta #(if (nil? %) "-" %)
            :valinnat ["A" "B" "C" "D"]}  
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys "20%"}]
          (mapv (fn [tarkastus]
                  {:otsikko (pvm/vuosi (:tarkastusaika tarkastus))
                   :nimi    (pvm/pvm (:tarkastusaika tarkastus))
                   :leveys "5%"
                   :tyyppi :string :muokattava? (constantly false)})
                muut-tarkastukset))))

(defn tallenna-tarkastukset [ur atomi uudet]
  (log "tallenna-tarkastukset uudet" (pr-str uudet)))

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
        muut-tarkastukset (reaction (when @valittu-tarkastus (filter #(not (= (:tarkastusaika %) (:tarkastusaika @valittu-tarkastus))) @sillan-tarkastukset)))
        siltatarkastussarakkeet (reaction (when @valittu-tarkastus (siltatarkastuksen-sarakkeet @valittu-tarkastus @muut-tarkastukset)))
        siltatarkastusrivit (reaction (when @valittu-tarkastus (siltatarkastusten-rivit @valittu-tarkastus @muut-tarkastukset @tarkastuskohteet)))
        tallennus-kaynnissa (atom false)]

    (hae-tarkastustiedot ur)

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
         [:div [:h3 (:siltanimi @valittu-silta)]
          [yleiset/tietoja {}
           "Sillan numero:" (:siltanro @valittu-silta)
           "Edellinen tarkastus" (pvm/pvm (:uusin_aika @valittu-silta))
           "Tarkastaja" (:tarkastaja @valittu-silta)]]
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Tarkastus"]
          [livi-pudotusvalikko {:valinta    @valittu-tarkastus
                                ;;\u2014 on väliviivan unikoodi
                                :format-fn  #(if % (str (pvm/pvm (:tarkastusaika %))) "Valitse")
                                :valitse-fn #(reset! valittu-tarkastus %)
                                :class      "suunnittelu-alasveto"
                                }
           @sillan-tarkastukset]]

         ;; FIXME: gridi ei läheskään valmis vielä. Käsiteltävä sillan-tarkastukset ja niiden kaikki tarkastuskohteet...
         [grid/grid
          {:otsikko        "Sillan tarkastukset"
           :tyhja          (if (nil? @sillan-tarkastukset) [ajax-loader "Urakan alueella olevia siltoja haetaan..."] "Sillasta ei ole vielä tarkastuksia Harjassa.")
           :tunniste       :kohdenro
           :tallenna       (istunto/jos-rooli-urakassa istunto/rooli-urakanvalvoja
                                                       (:id ur)
                                                       #(tallenna-tarkastukset ur
                                                                       sillan-tarkastukset %)
                                                       :ei-mahdollinen)
           }

          ;; sarakkeet
          @siltatarkastussarakkeet

          @siltatarkastusrivit
          ]]))))

(defn siltatarkastukset [ur]
  (if-let [vs @valittu-silta]
    [sillan-tarkastukset ur]
    [sillat ur]))
