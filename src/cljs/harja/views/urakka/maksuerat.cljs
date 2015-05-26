(ns harja.views.urakka.maksuerat
  "Urakan 'Maksuerat' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.urakka.maksuerat :as maksuerat]
            [harja.tiedot.istunto :as istunto]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.views.urakka.toteumat.lampotilat :refer [lampotilat]]

            [harja.ui.visualisointi :as vis]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn tyyppi-enum->string-plural [tyyppi]
  (case tyyppi
    "kokonaishintainen" "Kokonaishintaiset"
    "yksikkohintainen" "Yksikköhintaiset"
    "lisatyo" "Lisätyöt"
    "indeksi" "Indeksit"
    "bonus" "Bonukset"
    "sakko" "Sakot"
    "akillinen_hoitotyo" "Äkilliset hoitotyöt"
    "muu" "Muut"
    "Ei tyyppiä"))


(defn sorttausjarjestys [tyyppi]
  (case tyyppi
    "kokonaishintainen" 1
    "yksikkohintainen" 2
    "lisatyo" 3
    "indeksi" 4
    "bonus" 5
    "sakko" 6
    "akillinen_hoitotyo" 7
    "muu" 8
    9))

(defn ryhmittele-maksuerat [rivit]
  (let [otsikko (fn [rivi] (tyyppi-enum->string-plural (:tyyppi rivi)))
        otsikon-mukaan (group-by otsikko (sort-by #(sorttausjarjestys (:tyyppi %)) rivit))]
    (doall (mapcat (fn [[otsikko rivit]]
                     (concat [(grid/otsikko otsikko)] rivit))
                   (seq otsikon-mukaan)))))

(def lahetyksessa (atom #{})) ; Setti lähetyksessä olevista maksuerien numeroista
(def maksuerarivit (atom nil))
(def urakka-id (atom nil))

(declare aloita-pollaus)


(defn hae-urakan-maksuerat [ur]
  (go
    (log (str "Urakan id: " ur))
    (reset! maksuerarivit (ryhmittele-maksuerat (<! (maksuerat/hae-urakan-maksuerat (:id ur)))))
    (log (str "Maksuerät saatu: " (pr-str @maksuerarivit)))
    (reset! lahetyksessa (into #{} (mapv ; Lisää lahetyksessa-settiin lähetyksessä olevat maksueränumerot
                                     (fn [rivi] (:numero rivi))
                                     (filter
                                       (fn [rivi]
                                         (= (:tila rivi) "odottaa_vastausta"))
                                       @maksuerarivit))))))
(defn laheta-maksuerat [maksueranumerot] ; Lähetä vain ne numerot, jotka eivät jo ole lähetyksessä
  (let [lahetettavat-maksueranumerot (into #{} (filter #(not (contains? @lahetyksessa %)) maksueranumerot))]
    (go (reset! lahetyksessa (into #{} (clojure.set/union @lahetyksessa lahetettavat-maksueranumerot)))
        (let [res (<! (maksuerat/laheta-maksuerat lahetettavat-maksueranumerot))]
          (if res ; Poistaa lahetyksessa-setistä ne numerot, jotka lähetettiin tässä pyynnössä
            ;; Lähetys ok
            (do (reset! maksuerarivit (mapv (fn [rivi]
                                              (if (contains? lahetettavat-maksueranumerot (:numero rivi))
                                                (assoc rivi :tila "odottaa_vastausta")
                                                rivi))
                                            @maksuerarivit))
                (aloita-pollaus))
            ;; Epäonnistui jostain syystä
            (do (reset! lahetyksessa (into #{} (remove (set lahetettavat-maksueranumerot) @lahetyksessa)))
                (reset! maksuerarivit (mapv (fn [rivi]
                                              (if (contains? lahetettavat-maksueranumerot (:numero rivi))
                                                (assoc rivi :tila "virhe")
                                                rivi))
                                            @maksuerarivit))))))))
(def pollaus-id (atom nil))
(def pollataan-kantaa? (atom false))

(defn lopeta-pollaus []
  (reset! pollataan-kantaa? false)
  (log (str "Kannan pollaus lopetettiin. Pollaus-id: " (pr-str @pollaus-id)))
  (js/clearInterval @pollaus-id) (reset! pollaus-id nil))

(defn pollaa-kantaa
  "Jos tiedossa on lähetyksessä olevia maksueriä, hakee uusimmat tiedot kannasta. Muussa tapauksessa lopettaa pollauksen."
  []
  (if (not (empty? @lahetyksessa))
    (do
      (log "Pollataan kantaa...")
      (hae-urakan-maksuerat @urakka-id))
    (do (log "Lopetetaan pollaus (ei lähetyksessä olevia maksueriä)")
        (lopeta-pollaus))))

(defn aloita-pollaus
  "Aloittaa kannan pollaamisen jos pollaus ei jo ole käynnissä"
  []
  (if (false? @pollataan-kantaa?) (do
                                    (reset! pollataan-kantaa? true)
                                    (reset! pollaus-id (js/setInterval pollaa-kantaa 10000))
                                    (log (str "Alettiin pollaamaan kantaa tietyn ajan välein. Pollaus-id: " (pr-str @pollaus-id))))))

(defn maksuerat
  "Maksuerien pääkomponentti"
  [ur]
  (reset! urakka-id ur)
  (hae-urakan-maksuerat ur)
  (aloita-pollaus)
  (komp/luo
    {:component-will-unmount
    (fn []
      (lopeta-pollaus))}
    (fn [ur]
      [:div
       (let [lahetyksessa @lahetyksessa]
         [grid/grid
          {:otsikko "Maksuerät"
           :tyhja "Ei maksueriä."
           :tallenna nil
           :tunniste :numero}
          [{:otsikko "Numero" :nimi :numero :tyyppi :numero :leveys "10%" :pituus 16}
           {:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "33%" :pituus 16}
           {:otsikko "Maksuerän summa" :nimi :maksueran-summa :tyyppi :numero :leveys "14%" :pituus 16}
           {:otsikko "Kust.suunnitelman summa" :nimi :kustannussuunnitelma-summa :tyyppi :numero :leveys "18%"}
           {:otsikko "Tila" :nimi :tila :tyyppi :komponentti
            :komponentti (fn [rivi]
                           (case (:tila rivi)
                             "odottaa_vastausta" [:span.maksuera-odottaa-vastausta "Lähetetty, odottaa kuittausta" [yleiset/ajax-loader-pisteet]]
                             "lahetetty" [:span.maksuera-lahetetty (if (not (nil? (:lahetetty rivi)))
                                                                     (str "Lähetetty, kuitattu " (pvm/pvm-aika (:lahetetty rivi)))
                                                                     (str "Lähetetty, kuitattu (kuittauspäivämäärää puuttuu)"))]
                             "virhe" [:span.maksuera-virhe "Lähetys epäonnistui!"] ;
                             [:span "Ei lähetetty"])) :leveys "19%"}
           {:otsikko "Lähetys Sampoon" :nimi :laheta :tyyppi :komponentti
            :komponentti (fn [rivi]
                           (let [maksueranumero (:numero rivi)]
                             [:button.laheta-maksuera {:class (str "nappi-ensisijainen " (if (contains? lahetyksessa maksueranumero) "disabled"))
                                                       :type "button"
                                                       :on-click #(laheta-maksuerat #{maksueranumero})} "Lähetä"]))
            :leveys "7%"}] ;
          @maksuerarivit
          ])

       [:button.nappi-ensisijainen {:class (if (= (count @lahetyksessa) (count @maksuerarivit)) "disabled" "")
                                    :on-click #(do (.preventDefault %)
                                                   (laheta-maksuerat (into #{} (mapv (fn [rivi] (:numero rivi)) @maksuerarivit))))} "Lähetä kaikki" ]])))

